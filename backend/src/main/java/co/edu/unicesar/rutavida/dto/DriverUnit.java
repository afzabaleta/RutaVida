package co.edu.unicesar.rutavida.dto;

import co.edu.unicesar.rutavida.model.GeoPoint;

import java.time.LocalDateTime;

/** The only unit a field driver may publish from their phone. */
public record DriverUnit(long vehicleId, String unitCode, String plate, GeoPoint location,
                         LocalDateTime updatedAt) {
}
