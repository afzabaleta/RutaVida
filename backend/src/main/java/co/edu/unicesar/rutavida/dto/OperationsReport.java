package co.edu.unicesar.rutavida.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * The operations report of a period (RF-11, docs/api.md 11.4). Every number is counted in
 * Oracle over the emergencies reported between {@code from} and {@code to}, both days
 * included; nothing here is example data.
 *
 * @param from                   first day of the period
 * @param to                     last day of the period (included)
 * @param reportedEmergencies    emergencies reported in the period
 * @param closedEmergencies      of them, the ones that are CERRADA now
 * @param attendedEmergencies    of them, the ones where at least one unit arrived
 * @param averageResponseMinutes average of the first arrival of each attended emergency
 *                               (fn_tiempo_respuesta); null when none was attended
 * @param fastestResponseMinutes the fastest first arrival; null when none was attended
 * @param slowestResponseMinutes the slowest first arrival; null when none was attended
 * @param dispatches             units assigned to those emergencies, cancelled ones included
 * @param routesGenerated        routes saved in the period (recommended and alternatives)
 * @param recalculations         recommended routes that replaced an earlier one of the same
 *                               mission (CU-17)
 * @param roadIncidents          road incidents reported in the period
 * @param panicReports           panic button presses in the period (CU-22)
 * @param validatedPanicReports  of them, the ones that became an emergency
 * @param byEmergencyType        emergencies by type, the most frequent first
 * @param bySeverity             emergencies by severity, CRITICA first
 * @param byVehicleType          dispatches by vehicle type, the most used first
 * @param byDay                  emergencies of each day of the period, every day present
 */
public record OperationsReport(
        LocalDate from,
        LocalDate to,
        int reportedEmergencies,
        int closedEmergencies,
        int attendedEmergencies,
        Double averageResponseMinutes,
        Double fastestResponseMinutes,
        Double slowestResponseMinutes,
        int dispatches,
        int routesGenerated,
        int recalculations,
        int roadIncidents,
        int panicReports,
        int validatedPanicReports,
        List<ReportCount> byEmergencyType,
        List<ReportCount> bySeverity,
        List<ReportCount> byVehicleType,
        List<ReportCount> byDay
) {

    public OperationsReport {
        byEmergencyType = List.copyOf(byEmergencyType);
        bySeverity = List.copyOf(bySeverity);
        byVehicleType = List.copyOf(byVehicleType);
        byDay = List.copyOf(byDay);
    }
}
