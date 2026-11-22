import {
  getOperationsReport,
  listEmergenciesByPeriod,
  getEmergencyHistory,
  listUnits,
  hasPermission,
  todayIso,
  typeLabel,
  SEVERITIES,
} from "./api-client.js";
import { startPage } from "./page-session.js";
import { vehicleLabel } from "./map-icons.js";

const $ = (id) => document.getElementById(id);
let emergencies = [];
let unitCodes = new Map();
let currentRequest = 0;

function showMessage(message, error = false) {
  const element = $("report-message");
  element.textContent = message;
  element.classList.toggle("message-error", error);
}

function display(value) {
  return value === null || value === undefined || value === ""
    ? "Sin datos" : String(value);
}

function dateText(value) {
  if (!value) return "Sin datos";
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? String(value)
    : date.toLocaleString("es-CO");
}

function setPeriod(days) {
  $("date-to").value = todayIso();
  $("date-from").value = todayIso(1 - days);
}

function validPeriod(from, to) {
  if (!from || !to) return "Seleccione ambas fechas.";
  if (from > to) return "La fecha inicial debe ser anterior a la final.";

  const first = new Date(`${from}T12:00:00`);
  const last = new Date(`${to}T12:00:00`);
  const days = Math.round((last - first) / 86400000) + 1;

  if (!Number.isFinite(days) || days < 1 || days > 366) {
    return "El periodo debe tener entre 1 y 366 días.";
  }
  return null;
}

function renderKpis(report) {
  $("kpi-reported").textContent = display(report.reportedEmergencies);
  $("kpi-response").textContent =
    report.averageResponseMinutes == null
      ? "Sin datos"
      : `${Number(report.averageResponseMinutes).toLocaleString("es-CO", { maximumFractionDigits: 1 })} min`;
  $("kpi-dispatches").textContent = display(report.dispatches);
  $("kpi-incidents").textContent = display(report.roadIncidents);
}

/** "ALTA" -> "Alta"; an unknown code is shown as it comes. */
function severityLabel(code) {
  return SEVERITIES.find((severity) => severity.code === code)?.label ?? display(code);
}

/** labelOf turns the code the server sends into the text shown (EMERGENCIA_MEDICA -> Emergencia médica). */
function renderChart(id, values, labelOf = display) {
  const container = $(id);
  container.replaceChildren();

  if (!Array.isArray(values) || values.length === 0) {
    const empty = document.createElement("p");
    empty.className = "muted";
    empty.textContent = "Sin datos para este periodo.";
    container.appendChild(empty);
    return;
  }

  const max = Math.max(1, ...values.map((item) => Number(item.count) || 0));

  for (const item of values) {
    const row = document.createElement("div");
    row.className = "report-chart-row";

    const label = document.createElement("span");
    label.className = "report-chart-label";
    label.textContent = labelOf(item.label);

    const track = document.createElement("div");
    track.className = "report-chart-track";

    const bar = document.createElement("div");
    bar.className = "report-chart-bar";
    bar.style.width = `${Math.max(0, (Number(item.count) || 0) / max * 100)}%`;
    track.appendChild(bar);

    const count = document.createElement("strong");
    count.textContent = display(item.count);

    row.append(label, track, count);
    container.appendChild(row);
  }
}

function renderReport(report) {
  renderKpis(report);
  renderChart("chart-days", report.byDay);
  renderChart("chart-types", report.byEmergencyType, typeLabel);
  renderChart("chart-severity", report.bySeverity, severityLabel);
  renderChart("chart-vehicles", report.byVehicleType, vehicleLabel);
}

function addCell(row, value) {
  const cell = document.createElement("td");
  cell.textContent = display(value);
  row.appendChild(cell);
}

function updateStatusOptions() {
  const select = $("status-filter");
  const selected = select.value;
  const statuses = [...new Set(emergencies.map((item) => item.status).filter(Boolean))];

  select.replaceChildren();
  const all = document.createElement("option");
  all.value = "";
  all.textContent = "Todos";
  select.appendChild(all);

  for (const status of statuses.sort()) {
    const option = document.createElement("option");
    option.value = status;
    option.textContent = status;
    select.appendChild(option);
  }

  if (statuses.includes(selected)) select.value = selected;
}

function filteredEmergencies() {
  const status = $("status-filter").value;
  return emergencies.filter((item) => !status || item.status === status);
}

function renderHistory() {
  const tbody = $("history-rows");
  tbody.replaceChildren();

  const items = filteredEmergencies();

  if (!items.length) {
    const row = document.createElement("tr");
    const cell = document.createElement("td");
    cell.colSpan = 6;
    cell.textContent = "No hay emergencias para este filtro.";
    row.appendChild(cell);
    tbody.appendChild(row);
    return;
  }

  for (const item of items) {
    const row = document.createElement("tr");
    addCell(row, item.code);
    addCell(row, dateText(item.reportedAt));
    addCell(row, typeLabel(item.type));
    addCell(row, severityLabel(item.severity));
    addCell(row, item.status);

    const cell = document.createElement("td");
    const button = document.createElement("button");
    button.type = "button";
    button.className = "button button-secondary";
    button.textContent = "Ver historial";
    button.addEventListener("click", () => loadDetail(item.id));
    cell.appendChild(button);
    row.appendChild(cell);
    tbody.appendChild(row);
  }
}

