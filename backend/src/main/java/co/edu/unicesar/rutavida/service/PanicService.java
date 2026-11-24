
package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.EmergencyTypeDAO;
import co.edu.unicesar.rutavida.dao.PanicReportDAO;
import co.edu.unicesar.rutavida.dao.RoleDAO;
import co.edu.unicesar.rutavida.dao.TransactionManager;
import co.edu.unicesar.rutavida.dao.UserDAO;
import co.edu.unicesar.rutavida.dao.UnitDriverDAO;
import co.edu.unicesar.rutavida.dto.EmergencyRequest;
import co.edu.unicesar.rutavida.dto.PanicAlertRequest;
import co.edu.unicesar.rutavida.dto.PanicReportView;
import co.edu.unicesar.rutavida.dto.PanicResult;
import co.edu.unicesar.rutavida.dto.UnitCandidate;
import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.Emergency;
import co.edu.unicesar.rutavida.model.EmergencyVehicle;
import co.edu.unicesar.rutavida.model.EmergencyType;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.PanicReport;
import co.edu.unicesar.rutavida.model.Role;
import co.edu.unicesar.rutavida.model.User;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.enums.AuditResult;
import co.edu.unicesar.rutavida.model.enums.PanicReportStatus;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import co.edu.unicesar.rutavida.model.enums.Severity;
import co.edu.unicesar.rutavida.util.GeoUtil;
import co.edu.unicesar.rutavida.util.ValidationUtil;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Panic button (CU-22, rules RN-13 to RN-16, DT-12 and DT-13).
 *
 * <p>Only a registered person presses it (DT-13): every report has its user, so every alert
 * has someone responsible. An authenticated report validates the emergency immediately.
 * Reports of the same type near an open incident in the deduplication window are linked to it.
 *
 * <p>When a coordinator discards a pending alert as false, every citizen with 2 discarded
 * reports in 30 days is blocked (RN-16, CitizenService).
 *
 * <p>The values (300 m, 10 minutes, 2 false alarms) are initial parameters to
 * calibrate (DT-11). The controller checks the permissions.
 */
public class PanicService {

    private static final Logger LOG = Logger.getLogger(PanicService.class.getName());

    static final String AUDIT_MODULE = "PANICO";
    static final String VALIDATE = "VALIDAR";

    /** Created by 07_boton_panico.sql: inactive, nobody can log in with it. */
    static final String SYSTEM_USERNAME = "sistema.rutavida";

    /** RN-14: distinct people that validate an alert, how close and how recent. */
    public static final int REPORTS_NEEDED = 1;
    public static final Duration DEFAULT_GPS_MAX_AGE = Duration.ofSeconds(15);
    static final double RADIUS_METERS = 300;
    static final Duration WINDOW = Duration.ofMinutes(10);

    /** RN-13: reports one person can send in the window. */
    static final int MAX_REPORTS_PER_PERSON = 3;

    /** What the panel shows (docs/api.md 16.2). */
    static final Duration RECENT = Duration.ofHours(2);

    /** Type of the emergency when nobody chose one: an ambulance is sent. */
    static final String DEFAULT_TYPE = "EMERGENCIA_MEDICA";

    static final int MAX_DESCRIPTION = 200;

    private final PanicReportDAO reportDao;
    private final EmergencyTypeDAO emergencyTypeDao;
    private final UserDAO userDao;
    private final RoleDAO roleDao;
    private final EmergencyService emergencyService;
    private final DispatchService dispatchService;
    private final RouteService routeService;
    private final AlertService alertService;
    private final AuditService auditService;
    private final CitizenService citizenService;
    private final TransactionManager transactions;
    private final Clock clock;
    private final UnitDriverDAO drivers;
    private final GpsTrackingService gps;
    private final Duration maxGpsAge;

    public PanicService(PanicReportDAO reportDao, EmergencyTypeDAO emergencyTypeDao,
                        UserDAO userDao, RoleDAO roleDao,
                        EmergencyService emergencyService, DispatchService dispatchService,
                        RouteService routeService, AlertService alertService,
                        AuditService auditService, CitizenService citizenService,
                        TransactionManager transactions, Clock clock,
                        UnitDriverDAO drivers, GpsTrackingService gps, Duration maxGpsAge) {
        this.reportDao = Objects.requireNonNull(reportDao, "reportDao");
        this.emergencyTypeDao = Objects.requireNonNull(emergencyTypeDao, "emergencyTypeDao");
        this.userDao = Objects.requireNonNull(userDao, "userDao");
        this.roleDao = Objects.requireNonNull(roleDao, "roleDao");
        this.emergencyService = Objects.requireNonNull(emergencyService, "emergencyService");
        this.dispatchService = Objects.requireNonNull(dispatchService, "dispatchService");
        this.routeService = Objects.requireNonNull(routeService, "routeService");
        this.alertService = Objects.requireNonNull(alertService, "alertService");
        this.auditService = Objects.requireNonNull(auditService, "auditService");
        this.citizenService = Objects.requireNonNull(citizenService, "citizenService");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.drivers = Objects.requireNonNull(drivers, "drivers");
        this.gps = Objects.requireNonNull(gps, "gps");
        this.maxGpsAge = Objects.requireNonNull(maxGpsAge, "maxGpsAge");
        if (maxGpsAge.isZero() || maxGpsAge.isNegative()) {
            throw new IllegalArgumentException("maxGpsAge must be positive");
        }
    }

