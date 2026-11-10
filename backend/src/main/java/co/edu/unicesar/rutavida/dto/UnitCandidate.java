package co.edu.unicesar.rutavida.dto;

import co.edu.unicesar.rutavida.model.GeoPoint;

/**
 * A unit that can attend an emergency, with the time and distance of its fastest route
 * now (CU-09, docs/api.md 6.1). The list goes from the fastest to the slowest.
 *
 * @param vehicleId   unit (VEHICULO_EMERGENCIA)
 * @param unitCode    code shown to the coordinator (EJEMPLO-AMB-01)
 * @param vehicleType AMBULANCIA or BOMBERO
 * @param stationName its station or base
 * @param location    where the route starts: the last position, or the station if it has none
 * @param etaMinutes  estimated minutes of the recommended route with the current traffic
 * @param distanceKm  length of that route
 */
public record UnitCandidate(long vehicleId, String unitCode, String vehicleType, String stationName,
                            GeoPoint location, double etaMinutes, double distanceKm) {
}