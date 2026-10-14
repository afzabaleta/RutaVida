package co.edu.unicesar.rutavida.app;

import co.edu.unicesar.rutavida.dao.TransactionManager;
import co.edu.unicesar.rutavida.dao.jdbc.JdbcTransactionManager;
import co.edu.unicesar.rutavida.event.EventBus;
import co.edu.unicesar.rutavida.event.InMemoryEventBus;
import co.edu.unicesar.rutavida.util.DatabaseConnection;

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
 */
public final class AppContext {

    private final DatabaseConnection database;
    private final JdbcTransactionManager jdbc;
    private final EventBus events;
    private final Clock clock;

    private AppContext(DatabaseConnection database, Clock clock) {
        this.database = Objects.requireNonNull(database, "database");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.jdbc = new JdbcTransactionManager(database::open);
        this.events = new InMemoryEventBus();
        // DAOs, services and controllers will be created here as they are implemented.
    }

    /** Builds the application with the configuration in backend/db.properties. */
    public static AppContext create() {
        return new AppContext(DatabaseConnection.fromEnvironment(), Clock.systemDefaultZone());
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
}
