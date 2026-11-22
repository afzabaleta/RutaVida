
package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.Hospital;
import co.edu.unicesar.rutavida.model.Permission;
import co.edu.unicesar.rutavida.model.Role;
import co.edu.unicesar.rutavida.model.User;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.enums.HospitalLevel;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import co.edu.unicesar.rutavida.util.PasswordHasher;

import java.time.LocalDateTime;
import java.util.Set;

import static co.edu.unicesar.rutavida.service.EmergencyTestSupport.CLOCK;

/**
 * Users, roles and hospitals of EXAMPLE for the tests
 * of UserService and HospitalService (S9), without Oracle.
 *
 * <ul>
 *   <li>user 1 "admin.prueba": ADMINISTRADOR, manages users and hospitals;</li>
 *   <li>user 2 "operador.prueba": OPERADOR_CENTRAL, only sees hospitals.</li>
 * </ul>
 * Both have the password {@link #PASSWORD}.
 * Hospital 1 is "HOSPITAL EJEMPLO UNO", active.
 */
public final class AdministrationTestSupport {

    public static final String PASSWORD = "Clave-Segura1";
    public static final long ADMIN = 1;
    public static final long OPERATOR = 2;
    public static final long ADMIN_ROLE = 1;
    public static final long OPERATOR_ROLE = 2;
    public static final long INACTIVE_ROLE = 9;
    public static final GeoPoint VALLEDUPAR =
            new GeoPoint(10.4631, -73.2532);

    public final InMemoryUserDAO userDao = new InMemoryUserDAO();
    public final InMemoryRoleDAO roleDao = new InMemoryRoleDAO();
    public final InMemoryHospitalDAO hospitalDao = new InMemoryHospitalDAO();
    public final InMemoryAuditDAO auditDao = new InMemoryAuditDAO();

    public final EmergencyTestSupport.CountingTransactions transactions =
            new EmergencyTestSupport.CountingTransactions();

    public final PasswordHasher hasher = new PasswordHasher(1_000);
    public final AuditService auditService =
            new AuditService(auditDao, CLOCK);

    public AdministrationTestSupport() {

        roleDao.add(new Role(
                ADMIN_ROLE,
                "ADMINISTRADOR",
                "Prueba",
                RecordStatus.ACTIVE,
                Set.of(
                        new Permission(1, "USUARIOS", "CONSULTAR", "Prueba"),
                        new Permission(2, "USUARIOS", "GESTIONAR", "Prueba"),
                        new Permission(7, "HOSPITALES", "CONSULTAR", "Prueba"),
                        new Permission(8, "HOSPITALES", "GESTIONAR", "Prueba")
                )
        ));

        roleDao.add(new Role(
                OPERATOR_ROLE,
                "OPERADOR_CENTRAL",
                "Prueba",
                RecordStatus.ACTIVE,
                Set.of(
                        new Permission(7, "HOSPITALES", "CONSULTAR", "Prueba")
                )
        ));

        roleDao.add(new Role(
                INACTIVE_ROLE,
                "ROL_RETIRADO",
                "Prueba",
                RecordStatus.INACTIVE,
                Set.of()
        ));

        String hash = hasher.hash(PASSWORD.toCharArray());
        LocalDateTime registered = LocalDateTime.now(CLOCK);

        userDao.insert(new User(
                0, ADMIN_ROLE, "PRUEBAS901", "Admin", "Prueba",
                "admin.prueba@ejemplo.com",
                "admin.prueba", hash, RecordStatus.ACTIVE, registered
        ));

        userDao.insert(new User(
                0, OPERATOR_ROLE, "PRUEBAS902", "Operador", "Prueba",
                "operador.prueba@ejemplo.com",
                "operador.prueba", hash, RecordStatus.ACTIVE, registered
        ));

        hospitalDao.insert(new Hospital(
                0,
                "HOSPITAL EJEMPLO UNO",
                HospitalLevel.LEVEL_II,
                "Dirección de EJEMPLO",
                null,
                VALLEDUPAR,
                10,
                RecordStatus.ACTIVE
        ));
    }

    public UserService userService() {
        return new UserService(
                userDao, roleDao, hasher,
                auditService, transactions, CLOCK
        );
    }

    public HospitalService hospitalService() {
        return new HospitalService(
                hospitalDao, auditService, transactions
        );
    }

    public AuthService authService() {
        return new AuthService(
                userDao, roleDao, hasher,
                auditService, CLOCK
        );
    }

    /** The session of a user of this support. */
    public UserSession sessionOf(long userId) {
        User user = userDao.findById(userId).orElseThrow();
        Role role = roleDao.findById(user.getRoleId()).orElseThrow();

        return new UserSession(
                user, role, LocalDateTime.now(CLOCK)
        );
    }
}
