package co.edu.unicesar.rutavida.dto;

/**
 * One row of a group of the operations report (docs/api.md 11.4): how many there were of
 * something and how long the response took for them.
 *
 * @param label                  what is counted: an emergency type, a severity, a vehicle type
 *                               or a day (2026-11-14)
 * @param count                  how many emergencies (or, for a vehicle type, dispatches)
 * @param averageResponseMinutes average minutes from the report to the arrival; null when none
 *                               of them has an arrival, never 0
 */
public record ReportCount(String label, int count, Double averageResponseMinutes) {
}
