-- =====================================================================
-- RutaVida · 98_verificar.sql
-- Comprueba que el esquema quedó completo. Ejecutar como RUTAVIDA,
-- después de 01, 02 y 04 (03 es opcional).
-- Resultado esperado en cada consulta: columna "ok" = 'SI'.
-- =====================================================================

-- 1. Las 23 tablas existen
SELECT COUNT(*) AS tablas,
       CASE WHEN COUNT(*) = 23 THEN 'SI' ELSE 'NO' END AS ok
  FROM user_tables
 WHERE table_name IN ('ROL', 'PERMISO', 'ROL_PERMISO', 'USUARIO', 'ESTACION_EMERGENCIA',
                      'TIPO_VEHICULO', 'VEHICULO_EMERGENCIA', 'HOSPITAL', 'SERVICIO_HOSPITAL',
                      'TIPO_EMERGENCIA', 'EMERGENCIA', 'NODO_VIAL', 'TRAMO_VIAL', 'SEMAFORO',
                      'TRAFICO', 'INCIDENTE_VIAL', 'RUTA', 'RUTA_TRAMO', 'ASIGNACION_EMERGENCIA',
                      'UBICACION_VEHICULO', 'ALERTA', 'NOTIFICACION', 'AUDITORIA');

-- 2. Las 21 secuencias existen
SELECT COUNT(*) AS secuencias,
       CASE WHEN COUNT(*) = 21 THEN 'SI' ELSE 'NO' END AS ok
  FROM user_sequences
 WHERE sequence_name LIKE 'SEQ\_%' ESCAPE '\';

-- 2b. Los 24 índices propios existen (22 ix_ + 2 ux_)
SELECT COUNT(*) AS indices,
       CASE WHEN COUNT(*) = 24 THEN 'SI' ELSE 'NO' END AS ok
  FROM user_indexes
 WHERE index_name LIKE 'IX\_%' ESCAPE '\'
    OR index_name LIKE 'UX\_%' ESCAPE '\';

-- 3. Ningún objeto inválido
SELECT COUNT(*) AS objetos_invalidos,
       CASE WHEN COUNT(*) = 0 THEN 'SI' ELSE 'NO' END AS ok
  FROM user_objects
 WHERE status <> 'VALID';

-- 4. Restricciones por tipo (P = primaria, R = foránea, U = única, C = check)
SELECT constraint_type, COUNT(*) AS cantidad
  FROM user_constraints
 WHERE table_name NOT LIKE 'BIN$%'
 GROUP BY constraint_type
 ORDER BY constraint_type;

-- 5. Datos iniciales
SELECT 'ROL' AS tabla, COUNT(*) AS filas, CASE WHEN COUNT(*) = 4  THEN 'SI' ELSE 'NO' END AS ok FROM rol
UNION ALL
SELECT 'PERMISO',         COUNT(*), CASE WHEN COUNT(*) = 20 THEN 'SI' ELSE 'NO' END FROM permiso
UNION ALL
SELECT 'ROL_PERMISO',     COUNT(*), CASE WHEN COUNT(*) = 53 THEN 'SI' ELSE 'NO' END FROM rol_permiso
UNION ALL
SELECT 'TIPO_VEHICULO',   COUNT(*), CASE WHEN COUNT(*) = 2  THEN 'SI' ELSE 'NO' END FROM tipo_vehiculo
UNION ALL
SELECT 'TIPO_EMERGENCIA', COUNT(*), CASE WHEN COUNT(*) = 4  THEN 'SI' ELSE 'NO' END FROM tipo_emergencia;

-- 6. Prueba de las reglas (todo se deshace con ROLLBACK al final)
--    Cada INSERT marcado "DEBE FALLAR" tiene que mostrar un error ORA-02290
--    (check) o ORA-00001 (único). Si alguno se inserta, la regla no funciona.

-- 6a. Columna virtual: 1000 m a 36 km/h = 100 s
INSERT INTO nodo_vial (latitud, longitud, referencia) VALUES (10.4631, -73.2532, 'PRUEBA_A');
INSERT INTO nodo_vial (latitud, longitud, referencia) VALUES (10.4640, -73.2540, 'PRUEBA_B');
INSERT INTO tramo_vial (id_nodo_origen, id_nodo_destino, nombre_via, distancia_m, velocidad_max_kmh)
SELECT a.id_nodo, b.id_nodo, 'VIA DE PRUEBA', 1000, 36
  FROM nodo_vial a, nodo_vial b
 WHERE a.referencia = 'PRUEBA_A' AND b.referencia = 'PRUEBA_B';
SELECT tiempo_base_seg, CASE WHEN tiempo_base_seg = 100 THEN 'SI' ELSE 'NO' END AS ok
  FROM tramo_vial WHERE nombre_via = 'VIA DE PRUEBA';

