package co.edu.unicesar.rutavida.model;

/**
 * One step of a calculated route (table RUTA_TRAMO): the road segment at position
 * {@code order} (1, 2, 3...). The three estimates are optional, like their columns.
 */
public record RouteSegment(
        int order,
        long segmentId,
        Double estimatedSeconds,
        Double congestionFactor,
        Integer trafficLightCount) {
}
