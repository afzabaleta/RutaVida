package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.DataAccessException;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.service.DispatchService;
import co.edu.unicesar.rutavida.model.EmergencyAssignment;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.Route;
import co.edu.unicesar.rutavida.model.RouteSegment;
import co.edu.unicesar.rutavida.model.VehicleLocation;
import co.edu.unicesar.rutavida.model.enums.AssignmentStatus;
import co.edu.unicesar.rutavida.model.enums.PositionSource;
import co.edu.unicesar.rutavida.model.enums.RouteStatus;
import co.edu.unicesar.rutavida.model.enums.RouteType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static co.edu.unicesar.rutavida.dao.jdbc.OracleTestDatabase.inRolledBackTransaction;
import static co.edu.unicesar.rutavida.dao.jdbc.OracleTestDatabase.number;
import static co.edu.unicesar.rutavida.dao.jdbc.OracleTestDatabase.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * JdbcEmergencyAssignmentDAO, JdbcRouteDAO and JdbcVehicleLocationDAO against the local
 * Oracle. Each test builds its own coordinator, station, unit, emergency and three road
 * segments (all named PRUEBA_A6…) in a transaction that is rolled back.
 * Skipped without Oracle or without the procedures of 04_plsql.sql.
 */
class JdbcDispatchDAOIntegrationTest {

    private static final DispatchService DISPATCH = new DispatchService();

    /** Whole seconds: Oracle TIMESTAMP keeps them exactly. */
    private static final LocalDateTime T = LocalDateTime.of(2026, 10, 24, 8, 0);

    private static JdbcTransactionManager jdbc;
    private static JdbcEmergencyAssignmentDAO assignmentDao;
    private static JdbcRouteDAO routeDao;
    private static JdbcVehicleLocationDAO locationDao;

    /** Ids of the test rows; segments go A→B, B→C and C→D. */
    private record Fixture(long coordinator, long vehicle, long emergency, long[] segments) {
    }

    @BeforeAll
    static void connect() {
        jdbc = OracleTestDatabase.connectOrSkip();
        assumeTrue(number(jdbc, "SELECT COUNT(*) FROM user_objects WHERE object_name = 'SP_ASIGNAR_UNIDAD'"
                + " AND status = 'VALID'") == 1, "Run database/04_plsql.sql first: dispatch tests skipped");
        assignmentDao = new JdbcEmergencyAssignmentDAO(jdbc);
        routeDao = new JdbcRouteDAO(jdbc);
        locationDao = new JdbcVehicleLocationDAO(jdbc);
    }

    private static Fixture createFixture() {
        run(jdbc, "INSERT INTO usuario (id_rol, documento, nombres, apellidos, email, username, password_hash)"
                + " SELECT id_rol, 'PRUEBAA6001', 'Prueba', 'A6', 'prueba.a6@ejemplo.com', 'prueba_a6', 'NO_ES_UN_HASH'"
                + " FROM rol WHERE nombre_rol = 'COORDINADOR'");
        run(jdbc, "INSERT INTO estacion_emergencia (nombre, direccion, latitud, longitud)"
                + " VALUES ('ESTACION PRUEBA A6', 'Direccion de prueba', 10.4631, -73.2532)");
        run(jdbc, "INSERT INTO vehiculo_emergencia (id_tipo_vehiculo, id_estacion, codigo_unidad)"
                + " SELECT t.id_tipo_vehiculo, e.id_estacion, 'PRUEBA-A6-01' FROM tipo_vehiculo t, estacion_emergencia e"
                + " WHERE t.nombre = 'AMBULANCIA' AND e.nombre = 'ESTACION PRUEBA A6'");
        run(jdbc, "INSERT INTO emergencia (id_tipo_emergencia, id_usuario_reporta, direccion_referencia, latitud,"
                + " longitud, severidad) SELECT t.id_tipo_emergencia, u.id_usuario, 'PRUEBA A6 Parque La Popa',"
                + " 10.467172, -73.265099, 'ALTA' FROM tipo_emergencia t, usuario u"
                + " WHERE t.nombre = 'EMERGENCIA_MEDICA' AND u.username = 'prueba_a6'");
        for (String node : new String[]{"A", "B", "C", "D"}) {
            run(jdbc, "INSERT INTO nodo_vial (referencia, latitud, longitud) VALUES (?, 10.46, -73.26)",
                    "PRUEBA_A6_" + node);
        }
        long[] segments = new long[3];
        String[][] pairs = {{"A", "B"}, {"B", "C"}, {"C", "D"}};
        for (int i = 0; i < pairs.length; i++) {
            String name = "VIA PRUEBA A6 " + (i + 1);
            run(jdbc, "INSERT INTO tramo_vial (id_nodo_origen, id_nodo_destino, nombre_via, distancia_m,"
                            + " velocidad_max_kmh) SELECT a.id_nodo, b.id_nodo, ?, 200, 40 FROM nodo_vial a, nodo_vial b"
                            + " WHERE a.referencia = ? AND b.referencia = ?",
                    name, "PRUEBA_A6_" + pairs[i][0], "PRUEBA_A6_" + pairs[i][1]);
            segments[i] = number(jdbc, "SELECT id_tramo FROM tramo_vial WHERE nombre_via = ?", name);
        }
        return new Fixture(
                number(jdbc, "SELECT id_usuario FROM usuario WHERE username = 'prueba_a6'"),
                number(jdbc, "SELECT id_vehiculo FROM vehiculo_emergencia WHERE codigo_unidad = 'PRUEBA-A6-01'"),
                number(jdbc, "SELECT id_emergencia FROM emergencia WHERE direccion_referencia = 'PRUEBA A6 Parque La Popa'"),
                segments);
    }

