package co.edu.unicesar.rutavida.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Everything the live map needs, in one answer (docs/api.md 8.1). The web asks for it every
 * 5 seconds (DT-07) and draws it as it comes: it does not have to keep anything.
 *
 * @param unreadAlerts alerts of the user not read yet, for the counter of the bell
 */
public record TrackingSnapshot(
        LocalDateTime serverTime,
        SimulationState simulation,
        List<TrackedUnit> units,
        List<TrackedMission> missions,
        int unreadAlerts
) {

    public TrackingSnapshot {
        units = units == null ? List.of() : List.copyOf(units);
        missions = missions == null ? List.of() : List.copyOf(missions);
    }
}
