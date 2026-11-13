
package co.edu.unicesar.rutavida.dto;

import co.edu.unicesar.rutavida.model.GeoPoint;

/**
 * Data of a fire station or ambulance base to register or edit (CU-04). The status is not
 * here: it changes only with FleetService.changeStationStatus.
 *
 * @param neighborhood optional
 * @param phone        optional
 * @param location     where the units without a known position start their routes
 */
public record StationRequest(String name, String address, String neighborhood, String phone,
                             GeoPoint location) {
}
