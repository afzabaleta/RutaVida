package co.edu.unicesar.rutavida.event;

import java.time.Instant;

/**
 * Something relevant that already happened in the system, e.g. "traffic changed on
 * segment 120" or "route of emergency 7 was recalculated". Implement it with a record:
 *
 * <pre>{@code
 * public record EmergencyStatusChanged(long emergencyId, EmergencyStatus status,
 *                                      Instant occurredAt) implements DomainEvent {}
 * }</pre>
 */
public interface DomainEvent {

    Instant occurredAt();
}
