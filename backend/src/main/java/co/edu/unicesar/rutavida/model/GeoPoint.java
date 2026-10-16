
package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.util.ValidationUtil;

/**
 * A point on the map (latitude and longitude in decimal degrees).
 * Knows how to measure the distance to another point
 * (GRASP Information Expert).
 */
public record GeoPoint(double latitude, double longitude) {

    private static final double EARTH_RADIUS_METERS = 6_371_000;

    public GeoPoint {
        ValidationUtil.requireInRange(latitude, -90, 90, "latitud");
        ValidationUtil.requireInRange(longitude, -180, 180, "longitud");
    }

    /** Great-circle distance in meters (Haversine formula). */
    public double distanceMetersTo(GeoPoint other) {
        ValidationUtil.requireNonNull(other, "punto de destino");

        double lat1 = Math.toRadians(latitude);
        double lat2 = Math.toRadians(other.latitude);
        double deltaLat = lat2 - lat1;
        double deltaLon = Math.toRadians(other.longitude - longitude);

        double a = Math.pow(Math.sin(deltaLat / 2), 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.pow(Math.sin(deltaLon / 2), 2);

        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(a));
    }
}

