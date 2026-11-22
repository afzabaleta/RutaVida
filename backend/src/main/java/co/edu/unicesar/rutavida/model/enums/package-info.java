/**
 * <b>Responsibility.</b> Status and catalog enums. Each constant has an English name and the Spanish code stored in Oracle ({@link co.edu.unicesar.rutavida.model.enums.CodedEnum#code()}). The codes must match the CHECK constraints of database/01_esquema.sql; {@code EnumSqlContractTest} verifies it.
 *
 * <p><b>Allowed dependencies.</b> JDK only.
 *
 * <p><b>Principles.</b> Enums only name values. The valid status transitions live in the services ({@code EmergencyService}, {@code DispatchService}, {@code IncidentService}).
 *
 * <p><b>Owner.</b> Sherly (catalog codes: Andrés)
 */
package co.edu.unicesar.rutavida.model.enums;
