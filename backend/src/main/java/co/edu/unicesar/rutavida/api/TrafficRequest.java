package co.edu.unicesar.rutavida.api;

import co.edu.unicesar.rutavida.model.enums.TrafficLevel;

/**
 * Body of POST /api/simulation/traffic (docs/api.md 10.3).
 *
 * @param segmentId the segment (TRAMO_VIAL.id_tramo)
 * @param level     FLUIDO, MODERADO or CONGESTIONADO
 */
public record TrafficRequest(Long segmentId, TrafficLevel level) {
}
