
package co.edu.unicesar.rutavida.dto;

import co.edu.unicesar.rutavida.model.AuditLog;
import co.edu.unicesar.rutavida.model.Emergency;
import co.edu.unicesar.rutavida.model.EmergencyAssignment;
import co.edu.unicesar.rutavida.model.Route;

import java.util.List;

/**
 * Everything that happened to one emergency (CU-15, consult history):
 * the emergency, its assignments, its calculated routes and its audit trail
 * (registration and status changes).
 */
public record EmergencyHistory(
        Emergency emergency,
        List<EmergencyAssignment> assignments,
        List<Route> routes,
        List<AuditLog> auditTrail
) {

    public EmergencyHistory {
        assignments = List.copyOf(assignments);
        routes = List.copyOf(routes);
        auditTrail = List.copyOf(auditTrail);
    }
}
