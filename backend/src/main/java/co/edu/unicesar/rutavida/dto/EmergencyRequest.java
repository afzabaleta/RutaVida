
package co.edu.unicesar.rutavida.dto;

import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.enums.Severity;

/**
 * What the operator sends to register an emergency (CU-06).
 * Only data: EmergencyService checks it.
 *
 * @param emergencyTypeId ID of the emergency type
 * @param addressReference Address or reference written by the operator
 * @param location Point marked on the map
 * @param severity Emergency severity
 * @param description Optional details
 */
public record EmergencyRequest(
        long emergencyTypeId,
        String addressReference,
        GeoPoint location,
        Severity severity,
        String description
) {
}
