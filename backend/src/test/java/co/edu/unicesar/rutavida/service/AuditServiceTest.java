
package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.model.AuditLog;
import co.edu.unicesar.rutavida.model.enums.AuditResult;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuditServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-17T15:00:00Z"),
            ZoneId.of("America/Bogota"));

    @Test
    void recordsAuditWithCorrectValuesAndTime() {
        InMemoryAuditDAO dao = new InMemoryAuditDAO();
        AuditService service = new AuditService(dao, CLOCK);

        service.record(
                3L, "emergencias", "registrar",
                15L, AuditResult.SUCCESS, "Registro creado");

        AuditLog log = dao.last();

        assertTrue(log.getId() > 0);
        assertEquals(3L, log.getUserId());
        assertEquals("EMERGENCIAS", log.getModule());
        assertEquals("REGISTRAR", log.getOperation());
        assertEquals(15L, log.getRecordId());
        assertEquals(AuditResult.SUCCESS, log.getResult());
        assertEquals("Registro creado", log.getDetail());
        assertEquals(
                LocalDateTime.of(2026, 10, 17, 10, 0),
                log.getOccurredAt());
    }

    @Test
    void allowsNullOptionalFields() {
        InMemoryAuditDAO dao = new InMemoryAuditDAO();
        AuditService service = new AuditService(dao, CLOCK);

        service.record(
                null, "seguridad", "login",
                null, AuditResult.FAILURE, null);

        AuditLog log = dao.last();

        assertNull(log.getUserId());
        assertNull(log.getRecordId());
        assertNull(log.getDetail());
        assertEquals("SEGURIDAD", log.getModule());
        assertEquals("LOGIN", log.getOperation());
    }

    @Test
    void truncatesLongDetailTo500Characters() {
        InMemoryAuditDAO dao = new InMemoryAuditDAO();
        AuditService service = new AuditService(dao, CLOCK);

        service.record(
                3L, "emergencias", "registrar",
                15L, AuditResult.SUCCESS, "x".repeat(800));

        AuditLog log = dao.last();

        assertEquals(500, log.getDetail().length());
        assertEquals("x".repeat(500), log.getDetail());
    }

    @Test
    void findsAuditLogsByRecord() {
        InMemoryAuditDAO dao = new InMemoryAuditDAO();
        AuditService service = new AuditService(dao, CLOCK);

        service.record(
                3L, "emergencias", "registrar",
                15L, AuditResult.SUCCESS, "Primero");

        service.record(
                3L, "emergencias", "actualizar",
                15L, AuditResult.SUCCESS, "Segundo");

        service.record(
                3L, "emergencias", "registrar",
                16L, AuditResult.SUCCESS, "Tercero");

        List<AuditLog> found =
                service.findByRecord("EMERGENCIAS", 15L);

        assertEquals(2, found.size());
        assertTrue(found.stream()
                .allMatch(log -> log.getRecordId() == 15L));
    }
}
