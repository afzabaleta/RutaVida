import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { setImmediate } from "node:timers/promises";
import { createContext, SourceTextModule, SyntheticModule } from "node:vm";
import { test } from "node:test";

function fixture() {
  const elements = new Map();
  const markers = [];
  const lines = [];
  const sockets = [];
  const callbacks = [];
  const document = {
    visibilityState: "visible",
    addEventListener() {},
    createElement: () => ({ append() {}, style: {} }),
    getElementById(id) {
      if (!elements.has(id)) {
        elements.set(id, {
          hidden: true, textContent: "", addEventListener() {}, dataset: {}, classList: { toggle() {} },
        });
      }
      return elements.get(id);
    },
  };
  class Socket {
    events = new Map();
    constructor() { sockets.push(this); }
    addEventListener(event, callback) { this.events.set(event, callback); }
    send(frame) { this.auth = JSON.parse(frame); }
    emit(type, content = {}) {
      this.events.get("message")({ data: JSON.stringify({ type, ...content }) });
    }
    close() {}
  }
  const map = {
    on() {}, fitBounds() {},
    setView() { return this; },
    getZoom() { return 16; },
  };
  function layer(location) {
    return {
      location,
      addTo() { return this; },
      bindTooltip() { return this; },
      bindPopup() { return this; },
      setLatLng(point) { this.location = point; return this; },
      getLatLng() { return { lat: this.location[0], lng: this.location[1] }; },
      setIcon() {},
      clearLayers() {},
      remove() { this.removed = true; },
    };
  }
  const L = {
    map: () => map,
    tileLayer: () => layer(),
    layerGroup: () => layer(),
    circleMarker: layer,
    divIcon: () => ({}),
    marker(location) {
      const marker = layer(location);
      markers.push(marker);
      return marker;
    },
    polyline(path) {
      const line = layer(path);
      lines.push(line);
      return line;
    },
  };
  return {
    elements, markers, lines, sockets, map, callbacks,
    context: createContext({
      L, document, WebSocket: Socket, console,
      window: { WebSocket: Socket, matchMedia: () => ({ matches: true }), addEventListener() {} },
      setInterval(callback) { callbacks.push(callback); return 1; },
      setTimeout() { return 1; }, clearTimeout() {}, clearInterval() {}, cancelAnimationFrame() {},
    }),
  };
}

async function load(file, { context }, imports) {
  const url = new URL("../../frontend/js/" + file, import.meta.url);
  const module = new SourceTextModule(await readFile(url, "utf8"), { context });
  await module.link((name) => {
    const exports = imports[name];
    return new SyntheticModule(Object.keys(exports), function () {
      for (const [key, value] of Object.entries(exports)) this.setExport(key, value);
    }, { context });
  });
  await module.evaluate();
  await setImmediate();
  return module.namespace;
}

const origin = [10.463, -73.253];
const scene = [10.467, -73.250];
const detour = [10.463, -73.247];
const base = {
  apiWebSocketUrl: () => "ws://example.invalid/ws/gps",
  gpsSocketToken: () => "fixture",
  hasPermission: () => true,
};
const unit = { unitCode: "AMB-11", location: { latitude: origin[0], longitude: origin[1] } };
const metrics = {
  assignmentId: 1, emergencyId: 4, vehicleId: 11, status: "EN_RUTA",
  activeRouteId: 42, routesVersion: 1, remainingDistanceKm: 1.9, etaMinutes: 4,
};

test("the driver receives the mission without reload and draws and replaces its active route", async () => {
  const ui = fixture();
  let mission = { unit };
  let path = [origin, scene];
  await load("conductor.js", ui, {
    "./api-client.js": {
      ...base, getDriverUnit: async () => unit, getDriverMission: async () => mission,
      listDriverMissionRoutes: async () => [{ routeId: mission.tracking.activeRouteId, status: "ACTIVA", path }],
      listHospitals: async () => [], listStations: async () => [],
      confirmDriverArrival() {}, finishDriverAttention() {}, reportGpsPosition() {},
      startDriverGpsSession() {}, stopDriverGpsSession() {},
    },
    "./page-session.js": { startPage: async () => ({}) },
  });
  const [socket] = ui.sockets;
  socket.events.get("open")();
  assert.equal(socket.auth.audience, "driver");
  assert.equal(ui.elements.get("driver-emergency").hidden, true);
  mission = {
    ...metrics, unit, tracking: metrics, emergencyType: "EMERGENCIA_MEDICA",
    emergencyDescription: "Prueba del reporte", emergencyLocation: { latitude: scene[0], longitude: scene[1] },
  };
  socket.emit("mission");
  await setImmediate();
  assert.equal(ui.elements.get("driver-emergency").hidden, false);
  assert.match(ui.elements.get("driver-emergency-detail").textContent, /EMG-4/);
  assert.equal(ui.elements.get("route-eta").textContent, "4 min");
  assert.deepEqual(ui.lines[0].location, path);
  path = [detour, scene];
  mission = { ...mission, tracking: { ...metrics, activeRouteId: 43, routesVersion: 2 } };
  await ui.callbacks[0]();
  await setImmediate();
  assert.equal(ui.lines[0].removed, true);
  assert.deepEqual(ui.lines[1].location, path);
});

test("the coordinator draws the active route and moves the ambulance on a GPS frame", async () => {
  const ui = fixture();
  const { startLiveMap } = await load("live-map.js", ui, {
    "./api-client.js": {
      ...base, ApiError: Error,
      getTracking: async () => ({
        units: [{ ...unit, vehicleId: 11, vehicleType: "AMBULANCIA", location: origin }],
        missions: [{ ...metrics, destination: scene }],
      }),
      listMissionRoutes: async () => [{ status: "ACTIVA", path: [origin, scene] }],
    },
    "./map-icons.js": { UNIT_Z: 1000, unitIcon: () => ({}), unitSlots: () => [0], vehicleLabel: () => "Ambulancia" },
  });
  const map = startLiveMap(ui.map, {});
  await setImmediate();
  assert.deepEqual(ui.lines[0].location, [origin, scene]);
  assert.deepEqual(ui.markers[0].location, origin);
  ui.sockets[0].emit("position", {
    position: { vehicleId: 11, source: "GPS", location: { latitude: detour[0], longitude: detour[1] } },
  });
  assert.deepEqual(Array.from(ui.markers[0].location), detour);
  await setImmediate();
  map.stop();
});
