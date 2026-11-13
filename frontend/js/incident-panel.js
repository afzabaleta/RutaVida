
 // Road incidents on the main panel (CU-07 and CU-14).
 // A new incident stays PENDIENTE until validated (RN-05).
import {
  changeIncidentStatus,
  hasPermission,
  incidentLabel,
  listIncidents,
} from "./api-client.js";

const REFRESH_MS = 15000;

const SEVERITY_COLORS = {
  BAJA: "#f6d365",
  MEDIA: "#f6b94b",
  ALTA: "#ff8a4c",
  CRITICA: "#ff4d5e",
};

const ACTIONS = {
  PENDIENTE: [
    { status: "ACTIVO", text: "Validar" },
    { status: "RECHAZADO", text: "Rechazar" },
  ],
  ACTIVO: [{ status: "CERRADO", text: "Cerrar" }],
};

const DONE = {
  ACTIVO: "validado: el motor de rutas lo tendrá en cuenta",
  RECHAZADO: "rechazado",
  CERRADO: "cerrado",
};

function addText(parent, tag, text, className = "") {
  const element = document.createElement(tag);
  element.textContent = text;
  if (className) {
    element.className = className;
  }
  parent.appendChild(element);
  return element;
}

function shortTime(value) {
  const match = /^\d{4}-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(value ?? "");
  return match ? `${match[2]}/${match[1]} ${match[3]}:${match[4]}` : "";
}

function titleOf(incident) {
  const place = incident.streetName || `Tramo ${incident.segmentId}`;
  return `${incidentLabel(incident.type)} · ${place}`;
}

function detailOf(incident) {
  const parts = [`Severidad ${incident.severity}`];

  if (incident.blocksSegment) {
    parts.push("vía cerrada");
  }

  if (incident.description) {
    parts.push(incident.description);
  }

  return parts.join(" · ");
}

function sourceOf(incident) {
  const time = shortTime(incident.startedAt);
  const origin = incident.origin === "SIMULACION"
    ? "SIMULACION"
    : `origen ${incident.origin}`;

  return `Reportado ${time} · ${origin} · ORACLE`;
}

function statusTag(incident) {
  const tag = document.createElement("span");
  tag.textContent = incident.status;
  tag.className = incident.status === "ACTIVO"
    ? "data-tag active-tag"
    : "data-tag";
  return tag;
}

export function startIncidents(map, session, { list, pill, message }, onChange) {
  const layer = map ? L.layerGroup().addTo(map) : null;
  const canManage = hasPermission(session, "INCIDENTES:GESTIONAR");
  let busy = false;

  function say(text, isError = false) {
    message.textContent = text;
    message.classList.toggle("is-error", isError);
  }

  function actionButtons(incident) {
    const box = document.createElement("div");
    box.className = "incident-actions";

    for (const action of ACTIONS[incident.status] ?? []) {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "map-action";
      button.textContent = action.text;
      button.addEventListener("click", () => act(incident, action.status));
      box.appendChild(button);
    }

    return box;
  }

  function popupOf(incident) {
    const popup = document.createElement("div");
    popup.className = "map-popup";

    addText(popup, "strong", titleOf(incident));
    addText(popup, "p", detailOf(incident));
    addText(
      popup,
      "span",
      `${incident.status} · ${sourceOf(incident)}`,
      "map-popup-label"
    );

    if (canManage) {
      popup.appendChild(actionButtons(incident));
    }

    return popup;
  }

  function cardOf(incident, line) {
    const card = document.createElement("article");
    card.className = "data-card";

    const top = document.createElement("div");
    top.className = "data-card-top";

    addText(top, "strong", titleOf(incident));
    top.appendChild(statusTag(incident));
    card.appendChild(top);

    addText(card, "p", detailOf(incident));
    addText(
      card,
      "p",
      line
        ? sourceOf(incident)
        : `${sourceOf(incident)} · Sin tramo en el mapa`
    );

    const actions = canManage
      ? actionButtons(incident)
      : document.createElement("div");

    actions.className = "incident-actions";

    if (line) {
      const show = document.createElement("button");
      show.type = "button";
      show.className = "map-action";
      show.textContent = "Ver en el mapa";

      show.addEventListener("click", () => {
        map.fitBounds(line.getBounds(), { maxZoom: 17 });
        line.openPopup();
      });

      actions.prepend(show);
    }

    if (actions.hasChildNodes()) {
      card.appendChild(actions);
    }

    return card;
  }

  function render(incidents) {
    layer?.clearLayers();
    list.replaceChildren();
    pill.textContent = String(incidents.length);

    if (incidents.length === 0) {
      addText(
        list,
        "p",
        "No hay incidentes viales abiertos.",
        "empty-state"
      );
      return;
    }

    for (const incident of incidents) {
      let line = null;

      if (layer && incident.path?.every(Boolean)) {
        line = L.polyline(incident.path, {
          color: SEVERITY_COLORS[incident.severity] ?? SEVERITY_COLORS.MEDIA,
          weight: 7,
          opacity: 0.9,
          dashArray: incident.status === "PENDIENTE" ? "8 8" : null,
        }).addTo(layer);

        line.bindPopup(popupOf(incident));
      }

      list.appendChild(cardOf(incident, line));
    }
  }

  async function refresh() {
    if (busy) {
      return;
    }

    try {
      render(await listIncidents());
    } catch (error) {
      if (error.status === 401) {
        window.location.replace("./index.html?expirada=1");
        return;
      }

      layer?.clearLayers();
      list.replaceChildren();
      pill.textContent = "—";

      addText(
        list,
        "p",
        `No fue posible cargar los incidentes: ${error.message}`,
        "empty-state"
      );
    }
  }

  async function act(incident, status) {
    if (busy) {
      return;
    }

    busy = true;

    for (const button of document.querySelectorAll(".incident-actions button")) {
      button.disabled = true;
    }

    map?.closePopup();

    try {
      await changeIncidentStatus(incident.id, status);
      say(`Incidente ${titleOf(incident)} ${DONE[status]}.`);
      onChange?.();
    } catch (error) {
      say(error.message, true);
    } finally {
      busy = false;
      await refresh();
    }
  }

  refresh();
  setInterval(refresh, REFRESH_MS);
}
