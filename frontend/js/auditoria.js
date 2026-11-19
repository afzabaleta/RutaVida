// Audit page (CU-21): who did what and when, read from the table AUDITORIA (docs/api.md 15.13).
// It only reads. The entries are written by the server (AuditService) and by the Oracle
// triggers of 04_plsql.sql; nobody can edit or delete them from the web.
import { hasPermission, listAudit } from "./api-client.js";
import { startPage } from "./page-session.js";

const $ = (id) => document.getElementById(id);

/** The modules that write entries today (AuditService and the triggers of 04_plsql.sql). */
const MODULES = [
  "SEGURIDAD", "USUARIOS", "EMERGENCIAS", "DESPACHO", "INCIDENTES",
  "TRAFICO", "UNIDADES", "ESTACIONES", "HOSPITALES", "PANICO",
];

/** The server answers at most 500 entries and a period of at most 31 days (15.13). */
const LIMIT = 500;
const MAX_DAYS = 31;

let entries = [];
let busy = false;

/**
 * The user filter works on the entries already loaded (at most 500), so it needs no list of
 * users: the coordinator can read the audit but not the users (USUARIOS:CONSULTAR).
 */
function visibleEntries() {
  const text = $("audit-user").value.trim().toLowerCase();
  if (!text) return entries;
  return entries.filter((entry) => (entry.username ?? "sin sesión").toLowerCase().includes(text));
}

function say(text, isError = false) {
  const message = $("audit-message");
  message.textContent = text;
  message.classList.toggle("message-error", isError);
}

/** A Date as the value of a datetime-local input, in local time: 2026-11-19T08:30. */
function localInput(date) {
  const pad = (value) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
    + `T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function setLastHours(hours) {
  const now = new Date();
  $("audit-to").value = localInput(now);
  $("audit-from").value = localInput(new Date(now.getTime() - hours * 3600 * 1000));
}

function dateText(value) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? String(value ?? "") : date.toLocaleString("es-CO");
}

function cell(row, text, className = "") {
  const td = document.createElement("td");
  td.textContent = text ?? "—";
  if (className) td.className = className;
  row.appendChild(td);
  return td;
}

function resultTag(result) {
  const tag = document.createElement("span");
  tag.className = result === "EXITO" ? "audit-result audit-ok" : "audit-result audit-fail";
  tag.textContent = result === "EXITO" ? "Éxito" : result === "FALLO" ? "Fallo" : String(result);
  return tag;
}

function render() {
  const body = $("audit-rows");
  const shown = visibleEntries();
  $("audit-csv").disabled = shown.length === 0;
  const failures = shown.filter((entry) => entry.result === "FALLO").length;
  $("audit-count").textContent = shown.length === 0 ? ""
    : `${shown.length} entradas · ${failures} con fallo`
      + (entries.length === LIMIT ? ` · el servidor trajo las ${LIMIT} más recientes: acorte el periodo` : "");

  if (shown.length === 0) {
    const row = document.createElement("tr");
    cell(row, "No hay operaciones con esos filtros.").colSpan = 7;
    body.replaceChildren(row);
    return;
  }

  body.replaceChildren(...shown.map((entry) => {
    const row = document.createElement("tr");
    if (entry.result === "FALLO") row.className = "audit-row-fail";
    cell(row, dateText(entry.occurredAt), "audit-nowrap");
    // A failed login of a user that does not exist has no user: it is shown as such.
    cell(row, entry.username ?? (entry.userId ? `Usuario ${entry.userId}` : "Sin sesión"));
    cell(row, entry.module);
    cell(row, entry.operation);
    cell(row, entry.recordId === null || entry.recordId === undefined ? "—" : `#${entry.recordId}`);
    cell(row, "").appendChild(resultTag(entry.result));
    cell(row, entry.detail, "audit-detail");
    return row;
  }));
}

/** The filters of the form, checked here first so the operator reads a clear message. */
function readFilters() {
  const form = $("audit-filters");
  const from = form.elements.from.value;
  const to = form.elements.to.value;
  if (!from || !to) {
    throw new Error("Elija el periodo: desde y hasta.");
  }
  const days = (new Date(to) - new Date(from)) / 86400000;
  if (days < 0) {
    throw new Error("La fecha «Desde» es posterior a «Hasta».");
  }
  if (days > MAX_DAYS) {
    throw new Error(`El periodo puede ser de ${MAX_DAYS} días como máximo.`);
  }
  return {
    from,
    to,
    module: form.elements.module.value,
    result: form.elements.result.value,
    limit: LIMIT,
  };
}

async function search() {
  if (busy) return;
  let filters;
  try {
    filters = readFilters();
  } catch (error) {
    say(error.message, true);
    return;
  }
  busy = true;
  say("Consultando…");
  try {
    entries = await listAudit(filters);
    render();
    say(`Consultado a las ${new Date().toLocaleTimeString("es-CO", { hour: "numeric", minute: "2-digit" })}`);
  } catch (error) {
    // 400 (a filter the server does not accept) or 403: the server's message as it comes.
    say(error.message, true);
  } finally {
    busy = false;
  }
}

/** CSV for Excel: separated by ";" (Spanish locale) and with BOM so the accents are kept. */
function downloadCsv() {
  const quote = (value) => `"${String(value ?? "").replaceAll('"', '""')}"`;
  const header = ["Fecha y hora", "Usuario", "Módulo", "Operación", "Registro", "Resultado", "Detalle"];
  const lines = [header.map(quote).join(";"), ...visibleEntries().map((entry) => [
    entry.occurredAt, entry.username ?? "", entry.module, entry.operation,
    entry.recordId ?? "", entry.result, entry.detail ?? "",
  ].map(quote).join(";"))];
  const blob = new Blob([`﻿${lines.join("\r\n")}`], { type: "text/csv;charset=utf-8" });
  const link = document.createElement("a");
  link.href = URL.createObjectURL(blob);
  link.download = `auditoria-rutavida-${$("audit-from").value.slice(0, 10)}.csv`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(link.href);
}

async function initialize() {
  const session = await startPage();
  if (!session) return;
  if (!hasPermission(session, "AUDITORIA:CONSULTAR")) {
    say("Tu rol no puede consultar la auditoría.", true);
    $("audit-rows").replaceChildren();
    return;
  }

  const moduleSelect = $("audit-module");
  for (const module of MODULES) {
    const option = document.createElement("option");
    option.value = module;
    option.textContent = module;
    moduleSelect.appendChild(option);
  }

  // Typing a user filters the rows already loaded, without asking the server again.
  $("audit-user").addEventListener("input", render);

  for (const button of document.querySelectorAll("[data-hours]")) {
    button.addEventListener("click", () => {
      setLastHours(Number(button.dataset.hours));
      search();
    });
  }
  $("audit-filters").addEventListener("submit", (event) => {
    event.preventDefault();
    search();
  });
  $("audit-csv").addEventListener("click", downloadCsv);

  setLastHours(24);
  await search();
}

document.addEventListener("DOMContentLoaded", initialize);
