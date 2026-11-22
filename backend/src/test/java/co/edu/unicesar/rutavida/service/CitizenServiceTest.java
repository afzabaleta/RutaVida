package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.controller.WebTestSupport;
import co.edu.unicesar.rutavida.dto.CitizenLogin;
import co.edu.unicesar.rutavida.dto.CitizenRequest;
import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.Role;
import co.edu.unicesar.rutavida.model.User;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;

import static co.edu.unicesar.rutavida.service.PanicTestSupport.COORDINATOR;
import static co.edu.unicesar.rutavida.service.PanicTestSupport.POLICE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Citizens of the panic button (DT-13, CU-23, RN-16) on in-memory DAOs, without Oracle. */
class CitizenServiceTest {

    private PanicTestSupport support;
    private WebTestSupport.MutableClock clock;
    private CitizenService service;

    @BeforeEach
    void setUp() {
        support = new PanicTestSupport();
        clock = new WebTestSupport.MutableClock(Instant.parse("2026-11-15T15:00:00Z"));
        service = new CitizenService(support.users, support.roles, support.phoneSessions, support.hasher,
                new AuditService(support.city.audit, clock),
                new AlertService(support.alerts, support.users, support.roles, support.city.transactions, clock),
                support.city.transactions, clock);
    }

    private static CitizenRequest request(String username, Boolean consent) {
        return new CitizenRequest("1065" + username.length() + "0001", "Laura", "Prueba",
                username + "@ejemplo.com", username, "Clave-Segura1".toCharArray(), consent);
    }

    // ------------------------------------------------------------------ CU-23: register

    @Test
    void aPersonRegistersAsCitizenWithAuthorizationAndThePasswordIsErased() {
        CitizenRequest request = request("laura.prueba", true);

        User user = service.register(request);

        assertEquals(7, user.getRoleId(), "role CIUDADANO");
        assertEquals(RecordStatus.ACTIVE, user.getStatus());
        assertNotEquals("Clave-Segura1", user.getPasswordHash());
        assertTrue(support.hasher.matches("Clave-Segura1".toCharArray(), user.getPasswordHash()));
        assertArrayEquals(new char[13], request.password(), "the password is erased from memory");
        assertTrue(support.city.audit.findByRecord("USUARIOS", user.getId()).stream()
                .anyMatch(log -> "REGISTRAR_CIUDADANO".equals(log.getOperation())
                        && log.getDetail().contains("Ley 1581")), "the authorization is audited");
    }

    @Test
    void withoutAuthorizationOrWithTakenDataThereIsNoAccount() {
        assertThrows(ValidationException.class, () -> service.register(request("sin.permiso", false)));
        assertThrows(ValidationException.class, () -> service.register(request("sin.respuesta", null)));
        assertThrows(ValidationException.class, () -> service.register(new CitizenRequest("1065",
                "Laura", "Prueba", "laura@ejemplo.com", "laura.corta", "corta1".toCharArray(), true)));

        service.register(request("laura.prueba", true));
        assertThrows(BusinessRuleException.class, () -> service.register(request("laura.prueba", true)),
                "same username");
        assertThrows(BusinessRuleException.class, () -> service.register(new CitizenRequest("OTRO000001",
                "Ana", "Prueba", "laura.prueba@ejemplo.com", "ana.prueba", "Clave-Segura1".toCharArray(), true)),
                "same email");
    }

    @Test
    void withoutTheRoleOf08TheRegistrationSaysWhatToRun() {
        InMemoryRoleDAO withoutCitizens = new InMemoryRoleDAO();
        withoutCitizens.add(new Role(3, "COORDINADOR", "Prueba", RecordStatus.ACTIVE, Set.of()));
        CitizenService before08 = new CitizenService(support.users, withoutCitizens, support.phoneSessions,
                support.hasher, new AuditService(support.city.audit, clock),
                new AlertService(support.alerts, support.users, withoutCitizens, support.city.transactions, clock),
                support.city.transactions, clock);

        BusinessRuleException missing = assertThrows(BusinessRuleException.class,
                () -> before08.register(request("laura.prueba", true)));
        assertTrue(missing.getMessage().contains("08_ciudadanos.sql"));
    }

