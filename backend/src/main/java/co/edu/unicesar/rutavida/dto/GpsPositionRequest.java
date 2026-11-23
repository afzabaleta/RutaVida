package co.edu.unicesar.rutavida.dto;

import co.edu.unicesar.rutavida.model.GeoPoint;

/** One browser Geolocation API reading. Speed is km/h and heading is degrees clockwise from north. */
public record GpsPositionRequest(GeoPoint location, Double speedKmh, Double headingDegrees) {
}
