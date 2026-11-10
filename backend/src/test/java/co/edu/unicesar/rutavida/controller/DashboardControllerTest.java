package co.edu.unicesar.rutavida.controller;

import co.edu.unicesar.rutavida.dto.DashboardSummary;
import co.edu.unicesar.rutavida.dto.ErrorKind;
import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.exception.DataAccessException;
import co.edu.unicesar.rutavida.service.DashboardService;
import co.edu.unicesar.rutavida.util.Messages;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardControllerTest {

    private static final DashboardSummary COUNTED = new DashboardSummary(3, 3, 1, 2, 1, 4, 2, 9.4);

    @Test
    void theSummaryComesFromTheService() {
        DashboardController controller = new DashboardController(
                new DashboardService((since, start, end) -> COUNTED, Clock.systemDefaultZone()));

        OperationResult<DashboardSummary> result = controller.summary();

        assertTrue(result.success());
        assertEquals(COUNTED, result.value());
    }

    @Test
    void oracleDownIsAMessageNotAnException() {
        DashboardController controller = new DashboardController(new DashboardService((since, start, end) -> {
            throw new DataAccessException("ORA-12541: TNS:no listener", null);
        }, Clock.systemDefaultZone()));

        OperationResult<DashboardSummary> result = controller.summary();

        assertFalse(result.success());
        assertEquals(ErrorKind.UNAVAILABLE, result.error());
        assertEquals(Messages.DATABASE_DOWN, result.message());
    }
}