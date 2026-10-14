/**
 * <b>Responsibility.</b> Real-time simulation: the tick scheduler, simulated vehicle movement along an active route and reproducible demo scenarios (e.g. "accident on Calle X"). Every value it produces is marked as SIMULACION, never presented as real data.
 *
 * <p><b>Allowed dependencies.</b> May depend on {@code service}, {@code graph}, {@code traffic}, {@code event}, {@code model}.
 *
 * <p><b>Principles.</b> GRASP Indirection (the simulator talks to services, not to the database).
 *
 * <p><b>Owner.</b> Kendrick
 */
package co.edu.unicesar.rutavida.simulation;