-- 6b. DEBE FALLAR: estado con minúsculas
INSERT INTO tramo_vial (id_nodo_origen, id_nodo_destino, nombre_via, distancia_m, velocidad_max_kmh, estado)
SELECT a.id_nodo, b.id_nodo, 'VIA DE PRUEBA 2', 500, 30, 'Abierto'
  FROM nodo_vial a, nodo_vial b
 WHERE a.referencia = 'PRUEBA_A' AND b.referencia = 'PRUEBA_B';

-- 6c. DEBE FALLAR: tramo que sale y llega al mismo nodo
INSERT INTO tramo_vial (id_nodo_origen, id_nodo_destino, nombre_via, distancia_m, velocidad_max_kmh)
SELECT a.id_nodo, a.id_nodo, 'VIA DE PRUEBA 3', 500, 30
  FROM nodo_vial a WHERE a.referencia = 'PRUEBA_A';

-- 6d. Un incidente nuevo queda PENDIENTE por defecto
INSERT INTO incidente_vial (id_tramo, tipo_incidente, severidad)
SELECT id_tramo, 'ACCIDENTE', 'ALTA' FROM tramo_vial WHERE nombre_via = 'VIA DE PRUEBA';
SELECT estado, CASE WHEN estado = 'PENDIENTE' THEN 'SI' ELSE 'NO' END AS ok
  FROM incidente_vial
 WHERE id_tramo = (SELECT id_tramo FROM tramo_vial WHERE nombre_via = 'VIA DE PRUEBA');

-- 7. Objetos PL/SQL de 04_plsql.sql (todos deben existir y estar VALID)
SELECT object_type, COUNT(*) AS cantidad,
       CASE object_type WHEN 'VIEW' THEN 3 WHEN 'FUNCTION' THEN 3
                        WHEN 'PROCEDURE' THEN 2 WHEN 'TRIGGER' THEN 3 END AS esperado,
       CASE WHEN COUNT(*) = CASE object_type WHEN 'VIEW' THEN 3 WHEN 'FUNCTION' THEN 3
                                             WHEN 'PROCEDURE' THEN 2 WHEN 'TRIGGER' THEN 3 END
            THEN 'SI' ELSE 'NO' END AS ok
  FROM user_objects
 WHERE object_type IN ('VIEW', 'FUNCTION', 'PROCEDURE', 'TRIGGER')
   AND status = 'VALID'
 GROUP BY object_type
 ORDER BY object_type;

-- 8. Prueba de los procedimientos, funciones, vistas y triggers.
--    Crea datos de prueba, despacha, simula la llegada y cierra.
--    Cada linea debe terminar en SI. Todo se deshace con el ROLLBACK final.
SET SERVEROUTPUT ON
DECLARE
    v_ahora      TIMESTAMP := SYSTIMESTAMP;
    v_rol        rol.id_rol%TYPE;
    v_usuario    usuario.id_usuario%TYPE;
    v_estacion   estacion_emergencia.id_estacion%TYPE;
    v_tipo_veh   tipo_vehiculo.id_tipo_vehiculo%TYPE;
    v_vehiculo   vehiculo_emergencia.id_vehiculo%TYPE;
    v_tipo_em    tipo_emergencia.id_tipo_emergencia%TYPE;
    v_emergencia emergencia.id_emergencia%TYPE;
    v_asignacion asignacion_emergencia.id_asignacion%TYPE;
    v_otra       asignacion_emergencia.id_asignacion%TYPE;
    v_disponibles NUMBER;
    v_n          NUMBER;
    v_texto      VARCHAR2(30);

    PROCEDURE ok(p_prueba VARCHAR2, p_cumple BOOLEAN) IS
    BEGIN
        DBMS_OUTPUT.PUT_LINE(RPAD(p_prueba, 62, '.') || CASE WHEN p_cumple THEN ' SI' ELSE ' NO' END);
    END;