    private static EmergencyAssignment newAssignment(Fixture f) {
        return new EmergencyAssignment(0, f.emergency(), f.vehicle(), f.coordinator(), T,
                null, null, AssignmentStatus.ASSIGNED);
    }

    private static Route newRoute(Fixture f, Long hospitalId, RouteType type) {
        return new Route(0, f.emergency(), f.vehicle(), hospitalId, type, 0.6, 1.2, 80.5, T,
                type == RouteType.MAIN, RouteStatus.PROPOSED, List.of(
                new RouteSegment(1, f.segments()[0], 18.0, 1.0, 1),
                new RouteSegment(2, f.segments()[1], 27.5, 1.5, null),
                new RouteSegment(3, f.segments()[2], null, null, 0)));
    }

    private static String text(String sql, long id) {
        return jdbc.withConnection(connection -> {
            try (var statement = connection.prepareStatement(sql)) {
                statement.setLong(1, id);
                try (var row = statement.executeQuery()) {
                    row.next();
                    return row.getString(1);
                }
            }
        });
    }

    @Test
    void anAssignmentIsCreatedByTheProcedure() {
        inRolledBackTransaction(jdbc, () -> {
            Fixture f = createFixture();
            EmergencyAssignment assignment = newAssignment(f);

            long id = assignmentDao.insert(assignment);

            assertTrue(id > 0);
            assertEquals(id, assignment.getId());
            assertEquals("OCUPADO", text("SELECT estado_operativo FROM vehiculo_emergencia WHERE id_vehiculo = ?",
                    f.vehicle()));
            assertEquals("ASIGNADA", text("SELECT estado FROM emergencia WHERE id_emergencia = ?", f.emergency()));

            EmergencyAssignment read = assignmentDao.findById(id).orElseThrow();
            assertEquals(f.emergency(), read.getEmergencyId());
            assertEquals(f.vehicle(), read.getVehicleId());
            assertEquals(f.coordinator(), read.getCoordinatorUserId());
            assertEquals(T, read.getAssignedAt());
            assertEquals(AssignmentStatus.ASSIGNED, read.getStatus());
            assertEquals(id, assignmentDao.findOpenByVehicle(f.vehicle()).orElseThrow().getId());
            assertEquals(1, assignmentDao.findOpenByEmergency(f.emergency()).size());
        });
    }

