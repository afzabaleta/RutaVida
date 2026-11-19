package co.edu.unicesar.rutavida.dto;

import co.edu.unicesar.rutavida.model.GeoPoint;

/**
 * A hospital that can receive the patient of a mission (CU-11, RN-10, docs/api.md 6.5).
 *
 * @param level            "I" to "IV", like the column HOSPITAL.nivel
 * @param etaMinutes       time of the best route from the unit, with the current traffic
 * @param serviceConfirmed true if the hospital confirmed URGENCIAS as available ('S');
 *                         false if the service exists but is not confirmed ('N')
 * @param scoreMinutes     what orders the list: the ETA plus the penalty of an unconfirmed service
 */
public record HospitalOption(long hospitalId, String name, String level, GeoPoint location,
                             double etaMinutes, double distanceKm, boolean serviceConfirmed,
                             double scoreMinutes) {
}