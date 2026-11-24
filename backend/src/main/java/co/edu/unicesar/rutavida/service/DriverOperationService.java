package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.EmergencyAssignmentDAO;
import co.edu.unicesar.rutavida.dao.EmergencyDAO;
import co.edu.unicesar.rutavida.dao.EmergencyVehicleDAO;
import co.edu.unicesar.rutavida.dao.TransactionManager;
import co.edu.unicesar.rutavida.dao.UnitDriverDAO;
import co.edu.unicesar.rutavida.dto.DriverMission;
import co.edu.unicesar.rutavida.dto.DriverUnit;
import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.model.Emergency;
import co.edu.unicesar.rutavida.model.EmergencyAssignment;
import co.edu.unicesar.rutavida.model.EmergencyVehicle;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.enums.AlertType;
import co.edu.unicesar.rutavida.model.enums.AssignmentStatus;
import co.edu.unicesar.rutavida.model.enums.AuditResult;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Field actions of the driver in the "Compartir ubicación" screen.  This service does not
 * calculate, replace or move routes; it only uses the already existing mission lifecycle.
 */
public final class DriverOperationService {

    public static final String AUDIT_MODULE = "GPS";
    private static final String START = "INICIAR_GPS";
    private static final String STOP = "DETENER_GPS";
    private static final String ARRIVE = "CONFIRMAR_LLEGADA";
    private static final String FINISH = "FINALIZAR_ATENCION";

    private final UnitDriverDAO drivers;
    private final EmergencyVehicleDAO vehicles;
    private final EmergencyAssignmentDAO assignments;
    private final EmergencyDAO emergencies;
    private final DispatchService dispatch;
    private final AlertService alerts;
    private final AuditService audit;
    private final TransactionManager transactions;
    private final Clock clock;

