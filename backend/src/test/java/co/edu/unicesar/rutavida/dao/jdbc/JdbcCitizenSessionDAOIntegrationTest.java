package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.model.CitizenSession;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.PanicReport;
import co.edu.unicesar.rutavida.model.enums.PanicReportStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static co.edu.unicesar.rutavida.dao.jdbc.OracleTestDatabase.inRolledBackTransaction;
import static co.edu.unicesar.rutavida.dao.jdbc.OracleTestDatabase.number;
import static co.edu.unicesar.rutavida.dao.jdbc.OracleTestDatabase.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * JdbcCitizenSessionDAO and the counts per person of JdbcPanicReportDAO against Oracle, in
 * rolled-back transactions (DT-13). Skipped without Oracle or without 08_ciudadanos.sql.
 * The rows are dated in 2099 so they never meet real data.
 */
class JdbcCitizenSessionDAOIntegrationTest {

    private static final LocalDateTime T = LocalDateTime.of(2099, 1, 1, 10, 0);

    private static JdbcTransactionManager jdbc;
    private static JdbcCitizenSessionDAO dao;
    private static JdbcPanicReportDAO reports;

    @BeforeAll
    static void connect() {
        jdbc = OracleTestDatabase.connectOrSkip();
        assumeTrue(number(jdbc, "SELECT COUNT(*) FROM user_tables WHERE table_name = 'SESION_CIUDADANO'") == 1,
                "Run database/08_ciudadanos.sql first");
        dao = new JdbcCitizenSessionDAO(jdbc);
        reports = new JdbcPanicReportDAO(jdbc);
    }

    /** A citizen of the tests; returns its id. */
    private static long createCitizen() {
        run(jdbc, "INSERT INTO usuario (id_rol, documento, nombres, apellidos, email, username, password_hash)"
                + " SELECT id_rol, 'PRUEBADT13', 'Prueba', 'DT13', 'prueba.dt13@ejemplo.com', 'prueba_dt13',"
                + " 'NO_ES_UN_HASH' FROM rol WHERE nombre_rol = 'CIUDADANO'");
        return number(jdbc, "SELECT id_usuario FROM usuario WHERE username = 'prueba_dt13'");
    }

    private static String hash(char c) {
        return String.valueOf(c).repeat(64);
    }

    @Test
    void aSessionIsValidUntilItExpiresOrIsRevoked() {
        inRolledBackTransaction(jdbc, () -> {
            long user = createCitizen();
            CitizenSession session = new CitizenSession(0, user, hash('a'), T, T.plusDays(30), false);

            long id = dao.insert(session);

            assertTrue(id > 0);
            assertEquals(id, session.getId());
            CitizenSession read = dao.findValid(hash('a'), T.plusDays(29)).orElseThrow();
            assertEquals(user, read.getUserId());
            assertEquals(T.plusDays(30), read.getExpiresAt());
            assertFalse(read.isRevoked());

            assertTrue(dao.findValid(hash('a'), T.plusDays(30)).isEmpty(), "expired");
            assertTrue(dao.findValid(hash('b'), T).isEmpty(), "unknown token");

            assertEquals(1, dao.revoke(hash('a')));
            assertEquals(0, dao.revoke(hash('a')), "already revoked");
            assertTrue(dao.findValid(hash('a'), T).isEmpty());
        });
    }

    @Test
    void blockingRevokesEveryPhoneOfThePerson() {
        inRolledBackTransaction(jdbc, () -> {
            long user = createCitizen();
            dao.insert(new CitizenSession(0, user, hash('c'), T, T.plusDays(30), false));
            dao.insert(new CitizenSession(0, user, hash('d'), T, T.plusDays(30), false));

            assertEquals(2, dao.revokeAllOf(user));

            assertTrue(dao.findValid(hash('c'), T).isEmpty());
            assertTrue(dao.findValid(hash('d'), T).isEmpty());
        });
    }

    @Test
    void panicReportsAreCountedPerPersonAndStatus() {
        inRolledBackTransaction(jdbc, () -> {
            long user = createCitizen();
            GeoPoint popa = new GeoPoint(10.467172, -73.265099);
            reports.insert(new PanicReport(0, popa, T, "PRUEBA-DT13-1", user, null, null,
                    PanicReportStatus.PENDING, null));
            long discarded = reports.insert(new PanicReport(0, popa, T.plusMinutes(1), "PRUEBA-DT13-2", user, null,
                    null, PanicReportStatus.PENDING, null));
            reports.updateStatus(discarded, PanicReportStatus.DISCARDED);

            assertEquals(2, reports.countByUserSince(user, T), "whatever phone it used");
            assertEquals(1, reports.countByUserSince(user, T.plusMinutes(1)));
            assertEquals(1, reports.countByUserAndStatusSince(user, PanicReportStatus.DISCARDED, T));
            assertEquals(0, reports.countByUserAndStatusSince(user, PanicReportStatus.DISCARDED, T.plusDays(1)));
        });
    }
}
