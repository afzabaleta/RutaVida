package co.edu.unicesar.rutavida.dao;

import co.edu.unicesar.rutavida.dto.DashboardSummary;

import java.time.LocalDateTime;

/**
 * The indicators of the panel in one query (views and fn_tiempo_respuesta of 04_plsql.sql,
 * decision DT-05). Read only. It returns the DTO directly because no entity holds these
 * numbers.
 */
public interface DashboardDAO {

    /**
     * @param trafficSince only traffic readings from this moment count as current
     * @param dayStart     first moment of the day of the report (included)
     * @param dayEnd       first moment of the next day (excluded)
     */
    DashboardSummary summary(LocalDateTime trafficSince, LocalDateTime dayStart, LocalDateTime dayEnd);
}