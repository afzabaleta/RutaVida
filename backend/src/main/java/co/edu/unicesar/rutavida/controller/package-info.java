
/**
 * <b>Responsibility.</b> Use-case controllers (GRASP Controller): receive the system operations triggered by the user (login, register emergency, dispatch unit, close emergency), delegate to services and return model/DTO objects. No SQL, no business rules, no Swing or HTML code.
 *
 * <p><b>Allowed dependencies.</b> May depend on {@code service}, {@code model}, {@code dto}, {@code exception}, {@code event}, {@code util}. Must NOT import dao, java.sql or view.
 *
 * <p><b>Principles.</b> GRASP Controller, Low Coupling.
 *
 * <p><b>Owner.</b> Sherly
 */
package co.edu.unicesar.rutavida.controller;
