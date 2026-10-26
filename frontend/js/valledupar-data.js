// Real places of Valledupar, copied from database/03_datos_valledupar.sql.
// Every place keeps its source. Keep this file synchronized with D2.

export const SOURCES_CHECKED_ON = "2026-10-18";

export const STATIONS = [
  {
    name: "Estacion de Bomberos",
    address: "Ubicacion en Valledupar, Cesar; direccion exacta no verificada",
    location: [10.468580, -73.257590],
    source: "https://mapcarta.com/es/W687362314",
  },
];

export const HOSPITALS = [
  {
    name: "HOSPITAL ROSARIO PUMAREJO DE LOPEZ",
    level: "II",
    address: "Calle 16 No. 17-141, Valledupar, Cesar",
    location: [10.469330, -73.254520],
    source: "https://mapcarta.com/es/N6456494546",
    note: "Nivel II pendiente de confirmar con una fuente oficial",
  },
  {
    name: "HOSPITAL EDUARDO ARREDONDO DAZA - SAN MARTIN",
    level: "I",
    address: "Carrera 20 No. 43-63, barrio San Martin, Valledupar, Cesar",
    location: [10.446380, -73.247410],
    source: "https://mapcarta.com/es/W687415468",
    note: null,
  },
];

// Zone of the road network loaded from OpenStreetMap (K4).
export const ROAD_NETWORK_ZONE = {
  south: 10.435,
  west: -73.280,
  north: 10.480,
  east: -73.235,
};
