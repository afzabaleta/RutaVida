package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.dto.SegmentView;
import co.edu.unicesar.rutavida.model.GeoPoint;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static co.edu.unicesar.rutavida.dao.jdbc.OracleTestDatabase.inRolledBackTransaction;
import static co.edu.unicesar.rutavida.dao.jdbc.OracleTestDatabase.number;
import static co.edu.unicesar.rutavida.dao.jdbc.OracleTestDatabase.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JdbcRoadMapDAO against the local Oracle. The test creates its own segment (VIA PRUEBA A12C)
 * in a transaction that is rolled back. Skipped without Oracle.
 */
class JdbcRoadMapDAOIntegrationTest {

    private static JdbcTransactionManager jdbc;
    private static JdbcRoadMapDAO dao;

    @BeforeAll
    static void connect() {
        jdbc = OracleTestDatabase.connectOrSkip();
        dao = new JdbcRoadMapDAO(jdbc);
    }

    @Test
    void aSegmentComesWithItsStreetAndBothEnds() {
        inRolledBackTransaction(jdbc, () -> {
            run(jdbc, "INSERT INTO nodo_vial (referencia, latitud, longitud) VALUES ('PRUEBA_A12C_A', 10.468901, -73.258004)");
            run(jdbc, "INSERT INTO nodo_vial (referencia, latitud, longitud) VALUES ('PRUEBA_A12C_B', 10.468377, -73.260112)");
            run(jdbc, "INSERT INTO tramo_vial (id_nodo_origen, id_nodo_destino, nombre_via, distancia_m, velocidad_max_kmh)"
                    + " SELECT a.id_nodo, b.id_nodo, 'VIA PRUEBA A12C', 235, 40 FROM nodo_vial a, nodo_vial b"
                    + " WHERE a.referencia = 'PRUEBA_A12C_A' AND b.referencia = 'PRUEBA_A12C_B'");
            long id = number(jdbc, "SELECT id_tramo FROM tramo_vial WHERE nombre_via = 'VIA PRUEBA A12C'");

            List<SegmentView> views = dao.segmentViews(Set.of(id, -1L));

            assertEquals(List.of(new SegmentView(id, "VIA PRUEBA A12C",
                            new GeoPoint(10.468901, -73.258004), new GeoPoint(10.468377, -73.260112))), views,
                    "only the segment that exists, with both ends");
            assertTrue(dao.segmentViews(List.of()).isEmpty());
        });
    }
}