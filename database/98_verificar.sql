-- =====================================================================
-- RutaVida · 98_verificar.sql
-- Comprueba que el esquema quedó completo. Ejecutar como RUTAVIDA.
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

ROLLBACK;
