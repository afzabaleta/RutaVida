import {
  apiWebSocketUrl, confirmDriverArrival, finishDriverAttention, getDriverMission, getDriverUnit,
  gpsSocketToken, hasPermission, listHospitals, listDriverMissionRoutes, listDriverTransferHospitals,
  listStations, reportGpsPosition, startDriverGpsSession, startDriverTransfer, stopDriverGpsSession,
} from "./api-client.js";
import { startPage } from "./page-session.js";

const EVERY_MS = 5000;
const MOVING_UPDATE_MS = 2500;
const MOVEMENT_METERS = 15;
const REVERSE_GEOCODE_MS = 20000;
const ADDRESS_MOVE_METERS = 35;
const NOMINATIM_REVERSE = "https://nominatim.openstreetmap.org/reverse";
const VALLEDUPAR = [10.4631, -73.2532];
const $ = (id) => document.getElementById(id);

let watchId = null;
let timer = null;
let latest = null;
let publication = null;
let lastPublishedPoint = null;
let lastPublishAt = 0;
let stopping = false;
let tracking = false;
let trackingVersion = 0;
let gpsSessionRecorded = false;
let addressAt = 0;
let addressPosition = null;
let addressRequest = 0;
let canOperate = false;
let currentMission = null;
let missionRefreshing = false;
let missionRefreshQueued = false;
let missionRevision = 0;
let operationPending = false;
let transferQuery = 0;
let routeRequest = 0;
let routeLoadingKey = null;
let lastRouteKey = null;
let lastRouteDestination = null;
let map = null;
let ownMarker = null;
let emergencyMarker = null;
let routeLine = null;
let hospitals = [];
let resources = [];
let resourceLayer = null;
let selectedHospitalId = null;
let mapFitted = false;
let completedMission = null;

function status(text, state = "idle") {
  $("gps-status").textContent = text;
  $("gps-status").dataset.state = state;
  $("driver-map-state").textContent = state === "online" ? "GPS en vivo" : text === "Desconectado" ? "GPS detenido" : text;
  $("driver-map-state").classList.toggle("is-live", state === "online");
}

function message(text, error = false) {
  $("gps-message").textContent = text;
  $("gps-message").classList.toggle("is-error", error);
}

function compass(degrees) {
  if (!Number.isFinite(degrees)) return "No disponible";
  const names = ["N", "NE", "E", "SE", "S", "SO", "O", "NO"];
  return `${names[Math.round(degrees / 45) % 8]} (${Math.round(degrees)}°)`;
}

function distanceMeters(a, b) {
  if (!a || !b) return Number.POSITIVE_INFINITY;
  const radius = 6371000;
  const radians = (degrees) => degrees * Math.PI / 180;
  const dLat = radians(b.latitude - a.latitude);
  const dLng = radians(b.longitude - a.longitude);
  const h = Math.sin(dLat / 2) ** 2
    + Math.cos(radians(a.latitude)) * Math.cos(radians(b.latitude))
    * Math.sin(dLng / 2) ** 2;
  return 2 * radius * Math.asin(Math.sqrt(h));
}

function distanceKm(a, b) {
  return distanceMeters({ latitude: a[0], longitude: a[1] }, { latitude: b[0], longitude: b[1] }) / 1000;
}

function displayAddress(result) {
  const address = result?.address ?? {};
  const road = String(address.road ?? address.pedestrian ?? address.footway ?? address.path ?? "")
    .replace(/^carrera\s+/i, "Cra. ")
    .replace(/^calle\s+/i, "Cl. ");
  const number = address.house_number;
  const line = [road || null, number ? `#${number}` : null].filter(Boolean).join(" ");
  const city = address.city ?? address.town ?? address.village ?? address.municipality ?? "Valledupar";
  const region = address.state ?? address.county ?? "Cesar";
  return line ? `${line}\n${city}, ${region}` : `${city}, ${region}`;
}

