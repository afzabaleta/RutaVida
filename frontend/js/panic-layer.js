import {
  listPanicReports,
  discardPanicReport,
} from "./api-client.js";
import { placeIcon } from "./map-icons.js";
import { hasPermission } from "./api-client.js";

const REFRESH_MS = 15000;

function isValidated(report) {
  return ["VALIDADO", "VALIDADA", "CONFIRMADO", "CONFIRMADA"]
    .includes(String(report.status ?? "").toUpperCase());
}

function isPending(report) {
  return ["PENDIENTE", "PENDING"]
    .includes(String(report.status ?? "").toUpperCase());
}

function text(parent, tag, value, className = "") {
  const element = document.createElement(tag);
  element.textContent = String(value ?? "");
  if (className) element.className = className;
  parent.appendChild(element);
  return element;
}

export function startPanicLayer(map, session, elements = {}) {
  const { list, pill, message } = elements;

  if (!map || !list) return null;

  const markers = L.layerGroup().addTo(map);
  let busy = false;
  let disposed = false;

  async function refresh() {
    if (busy || disposed) return;
    busy = true;

    try {
      const reports = await listPanicReports();
      if (disposed) return;

      markers.clearLayers();
      list.replaceChildren();

      const visible = reports.filter(
        (report) => isPending(report) || isValidated(report)
      );

      if (pill) pill.textContent = String(visible.length);
      if (message) {
        message.textContent =
          `${visible.length} reportes SOS pendientes o validados.`;
      }

      for (const report of visible) {
        const pending = isPending(report);
        const kind = pending ? "panic" : "panic-validated";
        const title = `SOS #${report.id}`;
        const status = String(report.status ?? "SIN ESTADO");

        let marker = null;
        const location = report.location;

        if (
          Array.isArray(location) &&
          location.length === 2 &&
          location.every(Number.isFinite)
        ) {
          marker = L.marker(location, {
            icon: placeIcon(kind),
            zIndexOffset: 900,
          }).addTo(markers);

          const popup = document.createElement("div");
          text(popup, "strong", title);
          text(popup, "p", `Estado: ${status}`);
          if (report.description) {
            text(popup, "p", report.description);
          }
          marker.bindPopup(popup);
        }

        const card = document.createElement("article");
        card.className = pending
          ? "panic-report panic-report-pending"
          : "panic-report panic-report-validated";

        text(card, "strong", title);
        text(card, "span", status, "panic-report-status");

        if (report.description) {
          text(card, "p", report.description);
        }

        if (marker) {
          const focus = document.createElement("button");
          focus.type = "button";
          focus.textContent = "Ver en mapa";
          focus.addEventListener("click", () => {
            map.setView(location, Math.max(map.getZoom(), 16));
            marker.openPopup();
          });
          card.appendChild(focus);
        }

        if (pending && hasPermission(session, "PANICO:GESTIONAR")) {
          const discard = document.createElement("button");
          discard.type = "button";
          discard.className = "panic-discard";
          discard.textContent = "Descartar reporte";

          discard.addEventListener("click", async () => {
            if (!window.confirm(`¿Descartar el reporte SOS #${report.id}?`)) {
              return;
            }

            discard.disabled = true;

            try {
              await discardPanicReport(report.id);
              setTimeout(refresh, 0);
            } catch (error) {
              if (message) {
                message.textContent =
                  `No se pudo descartar: ${error.message}`;
              }
              discard.disabled = false;
            }
          });

          card.appendChild(discard);
        }

        list.appendChild(card);
      }

      if (visible.length === 0) {
        text(list, "p", "No hay reportes SOS activos.");
      }
    } catch (error) {
      if (message) {
        message.textContent =
          `No fue posible consultar los reportes SOS: ${error.message}`;
      }
    } finally {
      busy = false;
    }
  }

  refresh();

  const timer = setInterval(refresh, REFRESH_MS);

  return {
    refresh,
    stop() {
      disposed = true;
      clearInterval(timer);
      markers.clearLayers();
      map.removeLayer(markers);
    },
  };
}