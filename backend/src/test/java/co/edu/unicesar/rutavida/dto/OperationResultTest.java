
package co.edu.unicesar.rutavida.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
