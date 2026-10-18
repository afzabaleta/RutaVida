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
