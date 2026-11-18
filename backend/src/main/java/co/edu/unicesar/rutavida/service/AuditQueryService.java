package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.AuditDAO;
import co.edu.unicesar.rutavida.dao.UserDAO;
import co.edu.unicesar.rutavida.dto.AuditEntry;
import co.edu.unicesar.rutavida.dto.AuditQuery;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.AuditLog;
import co.edu.unicesar.rutavida.model.User;
import co.edu.unicesar.rutavida.util.ValidationUtil;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Consult the audit (CU-21): who did what and when, with filters. It only reads: the entries
 * are written by AuditService and by the triggers of 04_plsql.sql. The controller checks
 * AUDITORIA/CONSULTAR before calling here.
 *
 * <p>The search is limited so that one query never reads the whole table: at most 31 days
 * and at most 500 entries, newest first.
 */
public class AuditQueryService {

    static final Duration DEFAULT_PERIOD = Duration.ofHours(24);
    static final Duration MAX_PERIOD = Duration.ofDays(31);
    static final int DEFAULT_LIMIT = 200;
    static final int MAX_LIMIT = 500;

    /** Modules are written in upper case with letters and underscores (ck_auditoria_mayus). */
    private static final Pattern MODULE = Pattern.compile("[A-Z_]{1,60}");

    private final AuditDAO auditDao;
    private final UserDAO userDao;
    private final Clock clock;

    public AuditQueryService(AuditDAO auditDao, UserDAO userDao, Clock clock) {
        this.auditDao = Objects.requireNonNull(auditDao, "auditDao");
        this.userDao = Objects.requireNonNull(userDao, "userDao");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** The entries that match the filters, newest first, each with its username. */
    public List<AuditEntry> search(AuditQuery query) {
        AuditQuery filters = query == null ? AuditQuery.recent() : query;

        LocalDateTime to = filters.to() != null ? filters.to() : LocalDateTime.now(clock);
        LocalDateTime from = filters.from() != null ? filters.from() : to.minus(DEFAULT_PERIOD);
        if (from.isAfter(to)) {
            throw new ValidationException("La fecha inicial no puede ser posterior a la final.");
        }
        if (Duration.between(from, to).compareTo(MAX_PERIOD) > 0) {
            throw new ValidationException("Consulte como máximo 31 días a la vez.");
        }

        String module = null;
        if (filters.module() != null && !filters.module().isBlank()) {
            module = filters.module().trim().toUpperCase(Locale.ROOT);
            if (!MODULE.matcher(module).matches()) {
                throw new ValidationException("El módulo solo puede tener letras y guiones bajos.");
            }
        }
        if (filters.userId() != null) {
            ValidationUtil.requirePositiveId(filters.userId(), "usuario");
        }
        int limit = filters.limit() == null ? DEFAULT_LIMIT : filters.limit();
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new ValidationException("El límite debe estar entre 1 y " + MAX_LIMIT + ".");
        }

        List<AuditLog> logs = auditDao.search(from, to, module, filters.userId(), filters.result(), limit);
        if (logs.isEmpty()) {
            return List.of();
        }
        Map<Long, String> usernames = userDao.findAll().stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));
        return logs.stream()
                .map(log -> AuditEntry.of(log, log.getUserId() == null ? null : usernames.get(log.getUserId())))
                .toList();
    }
}
