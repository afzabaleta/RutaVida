-- ============================================================
-- RutaVida - Patrullas de policia de EJEMPLO (DT-10)
-- Archivo: 11_patrullas_ejemplo.sql
-- Requiere: 07_boton_panico.sql (tipo de vehiculo POLICIA) y
-- 10_hospitales_y_unidades.sql (bases EJEMPLO de la ciudad).
-- ============================================================
-- Las patrullas NO son reales: se llaman EJEMPLO-POL-..., no tienen placa
-- y salen de las mismas bases EJEMPLO de las ambulancias. Mientras la
-- simulacion esta en marcha recorren la ciudad (PatrolSimulator): cada una
-- elige un punto al azar de la red vial a 0,8 - 3 km y, al llegar, otro.
-- Sus posiciones se guardan como SIMULACION. No se despachan a emergencias.
-- 09_reiniciar_demo.sql las devuelve a su base, como a las demas EJEMPLO.
-- WHERE NOT EXISTS permite ejecutar el script varias veces sin duplicar.
-- ============================================================

INSERT INTO vehiculo_emergencia (
    id_tipo_vehiculo, id_estacion, codigo_unidad, placa,
    estado_operativo, latitud, longitud, fecha_actualizacion
)
SELECT t.id_tipo_vehiculo, e.id_estacion, u.codigo, NULL,
       'DISPONIBLE', e.latitud, e.longitud, CAST(SYSTIMESTAMP AT TIME ZONE 'America/Bogota' AS TIMESTAMP)
FROM (
         SELECT 'EJEMPLO-POL-01' AS codigo, 'EJEMPLO - Base de ambulancias' AS estacion FROM dual
         UNION ALL SELECT 'EJEMPLO-POL-02', 'EJEMPLO - Base de ambulancias' FROM dual
         UNION ALL SELECT 'EJEMPLO-POL-03', 'EJEMPLO - Base norte' FROM dual
         UNION ALL SELECT 'EJEMPLO-POL-04', 'EJEMPLO - Base occidente' FROM dual
         UNION ALL SELECT 'EJEMPLO-POL-05', 'EJEMPLO - Base oriente' FROM dual
         UNION ALL SELECT 'EJEMPLO-POL-06', 'EJEMPLO - Base sur' FROM dual
     ) u
         JOIN tipo_vehiculo t ON t.nombre = 'POLICIA'
         JOIN estacion_emergencia e ON e.nombre = u.estacion
WHERE NOT EXISTS (
    SELECT 1 FROM vehiculo_emergencia v WHERE v.codigo_unidad = u.codigo
);

COMMIT;

-- COMPROBACION: 6 patrullas EJEMPLO, DISPONIBLE, repartidas en 5 bases
SELECT v.codigo_unidad, e.nombre AS base, v.estado_operativo
FROM vehiculo_emergencia v
         JOIN tipo_vehiculo t ON t.id_tipo_vehiculo = v.id_tipo_vehiculo
         JOIN estacion_emergencia e ON e.id_estacion = v.id_estacion
WHERE t.nombre = 'POLICIA'
ORDER BY v.codigo_unidad;
