package co.edu.unicesar.rutavida.controller;

import co.edu.unicesar.rutavida.dto.CitizenLogin;
import co.edu.unicesar.rutavida.dto.ErrorKind;
import co.edu.unicesar.rutavida.dto.LoginResponse;
import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.dto.SessionUser;
import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.User;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.service.AuthService;
import co.edu.unicesar.rutavida.service.CitizenService;
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
import java.util.Optional;
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
 *
 * <p>Citizens of the panic button (DT-13) are the exception: their token lasts 30 days, has
 * no idle timeout and is saved (hashed) in Oracle by {@link CitizenService}, so the phone
 * stays logged in even if the server restarts. Their role only has PANICO/REPORTAR, so that
 * token only serves to press the button.
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
    /** Null without citizens (tests of the staff sessions). */
    private final CitizenService citizens;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Entry> sessions = new ConcurrentHashMap<>();

    public WebSessionController(AuthService authService, SessionContext sessionContext, Clock clock) {
        this(authService, sessionContext, clock, DEFAULT_IDLE, DEFAULT_MAX_AGE, null);
    }

    /** With the 30-day sessions of the citizens of the panic button (DT-13). */
    public WebSessionController(AuthService authService, SessionContext sessionContext, Clock clock,
                                CitizenService citizens) {
        this(authService, sessionContext, clock, DEFAULT_IDLE, DEFAULT_MAX_AGE,
                Objects.requireNonNull(citizens, "citizens"));
    }

    public WebSessionController(AuthService authService, SessionContext sessionContext, Clock clock,
                                Duration idle, Duration maxAge) {
        this(authService, sessionContext, clock, idle, maxAge, null);
    }

    private WebSessionController(AuthService authService, SessionContext sessionContext, Clock clock,
                                 Duration idle, Duration maxAge, CitizenService citizens) {
        this.authService = Objects.requireNonNull(authService, "authService");
        this.sessionContext = Objects.requireNonNull(sessionContext, "sessionContext");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.idle = Objects.requireNonNull(idle, "idle");
        this.maxAge = Objects.requireNonNull(maxAge, "maxAge");
        this.citizens = citizens;
        if (idle.isNegative() || idle.isZero() || maxAge.compareTo(idle) < 0) {
            throw new IllegalArgumentException("idle must be > 0 and not longer than maxAge");
        }
    }

    /** POST /api/auth/login. Wrong credentials and an inactive user are AUTHENTICATION errors. */
    public OperationResult<LoginResponse> login(String username, char[] password) {
        try {
            UserSession session = authService.login(username, password);
            if (citizens != null && CitizenService.isCitizen(session)) {
                return OperationResult.ok(toCitizenResponse(citizens.startSession(session)));
            }
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
            return citizenSession(token)
                    .map(found -> OperationResult.ok(found.session()))
                    .orElseGet(() -> OperationResult.error(ErrorKind.AUTHENTICATION, Messages.SESSION_REQUIRED));
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
            return citizenSession(tokenOf(authorizationHeader))
                    .map(citizen -> OperationResult.ok(toCitizenResponse(citizen)))
                    .orElseGet(() -> OperationResult.error(ErrorKind.AUTHENTICATION, Messages.SESSION_REQUIRED));
        }
        return OperationResult.ok(toResponse(null, entry));
    }

    /** POST /api/auth/logout. Always succeeds: an unknown token is already logged out. */
    public OperationResult<Void> logout(String authorizationHeader) {
        String token = tokenOf(authorizationHeader);
        Entry entry = token == null ? null : sessions.remove(token);
        UserSession ended = entry != null ? entry.session : null;
        if (entry == null && citizens != null && token != null) {
            try {
                ended = citizens.findSession(token).map(CitizenLogin::session).orElse(null);
                citizens.endSession(token);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Citizen logout failed", e);
            }
        }
        if (ended != null) {
            try {
                authService.logout(ended);
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

    /**
     * The 30-day session of a citizen for a token that is not a staff session. Empty when
     * there are no citizens here or the database cannot be read (the request gets a 401, as
     * with any unknown token).
     */
    private Optional<CitizenLogin> citizenSession(String token) {
        if (citizens == null || token == null) {
            return Optional.empty();
        }
        try {
            return citizens.findSession(token);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Citizen session lookup failed", e);
            return Optional.empty();
        }
    }

    /** A citizen session never expires for lack of use: idleTimeoutMinutes is 0. */
    private LoginResponse toCitizenResponse(CitizenLogin login) {
        return response(login.token(), login.session(), login.expiresAt().withNano(0), 0);
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
        LocalDateTime expiresAt = LocalDateTime.ofInstant(entry.startedAt.plus(maxAge), clock.getZone())
                .withNano(0);
        return response(token, entry.session, expiresAt, idle.toMinutes());
    }

    private static LoginResponse response(String token, UserSession session, LocalDateTime expiresAt,
                                          long idleMinutes) {
        User user = session.user();
        List<String> permissions = session.role().getPermissions().stream()
                .map(permission -> permission.getModule().toUpperCase(Locale.ROOT) + ":"
                        + permission.getAction().toUpperCase(Locale.ROOT))
                .sorted()
                .toList();
        return new LoginResponse(token, expiresAt, idleMinutes,
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