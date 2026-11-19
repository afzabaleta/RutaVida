package co.edu.unicesar.rutavida.controller;

import co.edu.unicesar.rutavida.dto.LoginResponse;
import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.service.AuditService;
import co.edu.unicesar.rutavida.service.AuthService;
import co.edu.unicesar.rutavida.service.EmergencyTestSupport;
import co.edu.unicesar.rutavida.service.InMemoryAuditDAO;
import co.edu.unicesar.rutavida.service.PanicTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The 30-day phone session of a citizen in the web sessions (DT-13), without Oracle. */
class CitizenWebSessionTest {

    private PanicTestSupport support;
    private AuthService auth;

    @BeforeEach
    void setUp() {
        support = new PanicTestSupport();
        auth = new AuthService(support.users, support.roles, support.hasher,
                new AuditService(new InMemoryAuditDAO(), EmergencyTestSupport.CLOCK), EmergencyTestSupport.CLOCK);
        support.citizen(1);
    }

    private WebSessionController server() {
        return new WebSessionController(auth, new SessionContext(), EmergencyTestSupport.CLOCK,
                support.citizenService());
    }

    @Test
    void theCitizenTokenSurvivesARestartOfTheServerAndIsNotKeptInMemory() {
        WebSessionController before = server();
        OperationResult<LoginResponse> login = before.login("ciudadano1", PanicTestSupport.PASSWORD.toCharArray());

        assertTrue(login.success(), login.message());
        assertEquals(0, login.value().idleTimeoutMinutes());
        assertEquals(0, before.openSessions(), "saved in the database, not in memory");

        WebSessionController afterRestart = server();
        OperationResult<UserSession> found = afterRestart.authenticate("Bearer " + login.value().token(), true);

        assertTrue(found.success(), found.message());
        assertEquals("ciudadano1", found.value().user().getUsername());
        assertEquals("CIUDADANO", afterRestart.describe("Bearer " + login.value().token()).value().role());
    }

    @Test
    void logoutEndsThePhoneSession() {
        WebSessionController sessions = server();
        String header = "Bearer " + sessions.login("ciudadano1", PanicTestSupport.PASSWORD.toCharArray()).value().token();

        sessions.logout(header);

        assertFalse(sessions.authenticate(header, true).success());
    }
}
