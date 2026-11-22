package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.RoadIncidentDAO;
import co.edu.unicesar.rutavida.dao.RoadSegmentDAO;
import co.edu.unicesar.rutavida.dao.TransactionManager;
import co.edu.unicesar.rutavida.dto.IncidentRequest;
import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.RoadIncident;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.enums.AuditResult;
import co.edu.unicesar.rutavida.model.enums.IncidentOrigin;
import co.edu.unicesar.rutavida.model.enums.IncidentStatus;
import co.edu.unicesar.rutavida.util.ValidationUtil;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Road incidents (CU-13, CU-14, rule RN-05): an incident starts PENDIENTE,
 * the operator activates or rejects it, and only ACTIVO incidents affect routes.
 * An active incident is closed when it is over.
 *
 * Permissions are checked by the controller before calling this service.
 * Reporting is audited here; every later status change is audited by the
 * database trigger trg_incidente_estado.
 *
 * This service only saves the incident. Applying it to the route graph and
 * checking active routes is part of K7.
 */
public class IncidentService {

    static final String AUDIT_MODULE = "INCIDENTES";

    private final RoadIncidentDAO incidentDao;
    private final RoadSegmentDAO segmentDao;
    private final AuditService auditService;
    private final TransactionManager transactions;
    private final Clock clock;

    public IncidentService(RoadIncidentDAO incidentDao, RoadSegmentDAO segmentDao,
                           AuditService auditService, TransactionManager transactions,
                           Clock clock) {
        this.incidentDao = Objects.requireNonNull(incidentDao, "incidentDao");
        this.segmentDao = Objects.requireNonNull(segmentDao, "segmentDao");
        this.auditService = Objects.requireNonNull(auditService, "auditService");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    // ------------------------------------------------------------------
    // Use cases

    /**
     * CU-13: saves a new incident as PENDIENTE and audits it in one transaction.
     * It does not change any route until someone activates it (RN-05).
     */
    public RoadIncident report(IncidentRequest request, IncidentOrigin origin,
                               UserSession session) {
        ValidationUtil.requireNonNull(request, "incidente");
        ValidationUtil.requireNonNull(origin, "origen");
        ValidationUtil.requireNonNull(session, "sesión");

        long segmentId = ValidationUtil.requirePositiveId(request.segmentId(), "tramo");

        if (segmentDao.findById(segmentId).isEmpty()) {
            throw new ValidationException("El tramo vial no existe.");
        }

        RoadIncident incident = new RoadIncident(
                0,
                segmentId,
                request.type(),
                request.severity(),
                request.blocksSegment(),
                ValidationUtil.optionalText(request.description(), "descripción", 300),
                LocalDateTime.now(clock),
                null,
                IncidentStatus.PENDING,
                origin
        );

        validateNew(incident);

        long userId = session.user().getId();

        transactions.runInTransaction(() -> {
            long id = incidentDao.insert(incident);
            auditService.record(userId, AUDIT_MODULE, "REGISTRAR",
                    id, AuditResult.SUCCESS, null);
        });

        return incident;
    }

    /**
     * CU-14: moves an incident to ACTIVO, RECHAZADO or CERRADO and saves it.
     */
    public RoadIncident changeStatus(long id, IncidentStatus next) {
        ValidationUtil.requireNonNull(next, "nuevo estado");

        return transactions.callInTransaction(() -> {
            RoadIncident incident = findById(id);
            LocalDateTime now = LocalDateTime.now(clock);

            switch (next) {
                case ACTIVE -> activate(incident, incident.isBlocksSegment());
                case REJECTED -> reject(incident, now);
                case CLOSED -> close(incident, now);
                default -> throw new ValidationException(
                        "El nuevo estado debe ser ACTIVO, RECHAZADO o CERRADO.");
            }

            incidentDao.update(incident);
            return incident;
        });
    }

    /** One incident by id. */
    public RoadIncident findById(long id) {
        return incidentDao.findById(id)
                .orElseThrow(() -> new NotFoundException("Incidente vial", id));
    }

    /** Incidents that are still open (PENDIENTE and ACTIVO), oldest first. */
    public List<RoadIncident> listOpen() {
        List<RoadIncident> open =
                new ArrayList<>(incidentDao.findByStatus(IncidentStatus.PENDING));

        open.addAll(incidentDao.findByStatus(IncidentStatus.ACTIVE));

        open.sort(Comparator.comparing(RoadIncident::getStartedAt)
                .thenComparing(RoadIncident::getId));

        return open;
    }

    /** Incidents in one status, oldest first. */
    public List<RoadIncident> listByStatus(IncidentStatus status) {
        ValidationUtil.requireNonNull(status, "estado");
        return incidentDao.findByStatus(status);
    }

    // ------------------------------------------------------------------
    // Rules

    /** Checks a new incident before saving it. */
    public void validateNew(RoadIncident incident) {
        ValidationUtil.requireNonNull(incident, "incidente");
        ValidationUtil.requirePositiveId(incident.getSegmentId(), "tramo");
        ValidationUtil.requireNonNull(incident.getType(), "tipo de incidente");
        ValidationUtil.requireNonNull(incident.getSeverity(), "gravedad");
        ValidationUtil.optionalText(incident.getDescription(), "descripción", 300);
        ValidationUtil.requireNonNull(incident.getStartedAt(), "fecha de inicio");
        ValidationUtil.requireNonNull(incident.getOrigin(), "origen");

        if (incident.getStatus() != IncidentStatus.PENDING) {
            throw new ValidationException(
                    "Un incidente nuevo debe iniciar en estado PENDIENTE.");
        }
    }

    /** CU-14: activates the incident and decides whether it closes the segment. */
    public void activate(RoadIncident incident, boolean blocksSegment) {
        requireStatus(incident, IncidentStatus.PENDING, "activar");
        incident.setBlocksSegment(blocksSegment);
        incident.setStatus(IncidentStatus.ACTIVE);
    }

    /** CU-14: alternative flow, the report was not valid. */
    public void reject(RoadIncident incident, LocalDateTime at) {
        requireStatus(incident, IncidentStatus.PENDING, "rechazar");
        finish(incident, IncidentStatus.REJECTED, at);
    }

    /** The incident is over: the segment goes back to normal. */
    public void close(RoadIncident incident, LocalDateTime at) {
        requireStatus(incident, IncidentStatus.ACTIVE, "cerrar");
        finish(incident, IncidentStatus.CLOSED, at);
    }

    /** RN-05: only ACTIVE incidents change the cost of a segment. */
    public boolean affectsRouting(RoadIncident incident) {
        return incident.getStatus() == IncidentStatus.ACTIVE;
    }

    /** True when the route engine must treat the segment as closed. */
    public boolean blocksTraffic(RoadIncident incident) {
        return affectsRouting(incident) && incident.isBlocksSegment();
    }

    private static void requireStatus(RoadIncident incident,
                                      IncidentStatus expected, String action) {
        if (incident.getStatus() != expected) {
            throw new BusinessRuleException(
                    "Solo se puede " + action + " un incidente "
                            + expected.code() + "; este está "
                            + incident.getStatus().code() + ".");
        }
    }

    private static void finish(RoadIncident incident, IncidentStatus status,
                               LocalDateTime at) {
        ValidationUtil.requireNonNull(at, "fecha de fin");

        if (at.isBefore(incident.getStartedAt())) {
            throw new ValidationException(
                    "La fecha de fin no puede ser anterior al inicio.");
        }

        incident.setStatus(status);
        incident.setEndedAt(at);
    }
}