function detailSection(title, items, fields) {
  const section = document.createElement("section");
  section.className = "report-detail-section";

  const heading = document.createElement("h3");
  heading.textContent = `${title} (${items.length})`;
  section.appendChild(heading);

  if (!items.length) {
    const empty = document.createElement("p");
    empty.textContent = "Sin registros.";
    section.appendChild(empty);
    return section;
  }

  for (const item of items) {
    const article = document.createElement("article");
    article.className = "report-detail-item";

    for (const [label, key] of fields) {
      const paragraph = document.createElement("p");
      const strong = document.createElement("strong");
      strong.textContent = `${label}: `;
      const value = document.createElement("span");
      value.textContent = key.endsWith("At") ? dateText(item[key]) : display(item[key]);
      paragraph.append(strong, value);
      article.appendChild(paragraph);
    }
    section.appendChild(article);
  }

  return section;
}

async function loadDetail(id) {
  const target = $("history-detail");
  const requestId = ++currentRequest;
  target.textContent = "Cargando historial...";

  try {
    const history = await getEmergencyHistory(id);
    if (requestId !== currentRequest) return;

    target.replaceChildren();

    const heading = document.createElement("h3");
    heading.textContent = `Emergencia #${history.emergency?.id ?? id}`;
    target.appendChild(heading);

    const status = document.createElement("p");
    status.textContent = `Estado: ${display(history.emergency?.status)}`;
    target.appendChild(status);

    const assignments = (history.assignments ?? []).map((assignment) => ({
      ...assignment,
      vehicleCode: unitCodes.get(String(assignment.vehicleId)) ?? null,
    }));

    target.appendChild(detailSection("Misiones", assignments, [
      ["ID", "id"],
      ["Vehículo", "vehicleCode"],
      ["Estado", "status"],
      ["Asignada", "assignedAt"],
      ["Salida", "departedAt"],
      ["Llegada", "arrivedAt"],
    ]));

    target.appendChild(detailSection("Rutas", history.routes ?? [], [
      ["ID", "id"],
      ["Tipo", "type"],
      ["Distancia (km)", "distanceKm"],
      ["Tiempo estimado (min)", "estimatedMinutes"],
      ["Estado", "status"],
    ]));

    target.appendChild(detailSection("Auditoría", history.auditTrail ?? [], [
      ["Operación", "operation"],
      ["Módulo", "module"],
      ["Fecha", "occurredAt"],
      ["Resultado", "result"],
      ["Detalle", "detail"],
    ]));
  } catch (error) {
    if (requestId === currentRequest) {
      target.textContent = `No se pudo cargar el historial: ${error.message}`;
    }
  }
}

function csvValue(value) {
  const text = String(value ?? "");
  const safe = /^[\s]*[=+\-@\t\r]/.test(text) ? `'${text}` : text;
  return `"${safe.replaceAll('"', '""')}"`;
}

function downloadCsv() {
  const rows = [
    ["Código", "Fecha", "Tipo", "Gravedad", "Estado"],
    ...filteredEmergencies().map((item) => [
      item.code, item.reportedAt, item.type, item.severity, item.status,
    ]),
  ];

  const csv = "\uFEFF" + rows.map((row) => row.map(csvValue).join(";")).join("\r\n");
  const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = "rutavida-historial.csv";
  link.click();
  URL.revokeObjectURL(url);
}

async function loadReport() {
  const from = $("date-from").value;
  const to = $("date-to").value;
  const problem = validPeriod(from, to);

  if (problem) {
    showMessage(problem, true);
    return;
  }

  showMessage("Consultando Oracle...");

  const results = await Promise.allSettled([
    getOperationsReport(from, to),
    listEmergenciesByPeriod(from, to),
  ]);

  if (results[0].status === "fulfilled") {
    renderReport(results[0].value);
  } else {
    showMessage(`Error al consultar indicadores: ${results[0].reason.message}`, true);
  }

  if (results[1].status === "fulfilled") {
    emergencies = results[1].value;
    updateStatusOptions();
    renderHistory();
    $("history-detail").textContent = "Seleccione una emergencia del historial.";
  } else {
    emergencies = [];
    updateStatusOptions();
    renderHistory();
  }

  if (results.every((result) => result.status === "fulfilled")) {
    showMessage(`Consulta completada: ${from} a ${to}.`);
  } else if (results[0].status === "fulfilled") {
    showMessage(`Indicadores cargados. No se pudo consultar el historial: ${results[1].reason.message}`, true);
  }
}

async function init() {
  const session = await startPage();
  if (!session) return;

  const reportsAllowed = hasPermission(session, "REPORTES:CONSULTAR");
  const emergenciesAllowed = hasPermission(session, "EMERGENCIAS:CONSULTAR");

  if (!reportsAllowed || !emergenciesAllowed) {
    showMessage("No tiene permisos para consultar reportes e historial.", true);
    $("report-filters").hidden = true;
    $("csv-button").disabled = true;
    return;
  }

  if (hasPermission(session, "UNIDADES:CONSULTAR")) {
    try {
      const units = await listUnits();
      unitCodes = new Map(
        units.map((unit) => [String(unit.id), unit.code])
      );
    } catch (error) {
      console.warn("Unit codes unavailable:", error);
    }
  }

  setPeriod(7);

  $("report-filters").addEventListener("submit", (event) => {
    event.preventDefault();
    loadReport();
  });

  document.querySelectorAll("[data-period]").forEach((button) => {
    button.addEventListener("click", () => {
      const days = button.dataset.period === "today"
        ? 1 : Number(button.dataset.period);
      setPeriod(days);
      loadReport();
    });
  });

  $("status-filter").addEventListener("change", renderHistory);
  $("csv-button").addEventListener("click", downloadCsv);
  $("print-button").addEventListener("click", () => window.print());

  await loadReport();
}

init().catch((error) => {
  console.error("Reports initialization failed:", error);
  showMessage(error.message, true);
});