BEGIN
    SELECT id_rol INTO v_rol FROM rol WHERE nombre_rol = 'COORDINADOR';
    INSERT INTO usuario (id_rol, documento, nombres, apellidos, email, username, password_hash)
    VALUES (v_rol, '99999999', 'PRUEBA', 'PLSQL', 'prueba.plsql@ejemplo.com', 'prueba_plsql', 'NO_ES_UN_HASH')
    RETURNING id_usuario INTO v_usuario;

    INSERT INTO estacion_emergencia (nombre, direccion, latitud, longitud)
    VALUES ('ESTACION DE PRUEBA', 'Direccion de prueba', 10.463100, -73.253200)
    RETURNING id_estacion INTO v_estacion;

    SELECT id_tipo_vehiculo INTO v_tipo_veh FROM tipo_vehiculo WHERE nombre = 'AMBULANCIA';
    INSERT INTO vehiculo_emergencia (id_tipo_vehiculo, id_estacion, codigo_unidad)
    VALUES (v_tipo_veh, v_estacion, 'PRUEBA-01')
    RETURNING id_vehiculo INTO v_vehiculo;

    SELECT id_tipo_emergencia INTO v_tipo_em FROM tipo_emergencia WHERE nombre = 'INCENDIO';
    INSERT INTO emergencia (id_tipo_emergencia, id_usuario_reporta, direccion_referencia,
                            latitud, longitud, fecha_hora_reporte, severidad)
    VALUES (v_tipo_em, v_usuario, 'Direccion de prueba', 10.464000, -73.254000,
            v_ahora - INTERVAL '10' MINUTE, 'ALTA')
    RETURNING id_emergencia INTO v_emergencia;

    DBMS_SESSION.SET_IDENTIFIER(TO_CHAR(v_usuario));

    SELECT COUNT(*) INTO v_n FROM vw_unidades_disponibles WHERE id_vehiculo = v_vehiculo;
    ok('8a. vw_unidades_disponibles muestra la unidad nueva', v_n = 1);
    SELECT COUNT(*) INTO v_n FROM vw_emergencias_activas WHERE id_emergencia = v_emergencia;
    ok('8b. vw_emergencias_activas muestra la emergencia', v_n = 1);
    v_disponibles := fn_unidades_disponibles;

    sp_asignar_unidad(v_emergencia, v_vehiculo, v_usuario, v_ahora - INTERVAL '9' MINUTE, v_asignacion);
    SELECT estado INTO v_texto FROM emergencia WHERE id_emergencia = v_emergencia;
    ok('8c. sp_asignar_unidad: emergencia pasa a ASIGNADA', v_texto = 'ASIGNADA');
    SELECT estado_operativo INTO v_texto FROM vehiculo_emergencia WHERE id_vehiculo = v_vehiculo;
    ok('8d. sp_asignar_unidad: unidad pasa a OCUPADO', v_texto = 'OCUPADO');
    ok('8e. fn_unidades_disponibles baja en 1', fn_unidades_disponibles = v_disponibles - 1);
    SELECT COUNT(*) INTO v_n FROM auditoria
     WHERE modulo = 'EMERGENCIAS' AND registro_id = v_emergencia
       AND detalle = 'REPORTADA -> ASIGNADA' AND id_usuario = v_usuario;
    ok('8f. trigger audita el cambio con el usuario de la sesion', v_n = 1);

    BEGIN
        sp_asignar_unidad(v_emergencia, v_vehiculo, v_usuario, NULL, v_otra);
        ok('8g. unidad ocupada rechazada (-20004)', FALSE);
    EXCEPTION
        WHEN OTHERS THEN
            ok('8g. unidad ocupada rechazada (-20004)', SQLCODE = -20004);
    END;

    BEGIN
        sp_cerrar_emergencia(v_emergencia, v_ahora);
        ok('8h. cierre desde ASIGNADA rechazado (-20005)', FALSE);
    EXCEPTION
        WHEN OTHERS THEN
            ok('8h. cierre desde ASIGNADA rechazado (-20005)', SQLCODE = -20005);
    END;

    UPDATE asignacion_emergencia SET estado = 'EN_RUTA', hora_salida = v_ahora - INTERVAL '8' MINUTE
     WHERE id_asignacion = v_asignacion;
    UPDATE emergencia SET estado = 'EN_RUTA' WHERE id_emergencia = v_emergencia;
    UPDATE asignacion_emergencia SET estado = 'EN_SITIO', hora_llegada = v_ahora - INTERVAL '2' MINUTE
     WHERE id_asignacion = v_asignacion;
    UPDATE emergencia SET estado = 'EN_SITIO' WHERE id_emergencia = v_emergencia;
    ok('8i. fn_tiempo_respuesta = 8 minutos (reporte -> llegada)', fn_tiempo_respuesta(v_asignacion) = 8);

    sp_cerrar_emergencia(v_emergencia, v_ahora);
    SELECT estado INTO v_texto FROM emergencia WHERE id_emergencia = v_emergencia;
    ok('8j. sp_cerrar_emergencia: emergencia CERRADA', v_texto = 'CERRADA');
    SELECT estado INTO v_texto FROM asignacion_emergencia WHERE id_asignacion = v_asignacion;
    ok('8k. sp_cerrar_emergencia: asignacion FINALIZADA', v_texto = 'FINALIZADA');
    SELECT estado_operativo INTO v_texto FROM vehiculo_emergencia WHERE id_vehiculo = v_vehiculo;
    ok('8l. sp_cerrar_emergencia: unidad vuelve a DISPONIBLE', v_texto = 'DISPONIBLE');
    SELECT COUNT(*) INTO v_n FROM vw_emergencias_activas WHERE id_emergencia = v_emergencia;
    ok('8m. la emergencia sale de vw_emergencias_activas', v_n = 0);
    SELECT COUNT(*) INTO v_n FROM auditoria WHERE modulo = 'DESPACHO' AND registro_id = v_asignacion;
    ok('8n. trigger audita 3 cambios de la asignacion', v_n = 3);

    DBMS_SESSION.CLEAR_IDENTIFIER;
END;
/

ROLLBACK;
