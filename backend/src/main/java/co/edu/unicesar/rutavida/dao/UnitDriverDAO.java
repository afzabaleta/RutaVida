package co.edu.unicesar.rutavida.dao;

import java.util.OptionalLong;

/**
 * The one-to-one link between an authenticated field driver and an emergency unit.
 * It deliberately lives apart from VEHICULO_EMERGENCIA: registering a vehicle never grants
 * somebody permission to transmit its location.
 */
public interface UnitDriverDAO {

    /** Vehicle currently assigned to the user, if the user is an active field driver. */
    OptionalLong findVehicleIdByUser(long userId);

    /** User currently assigned to a vehicle, useful for administration and diagnostics. */
    OptionalLong findUserIdByVehicle(long vehicleId);

    /** Replaces the assignment of both the user and the vehicle in one transaction. */
    void assign(long userId, long vehicleId);

    /** Removes the driver assignment when a unit is retired or reassigned. */
    void removeByVehicle(long vehicleId);
}
