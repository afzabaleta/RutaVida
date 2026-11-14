
package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.EmergencyTypeDAO;
import co.edu.unicesar.rutavida.dao.PanicReportDAO;
import co.edu.unicesar.rutavida.dao.RoleDAO;
import co.edu.unicesar.rutavida.dao.TransactionManager;
import co.edu.unicesar.rutavida.dao.UserDAO;
import co.edu.unicesar.rutavida.dto.EmergencyRequest;
import co.edu.unicesar.rutavida.dto.PanicAlertRequest;
import co.edu.unicesar.rutavida.dto.PanicReportView;
import co.edu.unicesar.rutavida.dto.PanicResult;
import co.edu.unicesar.rutavida.dto.UnitCandidate;
import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.Emergency;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Panic button (CU-22, rules RN-13 to RN-15, DT-12).
 *
 * <p>Anyone can press it without an account. A report starts PENDIENTE; the alert is
 * validated when 3 distinct devices report within 300 m in 10 minutes, or at once when the
 * reporter has PANICO/VALIDAR (police). A validated alert becomes a REPORTADA emergency in
 * the name of the system user, and the fastest available unit is sent on its own with the
 * same DispatchService of CU-09.
 *
 * <p>The values (3 devices, 300 m, 10 minutes) are initial parameters to calibrate (DT-11).
 * The controller checks the permissions of listing and discarding; reporting needs none.
 */
public class PanicService {

    private static final Logger LOG = Logger.getLogger(PanicService.class.getName());

    static final String AUDIT_MODULE = "PANICO";
    static final String VALIDATE = "VALIDAR";

    /** Created by 07_boton_panico.sql: inactive, nobody can log in with it. */
    static final String SYSTEM_USERNAME = "sistema.rutavida";

    /** RN-14: distinct devices that validate an alert, how close and how recent. */
    public static final int REPORTS_NEEDED = 3;
    static final double RADIUS_METERS = 300;
    static final Duration WINDOW = Duration.ofMinutes(10);

    /** RN-13: reports one device can send in the window. */
    static final int MAX_REPORTS_PER_DEVICE = 3;

    /** What the panel shows (docs/api.md 16.2). */
    static final Duration RECENT = Duration.ofHours(2);

    /** Type of the emergency when nobody chose one: an ambulance is sent. */
    static final String DEFAULT_TYPE = "EMERGENCIA_MEDICA";

    static final int MAX_DESCRIPTION = 200;
    private static final int MAX_EMERGENCY_DESCRIPTION = 400;

    private final PanicReportDAO reportDao;
    private final EmergencyTypeDAO emergencyTypeDao;
    private final UserDAO userDao;
    private final RoleDAO roleDao;
    private final EmergencyService emergencyService;
    private final DispatchService dispatchService;
    private final RouteService routeService;
    private final AlertService alertService;
    private final AuditService auditService;
    private final TransactionManager transactions;
    private final Clock clock;

