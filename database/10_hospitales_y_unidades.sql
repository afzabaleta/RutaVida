-- ============================================================
-- RutaVida - Mas hospitales reales y unidades EJEMPLO repartidas
-- Archivo: 10_hospitales_y_unidades.sql
-- Requiere: 03_datos_valledupar.sql y 06_unidades_ejemplo.sql, y la red vial
-- de toda el area urbana (05_red_vial_valledupar.sql despues de DT-14):
-- con la red del centro, las bases del norte, el oriente y el occidente
-- quedan fuera y no reciben rutas.
-- ============================================================
-- HOSPITALES: son lugares REALES. La posicion es la de OpenStreetMap
-- (consulta database/osm/salud_valledupar.overpassql, 19/11/2026); cada uno
-- dice su id de OpenStreetMap y de donde sale la direccion.
-- El NIVEL marcado "pendiente" no tiene fuente oficial: es una suposicion
-- que el equipo debe confirmar (la columna no admite NULL).
-- Los servicios quedan con disponible = 'N', igual que en 03: no son datos
-- de disponibilidad real.
--
-- BASES Y UNIDADES: NO son reales. Las bases se llaman "EJEMPLO - Base ..."
-- y las unidades EJEMPLO-..., para repartir el despacho por la ciudad en la
-- simulacion. No se inventan placas. Una base junto a un hospital NO quiere
-- decir que ese hospital tenga ambulancias alli.
-- WHERE NOT EXISTS permite ejecutar el script varias veces sin duplicar.
-- Nombres sin tildes, igual que 03_datos_valledupar.sql.
-- ============================================================

-- 1. HOSPITALES Y CLINICAS (5)
--
-- CLINICA MEDICOS ALTA COMPLEJIDAD DEL CARIBE (centro)
--   Posicion: https://www.openstreetmap.org/way/1317791406
--   Direccion: https://angels-initiative.com/content/clinica-medicos-alta-complejidad-del-caribe
--   Nivel III pendiente: se toma del nombre ("alta complejidad"), sin fuente oficial.
-- CLINICA DEL CESAR (centro)
--   Posicion: https://www.openstreetmap.org/way/687405295
--   Direccion: https://www.doctoralia.co/clinicas/urgencias/valledupar
--   Nivel II pendiente de confirmar.
-- CLINICA VALLEDUPAR (centro)
--   Posicion: https://www.openstreetmap.org/way/687405515
--   Direccion: https://www.doctoralia.co/clinicas/urgencias/valledupar
--   Nivel II pendiente de confirmar.
-- HOSPITAL EDUARDO ARREDONDO DAZA - LA NEVADA (occidente)
--   Posicion y nombre: https://www.openstreetmap.org/node/13950981456
--   Direccion exacta no verificada. Nivel I como la sede San Martin de 03 (misma E.S.E.).
-- HOSPITAL EDUARDO ARREDONDO DAZA - SEDE ORIENTE (oriente)
--   Posicion: https://www.openstreetmap.org/way/687434990
--   OpenStreetMap solo dice "Hospital Eduardo Arredondo Daza": el nombre de la
--   sede y la direccion no estan verificados. Nivel I como las otras sedes.
INSERT INTO hospital (nombre, nivel, direccion, telefono, latitud, longitud, capacidad_urgencias, estado)
SELECT h.nombre, h.nivel, h.direccion, NULL, h.latitud, h.longitud, NULL, 'A'
FROM (
         SELECT 'CLINICA MEDICOS ALTA COMPLEJIDAD DEL CARIBE' AS nombre, 'III' AS nivel,
                'Calle 14 No. 17-47, Valledupar, Cesar' AS direccion,
                10.471992 AS latitud, -73.255195 AS longitud FROM dual
         UNION ALL SELECT 'CLINICA DEL CESAR', 'II', 'Calle 16 No. 14-90, Valledupar, Cesar',
                10.472254, -73.252288 FROM dual
         UNION ALL SELECT 'CLINICA VALLEDUPAR', 'II', 'Calle 16 No. 15-15, Valledupar, Cesar',
                10.471413, -73.252342 FROM dual
         UNION ALL SELECT 'HOSPITAL EDUARDO ARREDONDO DAZA - LA NEVADA', 'I',
                'Barrio La Nevada, Comuna 5, Valledupar, Cesar; direccion exacta no verificada',
                10.476582, -73.283043 FROM dual
         UNION ALL SELECT 'HOSPITAL EDUARDO ARREDONDO DAZA - SEDE ORIENTE', 'I',
                'Valledupar, Cesar; sede y direccion exacta no verificadas (OpenStreetMap)',
                10.458312, -73.231835 FROM dual
     ) h
WHERE NOT EXISTS (SELECT 1 FROM hospital x WHERE x.nombre = h.nombre);

