package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.enums.AuditResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditLogTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 16, 22, 0);

    @Test
    void createsAValidEntryAndNormalizesCodes() {
        AuditLog log = new AuditLog(0, 3L, " emergencias ", "registrar", 15L, AT,
                AuditResult.SUCCESS, "  Emergencia registrada  ");
        assertEquals(0, log.getId());
        assertEquals(3L, log.getUserId());
        assertEquals("EMERGENCIAS", log.getModule());
        assertEquals("REGISTRAR", log.getOperation());
        assertEquals(15L, log.getRecordId());
        assertEquals(AT, log.getOccurredAt());
        assertEquals(AuditResult.SUCCESS, log.getResult());
        assertEquals("Emergencia registrada", log.getDetail());
    }

    @Test
    void userRecordAndDetailAreOptional() {
        AuditLog failedLogin = new AuditLog(0, null, "SEGURIDAD", "LOGIN", null, AT,
                AuditResult.FAILURE, "   ");
        assertNull(failedLogin.getUserId());
        assertNull(failedLogin.getRecordId());
        assertNull(failedLogin.getDetail());
    }

    @Test
    void rejectsMissingOrTooLongRequiredFields() {
        assertThrows(ValidationException.class, () ->
                new AuditLog(0, null, " ", "LOGIN", null, AT, AuditResult.SUCCESS, null));
        assertThrows(ValidationException.class, () ->
                new AuditLog(0, null, "SEGURIDAD", "X".repeat(41), null, AT, AuditResult.SUCCESS, null));
        assertThrows(ValidationException.class, () ->
                new AuditLog(0, null, "SEGURIDAD", "LOGIN", null, null, AuditResult.SUCCESS, null));
        assertThrows(ValidationException.class, () ->
                new AuditLog(0, null, "SEGURIDAD", "LOGIN", null, AT, null, null));
        assertThrows(ValidationException.class, () ->
                new AuditLog(0, null, "SEGURIDAD", "LOGIN", null, AT, AuditResult.SUCCESS, "X".repeat(501)));
    }

    @Test
    void rejectsInvalidIds() {
        assertThrows(ValidationException.class, () ->
                new AuditLog(-1, null, "SEGURIDAD", "LOGIN", null, AT, AuditResult.SUCCESS, null));
        assertThrows(ValidationException.class, () ->
                new AuditLog(0, 0L, "SEGURIDAD", "LOGIN", null, AT, AuditResult.SUCCESS, null));
        assertThrows(ValidationException.class, () ->
                new AuditLog(0, null, "SEGURIDAD", "LOGIN", 0L, AT, AuditResult.SUCCESS, null));
    }

    @Test
    void assignIdOnlyWorksOnce() {
        AuditLog log = new AuditLog(0, null, "SEGURIDAD", "LOGIN", null, AT, AuditResult.SUCCESS, null);
        log.assignId(8);
        assertEquals(8, log.getId());
        assertThrows(IllegalStateException.class, () -> log.assignId(9));
    }

    @Test
    void equalsComparesSavedEntriesById() {
        assertTrue(new AuditLog(8, null, "SEGURIDAD", "LOGIN", null, AT, AuditResult.SUCCESS, null)
                .equals(new AuditLog(8, null, "SEGURIDAD", "LOGOUT", null, AT, AuditResult.SUCCESS, null)));
        assertFalse(new AuditLog(0, null, "SEGURIDAD", "LOGIN", null, AT, AuditResult.SUCCESS, null)
                .equals(new AuditLog(0, null, "SEGURIDAD", "LOGIN", null, AT, AuditResult.SUCCESS, null)));
    }
}