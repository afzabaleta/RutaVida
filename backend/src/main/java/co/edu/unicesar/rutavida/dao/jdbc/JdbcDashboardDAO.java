package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.dao.DashboardDAO;
import co.edu.unicesar.rutavida.dto.DashboardSummary;
import co.edu.unicesar.rutavida.exception.DataAccessException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * DashboardDAO on Oracle: one SELECT with a subquery per indicator, so the panel costs a
 * single round trip to the database. It uses the views and the function fn_tiempo_respuesta
 * of 04_plsql.sql (DT-05).
 */
public class JdbcDashboardDAO extends AbstractJdbcDAO implements DashboardDAO {

    private static final String SUMMARY = """
            SELECT
              (SELECT COUNT(*) FROM vw_emergencias_activas) AS emergencias_activas,
              (SELECT COUNT(*) FROM vw_unidades_disponibles) AS unidades_disponibles,
              (SELECT COUNT(*) FROM vehiculo_emergencia
                WHERE estado_operativo = 'OCUPADO') AS unidades_ocupadas,
              (SELECT COUNT(*) FROM vw_hospitales_operativos) AS hospitales_operativos,
              (SELECT COUNT(*) FROM incidente_vial WHERE estado = 'ACTIVO') AS incidentes_activos,
              (SELECT COUNT(*)
                 FROM (SELECT t.nivel,
                              ROW_NUMBER() OVER (PARTITION BY t.id_tramo
                                                 ORDER BY t.fecha_hora DESC, t.id_trafico DESC) AS orden
                         FROM trafico t
                        WHERE t.fecha_hora >= ?) ultima
                WHERE ultima.orden = 1 AND ultima.nivel = 'CONGESTIONADO') AS tramos_congestionados,
              (SELECT COUNT(*) FROM emergencia
                WHERE estado = 'CERRADA'
                  AND fecha_hora_cierre >= ? AND fecha_hora_cierre < ?) AS cerradas_hoy,
              (SELECT AVG(primera.minutos)
                 FROM (SELECT MIN(fn_tiempo_respuesta(a.id_asignacion)) AS minutos
                         FROM asignacion_emergencia a
                        WHERE a.hora_llegada >= ? AND a.hora_llegada < ?
                        GROUP BY a.id_emergencia) primera) AS promedio_respuesta
            FROM dual""";

    private static final RowMapper<DashboardSummary> MAPPER = row -> {
        BigDecimal average = row.getBigDecimal("promedio_respuesta");
        return new DashboardSummary(
                row.getInt("emergencias_activas"),
                row.getInt("unidades_disponibles"),
                row.getInt("unidades_ocupadas"),
                row.getInt("hospitales_operativos"),
                row.getInt("incidentes_activos"),
                row.getInt("tramos_congestionados"),
                row.getInt("cerradas_hoy"),
                average == null ? null : average.doubleValue());
    };

    public JdbcDashboardDAO(JdbcTransactionManager connections) {
        super(connections);
    }

    @Override
    public DashboardSummary summary(LocalDateTime trafficSince, LocalDateTime dayStart, LocalDateTime dayEnd) {
        Objects.requireNonNull(trafficSince, "trafficSince");
        Objects.requireNonNull(dayStart, "dayStart");
        Objects.requireNonNull(dayEnd, "dayEnd");
        return queryOne(SUMMARY, MAPPER, trafficSince, dayStart, dayEnd, dayStart, dayEnd)
                .orElseThrow(() -> new DataAccessException("The dashboard query returned no row", null));
    }
}