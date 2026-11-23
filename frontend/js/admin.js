// Administration page (CU-03, CU-04 and CU-05): users, units, stations and hospitals.
// Everything goes through the API of docs/api.md section 15; the server validates again
// and its message is shown as it comes. Each tab shows only if the role can read it, and its form
// only if the role can manage it.
import {
  changeRecordStatus,
  createUser,
  hasPermission,
  listAllHospitals,
  listAllStations,
  listRoles,
  listUnitRecords,
  listUsers,
  listVehicleTypes,
  saveRecord,
} from "./api-client.js";
import { bindLiveValidation, clearLiveValidation, validateForm } from "./form-validation.js";
import { startPage } from "./page-session.js";

const $ = (id) => document.getElementById(id);

/** The tabs, in order. read/manage are the permissions of docs/api.md section 3. */
const TABS = [
  { key: "users", read: "USUARIOS:CONSULTAR", manage: "USUARIOS:GESTIONAR", load: loadUsers },
  { key: "units", read: "UNIDADES:CONSULTAR", manage: "UNIDADES:GESTIONAR", load: loadUnits },
  { key: "stations", read: "UNIDADES:CONSULTAR", manage: "UNIDADES:GESTIONAR", load: loadStations },
  { key: "hospitals", read: "HOSPITALES:CONSULTAR", manage: "HOSPITALES:GESTIONAR", load: loadHospitals },
];

/** What a unit can be set to by hand (15.8). OCUPADO is set only by the dispatch. */
const UNIT_STATUSES = ["DISPONIBLE", "MANTENIMIENTO", "FUERA_SERVICIO"];

let session = null;
let busy = false;
let roles = [];
let vehicleTypes = [];
let stations = [];
let users = [];
let units = [];

// ------------------------------------------------------------------ small helpers

function say(text, ok = true) {
  const box = $("admin-message");
  box.textContent = text;
  box.classList.toggle("ok", ok);
  box.classList.toggle("error", !ok);
  box.hidden = !text;
}

function canManage(key) {
  return hasPermission(session, TABS.find((tab) => tab.key === key).manage);
}

function cell(row, text) {
  const td = document.createElement("td");
  td.textContent = text ?? "—";
  row.appendChild(td);
  return td;
}

function statusText(status) {
  return status === "A" ? "Activo" : status === "I" ? "Inactivo" : status;
}

function button(parent, text, onClick, className = "secondary-button") {
  const element = document.createElement("button");
  element.type = "button";
  element.className = `${className} admin-row-button`;
  element.textContent = text;
  element.disabled = busy;
  element.addEventListener("click", onClick);
  parent.appendChild(element);
  return element;
}

function emptyRow(body, columns, text) {
  const row = document.createElement("tr");
  const td = cell(row, text);
  td.colSpan = columns;
  body.replaceChildren(row);
}

/**
 * Fills a select and keeps what the user had chosen: the tab is reloaded after every action,
 * also after an error, and a half-filled form must not change behind the user's back.
 * placeholder adds one empty first option, so a required choice is never made by default.
 */
function fillSelect(select, items, valueOf, textOf, placeholder = null) {
  const previous = select.value;
  const options = items.map((item) => {
    const option = document.createElement("option");
    option.value = String(valueOf(item));
    option.textContent = textOf(item);
    return option;
  });
  if (placeholder) {
    const empty = document.createElement("option");
    empty.value = "";
    empty.textContent = placeholder;
    options.unshift(empty);
  }
  select.replaceChildren(...options);
  if (options.some((option) => option.value === previous)) {
    select.value = previous;
  }
}

/** The form as an object: empty optional texts are null, ids and numbers are numbers. */
function readForm(form, numbers = []) {
  const data = {};
  for (const [name, value] of new FormData(form)) {
    const text = String(value).trim();
    if (numbers.includes(name)) {
      data[name] = text === "" ? null : Number(text);
    } else {
      data[name] = text === "" ? null : text;
    }
  }
  return data;
}

/** Latitude and longitude of the form become the location of the API ({ latitude, longitude }). */
function withLocation(data) {
  const { latitude, longitude, ...rest } = data;
  return { ...rest, location: { latitude, longitude } };
}