async function updateAddress(position) {
  const point = { latitude: position.coords.latitude, longitude: position.coords.longitude };
  const now = Date.now();
  if (now - addressAt < REVERSE_GEOCODE_MS && distanceMeters(addressPosition, point) < ADDRESS_MOVE_METERS) return;

  addressAt = now;
  addressPosition = point;
  const request = ++addressRequest;
  try {
    const query = new URLSearchParams({ format: "jsonv2", lat: String(point.latitude), lon: String(point.longitude), zoom: "18", addressdetails: "1", "accept-language": "es" });
    const response = await fetch(`${NOMINATIM_REVERSE}?${query}`, { headers: { Accept: "application/json" } });
    if (!response.ok) throw new Error("No se pudo consultar la dirección.");
    const result = await response.json();
    if (request === addressRequest) $("gps-address").textContent = displayAddress(result);
  } catch {
    const visibleAddress = $("gps-address").textContent.trim();
    if (request === addressRequest && (!visibleAddress || visibleAddress === "—")) $("gps-address").textContent = "Dirección no disponible";
  }
}

function driverIcon() {
  return L.divIcon({ html: '<div class="driver-map-unit"><span>🚑</span></div>', className: "", iconSize: [42, 42], iconAnchor: [21, 39] });
}

function emergencyIcon() {
  return L.divIcon({ html: '<div class="driver-map-emergency">!</div>', className: "", iconSize: [33, 33], iconAnchor: [16, 16] });
}

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>'"]/g, (character) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;",
  })[character]);
}

function resourceKind(resource) {
  if (resource.kind === "base") return "base";
  const name = String(resource.name ?? "");
  if (/cl[ií]nica/i.test(name)) return "clinic";
  if (/centro\s+(m[eé]dico|de salud|asistencial)|ips\b/i.test(name)) return "medical";
  return "hospital";
}

function resourceLabel(kind) {
  return {
    hospital: "Hospital", clinic: "Clínica", medical: "Centro médico", base: "Base operativa",
  }[kind];
}

function resourceGlyph(kind) {
  return { hospital: "+", clinic: "✚", medical: "♥", base: "▰" }[kind];
}

function isAmbulanceBase(station) {
  return !/bomberos?|polic[ií]a|defensa\s+civil/i.test(String(station.name ?? ""));
}

function secondaryIcon(kind, selected = false) {
  const size = map && map.getZoom() < 14 ? 11 : 15;
  return L.divIcon({
    html: `<span class="driver-map-place-icon driver-map-place-icon--${kind}${selected ? " is-selected" : ""}" aria-hidden="true">${resourceGlyph(kind)}</span>`,
    className: "driver-map-resource-icon",
    iconSize: [size, size],
    iconAnchor: [size / 2, size / 2],
    popupAnchor: [0, -size / 2],
  });
}

function clusterIcon(count) {
  return L.divIcon({
    html: `<span class="driver-map-resource-cluster" aria-label="${count} referencias médicas">${count}</span>`,
    className: "driver-map-resource-icon",
    iconSize: [24, 24],
    iconAnchor: [12, 12],
  });
}

function resourcePopup(resource) {
  const kind = resourceKind(resource);
  return `<strong>${escapeHtml(resource.name)}</strong><br><small>${resourceLabel(kind)}</small>${resource.address ? `<br>${escapeHtml(resource.address)}` : ""}${resource.level ? `<br>Nivel ${escapeHtml(resource.level)}` : ""}`;
}

function markerPoint(resource) {
  return resource.location;
}

