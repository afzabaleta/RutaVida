/**
 * <b>Responsibility.</b> Exception hierarchy shared by all layers. All are unchecked and extend {@link co.edu.unicesar.rutavida.exception.RutaVidaException}. Controllers translate them into messages for the user.
 *
 * <p><b>Allowed dependencies.</b> JDK only.
 *
 * <p><b>Principles.</b> GRASP Protected Variations: SQLException never leaves dao.jdbc.
 *
 * <p><b>Owner.</b> Andrés
 */
package co.edu.unicesar.rutavida.exception;
