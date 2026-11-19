package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.CitizenSessionDAO;
import co.edu.unicesar.rutavida.dao.RoleDAO;
import co.edu.unicesar.rutavida.dao.TransactionManager;
import co.edu.unicesar.rutavida.dao.UserDAO;
import co.edu.unicesar.rutavida.dto.CitizenLogin;
import co.edu.unicesar.rutavida.dto.CitizenRequest;
import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.CitizenSession;
import co.edu.unicesar.rutavida.model.Role;
import co.edu.unicesar.rutavida.model.User;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.enums.AuditResult;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import co.edu.unicesar.rutavida.util.PasswordHasher;
import co.edu.unicesar.rutavida.util.ValidationUtil;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Citizens of the panic button (DT-13, CU-23, RN-16), as the professor asked: only a
 * registered person presses it, so every alert has someone responsible.
 *
 * <ul>
 *   <li>A person registers alone, with few data and the authorization of Ley 1581 de 2012.
 *       The account gets the role CIUDADANO, whose only permission is PANICO/REPORTAR.</li>
 *   <li>The phone stays logged in for 30 days, even if the server restarts: the session is in
 *       SESION_CIUDADANO, which keeps only the SHA-256 hash of the token. It has no idle
 *       timeout: in an emergency the button never asks to log in again.</li>
 *   <li>An account with 2 alerts discarded as false in 30 days is blocked: it becomes
 *       inactive, its phones are logged out and the coordinators are told. An administrator
 *       can reactivate it (PATCH /api/users/{id}/status).</li>
 * </ul>
 *
 * <p>The staff of the operations center keeps its own session rules (30 minutes without use,
 * 8 hours at most, DT-08); changing the password asks for the current one again.
 */
public class CitizenService {

    public static final String CITIZEN_ROLE = "CIUDADANO";

    /** How long a phone stays logged in. */
    public static final Duration SESSION_LENGTH = Duration.ofDays(30);

    /** RN-16: discarded alerts that block an account, and in how long. */
    public static final int FALSE_ALARMS_TO_BLOCK = 2;
    public static final Duration FALSE_ALARM_WINDOW = Duration.ofDays(30);

    static final String AUDIT_MODULE = "USUARIOS";
    static final String CONSENT_TEXT =
            "Autorizó el tratamiento de sus datos personales (Ley 1581 de 2012).";

    private static final int TOKEN_BYTES = 32;

