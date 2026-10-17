
package co.edu.unicesar.rutavida.util;

import co.edu.unicesar.rutavida.exception.ValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidationUtilTest {

    @Test
    void requireTextTrimsTheValue() {
        assertEquals("Calle 16",
                ValidationUtil.requireText("     Calle 16 ",
                        "dirección", 50));
    }

    @Test
    void requireTextRejectsBlankAndTooLong() {
        assertThrows(ValidationException.class, () ->
                ValidationUtil.requireText("   ", "nombre", 10));
        assertThrows(ValidationException.class, () ->
                ValidationUtil.requireText(null, "nombre", 10));
        assertThrows(ValidationException.class, () ->
                ValidationUtil.requireText("12345678901", "nombre", 10));
    }

    @Test
    void optionalTextTurnsBlankIntoNull() {
        assertNull(ValidationUtil.optionalText(" ", "barrio", 100));
        assertNull(ValidationUtil.optionalText(null, "barrio", 100));

        assertEquals("Centro",
                ValidationUtil.optionalText(" Centro ", "barrio", 100));
    }

    @Test
    void requireInRangeRejectsValuesOutside() {
        assertEquals(5,
                ValidationUtil.requireInRange(5, 1, 10, "prioridad"));

        assertThrows(ValidationException.class, () ->
                ValidationUtil.requireInRange(11, 1, 10, "prioridad"));
    }

    @Test
    void messagesAreInSpanish() {
        ValidationException error = assertThrows(
                ValidationException.class,
                () -> ValidationUtil.requireInRange(
                        -91, -90, 90, "latitud"));

        assertEquals(
                "El campo latitud debe estar entre -90 y 90.",
                error.getMessage());
    }

    @Test
    void requirePositiveIdAndNonNull() {
        assertEquals(3,
                ValidationUtil.requirePositiveId(3, "rol"));

        assertThrows(ValidationException.class, () ->
                ValidationUtil.requirePositiveId(0, "rol"));

        assertThrows(ValidationException.class, () ->
                ValidationUtil.requireNonNull(null, "estado"));
    }
}
