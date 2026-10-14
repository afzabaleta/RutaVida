package co.edu.unicesar.rutavida.event;

import java.util.function.Consumer;

/**
 * Publish/subscribe channel for real-time updates (Observer pattern).
 *
 * <p>Typical flow: the traffic simulator updates the graph → TrafficService publishes
 * {@code TrafficChanged} → the route monitor recalculates active routes and publishes
 * {@code RouteRecalculated} → the view refreshes the map. No component knows who
 * listens to it (GRASP Low Coupling, Indirection).
 */
public interface EventBus extends EventPublisher {

    /**
     * Registers {@code listener} for events of {@code type} (and its subtypes).
     *
     * @return a handle to stop listening
     */
    <E extends DomainEvent> Subscription subscribe(Class<E> type, Consumer<? super E> listener);

    /** Handle returned by {@link #subscribe}; call {@link #cancel()} to stop receiving events. */
    @FunctionalInterface
    interface Subscription {
        void cancel();
    }
}
