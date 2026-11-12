package co.edu.unicesar.rutavida.dto;

import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.enums.RouteStatus;
import co.edu.unicesar.rutavida.model.enums.RouteType;

import java.util.List;
import java.util.Objects;

/**
 * A route saved in Oracle for a mission, ready for the map (docs/api.md 6.3 and 6.4): the
 * fields of RouteOption plus the id, the status and the destination of the RUTA row. The
 * numbers and the explanation are those of the moment the route was calculated.
 *
 * @param routeId               Oracle id (RUTA.id_ruta)
 * @param status                ACTIVA (the unit follows it) or PROPUESTA (alternative)
 * @param recommended           RUTA.es_recomendada
 * @param destinationHospitalId hospital of a transfer; null when the route goes to the emergency
 * @param rank                  1 = recommended, then the alternatives from the fastest
 * @param type                  PRINCIPAL or ALTERNATIVA
 * @param path                  points to draw on the map, in travel order
 * @param segmentIds            Oracle ids of the segments (RUTA_TRAMO), in travel order
 * @param distanceKm            total length
 * @param estimatedMinutes      estimated travel time when the route was calculated
 * @param trafficLights         segments that ended in a traffic light with waiting time
 * @param congestedSegments     segments that were congested
 * @param explanation           summary in Spanish
 */
public record MissionRoute(long routeId, RouteStatus status, boolean recommended, Long destinationHospitalId,
                           int rank, RouteType type, List<GeoPoint> path, List<Long> segmentIds,
                           double distanceKm, double estimatedMinutes, int trafficLights,
                           int congestedSegments, String explanation) {

    public MissionRoute {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(type, "type");
        if (rank < 1) {
            throw new IllegalArgumentException("rank must be >= 1");
        }
        path = List.copyOf(path);
        segmentIds = List.copyOf(segmentIds);
    }
}