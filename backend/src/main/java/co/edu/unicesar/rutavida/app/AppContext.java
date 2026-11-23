package co.edu.unicesar.rutavida.app;

import co.edu.unicesar.rutavida.controller.AlertController;
import co.edu.unicesar.rutavida.controller.AuditController;
import co.edu.unicesar.rutavida.controller.CatalogController;
import co.edu.unicesar.rutavida.controller.CitizenController;
import co.edu.unicesar.rutavida.controller.DashboardController;
import co.edu.unicesar.rutavida.controller.ReportController;
import co.edu.unicesar.rutavida.controller.DispatchController;
import co.edu.unicesar.rutavida.service.DispatchService;
import co.edu.unicesar.rutavida.controller.EmergencyController;
import co.edu.unicesar.rutavida.controller.FleetController;
import co.edu.unicesar.rutavida.controller.GpsController;
import co.edu.unicesar.rutavida.controller.HospitalController;
import co.edu.unicesar.rutavida.controller.IncidentController;
import co.edu.unicesar.rutavida.controller.LoginController;
import co.edu.unicesar.rutavida.controller.PanicController;
import co.edu.unicesar.rutavida.controller.RoadMapController;
import co.edu.unicesar.rutavida.controller.SimulationController;
import co.edu.unicesar.rutavida.controller.TrackingController;
import co.edu.unicesar.rutavida.controller.SessionContext;
import co.edu.unicesar.rutavida.controller.UserController;
import co.edu.unicesar.rutavida.controller.WebSessionController;
import co.edu.unicesar.rutavida.dao.AuditDAO;
import co.edu.unicesar.rutavida.dao.EmergencyTypeDAO;
import co.edu.unicesar.rutavida.dao.RoleDAO;
import co.edu.unicesar.rutavida.dao.TransactionManager;
import co.edu.unicesar.rutavida.dao.UserDAO;
import co.edu.unicesar.rutavida.dao.jdbc.ClientIdentifierConnectionFactory;
import co.edu.unicesar.rutavida.dao.jdbc.PooledConnectionFactory;
import co.edu.unicesar.rutavida.dao.jdbc.JdbcAuditDAO;
import co.edu.unicesar.rutavida.dao.jdbc.JdbcAlertDAO;
import co.edu.unicesar.rutavida.dao.jdbc.JdbcEmergencyAssignmentDAO;
import co.edu.unicesar.rutavida.dao.jdbc.JdbcEmergencyDAO;
import co.edu.unicesar.rutavida.dao.jdbc.JdbcEmergencyStationDAO;
import co.edu.unicesar.rutavida.dao.jdbc.JdbcEmergencyTypeDAO;
import co.edu.unicesar.rutavida.dao.jdbc.JdbcEmergencyVehicleDAO;
import co.edu.unicesar.rutavida.dao.jdbc.JdbcHospitalDAO;
import co.edu.unicesar.rutavida.dao.jdbc.JdbcRoadIncidentDAO;
import co.edu.unicesar.rutavida.dao.jdbc.JdbcRoadMapDAO;
import co.edu.unicesar.rutavida.dao.jdbc.JdbcRoadNodeDAO;
import co.edu.unicesar.rutavida.dao.jdbc.JdbcRoadSegmentDAO;
import co.edu.unicesar.rutavida.dao.jdbc.JdbcRoleDAO;
import co.edu.unicesar.rutavida.dao.jdbc.JdbcPanicReportDAO;
import co.edu.unicesar.rutavida.dao.jdbc.JdbcRouteDAO;
import co.edu.unicesar.rutavida.dao.jdbc.JdbcTrafficLightDAO;
import co.edu.unicesar.rutavida.dao.jdbc.JdbcTrafficMapDAO;
import co.edu.unicesar.rutavida.dao.jdbc.JdbcTrafficReadingDAO;
import co.edu.unicesar.rutavida.dao.jdbc.JdbcTransactionManager;
import co.edu.unicesar.rutavida.dao.jdbc.JdbcUserDAO;
import co.edu.unicesar.rutavida.dao.jdbc.JdbcVehicleLocationDAO;
import co.edu.unicesar.rutavida.dao.jdbc.JdbcVehicleTypeDAO;
import co.edu.unicesar.rutavida.dao.jdbc.JdbcUnitDriverDAO;
import co.edu.unicesar.rutavida.dao.jdbc.JdbcCitizenSessionDAO;
import co.edu.unicesar.rutavida.dao.jdbc.JdbcDashboardDAO;
import co.edu.unicesar.rutavida.dao.jdbc.JdbcReportDAO;
import co.edu.unicesar.rutavida.graph.EmergencyCost;
import co.edu.unicesar.rutavida.graph.RoadGraph;
import co.edu.unicesar.rutavida.service.AdminBootstrapService;
import co.edu.unicesar.rutavida.service.AuditQueryService;
import co.edu.unicesar.rutavida.service.AuditService;
import co.edu.unicesar.rutavida.service.AlertService;
import co.edu.unicesar.rutavida.service.AuthService;
import co.edu.unicesar.rutavida.service.CatalogService;
import co.edu.unicesar.rutavida.service.CitizenService;
import co.edu.unicesar.rutavida.service.DashboardService;
import co.edu.unicesar.rutavida.service.ReportService;
import co.edu.unicesar.rutavida.service.EmergencyService;
import co.edu.unicesar.rutavida.service.FleetService;
import co.edu.unicesar.rutavida.service.GraphLoader;
import co.edu.unicesar.rutavida.service.GpsTrackingService;
import co.edu.unicesar.rutavida.service.HospitalService;
import co.edu.unicesar.rutavida.service.IncidentService;
import co.edu.unicesar.rutavida.service.LiveMapService;
import co.edu.unicesar.rutavida.service.PanicService;
import co.edu.unicesar.rutavida.service.RoadConditionService;
import co.edu.unicesar.rutavida.service.RoadMapService;
import co.edu.unicesar.rutavida.service.RouteMonitor;
import co.edu.unicesar.rutavida.service.RouteService;
import co.edu.unicesar.rutavida.service.TrackingService;
import co.edu.unicesar.rutavida.service.TrafficMapService;
import co.edu.unicesar.rutavida.service.TrafficRecorder;
import co.edu.unicesar.rutavida.service.UserService;
import co.edu.unicesar.rutavida.simulation.PatrolSimulator;
import co.edu.unicesar.rutavida.simulation.SimulationEngine;
import co.edu.unicesar.rutavida.simulation.VehicleMovementSimulator;
import co.edu.unicesar.rutavida.traffic.SimulatedTrafficProvider;
import co.edu.unicesar.rutavida.util.DatabaseConnection;
import co.edu.unicesar.rutavida.util.PasswordHasher;

