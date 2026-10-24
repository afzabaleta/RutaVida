package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.RoleDAO;
import co.edu.unicesar.rutavida.dao.TransactionManager;
import co.edu.unicesar.rutavida.dao.UserDAO;
import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.Role;
import co.edu.unicesar.rutavida.model.User;
import co.edu.unicesar.rutavida.model.enums.AuditResult;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import co.edu.unicesar.rutavida.util.PasswordHasher;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Creates an ADMINISTRADOR account without a logged-in user: the first account of a new
 * installation, used by the CreateAdminUser tool. Normal user management (with a session
 * and permissions) belongs to UserService.
 *
 * <p>The password is only kept as a PBKDF2 hash; the user and its audit row are saved in
 * one transaction.
 */
public class AdminBootstrapService {

    static final String ADMIN_ROLE = "ADMINISTRADOR";
    static final int MIN_PASSWORD_LENGTH = 10;

    private final UserDAO userDao;
    private final RoleDAO roleDao;
    private final PasswordHasher passwordHasher;
    private final AuditService auditService;
    private final TransactionManager transactions;
    private final Clock clock;

    public AdminBootstrapService(UserDAO userDao, RoleDAO roleDao,
                                 PasswordHasher passwordHasher, AuditService auditService,
                                 TransactionManager transactions, Clock clock) {
        this.userDao = Objects.requireNonNull(userDao, "userDao");
        this.roleDao = Objects.requireNonNull(roleDao, "roleDao");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher");
        this.auditService = Objects.requireNonNull(auditService, "auditService");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** How many users already have the ADMINISTRADOR role (the tool warns if it is not 0). */
    public long countAdmins() {
        long roleId = adminRole().getId();
        return userDao.findAll().stream()
                .filter(user -> user.getRoleId() == roleId)
                .count();
    }

    /**
     * Creates an active administrator.
     *
     * @throws ValidationException   if the password or any field is not valid
     * @throws BusinessRuleException if the username is taken or the role is inactive
     */
    public User createAdmin(String document, String firstNames, String lastNames,
                            String email, String username, char[] password) {
        validatePassword(password);
        Role role = adminRole();
        if (!role.isActive()) {
            throw new BusinessRuleException("El rol ADMINISTRADOR está inactivo.");
        }
        if (username != null && userDao.findByUsername(username).isPresent()) {
            throw new BusinessRuleException("Ya existe un usuario con ese nombre de usuario.");
        }

        User admin = new User(0, role.getId(), document, firstNames, lastNames, email,
                username, passwordHasher.hash(password), RecordStatus.ACTIVE,
                LocalDateTime.now(clock));

        transactions.runInTransaction(() -> {
            long id = userDao.insert(admin);
            auditService.record(id, "SEGURIDAD", "CREAR_ADMIN", id, AuditResult.SUCCESS,
                    "Administrador creado con CreateAdminUser");
        });
        return admin;
    }

    /** At least 10 characters, with letters and numbers. */
    static void validatePassword(char[] password) {
        if (password == null || password.length < MIN_PASSWORD_LENGTH) {
            throw new ValidationException(
                    "La contraseña debe tener al menos " + MIN_PASSWORD_LENGTH + " caracteres.");
        }
        boolean letter = false;
        boolean digit = false;
        for (char c : password) {
            letter |= Character.isLetter(c);
            digit |= Character.isDigit(c);
        }
        if (!letter || !digit) {
            throw new ValidationException("La contraseña debe tener letras y números.");
        }
    }

    private Role adminRole() {
        return roleDao.findAll().stream()
                .filter(role -> ADMIN_ROLE.equals(role.getName()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Rol", ADMIN_ROLE));
    }
}