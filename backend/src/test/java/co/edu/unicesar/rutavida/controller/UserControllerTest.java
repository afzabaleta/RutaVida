
package co.edu.unicesar.rutavida.controller;

import co.edu.unicesar.rutavida.dto.ErrorKind;
import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.dto.UserRequest;
import co.edu.unicesar.rutavida.dto.UserView;
import co.edu.unicesar.rutavida.service.AdministrationTestSupport;
import co.edu.unicesar.rutavida.util.Messages;
import org.junit.jupiter.api.Test;

import java.util.List;

import static co.edu.unicesar.rutavida.service.AdministrationTestSupport.ADMIN;
import static co.edu.unicesar.rutavida.service.AdministrationTestSupport.OPERATOR;
import static co.edu.unicesar.rutavida.service.AdministrationTestSupport.OPERATOR_ROLE;
import static co.edu.unicesar.rutavida.service.AdministrationTestSupport.PASSWORD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UserController with the real service on in-memory DAOs,
 * without Oracle.
 */
class UserControllerTest {

    private final AdministrationTestSupport support =
            new AdministrationTestSupport();

    private final SessionContext session = new SessionContext();

    private final UserController controller =
            new UserController(
                    support.userService(),
                    support.authService(),
                    session
            );

    @Test
    void anAdministratorCreatesAndListsUsersWithoutTheirHash() {
        session.start(support.sessionOf(ADMIN));

        OperationResult<UserView> created = controller.create(
                new UserRequest(
                        OPERATOR_ROLE,
                        "PRUEBAS903",
                        "Laura",
                        "Prueba",
                        "laura@ejemplo.com",
                        "laura.prueba",
                        "Nueva-Clave22".toCharArray()
                )
        );

        OperationResult<List<UserView>> listed = controller.list();

        assertTrue(created.success(), created.message());
        assertEquals("laura.prueba", created.value().username());
        assertEquals(3, listed.value().size());

        assertTrue(
                listed.value().stream()
                        .noneMatch(user ->
                                user.toString().contains("pbkdf2")),
                "UserView has no password hash"
        );
    }

    @Test
    void withoutSessionOrPermissionNothingChanges() {

        assertEquals(ErrorKind.AUTHENTICATION, controller.list().error());
        assertEquals(
                Messages.SESSION_REQUIRED,
                controller.list().message()
        );

        session.start(support.sessionOf(OPERATOR));

        assertEquals(ErrorKind.PERMISSION, controller.list().error());
        assertEquals(
                ErrorKind.PERMISSION,
                controller.deactivate(ADMIN).error()
        );

        assertTrue(support.auditDao.saved.isEmpty());
    }

    @Test
    void anyUserChangesHisOwnPasswordWithoutPermission() {

        session.start(support.sessionOf(OPERATOR));

        OperationResult<Void> changed =
                controller.changeOwnPassword(
                        PASSWORD.toCharArray(),
                        "Nueva-Clave22".toCharArray()
                );

        OperationResult<Void> wrong =
                controller.changeOwnPassword(
                        "otra".toCharArray(),
                        "Otra-Clave33".toCharArray()
                );

        assertTrue(changed.success(), changed.message());

        assertEquals(ErrorKind.VALIDATION, wrong.error());

        assertEquals(
                "La contraseña actual no es correcta.",
                wrong.message()
        );
    }
}
