
package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.exception.ValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleTypeTest {

    @Test
    void createsAValidTypeAndNormalizesTheName() {
        VehicleType type = new VehicleType(0, " ambulancia ", 9, "   ");

        assertEquals(0, type.getId());
        assertEquals("AMBULANCIA", type.getName());
        assertEquals(9, type.getPriority());
        assertNull(type.getDescription());
    }

    @Test
    void rejectsPriorityOutOfRange() {
        assertThrows(ValidationException.class, () ->
                new VehicleType(0, "AMBULANCIA", 0, null));

        assertThrows(ValidationException.class, () ->
                new VehicleType(0, "AMBULANCIA", 11, null));
    }

    @Test
    void rejectsEmptyOrTooLongName() {
        assertThrows(ValidationException.class, () ->
                new VehicleType(0, " ", 5, null));

        assertThrows(ValidationException.class, () ->
                new VehicleType(0, "X".repeat(51), 5, null));
    }

    @Test
    void assignIdOnlyWorksOnce() {
        VehicleType type = new VehicleType(0, "BOMBERO", 7, null);

        type.assignId(4);

        assertEquals(4, type.getId());

        assertThrows(IllegalStateException.class,
                () -> type.assignId(5));
    }

    @Test
    void equalsComparesSavedEntitiesById() {
        assertTrue(new VehicleType(4, "BOMBERO", 7, null)
                .equals(new VehicleType(4, "BOMBERO", 7, null)));

        assertFalse(new VehicleType(0, "BOMBERO", 7, null)
                .equals(new VehicleType(0, "BOMBERO", 7, null)));
    }
}
