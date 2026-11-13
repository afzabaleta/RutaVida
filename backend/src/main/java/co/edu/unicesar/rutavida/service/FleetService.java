
package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.EmergencyStationDAO;
import co.edu.unicesar.rutavida.dao.EmergencyVehicleDAO;
import co.edu.unicesar.rutavida.dao.TransactionManager;
import co.edu.unicesar.rutavida.dao.VehicleTypeDAO;
import co.edu.unicesar.rutavida.dto.StationRequest;
import co.edu.unicesar.rutavida.dto.VehicleRequest;
import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.EmergencyStation;
import co.edu.unicesar.rutavida.model.EmergencyVehicle;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.enums.AuditResult;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import co.edu.unicesar.rutavida.model.enums.VehicleOperationalStatus;
import co.edu.unicesar.rutavida.util.ValidationUtil;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Units and stations (CU-04): register and edit the ambulances and fire trucks, change their
 * availability by hand, and register, edit, activate and deactivate the stations.
 *
 * <p>OCUPADO belongs to the missions: only the dispatch sets it and only the end of the
 * mission removes it (DispatchService), so nobody frees by hand a unit that is attending an
 * emergency. When the availability of a unit changes, the coordinators receive an alert (S8).
 *
 * <p>Permissions are checked by FleetController. Every change is saved together with its
 * audit row in one transaction.
 */
public class FleetService {

    static final String UNITS_MODULE = "UNIDADES";
    static final String STATIONS_MODULE = "ESTACIONES";

    private static final String CODE_FORMAT = "[A-Z0-9-]{3,30}";
    private static final String PLATE_FORMAT = "[A-Z0-9-]{5,10}";
    private static final String PHONE_FORMAT = "[0-9+() -]{7,20}";

    private final EmergencyVehicleDAO vehicleDao;
    private final EmergencyStationDAO stationDao;
    private final VehicleTypeDAO vehicleTypeDao;
    private final AlertService alertService;
    private final AuditService auditService;
    private final TransactionManager transactions;