    private final UserDAO userDao;
    private final RoleDAO roleDao;
    private final CitizenSessionDAO sessionDao;
    private final PasswordHasher passwordHasher;
    private final AuditService auditService;
    private final AlertService alertService;
    private final TransactionManager transactions;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public CitizenService(UserDAO userDao, RoleDAO roleDao, CitizenSessionDAO sessionDao,
                          PasswordHasher passwordHasher, AuditService auditService,
                          AlertService alertService, TransactionManager transactions, Clock clock) {
        this.userDao = Objects.requireNonNull(userDao, "userDao");
        this.roleDao = Objects.requireNonNull(roleDao, "roleDao");
        this.sessionDao = Objects.requireNonNull(sessionDao, "sessionDao");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher");
        this.auditService = Objects.requireNonNull(auditService, "auditService");
        this.alertService = Objects.requireNonNull(alertService, "alertService");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    // ------------------------------------------------------------------ CU-23: register

    /**
     * A person creates his own account to use the panic button. The password is erased from
     * the request after hashing it.
     *
     * @throws ValidationException if a field is not valid or the person did not authorize the data
     * @throws BusinessRuleException if the document, email or username is taken, or 08 was not run
     */
    public User register(CitizenRequest request) {
        ValidationUtil.requireNonNull(request, "registro");

        try {
            if (!Boolean.TRUE.equals(request.dataConsent())) {
                throw new ValidationException(
                        "Para registrarse debe autorizar el tratamiento de sus datos personales.");
            }
            AdminBootstrapService.validatePassword(request.password());

            User user = new User(
                    0,
                    citizenRole().getId(),
                    ValidationUtil.requireText(request.document(), "documento", 20),
                    ValidationUtil.requireText(request.firstNames(), "nombres", 80),
                    ValidationUtil.requireText(request.lastNames(), "apellidos", 80),
                    ValidationUtil.requireText(request.email(), "correo", 120).toLowerCase(Locale.ROOT),
                    AuthService.normalizeUsername(ValidationUtil.requireText(request.username(), "usuario", 40)),
                    passwordHasher.hash(request.password()),
                    RecordStatus.ACTIVE,
                    LocalDateTime.now(clock));

            AdminBootstrapService.validateFormats(user);
            requireUnique(user);

            transactions.runInTransaction(() -> {
                long id = userDao.insert(user);
                auditService.record(id, AUDIT_MODULE, "REGISTRAR_CIUDADANO", id, AuditResult.SUCCESS,
                        "Registro para el botón de pánico. " + CONSENT_TEXT);
            });
            return user;
        } finally {
            if (request.password() != null) {
                Arrays.fill(request.password(), '\0');
            }
        }
    }

    // ------------------------------------------------------------------ the 30-day session

    /** True if the session is of a citizen: it gets a 30-day session on its phone. */
    public static boolean isCitizen(UserSession session) {
        return session != null && CITIZEN_ROLE.equalsIgnoreCase(session.role().getName());
    }

    /** After a correct login of a citizen: a new token for the phone, valid for 30 days. */
    public CitizenLogin startSession(UserSession session) {
        if (!isCitizen(session)) {
            throw new IllegalArgumentException("Only a citizen gets a phone session");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime expiresAt = now.plus(SESSION_LENGTH);
        String token = newToken();

        sessionDao.insert(new CitizenSession(0, session.user().getId(), hashToken(token), now, expiresAt, false));
        return new CitizenLogin(token, session, now, expiresAt);
    }

    /**
     * The session of a token, if it is a valid citizen session: not revoked, not expired, and
     * the account is still active with the role CIUDADANO. Empty for any other token.
     */
    public Optional<CitizenLogin> findSession(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Optional<CitizenSession> found = sessionDao.findValid(hashToken(token), LocalDateTime.now(clock));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        CitizenSession saved = found.get();
        Optional<User> user = userDao.findById(saved.getUserId())
                .filter(each -> each.getStatus() == RecordStatus.ACTIVE);
        Optional<Role> role = user.flatMap(each -> roleDao.findById(each.getRoleId()))
                .filter(each -> each.getStatus() == RecordStatus.ACTIVE)
                .filter(each -> CITIZEN_ROLE.equalsIgnoreCase(each.getName()));
        if (role.isEmpty()) {
            return Optional.empty();
        }
        UserSession session = new UserSession(user.get(), role.get(), saved.getCreatedAt());
        return Optional.of(new CitizenLogin(null, session, saved.getCreatedAt(), saved.getExpiresAt()));
    }

    /** Logout of the phone: its token stops working. False if it was not a valid citizen token. */
    public boolean endSession(String token) {
        return token != null && !token.isBlank() && sessionDao.revoke(hashToken(token)) > 0;
    }

    // ------------------------------------------------------------------ RN-16: block

    /**
     * Blocks a citizen account for false alarms: inactive, every phone logged out, audited and
     * the coordinators told. Only CIUDADANO accounts are blocked; police officers and the staff
     * are not. Returns false if there was nothing to block.
     */
    public boolean blockForFalseAlarms(long userId, int falseAlarms, UserSession by) {
        ValidationUtil.requireNonNull(by, "sesión");

        Optional<User> found = userDao.findById(userId);
        if (found.isEmpty() || found.get().getStatus() != RecordStatus.ACTIVE) {
            return false;
        }
        User user = found.get();
        boolean citizen = roleDao.findById(user.getRoleId())
                .map(role -> CITIZEN_ROLE.equalsIgnoreCase(role.getName()))
                .orElse(false);
        if (!citizen) {
            return false;
        }

        transactions.runInTransaction(() -> {
            userDao.update(new User(user.getId(), user.getRoleId(), user.getDocument(), user.getFirstNames(),
                    user.getLastNames(), user.getEmail(), user.getUsername(), user.getPasswordHash(),
                    RecordStatus.INACTIVE, user.getRegisteredAt()));
            int phones = sessionDao.revokeAllOf(userId);
            auditService.record(by.user().getId(), AUDIT_MODULE, "BLOQUEAR", userId, AuditResult.SUCCESS,
                    falseAlarms + " alertas de pánico descartadas en "
                            + FALSE_ALARM_WINDOW.toDays() + " días; " + phones + " sesiones cerradas");
        });
        alertService.citizenBlocked(user.getUsername(), falseAlarms);
        return true;
    }

    // ------------------------------------------------------------------ helpers

    /** Only this is saved, never the token (64 hexadecimal characters). */
    static String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every Java runtime", e);
        }
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private Role citizenRole() {
        Role role = roleDao.findAll().stream()
                .filter(each -> CITIZEN_ROLE.equalsIgnoreCase(each.getName()))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException(
                        "Falta el rol CIUDADANO: ejecute 08_ciudadanos.sql."));
        if (role.getStatus() != RecordStatus.ACTIVE) {
            throw new BusinessRuleException("El registro de ciudadanos está desactivado.");
        }
        return role;
    }

    private void requireUnique(User user) {
        if (userDao.findByUsername(user.getUsername()).isPresent()) {
            throw new BusinessRuleException("Ya existe un usuario con ese nombre de usuario.");
        }
        for (User other : userDao.findAll()) {
            if (other.getDocument().equalsIgnoreCase(user.getDocument())) {
                throw new BusinessRuleException("Ya existe un usuario con ese documento.");
            }
            if (other.getEmail().equalsIgnoreCase(user.getEmail())) {
                throw new BusinessRuleException("Ya existe un usuario con ese correo.");
            }
        }
    }
}
