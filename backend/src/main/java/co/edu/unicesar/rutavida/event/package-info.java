/**
 * <b>Responsibility.</b> In-process publish/subscribe used for real-time updates: services publish domain events (traffic changed, route recalculated, emergency status changed) and the view or the route monitor subscribe to them.
 *
 * <p><b>Allowed dependencies.</b> JDK only; event classes may reference {@code model}.
 *
 * <p><b>Principles.</b> Observer pattern; GRASP Low Coupling and Indirection: publishers do not know who listens.
 *
 * <p><b>Owner.</b> Andrés
 */
package co.edu.unicesar.rutavida.event;
