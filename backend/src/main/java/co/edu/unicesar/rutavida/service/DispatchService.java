package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.EmergencyAssignmentDAO;
import co.edu.unicesar.rutavida.dao.EmergencyDAO;
import co.edu.unicesar.rutavida.dao.EmergencyStationDAO;
import co.edu.unicesar.rutavida.dao.EmergencyTypeDAO;
import co.edu.unicesar.rutavida.dao.EmergencyVehicleDAO;
import co.edu.unicesar.rutavida.dao.HospitalDAO;
import co.edu.unicesar.rutavida.dao.RouteDAO;
import co.edu.unicesar.rutavida.dao.TransactionManager;
import co.edu.unicesar.rutavida.dao.VehicleTypeDAO;
import co.edu.unicesar.rutavida.dto.DispatchResult;
import co.edu.unicesar.rutavida.dto.HospitalOption;
import co.edu.unicesar.rutavida.dto.RouteOption;
import co.edu.unicesar.rutavida.dto.UnitCandidate;
import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.graph.VehicleProfile;
import co.edu.unicesar.rutavida.model.Emergency;
import co.edu.unicesar.rutavida.model.EmergencyAssignment;
import co.edu.unicesar.rutavida.model.EmergencyStation;
import co.edu.unicesar.rutavida.model.EmergencyVehicle;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.Hospital;
import co.edu.unicesar.rutavida.model.HospitalServiceOffering;
import co.edu.unicesar.rutavida.model.Route;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.VehicleType;
import co.edu.unicesar.rutavida.model.enums.AssignmentStatus;
import co.edu.unicesar.rutavida.model.enums.AuditResult;
import co.edu.unicesar.rutavida.model.enums.EmergencyStatus;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import co.edu.unicesar.rutavida.model.enums.RouteStatus;
import co.edu.unicesar.rutavida.model.enums.VehicleOperationalStatus;
import co.edu.unicesar.rutavida.util.ValidationUtil;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Dispatching a unit (CU-08, CU-09, CU-10): which units can attend an emergency and how
 * fast, the routes before confirming, the dispatch itself in one transaction, and how the
 * mission advances. Permissions are checked by DispatchController before calling here.
 *
 * <p>Auditing: the dispatch is audited here (DESPACHAR); every later change of status of
 * the assignment and of the emergency is audited by the triggers of 04_plsql.sql.
 *
 * <p>Closing the emergency (CU-12) is done by Oracle with sp_cerrar_emergencia; here are its
 * rules and its audit. Not here yet: the hospital for a transfer (CU-11, K7b).
 */
public class DispatchService {

    static final String AUDIT_MODULE = "DESPACHO";

    /** The closing is audited with the emergency, so its history (CU-07) shows who closed it. */
    static final String EMERGENCY_AUDIT_MODULE = "EMERGENCIAS";

    /** Missions whose unit is moving: on its way to the scene or to a hospital (K9). */
    private static final Set<AssignmentStatus> MOVING =
            EnumSet.of(AssignmentStatus.ASSIGNED, AssignmentStatus.EN_ROUTE, AssignmentStatus.TRANSPORTING);

    /** CU-11 (RN-10): the service a hospital must offer to receive a patient. */
    static final String REQUIRED_SERVICE = "URGENCIAS";

    /** Only an ambulance takes a patient to a hospital. */
    static final String TRANSFER_VEHICLE = "AMBULANCIA";

    /**
     * Minutes added to a hospital whose service is not confirmed as available ('N'). The data of
     * D2 is 'N' until each hospital confirms it, so those hospitals are listed, but after the
     * confirmed ones that are as close. An assumption to calibrate, like RN-08 and RN-09.
     */

    static final double UNCONFIRMED_PENALTY_MINUTES = 10;

    /** An emergency is closed as a false alarm (REPORTADA) or after a unit arrived. */
    private static final Set<EmergencyStatus> CLOSABLE =
            EnumSet.of(EmergencyStatus.REPORTED, EmergencyStatus.ON_SCENE, EmergencyStatus.TRANSPORTING);

