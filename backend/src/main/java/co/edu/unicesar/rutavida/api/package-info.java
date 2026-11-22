/**
 * <b>Responsibility.</b> Web adapter (decision DT-06): the HTTP server (Javalin) that serves
 * the pages of {@code frontend/} and the REST API of docs/api.md. It reads the request,
 * checks the session and the permission through {@code WebSessionController}, calls one
 * controller and writes its {@code OperationResult} as JSON. No business rules and no SQL.
 *
 * <p><b>Allowed dependencies.</b> May depend on {@code controller}, {@code dto}, {@code model},
 * {@code exception}, {@code util}, Javalin and Jackson. Must NOT import dao, service,
 * graph, traffic, simulation, osm, app or java.sql ({@code ArchitectureTest}).
 *
 * <p><b>Principles.</b> GRASP Indirection and Low Coupling: the web only knows URLs and JSON,
 * the controllers only know Java objects.
 *
 * <p><b>Owner.</b> Andrés
 */
package co.edu.unicesar.rutavida.api;