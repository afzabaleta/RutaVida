package co.edu.unicesar.rutavida.dto;

import co.edu.unicesar.rutavida.model.EmergencyAssignment;
import co.edu.unicesar.rutavida.model.Route;

import java.util.List;

/**
 * What a dispatch creates (CU-09, docs/api.md 6.3): the assignment and the saved routes,
 * the recommended one ACTIVA first and the alternatives PROPUESTA.
 */
public record DispatchResult(EmergencyAssignment assignment, List<Route> routes) {

    public DispatchResult {
        routes = List.copyOf(routes);
    }
}