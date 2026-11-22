package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.model.VehicleLocation;

import java.util.List;

/**
 * Where the positions of the units come from. Today it is the simulator
 * (simulation.VehicleMovementSimulator, source SIMULACION); in phase 2 it can be a real GPS
 * without changing TrackingService. The interface lives here, in the layer that uses it
 * (SOLID: dependency inversion).
 */
public interface VehiclePositionSource {

    /** The new position of every unit that moved since the last call (may be empty). */
    List<VehicleLocation> poll();
}