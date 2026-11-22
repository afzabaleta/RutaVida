package co.edu.unicesar.rutavida.dto;

/**
 * The indicators of the main panel (CU-16, docs/api.md 11.1). Every number is read from
 * Oracle at the moment of the query; nothing here is example data.
 *
 * @param activeEmergencies      emergencies that are not CERRADA (vw_emergencias_activas)
 * @param availableUnits         units DISPONIBLE (vw_unidades_disponibles)
 * @param busyUnits              units OCUPADO
 * @param operationalHospitals   active hospitals (vw_hospitales_operativos)
 * @param activeIncidents        road incidents ACTIVO, the ones that change the routes
 * @param congestedSegments      segments whose last traffic reading of the last minutes is
 *                               CONGESTIONADO
 * @param closedToday            emergencies CERRADA today
 * @param averageResponseMinutes average minutes from the report to the arrival of the first
 *                               unit (fn_tiempo_respuesta) of the emergencies attended today;
 *                               null when no unit has arrived today, never 0
 */
public record DashboardSummary(
        int activeEmergencies,
        int availableUnits,
        int busyUnits,
        int operationalHospitals,
        int activeIncidents,
        int congestedSegments,
        int closedToday,
        Double averageResponseMinutes
) {
}