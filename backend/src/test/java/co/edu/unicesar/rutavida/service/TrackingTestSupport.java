package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.controller.SessionContext;
import co.edu.unicesar.rutavida.controller.TrackingController;

/**
 * LiveMapService on the small city of DispatchTestSupport (3 x 3 streets, EXAMPLE units),
 * without Oracle. {@code running} is what the simulation engine (K9) would say.
 */
public final class TrackingTestSupport {

    public final DispatchTestSupport city = new DispatchTestSupport();
    public final InMemoryAlertDAO alerts = new InMemoryAlertDAO();
    public boolean running;

    public LiveMapService service() {
        return new LiveMapService(city.vehicles, () -> DispatchTestSupport.VEHICLE_TYPES, city.assignments,
                city.routes, alerts, () -> running, EmergencyTestSupport.CLOCK);
    }

    /** The controller of the live map with this support, for the tests of the API. */
    public TrackingController controller(AuthService authService, SessionContext session) {
        return new TrackingController(service(), authService, session);
    }
}
