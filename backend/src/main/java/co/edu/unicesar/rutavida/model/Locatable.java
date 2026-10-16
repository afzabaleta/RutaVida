
package co.edu.unicesar.rutavida.model;

/** Anything that has a position on the map (GRASP Polymorphism). */
public interface Locatable {

    /** Current position, or null if it is not known yet. */
    GeoPoint getLocation();
}
