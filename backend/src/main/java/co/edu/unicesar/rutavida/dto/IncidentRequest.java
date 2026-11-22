package co.edu.unicesar.rutavida.dto;

import co.edu.unicesar.rutavida.model.enums.IncidentType;
import co.edu.unicesar.rutavida.model.enums.Severity;

/**
 * What the operator sends to report a road incident (CU-13, docs/api.md 9.2).
 * Only data: IncidentService checks it.
 *
 * @param segmentId road segment (TRAMO_VIAL) where the incident is
 * @param type accident, roadwork, closure...
 * @param severity how serious it is
 * @param blocksSegment true if the operator says the street cannot be used
 * @param description optional details
 */
public record IncidentRequest(
        long segmentId,
        IncidentType type,
        Severity severity,
        boolean blocksSegment,
        String description
) {
}