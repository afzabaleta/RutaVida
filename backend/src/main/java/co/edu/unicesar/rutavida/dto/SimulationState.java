package co.edu.unicesar.rutavida.dto;

import co.edu.unicesar.rutavida.model.enums.PositionSource;

/**
 * Whether the simulation is moving the units (K9) and where the positions come from.
 * The positions are always SIMULACION in this version: there is no real GPS yet.
 */
public record SimulationState(boolean running, PositionSource source) {
}