    // ------------------------------------------------------------------ CU-22: press the button

    /**
     * Saves the report and validates the alert when the rules say so (RN-13 to RN-15).
     *
     * @param reporter the session of who pressed it: a citizen, a police officer or an
     *                 administrator (PANICO/REPORTAR, checked by the controller)
     */
    public PanicResult report(PanicAlertRequest request, UserSession reporter) {
        ValidationUtil.requireNonNull(request, "reporte");
        ValidationUtil.requireNonNull(reporter, "sesión");
        String deviceId = ValidationUtil.requireText(
                request.deviceId(), "identificador del dispositivo", 100);
        GeoPoint location = ValidationUtil.requireLocation(request.location());

        if (!routeService.isWithinNetwork(location)) {
            throw new ValidationException(
                    "La ubicación está fuera de la zona que cubre RutaVida. Llame al 123.");
        }

        String description = ValidationUtil.optionalText(
                request.description(), "descripción", MAX_DESCRIPTION);
        Long typeId = request.emergencyTypeId();

        if (typeId != null) {
            ValidationUtil.requirePositiveId(typeId, "tipo de emergencia");
            if (emergencyTypeDao.findById(typeId).isEmpty()) {
                throw new ValidationException("El tipo de emergencia no existe.");
            }
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime since = now.minus(WINDOW);
        String device = hashDevice(deviceId);
        long userId = reporter.user().getId();
        UserSession system = systemSession(now);

        Validation validation = transactions.callInTransaction(() -> {
            reportDao.lockValidation(system.user().getId());
            reportDao.expirePendingBefore(since);

            if (reportDao.countByUserSince(userId, since) >= MAX_REPORTS_PER_PERSON
                    || reportDao.countByDeviceSince(device, since) >= MAX_REPORTS_PER_PERSON) {
                throw new BusinessRuleException(
                        "Ya envió varios reportes. Si la emergencia sigue, llame al 123.");
            }

            PanicReport report = new PanicReport(
                    0, location, now, device, userId, typeId, description,
                    PanicReportStatus.PENDING, null);
            reportDao.insert(report);

            // Someone near an alert that is already being attended: join it, do not create another.
            long resolvedType = typeId == null ? defaultType() : typeId;
            Optional<Long> open = openEmergencyNear(location, since, resolvedType);
            long emergencyId = open.orElseGet(() -> emergencyService.register(
                    toEmergency(report, resolvedType), system).getId());
            reportDao.markValidated(List.of(report.getId()), emergencyId);
            auditService.record(
                    system.user().getId(), AUDIT_MODULE, VALIDATE, emergencyId,
                    AuditResult.SUCCESS,
                    "Reporte " + report.getId() + (open.isPresent()
                            ? " vinculado a una emergencia abierta." : " validado automáticamente."));
            return new Validation(report.getId(), emergencyId, open.isEmpty());
        });

        UnitCandidate unit = validation.created()
                ? dispatchOnItsOwn(validation.emergencyId(), system) : null;
        return new PanicResult(validation.reportId(), PanicReportStatus.VALIDATED,
                validation.emergencyId(), unit == null ? null : unit.unitCode(),
                1, REPORTS_NEEDED, validation.created(), unit == null ? null : unit.vehicleId());
    }

    // ------------------------------------------------------------------ panel

    /** Reports of the last 2 hours, newest first; all of them when {@code status} is null. */
    public List<PanicReportView> listRecent(PanicReportStatus status) {
        LocalDateTime now = LocalDateTime.now(clock);
        reportDao.expirePendingBefore(now.minus(WINDOW));
        var policeByUser = new HashMap<Long, Boolean>();

        return reportDao.findSince(now.minus(RECENT)).stream()
                .filter(report -> status == null || report.getStatus() == status)
                .map(report -> PanicReportView.of(report,
                        report.getUserId() != null && policeByUser.computeIfAbsent(report.getUserId(), this::isPolice)))
                .toList();
    }

    /**
     * A coordinator marks a pending alert as false (CU-22, 7a): the report and the other
     * PENDIENTE reports near it in the window become DESCARTADO, so one more press does not
     * validate it. A validated report can count as a false alarm after coordination has
     * closed its emergency. The audit retains the original incident number.
     */
    public PanicReportView discard(long reportId, UserSession session) {
        ValidationUtil.requireNonNull(session, "sesión");

        Discarded discarded = transactions.callInTransaction(() -> {
            reportDao.lockValidation(systemSession(LocalDateTime.now(clock)).user().getId());
            PanicReport report = reportDao.findById(reportId)
                    .orElseThrow(() -> new NotFoundException("Reporte de pánico", reportId));

            if (report.getStatus() == PanicReportStatus.VALIDATED) {
                long emergencyId = report.getEmergencyId();
                if (emergencyService.isActive(emergencyService.findById(emergencyId))) {
                    throw new BusinessRuleException("Cierre la emergencia antes de marcar el reporte como falso.");
                }
                reportDao.updateStatus(reportId, PanicReportStatus.DISCARDED);
                auditService.record(session.user().getId(), AUDIT_MODULE, "DESCARTAR", reportId,
                        AuditResult.SUCCESS, "Reporte falso de la emergencia " + emergencyId);
                report.setStatus(PanicReportStatus.DISCARDED);
                report.setEmergencyId(null);
                return new Discarded(viewOf(report),
                        report.getUserId() == null ? List.of() : List.of(report.getUserId()));
            }
            if (report.getStatus() != PanicReportStatus.PENDING) {
                throw new BusinessRuleException(
                        "No se puede descartar un reporte en estado " + report.getStatus().code() + ".");
            }

            List<PanicReport> alert = near(
                    report.getLocation(),
                    reportDao.findPendingSince(report.getReportedAt().minus(WINDOW)));

            for (PanicReport pending : alert) {
                reportDao.updateStatus(pending.getId(), PanicReportStatus.DISCARDED);
            }

            auditService.record(
                    session.user().getId(), AUDIT_MODULE, "DESCARTAR", reportId,
                    AuditResult.SUCCESS, alert.size() + " reportes descartados");

            report.setStatus(PanicReportStatus.DISCARDED);
            List<Long> people = alert.stream()
                    .map(PanicReport::getUserId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            return new Discarded(viewOf(report), people);
        });

        blockRepeatedFalseAlarms(discarded.people(), session);
        return discarded.view();
    }

    /**
     * RN-16: a citizen with 2 reports discarded as false in 30 days is blocked. Done after the
     * discard is saved, so a problem blocking an account never undoes the discard.
     */
    private void blockRepeatedFalseAlarms(List<Long> people, UserSession by) {
        LocalDateTime since = LocalDateTime.now(clock).minus(CitizenService.FALSE_ALARM_WINDOW);

        for (long userId : people) {
            int falseAlarms = reportDao.countByUserAndStatusSince(userId, PanicReportStatus.DISCARDED, since);

            if (falseAlarms >= CitizenService.FALSE_ALARMS_TO_BLOCK) {
                try {
                    citizenService.blockForFalseAlarms(userId, falseAlarms, by);
                } catch (RuntimeException e) {
                    LOG.log(Level.WARNING, "Could not block user " + userId + " for false alarms", e);
                }
            }
        }
    }

    // ------------------------------------------------------------------ rules

    private PanicReportView viewOf(PanicReport report) {
        return PanicReportView.of(report, report.getUserId() != null && isPolice(report.getUserId()));
    }

    private boolean isPolice(long userId) {
        return userDao.findById(userId).flatMap(user -> roleDao.findById(user.getRoleId()))
                .map(role -> "POLICIA".equalsIgnoreCase(role.getName())).orElse(false);
    }

    /** RN-13: only this is saved, never the id the browser sent (64 hexadecimal characters). */
    public static String hashDevice(String deviceId) {
        ValidationUtil.requireText(deviceId, "identificador del dispositivo", 100);

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(deviceId.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every Java runtime", e);
        }
    }

    /** The reports within RADIUS_METERS of the point. */
    static List<PanicReport> near(GeoPoint point, List<PanicReport> reports) {
        return reports.stream()
                .filter(report -> metersBetween(point, report.getLocation()) <= RADIUS_METERS)
                .toList();
    }

    // ------------------------------------------------------------------ helpers

    /** The emergency of a validated report near the point that is still open, if any. */
    private Optional<Long> openEmergencyNear(GeoPoint point, LocalDateTime since, long typeId) {
        return near(point, reportDao.findSince(since)).stream()
                .filter(report -> report.getStatus() == PanicReportStatus.VALIDATED)
                .map(PanicReport::getEmergencyId)
                .distinct()
                .filter(id -> {
                    Emergency emergency = emergencyService.findById(id);
                    return emergency.getEmergencyTypeId() == typeId && emergencyService.isActive(emergency);
                })
                .findFirst();
    }

    /** RN-15: the emergency of a validated alert. */
    private EmergencyRequest toEmergency(PanicReport report, long typeId) {
        GeoPoint where = report.getLocation();

        String address = String.format(
                Locale.ROOT, "Botón de pánico cerca de %.5f, %.5f",
                where.latitude(), where.longitude());

        return new EmergencyRequest(typeId, address, where, Severity.HIGH, report.getDescription());
    }

    private long defaultType() {
        List<EmergencyType> types = emergencyTypeDao.findAll();

        return types.stream()
                .filter(type -> type.getName().equalsIgnoreCase(DEFAULT_TYPE))
                .findFirst()
                .or(() -> types.stream().findFirst())
                .orElseThrow(() -> new BusinessRuleException(
                        "No hay tipos de emergencia registrados."))
                .getId();
    }

    /**
     * RN-15: sends the fastest suitable unit on its own and tells the coordinators. If there
     * is none, or it could not be sent, the emergency stays REPORTADA for a manual dispatch.
     */
    private UnitCandidate dispatchOnItsOwn(long emergencyId, UserSession system) {
        UnitCandidate dispatched = null;
        try {
            for (UnitCandidate candidate : dispatchService.gpsCandidates(emergencyId, this::eligibleLocation)) {
                try {
                    dispatchService.dispatchGps(emergencyId, candidate.vehicleId(), system, this::eligibleLocation);
                    gps.clearRouteWarning(candidate.vehicleId());
                    dispatched = candidate;
                    break;
                } catch (BusinessRuleException unavailable) {
                    // Oracle locks the vehicle on assignment. A simultaneous dispatch may win that lock.
                    LOG.log(Level.FINE, "GPS candidate no longer available", unavailable);
                }
            }
        } catch (RuntimeException error) {
            LOG.log(Level.WARNING, "Automatic panic dispatch failed; the validated emergency remains registered", error);
        }
        String prefix = "Botón de pánico: emergencia " + emergencyId + ". ";
        String message = dispatched == null
                ? "Sin ambulancias compatibles con GPS activo y ruta disponible. Pendiente de asignación."
                : "Ambulancia " + dispatched.unitCode() + " asignada automáticamente.";
        try {
            alertService.panicAlert(emergencyId, prefix + message);
        } catch (RuntimeException error) {
            LOG.log(Level.WARNING, "Could not persist the coordination notification", error);
        }
        return dispatched;
    }

    private Optional<GeoPoint> eligibleLocation(EmergencyVehicle vehicle) {
        if (vehicle.getUnitCode().regionMatches(true, 0, "EJEMPLO-", 0, 8)) return Optional.empty();
        var driverId = drivers.findUserIdByVehicle(vehicle.getId());
        if (driverId.isEmpty()) return Optional.empty();
        return userDao.findById(driverId.getAsLong())
                .filter(user -> user.getStatus() == RecordStatus.ACTIVE)
                .flatMap(user -> roleDao.findById(user.getRoleId()))
                .filter(role -> role.getStatus() == RecordStatus.ACTIVE)
                .filter(role -> gpsPermission(role, "REPORTAR") && gpsPermission(role, "OPERAR"))
                .flatMap(role -> gps.freshLocation(vehicle.getId(), driverId.getAsLong(), maxGpsAge))
                .filter(routeService::isWithinNetwork);
    }

    private static boolean gpsPermission(Role role, String action) {
        return role.getPermissions().stream().anyMatch(permission ->
                "GPS".equalsIgnoreCase(permission.getModule()) && action.equalsIgnoreCase(permission.getAction()));
    }

    /** The session of sistema.rutavida, the user of what the button does on its own. */
    private UserSession systemSession(LocalDateTime now) {
        User system = userDao.findByUsername(SYSTEM_USERNAME)
                .orElseThrow(() -> new BusinessRuleException(
                        "Falta el usuario del sistema: ejecute 07_boton_panico.sql."));

        Role role = roleDao.findById(system.getRoleId())
                .orElseThrow(() -> new BusinessRuleException(
                        "El usuario del sistema no tiene rol."));

        return new UserSession(system, role, now);
    }

    private static double metersBetween(GeoPoint a, GeoPoint b) {
        return GeoUtil.metersBetween(
                a.latitude(), a.longitude(), b.latitude(), b.longitude());
    }

    /** What the transaction of a discard leaves: the report and the people whose reports were discarded. */
    private record Discarded(PanicReportView view, List<Long> people) {
    }

    /** What happened inside the transaction of a report. */
    private record Validation(long reportId, long emergencyId, boolean created) {
    }
}
