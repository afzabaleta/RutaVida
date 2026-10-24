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
     * Demo zone agreed on 21/10/2026: the fire station and both hospitals of
     * 03_datos_valledupar.sql and Parque La Popa, with at least 1 km of margin.
     */
    public static final BoundingBox VALLEDUPAR_DEMO =
            new BoundingBox(10.435, -73.280, 10.480, -73.235);

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