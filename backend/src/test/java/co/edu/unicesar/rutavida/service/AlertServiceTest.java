
package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.Alert;
import co.edu.unicesar.rutavida.model.EmergencyVehicle;
import co.edu.unicesar.rutavida.model.RoadIncident;
import co.edu.unicesar.rutavida.model.enums.AlertType;
import co.edu.unicesar.rutavida.model.enums.IncidentOrigin;
import co.edu.unicesar.rutavida.model.enums.IncidentStatus;
import co.edu.unicesar.rutavida.model.enums.IncidentType;
import co.edu.unicesar.rutavida.model.enums.Severity;
import co.edu.unicesar.rutavida.model.enums.VehicleOperationalStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static co.edu.unicesar.rutavida.service.AlertTestSupport.ADMINISTRATOR;
import static co.edu.unicesar.rutavida.service.AlertTestSupport.COORDINATOR;
import static co.edu.unicesar.rutavida.service.AlertTestSupport.MISSION;
import static co.edu.unicesar.rutavida.service.AlertTestSupport.OPERATOR;
import static co.edu.unicesar.rutavida.service.EmergencyTestSupport.CLOCK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AlertService on in-memory DAOs, without Oracle. */
class AlertServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    private final AlertTestSupport support = new AlertTestSupport();
    private final AlertService service = support.service();

    private static RoadIncident incident(IncidentType type, boolean blocks) {
        return new RoadIncident(
                31, 413, type, Severity.HIGH, blocks, null, NOW,
                null, IncidentStatus.ACTIVE, IncidentOrigin.OPERATOR
        );
    }

    private static EmergencyVehicle unit(VehicleOperationalStatus status) {
        return new EmergencyVehicle(
                1, 1, 2, "EJEMPLO-AMB-01", null, status, null, null
        );
    }

    // Alerts of a mission

    @Test
    void aRecalculatedRouteIsToldToTheCoordinatorOfTheMission() {
        Alert alert = service.routeRecalculated(
                MISSION, "EJEMPLO-AMB-01", 2.46
        );

        assertEquals(COORDINATOR, alert.getUserId());
        assertEquals(15L, alert.getEmergencyId());
        assertEquals(AlertType.ROUTE_RECALCULATION, alert.getType());
        assertEquals(
                "Se recalculó la ruta de EJEMPLO-AMB-01: llega 2,5 min antes.",
                alert.getMessage()
        );
        assertEquals(NOW, alert.getCreatedAt());
        assertFalse(alert.isRead());
        assertTrue(alert.getId() > 0);
        assertEquals(1, support.alertDao.all().size());

        assertEquals(
                "Se recalculó la ruta de EJEMPLO-AMB-01 porque la anterior ya no se puede usar.",
                service.routeRecalculated(
                        MISSION, "EJEMPLO-AMB-01", 0
                ).getMessage()
        );
    }

    @Test
    void theTypeOfAnIncidentAlertDependsOnTheIncident() {
        Alert closed = service.incidentOnRoute(
                MISSION,
                "EJEMPLO-AMB-01",
                incident(IncidentType.ACCIDENT, true),
                "Calle 16"
        );

        assertEquals(AlertType.ROAD_CLOSURE, closed.getType());
        assertEquals(
                "Accidente en Calle 16 (vía cerrada) en la ruta de EJEMPLO-AMB-01.",
                closed.getMessage()
        );

        Alert accident = service.incidentOnRoute(
                MISSION,
                "EJEMPLO-AMB-01",
                incident(IncidentType.ACCIDENT, false),
                "Calle 16"
        );

        assertEquals(AlertType.ACCIDENT_ON_ROUTE, accident.getType());
        assertEquals(
                "Accidente en Calle 16 en la ruta de EJEMPLO-AMB-01.",
                accident.getMessage()
        );

        assertEquals(
                AlertType.OTHER,
                service.incidentOnRoute(
                        MISSION, "EJEMPLO-AMB-01",
                        incident(IncidentType.ROADWORK, false),
                        "Carrera 9"
                ).getType()
        );

        assertEquals(
                AlertType.ROAD_CLOSURE,
                service.incidentOnRoute(
                        MISSION, "EJEMPLO-AMB-01",
                        incident(IncidentType.CLOSURE, false),
                        "Carrera 9"
                ).getType()
        );

        Alert congestion = service.congestionOnRoute(
                MISSION, "EJEMPLO-AMB-01", "Avenida Simón Bolívar"
        );

        assertEquals(AlertType.CONGESTION, congestion.getType());
        assertEquals(
                "Congestión en Avenida Simón Bolívar en la ruta de EJEMPLO-AMB-01.",
                congestion.getMessage()
        );
    }

    @Test
    void wrongDataSavesNothing() {
        assertThrows(
                ValidationException.class,
                () -> service.routeRecalculated(
                        null, "EJEMPLO-AMB-01", 1
                )
        );

        assertThrows(
                ValidationException.class,
                () -> service.routeRecalculated(MISSION, " ", 1)
        );

        assertThrows(
                ValidationException.class,
                () -> service.congestionOnRoute(
                        MISSION, "EJEMPLO-AMB-01", null
                )
        );

        assertThrows(
                ValidationException.class,
                () -> service.incidentOnRoute(
                        MISSION, "EJEMPLO-AMB-01", null, "Calle 16"
                )
        );

        assertThrows(
                ValidationException.class,
                () -> service.congestionOnRoute(
                        MISSION, "EJEMPLO-AMB-01", "x".repeat(121)
                )
        );

        assertTrue(support.alertDao.all().isEmpty());
    }

    // General alerts

    @Test
    void aChangeOfAvailabilityGoesToEveryActiveDispatcher() {
        List<Alert> alerts = service.availabilityChanged(
                unit(VehicleOperationalStatus.AVAILABLE),
                VehicleOperationalStatus.BUSY
        );

        assertEquals(
                List.of(COORDINATOR, ADMINISTRATOR),
                alerts.stream().map(Alert::getUserId).toList()
        );

        for (Alert alert : alerts) {
            assertEquals(AlertType.AVAILABILITY_CHANGE, alert.getType());
            assertNull(alert.getEmergencyId());
            assertEquals(
                    "La unidad EJEMPLO-AMB-01 pasó de OCUPADO a DISPONIBLE.",
                    alert.getMessage()
            );
        }

        assertEquals(1, support.transactions.calls);

        assertTrue(
                service.availabilityChanged(
                        unit(VehicleOperationalStatus.BUSY),
                        VehicleOperationalStatus.BUSY
                ).isEmpty()
        );

        assertEquals(2, support.alertDao.all().size());
    }

    // The user reads alerts

    @Test
    void eachUserSeesOnlyHisAlertsNewestFirst() {
        Alert first = service.routeRecalculated(
                MISSION, "EJEMPLO-AMB-01", 1
        );

        Alert second = service.congestionOnRoute(
                MISSION, "EJEMPLO-AMB-01", "Calle 16"
        );

        service.availabilityChanged(
                unit(VehicleOperationalStatus.AVAILABLE),
                VehicleOperationalStatus.BUSY
        );

        List<Alert> mine = service.list(
                support.sessionOf(COORDINATOR), false
        );

        assertEquals(3, mine.size());
        assertEquals(AlertType.AVAILABILITY_CHANGE, mine.get(0).getType());
        assertEquals(second.getId(), mine.get(1).getId());
        assertEquals(first.getId(), mine.get(2).getId());

        assertEquals(
                1,
                service.list(
                        support.sessionOf(ADMINISTRATOR), false
                ).size()
        );

        assertTrue(
                service.list(support.sessionOf(OPERATOR), false).isEmpty()
        );
    }

    @Test
    void markingAsReadWorksOnlyOnYourOwnAlerts() {
        long id = service.routeRecalculated(
                MISSION, "EJEMPLO-AMB-01", 1
        ).getId();

        assertThrows(
                NotFoundException.class,
                () -> service.markAsRead(
                        id, support.sessionOf(ADMINISTRATOR)
                )
        );

        assertThrows(
                NotFoundException.class,
                () -> service.markAsRead(
                        999, support.sessionOf(COORDINATOR)
                )
        );

        assertEquals(
                1,
                service.list(support.sessionOf(COORDINATOR), true).size()
        );

        Alert read = service.markAsRead(
                id, support.sessionOf(COORDINATOR)
        );

        assertTrue(read.isRead());

        assertTrue(
                service.list(support.sessionOf(COORDINATOR), true).isEmpty()
        );

        assertEquals(
                1,
                service.list(support.sessionOf(COORDINATOR), false).size()
        );

        assertTrue(
                service.markAsRead(
                        id, support.sessionOf(COORDINATOR)
                ).isRead()
        );
    }
}
