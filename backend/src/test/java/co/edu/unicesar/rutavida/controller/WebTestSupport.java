package co.edu.unicesar.rutavida.controller;

import co.edu.unicesar.rutavida.model.Permission;
import co.edu.unicesar.rutavida.model.Role;
import co.edu.unicesar.rutavida.model.User;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import co.edu.unicesar.rutavida.service.AuditService;
import co.edu.unicesar.rutavida.service.AuthService;
import co.edu.unicesar.rutavida.service.InMemoryAuditDAO;
import co.edu.unicesar.rutavida.service.InMemoryRoleDAO;
import co.edu.unicesar.rutavida.service.InMemoryUserDAO;
import co.edu.unicesar.rutavida.util.PasswordHasher;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;

/**
 * Builds a real AuthService over in-memory DAOs with two EXAMPLE users, for the tests of
 * the web sessions and the web server. Nothing touches Oracle.
 * <ul>
 *   <li>{@code ana.perez} / {@link #PASSWORD}: OPERADOR_CENTRAL with EMERGENCIAS consult and manage.</li>
 *   <li>{@code luis.gomez} / {@link #PASSWORD}: BOMBERO_PARAMEDICO without permissions.</li>
 *   <li>{@code marta.ruiz} / {@link #PASSWORD}: COORDINADOR with EMERGENCIAS, UNIDADES, HOSPITALES,
 *       REPORTES, INCIDENTES, RUTAS and DESPACHO.</li>
 * </ul>
 */
public final class WebTestSupport {

    public static final String PASSWORD = "Clave-Segura1";
    public static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    public final MutableClock clock = new MutableClock(Instant.parse("2026-11-05T14:00:00Z"));
    public final InMemoryAuditDAO auditDao = new InMemoryAuditDAO();
    public final SessionContext sessionContext = new SessionContext();
    public final AuthService authService;

    public WebTestSupport() {
        PasswordHasher hasher = new PasswordHasher(1_000);
        InMemoryUserDAO userDao = new InMemoryUserDAO();
        InMemoryRoleDAO roleDao = new InMemoryRoleDAO();

        roleDao.add(new Role(2L, "OPERADOR_CENTRAL", "Operador", RecordStatus.ACTIVE, Set.of(
                new Permission(3L, "EMERGENCIAS", "CONSULTAR", "Ver emergencias"),
                new Permission(4L, "EMERGENCIAS", "GESTIONAR", "Registrar emergencias"))));
        roleDao.add(new Role(4L, "BOMBERO_PARAMEDICO", "Bombero", RecordStatus.ACTIVE, Set.of()));
        roleDao.add(new Role(3L, "COORDINADOR", "Coordinador", RecordStatus.ACTIVE, Set.of(
                new Permission(3L, "EMERGENCIAS", "CONSULTAR", "Ver emergencias"),
                new Permission(4L, "EMERGENCIAS", "GESTIONAR", "Registrar emergencias"),
                new Permission(5L, "UNIDADES", "CONSULTAR", "Ver unidades y estaciones"),
                new Permission(7L, "HOSPITALES", "CONSULTAR", "Ver hospitales"),
                new Permission(15L, "REPORTES", "CONSULTAR", "Ver dashboard y reportes"),
                new Permission(13L, "INCIDENTES", "CONSULTAR", "Ver incidentes viales"),
                new Permission(14L, "INCIDENTES", "GESTIONAR", "Registrar, validar y cerrar incidentes"),
                new Permission(11L, "RUTAS", "CONSULTAR", "Ver rutas calculadas"),
                new Permission(12L, "RUTAS", "GESTIONAR", "Calcular y recalcular rutas"),
                new Permission(17L, "DESPACHO", "CONSULTAR", "Ver asignaciones"),
                new Permission(18L, "DESPACHO", "GESTIONAR", "Asignar unidades y actualizar el estado de la mision"))));
        String hash = hasher.hash(PASSWORD.toCharArray());
        LocalDateTime registered = LocalDateTime.now(clock);
        userDao.insert(new User(0L, 2L, "1000000001", "Ana", "Pérez", "ana.perez@ejemplo.com",
                "ana.perez", hash, RecordStatus.ACTIVE, registered));
        userDao.insert(new User(0L, 4L, "1000000002", "Luis", "Gómez", "luis.gomez@ejemplo.com",
                "luis.gomez", hash, RecordStatus.ACTIVE, registered));
        userDao.insert(new User(0L, 3L, "1000000003", "Marta", "Ruiz", "marta.ruiz@ejemplo.com",
                "marta.ruiz", hash, RecordStatus.ACTIVE, registered));

        authService = new AuthService(userDao, roleDao, hasher, new AuditService(auditDao, clock), clock);
    }

    public WebSessionController sessions() {
        return new WebSessionController(authService, sessionContext, clock);
    }

    /** A clock that the tests move forward by hand. */
    public static final class MutableClock extends Clock {

        private Instant now;

        public MutableClock(Instant start) {
            this.now = start;
        }

        public void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return BOGOTA;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}