    // ------------------------------------------------------------------ the 30-day session

    @Test
    void thePhoneStaysLoggedInFor30DaysWithoutIdleTimeoutAndOnlyTheHashIsSaved() {
        UserSession citizen = support.citizen(1);

        CitizenLogin login = service.startSession(citizen);

        assertEquals(64, login.token().length());
        assertEquals(login.startedAt().plusDays(30), login.expiresAt());
        assertEquals(CitizenService.hashToken(login.token()), support.phoneSessions.all().get(0).getTokenHash());
        assertNotEquals(login.token(), support.phoneSessions.all().get(0).getTokenHash());

        clock.advance(Duration.ofDays(29));
        assertEquals(citizen.user().getId(),
                service.findSession(login.token()).orElseThrow().session().user().getId(),
                "29 days without using it: still logged in");

        clock.advance(Duration.ofDays(2));
        assertTrue(service.findSession(login.token()).isEmpty(), "after 30 days it logs in again");
    }

    @Test
    void logoutEndsOnlyThatPhoneAndUnknownTokensFindNothing() {
        UserSession citizen = support.citizen(1);
        CitizenLogin phone = service.startSession(citizen);
        CitizenLogin tablet = service.startSession(citizen);

        assertTrue(service.endSession(phone.token()));

        assertTrue(service.findSession(phone.token()).isEmpty());
        assertTrue(service.findSession(tablet.token()).isPresent());
        assertFalse(service.endSession(phone.token()), "already logged out");
        assertTrue(service.findSession("no-es-un-token").isEmpty());
        assertTrue(service.findSession(null).isEmpty());
    }

    @Test
    void onlyCitizensGetAPhoneSessionAndAnInactiveAccountLosesIt() {
        assertThrows(IllegalArgumentException.class, () -> service.startSession(support.sessionOf(POLICE)));
        assertFalse(CitizenService.isCitizen(support.sessionOf(COORDINATOR)));

        UserSession citizen = support.citizen(1);
        String token = service.startSession(citizen).token();
        User user = citizen.user();
        support.users.update(new User(user.getId(), user.getRoleId(), user.getDocument(), user.getFirstNames(),
                user.getLastNames(), user.getEmail(), user.getUsername(), user.getPasswordHash(),
                RecordStatus.INACTIVE, user.getRegisteredAt()));

        assertTrue(service.findSession(token).isEmpty(), "deactivated by an administrator");
    }

    // ------------------------------------------------------------------ RN-16: block

    @Test
    void blockingACitizenDeactivatesItLogsOutEveryPhoneAndTellsTheCoordinators() {
        UserSession citizen = support.citizen(1);
        String phone = service.startSession(citizen).token();
        String tablet = service.startSession(citizen).token();

        assertTrue(service.blockForFalseAlarms(citizen.user().getId(), 2, support.sessionOf(COORDINATOR)));

        assertEquals(RecordStatus.INACTIVE, support.users.findById(citizen.user().getId()).orElseThrow().getStatus());
        assertTrue(service.findSession(phone).isEmpty());
        assertTrue(service.findSession(tablet).isEmpty());
        assertTrue(support.alerts.all().stream().anyMatch(alert -> alert.getMessage().contains("ciudadano1")));
        assertFalse(service.blockForFalseAlarms(citizen.user().getId(), 3, support.sessionOf(COORDINATOR)),
                "already blocked");
    }

    @Test
    void policeOfficersAndStaffAreNeverBlockedForFalseAlarms() {
        assertFalse(service.blockForFalseAlarms(POLICE, 5, support.sessionOf(COORDINATOR)));
        assertEquals(RecordStatus.ACTIVE, support.users.findById(POLICE).orElseThrow().getStatus());
        assertFalse(service.blockForFalseAlarms(999, 5, support.sessionOf(COORDINATOR)), "no such user");
    }

    @Test
    void theDatesOfTheSessionComeFromTheClock() {
        LocalDateTime now = LocalDateTime.now(clock);
        CitizenLogin login = service.startSession(support.citizen(1));

        assertEquals(now, login.startedAt());
    }
}
