package co.edu.unicesar.rutavida.util;

/** Distance between two map coordinates, used by the route engine and the OSM importer. */
public final class GeoUtil {

    private static final double EARTH_RADIUS_METERS = 6_371_000;

    private GeoUtil() {
    }

    /** Great-circle distance in meters (Haversine formula). */
    public static double metersBetween(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaPhi = phi2 - phi1;
        double deltaLambda = Math.toRadians(lon2 - lon1);
        double a = Math.pow(Math.sin(deltaPhi / 2), 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.pow(Math.sin(deltaLambda / 2), 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(a));
    }
}
