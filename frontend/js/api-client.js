
// Single place where the pages talk to the RutaVida REST API.

const TOKEN_KEY = "rutavida.token";

/*
 * Local development is same-origin: WebServer serves both the pages and /api.
 * The Vercel build injects the Render origin in a meta tag, keeping an API URL
 * (and any credentials) out of the source repository.
 */
function configuredApiBaseUrl() {
  const configured = document.querySelector('meta[name="rutavida-api-base"]')?.content?.trim();
  return configured ? configured.replace(/\/+$/, "") : "";
}

const API_BASE_URL = configuredApiBaseUrl();

function apiUrl(path) {
  return API_BASE_URL ? `${API_BASE_URL}${path}` : path;
}

/** Returns the matching secure WebSocket URL for the deployed API. */
export function apiWebSocketUrl(path) {
  const url = new URL(path, API_BASE_URL || window.location.origin);
  url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
  return url.toString();
}

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

// The staff token lives in sessionStorage: closing the tab logs out. The citizen token of
// the panic button (DT-13) lives in localStorage: the phone stays logged in for 30 days.
function readToken() {
  try {
    return sessionStorage.getItem(TOKEN_KEY) || localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

function saveToken(token, keepOnThisDevice = false) {
  try {
    clearToken();
    (keepOnThisDevice ? localStorage : sessionStorage).setItem(TOKEN_KEY, token);
  } catch {
    // Session lasts only while this page is open.
  }
}

function clearToken() {
  try {
    sessionStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(TOKEN_KEY);
  } catch {
    // Nothing to clear.
  }
}

export function hasToken() {
  return Boolean(readToken());
}

/** Token for the same-origin GPS WebSocket. It is sent in the first frame, never in its URL. */
export function gpsSocketToken() {
  return readToken();
}

async function call(method, path, body) {
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
    response = await fetch(apiUrl(path), {
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
    return answer;
  }

  if (response.status === 401) {
    clearToken();
  }

  throw new ApiError(
    response.status,
    answer?.message || GENERIC_ERROR
  );
}

async function request(method, path, body) {
  return (await call(method, path, body)).value;
}
export async function login(username, password) {
  const session = await request("POST", "/api/auth/login", {
    username,
    password,
  });

  saveToken(session.token, session.role === "CIUDADANO");
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

// ------------------------------------------------------------------ D10: history and reports

/** Build query parameters for a period. */
function periodQuery(from, to) {
  const params = new URLSearchParams();
  if (from) params.set("from", from);
  if (to) params.set("to", to);
  return params.toString();
}

/** Operations report calculated in Oracle. */
export function getOperationsReport(from, to) {
  return request("GET", `/api/reports/operations?${periodQuery(from, to)}`);
}

/** All emergencies in the period, including closed ones. */
export async function listEmergenciesByPeriod(from, to) {
  const query = periodQuery(from, to) || `to=${encodeURIComponent(todayIso())}`;
  const [types, emergencies] = await Promise.all([
    listEmergencyTypes(),
    request("GET", `/api/emergencies?${query}`),
  ]);

  return emergencies.map((emergency) => ({
    ...toEmergency(emergency, types),
    closedAt: emergency.closedAt,
  }));
}

/** Complete history of an emergency. */
export function getEmergencyHistory(id) {
  return request("GET", `/api/emergencies/${encodeURIComponent(id)}/history`);
}

/** Local date in YYYY-MM-DD format. */
export function todayIso(offsetDays = 0) {
  const day = new Date();
  day.setDate(day.getDate() + offsetDays);
  const month = String(day.getMonth() + 1).padStart(2, "0");
  const date = String(day.getDate()).padStart(2, "0");
  return `${day.getFullYear()}-${month}-${date}`;
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

/**
 * Hospitals for the transfer of a mission EN_SITIO (docs/api.md 6.5), the most convenient
 * first (RN-10), with location as [lat, lng].
 */
export async function listTransferHospitals(assignmentId) {
  const hospitals = await request(
    "GET",
    `/api/dispatches/${encodeURIComponent(assignmentId)}/hospitals`
  );
  return hospitals.map((hospital) => ({ ...hospital, location: toPoint(hospital.location) }));
}

/** Starts the transfer to the chosen hospital (6.6): the mission goes TRASLADANDO. */
export function startTransfer(assignmentId, hospitalId) {
  return request(
    "PATCH",
    `/api/dispatches/${encodeURIComponent(assignmentId)}/status`,
    { status: "TRASLADANDO", hospitalId }
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

/**
 * Live map tracking (D7).
 * Updates every 5 seconds without extending the session.
 */
export async function getTracking() {
  const snapshot = await request("GET", "/api/tracking");

  return {
    ...snapshot,
    units: snapshot.units.map((unit) => ({
      ...unit,
      location: toPoint(unit.location),
      example: unit.unitCode.startsWith("EJEMPLO-"),
    })),
  };
}

// ------------------------------------------------------------------ real GPS of the assigned field driver

/** The single emergency unit that the signed-in driver may publish from their phone. */
export function getDriverUnit() {
  return request("GET", "/api/gps/unit");
}

/** Sends one browser Geolocation reading. Browser speed is m/s; RutaVida stores km/h. */
export function reportGpsPosition({ latitude, longitude, speedMps, headingDegrees }) {
  return request("POST", "/api/gps/position", {
    location: { latitude: round6(latitude), longitude: round6(longitude) },
    speedKmh: Number.isFinite(speedMps) ? Math.round(speedMps * 3.6 * 10) / 10 : null,
    headingDegrees: Number.isFinite(headingDegrees) ? Math.round(headingDegrees * 10) / 10 : null,
  });
}

/** The driver's own open mission and its emergency, never the rest of the fleet. */
export function getDriverMission() {
  return request("GET", "/api/gps/mission");
}

/** The active or proposed routes of the authenticated driver's own open mission. */
export async function listDriverMissionRoutes() {
  const routes = await request("GET", "/api/gps/mission/routes");
  return routes.map(toRoute);
}

/** Registers the first confirmed GPS reading of a sharing session. */
export function startDriverGpsSession() {
  return request("POST", "/api/gps/session/start");
}

/** Registers a manual GPS stop; it never changes the mission state. */
export function stopDriverGpsSession() {
  return request("POST", "/api/gps/session/stop");
}

/** Confirms arrival of the unit assigned to the logged-in driver. */
export function confirmDriverArrival() {
  return request("POST", "/api/gps/mission/arrival");
}

/** Finishes attention for the driver's assigned mission without stopping GPS. */
export function finishDriverAttention() {
  return request("POST", "/api/gps/mission/finish");
}

/** Lists alerts of the current user. */
export function listAlerts(onlyUnread = false) {
  return request(
    "GET",
    onlyUnread ? "/api/alerts?unread=true" : "/api/alerts"
  );
}

/** Marks an alert as read. */
export function markAlertRead(alertId) {
  return request(
    "PATCH",
    `/api/alerts/${encodeURIComponent(alertId)}/read`
  );
}


// ------------------------------------------------------------------ panic button (D9)

/** Public emergency types for the panic button. */
export async function listPanicEmergencyTypes() {
  const types = await request("GET", "/api/panic/emergency-types");
  return types.map((type) => ({ ...type, label: typeLabel(type.name) }));
}

/** Sends a panic alert and keeps the server response message. */
export async function sendPanicAlert({ deviceId, location, emergencyTypeId, description }) {
  const answer = await call("POST", "/api/panic", {
    deviceId,
    location: {
      latitude: round6(location[0]),
      longitude: round6(location[1]),
    },
    emergencyTypeId: emergencyTypeId ?? null,
    description: description || null,
  });
  return { ...answer.value, message: answer.message };
}

/** Lists panic reports with Leaflet-compatible coordinates. */
export async function listPanicReports() {
  const reports = await request("GET", "/api/panic/reports");
  return reports.map((report) => ({
    ...report,
    location: toPoint(report.location),
  }));
}

/** Discards a pending false alarm. */
export function discardPanicReport(reportId) {
  return request(
    "PATCH",
    `/api/panic/reports/${encodeURIComponent(reportId)}/status`,
    { status: "DESCARTADO" }
  );
}

/**
 * A person creates the citizen account of the panic button (CU-23, docs/api.md 16.5).
 * Public: it works without a session. Returns the new user and the message of the server.
 */
export async function registerCitizen(form) {
  const answer = await call("POST", "/api/citizens", {
    document: form.document,
    firstNames: form.firstNames,
    lastNames: form.lastNames,
    email: form.email,
    username: form.username,
    password: form.password,
    dataConsent: form.dataConsent === true,
  });
  return { user: answer.value, message: answer.message };
}

/** Where a session starts working: the button for citizens and police, the panel for the staff. */
export function homeOf(session) {
  if (session?.role === "ADMINISTRADOR") return "./dashboard.html";
  return hasPermission(session, "GPS:REPORTAR")
    ? "./conductor.html"
    : hasPermission(session, "PANICO:REPORTAR") && !hasPermission(session, "DESPACHO:GESTIONAR")
    ? "./panico.html"
    : "./dashboard.html";
}


// ------------------------------------------------------------------ D12: simulation and traffic

/** State of the simulation: { running, startedAt, tickSeconds, source } (docs/api.md, 10.1). */
export function getSimulation() {
  return request("GET", "/api/simulation");
}

/** Starts the simulation; starting one that runs is not an error (docs/api.md, 10.2). */
export function startSimulation() {
  return request("POST", "/api/simulation/start");
}

/** Stops the simulation; the units stay where they are (docs/api.md, 10.2). */
export function stopSimulation() {
  return request("POST", "/api/simulation/stop");
}

/** Forces the traffic of one segment for the demo: FLUIDO, MODERADO or CONGESTIONADO (10.3). */
export function forceTraffic(segmentId, level) {
  return request("POST", "/api/simulation/traffic", { segmentId, level });
}

/** Segments with MODERADO or CONGESTIONADO traffic now, with both ends (docs/api.md, 8.2). */
export async function listTrafficSegments() {
  const segments = await request("GET", "/api/traffic/segments");
  return segments.map((segment) => ({
    ...segment,
    from: toPoint(segment.from),
    to: toPoint(segment.to),
  }));
}

// ------------------------------------------------------------------ Administration (docs/api.md 15)

/** Users, active or not, without password (15.1). */
export function listUsers() {
  return request("GET", "/api/users");
}

/** Active roles with their permissions (15.4). */
export function listRoles() {
  return request("GET", "/api/roles");
}

export function createUser(user) {
  return request("POST", "/api/users", user);
}

export function listVehicleTypes() {
  return request("GET", "/api/vehicle-types");
}

/** Units as the API sends them (7.2): ids of type and station, plate and status. */
export function listUnitRecords() {
  return request("GET", "/api/units");
}

/** Every station, active or not (7.3). */
export function listAllStations() {
  return request("GET", "/api/stations");
}

/** Every hospital, active or not (7.4 with ?all=true). */
export function listAllHospitals() {
  return request("GET", "/api/hospitals?all=true");
}

const ADMIN_COLLECTIONS = ["users", "units", "stations", "hospitals"];

function adminPath(collection, id) {
  if (!ADMIN_COLLECTIONS.includes(collection)) {
    throw new Error(`Colección desconocida: ${collection}`);
  }
  return id ? `/api/${collection}/${encodeURIComponent(id)}` : `/api/${collection}`;
}

/** Creates (POST, without id) or edits (PUT, with id) a unit, station or hospital (15.6 to 15.11). */
export function saveRecord(collection, id, body) {
  return request(id ? "PUT" : "POST", adminPath(collection, id), body);
}

/** PATCH .../{id}/status: A or I for users, stations and hospitals; the operational status for units. */
export function changeRecordStatus(collection, id, status) {
  return request("PATCH", `${adminPath(collection, id)}/status`, { status });
}


 // ------------------------------------------------------------------ Report a road incident (CU-13)

/**
 * The street segment closest to a point of the map (docs/api.md 9.4), with its two ends as
 * [lat, lng]. reverseSegmentId is the same street in the other direction (null if one-way).
 */
export async function findNearestSegment(latitude, longitude) {
    const query = `lat=${encodeURIComponent(latitude)}&lon=${encodeURIComponent(longitude)}`;
    const segment = await request("GET", `/api/road/segments/nearest?${query}`);
    return { ...segment, path: [toPoint(segment.from), toPoint(segment.to)] };
}

/** Registers a road incident (9.2): it enters PENDIENTE and does not change any route yet. */
export function reportIncident({ segmentId, type, severity, blocksSegment, description }) {
    return request("POST", "/api/incidents", { segmentId, type, severity, blocksSegment, description });
}

// ------------------------------------------------------------------ Audit (CU-21)

/**
 * Entries of AUDITORIA (docs/api.md 15.13), the newest first.
 */
export function listAudit(filters = {}) {
    const query = new URLSearchParams();
    for (const [name, value] of Object.entries(filters)) {
        if (value !== null && value !== undefined && String(value).trim() !== "") {
            query.set(name, String(value).trim());
        }
    }
    const text = query.toString();
    return request("GET", `/api/audit${text ? `?${text}` : ""}`);
}
