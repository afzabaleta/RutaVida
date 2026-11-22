package co.edu.unicesar.rutavida.dao;

import co.edu.unicesar.rutavida.model.Route;
import co.edu.unicesar.rutavida.model.enums.RouteStatus;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Calculated routes (table RUTA) with their ordered segments (table RUTA_TRAMO). */
public interface RouteDAO {

    /**
     * Saves a new route (id 0) and all its segments in one transaction (it joins the
     * caller's transaction if there is one), assigns the generated id and returns it.
     */
    long insert(Route route);

    /** The route with its segments already loaded. */
    Optional<Route> findById(long id);

    /** Every route of one emergency, oldest first: the main one, the alternatives and the recalculated ones. */
    List<Route> findByEmergency(long emergencyId);

    /** Routes in status ACTIVA (the ones the units are driving now). */
    List<Route> findActive();

    /** Active routes that drive through at least one of those road segments (used by RouteMonitor). */
    List<Route> findActiveUsingSegments(Set<Long> segmentIds);

    /**
     * Changes the status of one route. Oracle allows only one ACTIVA route per emergency
     * and unit, so the old one must leave ACTIVA first. Throws NotFoundException if the
     * route does not exist.
     */
    void updateStatus(long id, RouteStatus status);
}