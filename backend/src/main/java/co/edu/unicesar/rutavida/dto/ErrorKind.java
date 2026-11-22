package co.edu.unicesar.rutavida.dto;

/**
 * Why an operation failed. The web API turns it into an HTTP status code
 * (see docs/api.md, section 1.2); the rest of the system only needs the message.
 */
public enum ErrorKind {
    /** Invalid data sent by the user (ValidationException). */
    VALIDATION,
    /** No session, or the session expired (SessionRequiredException). */
    AUTHENTICATION,
    /** The role lacks the permission (AccessDeniedException). */
    PERMISSION,
    /** The requested record does not exist (NotFoundException). */
    NOT_FOUND,
    /** A business rule was broken (BusinessRuleException). */
    BUSINESS_RULE,
    /** The database is not reachable (DataAccessException). */
    UNAVAILABLE,
    /** Anything else. */
    UNEXPECTED
}