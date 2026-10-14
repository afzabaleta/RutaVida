package co.edu.unicesar.rutavida.event;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Synchronous, thread-safe event bus inside the same JVM.
 *
 * <p>Listeners run in the thread that publishes. A Swing view must move its UI
 * updates to the Event Dispatch Thread ({@code SwingUtilities.invokeLater}); a web
 * view would forward the event to the browser (e.g. Server-Sent Events).
 *
 * <p>An exception in one listener is logged and does not stop the other listeners.
 */
public final class InMemoryEventBus implements EventBus {

    private static final Logger LOG = Logger.getLogger(InMemoryEventBus.class.getName());

    private final List<Registration<?>> registrations = new CopyOnWriteArrayList<>();

    @Override
    public <E extends DomainEvent> Subscription subscribe(Class<E> type, Consumer<? super E> listener) {
        Registration<E> registration = new Registration<>(
                Objects.requireNonNull(type, "type"), Objects.requireNonNull(listener, "listener"));
        registrations.add(registration);
        return () -> registrations.remove(registration);
    }

    @Override
    public void publish(DomainEvent event) {
        Objects.requireNonNull(event, "event");
        for (Registration<?> registration : registrations) {
            try {
                registration.deliver(event);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Listener failed for " + event.getClass().getSimpleName(), e);
            }
        }
    }

    /** Number of active subscriptions (useful in tests). */
    public int subscriberCount() {
        return registrations.size();
    }

    private record Registration<E extends DomainEvent>(Class<E> type, Consumer<? super E> listener) {
        void deliver(DomainEvent event) {
            if (type.isInstance(event)) {
                listener.accept(type.cast(event));
            }
        }
    }
}
