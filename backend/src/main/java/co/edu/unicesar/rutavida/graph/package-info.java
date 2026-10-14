/**
 * <b>Responsibility.</b> Routing engine on the in-memory road graph: RoadGraph, Node, Edge, EdgeState, CostFunction strategies, A* and Dijkstra and alternative routes (see Part IV of the consolidated document). Pure Java, testable without a database.
 *
 * <p><b>Allowed dependencies.</b> May depend on {@code model.enums} and the JDK. Must NOT import java.sql, dao, service, controller or view.
 *
 * <p><b>Principles.</b> GRASP Polymorphism and Protected Variations (CostFunction); SOLID Open/Closed (a new cost strategy does not modify the router).
 *
 * <p><b>Owner.</b> Kendrick
 */
package co.edu.unicesar.rutavida.graph;
