package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.model.AuditLog;
import co.edu.unicesar.rutavida.model.User;
import co.edu.unicesar.rutavida.model.enums.AuditResult;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static co.edu.unicesar.rutavida.dao.jdbc.OracleTestDatabase.inRolledBackTransaction;
import static co.edu.unicesar.rutavida.dao.jdbc.OracleTestDatabase.number;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JdbcAuditDAO against the local Oracle. Every entry uses the module PRUEBAS_JUNIT so the
 * checks never mix with real audit rows. Skipped without Oracle; every write is rolled back.
 */
class JdbcAuditDAOIntegrationTest {

    private static final String MODULE = "PRUEBAS_JUNIT";
    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 16, 23, 30, 0);

    private static JdbcTransactionManager jdbc;
    private static JdbcAuditDAO dao;

    @BeforeAll
    static void connect() {
        jdbc = OracleTestDatabase.connectOrSkip();
        dao = new JdbcAuditDAO(jdbc);
    }

    private static AuditLog entry(Long userId, Long recordId, LocalDateTime at, String detail) {
        return new AuditLog(0, userId, MODULE, "PROBAR", recordId, at, AuditResult.SUCCESS, detail);
    }

    private static List<AuditLog> onlyTestEntries(List<AuditLog> logs) {
        return logs.stream().filter(log -> log.getModule().equals(MODULE)).toList();
    }

    @Test
    void insertAssignsTheIdAndFindByRecordReadsItBack() {
        inRolledBackTransaction(jdbc, () -> {
            AuditLog log = entry(null, 777_777L, AT, "Detalle de prueba");
            dao.insert(log);
            assertTrue(log.getId() > 0);

            List<AuditLog> found = dao.findByRecord("pruebas_junit", 777_777L);
            assertEquals(1, found.size());
            AuditLog saved = found.get(0);
            assertEquals(log.getId(), saved.getId());
            assertEquals("PROBAR", saved.getOperation());
            assertEquals(777_777L, saved.getRecordId());
            assertEquals(AT, saved.getOccurredAt());
            assertEquals(AuditResult.SUCCESS, saved.getResult());
            assertEquals("Detalle de prueba", saved.getDetail());
            assertNull(saved.getUserId());
        });
        assertTrue(dao.findByRecord(MODULE, 777_777L).isEmpty());
    }

    @Test
    void entryWithoutUserOrRecordKeepsTheNulls() {
        inRolledBackTransaction(jdbc, () -> {
            dao.insert(new AuditLog(0, null, MODULE, "LOGIN", null, AT, AuditResult.FAILURE, null));
            List<AuditLog> found = onlyTestEntries(dao.findByPeriod(AT, AT));
            assertEquals(1, found.size());
            assertNull(found.get(0).getUserId());
            assertNull(found.get(0).getRecordId());
            assertNull(found.get(0).getDetail());
            assertEquals(AuditResult.FAILURE, found.get(0).getResult());
        });
    }

    @Test
    void entryWithAUserKeepsTheUserId() {
        inRolledBackTransaction(jdbc, () -> {
            long role = number(jdbc, "SELECT id_rol FROM rol WHERE nombre_rol = ?", "COORDINADOR");
            User user = new User(0, role, "JUNIT0010", "Prueba", "Auditoria", "prueba.auditoria@ejemplo.com",
                    "prueba_auditoria", "NO_ES_UN_HASH", RecordStatus.ACTIVE, AT);
            new JdbcUserDAO(jdbc).insert(user);

            dao.insert(entry(user.getId(), 888_888L, AT, null));
            assertEquals(user.getId(), dao.findByRecord(MODULE, 888_888L).get(0).getUserId());
        });
    }

    @Test
    void resultsAreOrderedOldestFirst() {
        inRolledBackTransaction(jdbc, () -> {
            dao.insert(entry(null, 999_999L, AT.plusMinutes(5), "segunda"));
            dao.insert(entry(null, 999_999L, AT, "primera"));
            List<String> details = dao.findByRecord(MODULE, 999_999L).stream()
                    .map(AuditLog::getDetail).toList();
            assertEquals(List.of("primera", "segunda"), details);
        });
    }

    @Test
    void findByPeriodIncludesBothEndsAndNothingOutside() {
        inRolledBackTransaction(jdbc, () -> {
            dao.insert(entry(null, null, AT, "dentro"));
            dao.insert(entry(null, null, AT.plusHours(2), "fuera"));
            List<String> details = onlyTestEntries(dao.findByPeriod(AT, AT.plusHours(1))).stream()
                    .map(AuditLog::getDetail).toList();
            assertEquals(List.of("dentro"), details);
        });
    }

    @Test
    void invalidArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> dao.findByPeriod(AT, AT.minusSeconds(1)));
        AuditLog saved = new AuditLog(5, null, MODULE, "PROBAR", null, AT, AuditResult.SUCCESS, null);
        assertThrows(IllegalArgumentException.class, () -> dao.insert(saved));
    }
}