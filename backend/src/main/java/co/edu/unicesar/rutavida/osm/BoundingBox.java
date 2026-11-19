package co.edu.unicesar.rutavida.osm;

/**
 * Rectangle of the map that is imported, in decimal degrees.
 *
 * @param south minimum latitude
 * @param west  minimum longitude
 * @param north maximum latitude
 * @param east  maximum longitude
 */
public record BoundingBox(double south, double west, double north, double east) {

    /**
     * The urban area of Valledupar (decision DT-14, 19/11/2026), from the Anillo Vial de
     * Occidente to the Guatapuri river and from the south of the airport to the north of the
     * Avenida Sierra Nevada, with some margin. It replaced the demo zone of 21/10/2026
     * (10.435, -73.280, 10.480, -73.235), which only covered the center.
     */
    public static final BoundingBox VALLEDUPAR =
            new BoundingBox(10.405, -73.305, 10.510, -73.215);

    public BoundingBox {
        if (south < -90 || north > 90 || west < -180 || east > 180) {
            throw new IllegalArgumentException("Coordenadas fuera de rango");
        }
        if (south >= north || west >= east) {
            throw new IllegalArgumentException(
                    "El sur debe ser menor que el norte y el oeste menor que el este");
        }
    }

    /** True when the point is inside the rectangle or on its border. */
    public boolean contains(double latitude, double longitude) {
        return latitude >= south && latitude <= north
                && longitude >= west && longitude <= east;
    }
}