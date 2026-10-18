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
    'Avenida Pedro Castro Monsalvo, Valledupar, Cesar',
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
      AND direccion = 'Avenida Pedro Castro Monsalvo, Valledupar, Cesar'
);

COMMIT;

-- ============================================================
-- 2. HOSPITALES DE VALLEDUPAR
-- Direcciones verificadas en fuentes institucionales.
-- Coordenadas consultadas en OpenStreetMap mediante Mapcarta.
-- ============================================================

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
    'III',
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
