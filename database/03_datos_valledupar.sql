-- ============================================================
-- RutaVida - Datos reales de Valledupar
-- Archivo: 03_datos_valledupar.sql
-- Coordenadas y ubicacion: OpenStreetMap
-- Consulta de fuentes: 2026-10-18
-- ============================================================

-- Fuente de ubicacion:
-- https://mapcarta.com/es/W687362314
-- La ficha se basa en datos de OpenStreetMap.
-- Coordenadas publicadas: 10.46858, -73.25759.

-- Fuente cartografica de la ubicacion aproximada:
-- https://mapcarta.com/es/W687362314
-- La fuente no confirma una direccion postal exacta.
-- Coordenadas publicadas: 10.46858, -73.25759.
-- 1. ESTACION DE BOMBEROS
-- Se omiten telefono y barrio porque no se han verificado.
-- WHERE NOT EXISTS evita insertar nuevamente el mismo registro.

INSERT INTO estacion_emergencia (
    nombre,
    direccion,
    barrio,
    telefono,
    latitud,
    longitud,
    estado
)
SELECT
    'Estacion de Bomberos',
    'Ubicacion en Valledupar, Cesar; direccion exacta no verificada',
    NULL,
    NULL,
    10.468580,
    -73.257590,
    'A'
FROM dual
WHERE NOT EXISTS (
    SELECT 1
    FROM estacion_emergencia
    WHERE nombre = 'Estacion de Bomberos'
      AND direccion = 'Ubicacion en Valledupar, Cesar; direccion exacta no verificada'
);

COMMIT;

-- ============================================================
-- 2. HOSPITALES DE VALLEDUPAR
-- Direcciones verificadas en fuentes institucionales.
-- Coordenadas consultadas en OpenStreetMap mediante Mapcarta.
-- ============================================================

-- Fuente institucional para direccion y contacto:
-- https://hrplopez.gov.co/sitio/index.php/es/transparencia/52-trasparencia-y-acceso-a-la-informacion-publica/50-1-2-mecanismos-para-la-atencion-al-ciudadano
-- Fuente cartografica para coordenadas:
-- https://mapcarta.com/es/N6456494546
-- Nivel II pendiente de confirmar con una fuente oficial especifica.
-- Hospital Rosario Pumarejo de Lopez
INSERT INTO hospital (
    nombre,
    nivel,
    direccion,
    telefono,
    latitud,
    longitud,
    capacidad_urgencias,
    estado
)
SELECT
    'HOSPITAL ROSARIO PUMAREJO DE LOPEZ',
    'II',
    'Calle 16 No. 17-141, Valledupar, Cesar',
    NULL,
    10.469330,
    -73.254520,
    NULL,
    'A'
FROM dual
WHERE NOT EXISTS (
    SELECT 1
    FROM hospital
    WHERE nombre = 'HOSPITAL ROSARIO PUMAREJO DE LOPEZ'
);

-- Fuente institucional para direccion, telefono y atencion:
-- https://www.headese.gov.co/2021/preguntas-frecuentes/
-- Fuente cartografica para coordenadas:
-- https://mapcarta.com/es/W687415468
-- Hospital Eduardo Arredondo Daza - sede San Martin
INSERT INTO hospital (
    nombre,
    nivel,
    direccion,
    telefono,
    latitud,
    longitud,
    capacidad_urgencias,
    estado
)
SELECT
    'HOSPITAL EDUARDO ARREDONDO DAZA - SAN MARTIN',
    'I',
    'Carrera 20 No. 43-63, barrio San Martin, Valledupar, Cesar',
    '6055840179',
    10.446380,
    -73.247410,
    NULL,
    'A'
FROM dual
WHERE NOT EXISTS (
    SELECT 1
    FROM hospital
    WHERE nombre = 'HOSPITAL EDUARDO ARREDONDO DAZA - SAN MARTIN'
);

COMMIT;

-- ============================================================
-- 3. SERVICIOS HOSPITALARIOS
-- Disponibilidad marcada como N hasta contar con confirmacion.
-- No representa disponibilidad hospitalaria en tiempo real.
-- ============================================================

-- Servicios asociados al Hospital Rosario Pumarejo de Lopez
INSERT INTO servicio_hospital (
    id_hospital,
    nombre_servicio,
    disponible,
    capacidad
)
SELECT
    h.id_hospital,
    s.nombre_servicio,
    'N',
    NULL
FROM hospital h
CROSS JOIN (
    SELECT 'URGENCIAS' AS nombre_servicio FROM dual
    UNION ALL
    SELECT 'HOSPITALIZACION' FROM dual
    UNION ALL
    SELECT 'CIRUGIA' FROM dual
) s
WHERE h.nombre = 'HOSPITAL ROSARIO PUMAREJO DE LOPEZ'
  AND NOT EXISTS (
      SELECT 1
      FROM servicio_hospital sh
      WHERE sh.id_hospital = h.id_hospital
        AND sh.nombre_servicio = s.nombre_servicio
  );

-- Servicios asociados al Hospital Eduardo Arredondo Daza - San Martin
INSERT INTO servicio_hospital (
    id_hospital,
    nombre_servicio,
    disponible,
    capacidad
)
SELECT
    h.id_hospital,
    s.nombre_servicio,
    'N',
    NULL
FROM hospital h
CROSS JOIN (
    SELECT 'URGENCIAS' AS nombre_servicio FROM dual
    UNION ALL
    SELECT 'CONSULTA EXTERNA' FROM dual
    UNION ALL
    SELECT 'HOSPITALIZACION' FROM dual
) s
WHERE h.nombre = 'HOSPITAL EDUARDO ARREDONDO DAZA - SAN MARTIN'
  AND NOT EXISTS (
      SELECT 1
      FROM servicio_hospital sh
      WHERE sh.id_hospital = h.id_hospital
        AND sh.nombre_servicio = s.nombre_servicio
  );

COMMIT;
