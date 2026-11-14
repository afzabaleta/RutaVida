
// Single place where the pages talk to the RutaVida REST API.

const TOKEN_KEY = "rutavida.token";

const GENERIC_ERROR = "Ocurrió un error inesperado. Intente de nuevo.";
const NO_SERVER = "No se pudo conectar con el servidor de RutaVida. Revisa que WebServer esté encendido.";

const TYPE_LABELS = {
  EMERGENCIA_MEDICA: "Emergencia médica",
  INCENDIO: "Incendio",
  ACCIDENTE_VIAL: "Accidente de tránsito",
  RESCATE: "Rescate",
};

export const SEVERITIES = [
  { code: "BAJA", label: "Baja" },
  { code: "MEDIA", label: "Media" },
  { code: "ALTA", label: "Alta" },
  { code: "CRITICA", label: "Crítica" },
];

export class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

function readToken() {
  try {
    return sessionStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

function saveToken(token) {
  try {
    sessionStorage.setItem(TOKEN_KEY, token);
  } catch {
    // Session lasts only while this page is open.
  }
}

function clearToken() {
  try {
    sessionStorage.removeItem(TOKEN_KEY);
  } catch {
    // Nothing to clear.
  }
}

export function hasToken() {
  return Boolean(readToken());
}

async function request(method, path, body) {
  const headers = { Accept: "application/json" };
  const token = readToken();

  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }

  let response;

  try {
    response = await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, NO_SERVER);
  }

  let answer = null;

  try {
    answer = await response.json();
  } catch {
    // Not JSON.
  }

  if (response.ok && answer?.success === true) {
    return answer.value;
  }

  if (response.status === 401) {
    clearToken();
  }

  throw new ApiError(
    response.status,
    answer?.message || GENERIC_ERROR
  );
}

export async function login(username, password) {
  const session = await request("POST", "/api/auth/login", {
    username,
    password,
  });

  saveToken(session.token);
  return session;
}

export async function logout() {
  try {
    if (hasToken()) {
      await request("POST", "/api/auth/logout");
    }
  } catch {
    // Token is cleared anyway.
  } finally {
    clearToken();
  }
}

export async function currentSession() {
  if (!hasToken()) {
    return null;
  }

  try {
    return await request("GET", "/api/auth/session");
  } catch (error) {
    if (error.status === 401) {
      return null;
    }
    throw error;
  }
}

export function hasPermission(session, permission) {
  return Boolean(session?.permissions?.includes(permission));
}

function toPoint(location) {
  return location
    ? [location.latitude, location.longitude]
    : null;
}

function round6(value) {
  return Math.round(value * 1e6) / 1e6;
}

export function typeLabel(name) {
  return TYPE_LABELS[name] ?? String(name ?? "").replaceAll("_", " ");
}

let typesPromise = null;

export function listEmergencyTypes() {
  if (!typesPromise) {
    typesPromise = request("GET", "/api/emergency-types")
      .then((types) =>
        types.map((type) => ({
          ...type,
          label: typeLabel(type.name),
        }))
      )
      .catch((error) => {
        typesPromise = null;
        throw error;
      });
  }

  return typesPromise;
}

function toEmergency(emergency, types) {
  const type = types.find(
    (item) => item.id === emergency.emergencyTypeId
  );

  return {
    id: emergency.id,
    code: `#${emergency.id}`,
    type: type?.name ?? `TIPO_${emergency.emergencyTypeId}`,
    severity: emergency.severity,
    status: emergency.status,
    place: emergency.addressReference,
    description: emergency.description,
    location: toPoint(emergency.location),
    reportedAt: emergency.reportedAt,
  };
}

export async function listEmergencies() {
  const [types, emergencies] = await Promise.all([
    listEmergencyTypes(),
    request("GET", "/api/emergencies"),
  ]);

  return emergencies.map((emergency) =>
    toEmergency(emergency, types)
  );
}

export async function findEmergency(id) {
  try {
    const [types, emergency] = await Promise.all([
      listEmergencyTypes(),
      request(
        "GET",
        `/api/emergencies/${encodeURIComponent(id)}`
      ),
    ]);

    return toEmergency(emergency, types);
  } catch (error) {
    if (error.status === 404 || error.status === 400) {
      return null;
    }
    throw error;
  }
}

export async function registerEmergency(form) {
  const [types, emergency] = await Promise.all([
    listEmergencyTypes(),
    request("POST", "/api/emergencies", {
      emergencyTypeId: Number(form.typeId),
      addressReference: form.address,
      location: {
        latitude: round6(form.latitude),
        longitude: round6(form.longitude),
      },
      severity: form.severity,
      description: form.description || null,
    }),
  ]);

  return toEmergency(emergency, types);
}

export async function listUnits() {
  const [units, vehicleTypes, stations] = await Promise.all([
    request("GET", "/api/units"),
    request("GET", "/api/vehicle-types"),
    request("GET", "/api/stations"),
  ]);

  return units.map((unit) => ({
    id: unit.id,
    code: unit.unitCode,
    type:
      vehicleTypes.find(
        (type) => type.id === unit.vehicleTypeId
      )?.name ?? "UNIDAD",
    status: unit.operationalStatus,
    place:
      stations.find(
        (station) => station.id === unit.stationId
      )?.name ?? "Sin estación",
    location: toPoint(unit.lastLocation),
    example: unit.unitCode.startsWith("EJEMPLO-"),
  }));
}

