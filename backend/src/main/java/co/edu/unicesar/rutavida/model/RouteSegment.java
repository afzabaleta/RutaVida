
package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.util.ValidationUtil;

/**
 * One step of a calculated route (RUTA_TRAMO): the road segment at position
 * {@code order} (1, 2, 3...). It belongs to its Route and has no id of its
 * own, so the DAO writes the id of the route. The three estimates are
 * optional, like their columns.
 */
public record RouteSegment(
        int order,
        long segmentId,
        Double estimatedSeconds,
        Double congestionFactor,
        Integer trafficLightCount) {

    public RouteSegment {
        if (order < 1 || order > 99_999) {
            throw new ValidationException(
                    "El orden del tramo debe estar entre 1 y 99999.");
        }

        ValidationUtil.requirePositiveId(segmentId, "tramo");

        if (estimatedSeconds != null) {
            ValidationUtil.requireInRange(
                    estimatedSeconds, 0, 99_999_999.99, "tiempo estimado");
        }

        if (congestionFactor != null) {
            ValidationUtil.requireInRange(
                    congestionFactor, 1, 999.99, "factor de congestión");
        }

        if (trafficLightCount != null
                && (trafficLightCount < 0 || trafficLightCount > 999)) {
            throw new ValidationException(
                    "La cantidad de semáforos debe estar entre 0 y 999.");
        }
    }
}
