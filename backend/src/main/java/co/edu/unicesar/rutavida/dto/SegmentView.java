package co.edu.unicesar.rutavida.dto;

import co.edu.unicesar.rutavida.model.GeoPoint;

/**
 * A road segment ready to draw on the map: its street and its two ends
 * (docs/api.md 8.2 and 9.1).
 *
 * @param segmentId  Oracle id (TRAMO_VIAL.id_tramo)
 * @param streetName name of the street (TRAMO_VIAL.nombre_via)
 * @param from       start node of the segment
 * @param to         end node of the segment
 */
public record SegmentView(long segmentId, String streetName, GeoPoint from, GeoPoint to) {
}