package co.edu.unicesar.rutavida.controller;

import co.edu.unicesar.rutavida.dto.AuditEntry;
import co.edu.unicesar.rutavida.dto.AuditQuery;
import co.edu.unicesar.rutavida.dto.ErrorKind;
import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.service.AuditQueryService;
import co.edu.unicesar.rutavida.service.AuthService;
import co.edu.unicesar.rutavida.util.Messages;

import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Consult the audit (CU-21): needs AUDITORIA/CONSULTAR (administrator and coordinator). */
public class AuditController {

    private static final Logger LOG = Logger.getLogger(AuditController.class.getName());

    static final String MODULE = "AUDITORIA";
    static final String VIEW = "CONSULTAR";

    private final AuditQueryService auditQueryService;
    private final AuthService authService;
    private final SessionContext session;

    public AuditController(AuditQueryService auditQueryService, AuthService authService, SessionContext session) {
        this.auditQueryService = Objects.requireNonNull(auditQueryService, "auditQueryService");
        this.authService = Objects.requireNonNull(authService, "authService");
        this.session = Objects.requireNonNull(session, "session");
    }

    /** Who did what and when, with the filters of {@code query}, newest first. */
    public OperationResult<List<AuditEntry>> search(AuditQuery query) {
        if (!session.isActive()) {
            return OperationResult.error(ErrorKind.AUTHENTICATION, Messages.SESSION_REQUIRED);
        }
        try {
            UserSession user = session.current();
            authService.requirePermission(user, MODULE, VIEW);
            return OperationResult.ok(auditQueryService.search(query));
        } catch (RuntimeException e) {
            OperationResult<List<AuditEntry>> failure = OperationResult.failure(e);
            if (failure.error() == ErrorKind.UNEXPECTED || failure.error() == ErrorKind.UNAVAILABLE) {
                LOG.log(Level.WARNING, "Audit query failed", e);
            }
            return failure;
        }
    }
}
