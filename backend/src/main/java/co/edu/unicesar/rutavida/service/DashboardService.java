package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.DashboardDAO;
import co.edu.unicesar.rutavida.dto.DashboardSummary;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * The indicators of the main panel (CU-16). This service decides what "today" and "current
 * traffic" mean; the DAO counts in Oracle. The permission (REPORTES / CONSULTAR) is checked
 * by the web server before calling here.
 */
public class DashboardService {

    /** A traffic reading older than this is not current any more. */
    static final Duration TRAFFIC_WINDOW = Duration.ofMinutes(15);

    private final DashboardDAO dashboardDao;
    private final Clock clock;

    public DashboardService(DashboardDAO dashboardDao, Clock clock) {
        this.dashboardDao = Objects.requireNonNull(dashboardDao, "dashboardDao");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * The indicators now. "Today" is the calendar day of the server clock (Colombia), from
     * 00:00 to 24:00. The average response time keeps one decimal, or stays null when there
     * is no arrival today: the web shows "Sin datos", never a 0.
     */
    public DashboardSummary getSummary() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime dayStart = now.toLocalDate().atStartOfDay();
        DashboardSummary counted = dashboardDao.summary(now.minus(TRAFFIC_WINDOW), dayStart, dayStart.plusDays(1));

        return new DashboardSummary(
                counted.activeEmergencies(),
                counted.availableUnits(),
                counted.busyUnits(),
                counted.operationalHospitals(),
                counted.activeIncidents(),
                counted.congestedSegments(),
                counted.closedToday(),
                oneDecimal(counted.averageResponseMinutes()));
    }

    private static Double oneDecimal(Double minutes) {
        return minutes == null ? null : Math.round(minutes * 10) / 10.0;
    }
}