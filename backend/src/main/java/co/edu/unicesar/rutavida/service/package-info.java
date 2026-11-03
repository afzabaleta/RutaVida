/**
 * <b>Responsibility.</b> Business rules and use-case logic. A service validates input, applies the rules, coordinates DAOs inside a transaction and calls other services directly when something must react (no event bus). It does not know how data is stored or shown.
 *
 * <p><b>Allowed dependencies.</b> May depend on {@code model}, {@code dto}, {@code dao} (interfaces only), {@code graph}, {@code traffic}, {@code exception}, {@code util}. Must NOT import java.sql, dao.jdbc, controller or view.
 *
 * <p><b>Principles.</b> GRASP High Cohesion (one service per business area), Creator; SOLID Single Responsibility and Dependency Inversion.
 *
 * <p><b>Owner.</b> Kendrick (dispatch, routes, tracking) · Sherly (users, hospitals, alerts, audit)
 */
package co.edu.unicesar.rutavida.service;