-- Cada hospital nuevo con URGENCIAS (disponible = 'N' hasta confirmar)
INSERT INTO servicio_hospital (id_hospital, nombre_servicio, disponible, capacidad)
SELECT h.id_hospital, 'URGENCIAS', 'N', NULL
FROM hospital h
WHERE h.nombre IN ('CLINICA MEDICOS ALTA COMPLEJIDAD DEL CARIBE', 'CLINICA DEL CESAR',
                   'CLINICA VALLEDUPAR', 'HOSPITAL EDUARDO ARREDONDO DAZA - LA NEVADA',
                   'HOSPITAL EDUARDO ARREDONDO DAZA - SEDE ORIENTE')
  AND NOT EXISTS (
      SELECT 1 FROM servicio_hospital s
      WHERE s.id_hospital = h.id_hospital AND s.nombre_servicio = 'URGENCIAS'
  );

COMMIT;

-- 2. BASES DE EJEMPLO (4), una por zona; la del centro es la de 06
INSERT INTO estacion_emergencia (nombre, direccion, barrio, telefono, latitud, longitud, estado)
SELECT b.nombre, b.direccion, NULL, NULL, b.latitud, b.longitud, 'A'
FROM (
         SELECT 'EJEMPLO - Base norte' AS nombre,
                'EJEMPLO: base ficticia al norte de la ciudad; no es una base real' AS direccion,
                10.494652 AS latitud, -73.265681 AS longitud FROM dual
         UNION ALL SELECT 'EJEMPLO - Base occidente',
                'EJEMPLO: base ficticia junto al Hospital Eduardo Arredondo Daza - La Nevada; no es una base real',
                10.476582, -73.283043 FROM dual
         UNION ALL SELECT 'EJEMPLO - Base oriente',
                'EJEMPLO: base ficticia junto al Hospital Eduardo Arredondo Daza - Sede Oriente; no es una base real',
                10.458312, -73.231835 FROM dual
         UNION ALL SELECT 'EJEMPLO - Base sur',
                'EJEMPLO: base ficticia junto al Hospital Eduardo Arredondo Daza - San Martin; no es una base real',
                10.446380, -73.247410 FROM dual
     ) b
WHERE NOT EXISTS (SELECT 1 FROM estacion_emergencia x WHERE x.nombre = b.nombre);

COMMIT;

-- 3. UNIDADES DE EJEMPLO (10): dos ambulancias por base nueva, y una
-- maquina de bomberos en el norte y otra en el sur. Empiezan en su base.
INSERT INTO vehiculo_emergencia (
    id_tipo_vehiculo, id_estacion, codigo_unidad, placa,
    estado_operativo, latitud, longitud, fecha_actualizacion
)
SELECT t.id_tipo_vehiculo, e.id_estacion, u.codigo, NULL,
       'DISPONIBLE', e.latitud, e.longitud, SYSTIMESTAMP
FROM (
         SELECT 'EJEMPLO-AMB-04' AS codigo, 'AMBULANCIA' AS tipo, 'EJEMPLO - Base norte' AS estacion FROM dual
         UNION ALL SELECT 'EJEMPLO-AMB-05', 'AMBULANCIA', 'EJEMPLO - Base norte' FROM dual
         UNION ALL SELECT 'EJEMPLO-AMB-06', 'AMBULANCIA', 'EJEMPLO - Base occidente' FROM dual
         UNION ALL SELECT 'EJEMPLO-AMB-07', 'AMBULANCIA', 'EJEMPLO - Base occidente' FROM dual
         UNION ALL SELECT 'EJEMPLO-AMB-08', 'AMBULANCIA', 'EJEMPLO - Base oriente' FROM dual
         UNION ALL SELECT 'EJEMPLO-AMB-09', 'AMBULANCIA', 'EJEMPLO - Base oriente' FROM dual
         UNION ALL SELECT 'EJEMPLO-AMB-10', 'AMBULANCIA', 'EJEMPLO - Base sur' FROM dual
         UNION ALL SELECT 'EJEMPLO-AMB-11', 'AMBULANCIA', 'EJEMPLO - Base sur' FROM dual
         UNION ALL SELECT 'EJEMPLO-BOM-03', 'BOMBERO', 'EJEMPLO - Base norte' FROM dual
         UNION ALL SELECT 'EJEMPLO-BOM-04', 'BOMBERO', 'EJEMPLO - Base sur' FROM dual
     ) u
         JOIN tipo_vehiculo t ON t.nombre = u.tipo
         JOIN estacion_emergencia e ON e.nombre = u.estacion
WHERE NOT EXISTS (
    SELECT 1 FROM vehiculo_emergencia v WHERE v.codigo_unidad = u.codigo
);

COMMIT;

-- 4. COMPROBACION: 7 hospitales y 15 unidades EJEMPLO en 6 bases
SELECT nombre, nivel, latitud, longitud FROM hospital ORDER BY latitud DESC;

SELECT e.nombre AS base, COUNT(*) AS unidades
FROM vehiculo_emergencia v
         JOIN estacion_emergencia e ON e.id_estacion = v.id_estacion
WHERE v.codigo_unidad LIKE 'EJEMPLO-%'
GROUP BY e.nombre
ORDER BY e.nombre;