    /**
     * Which vehicle types can attend each emergency type (types of 02_datos_iniciales.sql).
     * A type that is not here can be attended by any unit.
     */
    static final Map<String, Set<String>> SUITABLE = Map.of(
            "EMERGENCIA_MEDICA", Set.of("AMBULANCIA"),
            "INCENDIO", Set.of("BOMBERO"),
            "ACCIDENTE_VIAL", Set.of("AMBULANCIA", "BOMBERO"),
            "RESCATE", Set.of("BOMBERO"));

    /** Mission: ASIGNADA → EN_RUTA → EN_SITIO → TRASLADANDO → FINALIZADA, or CANCELADA before arriving. */
    private static final Map<AssignmentStatus, Set<AssignmentStatus>> NEXT = Map.of(
            AssignmentStatus.ASSIGNED, EnumSet.of(AssignmentStatus.EN_ROUTE, AssignmentStatus.CANCELLED),
            AssignmentStatus.EN_ROUTE, EnumSet.of(AssignmentStatus.ON_SCENE, AssignmentStatus.CANCELLED),
            AssignmentStatus.ON_SCENE, EnumSet.of(AssignmentStatus.TRANSPORTING, AssignmentStatus.FINISHED),
            AssignmentStatus.TRANSPORTING, EnumSet.of(AssignmentStatus.FINISHED),
            AssignmentStatus.FINISHED, EnumSet.noneOf(AssignmentStatus.class),
            AssignmentStatus.CANCELLED, EnumSet.noneOf(AssignmentStatus.class));

    private final EmergencyDAO emergencyDao;
    private final EmergencyTypeDAO emergencyTypeDao;
    private final EmergencyVehicleDAO vehicleDao;
    private final VehicleTypeDAO vehicleTypeDao;
    private final EmergencyStationDAO stationDao;
    private final EmergencyAssignmentDAO assignmentDao;
    private final RouteDAO routeDao;
    private final HospitalDAO hospitalDao;
    private final RouteService routeService;
    private final AuditService auditService;
    private final TransactionManager transactions;
    private final Clock clock;