/** Puts a record in the form to edit it, or clears the form (record = null). */
function editInForm(form, title, record, newTitle, editTitle) {
  form.reset();
  clearLiveValidation(form);
  form.elements.id.value = record ? String(record.id) : "";
  $(title).textContent = record ? editTitle : newTitle;
  form.querySelector("[data-cancel]").hidden = !record;
  if (record) {
    for (const element of form.elements) {
      if (!element.name || element.name === "id") continue;
      if (element.name === "latitude") element.value = record.location?.latitude ?? "";
      else if (element.name === "longitude") element.value = record.location?.longitude ?? "";
      else element.value = record[element.name] ?? "";
    }
    form.scrollIntoView({ behavior: "smooth", block: "center" });
  }
}

/**
 * Runs one action against the API: one at a time, with its message, and then reloads the tab.
 * done is the message, or a function that writes it from what the server answered.
 */
async function act(work, done, reload) {
  if (busy) return;
  busy = true;
  document.querySelectorAll("button").forEach((element) => { element.disabled = true; });
  try {
    const result = await work();
    say(typeof done === "function" ? done(result) : done, true);
  } catch (error) {
    say(error.message, false);
  } finally {
    busy = false;
    document.querySelectorAll("button").forEach((element) => { element.disabled = false; });
    await reload();
  }
}

// ------------------------------------------------------------------ users (15.1 to 15.4)

async function loadUsers() {
  const body = $("users-rows");
  emptyRow(body, 6, "Cargando…");
  try {
    const [allRoles, loadedUsers] = await Promise.all([listRoles(), listUsers()]);
    roles = allRoles;
    users = loadedUsers;
    fillSelect($("new-user-role"), roles, (role) => role.id, (role) => role.name, "Elija un rol");
    const roleName = (id) => roles.find((role) => role.id === id)?.name ?? `Rol ${id}`;
    if (users.length === 0) {
      emptyRow(body, 6, "No hay usuarios.");
      return;
    }
    body.replaceChildren(...users.map((user) => {
      const row = document.createElement("tr");
      cell(row, user.username);
      cell(row, `${user.firstNames} ${user.lastNames}`);
      cell(row, roleName(user.roleId));
      cell(row, user.email);
      cell(row, statusText(user.status));
      const actions = cell(row, "");
      if (canManage("users")) {
        const next = user.status === "A" ? "I" : "A";
        button(actions, next === "I" ? "Desactivar" : "Activar", () => act(
          () => changeRecordStatus("users", user.id, next),
          `Usuario ${user.username}: ${statusText(next).toLowerCase()}.`,
          loadUsers,
        ));
      }
      return row;
    }));
  } catch (error) {
    emptyRow(body, 6, error.message);
  }
}

function setUpUserForm() {
  const form = $("user-form");
  bindLiveValidation(form);
  form.addEventListener("submit", (event) => {
    event.preventDefault();
    const invalid = validateForm(form);
    if (invalid) {
      say(invalid.message, false);
      invalid.input.focus();
      return;
    }
    const user = readForm(form, ["roleId"]);
    const duplicate = users.find((other) => other.username.toLowerCase() === user.username.toLowerCase()
      || other.document === user.document || other.email.toLowerCase() === user.email.toLowerCase());
    if (duplicate) {
      say("Ya existe un usuario con ese nombre de usuario, cédula o correo.", false);
      return;
    }
    act(async () => {
      const created = await createUser(user);
      form.reset();
      return created;
    }, (created) => `Usuario ${created.username} creado. Entréguele la contraseña por privado.`, loadUsers);
  });
}

// ------------------------------------------------------------------ units (7.2, 15.6 to 15.8)

