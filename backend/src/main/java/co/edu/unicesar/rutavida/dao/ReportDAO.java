package co.edu.unicesar.rutavida.dao;

import co.edu.unicesar.rutavida.dto.OperationsReport;

import java.time.LocalDate;

/**
 * The operations report counted in Oracle (fn_tiempo_respuesta of 04_plsql.sql, DT-05).
 * Read only. Like DashboardDAO, it returns the DTO directly because no entity holds these
 * numbers.
 */
public interface ReportDAO {

    /**
     * Counts the emergencies reported from 00:00 of {@code firstDay} to 24:00 of
     * {@code lastDay}. {@code byDay} only has the days with emergencies, and the averages
     * keep all their decimals: ReportService completes and rounds them.
     */
    OperationsReport operations(LocalDate firstDay, LocalDate lastDay);
}
