package co.edu.unicesar.rutavida.dto;

import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.enums.TrafficLevel;
import co.edu.unicesar.rutavida.model.enums.TrafficSource;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * The traffic of one road segment, ready to paint on the map (docs/api.md 8.2 and 10.3).
 *
 * @param segmentId        Oracle id (TRAMO_VIAL.id_tramo)
 * @param streetName       name of the street (TRAMO_VIAL.nombre_via)
 * @param level            MODERADO or CONGESTIONADO (FLUIDO is not painted)
 * @param congestionFactor 1.0 = free flow; 2.5 = it takes 2.5 times longer
 * @param measuredAt       when the reading was taken
 * @param source           SIMULACION in this version: the traffic is never real
 * @param from             start node of the segment
 * @param to               end node of the segment
 */
public record TrafficSegmentView(long segmentId, String streetName, TrafficLevel level, double congestionFactor,
                                 LocalDateTime measuredAt, TrafficSource source, GeoPoint from, GeoPoint to) {

    public TrafficSegmentView {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(measuredAt, "measuredAt");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
    }
}
