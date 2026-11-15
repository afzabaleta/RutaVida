import {
  listPanicEmergencyTypes,
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

function getDeviceId() {
  let id;
  try {
    id = localStorage.getItem(DEVICE_KEY);
    if (!id) {
      id = crypto.randomUUID();
      localStorage.setItem(DEVICE_KEY, id);
    }
  } catch {
    throw new Error("No se pudo crear el identificador del dispositivo.");
  }
  return id;
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
  sosButton.disabled = sending;
  panicStatus.textContent = "Ubicación lista. Puedes enviar la alerta.";
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
    panicStatus.textContent =
      error.message || "No fue posible enviar la alerta.";
  } finally {
    sending = false;
    sosButton.disabled = !selectedLocation;
  }
});

loadTypes();