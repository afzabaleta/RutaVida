
package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.model.EmergencyAssignment;
import co.edu.unicesar.rutavida.model.Permission;
import co.edu.unicesar.rutavida.model.Role;
import co.edu.unicesar.rutavida.model.User;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.enums.AssignmentStatus;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;

import java.time.LocalDateTime;
import java.util.Set;

import static co.edu.unicesar.rutavida.service.EmergencyTestSupport.CLOCK;

/**
 * Users, roles and a mission for the alert tests, without Oracle.
 *
 * user 1: coordinator, receives general alerts.
 * user 2: administrator, receives general alerts.
 * user 3: operator, cannot manage dispatch.
 * user 4: inactive coordinator.
 * user 5: user of an inactive role.
 */
public final class AlertTestSupport {

    public static final long COORDINATOR = 1;
    public static final long ADMINISTRATOR = 2;
    public static final long OPERATOR = 3;
    public static final long INACTIVE_COORDINATOR = 4;
    public static final long USER_OF_INACTIVE_ROLE = 5;

    /** Mission of emergency 15, dispatched by the coordinator. */
    public static final EmergencyAssignment MISSION =
            new EmergencyAssignment(
                    8, 15, 1, COORDINATOR,
                    LocalDateTime.now(CLOCK).minusMinutes(5),
                    null, null, AssignmentStatus.EN_ROUTE
            );

    private static final Permission DISPATCH =
            new Permission(20, "DESPACHO", "GESTIONAR", "Prueba");

    private static final Permission SEE_DISPATCH =
            new Permission(19, "DESPACHO", "CONSULTAR", "Prueba");

    public final InMemoryAlertDAO alertDao = new InMemoryAlertDAO();
    public final InMemoryUserDAO userDao = new InMemoryUserDAO();
    public final InMemoryRoleDAO roleDao = new InMemoryRoleDAO();

    public final EmergencyTestSupport.CountingTransactions transactions =
            new EmergencyTestSupport.CountingTransactions();

    public AlertTestSupport() {
        roleDao.add(new Role(
                1, "ADMINISTRADOR", "Prueba",
                RecordStatus.ACTIVE, Set.of(DISPATCH, SEE_DISPATCH)));

        roleDao.add(new Role(
                2, "OPERADOR_CENTRAL", "Prueba",
                RecordStatus.ACTIVE, Set.of(SEE_DISPATCH)));

        roleDao.add(new Role(
                3, "COORDINADOR", "Prueba",
                RecordStatus.ACTIVE, Set.of(DISPATCH, SEE_DISPATCH)));

        roleDao.add(new Role(
                9, "ROL_RETIRADO", "Prueba",
                RecordStatus.INACTIVE, Set.of(DISPATCH)));

        user(3, "coordinador", RecordStatus.ACTIVE);
        user(1, "administrador", RecordStatus.ACTIVE);
        user(2, "operador", RecordStatus.ACTIVE);
        user(3, "coordinador.retirado", RecordStatus.INACTIVE);
        user(9, "rol.retirado", RecordStatus.ACTIVE);
    }

    public AlertService service() {
        return new AlertService(
                alertDao,
                userDao,
                roleDao,
                transactions,
                CLOCK
        );
    }

    /** The session of a user of this support. */
    public UserSession sessionOf(long userId) {
        User user = userDao.findById(userId).orElseThrow();
        Role role = roleDao.findById(user.getRoleId()).orElseThrow();

        return new UserSession(
                user,
                role,
                LocalDateTime.now(CLOCK)
        );
    }

    private void user(long roleId, String username, RecordStatus status) {
        long next = userDao.findAll().size() + 1;

        userDao.insert(new User(
                0,
                roleId,
                "PRUEBA" + next,
                "Prueba",
                "Alertas",
                username + "@ejemplo.com",
                username,
                "NO_ES_UN_HASH",
                status,
                LocalDateTime.now(CLOCK)
        ));
    }
}
