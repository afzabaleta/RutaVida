
package co.edu.unicesar.rutavida.dto;

/**
 * Data of a unit to register or edit (CU-04). The operational status and the position are
 * not here: the status changes only with FleetService.changeVehicleStatus or with a mission,
 * and the position comes from the tracking.
 *
 * @param unitCode code shown to the coordinator, unique (EJEMPLO-AMB-01)
 * @param plate    license plate, unique; null if the unit has none registered
 */
public record VehicleRequest(long vehicleTypeId, long stationId, String unitCode, String plate) {
}
