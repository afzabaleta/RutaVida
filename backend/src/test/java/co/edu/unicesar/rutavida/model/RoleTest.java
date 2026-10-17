
package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RoleTest {

    @Test
    void createsAValidRoleAndNormalizesItsName() {
        Role role = new Role(
                0, " coordinador ", "   ",
                RecordStatus.ACTIVE, Set.of());

        assertEquals(0, role.getId());
        assertEquals("COORDINADOR", role.getName());
        assertNull(role.getDescription());
        assertTrue(role.isActive());
        assertTrue(role.getPermissions().isEmpty());
    }

    @Test
    void rejectsInvalidRequiredFields() {
        assertThrows(ValidationException.class, () ->
                new Role(0, " ", null,
                        RecordStatus.ACTIVE, Set.of()));

        assertThrows(ValidationException.class, () ->
                new Role(0, "COORDINADOR", null,
                        null, Set.of()));

        assertThrows(ValidationException.class, () ->
                new Role(0, "COORDINADOR", null,
                        RecordStatus.ACTIVE, null));

        assertThrows(ValidationException.class, () ->
                new Role(0, "X".repeat(51), null,
                        RecordStatus.ACTIVE, Set.of()));
    }

    @Test
    void activeRoleGrantsOnlyAssignedPermissions() {
        Permission consult = new Permission(
                1, "EMERGENCIAS", "CONSULTAR", null);

        Role role = new Role(
                0, "OPERADOR", null,
                RecordStatus.ACTIVE, Set.of(consult));

        assertTrue(role.hasPermission("emergencias", "consultar"));
        assertFalse(role.hasPermission("EMERGENCIAS", "GESTIONAR"));
        assertFalse(role.hasPermission("USUARIOS", "CONSULTAR"));
    }

    @Test
    void inactiveRoleNeverGrantsPermissions() {
        Permission manage = new Permission(
                1, "EMERGENCIAS", "GESTIONAR", null);

        Role role = new Role(
                0, "COORDINADOR", null,
                RecordStatus.INACTIVE, Set.of(manage));

        assertFalse(role.isActive());
        assertFalse(role.hasPermission("EMERGENCIAS", "GESTIONAR"));
    }

    @Test
    void permissionsAreAnImmutableCopy() {
        Permission consult = new Permission(
                1, "EMERGENCIAS", "CONSULTAR", null);

        Set<Permission> original = new HashSet<>();
        original.add(consult);

        Role role = new Role(
                0, "OPERADOR", null,
                RecordStatus.ACTIVE, original);

        original.clear();

        assertEquals(1, role.getPermissions().size());

        assertThrows(UnsupportedOperationException.class, () ->
                role.getPermissions().clear());
    }

    @Test
    void assignIdWorksOnlyOnce() {
        Role role = new Role(
                0, "OPERADOR", null,
                RecordStatus.ACTIVE, Set.of());

        role.assignId(8);

        assertEquals(8, role.getId());

        assertThrows(IllegalStateException.class,
                () -> role.assignId(9));
    }
}
