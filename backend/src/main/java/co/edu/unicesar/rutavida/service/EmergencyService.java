
package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.EmergencyAssignmentDAO;
import co.edu.unicesar.rutavida.dao.EmergencyDAO;
import co.edu.unicesar.rutavida.dao.EmergencyTypeDAO;
import co.edu.unicesar.rutavida.dao.RouteDAO;
import co.edu.unicesar.rutavida.dao.TransactionManager;
import co.edu.unicesar.rutavida.dto.EmergencyHistory;
import co.edu.unicesar.rutavida.dto.EmergencyRequest;
import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.Emergency;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.enums.AuditResult;
import co.edu.unicesar.rutavida.model.enums.EmergencyStatus;
import co.edu.unicesar.rutavida.util.ValidationUtil;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Emergencies: registering one (CU-06), the active ones (CU-07), the history (CU-15) and
 * the rules of the emergency.
 * Permissions are checked by the controller before calling this service.
 */
public class EmergencyService {

    private static final Map<EmergencyStatus, Set<EmergencyStatus>> NEXT = Map.of(
            EmergencyStatus.REPORTED, EnumSet.of(EmergencyStatus.ASSIGNED, EmergencyStatus.CLOSED),
            EmergencyStatus.ASSIGNED, EnumSet.of(EmergencyStatus.EN_ROUTE, EmergencyStatus.REPORTED),
            EmergencyStatus.EN_ROUTE, EnumSet.of(EmergencyStatus.ON_SCENE, EmergencyStatus.REPORTED),
            EmergencyStatus.ON_SCENE, EnumSet.of(EmergencyStatus.TRANSPORTING, EmergencyStatus.CLOSED),
            EmergencyStatus.TRANSPORTING, EnumSet.of(EmergencyStatus.CLOSED),
            EmergencyStatus.CLOSED, EnumSet.noneOf(EmergencyStatus.class));

    static final String AUDIT_MODULE = "EMERGENCIAS";

    private final EmergencyDAO emergencyDao;    private final EmergencyTypeDAO emergencyTypeDao;
    private final EmergencyAssignmentDAO assignmentDao;
    private final RouteDAO routeDao;
    private final AuditService auditService;
    private final TransactionManager transactions;
    private final Clock clock;

    public EmergencyService(EmergencyDAO emergencyDao, EmergencyTypeDAO emergencyTypeDao,
                            EmergencyAssignmentDAO assignmentDao, RouteDAO routeDao,
                            AuditService auditService, TransactionManager transactions, Clock clock) {
        this.emergencyDao = Objects.requireNonNull(emergencyDao, "emergencyDao");
        this.emergencyTypeDao = Objects.requireNonNull(emergencyTypeDao, "emergencyTypeDao");
        this.assignmentDao = Objects.requireNonNull(assignmentDao, "assignmentDao");
        this.routeDao = Objects.requireNonNull(routeDao, "routeDao");
        this.auditService = Objects.requireNonNull(auditService, "auditService");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Registers a new emergency as REPORTED and audits it in one transaction (CU-06). */
    public Emergency register(EmergencyRequest request, UserSession session) {
        ValidationUtil.requireNonNull(request, "emergencia");
        ValidationUtil.requireNonNull(session, "sesión");

        long typeId = ValidationUtil.requirePositiveId(
                request.emergencyTypeId(), "tipo de emergencia");

        if (emergencyTypeDao.findById(typeId).isEmpty()) {
            throw new ValidationException("El tipo de emergencia no existe.");
        }

        long userId = session.user().getId();

        Emergency emergency = new Emergency(
                0,
                typeId,
                userId,
                ValidationUtil.requireText(request.addressReference(),
                        "dirección de referencia", 200),
                request.location(),
                LocalDateTime.now(clock),
                request.severity(),
                ValidationUtil.optionalText(request.description(), "descripción", 400),
                EmergencyStatus.REPORTED,
                null
        );

        validateNew(emergency);

        transactions.runInTransaction(() -> {
            long id = emergencyDao.insert(emergency);
            auditService.record(userId, AUDIT_MODULE, "REGISTRAR",
                    id, AuditResult.SUCCESS, null);
        });

        return emergency;
    }

    /** Emergencies that are not closed, newest first (CU-07). */
    public List<Emergency> listActive() {
        return emergencyDao.findActive();
    }

    /** Finds an emergency by ID. */
    public Emergency findById(long id) {
        return emergencyDao.findById(id)
                .orElseThrow(() -> new NotFoundException("Emergencia", id));
    }

    /** Everything that happened to one emergency (CU-15). */
    public EmergencyHistory getHistory(long id) {
        Emergency emergency = findById(id);

        return new EmergencyHistory(
                emergency,
                assignmentDao.findByEmergency(id),
                routeDao.findByEmergency(id),
                auditService.findByRecord(AUDIT_MODULE, id)
        );
    }

    /** Checks the data of an emergency before saving it (CU-06). */
    public void validateNew(Emergency emergency) {
        ValidationUtil.requireNonNull(emergency, "emergencia");
        ValidationUtil.requirePositiveId(emergency.getEmergencyTypeId(), "tipo de emergencia");
        ValidationUtil.requirePositiveId(emergency.getReportedByUserId(), "usuario que reporta");
        ValidationUtil.requireText(emergency.getAddressReference(), "dirección de referencia", 200);
        ValidationUtil.requireLocation(emergency.getLocation());
        ValidationUtil.requireNonNull(emergency.getReportedAt(), "fecha de reporte");
        ValidationUtil.requireNonNull(emergency.getSeverity(), "gravedad");
        ValidationUtil.optionalText(emergency.getDescription(), "descripción", 400);

        if (emergency.getStatus() != EmergencyStatus.REPORTED) {
            throw new ValidationException(
                    "Una emergencia nueva debe iniciar en estado REPORTADA.");
        }
    }

    public boolean canChange(EmergencyStatus from, EmergencyStatus to) {
        return from != null && to != null && NEXT.get(from).contains(to);
    }

    /** Changes the status; CLOSED also records the closing time. */
    public void changeStatus(Emergency emergency, EmergencyStatus next, LocalDateTime at) {
        ValidationUtil.requireNonNull(next, "nuevo estado");
        ValidationUtil.requireNonNull(at, "fecha del cambio");

        if (!canChange(emergency.getStatus(), next)) {
            throw new BusinessRuleException("No se puede cambiar la emergencia de "
                    + emergency.getStatus().code() + " a " + next.code() + ".");
        }

        if (next == EmergencyStatus.CLOSED && at.isBefore(emergency.getReportedAt())) {
            throw new ValidationException(
                    "La fecha de cierre no puede ser anterior al reporte.");
        }

        emergency.setStatus(next);
        emergency.setClosedAt(next == EmergencyStatus.CLOSED ? at : null);
    }

    /** An emergency is active until it is closed. */
    public boolean isActive(Emergency emergency) {
        return emergency.getStatus() != EmergencyStatus.CLOSED;
    }
}
