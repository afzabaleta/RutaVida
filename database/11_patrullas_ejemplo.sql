-- ============================================================
-- RutaVida - Patrullas de policia de EJEMPLO (DT-10)
-- Archivo: 11_patrullas_ejemplo.sql
-- Requiere: 05_red_vial_valledupar.sql, 07_boton_panico.sql (tipo de vehiculo
-- POLICIA) y 10_hospitales_y_unidades.sql (bases EJEMPLO de la ciudad).
-- ============================================================
-- Las patrullas NO son reales: se llaman EJEMPLO-POL-..., no tienen placa y
-- no se despachan a emergencias. Empiezan REPARTIDAS por la ciudad, cada una
-- en una esquina distinta de la red vial, a mas de 1,8 km entre si y a mas
-- de 900 m de las bases y los hospitales:
--   EJEMPLO-POL-01: Calle 31 x Carrera 23 (nodo OSM 986879647), administrada por EJEMPLO - Base sur
--   EJEMPLO-POL-02: Diagonal 16A x Transversal 23 (nodo OSM 4002816496), administrada por EJEMPLO - Base de ambulancias
--   EJEMPLO-POL-03: Avenida Sierra Nevada x Carrera 42 (nodo OSM 986876596), administrada por EJEMPLO - Base occidente
--   EJEMPLO-POL-04: Calle 7 Bis x Carrera 4 (nodo OSM 13415602577), administrada por EJEMPLO - Base norte
--   EJEMPLO-POL-05: Calle 19 x Carrera 4 (nodo OSM 3999500262), administrada por EJEMPLO - Base oriente
--   EJEMPLO-POL-06: Calle 20B x Carrera 38 (nodo OSM 12697231977), administrada por EJEMPLO - Base sur
-- La estacion solo dice que base la administra: la patrulla no sale de ahi.
-- Mientras la simulacion esta en marcha recorren la ciudad (PatrolSimulator):
-- cada una va a un punto al azar a 0,8 - 3 km, lejos de las demas, y al
-- llegar elige otro. Sus posiciones se guardan como SIMULACION.
-- Se puede ejecutar varias veces: crea las que falten y vuelve a poner en su
-- esquina de partida las que no esten en una mision.
-- ============================================================

-- 1. Crear las que falten
INSERT INTO vehiculo_emergencia (
    id_tipo_vehiculo, id_estacion, codigo_unidad, placa,
    estado_operativo, latitud, longitud, fecha_actualizacion
)
SELECT t.id_tipo_vehiculo, e.id_estacion, u.codigo, NULL,
       'DISPONIBLE', u.latitud, u.longitud, CAST(SYSTIMESTAMP AT TIME ZONE 'America/Bogota' AS TIMESTAMP)
FROM (
         SELECT 'EJEMPLO-POL-01' AS codigo, 'EJEMPLO - Base sur' AS estacion, 10.452163 AS latitud, -73.253300 AS longitud FROM dual
         UNION ALL SELECT 'EJEMPLO-POL-02', 'EJEMPLO - Base de ambulancias', 10.463702, -73.265503 FROM dual
         UNION ALL SELECT 'EJEMPLO-POL-03', 'EJEMPLO - Base occidente', 10.486036, -73.284120 FROM dual
         UNION ALL SELECT 'EJEMPLO-POL-04', 'EJEMPLO - Base norte', 10.489399, -73.255838 FROM dual
         UNION ALL SELECT 'EJEMPLO-POL-05', 'EJEMPLO - Base oriente', 10.471853, -73.237772 FROM dual
         UNION ALL SELECT 'EJEMPLO-POL-06', 'EJEMPLO - Base sur', 10.443121, -73.275703 FROM dual
     ) u
         JOIN tipo_vehiculo t ON t.nombre = 'POLICIA'
         JOIN estacion_emergencia e ON e.nombre = u.estacion
WHERE NOT EXISTS (
    SELECT 1 FROM vehiculo_emergencia v WHERE v.codigo_unidad = u.codigo
);

-- 2. Las que ya existian vuelven a su esquina de partida, con su base
UPDATE vehiculo_emergencia v
   SET (latitud, longitud, id_estacion) = (
           SELECT u.latitud, u.longitud, e.id_estacion
             FROM (
                  SELECT 'EJEMPLO-POL-01' AS codigo, 'EJEMPLO - Base sur' AS estacion, 10.452163 AS latitud, -73.253300 AS longitud FROM dual
                  UNION ALL SELECT 'EJEMPLO-POL-02', 'EJEMPLO - Base de ambulancias', 10.463702, -73.265503 FROM dual
                  UNION ALL SELECT 'EJEMPLO-POL-03', 'EJEMPLO - Base occidente', 10.486036, -73.284120 FROM dual
                  UNION ALL SELECT 'EJEMPLO-POL-04', 'EJEMPLO - Base norte', 10.489399, -73.255838 FROM dual
                  UNION ALL SELECT 'EJEMPLO-POL-05', 'EJEMPLO - Base oriente', 10.471853, -73.237772 FROM dual
                  UNION ALL SELECT 'EJEMPLO-POL-06', 'EJEMPLO - Base sur', 10.443121, -73.275703 FROM dual
                  ) u
                  JOIN estacion_emergencia e ON e.nombre = u.estacion
            WHERE u.codigo = v.codigo_unidad),
       fecha_actualizacion = CAST(SYSTIMESTAMP AT TIME ZONE 'America/Bogota' AS TIMESTAMP)
 WHERE v.codigo_unidad IN ('EJEMPLO-POL-01', 'EJEMPLO-POL-02', 'EJEMPLO-POL-03',
                           'EJEMPLO-POL-04', 'EJEMPLO-POL-05', 'EJEMPLO-POL-06')
   AND v.estado_operativo <> 'OCUPADO';

COMMIT;

-- COMPROBACION: 6 patrullas EJEMPLO, DISPONIBLE, cada una en su esquina
SELECT v.codigo_unidad, v.latitud, v.longitud, e.nombre AS base, v.estado_operativo
FROM vehiculo_emergencia v
         JOIN tipo_vehiculo t ON t.id_tipo_vehiculo = v.id_tipo_vehiculo
         JOIN estacion_emergencia e ON e.id_estacion = v.id_estacion
WHERE t.nombre = 'POLICIA'
ORDER BY v.codigo_unidad;
