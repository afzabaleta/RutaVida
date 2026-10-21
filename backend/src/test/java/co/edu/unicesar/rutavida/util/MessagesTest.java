
package co.edu.unicesar.rutavida.util;

import co.edu.unicesar.rutavida.exception.AccessDeniedException;
import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.DataAccessException;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessagesTest {

    @Test
    void knownKeysHaveTheirSpanishText() {
        assertEquals(
                "No hay conexión con la base de datos.",
                Messages.get(Messages.DATABASE_DOWN));

        assertEquals(
                "Debe iniciar sesión.",
                Messages.get(Messages.SESSION_REQUIRED));
    }

    @Test
    void anUnknownKeyIsAProgrammingError() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Messages.get("no.existe"));
    }

    @Test
    void errorsWrittenForTheUserKeepTheirMessage() {
        assertEquals(
                "El usuario está inactivo",
                Messages.userMessage(
                        new BusinessRuleException(
                                "El usuario está inactivo")));

        assertEquals(
                "La latitud debe estar entre -90 y 90",
                Messages.userMessage(
                        new ValidationException(
                                "La latitud debe estar entre -90 y 90")));

        assertEquals(
                "Debe iniciar sesión.",
                Messages.userMessage(
                        new AccessDeniedException(
                                "Debe iniciar sesión.")));
    }

    @Test
    void technicalErrorsGetAGenericSpanishText() {
        String database = Messages.userMessage(
                new DataAccessException(
                        "Database operation failed: ORA-12541: TNS:no listener",
                        null));

        assertEquals(
                "No hay conexión con la base de datos.",
                database);

        assertFalse(database.contains("ORA"));

        assertEquals(
                "No se encontró la información solicitada.",
                Messages.userMessage(
                        new NotFoundException("Rol", 3)));

        assertEquals(
                "Ocurrió un error inesperado. Intente de nuevo.",
                Messages.userMessage(
                        new IllegalStateException("boom")));
    }
}
