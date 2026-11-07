
import { EXAMPLE_EMERGENCIES, EXAMPLE_UNITS } from "./example-data.js";

export const USE_EXAMPLES = true;

const EMERGENCY_TYPES = [
  { name: "EMERGENCIA_MEDICA", label: "Emergencia médica", priority: 9 },
  { name: "INCENDIO", label: "Incendio", priority: 9 },
  { name: "ACCIDENTE_VIAL", label: "Accidente de tránsito", priority: 8 },
  { name: "RESCATE", label: "Rescate", priority: 7 },
];

export const SEVERITIES = [
  { code: "BAJA", label: "Baja" },
  { code: "MEDIA", label: "Media" },
  { code: "ALTA", label: "Alta" },
  { code: "CRITICA", label: "Crítica" },
];

const STORAGE_KEY = "rutavida.ejemplo.emergencias";

function readRegistered() {
  try {
    return JSON.parse(sessionStorage.getItem(STORAGE_KEY) ?? "[]");
  } catch {
    return [];
  }
}

function writeRegistered(list) {
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(list));
  } catch {
    // Storage blocked: the example still works for this page.
  }
}

function copy(value) {
  return JSON.parse(JSON.stringify(value));
}

export function typeLabel(name) {
  return EMERGENCY_TYPES.find((type) => type.name === name)?.label ?? name;
}

export async function listEmergencyTypes() {
  return copy(EMERGENCY_TYPES);
}

export async function listEmergencies() {
  return copy([...EXAMPLE_EMERGENCIES, ...readRegistered()])
    .map((emergency) => ({ ...emergency, example: true }));
}

export async function findEmergency(code) {
  const all = await listEmergencies();
  return all.find((emergency) => emergency.code === code) ?? null;
}

export async function registerEmergency(request) {
  const registered = readRegistered();

  const emergency = {
    code: `EJ-${String(101 + registered.length)}`,
    type: request.type,
    severity: request.severity,
    status: "REPORTADA",
    place: request.address,
    description: request.description || null,
    location: [request.latitude, request.longitude],
  };

  registered.push(emergency);
  writeRegistered(registered);

  return { ...copy(emergency), example: true };
}

export async function listUnits() {
  return copy(EXAMPLE_UNITS).map((unit) => ({
    ...unit,
    example: true,
  }));
}

export async function dispatchUnit(emergencyCode, unitCode) {
  const registered = readRegistered();
  const own = registered.find((emergency) => emergency.code === emergencyCode);

  if (own) {
    own.status = "ASIGNADA";
    own.unit = unitCode;
    writeRegistered(registered);
  }

  return {
    emergencyCode,
    unitCode,
    status: "ASIGNADA",
    example: true,
  };
}
