package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.dao.ReportDAO;
import co.edu.unicesar.rutavida.dto.OperationsReport;
import co.edu.unicesar.rutavida.dto.ReportCount;
import co.edu.unicesar.rutavida.exception.DataAccessException;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * ReportDAO on Oracle: one SELECT for the totals and one per group (type, severity, vehicle
 * type and day). Every query is parameterized; the period is always the half-open interval
 * [start, end) over emergencia.fecha_hora_reporte.
 *
 * <p>The response time of an emergency is the one of its first unit that arrived:
 * MIN(fn_tiempo_respuesta) over its assignments, as the dashboard does (A14).
 */
public class JdbcReportDAO extends AbstractJdbcDAO implements ReportDAO {

    /** First arrival of each emergency of the period that a unit reached. Two parameters. */
    private static final String FIRST_ARRIVAL = """
            primera AS (
              SELECT a.id_emergencia, MIN(fn_tiempo_respuesta(a.id_asignacion)) AS minutos
                FROM asignacion_emergencia a
                JOIN emergencia e ON e.id_emergencia = a.id_emergencia
               WHERE a.hora_llegada IS NOT NULL
                 AND e.fecha_hora_reporte >= ? AND e.fecha_hora_reporte < ?
               GROUP BY a.id_emergencia)""";

    /**
     * A recommended route counts as a recalculation when the same mission (emergency, unit
     * and destination hospital) already had an earlier recommended route.
     */
    private static final String TOTALS = """
            WITH periodo AS (
              SELECT id_emergencia, estado
                FROM emergencia
               WHERE fecha_hora_reporte >= ? AND fecha_hora_reporte < ?),
            """ + FIRST_ARRIVAL + """

            SELECT
              (SELECT COUNT(*) FROM periodo) AS reportadas,
              (SELECT COUNT(*) FROM periodo WHERE estado = 'CERRADA') AS cerradas,
              (SELECT COUNT(*) FROM primera) AS atendidas,
              (SELECT AVG(minutos) FROM primera) AS promedio,
              (SELECT MIN(minutos) FROM primera) AS minimo,
              (SELECT MAX(minutos) FROM primera) AS maximo,
              (SELECT COUNT(*) FROM asignacion_emergencia a
                 JOIN periodo p ON p.id_emergencia = a.id_emergencia) AS despachos,
              (SELECT COUNT(*) FROM ruta
                WHERE fecha_generacion >= ? AND fecha_generacion < ?) AS rutas,
              (SELECT COUNT(*) FROM ruta r
                WHERE r.es_recomendada = 'S'
                  AND r.fecha_generacion >= ? AND r.fecha_generacion < ?
                  AND EXISTS (SELECT 1 FROM ruta anterior
                               WHERE anterior.id_emergencia = r.id_emergencia
                                 AND anterior.id_vehiculo = r.id_vehiculo
                                 AND NVL(anterior.id_hospital_destino, 0) = NVL(r.id_hospital_destino, 0)
                                 AND anterior.es_recomendada = 'S'
                                 AND anterior.id_ruta < r.id_ruta)) AS recalculos,
              (SELECT COUNT(*) FROM incidente_vial
                WHERE fecha_inicio >= ? AND fecha_inicio < ?) AS incidentes,
              (SELECT COUNT(*) FROM reporte_panico
                WHERE fecha_hora >= ? AND fecha_hora < ?) AS panico,
              (SELECT COUNT(*) FROM reporte_panico
                WHERE estado = 'VALIDADO' AND fecha_hora >= ? AND fecha_hora < ?) AS panico_validado
            FROM dual""";

    /** Emergencies of the period grouped by {@code label}; four parameters. */
    private static String emergenciesBy(String label, String order) {
        return "WITH " + FIRST_ARRIVAL + """

                SELECT %s AS etiqueta, COUNT(*) AS total, AVG(p.minutos) AS promedio
                  FROM emergencia e
                  JOIN tipo_emergencia t ON t.id_tipo_emergencia = e.id_tipo_emergencia
                  LEFT JOIN primera p ON p.id_emergencia = e.id_emergencia
                 WHERE e.fecha_hora_reporte >= ? AND e.fecha_hora_reporte < ?
                 GROUP BY %s
                 ORDER BY %s""".formatted(label, label, order);
    }

    private static final String BY_TYPE = emergenciesBy("t.nombre", "total DESC, etiqueta");
    private static final String BY_SEVERITY = emergenciesBy("e.severidad",
            "DECODE(e.severidad, 'CRITICA', 1, 'ALTA', 2, 'MEDIA', 3, 4)");
    private static final String BY_DAY = emergenciesBy("TO_CHAR(e.fecha_hora_reporte, 'YYYY-MM-DD')", "etiqueta");

    /** Each assignment counts; the average is the one of the units of that type that arrived. */
    private static final String BY_VEHICLE_TYPE = """
            SELECT tv.nombre AS etiqueta, COUNT(*) AS total,
                   AVG(fn_tiempo_respuesta(a.id_asignacion)) AS promedio
              FROM asignacion_emergencia a
              JOIN emergencia e ON e.id_emergencia = a.id_emergencia
              JOIN vehiculo_emergencia v ON v.id_vehiculo = a.id_vehiculo
              JOIN tipo_vehiculo tv ON tv.id_tipo_vehiculo = v.id_tipo_vehiculo
             WHERE e.fecha_hora_reporte >= ? AND e.fecha_hora_reporte < ?
             GROUP BY tv.nombre
             ORDER BY total DESC, etiqueta""";

    private static final RowMapper<ReportCount> COUNT = row -> new ReportCount(
            row.getString("etiqueta"), row.getInt("total"), nullableDouble(row, "promedio"));

    public JdbcReportDAO(JdbcTransactionManager connections) {
        super(connections);
    }

    @Override
    public OperationsReport operations(LocalDate firstDay, LocalDate lastDay) {
        Objects.requireNonNull(firstDay, "firstDay");
        Objects.requireNonNull(lastDay, "lastDay");
        LocalDateTime start = firstDay.atStartOfDay();
        LocalDateTime end = lastDay.plusDays(1).atStartOfDay();

        List<ReportCount> byType = queryList(BY_TYPE, COUNT, start, end, start, end);
        List<ReportCount> bySeverity = queryList(BY_SEVERITY, COUNT, start, end, start, end);
        List<ReportCount> byVehicleType = queryList(BY_VEHICLE_TYPE, COUNT, start, end);
        List<ReportCount> byDay = queryList(BY_DAY, COUNT, start, end, start, end);

        return queryOne(TOTALS, row -> new OperationsReport(firstDay, lastDay,
                        row.getInt("reportadas"),
                        row.getInt("cerradas"),
                        row.getInt("atendidas"),
                        nullableDouble(row, "promedio"),
                        nullableDouble(row, "minimo"),
                        nullableDouble(row, "maximo"),
                        row.getInt("despachos"),
                        row.getInt("rutas"),
                        row.getInt("recalculos"),
                        row.getInt("incidentes"),
                        row.getInt("panico"),
                        row.getInt("panico_validado"),
                        byType, bySeverity, byVehicleType, byDay),
                start, end, start, end, start, end, start, end, start, end, start, end, start, end)
                .orElseThrow(() -> new DataAccessException("The report query returned no row", null));
    }

    private static Double nullableDouble(ResultSet row, String column) throws SQLException {
        BigDecimal value = row.getBigDecimal(column);
        return value == null ? null : value.doubleValue();
    }
}
