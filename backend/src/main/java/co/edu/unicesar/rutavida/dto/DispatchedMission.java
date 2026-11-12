package co.edu.unicesar.rutavida.dto;

import co.edu.unicesar.rutavida.model.EmergencyAssignment;

import java.util.List;
import java.util.Objects;

/**
 * The answer of POST /api/dispatches (docs/api.md 6.3): the new assignment and its routes
 * ready for the map, the recommended one first.
 */
public record DispatchedMission(EmergencyAssignment assignment, List<MissionRoute> routes) {

    public DispatchedMission {
        Objects.requireNonNull(assignment, "assignment");
        routes = List.copyOf(routes);
    }
}