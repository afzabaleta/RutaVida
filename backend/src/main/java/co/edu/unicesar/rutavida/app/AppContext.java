package co.edu.unicesar.rutavida.app;

import co.edu.unicesar.rutavida.controller.LoginController;
import co.edu.unicesar.rutavida.controller.SessionContext;
import co.edu.unicesar.rutavida.dao.AuditDAO;
import co.edu.unicesar.rutavida.dao.RoleDAO;
import co.edu.unicesar.rutavida.dao.TransactionManager;
import co.edu.unicesar.rutavida.dao.UserDAO;
import co.edu.unicesar.rutavida.dao.jdbc.ClientIdentifierConnectionFactory;
import co.edu.unicesar.rutavida.dao.jdbc.JdbcAuditDAO;
import co.edu.unicesar.rutavida.dao.jdbc.JdbcRoleDAO;
import co.edu.unicesar.rutavida.dao.jdbc.JdbcTransactionManager;
import co.edu.unicesar.rutavida.dao.jdbc.JdbcUserDAO;
import co.edu.unicesar.rutavida.event.EventBus;
import co.edu.unicesar.rutavida.event.InMemoryEventBus;
import co.edu.unicesar.rutavida.service.AdminBootstrapService;
import co.edu.unicesar.rutavida.service.AuditService;
import co.edu.unicesar.rutavida.service.AuthService;
import co.edu.unicesar.rutavida.util.DatabaseConnection;
import co.edu.unicesar.rutavida.util.PasswordHasher;
import co.edu.unicesar.rutavida.util.Pbkdf2PasswordHasher;

import java.time.Clock;
import java.util.Objects;

/**
 * Composition root: the single place where implementations are chosen and
 * connected (manual dependency injection, no framework).
 *
 * <p>Rule for the team: services, controllers and views receive their dependencies
 * through the constructor and NEVER use {@code new JdbcXxxDAO(...)} themselves.
 * When a new DAO, service or controller is created, it is wired here:
 *
 * <pre>{@code
 * // dao.jdbc implementations (only this class knows them)
 * EmergencyDAO emergencyDao = new JdbcEmergencyDAO(jdbc);
 * // services receive interfaces
 * EmergencyService emergencyService = new EmergencyService(emergencyDao, transactions, events, clock);
 * // controllers receive services
 * EmergencyController emergencyController = new EmergencyController(emergencyService);
 * }</pre>
 *
 * <p>SOLID Dependency Inversion: high-level classes depend on abstractions
 * ({@link TransactionManager}, {@link EventBus}, DAO interfaces, {@link Clock}) and
 * this class decides the concrete implementation. In tests, a service can be built
 * with fakes instead of Oracle.
 *
 * <p>One AppContext holds one {@link SessionContext}: one logged-in user per running
 * application. The REST API (phase 7) will need one session per request.
 */
public final class AppContext {

    private final DatabaseConnection database;
    private final JdbcTransactionManager jdbc;
    private final EventBus events;
    private final Clock clock;

    private final SessionContext session;
    private final AdminBootstrapService adminBootstrap;
    private final LoginController loginController;

    private AppContext(DatabaseConnection database, Clock clock) {
        this.database = Objects.requireNonNull(database, "database");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.session = new SessionContext();
        // Every connection tells Oracle who is logged in (CLIENT_IDENTIFIER, used by the triggers).
        this.jdbc = new JdbcTransactionManager(
                new ClientIdentifierConnectionFactory(database::open, this::currentUserId));
        this.events = new InMemoryEventBus();

        // Security (A2 + S2 + S3)
        UserDAO userDao = new JdbcUserDAO(jdbc);
        RoleDAO roleDao = new JdbcRoleDAO(jdbc);
        AuditDAO auditDao = new JdbcAuditDAO(jdbc);
        PasswordHasher passwordHasher = new Pbkdf2PasswordHasher();
        AuditService auditService = new AuditService(auditDao, clock);

        AuthService authService = new AuthService(userDao, roleDao, passwordHasher, auditService, clock);
        this.adminBootstrap = new AdminBootstrapService(
                userDao, roleDao, passwordHasher, auditService, jdbc, clock);
        this.loginController = new LoginController(authService, session);
        // Next DAOs, services and controllers are created here as they are implemented.
    }

    /** Builds the application with the configuration in backend/db.properties. */
    public static AppContext create() {
        return new AppContext(DatabaseConnection.fromEnvironment(), Clock.systemDefaultZone());
    }

    /** Id of the logged-in user, or null when nobody is logged in. */
    private Long currentUserId() {
        return session.isActive() ? session.current().user().getId() : null;
    }

    public TransactionManager transactions() {
        return jdbc;
    }

    public EventBus events() {
        return events;
    }

    /** Injected clock: tests and the simulator can control time. */
    public Clock clock() {
        return clock;
    }

    public DatabaseConnection database() {
        return database;
    }

    public SessionContext session() {
        return session;
    }

    public LoginController loginController() {
        return loginController;
    }

    /** Only for the CreateAdminUser tool: creating users normally needs a session. */
    public AdminBootstrapService adminBootstrap() {
        return adminBootstrap;
    }

    /** For integration tests of this package: the JDBC side of {@link #transactions()}. */
    JdbcTransactionManager jdbc() {
        return jdbc;
    }
}