
package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.exception.ValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmergencyTypeTest {

    @Test
    void createsAValidTypeAndNormalizesTheName() {
        EmergencyType type = new EmergencyType(0, " incendio ", 9, "   ");
        assertEquals(0, type.getId());
        assertEquals("INCENDIO", type.getName());
        assertEquals(9, type.getPriority());
        assertNull(type.getDescription());
    }

    @Test
    void rejectsPriorityOutOfRange() {
        assertThrows(ValidationException.class, () ->
                new EmergencyType(0, "INCENDIO", 0, null));
        assertThrows(ValidationException.class, () ->
                new EmergencyType(0, "INCENDIO", 11, null));
    }

    @Test
    void rejectsEmptyOrTooLongName() {
        assertThrows(ValidationException.class, () ->
                new EmergencyType(0, " ", 5, null));
        assertThrows(ValidationException.class, () ->
                new EmergencyType(0, "X".repeat(81), 5, null));
    }

    @Test
    void assignIdOnlyWorksOnce() {
        EmergencyType type = new EmergencyType(0, "RESCATE", 7, null);
        type.assignId(4);
        assertEquals(4, type.getId());
        assertThrows(IllegalStateException.class, () -> type.assignId(5));
    }

    @Test
    void equalsComparesSavedEntitiesById() {
        assertTrue(new EmergencyType(4, "RESCATE", 7, null)
                .equals(new EmergencyType(4, "RESCATE", 7, null)));

        assertFalse(new EmergencyType(0, "RESCATE", 7, null)
                .equals(new EmergencyType(0, "RESCATE", 7, null)));
    }
}
