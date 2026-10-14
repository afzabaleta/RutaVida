package co.edu.unicesar.rutavida.event;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryEventBusTest {

    private record TrafficChanged(int segmentId, Instant occurredAt) implements DomainEvent {
    }

    private record RouteRecalculated(long emergencyId, Instant occurredAt) implements DomainEvent {
    }

    @Test
    void listenerReceivesOnlyItsEventType() {
        InMemoryEventBus bus = new InMemoryEventBus();
        List<TrafficChanged> received = new ArrayList<>();
        bus.subscribe(TrafficChanged.class, received::add);

        bus.publish(new TrafficChanged(120, Instant.now()));
        bus.publish(new RouteRecalculated(7, Instant.now()));

        assertEquals(1, received.size());
        assertEquals(120, received.get(0).segmentId());
    }

    @Test
    void listenerOfDomainEventReceivesEverything() {
        InMemoryEventBus bus = new InMemoryEventBus();
        List<DomainEvent> received = new ArrayList<>();
        bus.subscribe(DomainEvent.class, received::add);

        bus.publish(new TrafficChanged(1, Instant.now()));
        bus.publish(new RouteRecalculated(2, Instant.now()));

        assertEquals(2, received.size());
    }

    @Test
    void cancelledSubscriptionStopsReceiving() {
        InMemoryEventBus bus = new InMemoryEventBus();
        List<DomainEvent> received = new ArrayList<>();
        EventBus.Subscription subscription = bus.subscribe(TrafficChanged.class, received::add);

        subscription.cancel();
        bus.publish(new TrafficChanged(1, Instant.now()));

        assertTrue(received.isEmpty());
        assertEquals(0, bus.subscriberCount());
    }

    @Test
    void failingListenerDoesNotBlockTheOthers() {
        InMemoryEventBus bus = new InMemoryEventBus();
        List<DomainEvent> received = new ArrayList<>();
        bus.subscribe(TrafficChanged.class, event -> {
            throw new IllegalStateException("broken listener");
        });
        bus.subscribe(TrafficChanged.class, received::add);

        bus.publish(new TrafficChanged(1, Instant.now()));

        assertEquals(1, received.size());
    }
}
