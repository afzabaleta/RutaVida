package co.edu.unicesar.rutavida.graph;

/**
 * How an emergency vehicle is affected by traffic. The relief values are assumptions to
 * calibrate (consolidated document, section 8.2), not measured data.
 *
 * @param type             vehicle type name (AMBULANCIA, BOMBERO)
 * @param congestionRelief 0..1: share of the congestion the vehicle avoids
 * @param signalRelief     0..1: share of the traffic light wait the vehicle avoids
 * @param maxKmh           top speed of the vehicle
 */
public record VehicleProfile(String type, double congestionRelief, double signalRelief, double maxKmh) {

    public static final VehicleProfile AMBULANCE = new VehicleProfile("AMBULANCIA", 0.35, 0.60, 90);
    public static final VehicleProfile FIRE_TRUCK = new VehicleProfile("BOMBERO", 0.25, 0.50, 80);

    public VehicleProfile {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type is required");
        }
        if (!(congestionRelief >= 0 && congestionRelief < 1) || !(signalRelief >= 0 && signalRelief < 1)) {
            throw new IllegalArgumentException("relief values must be in [0, 1)");
        }
        if (!(maxKmh > 0)) {
            throw new IllegalArgumentException("maxKmh must be > 0");
        }
    }
}