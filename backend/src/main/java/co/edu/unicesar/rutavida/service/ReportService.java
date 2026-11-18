package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.ReportDAO;
import co.edu.unicesar.rutavida.dto.OperationsReport;
import co.edu.unicesar.rutavida.dto.ReportCount;
import co.edu.unicesar.rutavida.exception.ValidationException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The operations report of a period (RF-11): what happened between two days, while the
 * dashboard (DashboardService, CU-16) shows what is happening now. This service decides the
 * period and rounds; the DAO counts in Oracle. The permission (REPORTES / CONSULTAR) is
 * checked by the web server before calling here, as for the dashboard.
 */
public class ReportService {

    /** Without dates, the last 7 days, today included. */
    static final int DEFAULT_DAYS = 7;
    /** At most one year at a time, so one report never reads the whole history. */
    static final int MAX_DAYS = 366;

    private final ReportDAO reportDao;
    private final Clock clock;

    public ReportService(ReportDAO reportDao, Clock clock) {
        this.reportDao = Objects.requireNonNull(reportDao, "reportDao");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * The report from 00:00 of {@code from} to 24:00 of {@code to}, in the server clock
     * (Colombia). A null {@code to} is today; a null {@code from} is 6 days before {@code to}.
     * Every average keeps one decimal or stays null (the web shows "Sin datos", never 0), and
     * {@code byDay} has every day of the period, with 0 on the days without emergencies.
     */
    public OperationsReport operations(LocalDate from, LocalDate to) {
        LocalDate last = to != null ? to : LocalDate.now(clock);
        LocalDate first = from != null ? from : last.minusDays(DEFAULT_DAYS - 1);
        if (first.isAfter(last)) {
            throw new ValidationException("La fecha inicial no puede ser posterior a la final.");
        }
        if (ChronoUnit.DAYS.between(first, last) + 1 > MAX_DAYS) {
            throw new ValidationException("Consulte como máximo " + MAX_DAYS + " días a la vez.");
        }

        OperationsReport counted = reportDao.operations(first, last);
        return new OperationsReport(first, last,
                counted.reportedEmergencies(),
                counted.closedEmergencies(),
                counted.attendedEmergencies(),
                oneDecimal(counted.averageResponseMinutes()),
                oneDecimal(counted.fastestResponseMinutes()),
                oneDecimal(counted.slowestResponseMinutes()),
                counted.dispatches(),
                counted.routesGenerated(),
                counted.recalculations(),
                counted.roadIncidents(),
                counted.panicReports(),
                counted.validatedPanicReports(),
                rounded(counted.byEmergencyType()),
                rounded(counted.bySeverity()),
                rounded(counted.byVehicleType()),
                everyDay(first, last, counted.byDay()));
    }

    /** One row per day of the period, in order; the days Oracle did not return have 0. */
    private static List<ReportCount> everyDay(LocalDate first, LocalDate last, List<ReportCount> counted) {
        Map<String, ReportCount> byLabel = counted.stream()
                .collect(Collectors.toMap(ReportCount::label, Function.identity(), (a, b) -> a));
        List<ReportCount> days = new ArrayList<>();
        for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
            ReportCount found = byLabel.get(day.toString());
            days.add(found == null ? new ReportCount(day.toString(), 0, null) : round(found));
        }
        return days;
    }

    private static List<ReportCount> rounded(List<ReportCount> counts) {
        return counts.stream().map(ReportService::round).toList();
    }

    private static ReportCount round(ReportCount count) {
        return new ReportCount(count.label(), count.count(), oneDecimal(count.averageResponseMinutes()));
    }

    private static Double oneDecimal(Double minutes) {
        return minutes == null ? null : Math.round(minutes * 10) / 10.0;
    }
}
