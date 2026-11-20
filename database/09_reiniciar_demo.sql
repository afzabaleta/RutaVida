-- =====================================================================
-- RutaVida · 09_reiniciar_demo.sql
-- Deja la base lista para empezar una demostracion desde cero:
-- borra los datos de OPERACION (emergencias, despachos, rutas, posiciones,
-- trafico, incidentes viales, alertas, notificaciones y reportes de panico)
-- y devuelve las unidades EJEMPLO- a su estacion.
--
-- NO toca: roles, permisos, usuarios, sesiones de ciudadanos, auditoria,
-- red vial, semaforos, hospitales, estaciones, tipos ni unidades.
-- La AUDITORIA se conserva: es el registro de lo que se hizo.
--
-- Antes: detener WebServer (la simulacion guarda estado en memoria).
-- Despues: volver a iniciar WebServer y, si se quiere, 98_verificar.sql.
-- Ejecutar como RUTAVIDA, con F5 (Ejecutar script) en SQL Developer
-- o con @09_reiniciar_demo.sql en SQL*Plus. Pide escribir REINICIAR.
-- =====================================================================
SET SERVEROUTPUT ON
SET VERIFY OFF
ACCEPT rv_confirmar CHAR PROMPT 'Esto BORRA las emergencias y despachos de la base. Escriba REINICIAR para continuar: '

DECLARE
    v_unidades NUMBER;

    PROCEDURE borrar(p_tabla VARCHAR2) IS
    BEGIN
        EXECUTE IMMEDIATE 'DELETE FROM ' || p_tabla;
        DBMS_OUTPUT.PUT_LINE(RPAD(p_tabla, 24) || SQL%ROWCOUNT || ' filas borradas');
    END;
BEGIN
    IF '&&rv_confirmar' <> 'REINICIAR' THEN
        RAISE_APPLICATION_ERROR(-20901, 'Cancelado: no se escribio REINICIAR. No se borro nada.');
    END IF;
    IF USER <> 'RUTAVIDA' THEN
        RAISE_APPLICATION_ERROR(-20902, 'Ejecute este script como RUTAVIDA, no como ' || USER || '.');
    END IF;

    -- Hijas primero; RUTA_TRAMO se borra con RUTA (ON DELETE CASCADE)
    borrar('REPORTE_PANICO');
    borrar('ALERTA');
    borrar('NOTIFICACION');
    borrar('UBICACION_VEHICULO');
    borrar('RUTA');
    borrar('ASIGNACION_EMERGENCIA');
    borrar('EMERGENCIA');
    borrar('TRAFICO');
    borrar('INCIDENTE_VIAL');

    -- Las unidades EJEMPLO- vuelven a su estacion; una OCUPADA queda DISPONIBLE.
    -- Las patrullas (EJEMPLO-POL-) no: siguen en la calle donde iban, repartidas
    -- por la ciudad (11_patrullas_ejemplo.sql las pone en su esquina de partida).
    -- La hora en hora de Colombia, como la escribe el servidor Java (en la nube
    -- SYSTIMESTAMP sale en UTC y la simulacion descartaria las posiciones nuevas).
    -- MANTENIMIENTO y FUERA_SERVICIO se respetan.
    UPDATE vehiculo_emergencia v
       SET (latitud, longitud) = (SELECT e.latitud, e.longitud
                                    FROM estacion_emergencia e
                                   WHERE e.id_estacion = v.id_estacion),
           estado_operativo = CASE WHEN v.estado_operativo = 'OCUPADO'
                                   THEN 'DISPONIBLE' ELSE v.estado_operativo END,
           fecha_actualizacion = CAST(SYSTIMESTAMP AT TIME ZONE 'America/Bogota' AS TIMESTAMP)
     WHERE v.codigo_unidad LIKE 'EJEMPLO-%'
       AND v.codigo_unidad NOT LIKE 'EJEMPLO-POL-%';
    v_unidades := SQL%ROWCOUNT;
    DBMS_OUTPUT.PUT_LINE(RPAD('VEHICULO_EMERGENCIA', 24) || v_unidades || ' unidades EJEMPLO en su estacion');

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('Listo: la base quedo lista para la demostracion.');
EXCEPTION
    WHEN OTHERS THEN
        ROLLBACK;
        RAISE;
END;
/
UNDEFINE rv_confirmar
