package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.EmergencyStationDAO;
import co.edu.unicesar.rutavida.graph.Edge;
import co.edu.unicesar.rutavida.graph.Node;
import co.edu.unicesar.rutavida.graph.RoadGraph;
import co.edu.unicesar.rutavida.model.Emergency;
import co.edu.unicesar.rutavida.model.EmergencyStation;
import co.edu.unicesar.rutavida.model.EmergencyType;
import co.edu.unicesar.rutavida.model.EmergencyVehicle;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.VehicleType;
import co.edu.unicesar.rutavida.model.enums.EmergencyStatus;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import co.edu.unicesar.rutavida.model.enums.Severity;
import co.edu.unicesar.rutavida.model.enums.VehicleOperationalStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static co.edu.unicesar.rutavida.service.EmergencyTestSupport.CLOCK;

/**
 * A small city for the dispatch tests, without Oracle. Everything is invented test data:
 *
 * <pre>
 *   row 2   (2,0) S ---- (2,1) ---- (2,2)        S = station of unit 4 (no position)
 *             |            |          |
 *   row 1   (1,0) ------ (1,1) A ---- (1,2) E    A = ambulance 1, E = the emergency
 *             |            |          |
 *   row 0   (0,0) F ---- (0,1) ---- (0,2) B      F = fire truck 2, B = busy ambulance 3
 * </pre>
 *
 * Every street is two-way, 220 m long, 40 km/h. Unit 5 is outside the road network.
 */
public final class DispatchTestSupport {

    public static final EmergencyType MEDICAL = new EmergencyType(1, "EMERGENCIA_MEDICA", 9, "Prueba");
    public static final EmergencyType ACCIDENT = new EmergencyType(3, "ACCIDENTE_VIAL", 8, "Prueba");
    public static final List<VehicleType> VEHICLE_TYPES = List.of(
            new VehicleType(1, "AMBULANCIA", 8, "Prueba"),
            new VehicleType(2, "BOMBERO", 8, "Prueba"));

    public static final long AMBULANCE = 1;
    public static final long FIRE_TRUCK = 2;
    public static final long BUSY_AMBULANCE = 3;
    public static final long NO_POSITION_AMBULANCE = 4;
    public static final long FAR_AMBULANCE = 5;

    public final InMemoryEmergencyDAO emergencies = new InMemoryEmergencyDAO();
    public final InMemoryEmergencyTypeDAO emergencyTypes = new InMemoryEmergencyTypeDAO();
    public final InMemoryEmergencyVehicleDAO vehicles = new InMemoryEmergencyVehicleDAO();
    public final InMemoryEmergencyAssignmentDAO assignments = new InMemoryEmergencyAssignmentDAO(vehicles, emergencies);
    public final InMemoryRouteDAO routes = new InMemoryRouteDAO();
    public final InMemoryAuditDAO audit = new InMemoryAuditDAO();
    public final EmergencyTestSupport.CountingTransactions transactions = new EmergencyTestSupport.CountingTransactions();
    public final RoadGraph graph = grid();

    public DispatchTestSupport() {
        emergencyTypes.add(MEDICAL);
        emergencyTypes.add(ACCIDENT);
        vehicles.insert(unit("EJEMPLO-AMB-01", 1, VehicleOperationalStatus.AVAILABLE, at(1, 1)));
        vehicles.insert(unit("EJEMPLO-BOM-01", 2, VehicleOperationalStatus.AVAILABLE, at(0, 0)));
        vehicles.insert(unit("EJEMPLO-AMB-02", 1, VehicleOperationalStatus.BUSY, at(0, 2)));
        vehicles.insert(unit("EJEMPLO-AMB-03", 1, VehicleOperationalStatus.AVAILABLE, null));
        vehicles.insert(unit("EJEMPLO-AMB-04", 1, VehicleOperationalStatus.AVAILABLE, new GeoPoint(10.60, -73.00)));
    }

    /** The service with every fake; the routes are calculated on the small city. */
    public DispatchService service() {
        RouteService routeService = new RouteService(graph, routes, transactions, CLOCK);
        return new DispatchService(emergencies, emergencyTypes, vehicles, () -> VEHICLE_TYPES,
                stations(), assignments, routes, routeService, new AuditService(audit, CLOCK), transactions, CLOCK);
    }

    /** Only the state rules (isDispatchable, changeStatus, moveVehicle...); the fakes are not used. */
    public static DispatchService rules() {
        return new DispatchTestSupport().service();
    }

    /** Saves a new REPORTADA emergency at the node (row, column) and returns its id. */
    public long report(EmergencyType type, int row, int column) {
        return emergencies.insert(new Emergency(0, type.getId(), 7, "Calle de prueba", at(row, column),
                LocalDateTime.now(CLOCK), Severity.HIGH, "PRUEBA", EmergencyStatus.REPORTED, null));
    }

    public static GeoPoint at(int row, int column) {
        return new GeoPoint(10.460 + row * 0.002, -73.260 + column * 0.002);
    }

    private static EmergencyVehicle unit(String code, long typeId, VehicleOperationalStatus status, GeoPoint where) {
        return new EmergencyVehicle(0, typeId, 1, code, null, status, where,
                where == null ? null : LocalDateTime.now(CLOCK).minusMinutes(10));
    }

    /** Station 1, at (2,0): where unit 4 starts because it has no position. */
    private static EmergencyStationDAO stations() {
        EmergencyStation station = new EmergencyStation(1, "EJEMPLO - Base de prueba", "Calle de prueba",
                null, null, at(2, 0), RecordStatus.ACTIVE);
        return new EmergencyStationDAO() {
            @Override
            public Optional<EmergencyStation> findById(long id) {
                return id == 1 ? Optional.of(station) : Optional.empty();
            }

            @Override
            public List<EmergencyStation> findAll() {
                return List.of(station);
            }

            @Override
            public long insert(EmergencyStation newStation) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void update(EmergencyStation changed) {
                throw new UnsupportedOperationException();
            }
        };
    }

    /** 3 x 3 grid of two-way streets, 220 m and 40 km/h each. */
    private static RoadGraph grid() {
        List<Node> nodes = new ArrayList<>();
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                GeoPoint point = at(row, column);
                nodes.add(new Node(row * 3 + column, 100 + row * 3 + column, point.latitude(), point.longitude()));
            }
        }
        List<Edge> edges = new ArrayList<>();
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                int here = row * 3 + column;
                if (column < 2) {
                    twoWay(edges, here, here + 1);
                }
                if (row < 2) {
                    twoWay(edges, here, here + 3);
                }
            }
        }
        return new RoadGraph(nodes, edges);
    }

    private static void twoWay(List<Edge> edges, int a, int b) {
        edges.add(new Edge(edges.size(), 500 + edges.size(), a, b, 220, 40, "Calle de prueba"));
        edges.add(new Edge(edges.size(), 500 + edges.size(), b, a, 220, 40, "Calle de prueba"));
    }
}