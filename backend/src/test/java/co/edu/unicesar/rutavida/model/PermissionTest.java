
package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.exception.ValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PermissionTest {

    @Test
    void createsAValidPermissionAndNormalizesCodes() {
        Permission permission = new Permission(
                0, " emergencias ", " consultar ", "   ");

        assertEquals(0, permission.getId());
        assertEquals("EMERGENCIAS", permission.getModule());
        assertEquals("CONSULTAR", permission.getAction());
        assertNull(permission.getDescription());
    }

    @Test
    void rejectsInvalidModuleOrAction() {
        assertThrows(ValidationException.class, () ->
                new Permission(0, " ", "CONSULTAR", null));

        assertThrows(ValidationException.class, () ->
                new Permission(0, "EMERGENCIAS", " ", null));

        assertThrows(ValidationException.class, () ->
                new Permission(0, "X".repeat(61), "CONSULTAR", null));

        assertThrows(ValidationException.class, () ->
                new Permission(0, "EMERGENCIAS", "X".repeat(41), null));
    }

    @Test
    void matchesIgnoringCaseButNotDifferentActions() {
        Permission permission = new Permission(
                0, "EMERGENCIAS", "CONSULTAR", null);

        assertTrue(permission.matches("emergencias", "consultar"));
        assertTrue(permission.matches("EMERGENCIAS", "CONSULTAR"));

        assertFalse(permission.matches("EMERGENCIAS", "GESTIONAR"));
        assertFalse(permission.matches("USUARIOS", "CONSULTAR"));
        assertFalse(permission.matches(null, "CONSULTAR"));
    }

    @Test
    void assignIdWorksOnlyOnce() {
        Permission permission = new Permission(
                0, "USUARIOS", "GESTIONAR", null);

        permission.assignId(3);

        assertEquals(3, permission.getId());

        assertThrows(IllegalStateException.class,
                () -> permission.assignId(4));
    }

    @Test
    void savedEntitiesAreEqualById() {
        Permission first = new Permission(
                5, "EMERGENCIAS", "CONSULTAR", null);

        Permission second = new Permission(
                5, "EMERGENCIAS", "CONSULTAR", null);

        assertEquals(first, second);

        Permission unsavedOne = new Permission(
                0, "EMERGENCIAS", "CONSULTAR", null);

        Permission unsavedTwo = new Permission(
                0, "EMERGENCIAS", "CONSULTAR", null);

        assertNotEquals(unsavedOne, unsavedTwo);
    }
}
