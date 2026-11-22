/**
 * <b>Responsibility.</b> Traffic data sources behind the {@code TrafficProvider} interface: the simulator now (Phase 1) and an external source later (Phase 2). The simulator changes congestion and traffic lights; it never closes road segments (closures come from incidents).
 *
 * <p><b>Allowed dependencies.</b> May depend on {@code graph}, {@code model.enums} and the JDK.
 *
 * <p><b>Principles.</b> GRASP Protected Variations; SOLID Liskov Substitution: any provider must be usable without the rest of the system noticing.
 *
 * <p><b>Owner.</b> Kendrick
 */
package co.edu.unicesar.rutavida.traffic;