    @Test
    void theMissionIsSavedAndTheTriggerAuditsEachChange() {
        inRolledBackTransaction(jdbc, () -> {
            Fixture f = createFixture();
            EmergencyAssignment assignment = newAssignment(f);
            long id = assignmentDao.insert(assignment);

            DISPATCH.changeStatus(assignment, AssignmentStatus.EN_ROUTE, T.plusMinutes(2));
            assignmentDao.update(assignment);
            DISPATCH.changeStatus(assignment, AssignmentStatus.ON_SCENE, T.plusMinutes(9));
            assignmentDao.update(assignment);

            EmergencyAssignment read = assignmentDao.findById(id).orElseThrow();
            assertEquals(AssignmentStatus.ON_SCENE, read.getStatus());
            assertEquals(T.plusMinutes(2), read.getDepartedAt());
            assertEquals(T.plusMinutes(9), read.getArrivedAt());
            assertEquals(1, number(jdbc, "SELECT COUNT(*) FROM auditoria WHERE modulo = 'DESPACHO'"
                    + " AND registro_id = ? AND detalle = 'ASIGNADA -> EN_RUTA'", id));
            assertEquals(1, number(jdbc, "SELECT COUNT(*) FROM auditoria WHERE modulo = 'DESPACHO'"
                    + " AND registro_id = ? AND detalle = 'EN_RUTA -> EN_SITIO'", id));

            DISPATCH.changeStatus(assignment, AssignmentStatus.FINISHED, T.plusMinutes(30));
            assignmentDao.update(assignment);
            assertTrue(assignmentDao.findOpenByVehicle(f.vehicle()).isEmpty(), "a finished mission is not open");
            assertTrue(assignmentDao.findOpenByEmergency(f.emergency()).isEmpty());
            assertEquals(1, assignmentDao.findByEmergency(f.emergency()).size(), "but it stays in the history");
        });
    }

    @Test
    void aBusyUnitIsRejectedByTheProcedure() {
        inRolledBackTransaction(jdbc, () -> {
            Fixture f = createFixture();
            assignmentDao.insert(newAssignment(f));

            EmergencyAssignment second = newAssignment(f);
            BusinessRuleException busy = assertThrows(BusinessRuleException.class,
                    () -> assignmentDao.insert(second));
            assertEquals("La unidad " + f.vehicle() + " no esta disponible (OCUPADO)", busy.getMessage());
            assertEquals(0, second.getId(), "a rejected assignment gets no id");
        });
    }

    @Test
    void wrongAssignmentCallsAreRejected() {
        EmergencyAssignment saved = new EmergencyAssignment(999_999_999, 1, 1, 1, T, null, null,
                AssignmentStatus.ASSIGNED);
        assertThrows(IllegalArgumentException.class, () -> assignmentDao.insert(saved));
        assertThrows(NotFoundException.class, () -> assignmentDao.update(saved));

        EmergencyAssignment onTheWay = new EmergencyAssignment(0, 1, 1, 1, T, T.plusMinutes(1), null,
                AssignmentStatus.EN_ROUTE);
        assertThrows(IllegalArgumentException.class, () -> assignmentDao.insert(onTheWay));
        assertThrows(IllegalArgumentException.class, () -> assignmentDao.update(onTheWay));
        assertTrue(assignmentDao.findById(-1).isEmpty());
    }

    @Test
    void aRouteIsSavedWithItsSegmentsInOrder() {
        inRolledBackTransaction(jdbc, () -> {
            Fixture f = createFixture();
            Route route = newRoute(f, null, RouteType.MAIN);

            long id = routeDao.insert(route);

            assertEquals(id, route.getId());
            assertEquals(3, number(jdbc, "SELECT COUNT(*) FROM ruta_tramo WHERE id_ruta = ?", id));
            Route read = routeDao.findById(id).orElseThrow();
            assertEquals(f.emergency(), read.getEmergencyId());
            assertEquals(RouteType.MAIN, read.getType());
            assertEquals(0.6, read.getDistanceKm(), 1e-9);
            assertEquals(1.2, read.getEstimatedMinutes(), 1e-9);
            assertEquals(80.5, read.getTotalCost(), 1e-9);
            assertEquals(T, read.getGeneratedAt());
            assertTrue(read.isRecommended());
            assertNull(read.getDestinationHospitalId());
            assertEquals(RouteStatus.PROPOSED, read.getStatus());
            assertEquals(route.getSegments(), read.getSegments(), "same steps, same order, nulls kept");
        });
    }

