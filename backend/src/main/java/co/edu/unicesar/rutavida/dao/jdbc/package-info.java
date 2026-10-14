/**
 * <b>Responsibility.</b> Oracle/JDBC implementations of the {@code dao} interfaces ({@code JdbcEmergencyDAO}...), the transaction manager and the row mappers. All SQL lives here and only here, always with PreparedStatement parameters. Spanish table/column names are translated to the English model here.
 *
 * <p><b>Allowed dependencies.</b> May depend on {@code dao}, {@code model}, {@code exception}, {@code util} and java.sql.
 *
 * <p><b>Principles.</b> GRASP Pure Fabrication, Indirection; SOLID Single Responsibility (a DAO only translates between objects and rows).
 *
 * <p><b>Owner.</b> Andrés
 */
package co.edu.unicesar.rutavida.dao.jdbc;
