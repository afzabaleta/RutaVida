-- ============================================================
-- RutaVida - Hora de Colombia en la auditoria de cambios de estado
-- Archivo: 13_hora_auditoria.sql
-- Requiere: 04_plsql.sql.
-- ============================================================
-- Los 3 triggers de auditoria de 04_plsql.sql (emergencias, despacho e
-- incidentes) no escribian fecha_hora: la ponia el DEFAULT SYSTIMESTAMP de la
-- tabla. En Oracle Cloud el servidor esta en UTC, asi que esos registros
-- quedaban 5 horas adelante de los que escribe Java (hora de Colombia).
-- En Oracle XE local no se notaba porque el PC esta en hora de Colombia.
--
-- Este script:
--   1. Corrige los registros CAMBIO_ESTADO que ya quedaron en UTC. Solo si el
--      servidor NO esta en hora de Colombia (en la nube) y los triggers todavia
--      son los viejos; asi no se corrige dos veces ni se tocan los registros de
--      una base instalada con el 04_plsql.sql nuevo. Deja constancia en la
--      auditoria: SISTEMA / CORREGIR_HORA.
--   2. Vuelve a crear los 3 triggers con la hora de Colombia explicita, igual
--      que 06, 09, 10, 11 y 12: CAST(SYSTIMESTAMP AT TIME ZONE ...).
-- Se puede ejecutar varias veces. Detenga WebServer antes de ejecutarlo.
-- ============================================================

SET SERVEROUTPUT ON

-- 1. Registros que ya quedaron en UTC (solo en la nube, una sola vez)
DECLARE
    -- Una sola lectura del reloj: dos llamadas a SYSTIMESTAMP pueden diferir en microsegundos
    v_ahora     TIMESTAMP WITH TIME ZONE := SYSTIMESTAMP;
    v_desfase   INTERVAL DAY TO SECOND;
    v_arreglado NUMBER;
    v_filas     NUMBER := 0;
BEGIN
    v_desfase := CAST(v_ahora AS TIMESTAMP)
               - CAST(v_ahora AT TIME ZONE 'America/Bogota' AS TIMESTAMP);

    -- Los triggers nuevos ya escriben la hora de Colombia: lo que hay es correcto
    SELECT COUNT(*) INTO v_arreglado
      FROM user_source
     WHERE type = 'TRIGGER' AND name = 'TRG_EMERGENCIA_ESTADO'
       AND UPPER(text) LIKE '%AMERICA/BOGOTA%';

    IF v_desfase = INTERVAL '0' SECOND THEN
        DBMS_OUTPUT.PUT_LINE('El servidor ya esta en hora de Colombia: no hay registros que corregir.');
    ELSIF v_arreglado > 0 THEN
        DBMS_OUTPUT.PUT_LINE('Los triggers ya escriben la hora de Colombia: no se corrige nada otra vez.');
    ELSE
        UPDATE auditoria
           SET fecha_hora = fecha_hora - v_desfase
         WHERE operacion = 'CAMBIO_ESTADO';
        v_filas := SQL%ROWCOUNT;

        INSERT INTO auditoria (id_usuario, modulo, operacion, registro_id, fecha_hora, resultado, detalle)
        VALUES (NULL, 'SISTEMA', 'CORREGIR_HORA', NULL,
                CAST(SYSTIMESTAMP AT TIME ZONE 'America/Bogota' AS TIMESTAMP), 'EXITO',
                v_filas || ' registros CAMBIO_ESTADO pasados de la hora del servidor a la hora de Colombia ('
                    || EXTRACT(HOUR FROM v_desfase) || ' h menos), script 13_hora_auditoria.sql');

        DBMS_OUTPUT.PUT_LINE('Corregidos ' || v_filas || ' registros CAMBIO_ESTADO ('
                             || EXTRACT(HOUR FROM v_desfase) || ' h menos).');
    END IF;
END;
/

COMMIT;

-- 2. Los triggers, con la hora de Colombia explicita
CREATE OR REPLACE TRIGGER trg_emergencia_estado
AFTER UPDATE OF estado ON emergencia
    FOR EACH ROW
    WHEN (NEW.estado <> OLD.estado)
BEGIN
INSERT INTO auditoria (id_usuario, modulo, operacion, registro_id, fecha_hora, resultado, detalle)
VALUES (fn_usuario_sesion, 'EMERGENCIAS', 'CAMBIO_ESTADO', :NEW.id_emergencia,
        CAST(SYSTIMESTAMP AT TIME ZONE 'America/Bogota' AS TIMESTAMP), 'EXITO',
        :OLD.estado || ' -> ' || :NEW.estado);
END;
/

CREATE OR REPLACE TRIGGER trg_asignacion_estado
AFTER UPDATE OF estado ON asignacion_emergencia
    FOR EACH ROW
    WHEN (NEW.estado <> OLD.estado)
BEGIN
INSERT INTO auditoria (id_usuario, modulo, operacion, registro_id, fecha_hora, resultado, detalle)
VALUES (fn_usuario_sesion, 'DESPACHO', 'CAMBIO_ESTADO', :NEW.id_asignacion,
        CAST(SYSTIMESTAMP AT TIME ZONE 'America/Bogota' AS TIMESTAMP), 'EXITO',
        :OLD.estado || ' -> ' || :NEW.estado);
END;
/

CREATE OR REPLACE TRIGGER trg_incidente_estado
AFTER UPDATE OF estado ON incidente_vial
    FOR EACH ROW
    WHEN (NEW.estado <> OLD.estado)
BEGIN
INSERT INTO auditoria (id_usuario, modulo, operacion, registro_id, fecha_hora, resultado, detalle)
VALUES (fn_usuario_sesion, 'INCIDENTES', 'CAMBIO_ESTADO', :NEW.id_incidente,
        CAST(SYSTIMESTAMP AT TIME ZONE 'America/Bogota' AS TIMESTAMP), 'EXITO',
        :OLD.estado || ' -> ' || :NEW.estado);
END;
/

-- COMPROBACION 1: los 3 triggers deben salir VALID y ENABLED
SELECT o.object_name, o.status, t.status AS habilitado
FROM user_objects o
         JOIN user_triggers t ON t.trigger_name = o.object_name
WHERE o.object_name IN ('TRG_EMERGENCIA_ESTADO', 'TRG_ASIGNACION_ESTADO', 'TRG_INCIDENTE_ESTADO')
ORDER BY o.object_name;

-- COMPROBACION 2: la hora de Colombia y la ultima entrada de auditoria
SELECT CAST(SYSTIMESTAMP AT TIME ZONE 'America/Bogota' AS TIMESTAMP) AS hora_colombia,
       (SELECT MAX(fecha_hora) FROM auditoria) AS ultima_auditoria
FROM dual;
