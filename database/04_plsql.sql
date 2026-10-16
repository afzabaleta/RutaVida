-- =====================================================================
-- RutaVida · 04_plsql.sql
-- Vistas, funciones, procedimientos y triggers (seccion 7.13 del
-- documento consolidado). Ejecutar como RUTAVIDA despues de 01 y 02.
-- Se puede ejecutar varias veces: todo usa CREATE OR REPLACE.
--
-- Decisiones (docs/decisiones-tecnicas.md, DT-05):
--   * Java decide, PL/SQL ejecuta: los servicios validan las reglas y
--     llaman a los procedimientos; los procedimientos revalidan, hacen
--     los cambios juntos y NO hacen COMMIT (lo hace el TransactionManager).
--   * Los triggers solo auditan cambios de ESTADO. El usuario se toma de
--     CLIENT_IDENTIFIER, que el backend fija con el id del usuario.
--   * Tiempo de respuesta = desde el reporte hasta la llegada al sitio.
--
-- Codigos de error de los procedimientos:
--   -20001 la emergencia no existe        -20004 la unidad no esta disponible
--   -20002 la emergencia ya esta cerrada  -20005 no se puede cerrar desde ese estado
--   -20003 la unidad no existe            -20006 fecha de cierre anterior al reporte
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. FUNCION AUXILIAR: usuario de la sesion
--    Devuelve el id_usuario guardado en CLIENT_IDENTIFIER, o NULL si no
--    hay uno valido (asi un trigger nunca falla por la clave foranea).
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_usuario_sesion RETURN NUMBER IS
    v_texto   VARCHAR2(64) := SYS_CONTEXT('USERENV', 'CLIENT_IDENTIFIER');
    v_usuario usuario.id_usuario%TYPE;
BEGIN
    IF v_texto IS NULL OR NOT REGEXP_LIKE(v_texto, '^[0-9]{1,10}$') THEN
        RETURN NULL;
END IF;
SELECT id_usuario INTO v_usuario FROM usuario WHERE id_usuario = TO_NUMBER(v_texto);
RETURN v_usuario;
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        RETURN NULL;
END fn_usuario_sesion;
/

-- ---------------------------------------------------------------------
-- 2. VISTAS DEL DASHBOARD
-- ---------------------------------------------------------------------

-- Emergencias que no estan cerradas, con su tipo y cuantas unidades atienden
CREATE OR REPLACE VIEW vw_emergencias_activas AS
SELECT e.id_emergencia,
       e.estado,
       e.severidad,
       t.nombre    AS tipo_emergencia,
       t.prioridad AS prioridad_tipo,
       e.direccion_referencia,
       e.latitud,
       e.longitud,
       e.fecha_hora_reporte,
       (SELECT COUNT(*)
        FROM asignacion_emergencia a
        WHERE a.id_emergencia = e.id_emergencia
          AND a.estado NOT IN ('FINALIZADA', 'CANCELADA')) AS unidades_asignadas
FROM emergencia e
         JOIN tipo_emergencia t ON t.id_tipo_emergencia = e.id_tipo_emergencia
WHERE e.estado <> 'CERRADA';

-- Unidades que se pueden despachar ahora mismo.
-- Si la unidad no tiene posicion conocida, se usa la de su estacion.
CREATE OR REPLACE VIEW vw_unidades_disponibles AS
SELECT v.id_vehiculo,
       v.codigo_unidad,
       v.placa,
       tv.id_tipo_vehiculo,
       tv.nombre    AS tipo_vehiculo,
       tv.prioridad AS prioridad_tipo,
       s.id_estacion,
       s.nombre     AS estacion,
       NVL(v.latitud, s.latitud)   AS latitud,
       NVL(v.longitud, s.longitud) AS longitud,
       CASE WHEN v.latitud IS NULL THEN 'N' ELSE 'S' END AS posicion_conocida,
       v.fecha_actualizacion
FROM vehiculo_emergencia v
         JOIN tipo_vehiculo tv       ON tv.id_tipo_vehiculo = v.id_tipo_vehiculo
         JOIN estacion_emergencia s  ON s.id_estacion = v.id_estacion
WHERE v.estado_operativo = 'DISPONIBLE';

-- Hospitales activos y si su servicio de urgencias esta disponible
CREATE OR REPLACE VIEW vw_hospitales_operativos AS
SELECT h.id_hospital,
       h.nombre,
       h.nivel,
       h.direccion,
       h.telefono,
       h.latitud,
       h.longitud,
       h.capacidad_urgencias,
       NVL((SELECT sh.disponible
            FROM servicio_hospital sh
            WHERE sh.id_hospital = h.id_hospital
              AND sh.nombre_servicio = 'URGENCIAS'), 'N') AS urgencias_disponible,
       (SELECT COUNT(*)
        FROM servicio_hospital sh
        WHERE sh.id_hospital = h.id_hospital
          AND sh.disponible = 'S') AS servicios_disponibles
FROM hospital h
WHERE h.estado = 'A';

-- ---------------------------------------------------------------------
-- 3. FUNCIONES PARA REPORTES
-- ---------------------------------------------------------------------

