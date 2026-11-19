package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.dto.TrafficSegmentView;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.enums.TrafficLevel;
import co.edu.unicesar.rutavida.model.enums.TrafficSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static co.edu.unicesar.rutavida.dao.jdbc.OracleTestDatabase.inRolledBackTransaction;
import static co.edu.unicesar.rutavida.dao.jdbc.OracleTestDatabase.number;
import static co.edu.unicesar.rutavida.dao.jdbc.OracleTestDatabase.run;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * JdbcTrafficMapDAO against Oracle. The test creates three segments of its own (VIA PRUEBA
 * TRAFICO 1 to 3) and their readings, in a transaction that is rolled back; readings that
 * other runs left in TRAFICO are ignored. Skipped without Oracle.
 */
class JdbcTrafficMapDAOIntegrationTest {

    private static final String SEGMENT = "INSERT INTO tramo_vial (id_nodo_origen, id_nodo_destino, nombre_via,"
            + " distancia_m, velocidad_max_kmh) SELECT a.id_nodo, b.id_nodo, ?, 235, 40 FROM nodo_vial a, nodo_vial b"
            + " WHERE a.referencia = 'PRUEBA_TRAFICO_A' AND b.referencia = 'PRUEBA_TRAFICO_B'";
    private static final String READING = "INSERT INTO trafico (id_tramo, fecha_hora, nivel,"
            + " velocidad_promedio_kmh, factor_congestion, fuente) VALUES (?, ?, ?, 20, ?, 'SIMULACION')";

    private static JdbcTransactionManager jdbc;
    private static JdbcTrafficMapDAO dao;

    @BeforeAll
    static void connect() {
        jdbc = OracleTestDatabase.connectOrSkip();
        dao = new JdbcTrafficMapDAO(jdbc);
    }

    @Test
    void onlyTheLastReadingOfEachSegmentCountsAndFreeFlowIsLeftOut() {
        inRolledBackTransaction(jdbc, () -> {
            run(jdbc, "INSERT INTO nodo_vial (referencia, latitud, longitud) VALUES ('PRUEBA_TRAFICO_A', 10.468901, -73.258004)");
            run(jdbc, "INSERT INTO nodo_vial (referencia, latitud, longitud) VALUES ('PRUEBA_TRAFICO_B', 10.468377, -73.260112)");
            run(jdbc, SEGMENT, "VIA PRUEBA TRAFICO 1");
            run(jdbc, SEGMENT, "VIA PRUEBA TRAFICO 2");
            run(jdbc, SEGMENT, "VIA PRUEBA TRAFICO 3");
            long first = number(jdbc, "SELECT id_tramo FROM tramo_vial WHERE nombre_via = 'VIA PRUEBA TRAFICO 1'");
            long second = number(jdbc, "SELECT id_tramo FROM tramo_vial WHERE nombre_via = 'VIA PRUEBA TRAFICO 2'");
            long third = number(jdbc, "SELECT id_tramo FROM tramo_vial WHERE nombre_via = 'VIA PRUEBA TRAFICO 3'");

            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            // 1: congested, then moderate → moderate. 2: congested, then free flow → left out.
            // 3: congested, but before the window → left out.
            run(jdbc, READING, first, now.minusMinutes(8), "CONGESTIONADO", 2.6);
            run(jdbc, READING, first, now.minusMinutes(2), "MODERADO", 1.3);
            run(jdbc, READING, second, now.minusMinutes(8), "CONGESTIONADO", 2.5);
            run(jdbc, READING, second, now.minusMinutes(1), "FLUIDO", 1.0);
            run(jdbc, READING, third, now.minusMinutes(40), "CONGESTIONADO", 2.8);

            List<TrafficSegmentView> mine = dao.congestedSince(now.minusMinutes(15)).stream()
                    .filter(view -> Set.of(first, second, third).contains(view.segmentId()))
                    .toList();

            assertEquals(List.of(new TrafficSegmentView(first, "VIA PRUEBA TRAFICO 1", TrafficLevel.MODERATE, 1.3,
                    now.minusMinutes(2), TrafficSource.SIMULATION, new GeoPoint(10.468901, -73.258004),
                    new GeoPoint(10.468377, -73.260112))), mine);
        });
    }
}