/** Active stations and bases (docs/api.md, 7.3), with location as [lat, lng]. */
export async function listStations() {
  const stations = await request("GET", "/api/stations");
  return stations
    .filter((station) => station.status === "A")
    .map((station) => ({ ...station, location: toPoint(station.location) }));
}

/** Active hospitals (docs/api.md, 7.4), with location as [lat, lng]. */
export async function listHospitals() {
  const hospitals = await request("GET", "/api/hospitals");
  return hospitals.map((hospital) => ({
    ...hospital,
    location: toPoint(hospital.location),
  }));
}

/** Indicators of the main panel, counted in Oracle (docs/api.md, 11.1). */
export function getDashboardSummary() {
  return request("GET", "/api/dashboard/summary");
}

const INCIDENT_LABELS = {
  ACCIDENTE: "Accidente",
  OBRA: "Obra",
  CIERRE: "Cierre de vía",
  MANIFESTACION: "Manifestación",
  OTRO: "Incidente",
};

export function incidentLabel(code) {
  return INCIDENT_LABELS[code] ?? String(code ?? "").replaceAll("_", " ");
}

/** A road incident with its segment as two [lat, lng] ends (docs/api.md, 9.1). */
function toIncident(incident) {
  const segment = incident.segment;

  return {
    id: incident.id,
    segmentId: incident.segmentId,
    type: incident.type,
    severity: incident.severity,
    blocksSegment: incident.blocksSegment,
    description: incident.description,
    startedAt: incident.startedAt,
    status: incident.status,
    origin: incident.origin,
    streetName: segment?.streetName ?? null,
    path: segment ? [toPoint(segment.from), toPoint(segment.to)] : null,
  };
}

/** Open road incidents: PENDIENTE and ACTIVO (docs/api.md, 9.1). */
export async function listIncidents() {
  const incidents = await request("GET", "/api/incidents");
  return incidents.map(toIncident);
}

/** Validates (ACTIVO), rejects (RECHAZADO) or closes (CERRADO) an incident (docs/api.md, 9.3). */
export async function changeIncidentStatus(id, status) {
  const incident = await request(
    "PATCH",
    `/api/incidents/${encodeURIComponent(id)}/status`,
    { status }
  );

  return toIncident(incident);
}

/** A route of the engine with its path as [lat, lng] points, ready for L.polyline (6.2 and 6.4). */
function toRoute(route) {
  return {
    ...route,
    path: (route.path ?? []).map(toPoint),
  };
}

/** Units that can attend the emergency, the fastest first (docs/api.md, 6.1). */
export async function listCandidates(emergencyId) {
  const units = await request(
    "GET",
    `/api/emergencies/${encodeURIComponent(emergencyId)}/candidates`
  );

  return units.map((unit) => ({
    ...unit,
    location: toPoint(unit.location),
    example: unit.unitCode.startsWith("EJEMPLO-"),
  }));
}

/** Up to 3 routes from the unit to the emergency, without saving anything (docs/api.md, 6.2). */
export async function previewRoutes(emergencyId, vehicleId) {
  const routes = await request("POST", "/api/routes/preview", {
    emergencyId,
    vehicleId,
  });

  return routes.map(toRoute);
}

/** Dispatches the unit in one transaction and returns the mission with its routes (6.3). */
export async function dispatchUnit(emergencyId, vehicleId) {
  const mission = await request("POST", "/api/dispatches", {
    emergencyId,
    vehicleId,
  });

  return {
    assignment: mission.assignment,
    routes: mission.routes.map(toRoute),
  };
}

/** The routes of a mission that are still in use: ACTIVA and PROPUESTA (6.4). */
export async function listMissionRoutes(assignmentId) {
  const routes = await request(
    "GET",
    `/api/dispatches/${encodeURIComponent(assignmentId)}/routes`
  );

  return routes.map(toRoute);
}

/** Moves the mission: EN_RUTA, EN_SITIO, FINALIZADA or CANCELADA (6.6). */
export function changeMissionStatus(assignmentId, status) {
  return request(
    "PATCH",
    `/api/dispatches/${encodeURIComponent(assignmentId)}/status`,
    { status }
  );
}

/** The open assignment of an emergency, read from its history (5.5), or null. */
export async function findOpenAssignment(emergencyId) {
  const history = await request(
    "GET",
    `/api/emergencies/${encodeURIComponent(emergencyId)}/history`
  );

  return history.assignments.find(
    (assignment) => !["FINALIZADA", "CANCELADA"].includes(assignment.status)
  ) ?? null;
}

/** Closes the emergency with sp_cerrar_emergencia (6.7) and returns it CERRADA. */
export async function closeEmergency(emergencyId) {
  const [types, emergency] = await Promise.all([
    listEmergencyTypes(),
    request(
      "POST",
      `/api/emergencies/${encodeURIComponent(emergencyId)}/close`
    ),
  ]);

  return toEmergency(emergency, types);
}
