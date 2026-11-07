package co.edu.unicesar.rutavida.controller;

import co.edu.unicesar.rutavida.dto.ErrorKind;
import co.edu.unicesar.rutavida.dto.LoginResponse;
import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.dto.SessionUser;
import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.User;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.service.AuthService;
import co.edu.unicesar.rutavida.util.Messages;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sessions of the web (decision DT-08): login gives a random token, the web sends it in
 * {@code Authorization: Bearer <token>} and every request is checked here.
 *
 * <p>A token stops working after {@link #DEFAULT_IDLE} without use, after
 * {@link #DEFAULT_MAX_AGE} since the login, or with logout. The tokens live in memory:
 * if the server restarts, everybody logs in again.
 *
 * <p>Many users at once: each token has its own {@link UserSession}. The web server runs
 * every request with {@link #runAs}, so the other controllers and the audit triggers see
 * the user of that request.
 */
public class WebSessionController {

    public static final Duration DEFAULT_IDLE = Duration.ofMinutes(30);
    public static final Duration DEFAULT_MAX_AGE = Duration.ofHours(8);

    private static final Logger LOG = Logger.getLogger(WebSessionController.class.getName());
    private static final String BEARER = "bearer ";
    private static final int TOKEN_BYTES = 32;

    /** One logged-in browser tab. lastUse changes on every request that counts as use. */
    private static final class Entry {
        final UserSession session;
        final Instant startedAt;
        volatile Instant lastUse;

        Entry(UserSession session, Instant startedAt) {
            this.session = session;
            this.startedAt = startedAt;
            this.lastUse = startedAt;
        }
    }

    private final AuthService authService;
    private final SessionContext sessionContext;
    private final Clock clock;
    private final Duration idle;
    private final Duration maxAge;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Entry> sessions = new ConcurrentHashMap<>();

    public WebSessionController(AuthService authService, SessionContext sessionContext, Clock clock) {
        this(authService, sessionContext, clock, DEFAULT_IDLE, DEFAULT_MAX_AGE);
    }

    public WebSessionController(AuthService authService, SessionContext sessionContext, Clock clock,
                                Duration idle, Duration maxAge) {
        this.authService = Objects.requireNonNull(authService, "authService");
        this.sessionContext = Objects.requireNonNull(sessionContext, "sessionContext");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.idle = Objects.requireNonNull(idle, "idle");
        this.maxAge = Objects.requireNonNull(maxAge, "maxAge");
        if (idle.isNegative() || idle.isZero() || maxAge.compareTo(idle) < 0) {
            throw new IllegalArgumentException("idle must be > 0 and not longer than maxAge");
        }
    }

    /** POST /api/auth/login. Wrong credentials and an inactive user are AUTHENTICATION errors. */
    public OperationResult<LoginResponse> login(String username, char[] password) {
        try {
            UserSession session = authService.login(username, password);
            removeExpired();
            String token = newToken();
            Entry entry = new Entry(session, clock.instant());
            sessions.put(token, entry);
            return OperationResult.ok(toResponse(token, entry));
        } catch (ValidationException e) {
            return OperationResult.error(ErrorKind.VALIDATION, e.getMessage());
        } catch (BusinessRuleException e) {
            return OperationResult.error(ErrorKind.AUTHENTICATION, e.getMessage());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Web login failed due to a technical error", e);
            return OperationResult.failure(e);
        } finally {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
        }
    }

    /**
     * Finds the session of an {@code Authorization} header. {@code countsAsUse} is false for
     * the automatic queries of the map (tracking, traffic, alerts): if they counted, an open
     * tab would never expire.
     */
    public OperationResult<UserSession> authenticate(String authorizationHeader, boolean countsAsUse) {
        String token = tokenOf(authorizationHeader);
        Entry entry = token == null ? null : sessions.get(token);
        if (entry == null) {
            return OperationResult.error(ErrorKind.AUTHENTICATION, Messages.SESSION_REQUIRED);
        }
        Instant now = clock.instant();
        if (isExpired(entry, now)) {
            sessions.remove(token, entry);
            return OperationResult.error(ErrorKind.AUTHENTICATION, Messages.SESSION_EXPIRED);
        }
        if (countsAsUse) {
            entry.lastUse = now;
        }
        return OperationResult.ok(entry.session);
    }

    /** Rejects the request if the role of the session lacks the permission (403). */
    public OperationResult<Void> authorize(UserSession session, String module, String action) {
        try {
            authService.requirePermission(session, module, action);
            return OperationResult.ok(null);
        } catch (RuntimeException e) {
            return OperationResult.failure(e);
        }
    }

    /** GET /api/auth/session: the same data of the login, without the token. */
    public OperationResult<LoginResponse> describe(String authorizationHeader) {
        OperationResult<UserSession> found = authenticate(authorizationHeader, true);
        if (!found.success()) {
            return OperationResult.error(found.error(), found.message());
        }
        Entry entry = sessions.get(tokenOf(authorizationHeader));
        if (entry == null) {
            return OperationResult.error(ErrorKind.AUTHENTICATION, Messages.SESSION_REQUIRED);
        }
        return OperationResult.ok(toResponse(null, entry));
    }

    /** POST /api/auth/logout. Always succeeds: an unknown token is already logged out. */
    public OperationResult<Void> logout(String authorizationHeader) {
        String token = tokenOf(authorizationHeader);
        Entry entry = token == null ? null : sessions.remove(token);
        if (entry != null) {
            try {
                authService.logout(entry.session);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Logout audit failed", e);
            }
        }
        return OperationResult.ok(null);
    }

    /** Runs the work of one web request with its session (see {@link SessionContext#runAs}). */
    public void runAs(UserSession session, SessionContext.SessionWork work) throws Exception {
        sessionContext.runAs(session, work);
    }

    /** Number of open sessions (for tests and the server log). */
    public int openSessions() {
        return sessions.size();
    }

    private boolean isExpired(Entry entry, Instant now) {
        return !now.isBefore(entry.lastUse.plus(idle)) || !now.isBefore(entry.startedAt.plus(maxAge));
    }

    private void removeExpired() {
        Instant now = clock.instant();
        sessions.entrySet().removeIf(each -> isExpired(each.getValue(), now));
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private LoginResponse toResponse(String token, Entry entry) {
        UserSession session = entry.session;
        User user = session.user();
        LocalDateTime expiresAt = LocalDateTime.ofInstant(entry.startedAt.plus(maxAge), clock.getZone())
                .withNano(0);
        List<String> permissions = session.role().getPermissions().stream()
                .map(permission -> permission.getModule().toUpperCase(Locale.ROOT) + ":"
                        + permission.getAction().toUpperCase(Locale.ROOT))
                .sorted()
                .toList();
        return new LoginResponse(token, expiresAt, idle.toMinutes(),
                new SessionUser(user.getId(), user.getUsername(), user.getFirstNames(), user.getLastNames()),
                session.role().getName(), permissions);
    }

    private static String tokenOf(String authorizationHeader) {
        if (authorizationHeader == null) {
            return null;
        }
        String header = authorizationHeader.trim();
        if (header.length() <= BEARER.length()
                || !header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            return null;
        }
        String token = header.substring(BEARER.length()).trim();
        return token.isEmpty() ? null : token;
    }
}