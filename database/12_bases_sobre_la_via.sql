-- ============================================================
-- RutaVida - Bases EJEMPLO sobre la calle
-- Archivo: 12_bases_sobre_la_via.sql
-- Requiere: 05_red_vial_valledupar.sql, 06_unidades_ejemplo.sql y
-- 10_hospitales_y_unidades.sql.
-- ============================================================
-- 06 y 10 pusieron las bases EJEMPLO en el punto del hospital que tienen al
-- lado (el centro del edificio en OpenStreetMap), asi que las ambulancias
-- aparecian DENTRO del hospital. Este script mueve cada base a la esquina
-- con nombre mas cercana de la red vial (a 40 - 115 m), sobre la calle:
--   EJEMPLO - Base de ambulancias: Calle 16 x Carrera 18 (nodo OSM 986875758)
--   EJEMPLO - Base norte: Calle 2C x Transversal 8 (nodo OSM 3999846119)
--   EJEMPLO - Base occidente: Calle 6 x Carrera 43 (nodo OSM 986888229)
--   EJEMPLO - Base oriente: Calle 31 x Carrera 4g (nodo OSM 986874747)
--   EJEMPLO - Base sur: Calle 43 x Carrera 20 (nodo OSM 4001358158)
-- Despues pone ahi las unidades EJEMPLO de esas bases que no estan en una
-- mision (las patrullas no: empiezan repartidas, 11_patrullas_ejemplo.sql).
-- Las bases siguen siendo ficticias. Se puede ejecutar varias veces.
-- ============================================================

-- 1. Las bases, sobre la calle
UPDATE estacion_emergencia e
   SET (direccion, latitud, longitud) = (
           SELECT b.direccion, b.latitud, b.longitud
             FROM (
                  SELECT 'EJEMPLO - Base de ambulancias' AS nombre, 'EJEMPLO: base ficticia en Calle 16 x Carrera 18, cerca del Hospital Rosario Pumarejo de Lopez; no es una base real' AS direccion, 10.470032 AS latitud, -73.255271 AS longitud FROM dual
                  UNION ALL SELECT 'EJEMPLO - Base norte', 'EJEMPLO: base ficticia en Calle 2C x Transversal 8, al norte de la ciudad; no es una base real', 10.494728, -73.266239 FROM dual
                  UNION ALL SELECT 'EJEMPLO - Base occidente', 'EJEMPLO: base ficticia en Calle 6 x Carrera 43, cerca del Hospital Eduardo Arredondo Daza - La Nevada; no es una base real', 10.477029, -73.282986 FROM dual
                  UNION ALL SELECT 'EJEMPLO - Base oriente', 'EJEMPLO: base ficticia en Calle 31 x Carrera 4g, cerca del Hospital Eduardo Arredondo Daza - Sede Oriente; no es una base real', 10.458856, -73.231698 FROM dual
                  UNION ALL SELECT 'EJEMPLO - Base sur', 'EJEMPLO: base ficticia en Calle 43 x Carrera 20, cerca del Hospital Eduardo Arredondo Daza - San Martin; no es una base real', 10.446566, -73.247084 FROM dual
                  ) b
            WHERE b.nombre = e.nombre)
 WHERE e.nombre IN ('EJEMPLO - Base de ambulancias', 'EJEMPLO - Base norte', 'EJEMPLO - Base occidente',
                    'EJEMPLO - Base oriente', 'EJEMPLO - Base sur');

-- 2. Sus unidades EJEMPLO libres, en la base (menos las patrullas)
UPDATE vehiculo_emergencia v
   SET (latitud, longitud) = (SELECT e.latitud, e.longitud
                                FROM estacion_emergencia e
                               WHERE e.id_estacion = v.id_estacion),
       fecha_actualizacion = CAST(SYSTIMESTAMP AT TIME ZONE 'America/Bogota' AS TIMESTAMP)
 WHERE v.codigo_unidad LIKE 'EJEMPLO-%'
   AND v.codigo_unidad NOT LIKE 'EJEMPLO-POL-%'
   AND v.estado_operativo <> 'OCUPADO'
   AND v.id_estacion IN (SELECT id_estacion FROM estacion_emergencia WHERE nombre LIKE 'EJEMPLO - %');

COMMIT;

-- COMPROBACION: las 5 bases EJEMPLO con su esquina y cuantas unidades tienen
SELECT e.nombre, e.direccion, e.latitud, e.longitud, COUNT(v.id_vehiculo) AS unidades
FROM estacion_emergencia e
         LEFT JOIN vehiculo_emergencia v ON v.id_estacion = e.id_estacion
                                        AND v.codigo_unidad NOT LIKE 'EJEMPLO-POL-%'
WHERE e.nombre LIKE 'EJEMPLO - %'
GROUP BY e.nombre, e.direccion, e.latitud, e.longitud
ORDER BY e.nombre;
