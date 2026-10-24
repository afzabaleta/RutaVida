package co.edu.unicesar.rutavida.osm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundingBoxTest {

    @Test
    void containsPointsInsideAndOnTheBorder() {
        BoundingBox box = new BoundingBox(10.0, -73.0, 10.01, -72.99);

        assertTrue(box.contains(10.005, -72.995));
        assertTrue(box.contains(10.0, -73.0));
        assertFalse(box.contains(10.005, -73.001));
        assertFalse(box.contains(10.011, -72.995));
    }

    @Test
    void rejectsAnInvertedOrImpossibleRectangle() {
        assertThrows(IllegalArgumentException.class, () -> new BoundingBox(10.48, -73.28, 10.435, -73.235));
        assertThrows(IllegalArgumentException.class, () -> new BoundingBox(10.435, -73.235, 10.48, -73.28));
        assertThrows(IllegalArgumentException.class, () -> new BoundingBox(-91, -73.28, 10.48, -73.235));
    }

    @Test
    void demoZoneContainsThePlacesOfTheDemonstration() {
        BoundingBox demo = BoundingBox.VALLEDUPAR_DEMO;

        // Fire station and hospitals: coordinates of 03_datos_valledupar.sql.
        assertTrue(demo.contains(10.468580, -73.257590));
        assertTrue(demo.contains(10.469330, -73.254520));
        assertTrue(demo.contains(10.446380, -73.247410));
        // Parque La Popa (Google Maps, 21/10/2026): place of the demo emergency.
        assertTrue(demo.contains(10.467172, -73.265100));
    }
}
