
package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.enums.IncidentOrigin;
import co.edu.unicesar.rutavida.model.enums.IncidentStatus;
import co.edu.unicesar.rutavida.model.enums.IncidentType;
import co.edu.unicesar.rutavida.model.enums.Severity;
import co.edu.unicesar.rutavida.util.ValidationUtil;

import java.time.LocalDateTime;

/**
 * An accident, roadwork or closure on a road segment (INCIDENTE_VIAL).
 *
 * <p>Life cycle (RN-05): it starts PENDIENTE and does not affect routes.
 * The operator activates it (ACTIVO, now it affects routes) or rejects
 * it (RECHAZADO). An active incident ends when it is closed (CERRADO).
 * RECHAZADO and CERRADO are final and have an end date.
 */
public class RoadIncident {

    private long id;
    private final long segmentId;
    private final IncidentType type;
    private final Severity severity;
    private boolean blocksSegment;
    private final String description;
    private final LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private IncidentStatus status;
    private final IncidentOrigin origin;

    public RoadIncident(long id, long segmentId, IncidentType type,
                        Severity severity, boolean blocksSegment,
                        String description, LocalDateTime startedAt,
                        LocalDateTime endedAt, IncidentStatus status,
                        IncidentOrigin origin) {

        if (id < 0) {
            throw new ValidationException(
                    "El identificador no puede ser negativo.");
        }

        this.id = id;
        this.segmentId = ValidationUtil.requirePositiveId(
                segmentId, "tramo");
        this.type = ValidationUtil.requireNonNull(
                type, "tipo de incidente");
        this.severity = ValidationUtil.requireNonNull(
                severity, "gravedad");
        this.blocksSegment = blocksSegment;
        this.description = ValidationUtil.optionalText(
                description, "descripción", 300);
        this.startedAt = ValidationUtil.requireNonNull(
                startedAt, "fecha de inicio");
        this.status = ValidationUtil.requireNonNull(
                status, "estado");
        this.origin = ValidationUtil.requireNonNull(
                origin, "origen");

        if (id == 0 && status != IncidentStatus.PENDING) {
            throw new ValidationException(
                    "Un incidente nuevo debe iniciar en estado PENDIENTE.");
        }

        validateEnd(status, endedAt);
        this.endedAt = endedAt;
    }

    /**
     * RECHAZADO and CERRADO need an end date;
     * PENDIENTE and ACTIVO cannot have one.
     */
    private void validateEnd(IncidentStatus state, LocalDateTime end) {
        boolean finished = state == IncidentStatus.CLOSED
                || state == IncidentStatus.REJECTED;

        if (finished) {
            if (end == null) {
                throw new ValidationException(
                        "La fecha de fin es obligatoria.");
            }

            if (end.isBefore(startedAt)) {
                throw new ValidationException(
                        "La fecha de fin no puede ser anterior al inicio.");
            }
        } else if (end != null) {
            throw new ValidationException(
                    "Solo un incidente cerrado o rechazado puede tener fecha de fin.");
        }
    }

    /** Assigns an identifier only to an unsaved incident. */
    public void assignId(long newId) {
        if (id != 0) {
            throw new IllegalStateException(
                    "The entity already has id " + id);
        }

        id = ValidationUtil.requirePositiveId(
                newId, "identificador");
    }

    /**
     * CU-14: the operator validates the incident
     * and says whether it closes the segment.
     */
    public void activate(boolean blocks) {
        requireStatus(IncidentStatus.PENDING, "activar");
        this.blocksSegment = blocks;
        this.status = IncidentStatus.ACTIVE;
    }

    /** CU-14, alternative flow: the report was not valid. */
    public void reject(LocalDateTime at) {
        requireStatus(IncidentStatus.PENDING, "rechazar");
        validateEnd(IncidentStatus.REJECTED, at);

        this.status = IncidentStatus.REJECTED;
        this.endedAt = at;
    }

    /** The incident is over: the segment goes back to normal. */
    public void close(LocalDateTime at) {
        requireStatus(IncidentStatus.ACTIVE, "cerrar");
        validateEnd(IncidentStatus.CLOSED, at);

        this.status = IncidentStatus.CLOSED;
        this.endedAt = at;
    }

    private void requireStatus(IncidentStatus expected, String action) {
        if (status != expected) {
            throw new BusinessRuleException(
                    "Solo se puede " + action + " un incidente "
                            + expected.code() + "; este está "
                            + status.code() + ".");
        }
    }

    /** RN-05: only ACTIVO incidents change the cost of a segment. */
    public boolean affectsRouting() {
        return status.affectsRouting();
    }

    /** True when the routing engine must treat the segment as closed. */
    public boolean blocksTraffic() {
        return affectsRouting() && blocksSegment;
    }

    public long getId() {
        return id;
    }

    public long getSegmentId() {
        return segmentId;
    }

    public IncidentType getType() {
        return type;
    }

    public Severity getSeverity() {
        return severity;
    }

    public boolean isBlocksSegment() {
        return blocksSegment;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public IncidentOrigin getOrigin() {
        return origin;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        return other instanceof RoadIncident that
                && id != 0
                && id == that.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "RoadIncident{id=" + id
                + ", segmentId=" + segmentId
                + ", type=" + type
                + ", status=" + status + "}";
    }
}
