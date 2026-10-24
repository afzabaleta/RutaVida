package co.edu.unicesar.rutavida.dao;

import co.edu.unicesar.rutavida.model.TrafficLight;

import java.util.List;

/**
 * Reading of the traffic lights (table SEMAFORO). RutaVida only reads or simulates their
 * state: it never controls a real traffic light.
 */
public interface TrafficLightDAO {

    /** Every traffic light, active and inactive, ordered by id. */
    List<TrafficLight> findAll();
}