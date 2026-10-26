// EXAMPLE data for the mockup. It is NOT real and NOT calculated by the system.
// Every place where it appears on screen must carry the EJEMPLO label.
// Types and statuses use the same codes as Oracle.

export const EXAMPLE_USER = {
  name: "Usuario de ejemplo",
  role: "OPERADOR_CENTRAL",
};

export const EXAMPLE_EMERGENCIES = [
  {
    code: "EJ-001",
    type: "EMERGENCIA_MEDICA",
    severity: "CRITICA",
    status: "REPORTADA",
    place: "Parque La Popa (escenario de la demostración)",
    location: [10.467172, -73.265100],
  },
  {
    code: "EJ-002",
    type: "ACCIDENTE_VIAL",
    severity: "ALTA",
    status: "ASIGNADA",
    place: "Ubicación inventada para la maqueta",
    location: [10.456000, -73.250000],
  },
];

export const EXAMPLE_UNITS = [
  {
    code: "AMB-EJ-01",
    type: "AMBULANCIA",
    status: "DISPONIBLE",
    place: "Junto a la Estacion de Bomberos",
    location: [10.468300, -73.257200],
  },
  {
    code: "BOM-EJ-01",
    type: "BOMBERO",
    status: "OCUPADO",
    place: "Atendiendo EJ-002",
    location: [10.456500, -73.250600],
  },
];
