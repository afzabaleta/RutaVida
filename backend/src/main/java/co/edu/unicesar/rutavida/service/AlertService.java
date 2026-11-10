
package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.AlertDAO;
import co.edu.unicesar.rutavida.dao.RoleDAO;
import co.edu.unicesar.rutavida.dao.TransactionManager;
import co.edu.unicesar.rutavida.dao.UserDAO;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.model.Alert;
import co.edu.unicesar.rutavida.model.EmergencyAssignment;
import co.edu.unicesar.rutavida.model.EmergencyVehicle;
import co.edu.unicesar.rutavida.model.RoadIncident;
import co.edu.unicesar.rutavida.model.Role;
import co.edu.unicesar.rutavida.model.User;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.enums.AlertType;
import co.edu.unicesar.rutavida.model.enums.IncidentType;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import co.edu.unicesar.rutavida.model.enums.VehicleOperationalStatus;
import co.edu.unicesar.rutavida.util.ValidationUtil;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Operational alerts (RF-10): they tell the coordinators what changed while a unit is on
 * its way. Other services call this one when something happens (K7, K8, K9); the user reads
 * his alerts and marks them as read.
 *
 * Alerts are not audited: they are notices, not business operations.
 */
public class AlertService {

    static final String DISPATCH_MODULE = "DESPACHO";
    static final String MANAGE = "GESTIONAR";

    static final int RECENT_LIMIT = 50;
    static final int MAX_MESSAGE = 300;

    private static final Locale SPANISH = Locale.forLanguageTag("es-CO");

    private final AlertDAO alertDao;
    private final UserDAO userDao;
    private final RoleDAO roleDao;
    private final TransactionManager transactions;
    private final Clock clock;

    public AlertService(AlertDAO alertDao, UserDAO userDao, RoleDAO roleDao,
                        TransactionManager transactions, Clock clock) {
        this.alertDao = Objects.requireNonNull(alertDao, "alertDao");
        this.userDao = Objects.requireNonNull(userDao, "userDao");
        this.roleDao = Objects.requireNonNull(roleDao, "roleDao");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Alert routeRecalculated(EmergencyAssignment assignment,
                                   String unitCode, double minutesSaved) {
        String unit = unit(unitCode);

        String message = minutesSaved > 0
                ? "Se recalculó la ruta de " + unit + ": llega "
                + minutes(minutesSaved) + " min antes."
                : "Se recalculó la ruta de " + unit
                + " porque la anterior ya no se puede usar.";

        return forMission(assignment, AlertType.ROUTE_RECALCULATION, message);
    }

    public Alert incidentOnRoute(EmergencyAssignment assignment,
                                 String unitCode,
                                 RoadIncident incident,
                                 String roadName) {
        ValidationUtil.requireNonNull(incident, "incidente");

        String road = ValidationUtil.requireText(roadName, "vía", 120);
        String unit = unit(unitCode);

        boolean closed = incident.isBlocksSegment()
                || incident.getType() == IncidentType.CLOSURE;

        AlertType type = closed
                ? AlertType.ROAD_CLOSURE
                : incident.getType() == IncidentType.ACCIDENT
                ? AlertType.ACCIDENT_ON_ROUTE
                : AlertType.OTHER;

        String message = label(incident.getType()) + " en " + road
                + (closed ? " (vía cerrada)" : "")
                + " en la ruta de " + unit + ".";

        return forMission(assignment, type, message);
    }

    public Alert congestionOnRoute(EmergencyAssignment assignment,
                                   String unitCode,
                                   String roadName) {
        String road = ValidationUtil.requireText(roadName, "vía", 120);

        return forMission(
                assignment,
                AlertType.CONGESTION,
                "Congestión en " + road + " en la ruta de "
                        + unit(unitCode) + "."
        );
    }

    public List<Alert> availabilityChanged(
            EmergencyVehicle vehicle,
            VehicleOperationalStatus before) {

        ValidationUtil.requireNonNull(vehicle, "unidad");
        ValidationUtil.requireNonNull(before, "estado anterior");

        VehicleOperationalStatus now = vehicle.getOperationalStatus();

        if (now == before) {
            return List.of();
        }

        String message = "La unidad " + unit(vehicle.getUnitCode())
                + " pasó de " + before.code()
                + " a " + now.code() + ".";

        return transactions.callInTransaction(() -> {
            List<Alert> saved = new ArrayList<>();

            for (User dispatcher : dispatchers()) {
                saved.add(save(
                        dispatcher.getId(),
                        null,
                        AlertType.AVAILABILITY_CHANGE,
                        message
                ));
            }

            return saved;
        });
    }

    public List<Alert> list(UserSession session, boolean onlyUnread) {
        long userId = userOf(session);

        return onlyUnread
                ? alertDao.findUnreadByUser(userId)
                : alertDao.findByUser(userId, RECENT_LIMIT);
    }

    public Alert markAsRead(long alertId, UserSession session) {
        long userId = userOf(session);

        Alert alert = alertDao.findById(alertId)
                .filter(found -> found.getUserId() == userId)
                .orElseThrow(() -> new NotFoundException("Alerta", alertId));

        if (!alert.isRead()) {
            alertDao.markAsRead(alertId);
            alert.setRead(true);
        }

        return alert;
    }

    private Alert forMission(EmergencyAssignment assignment,
                             AlertType type,
                             String message) {
        ValidationUtil.requireNonNull(assignment, "asignación");

        return save(
                assignment.getCoordinatorUserId(),
                assignment.getEmergencyId(),
                type,
                message
        );
    }

    private Alert save(long userId,
                       Long emergencyId,
                       AlertType type,
                       String message) {

        Alert alert = new Alert(
                0,
                emergencyId,
                userId,
                type,
                ValidationUtil.requireText(message, "mensaje", MAX_MESSAGE),
                LocalDateTime.now(clock),
                false
        );

        alertDao.insert(alert);
        return alert;
    }

    private List<User> dispatchers() {
        Map<Long, Role> roles = roleDao.findAll().stream()
                .collect(Collectors.toMap(Role::getId, Function.identity()));

        return userDao.findAll().stream()
                .filter(user -> user.getStatus() == RecordStatus.ACTIVE)
                .filter(user -> canDispatch(roles.get(user.getRoleId())))
                .toList();
    }

    private static boolean canDispatch(Role role) {
        return role != null
                && role.getStatus() == RecordStatus.ACTIVE
                && role.getPermissions().stream().anyMatch(permission ->
                permission.getModule().equalsIgnoreCase(DISPATCH_MODULE)
                        && permission.getAction().equalsIgnoreCase(MANAGE));
    }

    private static long userOf(UserSession session) {
        return ValidationUtil.requireNonNull(session, "sesión")
                .user().getId();
    }

    private static String unit(String unitCode) {
        return ValidationUtil.requireText(unitCode, "unidad", 30);
    }

    private static String minutes(double value) {
        return String.format(SPANISH, "%.1f", value);
    }

    private static String label(IncidentType type) {
        return switch (type) {
            case ACCIDENT -> "Accidente";
            case ROADWORK -> "Obra";
            case CLOSURE -> "Cierre";
            case PROTEST -> "Manifestación";
            case OTHER -> "Incidente";
        };
    }
}
