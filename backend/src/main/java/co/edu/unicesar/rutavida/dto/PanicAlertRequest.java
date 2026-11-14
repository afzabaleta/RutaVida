package co.edu.unicesar.rutavida.dto;

import co.edu.unicesar.rutavida.model.GeoPoint;

/**
 * What the panic button page sends when someone presses it (CU-22, docs/api.md 16.1).
 *
 * @param deviceId        the id the page generates the first time and keeps in the browser;
 *                        the service only saves its SHA-256 hash (RN-13)
 * @param location        where the phone is
 * @param emergencyTypeId what the person chose; null if they did not choose
 * @param description     short text written by the person; null if empty (200 characters at most)
 */
public record PanicAlertRequest(
        String deviceId,
        GeoPoint location,
        Long emergencyTypeId,
        String description
) {
}