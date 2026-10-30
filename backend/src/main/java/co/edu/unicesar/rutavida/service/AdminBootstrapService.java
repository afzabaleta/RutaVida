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
import co.edu.unicesar.rutavida.util.ValidationUtil;

import java.util.Locale;
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
        if (role.getStatus() != RecordStatus.ACTIVE) {
            throw new BusinessRuleException("El rol ADMINISTRADOR está inactivo.");
        }
        User admin = new User(0, role.getId(),
                ValidationUtil.requireText(document, "documento", 20),
                ValidationUtil.requireText(firstNames, "nombres", 80),
                ValidationUtil.requireText(lastNames, "apellidos", 80),
                ValidationUtil.requireText(email, "correo", 120).toLowerCase(Locale.ROOT),
                AuthService.normalizeUsername(ValidationUtil.requireText(username, "usuario", 40)),
                passwordHasher.hash(password), RecordStatus.ACTIVE, LocalDateTime.now(clock));
        validateFormats(admin);
        if (userDao.findByUsername(admin.getUsername()).isPresent()) {
            throw new BusinessRuleException("Ya existe un usuario con ese nombre de usuario.");
        }
        transactions.runInTransaction(() -> {
            long id = userDao.insert(admin);
            auditService.record(id, "SEGURIDAD", "CREAR_ADMIN", id, AuditResult.SUCCESS,
                    "Administrador creado con CreateAdminUser");
        });
        return admin;
    }

    /** Format rules of a user (the same ones the USUARIO table expects). */
    static void validateFormats(User user) {
        if (!user.getDocument().matches("[a-zA-Z0-9]{5,20}")) {
            throw new ValidationException("El documento debe tener entre 5 y 20 letras o números.");
        }
        if (!user.getEmail().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new ValidationException("El correo electrónico no tiene un formato válido.");
        }
        if (!user.getUsername().matches("[a-z0-9._]{4,40}")) {
            throw new ValidationException("El usuario debe tener entre 4 y 40 caracteres válidos.");
        }
    }

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