/**
 * <b>Responsibility.</b> Persistence contracts: one small interface per aggregate ({@code EmergencyDAO}, {@code RoadSegmentDAO}...) plus {@link co.edu.unicesar.rutavida.dao.TransactionManager}. Interfaces here never expose JDBC types, so services stay independent of the database technology.
 *
 * <p><b>Allowed dependencies.</b> May depend on {@code model}, {@code model.enums}, {@code exception}, and on {@code dto} only for read-only views that no entity holds ({@code DashboardDAO} returns {@code DashboardSummary}, {@code RoadMapDAO} returns {@code SegmentView}). Must NOT import java.sql.
 *
 *  <p><b>Principles.</b> SOLID Interface Segregation (no generic CRUD with methods a table does not need, e.g. TRAFICO and AUDITORIA are append-only) and Dependency Inversion; GRASP Protected Variations.
 *
 * <p><b>Owner.</b> Andrés
 */
package co.edu.unicesar.rutavida.dao;