    public DriverOperationService(UnitDriverDAO drivers, EmergencyVehicleDAO vehicles,
                                  EmergencyAssignmentDAO assignments, EmergencyDAO emergencies,
                                  DispatchService dispatch, AlertService alerts, AuditService audit,
                                  TransactionManager transactions, Clock clock) {
        this.drivers = Objects.requireNonNull(drivers, "drivers");
        this.vehicles = Objects.requireNonNull(vehicles, "vehicles");
        this.assignments = Objects.requireNonNull(assignments, "assignments");
        this.emergencies = Objects.requireNonNull(emergencies, "emergencies");
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
        this.alerts = Objects.requireNonNull(alerts, "alerts");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Context for the mobile conductor screen. A unit without an open mission still keeps GPS. */
    public DriverMission current(UserSession driver) {
        EmergencyVehicle vehicle = ownVehicle(driver);
        return assignments.findOpenByVehicle(vehicle.getId())
                .map(assignment -> describe(vehicle, assignment))
                .orElseGet(() -> describe(vehicle, null));
    }

    /** Records the beginning of a GPS session and marks an assigned mission as EN_RUTA. */
    public DriverMission startGps(UserSession driver) {
        return transactions.callInTransaction(() -> {
            EmergencyVehicle vehicle = ownVehicle(driver);
            requireLocation(vehicle);
            EmergencyAssignment assignment = assignments.findOpenByVehicle(vehicle.getId()).orElse(null);
            if (assignment != null && assignment.getStatus() == AssignmentStatus.ASSIGNED) {
                assignment = dispatch.advance(assignment.getId(), AssignmentStatus.EN_ROUTE);
            }
            audit(driver, START, assignment, vehicle, "GPS iniciado");
            return assignment == null ? describe(vehicle, null) : describe(vehicle, assignment);
        });
    }

    /** Evidence of a manual stop. It intentionally leaves the mission and route untouched. */
    public DriverMission stopGps(UserSession driver) {
        return transactions.callInTransaction(() -> {
            EmergencyVehicle vehicle = ownVehicle(driver);
            EmergencyAssignment assignment = assignments.findOpenByVehicle(vehicle.getId()).orElse(null);
            audit(driver, STOP, assignment, vehicle, "GPS detenido por el conductor");
            return assignment == null ? describe(vehicle, null) : describe(vehicle, assignment);
        });
    }

    /**
     * Confirms the physical arrival. GPS is not touched: the browser keeps sending positions
     * until the driver explicitly uses {@link #stopGps(UserSession)}.
     */
    public DriverMission confirmArrival(UserSession driver) {
        return transactions.callInTransaction(() -> {
            EmergencyVehicle vehicle = ownVehicle(driver);
            requireLocation(vehicle);
            EmergencyAssignment assignment = openMission(vehicle);
            if (assignment.getStatus() == AssignmentStatus.ASSIGNED) {
                assignment = dispatch.advance(assignment.getId(), AssignmentStatus.EN_ROUTE);
            }
            if (assignment.getStatus() != AssignmentStatus.EN_ROUTE) {
                throw new BusinessRuleException("La llegada solo se confirma cuando la unidad está EN_RUTA.");
            }
            assignment = dispatch.advance(assignment.getId(), AssignmentStatus.ON_SCENE);
            audit(driver, ARRIVE, assignment, vehicle, "Llegada confirmada al sitio");
            alerts.driverOperation(assignment, AlertType.OTHER,
                    "La unidad " + vehicle.getUnitCode() + " confirmó llegada a la emergencia "
                            + assignment.getEmergencyId() + ".");
            return describe(vehicle, assignment);
        });
    }

    /**
     * Ends the attention for the driver's own mission and frees only that unit. If it is the
     * last assignment of the emergency, the existing Oracle close procedure closes it too.
     * No location is changed and GPS remains independent from this action.
     */
    public DriverMission finishAttention(UserSession driver) {
        return transactions.callInTransaction(() -> {
            EmergencyVehicle vehicle = ownVehicle(driver);
            requireLocation(vehicle);
            EmergencyAssignment assignment = openMission(vehicle);
            if (assignment.getStatus() != AssignmentStatus.ON_SCENE) {
                throw new BusinessRuleException("La atención solo se finaliza después de confirmar la llegada.");
            }
            assignment = dispatch.advance(assignment.getId(), AssignmentStatus.FINISHED);
            LocalDateTime finishedAt = assignment.getFinishedAt();
            if (assignments.findOpenByEmergency(assignment.getEmergencyId()).isEmpty()) {
                emergencies.close(assignment.getEmergencyId(), finishedAt);
            }
            audit(driver, FINISH, assignment, vehicle, "Atención finalizada");
            alerts.driverOperation(assignment, AlertType.OTHER,
                    "La unidad " + vehicle.getUnitCode() + " finalizó la atención de la emergencia "
                            + assignment.getEmergencyId() + ".");
            return describe(vehicle, assignment);
        });
    }

    private EmergencyVehicle ownVehicle(UserSession driver) {
        Objects.requireNonNull(driver, "sesión");
        long userId = driver.user().getId();
        long vehicleId = drivers.findVehicleIdByUser(userId)
                .orElseThrow(() -> new BusinessRuleException("No tienes una unidad asignada para operar."));
        return vehicles.findById(vehicleId)
                .orElseThrow(() -> new NotFoundException("Unidad", vehicleId));
    }

    private EmergencyAssignment openMission(EmergencyVehicle vehicle) {
        return assignments.findOpenByVehicle(vehicle.getId())
                .orElseThrow(() -> new BusinessRuleException("Tu unidad no tiene una emergencia asignada."));
    }

    private DriverMission describe(EmergencyVehicle vehicle, EmergencyAssignment assignment) {
        DriverUnit unit = new DriverUnit(vehicle.getId(), vehicle.getUnitCode(), vehicle.getPlate(),
                vehicle.getLastLocation(), vehicle.getLastLocationAt());
        if (assignment == null) {
            return new DriverMission(unit, null, null, null, null, null, null, null, null);
        }
        Emergency emergency = emergencies.findById(assignment.getEmergencyId())
                .orElseThrow(() -> new NotFoundException("Emergencia", assignment.getEmergencyId()));
        return new DriverMission(unit, assignment.getId(), assignment.getEmergencyId(),
                emergency.getAddressReference(), emergency.getLocation(), assignment.getStatus(),
                assignment.getDepartedAt(), assignment.getArrivedAt(), assignment.getFinishedAt());
    }

    private void audit(UserSession driver, String operation, EmergencyAssignment assignment,
                       EmergencyVehicle vehicle, String text) {
        Long recordId = assignment == null ? vehicle.getId() : assignment.getId();
        audit.record(driver.user().getId(), AUDIT_MODULE, operation, recordId, AuditResult.SUCCESS,
                text + "; unidad=" + vehicle.getUnitCode() + "; emergencia="
                        + (assignment == null ? "sin asignación" : assignment.getEmergencyId())
                        + "; " + coordinates(vehicle.getLastLocation()));
    }

    private static void requireLocation(EmergencyVehicle vehicle) {
        if (vehicle.getLastLocation() == null || vehicle.getLastLocationAt() == null) {
            throw new BusinessRuleException("Espera una ubicación GPS válida antes de registrar esta operación.");
        }
    }

    private static String coordinates(GeoPoint point) {
        return point == null ? "sin coordenadas" : "lat=" + point.latitude() + ", lon=" + point.longitude();
    }
}