    public DispatchService(EmergencyDAO emergencyDao, EmergencyTypeDAO emergencyTypeDao,
                           EmergencyVehicleDAO vehicleDao, VehicleTypeDAO vehicleTypeDao,
                           EmergencyStationDAO stationDao, EmergencyAssignmentDAO assignmentDao,
                           RouteDAO routeDao, HospitalDAO hospitalDao, RouteService routeService,
                           AuditService auditService, TransactionManager transactions, Clock clock) {
        this.emergencyDao = Objects.requireNonNull(emergencyDao, "emergencyDao");
        this.emergencyTypeDao = Objects.requireNonNull(emergencyTypeDao, "emergencyTypeDao");
        this.vehicleDao = Objects.requireNonNull(vehicleDao, "vehicleDao");
        this.vehicleTypeDao = Objects.requireNonNull(vehicleTypeDao, "vehicleTypeDao");
        this.stationDao = Objects.requireNonNull(stationDao, "stationDao");
        this.assignmentDao = Objects.requireNonNull(assignmentDao, "assignmentDao");
        this.routeDao = Objects.requireNonNull(routeDao, "routeDao");
        this.hospitalDao = Objects.requireNonNull(hospitalDao, "hospitalDao");
        this.routeService = Objects.requireNonNull(routeService, "routeService");
        this.auditService = Objects.requireNonNull(auditService, "auditService");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    // ------------------------------------------------------------------ CU-09: who can go

    /**
     * Available units of a suitable type, fastest first. A unit that cannot reach the
     * emergency by the loaded road network is left out.
     */
    public List<UnitCandidate> candidates(long emergencyId) {
        return candidates(emergencyId, null);
    }

    List<UnitCandidate> gpsCandidates(long emergencyId,
                                      java.util.function.Function<EmergencyVehicle, Optional<GeoPoint>> gpsStart) {
        return candidates(emergencyId, Objects.requireNonNull(gpsStart));
    }

    private List<UnitCandidate> candidates(long emergencyId,
                                           java.util.function.Function<EmergencyVehicle, Optional<GeoPoint>> gpsStart) {
        Emergency emergency = findEmergency(emergencyId);
        requireReported(emergency);
        String emergencyType = emergencyTypeName(emergency);
        List<VehicleType> types = vehicleTypeDao.findAll();

        List<UnitCandidate> candidates = new ArrayList<>();
        for (EmergencyVehicle vehicle : vehicleDao.findAvailable()) {
            String vehicleType = typeName(types, vehicle);
            if (!suits(emergencyType, vehicleType)) {
                continue;
            }
            if (gpsStart != null && (!"AMBULANCIA".equals(vehicleType)
                    || assignmentDao.findOpenByVehicle(vehicle.getId()).isPresent())) {
                continue;
            }
            EmergencyStation station = stationDao.findById(vehicle.getStationId()).orElse(null);
            GeoPoint start = gpsStart == null ? startOf(vehicle, station) : gpsStart.apply(vehicle).orElse(null);
            if (start == null) {
                continue;
            }
            try {
                RouteOption best = routeService.calculateOptions(
                        start, emergency.getLocation(), VehicleProfile.forType(vehicleType)).get(0);
                candidates.add(new UnitCandidate(vehicle.getId(), vehicle.getUnitCode(), vehicleType,
                        station == null ? null : station.getName(), start,
                        best.estimatedMinutes(), best.distanceKm()));
            } catch (BusinessRuleException outsideOrNoWay) {
                // This unit cannot reach the emergency by the loaded road network.
            }
        }
        candidates.sort(Comparator.comparingDouble(UnitCandidate::etaMinutes).thenComparingLong(UnitCandidate::vehicleId));
        return candidates;
    }

    // ------------------------------------------------------------------ CU-08: routes before confirming

    /** The routes of a unit to the emergency, recommended first, without saving anything. */
    public List<RouteOption> preview(long emergencyId, long vehicleId) {
        Emergency emergency = findEmergency(emergencyId);
        EmergencyVehicle vehicle = findVehicle(vehicleId);
        String vehicleType = typeName(vehicleTypeDao.findAll(), vehicle);
        return routeService.calculateOptions(requireStart(vehicle), emergency.getLocation(),
                VehicleProfile.forType(vehicleType));
    }

    // ------------------------------------------------------------------ CU-09: dispatch

    /**
     * Sends the unit in one transaction: the assignment (sp_asignar_unidad also marks the
     * unit OCUPADO and the emergency ASIGNADA), the routes (the recommended one ACTIVA, the
     * alternatives PROPUESTA) and the audit. The routes are calculated again here: the
     * traffic may have changed since the preview.
     */
    public DispatchResult dispatch(long emergencyId, long vehicleId, UserSession session) {
        return dispatch(emergencyId, vehicleId, session, null);
    }

    DispatchResult dispatchGps(long emergencyId, long vehicleId, UserSession session,
                               java.util.function.Function<EmergencyVehicle, Optional<GeoPoint>> gpsStart) {
        return dispatch(emergencyId, vehicleId, session, Objects.requireNonNull(gpsStart));
    }

    private DispatchResult dispatch(long emergencyId, long vehicleId, UserSession session,
                                    java.util.function.Function<EmergencyVehicle, Optional<GeoPoint>> gpsStart) {
        ValidationUtil.requireNonNull(session, "sesión");
        long userId = session.user().getId();

        return transactions.callInTransaction(() -> {
            Emergency emergency = findEmergency(emergencyId);
            requireReported(emergency);
            EmergencyVehicle vehicle = findVehicle(vehicleId);
            if (!isDispatchable(vehicle)) {
                throw new BusinessRuleException("La unidad " + vehicle.getUnitCode()
                        + " no está disponible (" + vehicle.getOperationalStatus().code() + ").");
            }
            String vehicleType = typeName(vehicleTypeDao.findAll(), vehicle);
            String emergencyType = emergencyTypeName(emergency);
            if (!suits(emergencyType, vehicleType)) {
                throw new BusinessRuleException("Una unidad " + vehicleType
                        + " no atiende emergencias de tipo " + emergencyType + ".");
            }

            VehicleProfile profile = VehicleProfile.forType(vehicleType);
            if (gpsStart != null && (!"AMBULANCIA".equals(vehicleType)
                    || assignmentDao.findOpenByVehicle(vehicleId).isPresent())) {
                throw new BusinessRuleException("La unidad no está disponible para el despacho GPS.");
            }
            GeoPoint start = gpsStart == null ? requireStart(vehicle) : gpsStart.apply(vehicle)
                    .orElseThrow(() -> new BusinessRuleException("La ambulancia ya no tiene GPS activo."));
            List<RouteOption> options = routeService.calculateOptions(start, emergency.getLocation(), profile);

            EmergencyAssignment assignment = new EmergencyAssignment(0, emergencyId, vehicleId, userId,
                    LocalDateTime.now(clock), null, null, AssignmentStatus.ASSIGNED);
            validateNew(assignment);
            assignmentDao.insert(assignment);

            List<Route> routes = routeService.saveForMission(emergencyId, vehicleId, null, profile, options);
            auditService.record(userId, AUDIT_MODULE, "DESPACHAR", assignment.getId(), AuditResult.SUCCESS,
                    "Unidad " + vehicle.getUnitCode() + " a la emergencia " + emergencyId);
            return new DispatchResult(assignment, routes);
        });
    }

    // ------------------------------------------------------------------ CU-10: the mission advances

    /**
     * Moves the mission to EN_RUTA, EN_SITIO, FINALIZADA or CANCELADA and keeps the rest in
     * step: the emergency follows the unit, the routes end when it arrives, is cancelled or
     * finishes, and the unit is free again when the mission ends. TRASLADANDO needs the
     * hospital: it is {@link #startTransfer}.
     */
    public EmergencyAssignment advance(long assignmentId, AssignmentStatus next) {
        ValidationUtil.requireNonNull(next, "nuevo estado");
        if (next == AssignmentStatus.TRANSPORTING) {
            throw new ValidationException("Para el traslado elija el hospital de destino (CU-11).");
        }

        return transactions.callInTransaction(() -> {
            EmergencyAssignment assignment = lockAssignment(assignmentId);
            LocalDateTime changedAt = LocalDateTime.now(clock);
            changeStatus(assignment, next, changedAt);
            if (next == AssignmentStatus.FINISHED) {
                assignment.setFinishedAt(changedAt);
            }
            assignmentDao.update(assignment);

            Emergency emergency = findEmergency(assignment.getEmergencyId());
            EmergencyStatus status = emergency.getStatus();
            if (next == AssignmentStatus.EN_ROUTE && status == EmergencyStatus.ASSIGNED) {
                emergencyDao.updateStatus(emergency.getId(), EmergencyStatus.EN_ROUTE, null);
            } else if (next == AssignmentStatus.ON_SCENE
                    && (status == EmergencyStatus.ASSIGNED || status == EmergencyStatus.EN_ROUTE)) {
                emergencyDao.updateStatus(emergency.getId(), EmergencyStatus.ON_SCENE, null);
            } else if (next == AssignmentStatus.CANCELLED
                    && (status == EmergencyStatus.ASSIGNED || status == EmergencyStatus.EN_ROUTE)
                    && assignmentDao.findOpenByEmergency(emergency.getId()).isEmpty()) {
                emergencyDao.updateStatus(emergency.getId(), EmergencyStatus.REPORTED, null);
            }

            if (next == AssignmentStatus.ON_SCENE || next == AssignmentStatus.CANCELLED
                    || next == AssignmentStatus.FINISHED) {
                endRoutes(assignment, next != AssignmentStatus.CANCELLED);
            }
            if (next == AssignmentStatus.FINISHED || next == AssignmentStatus.CANCELLED) {
                vehicleDao.updateStatus(assignment.getVehicleId(), VehicleOperationalStatus.AVAILABLE);
            }
            return assignment;
        });
    }

    /** One assignment (mission) by id. */
    public EmergencyAssignment findAssignment(long assignmentId) {
        return assignmentDao.findById(assignmentId)
                .orElseThrow(() -> new NotFoundException("Asignación", assignmentId));
    }

    private EmergencyAssignment lockAssignment(long assignmentId) {
        // The Oracle close procedure locks this same parent first. Keep that order when guiding
        // transitions so simultaneous arrivals, transfers and finalizations see committed state.
        long emergencyId = findAssignment(assignmentId).getEmergencyId();
        emergencyDao.findByIdForUpdate(emergencyId)
                .orElseThrow(() -> new NotFoundException("Emergencia", emergencyId));
        return assignmentDao.findByIdForUpdate(assignmentId)
                .orElseThrow(() -> new NotFoundException("Asignación", assignmentId));
    }

    /** The routes of a mission that are still in use: the ACTIVA and the PROPUESTA. */
    public List<Route> missionRoutes(long assignmentId) {
        EmergencyAssignment assignment = findAssignment(assignmentId);
        return routesOf(assignment).stream()
                .filter(route -> route.getStatus() == RouteStatus.ACTIVE
                        || route.getStatus() == RouteStatus.PROPOSED)
                .toList();
    }

    // ------------------------------------------------------------------ K9: units on their way

    /** A mission whose unit is moving: its assignment, its ACTIVA route and how the unit drives. */
    public record ActiveMission(EmergencyAssignment assignment, Route route, VehicleProfile vehicle) {
    }

    /**
     * The missions that the simulation must move (K9): every ACTIVA route whose unit has an open
     * assignment ASIGNADA, EN_RUTA or TRASLADANDO for the same emergency.
     */
    public List<ActiveMission> activeMissions() {
        List<VehicleType> types = vehicleTypeDao.findAll();
        List<ActiveMission> missions = new ArrayList<>();
        for (Route route : routeDao.findActive()) {
            Optional<EmergencyAssignment> open = assignmentDao.findOpenByVehicle(route.getVehicleId())
                    .filter(assignment -> assignment.getEmergencyId() == route.getEmergencyId())
                    .filter(assignment -> MOVING.contains(assignment.getStatus()));
            Optional<EmergencyVehicle> vehicle = vehicleDao.findById(route.getVehicleId());
            if (open.isPresent() && vehicle.isPresent()) {
                missions.add(new ActiveMission(open.get(), route,
                        VehicleProfile.forType(typeName(types, vehicle.get()))));
            }
        }
        return missions;
    }

    // ------------------------------------------------------------------ CU-11 / K10: transfer to a hospital

    /**
     * The hospitals that can receive the patient of a mission EN_SITIO, the most convenient first
     * (RN-10): operational, offering URGENCIAS and reachable by the loaded road network, ordered by
     * the ETA from where the ambulance is, plus a penalty if URGENCIAS is not confirmed.
     */
    public List<HospitalOption> hospitalsFor(long assignmentId) {
        EmergencyAssignment assignment = findAssignment(assignmentId);
        EmergencyVehicle vehicle = requireTransferUnit(assignment);
        return rankHospitals(requireStart(vehicle), VehicleProfile.forType(TRANSFER_VEHICLE));
    }

    List<HospitalOption> hospitalsFor(long assignmentId, GeoPoint gpsOrigin) {
        requireTransferUnit(findAssignment(assignmentId));
        return rankHospitals(gpsOrigin, VehicleProfile.forType(TRANSFER_VEHICLE));
    }

    /**
     * Starts the transfer in one transaction: the routes to the hospital (the recommended one
     * ACTIVA, the others PROPUESTA, all with the hospital as destination), the mission and the
     * emergency TRASLADANDO, and the audit. The simulation (K9) then drives the ambulance by the
     * new ACTIVA route. At the hospital, the coordinator ends the mission (FINALIZADA).
     */
    public DispatchResult startTransfer(long assignmentId, long hospitalId, UserSession session) {
        return startTransfer(assignmentId, hospitalId, session, null);
    }

    DispatchResult startTransfer(long assignmentId, long hospitalId, UserSession session, GeoPoint gpsOrigin) {
        ValidationUtil.requireNonNull(session, "sesión");
        ValidationUtil.requirePositiveId(hospitalId, "hospital");
        long userId = session.user().getId();
        return transactions.callInTransaction(() -> {
            EmergencyAssignment assignment = lockAssignment(assignmentId);
            EmergencyVehicle vehicle = requireTransferUnit(assignment);
            Hospital hospital = hospitalDao.findById(hospitalId)
                    .orElseThrow(() -> new NotFoundException("Hospital", hospitalId));
            GeoPoint start = gpsOrigin == null ? requireStart(vehicle) : gpsOrigin;
            VehicleProfile profile = VehicleProfile.forType(TRANSFER_VEHICLE);
            if (hospital.getStatus() != RecordStatus.ACTIVE || emergencyService(hospital).isEmpty()) {
                throw new BusinessRuleException("El hospital " + hospital.getName() + " no está operativo"
                        + " o no ofrece " + REQUIRED_SERVICE + ".");
            }
            List<RouteOption> options = routeService.calculateOptions(start, hospital.getLocation(), profile);
            List<Route> routes = routeService.saveForMission(
                    assignment.getEmergencyId(), assignment.getVehicleId(), hospitalId, profile, options);
            changeStatus(assignment, AssignmentStatus.TRANSPORTING, LocalDateTime.now(clock));
            assignmentDao.update(assignment);
            Emergency emergency = findEmergency(assignment.getEmergencyId());
            if (emergency.getStatus() == EmergencyStatus.ON_SCENE) {
                emergencyDao.updateStatus(emergency.getId(), EmergencyStatus.TRANSPORTING, null);
            }
            auditService.record(userId, AUDIT_MODULE, "TRASLADAR", assignment.getId(), AuditResult.SUCCESS,
                    "Unidad " + vehicle.getUnitCode() + " al hospital " + hospital.getName());
            return new DispatchResult(assignment, routes);
        });
    }

    /** RN-10 on every operational hospital; one that cannot be reached is left out. */
    private List<HospitalOption> rankHospitals(GeoPoint from, VehicleProfile profile) {
        List<HospitalOption> options = new ArrayList<>();
        for (Hospital hospital : hospitalDao.findOperational()) {
            Optional<HospitalServiceOffering> service = emergencyService(hospital);
            if (service.isEmpty()) {
                continue;
            }
            try {
                RouteOption best = routeService.calculateOptions(from, hospital.getLocation(), profile).get(0);
                boolean confirmed = service.get().isAvailable();
                double score = best.estimatedMinutes() + (confirmed ? 0 : UNCONFIRMED_PENALTY_MINUTES);
                options.add(new HospitalOption(hospital.getId(), hospital.getName(), hospital.getLevel().code(),
                        hospital.getLocation(), best.estimatedMinutes(), best.distanceKm(), confirmed, score));
            } catch (BusinessRuleException outsideOrNoWay) {
                // This hospital cannot be reached by the loaded road network.
            }
        }
        options.sort(Comparator.comparingDouble(HospitalOption::scoreMinutes));
        return options;
    }

    private Optional<HospitalServiceOffering> emergencyService(Hospital hospital) {
        return hospitalDao.findServices(hospital.getId()).stream()
                .filter(offering -> REQUIRED_SERVICE.equals(offering.getServiceName()))
                .findFirst();
    }

    boolean isAmbulance(EmergencyVehicle vehicle) {
        return TRANSFER_VEHICLE.equals(typeName(vehicleTypeDao.findAll(), vehicle));
    }

    /** A transfer starts from a mission EN_SITIO, and only with an ambulance. */
    private EmergencyVehicle requireTransferUnit(EmergencyAssignment assignment) {
        if (assignment.getStatus() != AssignmentStatus.ON_SCENE) {
            throw new BusinessRuleException("El hospital se elige cuando la unidad está EN_SITIO; esta misión está "
                    + assignment.getStatus().code() + ".");
        }
        EmergencyVehicle vehicle = findVehicle(assignment.getVehicleId());
        String vehicleType = typeName(vehicleTypeDao.findAll(), vehicle);
        if (!TRANSFER_VEHICLE.equals(vehicleType)) {
            throw new BusinessRuleException("Solo una ambulancia traslada pacientes; "
                    + vehicle.getUnitCode() + " es " + vehicleType + ".");
        }
        return vehicle;
    }

    // ------------------------------------------------------------------ CU-12: close

    /**
     * Closes the emergency: as a false alarm (REPORTADA) or when the attention ended (EN_SITIO
     * or TRASLADANDO). Oracle ends the open missions, frees their units and closes their
     * routes in the same call (sp_cerrar_emergencia). An emergency with a unit still on its
     * way (ASIGNADA or EN_RUTA) is not closed: first the mission is cancelled or arrives.
     */
    public Emergency closeEmergency(long emergencyId, UserSession session) {
        ValidationUtil.requireNonNull(session, "sesión");
        return transactions.callInTransaction(() -> {
            Emergency emergency = findEmergency(emergencyId);
            EmergencyStatus before = emergency.getStatus();
            if (before == EmergencyStatus.CLOSED) {
                throw new BusinessRuleException("La emergencia " + emergencyId + " ya está cerrada.");
            }
            if (!CLOSABLE.contains(before)) {
                throw new BusinessRuleException("No se puede cerrar una emergencia " + before.code()
                        + ": primero la unidad debe llegar (EN_SITIO) o la misión cancelarse.");
            }
            emergencyDao.close(emergencyId, LocalDateTime.now(clock));
            auditService.record(session.user().getId(), EMERGENCY_AUDIT_MODULE, "CERRAR", emergencyId,
                    AuditResult.SUCCESS, "Desde " + before.code());
            return findEmergency(emergencyId);
        });
    }

    // ------------------------------------------------------------------ rules

    /** Only available units can be sent to a new emergency. */
    public boolean isDispatchable(EmergencyVehicle vehicle) {
        return vehicle.getOperationalStatus() == VehicleOperationalStatus.AVAILABLE;
    }

    /** Checks a new assignment before saving it. */
    public void validateNew(EmergencyAssignment assignment) {
        ValidationUtil.requireNonNull(assignment, "asignación");
        ValidationUtil.requirePositiveId(assignment.getEmergencyId(), "emergencia");
        ValidationUtil.requirePositiveId(assignment.getVehicleId(), "unidad");
        ValidationUtil.requirePositiveId(assignment.getCoordinatorUserId(), "coordinador");
        ValidationUtil.requireNonNull(assignment.getAssignedAt(), "fecha de asignación");
        if (assignment.getStatus() != AssignmentStatus.ASSIGNED) {
            throw new ValidationException("Una asignación nueva debe iniciar en estado ASIGNADA.");
        }
    }

    public boolean canChange(AssignmentStatus from, AssignmentStatus to) {
        return from != null && to != null && NEXT.get(from).contains(to);
    }

    /** Advances the mission; EN_RUTA records the departure and EN_SITIO the arrival. */
    public void changeStatus(EmergencyAssignment assignment, AssignmentStatus next, LocalDateTime at) {
        ValidationUtil.requireNonNull(next, "nuevo estado");
        ValidationUtil.requireNonNull(at, "fecha del cambio");
        if (!canChange(assignment.getStatus(), next)) {
            throw new BusinessRuleException("No se puede cambiar la asignación de "
                    + assignment.getStatus().code() + " a " + next.code() + ".");
        }
        if (at.isBefore(assignment.getAssignedAt())) {
            throw new ValidationException("La fecha del cambio no puede ser anterior a la asignación.");
        }
        if (next == AssignmentStatus.EN_ROUTE) {
            assignment.setDepartedAt(at);
        }
        if (next == AssignmentStatus.ON_SCENE) {
            assignment.setArrivedAt(at);
        }
        assignment.setStatus(next);
    }

    /** True while the unit is still busy with this mission. */
    public boolean isOpen(EmergencyAssignment assignment) {
        return assignment.getStatus() != AssignmentStatus.FINISHED
                && assignment.getStatus() != AssignmentStatus.CANCELLED;
    }

    /** Updates the last known position of a unit; it cannot go back in time. */
    public void moveVehicle(EmergencyVehicle vehicle, GeoPoint point, LocalDateTime at) {
        ValidationUtil.requireLocation(point);
        ValidationUtil.requireNonNull(at, "fecha de ubicación");
        if (vehicle.getLastLocationAt() != null && at.isBefore(vehicle.getLastLocationAt())) {
            throw new ValidationException(
                    "La nueva fecha de ubicación no puede ser anterior a la registrada.");
        }
        vehicle.setLastLocation(point);
        vehicle.setLastLocationAt(at);
    }

    /** True if a unit of {@code vehicleType} can attend an emergency of {@code emergencyType}. */
    public boolean suits(String emergencyType, String vehicleType) {
        Set<String> allowed = SUITABLE.get(emergencyType);
        return allowed == null || allowed.contains(vehicleType);
    }

    // ------------------------------------------------------------------ helpers

    private Emergency findEmergency(long id) {
        return emergencyDao.findById(id).orElseThrow(() -> new NotFoundException("Emergencia", id));
    }

    private EmergencyVehicle findVehicle(long id) {
        return vehicleDao.findById(id).orElseThrow(() -> new NotFoundException("Unidad", id));
    }

    private static void requireReported(Emergency emergency) {
        if (emergency.getStatus() != EmergencyStatus.REPORTED) {
            throw new BusinessRuleException("Solo se despacha a una emergencia REPORTADA; esta está "
                    + emergency.getStatus().code() + ".");
        }
    }

    private String emergencyTypeName(Emergency emergency) {
        return emergencyTypeDao.findById(emergency.getEmergencyTypeId())
                .map(type -> type.getName())
                .orElseThrow(() -> new NotFoundException("Tipo de emergencia", emergency.getEmergencyTypeId()));
    }

    private static String typeName(List<VehicleType> types, EmergencyVehicle vehicle) {
        return types.stream()
                .filter(type -> type.getId() == vehicle.getVehicleTypeId())
                .map(VehicleType::getName)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Tipo de vehículo", vehicle.getVehicleTypeId()));
    }

    /** Where a route of the unit starts: its last position, or its station if it has none. */
    private static GeoPoint startOf(EmergencyVehicle vehicle, EmergencyStation station) {
        if (vehicle.getLastLocation() != null) {
            return vehicle.getLastLocation();
        }
        return station == null ? null : station.getLocation();
    }

    private GeoPoint requireStart(EmergencyVehicle vehicle) {
        GeoPoint start = startOf(vehicle, stationDao.findById(vehicle.getStationId()).orElse(null));
        if (start == null) {
            throw new BusinessRuleException("La unidad " + vehicle.getUnitCode() + " no tiene una posición conocida.");
        }
        return start;
    }

    private List<Route> routesOf(EmergencyAssignment assignment) {
        return routeDao.findByEmergency(assignment.getEmergencyId()).stream()
                .filter(route -> route.getVehicleId() == assignment.getVehicleId())
                .toList();
    }

    /** The unit arrived (the ACTIVA route is FINALIZADA) or was cancelled (all are DESCARTADA). */
    private void endRoutes(EmergencyAssignment assignment, boolean arrived) {
        for (Route route : routesOf(assignment)) {
            if (route.getStatus() == RouteStatus.ACTIVE) {
                routeDao.updateStatus(route.getId(), arrived ? RouteStatus.FINISHED : RouteStatus.DISCARDED);
            } else if (route.getStatus() == RouteStatus.PROPOSED) {
                routeDao.updateStatus(route.getId(), RouteStatus.DISCARDED);
            }
        }
    }
}
