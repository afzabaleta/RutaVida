
package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.enums.NodeType;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoadNodeTest {

    private static final GeoPoint LOCATION =
            new GeoPoint(10.467172, -73.265100);

    private RoadNode createNode(long id) {
        return new RoadNode(
                id,
                123456789L,
                " Calle 16 x Carrera 20 ",
                LOCATION,
                NodeType.INTERSECTION,
                RecordStatus.ACTIVE
        );
    }

    @Test
    void createsAValidNodeThatIsLocatable() {
        RoadNode node = createNode(0);

        assertEquals(0, node.getId());
        assertEquals(123456789L, node.getOsmId());
        assertEquals("Calle 16 x Carrera 20", node.getReference());
        assertEquals(LOCATION, node.getLocation());
        assertEquals(NodeType.INTERSECTION, node.getNodeType());
        assertTrue(node.isActive());
        assertTrue(node instanceof Locatable);
    }

    @Test
    void optionalFieldsCanBeEmpty() {
        RoadNode node = new RoadNode(
                0,
                null,
                "               ",
                LOCATION,
                NodeType.WAY_POINT,
                RecordStatus.INACTIVE
        );

        assertNull(node.getOsmId());
        assertNull(node.getReference());
        assertFalse(node.isActive());
    }

    @Test
    void rejectsInvalidData() {
        assertThrows(ValidationException.class, () -> new RoadNode(
                -1, null, null, LOCATION,
                NodeType.INTERSECTION, RecordStatus.ACTIVE));

        assertThrows(ValidationException.class, () -> new RoadNode(
                0, 0L, null, LOCATION,
                NodeType.INTERSECTION, RecordStatus.ACTIVE));

        assertThrows(ValidationException.class, () -> new RoadNode(
                0, null, "x".repeat(121), LOCATION,
                NodeType.INTERSECTION, RecordStatus.ACTIVE));

        assertThrows(ValidationException.class, () -> new RoadNode(
                0, null, null, null,
                NodeType.INTERSECTION, RecordStatus.ACTIVE));

        assertThrows(ValidationException.class, () -> new RoadNode(
                0, null, null, LOCATION,
                null, RecordStatus.ACTIVE));

        assertThrows(ValidationException.class, () -> new RoadNode(
                0, null, null, LOCATION,
                NodeType.INTERSECTION, null));
    }

    @Test
    void assignIdWorksOnceAndDefinesEquality() {
        RoadNode node = createNode(0);
        node.assignId(7);

        assertEquals(7, node.getId());

        assertThrows(
                IllegalStateException.class,
                () -> node.assignId(8)
        );

        assertEquals(createNode(7), node);
        assertNotEquals(createNode(0), createNode(0));
    }
}