function renderResources() {
  if (!map) return;
  if (!resourceLayer) resourceLayer = L.layerGroup().addTo(map);
  resourceLayer.clearLayers();

  const located = resources.filter((resource) => Array.isArray(resource.location));
  const showIndividuals = map.getZoom() >= 14 || located.length <= 6;
  if (showIndividuals) {
    for (const resource of located) {
      const marker = L.marker(markerPoint(resource), {
        icon: secondaryIcon(resourceKind(resource), resource.kind !== "base" && resource.id === selectedHospitalId),
        keyboard: false,
        zIndexOffset: resource.id === selectedHospitalId ? 450 : 120,
      }).bindPopup(resourcePopup(resource));
      marker.addTo(resourceLayer);
    }
    return;
  }

  const groups = new Map();
  for (const resource of located) {
    // A lightweight Leaflet-only cluster: it keeps the map legible without adding a dependency.
    const precision = map.getZoom() <= 11 ? 50 : 120;
    const key = `${Math.round(resource.location[0] * precision)}:${Math.round(resource.location[1] * precision)}`;
    const group = groups.get(key) ?? [];
    group.push(resource);
    groups.set(key, group);
  }
  for (const group of groups.values()) {
    if (group.length === 1) {
      const resource = group[0];
      L.marker(markerPoint(resource), { icon: secondaryIcon(resourceKind(resource)), keyboard: false, zIndexOffset: 120 })
        .bindPopup(resourcePopup(resource)).addTo(resourceLayer);
      continue;
    }
    const latitude = group.reduce((sum, resource) => sum + resource.location[0], 0) / group.length;
    const longitude = group.reduce((sum, resource) => sum + resource.location[1], 0) / group.length;
    L.marker([latitude, longitude], { icon: clusterIcon(group.length), keyboard: false, zIndexOffset: 130 })
      .bindTooltip(`${group.length} referencias médicas`, { direction: "top" })
      .on("click", () => map.setView([latitude, longitude], Math.max(map.getZoom() + 2, 14)))
      .addTo(resourceLayer);
  }
}

function createMap() {
  if (typeof L === "undefined") {
    $("driver-map").textContent = "No se pudo cargar el mapa.";
    return;
  }
  map = L.map("driver-map", { zoomControl: true }).setView(VALLEDUPAR, 13);
  L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", { maxZoom: 19 }).addTo(map);
  map.on("zoomend", renderResources);
}

function updateOwnMarker(location, focus = false) {
  if (!map || !location) return;
  if (!ownMarker) ownMarker = L.marker(location, { icon: driverIcon(), zIndexOffset: 1000 }).addTo(map).bindTooltip("Tu ambulancia", { direction: "top" });
  else ownMarker.setLatLng(location);
  if (focus || !mapFitted) {
    map.setView(location, Math.max(map.getZoom(), 15));
    mapFitted = true;
  }
}

function setEmergencyMarker(mission) {
  if (!map) return;
  if (!mission?.emergencyLocation) {
    emergencyMarker?.remove();
    emergencyMarker = null;
    return;
  }
  const label = `Emergencia #${mission.emergencyId}${mission.emergencyAddress ? ` · ${mission.emergencyAddress}` : ""}`;
  if (!emergencyMarker) emergencyMarker = L.marker([mission.emergencyLocation.latitude, mission.emergencyLocation.longitude], { icon: emergencyIcon() }).addTo(map).bindTooltip(label, { direction: "top" });
  else emergencyMarker.setLatLng([mission.emergencyLocation.latitude, mission.emergencyLocation.longitude]).bindTooltip(label);
}

function clearRoute() {
  routeRequest += 1;
  routeLoadingKey = null;
  routeLine?.remove();
  routeLine = null;
  lastRouteKey = null;
  lastRouteDestination = null;
}

async function drawRoute(mission, active) {
  const warning = $("driver-route-warning");
  const enRoute = ["ASIGNADA", "EN_RUTA", "TRASLADANDO"].includes(mission?.status);
  if (!map || !active || mission.tracking?.activeRouteId == null) {
    clearRoute();
    warning.hidden = !active || !enRoute;
    warning.textContent = warning.hidden ? "" : "Ruta no disponible. Informa a coordinación.";
    return;
  }
  if (mission.tracking.routeUnavailable) {
    warning.hidden = false;
    warning.textContent = "No se pudo recalcular desde tu ubicación. Se muestra el recorrido anterior.";
  } else if (warning.textContent.startsWith("No se pudo recalcular")) {
    warning.hidden = true;
  }
  const key = mission.assignmentId + ":" + mission.tracking.activeRouteId + ":" + mission.tracking.routesVersion;
  if (key === lastRouteKey || key === routeLoadingKey) return;
  if (lastRouteKey && !lastRouteKey.startsWith(mission.assignmentId + ":")) clearRoute();
  routeLoadingKey = key;
  const request = ++routeRequest;
  try {
    const routes = await listDriverMissionRoutes();
    if (request !== routeRequest) return;
    const route = routes.find((item) => item.routeId === mission.tracking.activeRouteId
      && item.status === "ACTIVA");
    if (!route?.path || route.path.length < 2) {
      throw new Error("La ruta activa todavía no está disponible.");
    }
    const nextLine = L.polyline(route.path, { color: "#3d8bff", weight: 6, opacity: .88 }).addTo(map);
    routeLine?.remove();
    routeLine = nextLine;
    lastRouteKey = key;
    const destination = route.destinationHospitalId == null
      ? "emergency:" + mission.emergencyId : "hospital:" + route.destinationHospitalId;
    if (lastRouteDestination !== destination) {
      map.fitBounds(routeLine.getBounds(), { padding: [32, 32], maxZoom: 16 });
      lastRouteDestination = destination;
    }
    if (route.destinationHospitalId != null && selectedHospitalId !== route.destinationHospitalId) {
      selectedHospitalId = route.destinationHospitalId;
      renderResources();
    }
    if (!mission.tracking.routeUnavailable) warning.hidden = true;
  } catch (error) {
    if (request !== routeRequest) return;
    warning.hidden = false;
    warning.textContent = "No se pudo mostrar la ruta actual. Informa a coordinación.";
    console.warn("No se pudo mostrar la ruta activa", error);
  } finally {
    if (request === routeRequest) routeLoadingKey = null;
  }
}

