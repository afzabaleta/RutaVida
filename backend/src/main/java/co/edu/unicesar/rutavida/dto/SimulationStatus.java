package co.edu.unicesar.rutavida.dto;

import co.edu.unicesar.rutavida.model.enums.TrafficSource;

import java.time.LocalDateTime;

/**
 * The state of the simulation (docs/api.md 10.1).
 *
 * @param running     true while the cycle runs
 * @param startedAt   when it was started; null while stopped
 * @param tickSeconds seconds between two cycles
 * @param source      SIMULACION: the positions and the traffic are simulated, never real
 */
public record SimulationStatus(boolean running, LocalDateTime startedAt, long tickSeconds, TrafficSource source) {
}
