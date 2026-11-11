package co.edu.unicesar.rutavida.controller;

import co.edu.unicesar.rutavida.dto.ErrorKind;
import co.edu.unicesar.rutavida.dto.IncidentView;
import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.exception.DataAccessException;
import co.edu.unicesar.rutavida.model.RoadIncident;
import co.edu.unicesar.rutavida.model.enums.IncidentOrigin;
import co.edu.unicesar.rutavida.model.enums.IncidentStatus;
import co.edu.unicesar.rutavida.model.enums.IncidentType;
import co.edu.unicesar.rutavida.model.enums.Severity;
import co.edu.unicesar.rutavida.service.RoadMapService;
import co.edu.unicesar.rutavida.util.Messages;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadMapControllerTest {

    private static final RoadIncident INCIDENT = new RoadIncident(4, 413, IncidentType.ACCIDENT, Severity.HIGH,
            false, null, LocalDateTime.of(2026, 11, 9, 9, 0), null, IncidentStatus.PENDING, IncidentOrigin.OPERATOR);

    @Test
    void anErrorOfIncidentControllerPassesThroughUnchanged() {
        RoadMapController controller = new RoadMapController(new RoadMapService(ids -> List.of()));

        OperationResult<IncidentView> denied = controller.incidentOnMap(
                OperationResult.error(ErrorKind.PERMISSION, "No tiene permiso para realizar esta operación."));

        assertFalse(denied.success());
        assertEquals(ErrorKind.PERMISSION, denied.error(), "403 in the API, like before");
        assertEquals("No tiene permiso para realizar esta operación.", denied.message());
        assertEquals(ErrorKind.AUTHENTICATION, controller.incidentsOnMap(
                OperationResult.error(ErrorKind.AUTHENTICATION, Messages.SESSION_REQUIRED)).error());
    }

    @Test
    void aSuccessGetsItsSegment() {
        RoadMapController controller = new RoadMapController(new RoadMapService(ids -> List.of()));

        OperationResult<List<IncidentView>> listed = controller.incidentsOnMap(OperationResult.ok(List.of(INCIDENT)));

        assertTrue(listed.success());
        assertEquals(4, listed.value().get(0).id());
        assertNull(listed.value().get(0).segment(), "the fake knows no segment");
    }

    @Test
    void oracleDownWhileReadingTheSegmentsIsAMessage() {
        RoadMapController controller = new RoadMapController(new RoadMapService(ids -> {
            throw new DataAccessException("ORA-12541: TNS:no listener", null);
        }));

        OperationResult<IncidentView> result = controller.incidentOnMap(OperationResult.ok(INCIDENT));

        assertEquals(ErrorKind.UNAVAILABLE, result.error());
        assertEquals(Messages.DATABASE_DOWN, result.message());
    }
}