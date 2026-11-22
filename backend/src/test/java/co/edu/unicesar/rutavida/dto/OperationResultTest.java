
package co.edu.unicesar.rutavida.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import co.edu.unicesar.rutavida.exception.AccessDeniedException;
import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.DataAccessException;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.exception.SessionRequiredException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.util.Messages;

class OperationResultTest {

    @Test
    void okCarriesTheValueAndNoMessage() {
        OperationResult<String> result =
                OperationResult.ok("listo");

        assertTrue(result.success());
        assertEquals("listo", result.value());
        assertNull(result.message());
    }

    @Test
    void okWithoutAValueIsAllowed() {
        OperationResult<Void> result =
                OperationResult.ok(null);

        assertTrue(result.success());
        assertNull(result.value());
    }

    @Test
    void errorCarriesTheMessageAndNoValue() {
        OperationResult<String> result =
                OperationResult.error(
                        "Usuario o contraseña incorrectos");

        assertFalse(result.success());
        assertEquals(
                "Usuario o contraseña incorrectos",
                result.message());
        assertNull(result.value());
    }

    @Test
    void anErrorWithoutAMessageIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> OperationResult.error(null));

        assertThrows(
                IllegalArgumentException.class,
                () -> OperationResult.error("     "));
    }

    @Test
    void okHasNoErrorKindAndOldErrorsAreUnexpected() {
        assertNull(OperationResult.ok("listo").error());
        assertEquals(ErrorKind.UNEXPECTED, OperationResult.error("Algo falló").error());
        assertEquals(ErrorKind.UNEXPECTED, new OperationResult<>(false, "Algo falló", null).error());
        assertNull(new OperationResult<>(true, null, "x", ErrorKind.VALIDATION).error(),
                "a success never carries an error kind");
    }

    @Test
    void failureTakesTheKindFromTheExceptionAndTheMessageFromMessages() {
        assertEquals(ErrorKind.VALIDATION,
                OperationResult.failure(new ValidationException("La latitud es obligatoria.")).error());
        assertEquals(ErrorKind.AUTHENTICATION,
                OperationResult.failure(new SessionRequiredException(Messages.SESSION_REQUIRED)).error());
        assertEquals(ErrorKind.PERMISSION,
                OperationResult.failure(new AccessDeniedException("Sin permiso.")).error());
        assertEquals(ErrorKind.NOT_FOUND,
                OperationResult.failure(new NotFoundException("Emergencia", 5L)).error());
        assertEquals(ErrorKind.BUSINESS_RULE,
                OperationResult.failure(new BusinessRuleException("No se puede.")).error());
        assertEquals(ErrorKind.UNEXPECTED,
                OperationResult.failure(new IllegalStateException("detalle técnico")).error());

        OperationResult<Void> down = OperationResult.failure(new DataAccessException("ORA-12541", null));
        assertEquals(ErrorKind.UNAVAILABLE, down.error());
        assertEquals(Messages.DATABASE_DOWN, down.message(), "no technical detail reaches the user");
        assertEquals("La latitud es obligatoria.",
                OperationResult.failure(new ValidationException("La latitud es obligatoria.")).message());
    }
}