import java.time.Clock;
import java.util.Objects;
import java.util.Random;

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
 * EmergencyService emergencyService = new EmergencyService(emergencyDao, transactions, clock);
 * // controllers receive services
 * EmergencyController emergencyController = new EmergencyController(emergencyService);
 * }</pre>
 *
 * <p>SOLID Dependency Inversion: high-level classes depend on abstractions
 * ({@link TransactionManager}, DAO interfaces, {@link Clock}) and
 * this class decides the concrete implementation. In tests, a service can be built
 * with fakes instead of Oracle.
 *
 * <p>One AppContext holds one {@link SessionContext}. The console tools use its shared
 * session; the web server gives each request its own session through
 * {@link WebSessionController} (many users at once).
 */
public final class AppContext {

    /** Seed of the simulated traffic: the same demo gives the same traffic every time (K2). */
    static final long TRAFFIC_SEED = 2026L;
    /** Seed of the rounds of the patrols: the same demo every time (DT-10). */
    private static final long PATROL_SEED = 2026L;

    private final DatabaseConnection database;
    private final JdbcTransactionManager jdbc;
    private final Clock clock;

    private final SessionContext session;
    private final AdminBootstrapService adminBootstrap;
    private final LoginController loginController;
    private final WebSessionController webSessionController;
    private final CatalogController catalogController;
    private final EmergencyController emergencyController;
    private final DashboardController dashboardController;
    private final ReportController reportController;
    private final IncidentController incidentController;
    private final RoadMapController roadMapController;
    private final AlertService alertService;
    private final AlertController alertController;
    private final UserController userController;
    private final HospitalController hospitalController;
    private final FleetController fleetController;
    private final TrackingController trackingController;
    private final GpsController gpsController;
    private final AuditController auditController;
    private final GraphLoader graphLoader;
    private final AuthService authService;
    private final AuditService auditService;
    private final UserDAO userDao;
    private final RoleDAO roleDao;
    private final EmergencyService emergencyService;
    private RoadGraph roadGraph;
    private RouteService routeService;
    private SimulationEngine simulationEngine;
    private SimulationController simulationController;
    private DispatchService dispatchService;
    private DispatchController dispatchController;
    private PanicController panicController;
    private final CitizenService citizenService;
    private final CitizenController citizenController;

