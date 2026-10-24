
package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.enums.RoadSegmentStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoadSegmentTest {

    private RoadSegment createSegment(RoadSegmentStatus status) {
        return new RoadSegment(
                0, 1, 2, 555L, " Calle 16 ", 1000, 36, status
        );
    }

    @Test
    void createsAValidSegment() {
        RoadSegment segment =
                createSegment(RoadSegmentStatus.OPEN);

        assertEquals(1, segment.getFromNodeId());
        assertEquals(2, segment.getToNodeId());
        assertEquals(555L, segment.getOsmWayId());
        assertEquals("Calle 16", segment.getRoadName());
        assertEquals(1000, segment.getLengthMeters(), 1e-9);
        assertEquals(36, segment.getMaxSpeedKmh(), 1e-9);
    }

    @Test
    void baseTimeIsTheSameAsTheVirtualColumnOfOracle() {
        // 98_verificar.sql, test 6a: 1000 m at 36 km/h = 100 s.
        assertEquals(
                100,
                createSegment(RoadSegmentStatus.OPEN)
                        .getBaseTimeSeconds(),
                1e-9
        );

        assertEquals(
                7.2,
                new RoadSegment(
                        0, 1, 2, null, "Calle 16",
                        100, 50, RoadSegmentStatus.OPEN
                ).getBaseTimeSeconds(),
                1e-9
        );
    }

    @Test
    void onlyAClosedSegmentIsNotOpen() {
        assertTrue(
                createSegment(RoadSegmentStatus.OPEN).isOpen()
        );

        assertTrue(
                createSegment(RoadSegmentStatus.RESTRICTED).isOpen()
        );

        assertFalse(
                createSegment(RoadSegmentStatus.CLOSED).isOpen()
        );
    }

    @Test
    void rejectsASegmentThatStartsAndEndsInTheSameNode() {
        ValidationException error = assertThrows(
                ValidationException.class,
                () -> new RoadSegment(
                        0, 3, 3, null, "Calle 16",
                        100, 30, RoadSegmentStatus.OPEN
                )
        );

        assertEquals(
                "Un tramo no puede empezar y terminar en el mismo nodo.",
                error.getMessage()
        );
    }

    @Test
    void rejectsInvalidData() {
        RoadSegmentStatus open = RoadSegmentStatus.OPEN;

        assertThrows(ValidationException.class, () ->
                new RoadSegment(0, 0, 2, null,
                        "Calle 16", 100, 30, open));

        assertThrows(ValidationException.class, () ->
                new RoadSegment(0, 1, 2, 0L,
                        "Calle 16", 100, 30, open));

        assertThrows(ValidationException.class, () ->
                new RoadSegment(0, 1, 2, null,
                        "           ", 100, 30, open));

        assertThrows(ValidationException.class, () ->
                new RoadSegment(0, 1, 2, null,
                        "x".repeat(121), 100, 30, open));

        assertThrows(ValidationException.class, () ->
                new RoadSegment(0, 1, 2, null,
                        "Calle 16", 0, 30, open));

        assertThrows(ValidationException.class, () ->
                new RoadSegment(0, 1, 2, null,
                        "Calle 16", Double.NaN, 30, open));

        assertThrows(ValidationException.class, () ->
                new RoadSegment(0, 1, 2, null,
                        "Calle 16", 100, 0, open));

        assertThrows(ValidationException.class, () ->
                new RoadSegment(0, 1, 2, null,
                        "Calle 16", 100, 120.01, open));

        assertThrows(ValidationException.class, () ->
                new RoadSegment(0, 1, 2, null,
                        "Calle 16", 100, 30, null));
    }
}