    public PanicService(PanicReportDAO reportDao, EmergencyTypeDAO emergencyTypeDao,
                        UserDAO userDao, RoleDAO roleDao,
                        EmergencyService emergencyService, DispatchService dispatchService,
                        RouteService routeService, AlertService alertService,
                        AuditService auditService, TransactionManager transactions, Clock clock) {
        this.reportDao = Objects.requireNonNull(reportDao, "reportDao");
        this.emergencyTypeDao = Objects.requireNonNull(emergencyTypeDao, "emergencyTypeDao");
        this.userDao = Objects.requireNonNull(userDao, "userDao");
        this.roleDao = Objects.requireNonNull(roleDao, "roleDao");
        this.emergencyService = Objects.requireNonNull(emergencyService, "emergencyService");
        this.dispatchService = Objects.requireNonNull(dispatchService, "dispatchService");
        this.routeService = Objects.requireNonNull(routeService, "routeService");
        this.alertService = Objects.requireNonNull(alertService, "alertService");
        this.auditService = Objects.requireNonNull(auditService, "auditService");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    // ------------------------------------------------------------------ CU-22: press the button

    /**
     * Saves the report and validates the alert when the rules say so (RN-13 to RN-15).
     *
     * @param reporter the session of who pressed it; null for an anonymous citizen
     */
    public PanicResult report(PanicAlertRequest request, UserSession reporter) {
        ValidationUtil.requireNonNull(request, "reporte");
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
        boolean trusted = canValidateAlone(reporter);
        Long userId = reporter == null ? null : reporter.user().getId();

        Validation validation = transactions.callInTransaction(() -> {
            reportDao.expirePendingBefore(since);

            if (reportDao.countByDeviceSince(device, since) >= MAX_REPORTS_PER_DEVICE) {
                throw new BusinessRuleException(
                        "Ya envió varios reportes. Si la emergencia sigue, llame al 123.");
            }

            PanicReport report = new PanicReport(
                    0, location, now, device, userId, typeId, description,
                    PanicReportStatus.PENDING, null);
            reportDao.insert(report);

            // Someone near an alert that is already being attended: join it, do not create another.
            Optional<Long> open = openEmergencyNear(location, since);
            if (open.isPresent()) {
                reportDao.markValidated(List.of(report.getId()), open.get());
                return new Validation(report.getId(), open.get(), false, REPORTS_NEEDED);
            }

            List<PanicReport> cluster = near(location, reportDao.findPendingSince(since));
            int devices = distinctDevices(cluster);

            if (!trusted && devices < REPORTS_NEEDED) {
                return new Validation(report.getId(), null, false, devices);
            }

            UserSession system = systemSession(now);
            Emergency emergency = emergencyService.register(
                    toEmergency(cluster, devices, trusted), system);

            List<Long> ids = cluster.stream().map(PanicReport::getId).toList();
            reportDao.markValidated(ids, emergency.getId());

            auditService.record(
                    system.user().getId(), AUDIT_MODULE, VALIDATE, emergency.getId(),
                    AuditResult.SUCCESS,
                    (trusted ? "Validada por un policía. " : "")
                            + devices + " dispositivos, reportes " + ids);

            return new Validation(report.getId(), emergency.getId(), true, devices);
        });

        String unit = validation.created()
                ? dispatchOnItsOwn(validation.emergencyId(), now) : null;

        PanicReportStatus status = validation.emergencyId() == null
                ? PanicReportStatus.PENDING : PanicReportStatus.VALIDATED;

        return new PanicResult(
                validation.reportId(), status, validation.emergencyId(), unit,
                validation.devices(), REPORTS_NEEDED);
    }

    // ------------------------------------------------------------------ panel

    /** Reports of the last 2 hours, newest first; all of them when {@code status} is null. */
    public List<PanicReportView> listRecent(PanicReportStatus status) {
        LocalDateTime now = LocalDateTime.now(clock);
        reportDao.expirePendingBefore(now.minus(WINDOW));

        return reportDao.findSince(now.minus(RECENT)).stream()
                .filter(report -> status == null || report.getStatus() == status)
                .map(PanicReportView::of)
                .toList();
    }

    /**
     * A coordinator marks a pending alert as false (CU-22, 7a): the report and the other
     * PENDIENTE reports near it in the window become DESCARTADO, so one more press does not
     * validate it. A validated alert is not discarded here: its emergency is closed as a
     * false alarm (CU-12).
     */
    public PanicReportView discard(long reportId, UserSession session) {
        ValidationUtil.requireNonNull(session, "sesión");

        return transactions.callInTransaction(() -> {
            PanicReport report = reportDao.findById(reportId)
                    .orElseThrow(() -> new NotFoundException("Reporte de pánico", reportId));

            if (report.getStatus() != PanicReportStatus.PENDING) {
                throw new BusinessRuleException(
                        "Solo se descarta un reporte PENDIENTE; este está "
                                + report.getStatus().code()
                                + ". Si la alerta ya creó una emergencia, "
                                + "ciérrela como falsa alarma.");
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
            return PanicReportView.of(report);
        });
    }

    // ------------------------------------------------------------------ rules

    /** RN-14: an active user whose active role has PANICO/VALIDAR validates the alert alone. */
    public static boolean canValidateAlone(UserSession session) {
        return session != null
                && session.user().getStatus() == RecordStatus.ACTIVE
                && session.role().getStatus() == RecordStatus.ACTIVE
                && session.role().getPermissions().stream().anyMatch(permission ->
                permission.getModule().equalsIgnoreCase(AUDIT_MODULE)
                        && permission.getAction().equalsIgnoreCase(VALIDATE));
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

    /** How many different devices sent these reports. */
    static int distinctDevices(List<PanicReport> reports) {
        return (int) reports.stream()
                .map(PanicReport::getDeviceId)
                .distinct()
                .count();
    }

    /** Midpoint of the reports: where the emergency is placed (RN-15). */
    static GeoPoint center(List<PanicReport> reports) {
        double latitude = reports.stream()
                .mapToDouble(r -> r.getLocation().latitude())
                .average().orElseThrow();

        double longitude = reports.stream()
                .mapToDouble(r -> r.getLocation().longitude())
                .average().orElseThrow();

        return new GeoPoint(latitude, longitude);
    }

    // ------------------------------------------------------------------ helpers

    /** The emergency of a validated report near the point that is still open, if any. */
    private Optional<Long> openEmergencyNear(GeoPoint point, LocalDateTime since) {
        return near(point, reportDao.findSince(since)).stream()
                .filter(report -> report.getStatus() == PanicReportStatus.VALIDATED)
                .map(PanicReport::getEmergencyId)
                .filter(id -> emergencyService.isActive(emergencyService.findById(id)))
                .findFirst();
    }

    /** RN-15: the emergency of a validated alert. */
    private EmergencyRequest toEmergency(List<PanicReport> cluster, int devices, boolean trusted) {
        GeoPoint where = center(cluster);

        String address = String.format(
                Locale.ROOT, "Botón de pánico cerca de %.5f, %.5f",
                where.latitude(), where.longitude());

        String said = cluster.stream()
                .map(PanicReport::getDescription)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.joining(" | "));

        String description = (trusted
                ? "Alerta de pánico validada por un policía."
                : "Alerta de pánico validada por " + devices + " personas cercanas.")
                + (said.isEmpty() ? "" : " Dicen: " + said);

        if (description.length() > MAX_EMERGENCY_DESCRIPTION) {
            description = description.substring(0, MAX_EMERGENCY_DESCRIPTION);
        }

        return new EmergencyRequest(
                typeOf(cluster), address, where, Severity.HIGH, description);
    }

    /** The type most people chose (the newest one on a tie); EMERGENCIA_MEDICA if nobody chose. */
    private long typeOf(List<PanicReport> cluster) {
        Map<Long, Integer> votes = new HashMap<>();
        Long chosen = null;

        for (PanicReport report : cluster.stream()
                .sorted(Comparator.comparing(PanicReport::getReportedAt).reversed()).toList()) {
            Long type = report.getEmergencyTypeId();

            if (type == null) {
                continue;
            }

            int count = votes.merge(type, 1, Integer::sum);

            if (chosen == null || count > votes.get(chosen)) {
                chosen = type;
            }
        }

        if (chosen != null) {
            return chosen;
        }

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
    private String dispatchOnItsOwn(long emergencyId, LocalDateTime now) {
        String unit = null;
        String message;

        try {
            List<UnitCandidate> candidates = dispatchService.candidates(emergencyId);

            if (candidates.isEmpty()) {
                message = "Botón de pánico: se creó la emergencia " + emergencyId
                        + ". No hay unidades disponibles: despache a mano.";
            } else {
                UnitCandidate best = candidates.get(0);

                dispatchService.dispatch(emergencyId, best.vehicleId(), systemSession(now));
                unit = best.unitCode();

                message = "Botón de pánico: se creó la emergencia " + emergencyId
                        + " y se despachó " + unit
                        + String.format(Locale.ROOT, " (llega en %.0f min).",
                        best.etaMinutes());
            }
        } catch (BusinessRuleException e) {
            message = "Botón de pánico: se creó la emergencia " + emergencyId
                    + ", pero no se pudo despachar sola: " + e.getMessage();

            LOG.log(Level.INFO, "Panic alert left for a manual dispatch", e);
        }

        alertService.panicAlert(emergencyId, message);
        return unit;
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

    /** What happened inside the transaction of a report. */
    private record Validation(long reportId, Long emergencyId, boolean created, int devices) {
    }
}
