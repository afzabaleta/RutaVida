/**
 * <b>Responsibility.</b> Status and catalog enums. Each constant has an English name and the Spanish code stored in Oracle ({@link co.edu.unicesar.rutavida.model.enums.CodedEnum#code()}). The codes must match the CHECK constraints of database/01_esquema.sql; {@code EnumSqlContractTest} verifies it.
 *
 * <p><b>Allowed dependencies.</b> JDK only.
 *
 * <p><b>Principles.</b> GRASP Information Expert: each status enum knows its own valid transitions.
 *
 * <p><b>Owner.</b> Sherly (catalog codes: Andrés)
 */
package co.edu.unicesar.rutavida.model.enums;