    private AppContext(DatabaseConnection database, Clock clock) {
        this.database = Objects.requireNonNull(database, "database");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.session = new SessionContext();
        // Connections are reused (a new one to Oracle Cloud costs about half a second), and every
        // one handed out tells Oracle who is logged in (CLIENT_IDENTIFIER, used by the triggers).
        this.jdbc = new JdbcTransactionManager(new ClientIdentifierConnectionFactory(
                new PooledConnectionFactory(database::open), this::currentUserId));

        // Security (A2 + S2 + S3)
        this.userDao = new JdbcUserDAO(jdbc);
        this.roleDao = new JdbcRoleDAO(jdbc);
        AuditDAO auditDao = new JdbcAuditDAO(jdbc);
        PasswordHasher passwordHasher = new PasswordHasher();
        this.auditService = new AuditService(auditDao, clock);
        this.authService = new AuthService(userDao, roleDao, passwordHasher, auditService, clock);
        this.adminBootstrap = new AdminBootstrapService(
                userDao, roleDao, passwordHasher, auditService, jdbc, clock);
        this.loginController = new LoginController(authService, session);
        // Consult the audit (CU-21, A18)
        this.auditController = new AuditController(new AuditQueryService(auditDao, userDao, clock),
                authService, session);

        // Catalogs for the web (A12a + A12b, DAOs of A7b and A7c)
        EmergencyTypeDAO emergencyTypeDao = new JdbcEmergencyTypeDAO(jdbc);
        this.catalogController = new CatalogController(new CatalogService(emergencyTypeDao,
                new JdbcVehicleTypeDAO(jdbc), new JdbcEmergencyStationDAO(jdbc),
                new JdbcEmergencyVehicleDAO(jdbc), new JdbcHospitalDAO(jdbc)));

        // Emergencies (S6 + A7d): register, active, history
        this.emergencyService = new EmergencyService(new JdbcEmergencyDAO(jdbc), emergencyTypeDao,
                new JdbcEmergencyAssignmentDAO(jdbc), new JdbcRouteDAO(jdbc), auditService, jdbc, clock);
        this.emergencyController = new EmergencyController(emergencyService, authService, session);

        // Dashboard (A14): one query on the views and fn_tiempo_respuesta of 04_plsql.sql
        this.dashboardController = new DashboardController(
                new DashboardService(new JdbcDashboardDAO(jdbc), clock));

        // Operations report of a period (A23): by type, severity, vehicle type and day
        this.reportController = new ReportController(new ReportService(new JdbcReportDAO(jdbc), clock));

        // Road incidents (S7) and their segments on the map (A12c)
        IncidentService incidentService = new IncidentService(new JdbcRoadIncidentDAO(jdbc),
                new JdbcRoadSegmentDAO(jdbc), auditService, jdbc, clock);
        this.incidentController = new IncidentController(incidentService, authService, session);
        // The graph is read on the first nearest-segment search (9.4); WebServer loads it at start.
        this.roadMapController = new RoadMapController(new RoadMapService(new JdbcRoadMapDAO(jdbc), this::roadGraph));

        // Alerts of each user (S8, A12f). Dispatch, recalculation and simulation (K7, K8, K9)
        // create them through alertService(), so everyone shares this instance.
        this.alertService = new AlertService(new JdbcAlertDAO(jdbc), userDao, roleDao, jdbc, clock);
        this.alertController = new AlertController(alertService, session);

        // Citizens of the panic button (DT-13): registration, 30-day phone sessions and blocking.
        // The web sessions need them, so they are created after the alerts.
        this.citizenService = new CitizenService(userDao, roleDao, new JdbcCitizenSessionDAO(jdbc),
                passwordHasher, auditService, alertService, jdbc, clock);
        this.citizenController = new CitizenController(citizenService);
        this.webSessionController = new WebSessionController(authService, session, clock, citizenService);

        // Administration (S9, S10, A17): users and roles, hospitals, units and stations
        this.userController = new UserController(new UserService(userDao, roleDao, passwordHasher,
                auditService, jdbc, clock), authService, session);
        this.hospitalController = new HospitalController(
                new HospitalService(new JdbcHospitalDAO(jdbc), auditService, jdbc), authService, session);
        this.fleetController = new FleetController(new FleetService(new JdbcEmergencyVehicleDAO(jdbc),
                new JdbcEmergencyStationDAO(jdbc), new JdbcVehicleTypeDAO(jdbc), alertService, auditService, jdbc),
                authService, session);

        // Live map (A12e): positions, open missions and unread alerts. The simulation engine
        // (K9, simulationEngine()) gives the "running" flag through isSimulationRunning().
        this.trackingController = new TrackingController(new LiveMapService(new JdbcEmergencyVehicleDAO(jdbc),
                new JdbcVehicleTypeDAO(jdbc), new JdbcEmergencyAssignmentDAO(jdbc), new JdbcRouteDAO(jdbc),
                new JdbcAlertDAO(jdbc), new JdbcVehicleLocationDAO(jdbc), this::isSimulationRunning, clock),
                authService, session);
        this.gpsController = new GpsController(new GpsTrackingService(new JdbcUnitDriverDAO(jdbc),
                new JdbcEmergencyVehicleDAO(jdbc), new JdbcVehicleLocationDAO(jdbc), jdbc, clock),
                authService, session);
        // Next DAOs, services and controllers are created here as they are implemented.

        // Road network (A4 + K5). The graph is not loaded here: graphLoader().load() reads Oracle.
        this.graphLoader = new GraphLoader(
                new JdbcRoadNodeDAO(jdbc), new JdbcRoadSegmentDAO(jdbc), new JdbcTrafficLightDAO(jdbc));
    }

