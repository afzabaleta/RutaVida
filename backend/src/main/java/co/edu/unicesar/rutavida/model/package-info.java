/**
 * <b>Responsibility.</b> Domain entities: one class per database table, with English names (see docs/convenciones-base-datos.md, table V.4 of the consolidated document). Entities are plain data holders: fields, constructor, getters, {@code setId} and setters only for the data that changes.
 *
 * <p><b>Allowed dependencies.</b> Depends only on {@code model.enums} and the JDK (java.time, java.util). Entities only hold data: they do not validate, so they must NOT import exception or util (validation and business rules live in {@code service}). Must NOT import java.sql, dao, service, controller or view.
 *
 * <p><b>Principles.</b> High Cohesion, single responsibility (data only).
 *
 * <p><b>Owner.</b> Sherly
 */
package co.edu.unicesar.rutavida.model;
