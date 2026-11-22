package co.edu.unicesar.rutavida.api;

import co.edu.unicesar.rutavida.dto.ErrorKind;
import co.edu.unicesar.rutavida.dto.OperationResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The table of docs/api.md, section 1.2. */
class ApiStatusTest {

    @Test
    void everyErrorKindHasItsHttpCode() {
        assertEquals(400, ApiStatus.of(ErrorKind.VALIDATION));
        assertEquals(401, ApiStatus.of(ErrorKind.AUTHENTICATION));
        assertEquals(403, ApiStatus.of(ErrorKind.PERMISSION));
        assertEquals(404, ApiStatus.of(ErrorKind.NOT_FOUND));
        assertEquals(409, ApiStatus.of(ErrorKind.BUSINESS_RULE));
        assertEquals(503, ApiStatus.of(ErrorKind.UNAVAILABLE));
        assertEquals(500, ApiStatus.of(ErrorKind.UNEXPECTED));
        assertEquals(500, ApiStatus.of((ErrorKind) null));
    }

    @Test
    void aSuccessUsesTheStatusOfTheRoute() {
        assertEquals(200, ApiStatus.of(OperationResult.ok("x"), ApiStatus.OK));
        assertEquals(201, ApiStatus.of(OperationResult.ok("x"), ApiStatus.CREATED));
        assertEquals(409, ApiStatus.of(OperationResult.error(ErrorKind.BUSINESS_RULE, "No."), ApiStatus.CREATED));
    }
}