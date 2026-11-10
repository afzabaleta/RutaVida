package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.DashboardDAO;
import co.edu.unicesar.rutavida.dto.DashboardSummary;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** DashboardService with a fake DAO that records what it was asked. Does not need Oracle. */
class DashboardServiceTest {

    /** 09/11/2026 10:30 in Valledupar (15:30 UTC). */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-09T15:30:00Z"), ZoneId.of("America/Bogota"));

    private final List<LocalDateTime> asked = new ArrayList<>();

    private DashboardService serviceAnswering(DashboardSummary counted) {
        DashboardDAO dao = (trafficSince, dayStart, dayEnd) -> {
            asked.addAll(List.of(trafficSince, dayStart, dayEnd));
            return counted;
        };
        return new DashboardService(dao, CLOCK);
    }

    @Test
    void todayIsTheCalendarDayOfTheServerAndTrafficTheLastMinutes() {
        serviceAnswering(new DashboardSummary(0, 0, 0, 0, 0, 0, 0, null)).getSummary();

        assertEquals(LocalDateTime.of(2026, 11, 9, 10, 15), asked.get(0), "15 minutes of traffic");
        assertEquals(LocalDateTime.of(2026, 11, 9, 0, 0), asked.get(1), "from 00:00 in Colombia");
        assertEquals(LocalDateTime.of(2026, 11, 10, 0, 0), asked.get(2), "to 24:00, excluded");
    }

    @Test
    void theCountsPassAndTheAverageKeepsOneDecimal() {
        DashboardSummary summary = serviceAnswering(new DashboardSummary(3, 3, 1, 2, 1, 4, 2, 9.4166)).getSummary();

        assertEquals(new DashboardSummary(3, 3, 1, 2, 1, 4, 2, 9.4), summary);
    }

    @Test
    void withoutArrivalsTheAverageStaysNull() {
        assertNull(serviceAnswering(new DashboardSummary(1, 2, 0, 2, 0, 0, 0, null))
                .getSummary().averageResponseMinutes(), "the web shows Sin datos, never 0");
    }
}