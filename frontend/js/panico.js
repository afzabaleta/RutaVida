import {
  currentSession,
  hasPermission,
  listPanicEmergencyTypes,
  logout,
  sendPanicAlert,
} from "./api-client.js";
import {
  DEMO_POINT,
  createBaseMap,
  insideZone,
  byId,
} from "./map-base.js";
import { placeIcon } from "./map-icons.js";

const DEVICE_KEY = "rutavida.panic.device-id";

const map = createBaseMap("panic-map");
const gpsButton = byId("gps-button");
const demoButton = byId("demo-button");
const sosButton = byId("sos-button");
const locationStatus = byId("location-status");
const panicStatus = byId("panic-status");
const typeSelect = byId("panic-type");
const descriptionInput = byId("panic-description");

let selectedLocation = null;
let marker = null;
let sending = false;
let memoryDeviceId = null;
/** DT-13: only a person with an account (PANICO:REPORTAR) can send the alert. */
let account = null;

/**
 * Random UUID v4. crypto.randomUUID only exists on https or localhost, and phones open the
 * page through http://<IP of the PC>; crypto.getRandomValues works everywhere.
 */
function newUuid() {
  if (window.crypto?.randomUUID) {
    return window.crypto.randomUUID();
  }
  const bytes = window.crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = [...bytes].map((byte) => byte.toString(16).padStart(2, "0")).join("");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

/** Identifier of this browser, kept so the server can limit it (RN-13). Never blocks the alert. */
function getDeviceId() {
  try {
    let id = localStorage.getItem(DEVICE_KEY);
    if (!id) {
      id = newUuid();
      localStorage.setItem(DEVICE_KEY, id);
    }
    return id;
  } catch {
    // Private mode or blocked storage: keep one id while the page is open.
    memoryDeviceId ??= newUuid();
    return memoryDeviceId;
  }
}

function setLocation(point, source) {
  const lat = Number(point[0]);
  const lng = Number(point[1]);

  if (!Number.isFinite(lat) || !Number.isFinite(lng) || !insideZone(lat, lng)) {
    locationStatus.textContent =
      "La ubicación está fuera de la zona de demostración. Selecciona un punto dentro del área permitida.";
    return;
  }

  selectedLocation = [lat, lng];
  if (map) {
    if (marker) map.removeLayer(marker);
    marker = L.marker(selectedLocation, { icon: placeIcon("panic") }).addTo(map);
    map.setView(selectedLocation, 16);
  }

  locationStatus.textContent =
    `${source}: ${lat.toFixed(6)}, ${lng.toFixed(6)}`;
  sosButton.disabled = sending || !account;
  panicStatus.textContent = account
    ? "Ubicación lista. Puedes enviar la alerta."
    : "Ubicación lista. Inicia sesión para enviar la alerta.";
}

demoButton.addEventListener("click", () => {
  setLocation(DEMO_POINT, "Ubicación de prueba");
});

gpsButton.addEventListener("click", () => {
  if (!navigator.geolocation) {
    locationStatus.textContent = "Este navegador no admite geolocalización.";
    return;
  }

  gpsButton.disabled = true;
  locationStatus.textContent = "Buscando ubicación GPS...";

  navigator.geolocation.getCurrentPosition(
    (position) => {
      gpsButton.disabled = false;
      setLocation(
        [position.coords.latitude, position.coords.longitude],
        "GPS"
      );
    },
    () => {
      gpsButton.disabled = false;
      locationStatus.textContent =
        "No se pudo obtener el GPS. Puedes seleccionar un punto en el mapa.";
    },
    { enableHighAccuracy: true, timeout: 12000, maximumAge: 0 }
  );
});

if (map) {
  map.on("click", (event) => {
    setLocation([event.latlng.lat, event.latlng.lng], "Mapa");
  });
}

async function loadTypes() {
  try {
    const types = await listPanicEmergencyTypes();
    for (const type of types) {
      const option = document.createElement("option");
      option.value = String(type.id);
      option.textContent = type.label;
      typeSelect.appendChild(option);
    }
  } catch {
    panicStatus.textContent =
      "No se pudieron cargar los tipos de emergencia. Puedes enviar el reporte sin especificar el tipo.";
  }
}

sosButton.addEventListener("click", async () => {
  if (sending || !selectedLocation) return;

  sending = true;
  sosButton.disabled = true;
  panicStatus.textContent = "Enviando alerta al servidor...";

  try {
    const result = await sendPanicAlert({
      deviceId: getDeviceId(),
      location: selectedLocation,
      emergencyTypeId: typeSelect.value ? Number(typeSelect.value) : null,
      description: descriptionInput.value.trim() || null,
    });

    panicStatus.textContent =
      result.message ||
      "Reporte recibido por el servidor. Su estado depende de la validación del sistema.";
  } catch (error) {
    if (error.status === 401) {
      showAccountRequired("Tu sesión terminó. Inicia sesión de nuevo para enviar la alerta.");
    }
    panicStatus.textContent =
      error.message || "No fue posible enviar la alerta.";
  } finally {
    sending = false;
    sosButton.disabled = !selectedLocation || !account;
  }
});

// ------------------------------------------------------------------ account (DT-13)

function link(href, text, className) {
  const anchor = document.createElement("a");
  anchor.href = href;
  anchor.textContent = text;
  anchor.className = className;
  return anchor;
}

/** Without an account the page explains how to get one; the SOS button stays off. */
function showAccountRequired(reason) {
  account = null;
  sosButton.disabled = true;

  const box = byId("panic-account");
  box.replaceChildren();

  const title = document.createElement("h2");
  title.textContent = "Necesitas una cuenta";
  const text = document.createElement("p");
  text.textContent = reason
    || "Para evitar alertas falsas, el botón solo funciona con una cuenta. Inicia sesión una vez y tu "
      + "celular quedará conectado 30 días.";
  const actions = document.createElement("div");
  actions.className = "panic-actions";
  actions.append(
    link("./index.html", "Iniciar sesión", "panic-secondary"),
    link("./registro.html", "Crear cuenta", "panic-secondary"),
  );
  box.append(title, text, actions);
}

function showAccount(session) {
  account = session;
  sosButton.disabled = !selectedLocation || sending;

  const box = byId("panic-account");
  box.replaceChildren();

  const text = document.createElement("p");
  const name = `${session.user.firstNames ?? ""} ${session.user.lastNames ?? ""}`.trim()
    || session.user.username;
  text.textContent = `Conectado como ${name} (${session.role}).`;

  const exit = document.createElement("button");
  exit.type = "button";
  exit.className = "panic-secondary";
  exit.textContent = "Cerrar sesión";
  exit.addEventListener("click", async () => {
    await logout();
    showAccountRequired("Cerraste sesión en este celular.");
  });

  const note = document.createElement("p");
  note.className = "panic-hint";
  note.textContent = hasPermission(session, "PANICO:VALIDAR")
    ? "Como policía, tu reporte valida la alerta de inmediato."
    : "Las alertas quedan a tu nombre. Dos alertas falsas bloquean la cuenta.";

  box.append(text, note, exit);
}

async function loadAccount() {
  let session = null;
  try {
    session = await currentSession();
  } catch {
    session = null;
  }

  if (session && hasPermission(session, "PANICO:REPORTAR")) {
    showAccount(session);
  } else {
    showAccountRequired(session
      ? "Tu rol no envía alertas desde el botón de pánico."
      : null);
  }
}

loadAccount();
loadTypes();