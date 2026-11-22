package co.edu.unicesar.rutavida.dto;

import co.edu.unicesar.rutavida.model.GeoPoint;

/**
 * The street segment closest to a point of the map (docs/api.md 9.4), so the web can report an
 * incident by clicking on a street.
 *
 * @param segmentId        the segment (TRAMO_VIAL.id_tramo), in the direction from → to
 * @param reverseSegmentId the same street in the other direction; null on a one-way street
 * @param distanceMeters   how far the clicked point is from the line of the segment
 */
public record NearbySegment(long segmentId, Long reverseSegmentId, String streetName,
                            GeoPoint from, GeoPoint to, double distanceMeters) {
}
