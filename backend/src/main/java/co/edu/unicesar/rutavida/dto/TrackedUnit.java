package co.edu.unicesar.rutavida.dto;

import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.enums.PositionSource;
import co.edu.unicesar.rutavida.model.enums.VehicleOperationalStatus;

import java.time.LocalDateTime;

/**
 * Where one unit is now, for the live map (docs/api.md 8.1).
 *
 * @param vehicleType name of its type (AMBULANCIA, BOMBERO, POLICIA): the web picks the icon with it
 * @param location    last known position; null if the unit never reported one
 * @param source      SIMULACION while there is no real GPS; null without position
 */
public record TrackedUnit(
        long vehicleId,
        String unitCode,
        String vehicleType,
        VehicleOperationalStatus operationalStatus,
        GeoPoint location,
        LocalDateTime updatedAt,
        PositionSource source
) {
}