    /** Builds the application with the configuration in backend/db.properties. */
    public static AppContext create() {
        return new AppContext(DatabaseConnection.fromEnvironment(), Clock.systemDefaultZone());
    }

    /**
     * Id of the logged-in user (of this web request, or the shared session of the console
     * tools), or null when nobody is logged in. Read when a connection is opened.
     */
    private Long currentUserId() {
        return session.isActive() ? session.current().user().getId() : null;
    }

    public TransactionManager transactions() {
        return jdbc;
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

    /** Sessions of the web server (token per browser tab, DT-08). */
    public WebSessionController webSessionController() {
        return webSessionController;
    }

    public CatalogController catalogController() {
        return catalogController;
    }

    public EmergencyController emergencyController() {
        return emergencyController;
    }


    /** Indicators of the main panel (A14). */
    public DashboardController dashboardController() {
        return dashboardController;
    }

    /** Operations report of a period (A23). */
    public ReportController reportController() {
        return reportController;
    }

    /** Road incidents: report, validate and list (S7, A12c). */
    public IncidentController incidentController() {
        return incidentController;
    }

    /** Road data for the map: incidents with their segments (A12c). */
    public RoadMapController roadMapController() {
        return roadMapController;
    }


    /** Creates the alerts of the users; K7, K8 and K9 receive this one (S8, A12f). */
    public AlertService alertService() {
        return alertService;
    }

    /** Alerts of the user of the session (A12f). */
    public AlertController alertController() {
        return alertController;
    }

    /** Users and roles (CU-03, S9). */
    public UserController userController() {
        return userController;
    }

    /** Hospitals (CU-05, S9). */
    public HospitalController hospitalController() {
        return hospitalController;
    }

    /** Units and stations (CU-04, S10). */
    public FleetController fleetController() {
        return fleetController;
    }

    /** Consult the audit (CU-21, A18). */
    public AuditController auditController() {
        return auditController;
    }

    /** Live map (docs/api.md 8.1, A12e). */
    public TrackingController trackingController() {
        return trackingController;
    }

    /** Phone GPS reporting for the driver explicitly assigned to a unit. */
    public GpsController gpsController() {
        return gpsController;
    }

    /**
     * True while the simulation moves the units. It does not create the engine: before the
     * first start the answer is false without loading the road graph.
     */
    public boolean isSimulationRunning() {
        SimulationEngine engine;
        synchronized (this) {
            engine = simulationEngine;
        }
        return engine != null && engine.isRunning();
    }

    /** Builds the road graph from Oracle each time load() is called (it takes well under a second). */
    public GraphLoader graphLoader() {
        return graphLoader;
    }

    /**
     * Route service on the real road network. The graph is loaded from Oracle the first
     * time this is called and then shared, so every route uses the same traffic state.
     */
    public synchronized RouteService routeService() {
        if (routeService == null) {
            routeService = new RouteService(roadGraph(), new JdbcRouteDAO(jdbc), jdbc, clock);
        }
        return routeService;
    }

    /**
     * The road graph of the application, loaded from Oracle the first time. The routes and the
     * simulation share this one instance: the traffic the simulation applies is the traffic
     * the routes see.
     */
    public synchronized RoadGraph roadGraph() {
        if (roadGraph == null) {
            roadGraph = graphLoader.load();
        }
        return roadGraph;
    }

    /**
     * The simulation (CU-19, K9), created the first time and stopped until someone starts it.
     * Every part works on the shared graph and reads and writes Oracle; everything it
     * produces is marked SIMULACION.
     */
    /**
     * The police patrols on their rounds (DT-10), with their own movement and their own
     * tracking, so they never touch the units on a mission.
     */
    private PatrolSimulator patrols(RoadGraph graph) {
        VehicleMovementSimulator patrolMovement = new VehicleMovementSimulator(graph, new EmergencyCost(), clock);
        TrackingService patrolTracking = new TrackingService(patrolMovement, new JdbcEmergencyVehicleDAO(jdbc),
                new JdbcVehicleLocationDAO(jdbc), dispatchService(), jdbc);
        return new PatrolSimulator(graph, patrolMovement, patrolTracking, new JdbcEmergencyVehicleDAO(jdbc),
                new JdbcVehicleTypeDAO(jdbc), new EmergencyCost(), new Random(PATROL_SEED),
                PatrolSimulator.DEFAULT_MIN_METERS, PatrolSimulator.DEFAULT_MAX_METERS,
                PatrolSimulator.DEFAULT_SEPARATION_METERS);
    }

    public synchronized SimulationEngine simulationEngine() {
        if (simulationEngine == null) {
            RoadGraph graph = roadGraph();
            VehicleMovementSimulator movement = new VehicleMovementSimulator(graph, new EmergencyCost(), clock);
            RouteMonitor monitor = new RouteMonitor(new JdbcRouteDAO(jdbc), routeService(),
                    new JdbcEmergencyVehicleDAO(jdbc), new JdbcVehicleTypeDAO(jdbc),
                    new JdbcEmergencyAssignmentDAO(jdbc), alertService, jdbc);
            TrackingService tracking = new TrackingService(movement, new JdbcEmergencyVehicleDAO(jdbc),
                    new JdbcVehicleLocationDAO(jdbc), dispatchService(), jdbc);
            simulationEngine = new SimulationEngine(graph,
                    new SimulatedTrafficProvider(graph, new Random(TRAFFIC_SEED), clock),
                    new RoadConditionService(graph, new JdbcRoadIncidentDAO(jdbc), clock),
                    new TrafficRecorder(graph, new JdbcTrafficReadingDAO(jdbc), jdbc, clock),
                    monitor, movement, tracking, dispatchService(), clock,
                    SimulationEngine.DEFAULT_TICK, SimulationEngine.DEFAULT_TRAFFIC_EVERY, patrols(graph));
        }
        return simulationEngine;
    }

    /** Control of the simulation and its traffic layer (docs/api.md 8.2 and 10). */
    public synchronized SimulationController simulationController() {
        if (simulationController == null) {
            simulationController = new SimulationController(simulationEngine(),
                    new TrafficMapService(new JdbcTrafficMapDAO(jdbc), new JdbcRoadMapDAO(jdbc), clock),
                    authService, session);
        }
        return simulationController;
    }

    /** Stops the simulation if it runs; the web server calls it when it stops. */
    public void stopSimulation() {
        SimulationEngine engine;
        synchronized (this) {
            engine = simulationEngine;
        }
        if (engine != null) {
            engine.stop();
        }
    }

    /**
     * Dispatch (K7). Created the first time it is used because it needs routeService(),
     * which loads the road graph from Oracle.
     */
    public synchronized DispatchController dispatchController() {
        if (dispatchController == null) {
            dispatchController = new DispatchController(dispatchService(), authService, session);
        }
        return dispatchController;
    }

    /**
     * The dispatch rules (K7), shared by the dispatch page and the panic button. Created the
     * first time, because it needs the road graph (routeService()).
     */
    public synchronized DispatchService dispatchService() {
        if (dispatchService == null) {
            dispatchService = new DispatchService(new JdbcEmergencyDAO(jdbc),
                    new JdbcEmergencyTypeDAO(jdbc), new JdbcEmergencyVehicleDAO(jdbc),
                    new JdbcVehicleTypeDAO(jdbc), new JdbcEmergencyStationDAO(jdbc),
                    new JdbcEmergencyAssignmentDAO(jdbc), new JdbcRouteDAO(jdbc), new JdbcHospitalDAO(jdbc),
                    routeService(), auditService, jdbc, clock);
        }
        return dispatchService;
    }

    /**
     * Panic button (CU-22, S11 + A20b). Created the first time, like the dispatch: a validated
     * alert is dispatched with the same DispatchService and the zone is the loaded road graph.
     */
    public synchronized PanicController panicController() {
        if (panicController == null) {
            PanicService panicService = new PanicService(new JdbcPanicReportDAO(jdbc),
                    new JdbcEmergencyTypeDAO(jdbc), userDao, roleDao, emergencyService, dispatchService(),
                    routeService(), alertService, auditService, citizenService, jdbc, clock);
            panicController = new PanicController(panicService, authService, session);
        }
        return panicController;
    }

    /** Citizen registration of the panic button (CU-23, DT-13). */
    public CitizenController citizenController() {
        return citizenController;
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
