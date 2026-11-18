
package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.controller.PanicController;
import co.edu.unicesar.rutavida.controller.SessionContext;
import co.edu.unicesar.rutavida.dto.PanicAlertRequest;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.PanicReport;
import co.edu.unicesar.rutavida.model.Permission;
import co.edu.unicesar.rutavida.model.Role;
import co.edu.unicesar.rutavida.model.User;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.enums.PanicReportStatus;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;

import java.time.LocalDateTime;
import java.util.Set;

import static co.edu.unicesar.rutavida.service.EmergencyTestSupport.CLOCK;

public final class PanicTestSupport {

    public static final Permission DISPATCH =
            new Permission(1, "DESPACHO", "GESTIONAR", null);
    public static final Permission VALIDATE =
            new Permission(2, "PANICO", "VALIDAR", null);
    public static final Permission VIEW =
            new Permission(3, "PANICO", "CONSULTAR", null);
    public static final Permission MANAGE =
            new Permission(4, "PANICO", "GESTIONAR", null);

    public static final long SYSTEM = 1;
    public static final long COORDINATOR = 2;
    public static final long POLICE = 3;

    public static final GeoPoint SCENE = DispatchTestSupport.at(1, 2);

    public final DispatchTestSupport city = new DispatchTestSupport();
    public final InMemoryPanicReportDAO reports = new InMemoryPanicReportDAO();
    public final InMemoryUserDAO users = new InMemoryUserDAO();
    public final InMemoryRoleDAO roles = new InMemoryRoleDAO();
    public final InMemoryAlertDAO alerts = new InMemoryAlertDAO();

    public PanicTestSupport() {
        roles.add(new Role(3, "COORDINADOR", "Prueba",
                RecordStatus.ACTIVE, Set.of(DISPATCH, VIEW, MANAGE)));
        roles.add(new Role(6, "POLICIA", "Prueba",
                RecordStatus.ACTIVE, Set.of(VALIDATE, VIEW)));

        user(3, "sistema.rutavida", RecordStatus.INACTIVE);
        user(3, "coordinador", RecordStatus.ACTIVE);
        user(6, "policia", RecordStatus.ACTIVE);
    }

    public PanicService service() {
        AuditService audit = new AuditService(city.audit, CLOCK);
        EmergencyService emergencies = new EmergencyService(
                city.emergencies, city.emergencyTypes,
                city.assignments, city.routes, audit,
                city.transactions, CLOCK);

        RouteService routes = new RouteService(
                city.graph, city.routes, city.transactions, CLOCK);

        AlertService alertService = new AlertService(
                alerts, users, roles, city.transactions, CLOCK);

        return new PanicService(
                reports, city.emergencyTypes, users, roles,
                emergencies, city.service(), routes,
                alertService, audit, city.transactions, CLOCK);
    }

    public static PanicAlertRequest press(String device, double metersNorth) {
        return new PanicAlertRequest(device, north(metersNorth), null, null);
    }

    public static GeoPoint north(double meters) {
        return new GeoPoint(
                SCENE.latitude() + meters / 111_195.0,
                SCENE.longitude());
    }

    public long oldReport(String device, int minutesAgo) {
        return reports.insert(new PanicReport(
                0, SCENE,
                LocalDateTime.now(CLOCK).minusMinutes(minutesAgo),
                PanicService.hashDevice(device),
                null, null, null,
                PanicReportStatus.PENDING, null));
    }

    public UserSession sessionOf(long userId) {
        User user = users.findById(userId).orElseThrow();
        return new UserSession(
                user,
                roles.findById(user.getRoleId()).orElseThrow(),
                LocalDateTime.now(CLOCK));
    }

    private void user(long roleId, String username, RecordStatus status) {
        long next = users.findAll().size() + 1;

        users.insert(new User(
                0, roleId, "PRUEBA" + next, "Prueba", "Panico",
                username + "@ejemplo.com", username, "NO_ES_UN_HASH",
                status, LocalDateTime.now(CLOCK)));
    }

    /** The controller of the panic button with this support, for the tests of the API (A20b). */
    public PanicController controller(AuthService authService, SessionContext session) {
        return new PanicController(service(), authService, session);
    }
}
