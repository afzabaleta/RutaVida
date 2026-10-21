
package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.UserDAO;
import co.edu.unicesar.rutavida.dao.RoleDAO;
import co.edu.unicesar.rutavida.util.PasswordHasher;

import java.time.Clock;
import java.util.Objects;

import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.Role;
import co.edu.unicesar.rutavida.model.User;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.enums.AuditResult;

import java.time.LocalDateTime;
import java.util.Optional;


/**
 * Service responsible for user authentication.
 * Implements login (CU-01) and logout (CU-02).
 */
public class AuthService {

    private final UserDAO userDao;
    private final RoleDAO roleDao;
    private final PasswordHasher passwordHasher;
    private final AuditService auditService;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(
            UserDAO userDao,
            RoleDAO roleDao,
            PasswordHasher passwordHasher,
            AuditService auditService,
            Clock clock) {

        this.userDao = Objects.requireNonNull(userDao, "userDao");
        this.roleDao = Objects.requireNonNull(roleDao, "roleDao");
        this.passwordHasher = Objects.requireNonNull(
                passwordHasher, "passwordHasher");
        this.auditService = Objects.requireNonNull(
                auditService, "auditService");
        this.clock = Objects.requireNonNull(clock, "clock");

        this.dummyHash = passwordHasher.hash(
                "rutavida-dummy".toCharArray());
    }


    /**
     * Authenticates a user and starts a session (CU-01).
     */
    public UserSession login(String username, char[] password) {

        if (username == null || username.isBlank()
                || password == null || password.length == 0) {
            throw new ValidationException(
                    "Escriba el usuario y la contraseña.");
        }

        String normalizedUsername = username.trim();

        Optional<User> found =
                userDao.findByUsername(normalizedUsername);

        if (found.isEmpty()) {
            passwordHasher.matches(password, dummyHash);

            auditService.record(
                    null, "SEGURIDAD", "LOGIN",
                    null, AuditResult.FAILURE,
                    "Usuario no existe: " + normalizedUsername);

            throw new BusinessRuleException(
                    "Usuario o contraseña incorrectos");
        }

        User user = found.get();

        if (!passwordHasher.matches(
                password, user.getPasswordHash())) {

            auditService.record(
                    user.getId(), "SEGURIDAD", "LOGIN",
                    null, AuditResult.FAILURE,
                    "Contraseña incorrecta");

            throw new BusinessRuleException(
                    "Usuario o contraseña incorrectos");
        }

        if (!user.isActive()) {

            auditService.record(
                    user.getId(), "SEGURIDAD", "LOGIN",
                    null, AuditResult.FAILURE,
                    "Usuario inactivo");

            throw new BusinessRuleException(
                    "El usuario está inactivo");
        }

        Role role = roleDao.findById(user.getRoleId())
                .orElseThrow(() ->
                        new NotFoundException(
                                "Rol", user.getRoleId()));

        UserSession session = new UserSession(
                user, role, LocalDateTime.now(clock));

        auditService.record(
                user.getId(), "SEGURIDAD", "LOGIN",
                null, AuditResult.SUCCESS,
                "Inicio de sesión exitoso");

        return session;
    }


/**
 * Ends a user session and records the logout (CU-02).
 */
        public void logout(UserSession session) {

            Objects.requireNonNull(session, "session");

            auditService.record(
                    session.user().getId(),
                    "SEGURIDAD",
                    "LOGOUT",
                    null,
                    AuditResult.SUCCESS,
                    "Cierre de sesión exitoso"
            );
        }


}