    @Test
    void aTransferKeepsItsHospital() {
        long hospital = number(jdbc, "SELECT NVL(MIN(id_hospital), 0) FROM hospital");
        assumeTrue(hospital > 0, "Run 03_datos_valledupar.sql first");
        inRolledBackTransaction(jdbc, () -> {
            Fixture f = createFixture();
            long id = routeDao.insert(newRoute(f, hospital, RouteType.ALTERNATIVE));

            Route read = routeDao.findById(id).orElseThrow();
            assertTrue(read.getDestinationHospitalId() != null);
            assertEquals(hospital, read.getDestinationHospitalId());
            assertFalse(read.isRecommended());
        });
    }

    @Test
    void activeRoutesAndTheOneActiveRouteRule() {
        inRolledBackTransaction(jdbc, () -> {
            Fixture f = createFixture();
            long first = routeDao.insert(newRoute(f, null, RouteType.MAIN));
            long second = routeDao.insert(newRoute(f, null, RouteType.ALTERNATIVE));

            routeDao.updateStatus(first, RouteStatus.ACTIVE);

            assertTrue(routeDao.findActive().stream().anyMatch(r -> r.getId() == first));
            assertTrue(routeDao.findActiveUsingSegments(Set.of(f.segments()[1]))
                    .stream().anyMatch(r -> r.getId() == first));
            assertFalse(routeDao.findActiveUsingSegments(Set.of(999_999_999L))
                    .stream().anyMatch(r -> r.getId() == first));
            assertTrue(routeDao.findActiveUsingSegments(Set.of()).isEmpty());
            assertEquals(List.of(first, second),
                    routeDao.findByEmergency(f.emergency()).stream().map(Route::getId).toList());

            // ux_ruta_activa: only one ACTIVA route per emergency and unit
            assertThrows(DataAccessException.class, () -> routeDao.updateStatus(second, RouteStatus.ACTIVE));
            routeDao.updateStatus(first, RouteStatus.DISCARDED);
            routeDao.updateStatus(second, RouteStatus.ACTIVE);
            assertEquals(RouteStatus.ACTIVE, routeDao.findById(second).orElseThrow().getStatus());
        });
        assertThrows(NotFoundException.class, () -> routeDao.updateStatus(-1, RouteStatus.FINISHED));
    }

    @Test
    void positionsAreSavedInABatchAndReadByPeriod() {
        inRolledBackTransaction(jdbc, () -> {
            Fixture f = createFixture();
            locationDao.insertBatch(List.of(
                    new VehicleLocation(0, f.vehicle(), T, new GeoPoint(10.4631, -73.2532), 0.0, 90.0,
                            PositionSource.SIMULATION),
                    new VehicleLocation(0, f.vehicle(), T.plusSeconds(5), new GeoPoint(10.4640, -73.2540), 38.5,
                            null, PositionSource.SIMULATION),
                    new VehicleLocation(0, f.vehicle(), T.plusSeconds(10), new GeoPoint(10.4652, -73.2561), null,
                            null, PositionSource.SIMULATION),
                    new VehicleLocation(0, f.vehicle(), T.plusMinutes(5), new GeoPoint(10.4671, -73.2650), 20.0,
                            180.0, PositionSource.SIMULATION)));

            List<VehicleLocation> track = locationDao.findByVehicle(f.vehicle(), T, T.plusSeconds(10));

            assertEquals(3, track.size(), "both ends included, the fourth one is outside");
            assertEquals(T, track.get(0).getRecordedAt());
            assertEquals(T.plusSeconds(10), track.get(2).getRecordedAt());
            assertEquals(10.4640, track.get(1).getLocation().latitude(), 1e-6);
            assertEquals(38.5, track.get(1).getSpeedKmh(), 1e-9);
            assertNull(track.get(1).getHeadingDegrees(), "a NULL heading stays null, not 0");
            assertNull(track.get(2).getSpeedKmh());
            assertTrue(track.stream().allMatch(p -> p.getSource() == PositionSource.SIMULATION));
        });
    }

    @Test
    void wrongLocationCallsAreRejected() {
        locationDao.insertBatch(List.of());
        assertThrows(IllegalArgumentException.class, () -> locationDao.findByVehicle(1, T.plusMinutes(1), T));

        VehicleLocation saved = new VehicleLocation(0, 1, T, new GeoPoint(10.46, -73.26), null, null,
                PositionSource.SIMULATION);
        saved.setId(3);
        assertThrows(IllegalArgumentException.class, () -> locationDao.insertBatch(List.of(saved)));
    }
}