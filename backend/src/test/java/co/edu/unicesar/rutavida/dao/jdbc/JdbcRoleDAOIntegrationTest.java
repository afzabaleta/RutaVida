package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.model.Role;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static co.edu.unicesar.rutavida.dao.jdbc.OracleTestDatabase.inRolledBackTransaction;
import static co.edu.unicesar.rutavida.dao.jdbc.OracleTestDatabase.number;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JdbcRoleDAO against the local Oracle with the roles and permissions of
 * 02_datos_iniciales.sql. Skipped without Oracle.
 */
class JdbcRoleDAOIntegrationTest {

    private static JdbcTransactionManager jdbc;
    private static JdbcRoleDAO dao;

    @BeforeAll
    static void connect() {
        jdbc = OracleTestDatabase.connectOrSkip();
        dao = new JdbcRoleDAO(jdbc);
    }

    private static Role role(String name) {
        return dao.findAll().stream()
                .filter(r -> r.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Role " + name + " not found: run 02_datos_iniciales.sql"));
    }

    @Test
    void findAllReturnsTheInitialRolesOrderedByName() {
        List<Role> roles = dao.findAll();
        List<String> names = roles.stream().map(Role::getName).toList();
        assertTrue(names.containsAll(List.of("ADMINISTRADOR", "BOMBERO_PARAMEDICO", "COORDINADOR", "OPERADOR_CENTRAL")));
        assertEquals(names.stream().sorted(Comparator.naturalOrder()).toList(), names);
    }

    @Test
    void administratorHasEveryPermission() {
        Role admin = role("ADMINISTRADOR");
        assertEquals(number(jdbc, "SELECT COUNT(*) FROM permiso"), admin.getPermissions().size());
        assertEquals(RecordStatus.ACTIVE, admin.getStatus());
        assertTrue(admin.hasPermission("AUDITORIA", "CONSULTAR"));
        assertTrue(admin.hasPermission("USUARIOS", "GESTIONAR"));
    }

    @Test
    void operatorHasOnlyItsPermissions() {
        Role operator = role("OPERADOR_CENTRAL");
        assertEquals(number(jdbc, "SELECT COUNT(*) FROM rol_permiso WHERE id_rol = ?", operator.getId()),
                operator.getPermissions().size());
        assertTrue(operator.hasPermission("EMERGENCIAS", "GESTIONAR"));
        assertTrue(operator.hasPermission("RUTAS", "GESTIONAR"));
        assertFalse(operator.hasPermission("USUARIOS", "GESTIONAR"));
        assertFalse(operator.hasPermission("AUDITORIA", "CONSULTAR"));
    }

    @Test
    void findByIdGivesTheSameRoleAsFindAll() {
        for (Role listed : dao.findAll()) {
            Role found = dao.findById(listed.getId()).orElseThrow();
            assertEquals(listed.getName(), found.getName());
            assertEquals(listed.getStatus(), found.getStatus());
            assertEquals(listed.getPermissions(), found.getPermissions());
        }
    }

    @Test
    void roleWithoutPermissionsHasAnEmptySet() {
        inRolledBackTransaction(jdbc, () -> {
            OracleTestDatabase.run(jdbc, "INSERT INTO rol (nombre_rol, descripcion) VALUES (?, ?)",
                    "PRUEBA_JUNIT", "Rol de prueba");
            long id = number(jdbc, "SELECT id_rol FROM rol WHERE nombre_rol = ?", "PRUEBA_JUNIT");
            Role role = dao.findById(id).orElseThrow();
            assertTrue(role.getPermissions().isEmpty());
            assertFalse(role.hasPermission("EMERGENCIAS", "CONSULTAR"));
        });
    }

    @Test
    void missingRoleIsEmpty() {
        assertTrue(dao.findById(-1).isEmpty());
    }
}