-- Minutos desde que se reporto la emergencia hasta que la unidad llego.
-- NULL si la asignacion no existe o la unidad todavia no ha llegado.
CREATE OR REPLACE FUNCTION fn_tiempo_respuesta (
    p_id_asignacion IN asignacion_emergencia.id_asignacion%TYPE
) RETURN NUMBER IS
    v_diferencia INTERVAL DAY(3) TO SECOND(3);
BEGIN
SELECT a.hora_llegada - e.fecha_hora_reporte
INTO v_diferencia
FROM asignacion_emergencia a
         JOIN emergencia e ON e.id_emergencia = a.id_emergencia
WHERE a.id_asignacion = p_id_asignacion;

IF v_diferencia IS NULL THEN
        RETURN NULL;
END IF;
RETURN ROUND(EXTRACT(DAY    FROM v_diferencia) * 1440
                 + EXTRACT(HOUR   FROM v_diferencia) * 60
                 + EXTRACT(MINUTE FROM v_diferencia)
                 + EXTRACT(SECOND FROM v_diferencia) / 60, 2);
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        RETURN NULL;
END fn_tiempo_respuesta;
/

-- Cantidad de unidades DISPONIBLES; con p_id_tipo_vehiculo, solo de ese tipo
CREATE OR REPLACE FUNCTION fn_unidades_disponibles (
    p_id_tipo_vehiculo IN tipo_vehiculo.id_tipo_vehiculo%TYPE DEFAULT NULL
) RETURN NUMBER IS
    v_total NUMBER;
BEGIN
SELECT COUNT(*)
INTO v_total
FROM vehiculo_emergencia
WHERE estado_operativo = 'DISPONIBLE'
  AND (p_id_tipo_vehiculo IS NULL OR id_tipo_vehiculo = p_id_tipo_vehiculo);
RETURN v_total;
END fn_unidades_disponibles;
/

-- ---------------------------------------------------------------------
-- 4. PROCEDIMIENTOS (no hacen COMMIT: la transaccion es del backend)
-- ---------------------------------------------------------------------

-- Despacha una unidad a una emergencia: crea la asignacion, marca la
-- unidad OCUPADO y, si la emergencia estaba REPORTADA, la pasa a ASIGNADA.
CREATE OR REPLACE PROCEDURE sp_asignar_unidad (
    p_id_emergencia  IN  emergencia.id_emergencia%TYPE,
    p_id_vehiculo    IN  vehiculo_emergencia.id_vehiculo%TYPE,
    p_id_coordinador IN  usuario.id_usuario%TYPE,
    p_fecha          IN  TIMESTAMP DEFAULT NULL,
    p_id_asignacion  OUT asignacion_emergencia.id_asignacion%TYPE
) IS
    v_estado_emergencia emergencia.estado%TYPE;
    v_estado_unidad     vehiculo_emergencia.estado_operativo%TYPE;
BEGIN
BEGIN
SELECT estado INTO v_estado_emergencia
FROM emergencia WHERE id_emergencia = p_id_emergencia
    FOR UPDATE;
EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(-20001, 'La emergencia ' || p_id_emergencia || ' no existe');
END;
    IF v_estado_emergencia = 'CERRADA' THEN
        RAISE_APPLICATION_ERROR(-20002, 'La emergencia ' || p_id_emergencia || ' ya esta cerrada');
END IF;

BEGIN
SELECT estado_operativo INTO v_estado_unidad
FROM vehiculo_emergencia WHERE id_vehiculo = p_id_vehiculo
    FOR UPDATE;
EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(-20003, 'La unidad ' || p_id_vehiculo || ' no existe');
END;
    IF v_estado_unidad <> 'DISPONIBLE' THEN
        RAISE_APPLICATION_ERROR(-20004, 'La unidad ' || p_id_vehiculo || ' no esta disponible (' || v_estado_unidad || ')');
END IF;

INSERT INTO asignacion_emergencia (id_emergencia, id_vehiculo, id_usuario_coordinador, fecha_asignacion)
VALUES (p_id_emergencia, p_id_vehiculo, p_id_coordinador, NVL(p_fecha, SYSTIMESTAMP))
    RETURNING id_asignacion INTO p_id_asignacion;

UPDATE vehiculo_emergencia SET estado_operativo = 'OCUPADO' WHERE id_vehiculo = p_id_vehiculo;

IF v_estado_emergencia = 'REPORTADA' THEN
UPDATE emergencia SET estado = 'ASIGNADA' WHERE id_emergencia = p_id_emergencia;
END IF;
END sp_asignar_unidad;
/

-- Cierra una emergencia: finaliza o cancela sus asignaciones abiertas,
-- libera las unidades, cierra sus rutas y marca la emergencia CERRADA.
-- Solo se cierra desde REPORTADA (falsa alarma), EN_SITIO o TRASLADANDO,
-- igual que EmergencyStatus en Java.
CREATE OR REPLACE PROCEDURE sp_cerrar_emergencia (
    p_id_emergencia IN emergencia.id_emergencia%TYPE,
    p_fecha         IN TIMESTAMP DEFAULT NULL
) IS
    v_estado  emergencia.estado%TYPE;
    v_reporte emergencia.fecha_hora_reporte%TYPE;
    v_cierre  TIMESTAMP := NVL(p_fecha, SYSTIMESTAMP);
