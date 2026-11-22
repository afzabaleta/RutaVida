package co.edu.unicesar.rutavida.graph;

import co.edu.unicesar.rutavida.util.GeoUtil;

public record Node(int index, long dbId, double lat, double lon) {


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
        return GeoUtil.metersBetween(lat, lon, otherLat, otherLon);
    }
}