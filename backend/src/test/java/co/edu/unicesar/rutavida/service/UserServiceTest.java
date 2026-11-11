
package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dto.UserRequest;
import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.User;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static co.edu.unicesar.rutavida.service.AdministrationTestSupport.ADMIN;
import static co.edu.unicesar.rutavida.service.AdministrationTestSupport.ADMIN_ROLE;
import static co.edu.unicesar.rutavida.service.AdministrationTestSupport.INACTIVE_ROLE;
import static co.edu.unicesar.rutavida.service.AdministrationTestSupport.OPERATOR;
import static co.edu.unicesar.rutavida.service.AdministrationTestSupport.OPERATOR_ROLE;
import static co.edu.unicesar.rutavida.service.AdministrationTestSupport.PASSWORD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** UserService (CU-03) on in-memory DAOs, without Oracle. */
class UserServiceTest {

    private final AdministrationTestSupport support =
            new AdministrationTestSupport();

    private final UserService service = support.userService();

    private static UserRequest request(
            long roleId,
            String document,
            String email,
            String username,
            String password) {

        return new UserRequest(
                roleId,
                document,
                "Laura",
                "Prueba",
                email,
                username,
                password == null ? null : password.toCharArray()
        );
    }

    /** The service erases the passwords from memory after using them. */
    private static void assertErased(char[] password) {
        for (char c : password) {
            assertEquals('\0', c, "the password must be erased");
        }
    }

    private static UserRequest valid() {
        return request(
                OPERATOR_ROLE,
                "PRUEBAS903",
                "Laura.Prueba@Ejemplo.com",
                "   Laura.Prueba ",
                "Nueva-Clave22"
        );
    }

    @Test
    void createsAnActiveUserWithItsHashAndAudits() {
        UserRequest request = valid();

        User user = service.create(request, support.sessionOf(ADMIN));

        assertTrue(user.getId() > 0);
        assertEquals("laura.prueba", user.getUsername());
        assertEquals("laura.prueba@ejemplo.com", user.getEmail());
        assertEquals(RecordStatus.ACTIVE, user.getStatus());
        assertNotEquals("Nueva-Clave22", user.getPasswordHash());

        assertTrue(support.hasher.matches(
                "Nueva-Clave22".toCharArray(),
                user.getPasswordHash()
        ));

        assertErased(request.password());

        assertEquals("CREAR", support.auditDao.last().getOperation());
        assertEquals(ADMIN, support.auditDao.last().getUserId());
        assertEquals(user.getId(), support.auditDao.last().getRecordId());
    }

    @Test
    void wrongDataIsRejectedAndNothingIsSaved() {
        var admin = support.sessionOf(ADMIN);

        assertThrows(ValidationException.class, () -> service.create(
                request(OPERATOR_ROLE, "PRUEBAS903", "a@ejemplo.com",
                        "laura.prueba", "corta1"), admin));

        assertThrows(ValidationException.class, () -> service.create(
                request(OPERATOR_ROLE, "PRUEBAS903", "a@ejemplo.com",
                        "laura.prueba", "SoloLetrasSinNumero"), admin));

        assertThrows(ValidationException.class, () -> service.create(
                request(OPERATOR_ROLE, "PRUEBAS903", "no-es-un-correo",
                        "laura.prueba", "Nueva-Clave22"), admin));

        assertThrows(ValidationException.class, () -> service.create(
                request(OPERATOR_ROLE, "PRUEBAS903", "a@ejemplo.com",
                        "la", "Nueva-Clave22"), admin));

        assertThrows(NotFoundException.class, () -> service.create(
                request(77, "PRUEBAS903", "a@ejemplo.com",
                        "laura.prueba", "Nueva-Clave22"), admin));

        assertThrows(BusinessRuleException.class, () -> service.create(
                request(INACTIVE_ROLE, "PRUEBAS903", "a@ejemplo.com",
                        "laura.prueba", "Nueva-Clave22"), admin));

        assertEquals(2, support.userDao.findAll().size());
        assertTrue(support.auditDao.saved.isEmpty());
    }

