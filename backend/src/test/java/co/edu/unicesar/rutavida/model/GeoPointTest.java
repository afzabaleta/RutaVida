
package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.exception.ValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeoPointTest {

    @Test
    void acceptsAValidPoint() {
        GeoPoint point = new GeoPoint(10.4631, -73.2532);
        assertEquals(10.4631, point.latitude());
        assertEquals(-73.2532, point.longitude());
    }

    @Test
    void rejectsLatitudeOutOfRange() {
        assertThrows(ValidationException.class,
                () -> new GeoPoint(91, -73.25));
    }

    @Test
    void rejectsLongitudeOutOfRange() {
        assertThrows(ValidationException.class,
                () -> new GeoPoint(10.46, -181));
    }

    @Test
    void rejectsNotANumber() {
        assertThrows(ValidationException.class,
                () -> new GeoPoint(Double.NaN, -73.25));
    }

    @Test
    void distanceToItselfIsZero() {
        GeoPoint point = new GeoPoint(10.4631, -73.2532);
        assertEquals(0, point.distanceMetersTo(point), 0.001);
    }

    @Test
    void oneThousandthOfADegreeOfLatitudeIsAbout111Meters() {
        GeoPoint a = new GeoPoint(10.4631, -73.2532);
        GeoPoint b = new GeoPoint(10.4641, -73.2532);
        assertEquals(111.2, a.distanceMetersTo(b), 0.5);
    }

    @Test
    void distanceRequiresAnotherPoint() {
        GeoPoint point = new GeoPoint(10.4631, -73.2532);
        assertThrows(ValidationException.class,
                () -> point.distanceMetersTo(null));
    }
}