async function loadUnits() {
  const body = $("units-rows");
  emptyRow(body, 6, "Cargando…");
  try {
    const [loadedUnits, types, allStations] = await Promise.all([listUnitRecords(), listVehicleTypes(), listAllStations()]);
    units = loadedUnits;
    vehicleTypes = types;
    stations = allStations;
    fillSelect($("unit-type"), vehicleTypes, (type) => type.id, (type) => type.name);
    // A new unit can only go to an active station (15.6).
    fillSelect($("unit-station"), stations.filter((station) => station.status === "A"), (station) => station.id, (station) => station.name);
    const typeName = (id) => vehicleTypes.find((type) => type.id === id)?.name ?? `Tipo ${id}`;
    const stationName = (id) => stations.find((station) => station.id === id)?.name ?? `Estación ${id}`;
    if (units.length === 0) {
      emptyRow(body, 6, "No hay unidades.");
      return;
    }
    body.replaceChildren(...units.map((unit) => {
      const row = document.createElement("tr");
      cell(row, unit.unitCode);
      cell(row, typeName(unit.vehicleTypeId));
      cell(row, stationName(unit.stationId));
      cell(row, unit.plate);
      const status = cell(row, "");
      const actions = cell(row, "");
      const busyUnit = unit.operationalStatus === "OCUPADO";
      if (!canManage("units") || busyUnit) {
        status.textContent = unit.operationalStatus;
        if (busyUnit) actions.textContent = "En misión";
        return row;
      }
      const select = document.createElement("select");
      select.className = "admin-status-select";
      select.setAttribute("aria-label", `Estado de ${unit.unitCode}`);
      fillSelect(select, UNIT_STATUSES, (value) => value, (value) => value);
      select.value = unit.operationalStatus;
      select.addEventListener("change", () => act(
        () => changeRecordStatus("units", unit.id, select.value),
        `Unidad ${unit.unitCode}: ${select.value}.`,
        loadUnits,
      ));
      status.appendChild(select);
      button(actions, "Editar", () => editInForm($("unit-form"), "unit-form-title", unit, "Nueva unidad", `Editar ${unit.unitCode}`));
      return row;
    }));
  } catch (error) {
    emptyRow(body, 6, error.message);
  }
}

function setUpUnitForm() {
  const form = $("unit-form");
  bindLiveValidation(form);
  form.querySelector("[data-cancel]").addEventListener("click", () => editInForm(form, "unit-form-title", null, "Nueva unidad"));
  form.addEventListener("submit", (event) => {
    event.preventDefault();
    const invalid = validateForm(form);
    if (invalid) {
      say(invalid.message, false);
      invalid.input.focus();
      return;
    }
    const { id, ...unit } = readForm(form, ["id", "vehicleTypeId", "stationId"]);
    const duplicate = unit.plate && units.find((other) => other.id !== id
      && other.plate?.toUpperCase() === unit.plate.toUpperCase());
    if (duplicate) {
      say("Ya existe una unidad con esa placa.", false);
      return;
    }
    act(async () => {
      const saved = await saveRecord("units", id, unit);
      editInForm(form, "unit-form-title", null, "Nueva unidad");
      return saved;
    }, (saved) => id ? `Unidad ${saved.unitCode} actualizada.` : `Unidad ${saved.unitCode} registrada: queda DISPONIBLE en su estación.`, loadUnits);
  });
}

// ------------------------------------------------------------------ stations (7.3, 15.9 and 15.10)

async function loadStations() {
  const body = $("stations-rows");
  emptyRow(body, 5, "Cargando…");
  try {
    stations = await listAllStations();
    if (stations.length === 0) {
      emptyRow(body, 5, "No hay estaciones.");
      return;
    }
    body.replaceChildren(...stations.map((station) => {
      const row = document.createElement("tr");
      cell(row, station.name);
      cell(row, station.address);
      cell(row, station.location ? `${station.location.latitude.toFixed(5)}, ${station.location.longitude.toFixed(5)}` : null);
      cell(row, statusText(station.status));
      const actions = cell(row, "");
      if (canManage("stations")) {
        button(actions, "Editar", () => editInForm($("station-form"), "station-form-title", station, "Nueva estación", `Editar ${station.name}`));
        const next = station.status === "A" ? "I" : "A";
        button(actions, next === "I" ? "Desactivar" : "Activar", () => act(
          () => changeRecordStatus("stations", station.id, next),
          `Estación ${station.name}: ${statusText(next).toLowerCase()}.`,
          loadStations,
        ));
      }
      return row;
    }));
  } catch (error) {
    emptyRow(body, 5, error.message);
  }
}