async function loadResources() {
  if (!map) return;
  const [hospitalResult, stationResult] = await Promise.allSettled([listHospitals(), listStations()]);
  hospitals = hospitalResult.status === "fulfilled"
    ? hospitalResult.value.filter((hospital) => hospital.location) : [];
  const bases = stationResult.status === "fulfilled"
    ? stationResult.value.filter((station) => station.location && isAmbulanceBase(station))
      .map((station) => ({ ...station, kind: "base" })) : [];
  resources = [...hospitals, ...bases];
  renderResources();
}

function formatTime(value) {
  return value ? new Date(value).toLocaleTimeString("es-CO", { hour: "2-digit", minute: "2-digit", second: "2-digit" }) : "—";
}

function formatDuration(start, end) {
  if (!start || !end) return "—";
  const seconds = Math.max(0, Math.round((new Date(end) - new Date(start)) / 1000));
  const minutes = Math.floor(seconds / 60);
  const hours = Math.floor(minutes / 60);
  return hours > 0 ? `${hours} h ${minutes % 60} min` : `${minutes} min ${seconds % 60} s`;
}

function renderMission(mission) {
  const isNew = Boolean(mission?.assignmentId) && mission.assignmentId !== currentMission?.assignmentId;
  currentMission = mission;
  const active = Boolean(mission?.assignmentId) && mission.status !== "FINALIZADA" && mission.status !== "CANCELADA";
  const status = mission?.status;
  setMissionButtons();
  if (!mission?.canTransfer && $("transfer-modal").open) $("transfer-modal").close();
  $("driver-emergency").hidden = !active;
  if (active) {
    const id = "EMG-" + mission.emergencyId;
    const type = mission.emergencyType?.replaceAll("_", " ").toLocaleLowerCase("es-CO");
    $("driver-emergency-detail").textContent = [id, type, formatTime(mission.reportedAt)].filter(Boolean).join(" · ");
    $("driver-emergency-address").textContent = mission.emergencyAddress ?? "";
    $("driver-emergency-description").textContent = mission.emergencyDescription ?? "";
    $("driver-emergency-title").textContent = status === "TRASLADANDO"
      ? "Traslado al hospital" : isNew ? "Nueva emergencia asignada" : "Emergencia asignada";
    if (isNew) {
      $("driver-announcement").textContent = "Nueva emergencia asignada " + id;
    }
  } else {
    $("driver-announcement").textContent = "";
  }
  const metrics = mission?.tracking;
  const hasRoute = active && metrics?.activeRouteId != null;
  $("driver-route").hidden = !hasRoute;
  if (hasRoute) {
    $("route-distance").textContent = Number.isFinite(metrics.remainingDistanceKm)
      ? metrics.remainingDistanceKm.toFixed(2) + " km" : "—";
    $("route-eta").textContent = Number.isFinite(metrics.etaMinutes) ? Math.ceil(metrics.etaMinutes) + " min" : "—";
  }
  const finished = mission?.status === "FINALIZADA" && mission.finishedAt;
  $("driver-finished").hidden = !finished;
  if (finished) {
    $("finished-arrival").textContent = formatTime(mission.arrivedAt);
    $("finished-at").textContent = formatTime(mission.finishedAt);
    $("finished-duration").textContent = formatDuration(mission.arrivedAt, mission.finishedAt);
  }
  setEmergencyMarker(active ? mission : null);
  if (isNew && active && map && emergencyMarker) {
    const points = ownMarker ? [ownMarker.getLatLng(), emergencyMarker.getLatLng()] : [emergencyMarker.getLatLng()];
    map.fitBounds(points, { padding: [32, 32], maxZoom: 16 });
  }
  drawRoute(mission, active);
}

