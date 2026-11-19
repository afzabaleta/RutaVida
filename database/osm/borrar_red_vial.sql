-- =====================================================================
-- RutaVida · database/osm/borrar_red_vial.sql
-- Borra la red vial (NODO_VIAL, TRAMO_VIAL, SEMAFORO) y lo que depende de
-- ella (RUTA con RUTA_TRAMO, TRAFICO, INCIDENTE_VIAL), para cargar una red
-- vial nueva con 05_red_vial_valledupar.sql (DT-14).
--
-- Orden: detener WebServer, 09_reiniciar_demo.sql, ESTE script,
-- 05_red_vial_valledupar.sql, 98_verificar.sql y volver a iniciar WebServer.
-- Ejecutar como RUTAVIDA. Pide escribir BORRAR RED.
-- No toca usuarios, auditoria, hospitales, estaciones ni unidades.
-- =====================================================================
SET SERVEROUTPUT ON
SET VERIFY OFF
ACCEPT rv_confirmar CHAR PROMPT 'Esto BORRA la red vial y las rutas guardadas. Escriba BORRAR RED para continuar: '

DECLARE
    v_abiertas NUMBER;

    PROCEDURE borrar(p_tabla VARCHAR2) IS
    BEGIN
        EXECUTE IMMEDIATE 'DELETE FROM ' || p_tabla;
        DBMS_OUTPUT.PUT_LINE(RPAD(p_tabla, 20) || SQL%ROWCOUNT || ' filas borradas');
    END;
BEGIN
    IF UPPER(REPLACE(TRIM('&&rv_confirmar'), '_', ' ')) <> 'BORRAR RED' THEN
        RAISE_APPLICATION_ERROR(-20911, 'Cancelado: no se escribio BORRAR RED. No se borro nada.');
    END IF;
    IF USER <> 'RUTAVIDA' THEN
        RAISE_APPLICATION_ERROR(-20912, 'Ejecute este script como RUTAVIDA, no como ' || USER || '.');
    END IF;

    SELECT COUNT(*) INTO v_abiertas
      FROM asignacion_emergencia
     WHERE estado IN ('ASIGNADA', 'EN_RUTA', 'EN_SITIO', 'TRASLADANDO');
    IF v_abiertas > 0 THEN
        RAISE_APPLICATION_ERROR(-20913, 'Hay ' || v_abiertas
            || ' misiones abiertas. Ejecute primero 09_reiniciar_demo.sql. No se borro nada.');
    END IF;

    borrar('RUTA');
    borrar('TRAFICO');
    borrar('INCIDENTE_VIAL');
    borrar('SEMAFORO');
    borrar('TRAMO_VIAL');
    borrar('NODO_VIAL');

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('Listo: ahora ejecute 05_red_vial_valledupar.sql.');
EXCEPTION
    WHEN OTHERS THEN
        ROLLBACK;
        RAISE;
END;
/
UNDEFINE rv_confirmar
