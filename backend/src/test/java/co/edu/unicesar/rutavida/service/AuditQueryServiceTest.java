package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dto.AuditEntry;
import co.edu.unicesar.rutavida.dto.AuditQuery;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.AuditLog;
import co.edu.unicesar.rutavida.model.User;
import co.edu.unicesar.rutavida.model.enums.AuditResult;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static co.edu.unicesar.rutavida.service.EmergencyTestSupport.CLOCK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Consult the audit (CU-21) on in-memory DAOs, without Oracle. EXAMPLE entries. */
class AuditQueryServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    private InMemoryAuditDAO auditDao;
    private AuditQueryService service;
    private long marta;

    @BeforeEach
    void setUp() {
        auditDao = new InMemoryAuditDAO();
        InMemoryUserDAO users = new InMemoryUserDAO();
        marta = users.insert(new User(0, 3, "PRUEBA0001", "Marta", "Ruiz", "marta@ejemplo.com", "marta.ruiz",
                "NO_ES_UN_HASH", RecordStatus.ACTIVE, NOW));
        service = new AuditQueryService(auditDao, users, CLOCK);

        add(marta, "SEGURIDAD", "LOGIN", NOW.minusHours(2), AuditResult.SUCCESS);
        add(marta, "DESPACHO", "DESPACHAR", NOW.minusHours(1), AuditResult.SUCCESS);
        add(null, "SEGURIDAD", "LOGIN", NOW.minusMinutes(30), AuditResult.FAILURE);
        add(marta, "EMERGENCIAS", "REGISTRAR", NOW.minusDays(3), AuditResult.SUCCESS);
    }

    @Test
    void withoutFiltersItIsTheLast24HoursNewestFirstWithTheUsername() {
        List<AuditEntry> entries = service.search(null);

        assertEquals(3, entries.size(), "not the one of 3 days ago");
        assertEquals(AuditResult.FAILURE, entries.get(0).result(), "newest first");
        assertNull(entries.get(0).username(), "a failed login of nobody");
        assertEquals("marta.ruiz", entries.get(1).username());
        assertEquals("DESPACHAR", entries.get(1).operation());
    }

    @Test
    void theFiltersCombine() {
        List<AuditEntry> logins = service.search(
                new AuditQuery(null, null, " seguridad ", null, null, null));
        assertEquals(2, logins.size(), "module in any case");

        List<AuditEntry> mine = service.search(
                new AuditQuery(NOW.minusDays(5), NOW, null, marta, AuditResult.SUCCESS, null));
        assertEquals(3, mine.size());
        assertTrue(mine.stream().allMatch(entry -> entry.userId() == marta));

        assertEquals(1, service.search(new AuditQuery(null, null, null, null, null, 1)).size(), "limit");
    }

    @Test
    void wrongFiltersAreRejected() {
        assertThrows(ValidationException.class, () -> service.search(new AuditQuery(NOW, NOW.minusHours(1),
                null, null, null, null)), "from after to");
        assertThrows(ValidationException.class, () -> service.search(new AuditQuery(NOW.minusDays(32), NOW,
                null, null, null, null)), "more than 31 days");
        assertThrows(ValidationException.class, () -> service.search(new AuditQuery(null, null,
                "DESPACHO'; DROP TABLE auditoria --", null, null, null)), "only letters and underscores");
        assertThrows(ValidationException.class, () -> service.search(new AuditQuery(null, null, null, null,
                null, 501)));
        assertThrows(ValidationException.class, () -> service.search(new AuditQuery(null, null, null, 0L,
                null, null)));
    }

    private void add(Long userId, String module, String operation, LocalDateTime at, AuditResult result) {
        auditDao.insert(new AuditLog(0, userId, module, operation, null, at, result, "EJEMPLO"));
    }
}
