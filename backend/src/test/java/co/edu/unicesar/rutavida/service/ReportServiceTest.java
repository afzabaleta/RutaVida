package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.ReportDAO;
import co.edu.unicesar.rutavida.dto.OperationsReport;
import co.edu.unicesar.rutavida.dto.ReportCount;
import co.edu.unicesar.rutavida.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ReportService with a fake DAO that records the period it was asked. Does not need Oracle. */
class ReportServiceTest {

    /** 14/11/2026 22:30 in Valledupar, already 15/11 in UTC (03:30). */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-15T03:30:00Z"), ZoneId.of("America/Bogota"));

    private final List<LocalDate> asked = new ArrayList<>();

    /** EXAMPLE counts: the averages keep all their decimals, as Oracle returns them. */
    private ReportService serviceCounting(List<ReportCount> byDay, Double average) {
        ReportDAO dao = (first, last) -> {
            asked.add(first);
            asked.add(last);
            return new OperationsReport(first, last, 3, 2, 2, average, 6.04, 12.96, 4, 9, 1, 2, 5, 1,
                    List.of(new ReportCount("INCENDIO", 2, 12.96), new ReportCount("EMERGENCIA_MEDICA", 1, null)),
                    List.of(new ReportCount("CRITICA", 3, average)),
                    List.of(new ReportCount("AMBULANCIA", 4, 7.55)),
                    byDay);
        };
        return new ReportService(dao, CLOCK);
    }

    @Test
    void withoutDatesItIsTheLast7DaysOfTheColombianCalendar() {
        OperationsReport report = serviceCounting(List.of(), null).operations(null, null);

        assertEquals(List.of(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 14)), asked,
                "today is 14/11 in Colombia even if it is 15/11 in UTC");
        assertEquals(LocalDate.of(2026, 11, 8), report.from());
        assertEquals(7, report.byDay().size());
        assertTrue(report.byDay().stream().allMatch(day -> day.count() == 0 && day.averageResponseMinutes() == null),
                "days without emergencies count 0 and have no average");
    }

    @Test
    void everyDayIsPresentInOrderAndTheAveragesKeepOneDecimal() {
        OperationsReport report = serviceCounting(List.of(new ReportCount("2026-11-03", 3, 9.4166)), 9.4166)
                .operations(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 4));

        assertEquals(List.of(new ReportCount("2026-11-02", 0, null), new ReportCount("2026-11-03", 3, 9.4),
                new ReportCount("2026-11-04", 0, null)), report.byDay());
        assertEquals(9.4, report.averageResponseMinutes());
        assertEquals(6.0, report.fastestResponseMinutes());
        assertEquals(13.0, report.slowestResponseMinutes());
        assertEquals(13.0, report.byEmergencyType().get(0).averageResponseMinutes());
        assertNull(report.byEmergencyType().get(1).averageResponseMinutes(), "null, never 0");
        assertEquals(7.6, report.byVehicleType().get(0).averageResponseMinutes());
        assertEquals(4, report.dispatches());
        assertEquals(1, report.recalculations());
    }

    @Test
    void aSingleDayAndAWholeYearAreAllowedButNotMore() {
        ReportService service = serviceCounting(List.of(), null);

        assertEquals(1, service.operations(LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 14)).byDay().size());
        assertEquals(366, service.operations(LocalDate.of(2025, 11, 15), LocalDate.of(2026, 11, 15)).byDay().size());
        assertThrows(ValidationException.class,
                () -> service.operations(LocalDate.of(2025, 11, 14), LocalDate.of(2026, 11, 15)));
        assertThrows(ValidationException.class,
                () -> service.operations(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 1)));
    }

    @Test
    void onlyFromCountsUntilTodayAndOnlyToTakesTheWeekBefore() {
        ReportService service = serviceCounting(List.of(), null);

        assertEquals(LocalDate.of(2026, 11, 14), service.operations(LocalDate.of(2026, 11, 10), null).to());
        assertEquals(LocalDate.of(2026, 10, 25), service.operations(null, LocalDate.of(2026, 10, 31)).from());
    }
}