    public FleetService(EmergencyVehicleDAO vehicleDao, EmergencyStationDAO stationDao,
                        VehicleTypeDAO vehicleTypeDao, AlertService alertService,
                        AuditService auditService, TransactionManager transactions) {
        this.vehicleDao = Objects.requireNonNull(vehicleDao, "vehicleDao");
        this.stationDao = Objects.requireNonNull(stationDao, "stationDao");
        this.vehicleTypeDao = Objects.requireNonNull(vehicleTypeDao, "vehicleTypeDao");
        this.alertService = Objects.requireNonNull(alertService, "alertService");
        this.auditService = Objects.requireNonNull(auditService, "auditService");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    // ------------------------------------------------------------------ units

    /** Every unit, in any status, by code. */
    public List<EmergencyVehicle> listAll() {
        return vehicleDao.findAll();
    }

    /** The units that can be sent now (DISPONIBLE). */
    public List<EmergencyVehicle> listAvailable() {
        return vehicleDao.findAvailable();
    }

    /** One unit by id. */
    public EmergencyVehicle findVehicle(long id) {
        return vehicleDao.findById(id).orElseThrow(() -> new NotFoundException("Unidad", id));
    }

    /**
     * Registers a unit DISPONIBLE, without a position: until the tracking sends one, its
     * routes start at its station. The station must be active.
     */
    public EmergencyVehicle registerVehicle(VehicleRequest request, UserSession session) {
        ValidationUtil.requireNonNull(session, "sesión");
        return transactions.callInTransaction(() -> {
            EmergencyVehicle vehicle = buildVehicle(0, request, VehicleOperationalStatus.AVAILABLE);
            requireUnique(vehicle);
            long id = vehicleDao.insert(vehicle);
            auditService.record(session.user().getId(), UNITS_MODULE, "CREAR", id,
                    AuditResult.SUCCESS, "Unidad " + vehicle.getUnitCode());
            return vehicle;
        });
    }

    /** Rewrites the type, station, code and plate of a unit that is not on a mission. */
    public EmergencyVehicle updateVehicle(long id, VehicleRequest request, UserSession session) {
        ValidationUtil.requireNonNull(session, "sesión");
        return transactions.callInTransaction(() -> {
            EmergencyVehicle saved = findVehicle(id);
            if (saved.getOperationalStatus() == VehicleOperationalStatus.BUSY) {
                throw new BusinessRuleException("La unidad " + saved.getUnitCode()
                        + " está en una misión; se puede editar cuando termine.");
            }
            EmergencyVehicle vehicle = buildVehicle(id, request, saved.getOperationalStatus());
            vehicle.setLastLocation(saved.getLastLocation());
            vehicle.setLastLocationAt(saved.getLastLocationAt());
            requireUnique(vehicle);
            vehicleDao.update(vehicle);
            auditService.record(session.user().getId(), UNITS_MODULE, "EDITAR", id,
                    AuditResult.SUCCESS, "Unidad " + vehicle.getUnitCode());
            return vehicle;
        });
    }

    /**
     * Changes the availability of a unit by hand: DISPONIBLE, MANTENIMIENTO or FUERA_SERVICIO.
     * OCUPADO is never set nor removed here, and a unit is DISPONIBLE only at an active
     * station. The coordinators receive an alert.
     */
    public EmergencyVehicle changeVehicleStatus(long id, VehicleOperationalStatus status,
                                                UserSession session) {
        ValidationUtil.requireNonNull(status, "estado");
        ValidationUtil.requireNonNull(session, "sesión");
        if (status == VehicleOperationalStatus.BUSY) {
            throw new BusinessRuleException("El estado OCUPADO solo lo pone el despacho.");
        }
        return transactions.callInTransaction(() -> {
            EmergencyVehicle vehicle = findVehicle(id);
            VehicleOperationalStatus before = vehicle.getOperationalStatus();
            if (before == VehicleOperationalStatus.BUSY) {
                throw new BusinessRuleException("La unidad " + vehicle.getUnitCode()
                        + " está en una misión; su estado cambia cuando la misión termina.");
            }
            if (before == status) {
                throw new BusinessRuleException("La unidad ya está " + status.code() + ".");
            }
            if (status == VehicleOperationalStatus.AVAILABLE
                    && findStation(vehicle.getStationId()).getStatus() != RecordStatus.ACTIVE) {
                throw new BusinessRuleException("La estación de la unidad está inactiva; muévala a otra estación primero.");
            }
            vehicleDao.updateStatus(id, status);
            vehicle.setOperationalStatus(status);
            auditService.record(session.user().getId(), UNITS_MODULE, "CAMBIAR_ESTADO", id,
                    AuditResult.SUCCESS, before.code() + " -> " + status.code());
            alertService.availabilityChanged(vehicle, before);
            return vehicle;
        });
    }

    // ------------------------------------------------------------------ stations

    /** Every station, active or not. */
    public List<EmergencyStation> listStations() {
        return stationDao.findAll();
    }

    /** One station by id. */
    public EmergencyStation findStation(long id) {
        return stationDao.findById(id).orElseThrow(() -> new NotFoundException("Estación", id));
    }

    /** Registers an active station. Two stations cannot have the same name. */
    public EmergencyStation registerStation(StationRequest request, UserSession session) {
        ValidationUtil.requireNonNull(session, "sesión");
        EmergencyStation station = buildStation(0, request, RecordStatus.ACTIVE);
        return transactions.callInTransaction(() -> {
            requireUniqueName(station);
            long id = stationDao.insert(station);
            auditService.record(session.user().getId(), STATIONS_MODULE, "CREAR", id,
                    AuditResult.SUCCESS, null);
            return station;
        });
    }

    /** Rewrites the data of a station; its status does not change. */
    public EmergencyStation updateStation(long id, StationRequest request, UserSession session) {
        ValidationUtil.requireNonNull(session, "sesión");
        return transactions.callInTransaction(() -> {
            EmergencyStation saved = findStation(id);
            EmergencyStation station = buildStation(saved.getId(), request, saved.getStatus());
            requireUniqueName(station);
            stationDao.update(station);
            auditService.record(session.user().getId(), STATIONS_MODULE, "EDITAR", id,
                    AuditResult.SUCCESS, null);
            return station;
        });
    }

    /**
     * Activates or deactivates a station. A station with units DISPONIBLE or OCUPADO cannot be
     * deactivated: first they are moved to another station or taken out of service.
     */
    public EmergencyStation changeStationStatus(long id, RecordStatus status, UserSession session) {
        ValidationUtil.requireNonNull(status, "estado");
        ValidationUtil.requireNonNull(session, "sesión");
        return transactions.callInTransaction(() -> {
            EmergencyStation saved = findStation(id);
            if (saved.getStatus() == status) {
                throw new BusinessRuleException(status == RecordStatus.ACTIVE
                        ? "La estación ya está activa." : "La estación ya está inactiva.");
            }
            if (status == RecordStatus.INACTIVE) {
                long working = vehicleDao.findAll().stream()
                        .filter(vehicle -> vehicle.getStationId() == id)
                        .filter(vehicle -> vehicle.getOperationalStatus() == VehicleOperationalStatus.AVAILABLE
                                || vehicle.getOperationalStatus() == VehicleOperationalStatus.BUSY)
                        .count();
                if (working > 0) {
                    throw new BusinessRuleException("La estación tiene " + working
                            + " unidad(es) disponibles u ocupadas; muévalas a otra estación primero.");
                }
            }
            EmergencyStation station = new EmergencyStation(saved.getId(), saved.getName(),
                    saved.getAddress(), saved.getNeighborhood(), saved.getPhone(),
                    saved.getLocation(), status);
            stationDao.update(station);
            auditService.record(session.user().getId(), STATIONS_MODULE,
                    status == RecordStatus.ACTIVE ? "ACTIVAR" : "DESACTIVAR", id,
                    AuditResult.SUCCESS, null);
            return station;
        });
    }

    // ------------------------------------------------------------------ rules

    /** Checks the data of a unit (the same limits as the VEHICULO_EMERGENCIA table). */
    private EmergencyVehicle buildVehicle(long id, VehicleRequest request,
                                          VehicleOperationalStatus status) {
        ValidationUtil.requireNonNull(request, "unidad");
        long typeId = ValidationUtil.requirePositiveId(request.vehicleTypeId(), "tipo de vehículo");
        if (vehicleTypeDao.findAll().stream().noneMatch(type -> type.getId() == typeId)) {
            throw new NotFoundException("Tipo de vehículo", typeId);
        }
        EmergencyStation station = findStation(ValidationUtil.requirePositiveId(request.stationId(),
                "estación"));
        if (station.getStatus() != RecordStatus.ACTIVE) {
            throw new BusinessRuleException("La estación " + station.getName() + " está inactiva.");
        }
        String code = ValidationUtil.requireText(request.unitCode(), "código de la unidad", 30)
                .toUpperCase(Locale.ROOT);
        if (!code.matches(CODE_FORMAT)) {
            throw new ValidationException("El código de la unidad solo puede tener letras, números y guiones (3 a 30).");
        }
        String plate = ValidationUtil.optionalText(request.plate(), "placa", 10);
        if (plate != null) {
            plate = plate.toUpperCase(Locale.ROOT);
            if (!plate.matches(PLATE_FORMAT)) {
                throw new ValidationException("La placa solo puede tener letras, números y guiones (5 a 10).");
            }
        }
        return new EmergencyVehicle(id, typeId, station.getId(), code, plate, status, null, null);
    }

    /** Oracle also rejects a repeated code or plate; checking first gives a clear message. */
    private void requireUnique(EmergencyVehicle vehicle) {
        for (EmergencyVehicle other : vehicleDao.findAll()) {
            if (other.getId() == vehicle.getId()) {
                continue;
            }
            if (other.getUnitCode().equalsIgnoreCase(vehicle.getUnitCode())) {
                throw new BusinessRuleException("Ya existe una unidad con el código "
                        + vehicle.getUnitCode() + ".");
            }
            if (vehicle.getPlate() != null && vehicle.getPlate().equalsIgnoreCase(other.getPlate())) {
                throw new BusinessRuleException("Ya existe una unidad con la placa "
                        + vehicle.getPlate() + ".");
            }
        }
    }

    /** Checks the data of a station (the same limits as the ESTACION_EMERGENCIA table). */
    private static EmergencyStation buildStation(long id, StationRequest request, RecordStatus status) {
        ValidationUtil.requireNonNull(request, "estación");
        String phone = ValidationUtil.optionalText(request.phone(), "teléfono", 20);
        if (phone != null && !phone.matches(PHONE_FORMAT)) {
            throw new ValidationException("El teléfono solo puede tener números, espacios y los signos + ( ) -.");
        }
        return new EmergencyStation(id,
                ValidationUtil.requireText(request.name(), "nombre", 100),
                ValidationUtil.requireText(request.address(), "dirección", 180),
                ValidationUtil.optionalText(request.neighborhood(), "barrio", 100),
                phone,
                ValidationUtil.requireLocation(request.location()),
                status);
    }

    private void requireUniqueName(EmergencyStation station) {
        boolean taken = stationDao.findAll().stream()
                .anyMatch(other -> other.getId() != station.getId()
                        && other.getName().equalsIgnoreCase(station.getName()));
        if (taken) {
            throw new BusinessRuleException("Ya existe una estación con ese nombre.");
        }
    }
}
