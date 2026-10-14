/**
 * <b>Responsibility.</b> Domain entities: one class per database table, with English names (see docs/convenciones-base-datos.md, table V.4 of the consolidated document). Entities hold data and the rules that only need their own data (GRASP Information Expert), e.g. an emergency checking whether it may change to a given status.
 *
 * <p><b>Allowed dependencies.</b> Depends only on {@code model.enums} and the JDK (java.time, java.util). Must NOT import java.sql, dao, service, controller or view.
 *
 * <p><b>Principles.</b> GRASP Information Expert, High Cohesion.
 *
 * <p><b>Owner.</b> Sherly
 */
package co.edu.unicesar.rutavida.model;
