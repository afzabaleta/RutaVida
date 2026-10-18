package co.edu.unicesar.rutavida.dto;

import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.enums.RouteType;

import java.util.List;
import java.util.Objects;

/**
 * One of the (up to 3) routes shown to the coordinator, ready for the map and the panel:
 * the line to draw, the numbers to compare and the explanation in Spanish (CU-08).
 *
 * @param rank              1 = recommended, 2 and 3 = alternatives
 * @param type              MAIN for rank 1, ALTERNATIVE for the rest
 * @param path              points to draw on the map, in travel order
 * @param segmentIds        Oracle ids of the segments (TRAMO_VIAL.id_tramo), in travel order
 * @param distanceKm        total length
 * @param estimatedMinutes  estimated travel time with the traffic at calculation time
 * @param trafficLights     segments that end in a traffic light with waiting time
 * @param congestedSegments segments with congestion at or above the congested threshold
 * @param explanation       why this route, in Spanish
 */
public record RouteOption(int rank, RouteType type, List<GeoPoint> path, List<Long> segmentIds,
                          double distanceKm, double estimatedMinutes, int trafficLights,
                          int congestedSegments, String explanation) {

    public RouteOption {
        if (rank < 1) {
            throw new IllegalArgumentException("rank must be >= 1");
        }
        Objects.requireNonNull(type, "type");
        path = List.copyOf(path);
        segmentIds = List.copyOf(segmentIds);
        if (!(distanceKm >= 0) || !(estimatedMinutes >= 0)) {
            throw new IllegalArgumentException("distance and time must be >= 0");
        }
        if (trafficLights < 0 || congestedSegments < 0) {
            throw new IllegalArgumentException("counts must be >= 0");
        }
        if (explanation == null || explanation.isBlank()) {
            throw new IllegalArgumentException("explanation is required");
        }
    }
}
