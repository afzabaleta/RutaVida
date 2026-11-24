package co.edu.unicesar.rutavida.dto;

import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.enums.PositionSource;

import java.time.LocalDateTime;

/** Position confirmed by RutaVida and sent to connected live-map viewers. */
public record GpsPosition(long vehicleId, GeoPoint location, LocalDateTime updatedAt,
                          Double speedKmh, Double headingDegrees, PositionSource source,
                          boolean routeRecalculated) {
}
