
package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.EmergencyAssignmentDAO;
import co.edu.unicesar.rutavida.dao.RouteDAO;
import co.edu.unicesar.rutavida.dao.TransactionManager;
import co.edu.unicesar.rutavida.model.EmergencyAssignment;
import co.edu.unicesar.rutavida.model.EmergencyType;
import co.edu.unicesar.rutavida.model.Permission;
import co.edu.unicesar.rutavida.model.Role;
import co.edu.unicesar.rutavida.model.Route;
import co.edu.unicesar.rutavida.model.User;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import co.edu.unicesar.rutavida.model.enums.RouteStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Test data and fakes shared by the emergency tests (service and controller), without
 * Oracle. Everything here is invented for the tests.
 */
public final class EmergencyTestSupport {

    public static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-03T15:00:00Z"),
                    ZoneId.of("America/Bogota"));

    public static final long OPERATOR_ID = 7;

    public static final EmergencyType ACCIDENT =
            new EmergencyType(3, "ACCIDENTE_VIAL", 8, "Prueba");

    private EmergencyTestSupport() {
    }

    /** Fake TransactionManager: runs the work directly and counts the transactions. */
    public static final class CountingTransactions implements TransactionManager {
        public int calls;

        @Override
        public <T> T callInTransaction(Supplier<T> work) {
            calls++;
            return work.get();
        }
    }

    /** Assignments of an emergency with a fixed answer. */
    public static final class FixedAssignments implements EmergencyAssignmentDAO {
        public List<EmergencyAssignment> list = List.of();

        @Override
        public List<EmergencyAssignment> findByEmergency(long emergencyId) {
            return list;
        }

        @Override
        public long insert(EmergencyAssignment assignment) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<EmergencyAssignment> findById(long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<EmergencyAssignment> findOpenByEmergency(long emergencyId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<EmergencyAssignment> findOpenByVehicle(long vehicleId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void update(EmergencyAssignment assignment) {
            throw new UnsupportedOperationException();
        }
    }

    /** Routes of an emergency with a fixed answer. */
    public static final class FixedRoutes implements RouteDAO {
        public List<Route> list = List.of();

        @Override
        public List<Route> findByEmergency(long emergencyId) {
            return list;
        }

        @Override
        public long insert(Route route) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Route> findById(long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Route> findActive() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Route> findActiveUsingSegments(Set<Long> segmentIds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateStatus(long id, RouteStatus status) {
            throw new UnsupportedOperationException();
        }
    }

    /** An active operator whose role has exactly these permissions. */
    public static UserSession session(String... permissions) {
        Set<Permission> granted = new HashSet<>();

        for (int i = 0; i < permissions.length; i++) {
            String[] parts = permissions[i].split(":");
            granted.add(new Permission(i + 1, parts[0], parts[1], null));
        }

        Role role = new Role(
                2, "OPERADOR_CENTRAL", null, RecordStatus.ACTIVE, granted);

        User user = new User(
                OPERATOR_ID,
                2,
                "1065000000",
                "Operador",
                "De Prueba",
                "operador@ejemplo.com",
                "operador.prueba",
                "NO_ES_UN_HASH",
                RecordStatus.ACTIVE,
                LocalDateTime.now(CLOCK)
        );

        return new UserSession(user, role, LocalDateTime.now(CLOCK));
    }
}
