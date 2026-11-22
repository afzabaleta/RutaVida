package co.edu.unicesar.rutavida.dao;

import co.edu.unicesar.rutavida.model.VehicleType;

import java.util.List;

/** Catalog of vehicle types (table TIPO_VEHICULO). Read only: it comes from 02_datos_iniciales.sql. */
public interface VehicleTypeDAO {

    /** Every type, by name (AMBULANCIA, BOMBERO). */
    List<VehicleType> findAll();
}