function setMissionButtons() {
  const status = currentMission?.status;
  $("mission-arrival").disabled = !canOperate || operationPending || !["ASIGNADA", "EN_RUTA"].includes(status);
  $("mission-finish").disabled = !canOperate || operationPending || !["EN_SITIO", "TRASLADANDO"].includes(status);
  $("driver-transfer").hidden = !canOperate || !currentMission?.canTransfer;
  $("driver-transfer").disabled = operationPending;
}

function setOperationPending(value) {
  operationPending = value;
  if (value) missionRevision += 1;
  setMissionButtons();
}

async function refreshMission() {
  if (missionRefreshing || operationPending) {
    missionRefreshQueued = true;
    return;
  }
  missionRefreshing = true;
  const revision = missionRevision;
  try {
    const mission = await getDriverMission();
    if (revision !== missionRevision || operationPending) return;
    if (mission.assignmentId) completedMission = null;
    if (!mission.assignmentId && completedMission) {
      renderMission(completedMission);
      return;
    }
    const point = mission.unit?.location;
    if (point && !tracking) updateOwnMarker([point.latitude, point.longitude]);
    renderMission(mission);
  } catch (error) {
    console.warn("No se pudo actualizar la misión del conductor", error);
  } finally {
    missionRefreshing = false;
    if (missionRefreshQueued && !operationPending) {
      missionRefreshQueued = false;
      refreshMission();
    }
  }
}

function subscribeToMission() {
  let socket;
  let retry;
  const connect = () => {
    const token = gpsSocketToken();
    if (!token || !window.WebSocket || socket) return;
    const connection = new WebSocket(apiWebSocketUrl("/ws/gps"));
    socket = connection;
    connection.addEventListener("open", () => connection.send(JSON.stringify({ type: "auth", token, audience: "driver" })));
    connection.addEventListener("message", ({ data }) => {
      try {
        const { type } = JSON.parse(data);
        if (type === "ready" || type === "mission") refreshMission();
      } catch {
        // The periodic REST refresh remains available.
      }
    });
    connection.addEventListener("close", (event) => {
      if (socket !== connection) return;
      socket = null;
      if (event.code !== 1008) retry = setTimeout(connect, EVERY_MS);
    });
    connection.addEventListener("error", () => connection.close());
  };
  let fallback = setInterval(refreshMission, EVERY_MS);
  connect();
  window.addEventListener("pagehide", () => {
    clearTimeout(retry);
    clearInterval(fallback);
    const closing = socket;
    socket = null;
    closing?.close();
  });
  window.addEventListener("pageshow", (event) => {
    if (!event.persisted) return;
    refreshMission();
    fallback = setInterval(refreshMission, EVERY_MS);
    connect();
  });
}

function estimatedHospitalMinutes(kilometers) {
  const speedKmh = Number.isFinite(latest?.coords?.speed) && latest.coords.speed > 1
    ? latest.coords.speed * 3.6
    : 35;
  return Math.max(1, Math.round(kilometers / speedKmh * 60));
}

function selectHospital(hospital) {
  selectedHospitalId = hospital.id;
  renderResources();
  map?.setView(hospital.location, Math.max(map.getZoom(), 16));
  message(`${hospital.name} seleccionado como destino de referencia. Coordinación confirma el traslado antes de cambiar la ruta.`);
  if (!$('nearby-hospitals-list').hidden) renderNearbyHospitals();
}

