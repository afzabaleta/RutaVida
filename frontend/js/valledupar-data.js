// Real places of Valledupar, copied from database/03_datos_valledupar.sql.
// Every place keeps its source. Keep this file synchronized with D2.

export const SOURCES_CHECKED_ON = "2026-11-19";

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
  // Added by database/10_hospitales_y_unidades.sql (DT-14): position from OpenStreetMap.
  {
    name: "CLINICA MEDICOS ALTA COMPLEJIDAD DEL CARIBE",
    level: "III",
    address: "Calle 14 No. 17-47, Valledupar, Cesar",
    location: [10.471992, -73.255195],
    source: "https://www.openstreetmap.org/way/1317791406",
    note: "Nivel III pendiente de confirmar con una fuente oficial",
  },
  {
    name: "CLINICA DEL CESAR",
    level: "II",
    address: "Calle 16 No. 14-90, Valledupar, Cesar",
    location: [10.472254, -73.252288],
    source: "https://www.openstreetmap.org/way/687405295",
    note: "Nivel II pendiente de confirmar con una fuente oficial",
  },
  {
    name: "CLINICA VALLEDUPAR",
    level: "II",
    address: "Calle 16 No. 15-15, Valledupar, Cesar",
    location: [10.471413, -73.252342],
    source: "https://www.openstreetmap.org/way/687405515",
    note: "Nivel II pendiente de confirmar con una fuente oficial",
  },
  {
    name: "HOSPITAL EDUARDO ARREDONDO DAZA - LA NEVADA",
    level: "I",
    address: "Barrio La Nevada, Comuna 5, Valledupar, Cesar; direccion exacta no verificada",
    location: [10.476582, -73.283043],
    source: "https://www.openstreetmap.org/node/13950981456",
    note: "Direccion exacta no verificada",
  },
  {
    name: "HOSPITAL EDUARDO ARREDONDO DAZA - SEDE ORIENTE",
    level: "I",
    address: "Valledupar, Cesar; sede y direccion exacta no verificadas (OpenStreetMap)",
    location: [10.458312, -73.231835],
    source: "https://www.openstreetmap.org/way/687434990",
    note: "Nombre de la sede y direccion no verificados",
  },
];

// Zone of the road network loaded from OpenStreetMap: the urban area (DT-14).
export const ROAD_NETWORK_ZONE = {
  south: 10.405,
  west: -73.305,
  north: 10.510,
  east: -73.215,
};
