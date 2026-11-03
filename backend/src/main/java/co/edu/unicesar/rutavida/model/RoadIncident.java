package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.model.enums.IncidentOrigin;
import co.edu.unicesar.rutavida.model.enums.IncidentStatus;
import co.edu.unicesar.rutavida.model.enums.IncidentType;
import co.edu.unicesar.rutavida.model.enums.Severity;
import java.time.LocalDateTime;

/**
 * An accident, roadwork or closure on a road segment (table INCIDENTE_VIAL).
 * Only data: the business rules live in the service layer.
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

    public RoadIncident(long id,
            long segmentId,
            IncidentType type,
            Severity severity,
            boolean blocksSegment,
            String description,
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            IncidentStatus status,
            IncidentOrigin origin) {
        this.id = id;
        this.segmentId = segmentId;
        this.type = type;
        this.severity = severity;
        this.blocksSegment = blocksSegment;
        this.description = description;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.status = status;
        this.origin = origin;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
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

    public void setBlocksSegment(boolean blocksSegment) {
        this.blocksSegment = blocksSegment;
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

    public void setEndedAt(LocalDateTime endedAt) {
        this.endedAt = endedAt;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public void setStatus(IncidentStatus status) {
        this.status = status;
    }

    public IncidentOrigin getOrigin() {
        return origin;
    }
}