function renderNearbyHospitals() {
  const list = $("nearby-hospitals-list");
  const point = latest ? [latest.coords.latitude, latest.coords.longitude]
    : currentMission?.unit?.location ? [currentMission.unit.location.latitude, currentMission.unit.location.longitude] : null;
  list.replaceChildren();
  if (!point || hospitals.length === 0) {
    const item = document.createElement("li");
    item.textContent = point ? "No hay hospitales de referencia disponibles."
      : "Inicia el GPS para ordenar hospitales por cercanía.";
    list.append(item);
    return;
  }
  hospitals.map((hospital) => ({ ...hospital, km: distanceKm(point, hospital.location) }))
    .sort((a, b) => a.km - b.km).forEach((hospital) => {
      const item = document.createElement("li");
      const text = document.createElement("span");
      const name = document.createElement("strong");
      name.textContent = hospital.name;
      const detail = document.createElement("small");
      detail.textContent = `${hospital.km.toFixed(1)} km · ${estimatedHospitalMinutes(hospital.km)} min aprox.`;
      text.append(name, detail);
      const show = document.createElement("button");
      show.type = "button";
      show.textContent = hospital.id === selectedHospitalId ? "Seleccionado" : "Seleccionar";
      show.disabled = hospital.id === selectedHospitalId;
      show.addEventListener("click", () => selectHospital(hospital));
      item.append(text, show);
      list.append(item);
    });
}

function applyTelemetry(position) {
  $("gps-speed").textContent = Number.isFinite(position.coords.speed) ? `${Math.round(position.coords.speed * 3.6)} km/h` : "No disponible";
  updateAddress(position);
  updateOwnMarker([position.coords.latitude, position.coords.longitude]);
  if (!$("nearby-hospitals-list").hidden) renderNearbyHospitals();
}

function publish(version = trackingVersion) {
  if (!tracking || stopping || version !== trackingVersion || publication || !navigator.onLine) return;
  lastPublishAt = Date.now();
  publication = publishFix(version).finally(() => { publication = null; });
}

async function publishFix(version) {
  try {
    // A stationary phone may stop issuing watch callbacks. Request a fresh fix instead of
    // repeating the old coordinates and letting the server mistake them for live GPS.
    if (!latest || Date.now() - latest.timestamp > EVERY_MS) {
      const fix = await new Promise((resolve, reject) => navigator.geolocation.getCurrentPosition(
        resolve, reject, { enableHighAccuracy: true, maximumAge: 0, timeout: 6000 },
      ));
      if (!tracking || stopping || version !== trackingVersion) return;
      latest = fix;
      applyTelemetry(fix);
    }
    if (Date.now() - latest.timestamp > EVERY_MS) return;
    const { latitude, longitude, speed, heading } = latest.coords;
    const saved = await reportGpsPosition({ latitude, longitude, speedMps: speed, headingDegrees: heading });
    lastPublishedPoint = { latitude, longitude };
    if (tracking && version === trackingVersion) {
      $("gps-updated").textContent = formatTime(saved.updatedAt);
      const wasOnline = $("gps-status").dataset.state === "online";
      status("Transmitiendo", "online");
      if (!wasOnline) message("Ubicación enviada al mapa en vivo.");
      if (!gpsSessionRecorded && canOperate) {
        // A failure to register the audit event must not pretend that the confirmed GPS
        // position was lost; it was already stored by reportGpsPosition above.
        try {
          const operation = await startDriverGpsSession();
          gpsSessionRecorded = true;
          if (!operationPending && !currentMission?.assignmentId) renderMission(operation);
        } catch (error) {
          console.warn("No se pudo registrar el inicio de GPS", error);
        }
      }
      refreshMission();
    }
  } catch (error) {
    if (tracking && version === trackingVersion) {
      status("Sin conexión", "offline");
      message(error.message, true);
    }
  }
}

