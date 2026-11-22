package co.edu.unicesar.rutavida.dto;

import co.edu.unicesar.rutavida.model.RoadIncident;
import co.edu.unicesar.rutavida.model.enums.IncidentOrigin;
import co.edu.unicesar.rutavida.model.enums.IncidentStatus;
import co.edu.unicesar.rutavida.model.enums.IncidentType;
import co.edu.unicesar.rutavida.model.enums.Severity;

import java.time.LocalDateTime;

/**
 * A road incident as the web receives it (docs/api.md 9.1): the fields of RoadIncident plus
 * the segment where it is, so the map can draw it. {@code segment} is null only if the
 * segment is no longer in TRAMO_VIAL.
 */
public record IncidentView(
        long id,
        long segmentId,
        IncidentType type,
        Severity severity,
        boolean blocksSegment,
        String description,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        IncidentStatus status,
        IncidentOrigin origin,
        SegmentView segment
) {

    public static IncidentView of(RoadIncident incident, SegmentView segment) {
        return new IncidentView(incident.getId(), incident.getSegmentId(), incident.getType(),
                incident.getSeverity(), incident.isBlocksSegment(), incident.getDescription(),
                incident.getStartedAt(), incident.getEndedAt(), incident.getStatus(), incident.getOrigin(),
                segment);
    }
}