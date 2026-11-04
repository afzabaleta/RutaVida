-- ============================================================
-- RutaVida - Unidades de EJEMPLO para la simulacion
-- Archivo: 06_unidades_ejemplo.sql
-- Requiere: 02_datos_iniciales.sql y 03_datos_valledupar.sql
-- ============================================================
-- ESTOS DATOS NO SON REALES. Sirven para demostrar el despacho y la
-- simulacion. Los codigos empiezan por EJEMPLO- y la base de ambulancias
-- dice EJEMPLO en el nombre, para que nunca se confundan con unidades reales.
-- No se inventan placas: la columna placa queda NULL.
-- La posicion inicial de cada unidad es la de su estacion.
-- WHERE NOT EXISTS permite ejecutar el script varias veces sin duplicar.

-- 1. BASE DE AMBULANCIAS DE EJEMPLO
-- Se ubica en las coordenadas del Hospital Rosario Pumarejo de Lopez
-- (03_datos_valledupar.sql), dentro de la zona de la red vial.
INSERT INTO estacion_emergencia (nombre, direccion, barrio, telefono, latitud, longitud, estado)
SELECT
    'EJEMPLO - Base de ambulancias',
    'EJEMPLO: base ficticia en el Hospital Rosario Pumarejo de Lopez; no es una base real',
    NULL,
    NULL,
    10.469330,
    -73.254520,
    'A'
FROM dual
WHERE NOT EXISTS (
    SELECT 1 FROM estacion_emergencia WHERE nombre = 'EJEMPLO - Base de ambulancias'
);

-- 2. UNIDADES DE EJEMPLO
-- Tres ambulancias en la base de ejemplo (una en mantenimiento) y dos
-- maquinas de bomberos en la Estacion de Bomberos de 03_datos_valledupar.sql.
INSERT INTO vehiculo_emergencia (
    id_tipo_vehiculo, id_estacion, codigo_unidad, placa,
    estado_operativo, latitud, longitud, fecha_actualizacion
)
SELECT t.id_tipo_vehiculo, e.id_estacion, u.codigo, NULL,
       u.estado, e.latitud, e.longitud, SYSTIMESTAMP
FROM (
         SELECT 'EJEMPLO-AMB-01' AS codigo, 'AMBULANCIA' AS tipo, 'EJEMPLO - Base de ambulancias' AS estacion, 'DISPONIBLE' AS estado FROM dual
         UNION ALL SELECT 'EJEMPLO-AMB-02', 'AMBULANCIA', 'EJEMPLO - Base de ambulancias', 'DISPONIBLE' FROM dual
         UNION ALL SELECT 'EJEMPLO-AMB-03', 'AMBULANCIA', 'EJEMPLO - Base de ambulancias', 'MANTENIMIENTO' FROM dual
         UNION ALL SELECT 'EJEMPLO-BOM-01', 'BOMBERO', 'Estacion de Bomberos', 'DISPONIBLE' FROM dual
         UNION ALL SELECT 'EJEMPLO-BOM-02', 'BOMBERO', 'Estacion de Bomberos', 'DISPONIBLE' FROM dual
     ) u
         JOIN tipo_vehiculo t ON t.nombre = u.tipo
         JOIN estacion_emergencia e ON e.nombre = u.estacion
WHERE NOT EXISTS (
    SELECT 1 FROM vehiculo_emergencia v WHERE v.codigo_unidad = u.codigo
);

COMMIT;

-- 3. COMPROBACION: deben salir 5 filas
SELECT v.codigo_unidad, t.nombre AS tipo, e.nombre AS estacion, v.estado_operativo
FROM vehiculo_emergencia v
         JOIN tipo_vehiculo t ON t.id_tipo_vehiculo = v.id_tipo_vehiculo
         JOIN estacion_emergencia e ON e.id_estacion = v.id_estacion
WHERE v.codigo_unidad LIKE 'EJEMPLO-%'
ORDER BY v.codigo_unidad;