BEGIN
BEGIN
SELECT estado, fecha_hora_reporte INTO v_estado, v_reporte
FROM emergencia WHERE id_emergencia = p_id_emergencia
    FOR UPDATE;
EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(-20001, 'La emergencia ' || p_id_emergencia || ' no existe');
END;
    IF v_estado = 'CERRADA' THEN
        RAISE_APPLICATION_ERROR(-20002, 'La emergencia ' || p_id_emergencia || ' ya esta cerrada');
END IF;
    IF v_estado NOT IN ('REPORTADA', 'EN_SITIO', 'TRASLADANDO') THEN
        RAISE_APPLICATION_ERROR(-20005, 'No se puede cerrar una emergencia en estado ' || v_estado);
END IF;
    IF v_cierre < v_reporte THEN
        RAISE_APPLICATION_ERROR(-20006, 'La fecha de cierre es anterior al reporte');
END IF;

    -- Libera las unidades de las asignaciones abiertas
UPDATE vehiculo_emergencia
SET estado_operativo = 'DISPONIBLE'
WHERE estado_operativo = 'OCUPADO'
  AND id_vehiculo IN (SELECT id_vehiculo
                      FROM asignacion_emergencia
                      WHERE id_emergencia = p_id_emergencia
                        AND estado NOT IN ('FINALIZADA', 'CANCELADA'));

-- Las que llegaron al sitio terminan; las que no alcanzaron a llegar se cancelan
UPDATE asignacion_emergencia
SET estado = CASE WHEN estado IN ('EN_SITIO', 'TRASLADANDO') THEN 'FINALIZADA' ELSE 'CANCELADA' END
WHERE id_emergencia = p_id_emergencia
  AND estado NOT IN ('FINALIZADA', 'CANCELADA');

UPDATE ruta
SET estado = CASE WHEN estado = 'ACTIVA' THEN 'FINALIZADA' ELSE 'DESCARTADA' END
WHERE id_emergencia = p_id_emergencia
  AND estado IN ('ACTIVA', 'PROPUESTA');

UPDATE emergencia
SET estado = 'CERRADA', fecha_hora_cierre = v_cierre
WHERE id_emergencia = p_id_emergencia;
END sp_cerrar_emergencia;
/

-- ---------------------------------------------------------------------
-- 5. TRIGGERS DE AUDITORIA (solo cambios de estado)
--    operacion = CAMBIO_ESTADO; detalle = 'ANTES -> DESPUES'
-- ---------------------------------------------------------------------

CREATE OR REPLACE TRIGGER trg_emergencia_estado
AFTER UPDATE OF estado ON emergencia
    FOR EACH ROW
    WHEN (NEW.estado <> OLD.estado)
BEGIN
INSERT INTO auditoria (id_usuario, modulo, operacion, registro_id, resultado, detalle)
VALUES (fn_usuario_sesion, 'EMERGENCIAS', 'CAMBIO_ESTADO', :NEW.id_emergencia, 'EXITO',
        :OLD.estado || ' -> ' || :NEW.estado);
END;
/

CREATE OR REPLACE TRIGGER trg_asignacion_estado
AFTER UPDATE OF estado ON asignacion_emergencia
    FOR EACH ROW
    WHEN (NEW.estado <> OLD.estado)
BEGIN
INSERT INTO auditoria (id_usuario, modulo, operacion, registro_id, resultado, detalle)
VALUES (fn_usuario_sesion, 'DESPACHO', 'CAMBIO_ESTADO', :NEW.id_asignacion, 'EXITO',
        :OLD.estado || ' -> ' || :NEW.estado);
END;
/

CREATE OR REPLACE TRIGGER trg_incidente_estado
AFTER UPDATE OF estado ON incidente_vial
    FOR EACH ROW
    WHEN (NEW.estado <> OLD.estado)
BEGIN
INSERT INTO auditoria (id_usuario, modulo, operacion, registro_id, resultado, detalle)
VALUES (fn_usuario_sesion, 'INCIDENTES', 'CAMBIO_ESTADO', :NEW.id_incidente, 'EXITO',
        :OLD.estado || ' -> ' || :NEW.estado);
END;
/

-- Si algo quedo invalido, esta consulta lo muestra (debe salir vacia)
SELECT object_type, object_name, status
FROM user_objects
WHERE object_name IN ('FN_USUARIO_SESION', 'VW_EMERGENCIAS_ACTIVAS', 'VW_UNIDADES_DISPONIBLES',
                      'VW_HOSPITALES_OPERATIVOS', 'FN_TIEMPO_RESPUESTA', 'FN_UNIDADES_DISPONIBLES',
                      'SP_ASIGNAR_UNIDAD', 'SP_CERRAR_EMERGENCIA', 'TRG_EMERGENCIA_ESTADO',
                      'TRG_ASIGNACION_ESTADO', 'TRG_INCIDENTE_ESTADO')
  AND status <> 'VALID';