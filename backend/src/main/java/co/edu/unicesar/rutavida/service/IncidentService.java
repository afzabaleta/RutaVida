package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.RoadIncident;
import co.edu.unicesar.rutavida.model.enums.IncidentStatus;
import co.edu.unicesar.rutavida.util.ValidationUtil;

import java.time.LocalDateTime;

/**
 * Business rules of road incidents (CU-13, CU-14, rule RN-05): an incident starts
 * PENDIENTE, the operator activates or rejects it, and only ACTIVO incidents affect routes.
 */
public class IncidentService {

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
            throw new ValidationException("Un incidente nuevo debe iniciar en estado PENDIENTE.");
        }
    }

    /** CU-14: the operator validates the incident and says whether it closes the segment. */
    public void activate(RoadIncident incident, boolean blocksSegment) {
        requireStatus(incident, IncidentStatus.PENDING, "activar");
        incident.setBlocksSegment(blocksSegment);
        incident.setStatus(IncidentStatus.ACTIVE);
    }

    /** CU-14, alternative flow: the report was not valid. */
    public void reject(RoadIncident incident, LocalDateTime at) {
        requireStatus(incident, IncidentStatus.PENDING, "rechazar");
        finish(incident, IncidentStatus.REJECTED, at);
    }

    /** The incident is over: the segment goes back to normal. */
    public void close(RoadIncident incident, LocalDateTime at) {
        requireStatus(incident, IncidentStatus.ACTIVE, "cerrar");
        finish(incident, IncidentStatus.CLOSED, at);
    }

    /** RN-05: only ACTIVO incidents change the cost of a segment. */
    public boolean affectsRouting(RoadIncident incident) {
        return incident.getStatus() == IncidentStatus.ACTIVE;
    }

    /** True when the route engine must treat the segment as closed. */
    public boolean blocksTraffic(RoadIncident incident) {
        return affectsRouting(incident) && incident.isBlocksSegment();
    }

    private static void requireStatus(RoadIncident incident, IncidentStatus expected, String action) {
        if (incident.getStatus() != expected) {
            throw new BusinessRuleException("Solo se puede " + action + " un incidente "
                    + expected.code() + "; este está " + incident.getStatus().code() + ".");
        }
    }

    private static void finish(RoadIncident incident, IncidentStatus status, LocalDateTime at) {
        ValidationUtil.requireNonNull(at, "fecha de fin");
        if (at.isBefore(incident.getStartedAt())) {
            throw new ValidationException("La fecha de fin no puede ser anterior al inicio.");
        }
        incident.setStatus(status);
        incident.setEndedAt(at);
    }
}
