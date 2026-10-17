package co.edu.unicesar.rutavida.graph;

public record Node(int index, long dbId, double lat, double lon) {

    private static final double EARTH_RADIUS_METERS =6_371_000;

    public Node {
        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0");
        }
        if (dbId < 0) {
            throw new IllegalArgumentException("dbId must be >= 0");
        }
        if (!(lat >= -90 && lat <= 90)|| !(lon >= -180 && lon <= 180)) {
            throw new IllegalArgumentException("invalid coordinates: " + lat +", "+lon);
        }
    }

    public double distanceMetersTo(Node other) {
        return distanceMetersTo(other.lat, other.lon);
    }

    public double distanceMetersTo(double otherLat, double otherLon) {
        double lat1 = Math.toRadians(lat);
        double lat2 = Math.toRadians(otherLat);
        double deltaLat = lat2 - lat1;
        double deltaLon = Math.toRadians(otherLon - lon);
        double a = Math.pow(Math.sin(deltaLat / 2), 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(deltaLon / 2), 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(a));
    }
}