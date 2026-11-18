
package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.model.EmergencyStation;
import co.edu.unicesar.rutavida.model.EmergencyVehicle;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.Permission;
import co.edu.unicesar.rutavida.model.Role;
import co.edu.unicesar.rutavida.model.User;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.VehicleType;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import co.edu.unicesar.rutavida.model.enums.VehicleOperationalStatus;
import co.edu.unicesar.rutavida.util.PasswordHasher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static co.edu.unicesar.rutavida.service.EmergencyTestSupport.CLOCK;

/**
 * Units and stations of EXAMPLE for the tests of FleetService and FleetController (S10),
 * without Oracle.
 *
 * <ul>
 *   <li>user 1 "coordinador.prueba": COORDINADOR, manages units and dispatches
 *       (receives the alerts);</li>
 *   <li>user 2 "operador.prueba": OPERADOR_CENTRAL, only sees the units.</li>
 *   <li>station 1 "EJEMPLO - Base de ambulancias", active; station 2
 *       "EJEMPLO - Base cerrada", inactive;</li>
 *   <li>unit 1 EJEMPLO-AMB-01 (plate EJM101) DISPONIBLE and unit 2
 *       EJEMPLO-AMB-02 OCUPADO, both at station 1.</li>
 * </ul>
 */
public final class FleetTestSupport {

    public static final long COORDINATOR = 1;
    public static final long OPERATOR = 2;
    public static final long AMBULANCE_TYPE = 1;
    public static final long FIRE_TRUCK_TYPE = 2;
    public static final long BASE = 1;
    public static final long CLOSED_BASE = 2;
    public static final long AVAILABLE_UNIT = 1;
    public static final long BUSY_UNIT = 2;

    public static final GeoPoint VALLEDUPAR =
            new GeoPoint(10.4631, -73.2532);

    private static final List<VehicleType> TYPES = List.of(
            new VehicleType(AMBULANCE_TYPE, "AMBULANCIA", 8, "Prueba"),
            new VehicleType(FIRE_TRUCK_TYPE, "BOMBERO", 8, "Prueba")
    );

    public final InMemoryUserDAO userDao = new InMemoryUserDAO();
    public final InMemoryRoleDAO roleDao = new InMemoryRoleDAO();
    public final InMemoryEmergencyVehicleDAO vehicleDao =
            new InMemoryEmergencyVehicleDAO();
    public final InMemoryEmergencyStationDAO stationDao =
            new InMemoryEmergencyStationDAO();
    public final InMemoryAlertDAO alertDao = new InMemoryAlertDAO();
    public final InMemoryAuditDAO auditDao = new InMemoryAuditDAO();

    public final EmergencyTestSupport.CountingTransactions transactions =
            new EmergencyTestSupport.CountingTransactions();

    private final PasswordHasher hasher = new PasswordHasher(1_000);
    private final AuditService auditService = new AuditService(auditDao, CLOCK);

    public FleetTestSupport() {
        roleDao.add(new Role(
                1,
                "COORDINADOR",
                "Prueba",
                RecordStatus.ACTIVE,
                Set.of(
                        new Permission(5, "UNIDADES", "CONSULTAR", "Prueba"),
                        new Permission(6, "UNIDADES", "GESTIONAR", "Prueba"),
                        new Permission(18, "DESPACHO", "GESTIONAR", "Prueba")
                )
        ));

        roleDao.add(new Role(
                2,
                "OPERADOR_CENTRAL",
                "Prueba",
                RecordStatus.ACTIVE,
                Set.of(
                        new Permission(5, "UNIDADES", "CONSULTAR", "Prueba")
                )
        ));

        String hash = hasher.hash("Clave-Segura1".toCharArray());
        LocalDateTime registered = LocalDateTime.now(CLOCK);

        userDao.insert(new User(
                0,
                1,
                "PRUEBAS911",
                "Coordinador",
                "Prueba",
                "coordinador.prueba@ejemplo.com",
                "coordinador.prueba",
                hash,
                RecordStatus.ACTIVE,
                registered
        ));

        userDao.insert(new User(
                0,
                2,
                "PRUEBAS912",
                "Operador",
                "Prueba",
                "operador.prueba@ejemplo.com",
                "operador.prueba",
                hash,
                RecordStatus.ACTIVE,
                registered
        ));

        stationDao.insert(new EmergencyStation(
                0,
                "EJEMPLO - Base de ambulancias",
                "Calle de EJEMPLO 1",
                null,
                null,
                VALLEDUPAR,
                RecordStatus.ACTIVE
        ));

        stationDao.insert(new EmergencyStation(
                0,
                "EJEMPLO - Base cerrada",
                "Calle de EJEMPLO 2",
                null,
                null,
                VALLEDUPAR,
                RecordStatus.INACTIVE
        ));

        vehicleDao.insert(new EmergencyVehicle(
                0,
                AMBULANCE_TYPE,
                BASE,
                "EJEMPLO-AMB-01",
                "EJM101",
                VehicleOperationalStatus.AVAILABLE,
                VALLEDUPAR,
                registered
        ));

        vehicleDao.insert(new EmergencyVehicle(
                0,
                AMBULANCE_TYPE,
                BASE,
                "EJEMPLO-AMB-02",
                null,
                VehicleOperationalStatus.BUSY,
                VALLEDUPAR,
                registered
        ));
    }

    public FleetService fleetService() {
        AlertService alertService =
                new AlertService(alertDao, userDao, roleDao, transactions, CLOCK);

        return new FleetService(
                vehicleDao,
                stationDao,
                () -> TYPES,
                alertService,
                auditService,
                transactions
        );
    }

    public AuthService authService() {
        return new AuthService(
                userDao,
                roleDao,
                hasher,
                auditService,
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
}
