package co.edu.unicesar.rutavida.controller;

import co.edu.unicesar.rutavida.dto.ErrorKind;
import co.edu.unicesar.rutavida.dto.LoginResponse;
import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.util.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebSessionControllerTest {

    private WebTestSupport support;
    private WebSessionController sessions;

    @BeforeEach
    void setUp() {
        support = new WebTestSupport();
        sessions = support.sessions();
    }

    private String loginAna() {
        OperationResult<LoginResponse> result = sessions.login("ana.perez", WebTestSupport.PASSWORD.toCharArray());
        assertTrue(result.success(), result.message());
        return "Bearer " + result.value().token();
    }

    @Test
    void loginGivesARandomTokenAndTheSessionData() {
        char[] password = WebTestSupport.PASSWORD.toCharArray();
        OperationResult<LoginResponse> first = sessions.login("ana.perez", password);
        OperationResult<LoginResponse> second = sessions.login("ana.perez", WebTestSupport.PASSWORD.toCharArray());

        assertTrue(first.success());
        LoginResponse response = first.value();
        assertTrue(response.token().matches("[0-9a-f]{64}"), "32 random bytes in hexadecimal");
        assertNotEquals(response.token(), second.value().token());
        assertEquals(LocalDateTime.of(2026, 11, 5, 17, 0), response.expiresAt(), "login at 9:00 + 8 hours");
        assertEquals(30, response.idleTimeoutMinutes());
        assertEquals("ana.perez", response.user().username());
        assertEquals("Ana", response.user().firstNames());
        assertEquals("OPERADOR_CENTRAL", response.role());
        assertEquals(List.of("EMERGENCIAS:CONSULTAR", "EMERGENCIAS:GESTIONAR"), response.permissions());
        assertArrayEquals(new char[password.length], password);
        assertEquals(2, sessions.openSessions());
    }

    @Test
    void wrongCredentialsAreAuthenticationErrorsAndEmptyFieldsAreValidationErrors() {
        OperationResult<LoginResponse> wrong = sessions.login("ana.perez", "otra".toCharArray());
        assertFalse(wrong.success());
        assertEquals(ErrorKind.AUTHENTICATION, wrong.error());
        assertEquals("Usuario o contraseña incorrectos", wrong.message());

        OperationResult<LoginResponse> empty = sessions.login(" ", new char[0]);
        assertEquals(ErrorKind.VALIDATION, empty.error());
        assertEquals(0, sessions.openSessions());
    }

    @Test
    void theTokenOpensTheSessionAndAnythingElseIsRejected() {
        String header = loginAna();

        OperationResult<UserSession> found = sessions.authenticate(header, true);
        assertTrue(found.success());
        assertEquals("ana.perez", found.value().user().getUsername());
        assertTrue(sessions.authenticate(header.replace("Bearer", "bearer"), true).success());

        for (String bad : new String[]{null, "", "Bearer", "Bearer ", "Basic abc", "Bearer 1234"}) {
            OperationResult<UserSession> rejected = sessions.authenticate(bad, true);
            assertEquals(ErrorKind.AUTHENTICATION, rejected.error(), "header: " + bad);
            assertEquals(Messages.SESSION_REQUIRED, rejected.message());
        }
    }

    @Test
    void theSessionEndsAfterThirtyMinutesWithoutUse() {
        String header = loginAna();

        support.clock.advance(Duration.ofMinutes(29));
        assertTrue(sessions.authenticate(header, true).success(), "used at minute 29: counter restarts");
        support.clock.advance(Duration.ofMinutes(29));
        assertTrue(sessions.authenticate(header, true).success(), "29 minutes after the last use");
        support.clock.advance(Duration.ofMinutes(30));

        OperationResult<UserSession> expired = sessions.authenticate(header, true);
        assertEquals(ErrorKind.AUTHENTICATION, expired.error());
        assertEquals(Messages.SESSION_EXPIRED, expired.message());
        assertEquals(0, sessions.openSessions(), "an expired token is removed");
    }

    @Test
    void theAutomaticQueriesOfTheMapDoNotKeepTheSessionAlive() {
        String header = loginAna();

        for (int minute = 5; minute <= 25; minute += 5) {
            support.clock.advance(Duration.ofMinutes(5));
            assertTrue(sessions.authenticate(header, false).success());
        }
        support.clock.advance(Duration.ofMinutes(5));
        assertFalse(sessions.authenticate(header, false).success(), "30 minutes without a user action");
    }

    @Test
    void theSessionEndsAfterEightHoursEvenWithActivity() {
        String header = loginAna();

        for (int i = 0; i < 31; i++) {
            support.clock.advance(Duration.ofMinutes(15));
            assertTrue(sessions.authenticate(header, true).success(), "still inside the 8 hours");
        }
        support.clock.advance(Duration.ofMinutes(15));
        assertEquals(Messages.SESSION_EXPIRED, sessions.authenticate(header, true).message(), "8 hours reached");
    }

    @Test
    void logoutClosesTheTokenAndIsAudited() {
        String header = loginAna();

        assertTrue(sessions.logout(header).success());
        assertFalse(sessions.authenticate(header, true).success());
        assertEquals("LOGOUT", support.auditDao.last().getOperation());
        assertTrue(sessions.logout(header).success(), "a second logout is not an error");
        assertTrue(sessions.logout(null).success());
    }

    @Test
    void permissionsAreCheckedWithTheRoleOfTheSession() {
        UserSession ana = sessions.authenticate(loginAna(), true).value();
        String luisToken = sessions.login("luis.gomez", WebTestSupport.PASSWORD.toCharArray()).value().token();
        UserSession luis = sessions.authenticate("Bearer " + luisToken, true).value();

        assertTrue(sessions.authorize(ana, "EMERGENCIAS", "GESTIONAR").success());
        OperationResult<Void> denied = sessions.authorize(luis, "EMERGENCIAS", "GESTIONAR");
        assertEquals(ErrorKind.PERMISSION, denied.error());
        assertEquals("No tiene permiso para realizar esta operación.", denied.message());
    }

    @Test
    void describeReturnsTheSessionWithoutTheToken() {
        String header = loginAna();

        OperationResult<LoginResponse> described = sessions.describe(header);
        assertTrue(described.success());
        assertNull(described.value().token());
        assertEquals("ana.perez", described.value().user().username());
        assertEquals(ErrorKind.AUTHENTICATION, sessions.describe("Bearer otro").error());
    }

    @Test
    void runAsMakesTheSessionCurrentOnlyDuringTheRequest() throws Exception {
        UserSession ana = sessions.authenticate(loginAna(), true).value();
        AtomicReference<UserSession> seen = new AtomicReference<>();

        sessions.runAs(ana, () -> seen.set(support.sessionContext.current()));

        assertSame(ana, seen.get());
        assertFalse(support.sessionContext.isActive(), "nothing is left after the request");
    }

    @Test
    void idleLongerThanTheMaximumIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new WebSessionController(support.authService,
                support.sessionContext, support.clock, Duration.ofHours(9), Duration.ofHours(8)));
    }
}