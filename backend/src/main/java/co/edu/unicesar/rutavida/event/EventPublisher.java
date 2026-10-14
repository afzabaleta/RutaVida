package co.edu.unicesar.rutavida.event;

/**
 * What a service needs to announce an event. Services depend on this narrow
 * interface, not on the whole bus (SOLID Interface Segregation).
 */
@FunctionalInterface
public interface EventPublisher {

    void publish(DomainEvent event);
}
