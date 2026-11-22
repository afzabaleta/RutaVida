
package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.model.Alert;
import co.edu.unicesar.rutavida.model.enums.AlertType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static co.edu.unicesar.rutavida.dao.jdbc.OracleTestDatabase.inRolledBackTransaction;
import static co.edu.unicesar.rutavida.dao.jdbc.OracleTestDatabase.number;
import static co.edu.unicesar.rutavida.dao.jdbc.OracleTestDatabase.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JdbcAlertDAO against the local Oracle.
 * Each test creates its own user and alerts in a rolled-back transaction.
 * Skipped without Oracle.
 */
class JdbcAlertDAOIntegrationTest {

    private static final LocalDateTime T =
            LocalDateTime.of(2026, 11, 9, 10, 0);

    private static JdbcTransactionManager jdbc;
    private static JdbcAlertDAO dao;

    @BeforeAll
    static void connect() {
        jdbc = OracleTestDatabase.connectOrSkip();
        dao = new JdbcAlertDAO(jdbc);
    }

    private static long createUser() {
        run(jdbc,
                "INSERT INTO usuario (id_rol, documento, nombres, apellidos, email, username, password_hash)"
                        + " SELECT id_rol, 'PRUEBAS8001', 'Prueba', 'S8',"
                        + " 'prueba.s8@ejemplo.com', 'prueba_s8', 'NO_ES_UN_HASH'"
                        + " FROM rol WHERE nombre_rol = 'COORDINADOR'");

        return number(jdbc,
                "SELECT id_usuario FROM usuario WHERE username = 'prueba_s8'");
    }

    private static Alert alert(long userId, AlertType type, LocalDateTime at) {
        return new Alert(
                0,
                null,
                userId,
                type,
                "PRUEBA S8 " + type.code(),
                at,
                false);
    }

    @Test
    void anAlertIsSavedAndReadBack() {
        inRolledBackTransaction(jdbc, () -> {
            long user = createUser();
            Alert alert = alert(user, AlertType.AVAILABILITY_CHANGE, T);

            long id = dao.insert(alert);

            assertTrue(id > 0);
            assertEquals(id, alert.getId());

            Alert read = dao.findById(id).orElseThrow();

            assertNull(read.getEmergencyId(), "a general alert");
            assertEquals(user, read.getUserId());
            assertEquals(AlertType.AVAILABILITY_CHANGE, read.getType());
            assertEquals("PRUEBA S8 CAMBIO_DISPONIBILIDAD", read.getMessage());
            assertEquals(T, read.getCreatedAt());
            assertFalse(read.isRead());
            assertTrue(dao.findById(-1).isEmpty());
        });
    }

    @Test
    void theListsAreNewestFirstAndMarkingAsReadChangesThem() {
        inRolledBackTransaction(jdbc, () -> {
            long user = createUser();

            long old = dao.insert(
                    alert(user, AlertType.CONGESTION, T));

            long middle = dao.insert(
                    alert(user, AlertType.ROAD_CLOSURE, T.plusMinutes(1)));

            long recent = dao.insert(
                    alert(user, AlertType.ROUTE_RECALCULATION, T.plusMinutes(2)));

            assertEquals(
                    List.of(recent, middle, old),
                    ids(dao.findByUser(user, 10)));

            assertEquals(
                    List.of(recent, middle),
                    ids(dao.findByUser(user, 2)),
                    "at most the limit");

            dao.markAsRead(middle);

            assertTrue(dao.findById(middle).orElseThrow().isRead());

            assertEquals(
                    List.of(recent, old),
                    ids(dao.findUnreadByUser(user)));

            assertEquals(
                    3,
                    dao.findByUser(user, 10).size(),
                    "a read alert is still in the list");

            assertThrows(
                    NotFoundException.class,
                    () -> dao.markAsRead(-1));
        });
    }

    private static List<Long> ids(List<Alert> alerts) {
        return alerts.stream()
                .map(Alert::getId)
                .toList();
    }
}