async function stop() {
  if (stopping) return;
  stopping = true;
  $("gps-stop").disabled = true;
  try {
    await publication;
    if (gpsSessionRecorded || latest) await stopDriverGpsSession();
  } catch (error) {
    stopping = false;
    $("gps-stop").disabled = false;
    message(error.message, true);
    refreshMission();
    return;
  }
  tracking = false;
  trackingVersion += 1;
  if (watchId != null) navigator.geolocation.clearWatch(watchId);
  clearInterval(timer);
  watchId = null;
  timer = null;
  latest = null;
  lastPublishedPoint = null;
  lastPublishAt = 0;
  $("gps-start").disabled = false;
  $("gps-stop").disabled = true;
  status("Desconectado", "offline");
  $("gps-updated").textContent = formatTime(new Date());
  message("GPS detenido.");
  gpsSessionRecorded = false;
  stopping = false;
}

function start() {
  if (!window.isSecureContext) {
    status("Se requiere HTTPS", "error");
    message("Abre RutaVida con HTTPS en el teléfono para permitir el GPS real.", true);
    return;
  }
  if (!navigator.geolocation) {
    status("GPS no disponible", "error");
    message("Este navegador no ofrece Geolocation API.", true);
    return;
  }
  $("gps-start").disabled = true;
  $("gps-stop").disabled = false;
  tracking = true;
  const version = ++trackingVersion;
  status("Buscando señal…", "idle");
  message("Permite la ubicación precisa cuando el teléfono la solicite.");
  watchId = navigator.geolocation.watchPosition((position) => {
    if (!tracking || stopping || version !== trackingVersion) return;
    const first = latest == null;
    latest = position;
    applyTelemetry(position);
    if (first || (lastPublishedPoint && Date.now() - lastPublishAt >= MOVING_UPDATE_MS
      && distanceMeters(lastPublishedPoint, position.coords) >= MOVEMENT_METERS)) publish(version);
  }, (error) => {
    if (!tracking || version !== trackingVersion) return;
    const text = error.code === error.PERMISSION_DENIED ? "Debes permitir la ubicación para compartirla."
      : error.code === error.POSITION_UNAVAILABLE ? "El teléfono no pudo obtener una ubicación." : "Se agotó el tiempo para obtener la ubicación.";
    status("GPS no disponible", "error");
    message(text, true);
  }, { enableHighAccuracy: true, maximumAge: 3000, timeout: 15000 });
  timer = setInterval(() => {
    if (Date.now() - lastPublishAt >= EVERY_MS) publish(version);
  }, 1000);
}

async function confirmArrival() {
  if (operationPending) return;
  setOperationPending(true);
  try {
    const mission = await confirmDriverArrival();
    renderMission(mission);
    message("Llegada registrada. El GPS continúa transmitiendo.");
  } catch (error) {
    message(error.message, true);
    return;
  } finally {
    setOperationPending(false);
    refreshMission();
  }
  if (currentMission?.canTransfer) showTransfer();
}

async function showTransfer() {
  if (!currentMission?.canTransfer || operationPending) return;
  const modal = $("transfer-modal");
  const status = $("transfer-status");
  const list = $("transfer-hospitals");
  const assignmentId = currentMission.assignmentId;
  const query = ++transferQuery;
  list.replaceChildren();
  $("transfer-retry").hidden = true;
  status.textContent = "Buscando hospitales cercanos…";
  if (!modal.open) modal.showModal();
  try {
    const candidates = await listDriverTransferHospitals(assignmentId);
    if (!modal.open || query !== transferQuery || assignmentId !== currentMission?.assignmentId) return;
    status.textContent = candidates.length ? "Ordenados por distancia según la ruta."
      : "No hay hospitales operativos con urgencias y una ruta disponible desde tu ubicación.";
    $("transfer-retry").hidden = candidates.length > 0;
    for (const hospital of candidates) {
      const item = document.createElement("li");
      const details = document.createElement("span");
      const name = document.createElement("strong");
      name.textContent = hospital.name;
      const eta = document.createElement("small");
      eta.textContent = hospital.distanceKm.toFixed(2) + " km · " + Math.ceil(hospital.etaMinutes) + " min"
        + (hospital.serviceConfirmed ? "" : " · Urgencias por confirmar");
      details.append(name, eta);
      const button = document.createElement("button");
      button.type = "button";
      button.textContent = "Seleccionar";
      button.addEventListener("click", () => transferTo(assignmentId, hospital));
      item.append(details, button);
      list.append(item);
    }
  } catch (error) {
    if (!modal.open || query !== transferQuery) return;
    status.textContent = error.message;
    $("transfer-retry").hidden = false;
  }
}

