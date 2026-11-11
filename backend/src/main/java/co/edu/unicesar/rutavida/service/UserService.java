
package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.RoleDAO;
import co.edu.unicesar.rutavida.dao.TransactionManager;
import co.edu.unicesar.rutavida.dao.UserDAO;
import co.edu.unicesar.rutavida.dto.UserRequest;
import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.Role;
import co.edu.unicesar.rutavida.model.User;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.enums.AuditResult;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import co.edu.unicesar.rutavida.util.PasswordHasher;
import co.edu.unicesar.rutavida.util.ValidationUtil;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class UserService {

    static final String AUDIT_MODULE = "USUARIOS";

    private final UserDAO userDao;
    private final RoleDAO roleDao;
    private final PasswordHasher passwordHasher;
    private final AuditService auditService;
    private final TransactionManager transactions;
    private final Clock clock;

    public UserService(UserDAO userDao, RoleDAO roleDao,
                       PasswordHasher passwordHasher,
                       AuditService auditService,
                       TransactionManager transactions,
                       Clock clock) {
        this.userDao = Objects.requireNonNull(userDao, "userDao");
        this.roleDao = Objects.requireNonNull(roleDao, "roleDao");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher");
        this.auditService = Objects.requireNonNull(auditService, "auditService");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public User create(UserRequest request, UserSession session) {
        ValidationUtil.requireNonNull(request, "usuario");
        ValidationUtil.requireNonNull(session, "sesión");

        try {
            AdminBootstrapService.validatePassword(request.password());

            Role role = roleDao.findById(
                    ValidationUtil.requirePositiveId(request.roleId(), "rol")
            ).orElseThrow(() -> new NotFoundException("Rol", request.roleId()));

            if (role.getStatus() != RecordStatus.ACTIVE) {
                throw new BusinessRuleException(
                        "El rol " + role.getName() + " está inactivo."
                );
            }

            User user = new User(
                    0,
                    role.getId(),
                    ValidationUtil.requireText(request.document(), "documento", 20),
                    ValidationUtil.requireText(request.firstNames(), "nombres", 80),
                    ValidationUtil.requireText(request.lastNames(), "apellidos", 80),
                    ValidationUtil.requireText(request.email(), "correo", 120)
                            .toLowerCase(Locale.ROOT),
                    AuthService.normalizeUsername(
                            ValidationUtil.requireText(request.username(), "usuario", 40)
                    ),
                    passwordHasher.hash(request.password()),
                    RecordStatus.ACTIVE,
                    LocalDateTime.now(clock)
            );

            AdminBootstrapService.validateFormats(user);
            requireUnique(user);

            transactions.runInTransaction(() -> {
                long id = userDao.insert(user);
                auditService.record(
                        session.user().getId(),
                        AUDIT_MODULE,
                        "CREAR",
                        id,
                        AuditResult.SUCCESS,
                        "Rol " + role.getName()
                );
            });

            return user;

        } finally {
            if (request.password() != null) {
                Arrays.fill(request.password(), '\0');
            }
        }
    }

    public List<User> listAll() {
        return userDao.findAll().stream()
                .sorted(Comparator.comparing(User::getUsername))
                .toList();
    }

    public User findById(long id) {
        return userDao.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuario", id));
    }

    public User deactivate(long userId, UserSession session) {
        ValidationUtil.requireNonNull(session, "sesión");

        return transactions.callInTransaction(() -> {
            User user = findById(userId);

            if (user.getId() == session.user().getId()) {
                throw new BusinessRuleException(
                        "No puede desactivar su propio usuario."
                );
            }

            if (user.getStatus() == RecordStatus.INACTIVE) {
                throw new BusinessRuleException("El usuario ya está inactivo.");
            }

            if (isLastActiveAdmin(user)) {
                throw new BusinessRuleException(
                        "No se puede desactivar el último administrador activo."
                );
            }

            return save(withStatus(user, RecordStatus.INACTIVE),
                    session, "DESACTIVAR");
        });
    }

    public User activate(long userId, UserSession session) {
        ValidationUtil.requireNonNull(session, "sesión");

        return transactions.callInTransaction(() -> {
            User user = findById(userId);

            if (user.getStatus() == RecordStatus.ACTIVE) {
                throw new BusinessRuleException("El usuario ya está activo.");
            }

            return save(withStatus(user, RecordStatus.ACTIVE),
                    session, "ACTIVAR");
        });
    }

    public void changeOwnPassword(
            UserSession session,
            char[] current,
            char[] next) {

        ValidationUtil.requireNonNull(session, "sesión");

        try {
            User user = findById(session.user().getId());

            if (current == null ||
                    !passwordHasher.matches(current, user.getPasswordHash())) {
                throw new ValidationException(
                        "La contraseña actual no es correcta."
                );
            }

            AdminBootstrapService.validatePassword(next);

            if (Arrays.equals(current, next)) {
                throw new ValidationException(
                        "La nueva contraseña debe ser distinta de la actual."
                );
            }

            User changed = new User(
                    user.getId(),
                    user.getRoleId(),
                    user.getDocument(),
                    user.getFirstNames(),
                    user.getLastNames(),
                    user.getEmail(),
                    user.getUsername(),
                    passwordHasher.hash(next),
                    user.getStatus(),
                    user.getRegisteredAt()
            );

            transactions.runInTransaction(
                    () -> save(changed, session, "CAMBIAR_CLAVE")
            );

        } finally {
            if (current != null) {
                Arrays.fill(current, '\0');
            }
            if (next != null) {
                Arrays.fill(next, '\0');
            }
        }
    }

    private void requireUnique(User user) {
        if (userDao.findByUsername(user.getUsername()).isPresent()) {
            throw new BusinessRuleException(
                    "Ya existe un usuario con ese nombre de usuario."
            );
        }

        for (User other : userDao.findAll()) {
            if (other.getDocument().equalsIgnoreCase(user.getDocument())) {
                throw new BusinessRuleException(
                        "Ya existe un usuario con ese documento."
                );
            }

            if (other.getEmail().equalsIgnoreCase(user.getEmail())) {
                throw new BusinessRuleException(
                        "Ya existe un usuario con ese correo."
                );
            }
        }
    }

    private boolean isLastActiveAdmin(User user) {
        Role role = roleDao.findById(user.getRoleId()).orElse(null);

        if (role == null ||
                !AdminBootstrapService.ADMIN_ROLE.equals(role.getName())) {
            return false;
        }

        return userDao.findAll().stream()
                .filter(other -> other.getRoleId() == user.getRoleId())
                .filter(other -> other.getStatus() == RecordStatus.ACTIVE)
                .count() <= 1;
    }

    private static User withStatus(User user, RecordStatus status) {
        return new User(
                user.getId(),
                user.getRoleId(),
                user.getDocument(),
                user.getFirstNames(),
                user.getLastNames(),
                user.getEmail(),
                user.getUsername(),
                user.getPasswordHash(),
                status,
                user.getRegisteredAt()
        );
    }

    private User save(User user, UserSession session, String operation) {
        userDao.update(user);

        auditService.record(
                session.user().getId(),
                AUDIT_MODULE,
                operation,
                user.getId(),
                AuditResult.SUCCESS,
                null
        );

        return user;
    }
}
