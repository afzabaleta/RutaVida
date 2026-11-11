
package co.edu.unicesar.rutavida.dto;

import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.enums.HospitalLevel;

/**
 * Data of a hospital to register or edit (CU-05).
 * The status is not here: it changes only with HospitalService.changeStatus.
 *
 * @param emergencyCapacity beds of the emergency room; null if unknown
 */
public record HospitalRequest(
        String name,
        HospitalLevel level,
        String address,
        String phone,
        GeoPoint location,
        Integer emergencyCapacity
) {
}