async function transferTo(assignmentId, hospital) {
  if (operationPending || assignmentId !== currentMission?.assignmentId) return;
  setOperationPending(true);
  const buttons = $("transfer-modal").querySelectorAll("button");
  buttons.forEach((button) => { button.disabled = true; });
  $("transfer-status").textContent = "Calculando la ruta al hospital…";
  try {
    const mission = await startDriverTransfer(assignmentId, hospital.hospitalId);
    selectedHospitalId = hospital.hospitalId;
    renderResources();
    renderMission(mission);
    message("Traslado iniciado hacia " + hospital.name + ".");
  } catch (error) {
    $("transfer-status").textContent = error.message;
  } finally {
    buttons.forEach((button) => { button.disabled = false; });
    setOperationPending(false);
    refreshMission();
  }
}

function showFinishModal(show) {
  $("finish-modal").hidden = !show;
  if (show) {
    $("finish-error").hidden = true;
    $("finish-confirm").focus();
  }
}

async function finishAttention() {
  if (operationPending) return;
  setOperationPending(true);
  $("finish-confirm").disabled = true;
  $("finish-error").hidden = true;
  try {
    const mission = await finishDriverAttention();
    completedMission = mission;
    renderMission(mission);
    message("Atención finalizada. La unidad está disponible y el GPS sigue activo.");
    showFinishModal(false);
  } catch (error) {
    $("finish-error").textContent = error.message;
    $("finish-error").hidden = false;
  } finally {
    $("finish-confirm").disabled = false;
    setOperationPending(false);
    refreshMission();
  }
}

async function initialize() {
  const session = await startPage();
  if (!session) return;
  if (!hasPermission(session, "GPS:REPORTAR")) {
    status("Sin permiso", "error");
    message("Tu rol no tiene permiso para compartir GPS.", true);
    return;
  }
  canOperate = hasPermission(session, "GPS:OPERAR");
  createMap();
  try {
    const unit = await getDriverUnit();
    $("driver-unit").textContent = `${unit.unitCode}${unit.plate ? ` · ${unit.plate}` : ""}`;
    if (unit.location) updateOwnMarker([unit.location.latitude, unit.location.longitude], true);
    if (unit.updatedAt) $("gps-updated").textContent = new Date(unit.updatedAt).toLocaleString("es-CO");
  } catch (error) {
    status("Sin unidad asignada", "error");
    message(error.message, true);
    $("gps-start").disabled = true;
  }
  if (!canOperate) {
    message("Ejecuta database/15_operacion_conductor.sql y vuelve a iniciar sesión para habilitar llegada y finalización.", true);
  }
  await Promise.all([loadResources(), refreshMission()]);
  subscribeToMission();
  $("gps-start").addEventListener("click", start);
  $("gps-stop").addEventListener("click", stop);
  $("mission-arrival").addEventListener("click", confirmArrival);
  $("mission-finish").addEventListener("click", () => showFinishModal(true));
  $("driver-transfer").addEventListener("click", showTransfer);
  $("transfer-retry").addEventListener("click", showTransfer);
  $("transfer-close").addEventListener("click", () => $("transfer-modal").close());
  $("transfer-modal").addEventListener("cancel", (event) => {
    if (operationPending) event.preventDefault();
  });
  $("transfer-modal").addEventListener("close", () => { transferQuery += 1; });
  $("finish-cancel").addEventListener("click", () => showFinishModal(false));
  $("finish-cancel-top").addEventListener("click", () => showFinishModal(false));
  $("finish-confirm").addEventListener("click", finishAttention);
  $("driver-emergency").addEventListener("click", () => {
    if (emergencyMarker) map?.setView(emergencyMarker.getLatLng(), 16);
  });
  $("nearby-hospitals").addEventListener("click", () => {
    const list = $("nearby-hospitals-list");
    list.hidden = !list.hidden;
    if (!list.hidden) renderNearbyHospitals();
  });
  window.addEventListener("online", () => { if (tracking) { status("Buscando señal…", "idle"); publish(); } });
  window.addEventListener("offline", () => status("Sin conexión", "offline"));
}

initialize();
