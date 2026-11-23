package co.edu.unicesar.rutavida.dto;

/** Remaining portion of an active route from a unit's current reported position. */
public record RouteProgress(double distanceKm, double etaMinutes) {
}