function setUpStationForm() {
  const form = $("station-form");
  bindLiveValidation(form);
  form.querySelector("[data-cancel]").addEventListener("click", () => editInForm(form, "station-form-title", null, "Nueva estación"));
  form.addEventListener("submit", (event) => {
    event.preventDefault();
    const invalid = validateForm(form);
    if (invalid) {
      say(invalid.message, false);
      invalid.input.focus();
      return;
    }
    const { id, ...station } = withLocation(readForm(form, ["id", "latitude", "longitude"]));
    act(async () => {
      const saved = await saveRecord("stations", id, station);
      editInForm(form, "station-form-title", null, "Nueva estación");
      return saved;
    }, `Estación ${station.name} guardada.`, loadStations);
  });
}

// ------------------------------------------------------------------ hospitals (7.4, 15.11 and 15.12)

async function loadHospitals() {
  const body = $("hospitals-rows");
  emptyRow(body, 6, "Cargando…");
  try {
    const hospitals = await listAllHospitals();
    if (hospitals.length === 0) {
      emptyRow(body, 6, "No hay hospitales.");
      return;
    }
    body.replaceChildren(...hospitals.map((hospital) => {
      const row = document.createElement("tr");
      cell(row, hospital.name);
      cell(row, hospital.level);
      cell(row, hospital.address);
      cell(row, hospital.emergencyCapacity == null ? "Sin dato" : String(hospital.emergencyCapacity));
      cell(row, statusText(hospital.status));
      const actions = cell(row, "");
      if (canManage("hospitals")) {
        button(actions, "Editar", () => editInForm($("hospital-form"), "hospital-form-title", hospital, "Nuevo hospital", `Editar ${hospital.name}`));
        const next = hospital.status === "A" ? "I" : "A";
        button(actions, next === "I" ? "Desactivar" : "Activar", () => act(
          () => changeRecordStatus("hospitals", hospital.id, next),
          `Hospital ${hospital.name}: ${statusText(next).toLowerCase()}.` + (next === "I" ? " Ya no aparece como destino de traslado." : ""),
          loadHospitals,
        ));
      }
      return row;
    }));
  } catch (error) {
    emptyRow(body, 6, error.message);
  }
}

function setUpHospitalForm() {
  const form = $("hospital-form");
  bindLiveValidation(form);
  form.querySelector("[data-cancel]").addEventListener("click", () => editInForm(form, "hospital-form-title", null, "Nuevo hospital"));
  form.addEventListener("submit", (event) => {
    event.preventDefault();
    const invalid = validateForm(form);
    if (invalid) {
      say(invalid.message, false);
      invalid.input.focus();
      return;
    }
    const { id, ...hospital } = withLocation(readForm(form, ["id", "latitude", "longitude", "emergencyCapacity"]));
    act(async () => {
      const saved = await saveRecord("hospitals", id, hospital);
      editInForm(form, "hospital-form-title", null, "Nuevo hospital");
      return saved;
    }, `Hospital ${hospital.name} guardado.`, loadHospitals);
  });
}

// ------------------------------------------------------------------ tabs

function showTab(tab, visible) {
  for (const other of visible) {
    $(`tab-${other.key}`).hidden = other !== tab;
    $(`tab-button-${other.key}`).setAttribute("aria-selected", String(other === tab));
  }
  say("");
  history.replaceState(null, "", `#${tab.key}`);
  tab.load();
}

async function initialize() {
  session = await startPage();
  if (!session) return;
  const visible = TABS.filter((tab) => hasPermission(session, tab.read));
  if (visible.length === 0) {
    say("Tu rol no tiene acceso a la administración.", false);
    return;
  }
  const nav = $("admin-tabs");
  nav.setAttribute("role", "tablist");
  for (const tab of visible) {
    const section = $(`tab-${tab.key}`);
    section.querySelectorAll("[data-manage]").forEach((element) => {
      element.hidden = !hasPermission(session, tab.manage);
    });
    const tabButton = document.createElement("button");
    tabButton.type = "button";
    tabButton.id = `tab-button-${tab.key}`;
    tabButton.className = "admin-tab";
    tabButton.setAttribute("role", "tab");
    tabButton.textContent = section.dataset.title;
    tabButton.addEventListener("click", () => showTab(tab, visible));
    nav.appendChild(tabButton);
  }
  setUpUserForm();
  setUpUnitForm();
  setUpStationForm();
  setUpHospitalForm();
  const requested = visible.find((tab) => `#${tab.key}` === window.location.hash);
  showTab(requested ?? visible[0], visible);
}

document.addEventListener("DOMContentLoaded", initialize);