    @Test
    void usernameDocumentAndEmailCannotBeRepeated() {
        var admin = support.sessionOf(ADMIN);

        assertEquals(
                "Ya existe un usuario con ese nombre de usuario.",
                assertThrows(BusinessRuleException.class,
                        () -> service.create(
                                request(OPERATOR_ROLE, "PRUEBAS903",
                                        "a@ejemplo.com", "Operador.Prueba",
                                        "Nueva-Clave22"), admin
                        )).getMessage()
        );

        assertEquals(
                "Ya existe un usuario con ese documento.",
                assertThrows(BusinessRuleException.class,
                        () -> service.create(
                                request(OPERATOR_ROLE, "pruebas901",
                                        "a@ejemplo.com", "laura.prueba",
                                        "Nueva-Clave22"), admin
                        )).getMessage()
        );

        assertEquals(
                "Ya existe un usuario con ese correo.",
                assertThrows(BusinessRuleException.class,
                        () -> service.create(
                                request(OPERATOR_ROLE, "PRUEBAS903",
                                        "ADMIN.PRUEBA@ejemplo.com",
                                        "laura.prueba", "Nueva-Clave22"), admin
                        )).getMessage()
        );
    }

    @Test
    void aUserIsDeactivatedAndActivatedAgain() {
        var admin = support.sessionOf(ADMIN);

        assertEquals(
                RecordStatus.INACTIVE,
                service.deactivate(OPERATOR, admin).getStatus()
        );

        assertEquals(
                RecordStatus.INACTIVE,
                support.userDao.findById(OPERATOR).orElseThrow().getStatus()
        );

        assertEquals("DESACTIVAR", support.auditDao.last().getOperation());

        assertThrows(
                BusinessRuleException.class,
                () -> service.deactivate(OPERATOR, admin)
        );

        assertEquals(
                RecordStatus.ACTIVE,
                service.activate(OPERATOR, admin).getStatus()
        );

        assertEquals("ACTIVAR", support.auditDao.last().getOperation());

        assertThrows(
                BusinessRuleException.class,
                () -> service.activate(OPERATOR, admin)
        );

        assertThrows(
                NotFoundException.class,
                () -> service.deactivate(99, admin)
        );
    }

    @Test
    void nobodyDeactivatesHimselfNorTheLastAdministrator() {

        assertEquals(
                "No puede desactivar su propio usuario.",
                assertThrows(
                        BusinessRuleException.class,
                        () -> service.deactivate(ADMIN, support.sessionOf(ADMIN))
                ).getMessage()
        );

        assertEquals(
                "No se puede desactivar el último administrador activo.",
                assertThrows(
                        BusinessRuleException.class,
                        () -> service.deactivate(ADMIN, support.sessionOf(OPERATOR))
                ).getMessage()
        );

        User second = service.create(
                request(ADMIN_ROLE, "PRUEBAS904",
                        "otro.admin@ejemplo.com",
                        "otro.admin", "Nueva-Clave22"),
                support.sessionOf(ADMIN)
        );

        assertEquals(
                RecordStatus.INACTIVE,
                service.deactivate(ADMIN, support.sessionOf(second.getId())).getStatus()
        );
    }

    @Test
    void eachUserChangesHisOwnPassword() {
        var operator = support.sessionOf(OPERATOR);

        assertEquals(
                "La contraseña actual no es correcta.",
                assertThrows(
                        ValidationException.class,
                        () -> service.changeOwnPassword(
                                operator,
                                "otra".toCharArray(),
                                "Nueva-Clave22".toCharArray()
                        )
                ).getMessage()
        );

        assertThrows(
                ValidationException.class,
                () -> service.changeOwnPassword(
                        operator,
                        PASSWORD.toCharArray(),
                        PASSWORD.toCharArray()
                )
        );

        assertThrows(
                ValidationException.class,
                () -> service.changeOwnPassword(
                        operator,
                        PASSWORD.toCharArray(),
                        "corta1".toCharArray()
                )
        );

        char[] current = PASSWORD.toCharArray();
        char[] next = "Nueva-Clave22".toCharArray();

        service.changeOwnPassword(operator, current, next);

        User saved = support.userDao.findById(OPERATOR).orElseThrow();

        assertTrue(support.hasher.matches(
                "Nueva-Clave22".toCharArray(),
                saved.getPasswordHash()
        ));

        assertErased(current);
        assertErased(next);

        assertEquals("CAMBIAR_CLAVE", support.auditDao.last().getOperation());
    }

    @Test
    void theListIsOrderedByUsername() {
        service.create(valid(), support.sessionOf(ADMIN));

        List<String> names = service.listAll().stream()
                .map(User::getUsername)
                .toList();

        assertEquals(
                List.of("admin.prueba", "laura.prueba", "operador.prueba"),
                names
        );
    }
}
