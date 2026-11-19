// Report a road incident from the main panel (CU-13): click on a street of the map, fill in
// the form and the incident enters PENDIENTE (RN-05). It changes no route until someone
// validates it in the list of incidents (CU-14).
import { SEVERITIES, findNearestSegment, hasPermission, incidentLabel, reportIncident } from "./api-client.js";

const TYPES = ["ACCIDENTE", "CIERRE", "OBRA", "MANIFESTACION", "OTRO"];
// Cyan, so the chosen street is not confused with a blue route nor with a red incident.
const PICK_STYLE = { color: "#06b6d4", weight: 11, opacity: 0.95, lineCap: "round" };

function option(select, value, text) {
  const element = document.createElement("option");
  element.value = value;
  element.textContent = text;
  select.appendChild(element);
}

/**
 * Starts the form. Returns false (and leaves everything hidden) without a map, or if the role
 * cannot register incidents (INCIDENTES:GESTIONAR) or read the road network (RED_VIAL:CONSULTAR).
 * onReported runs after a successful report, to refresh the list of incidents.
 */
export function startIncidentForm(map, session, elements, onReported) {
  const { openButton, form, street, bothWays, bothWaysField, message } = elements;
  if (!map || !hasPermission(session, "INCIDENTES:GESTIONAR")
      || !hasPermission(session, "RED_VIAL:CONSULTAR")) {
    return false;
  }

  const layer = L.layerGroup().addTo(map);
  const container = map.getContainer();
  let picking = false;
  let segment = null;
  let busy = false;

  for (const type of TYPES) option(form.elements.type, type, incidentLabel(type));
  for (const severity of SEVERITIES) option(form.elements.severity, severity.code, severity.label);
  form.elements.severity.value = "ALTA";
  openButton.hidden = false;

  function say(text, isError = false) {
    message.textContent = text;
    message.classList.toggle("is-error", isError);
  }

  function setPicking(on) {
    picking = on;
    container.classList.toggle("is-picking", on);
    openButton.textContent = on ? "Cancelar" : "Reportar incidente";
    openButton.setAttribute("aria-pressed", String(on));
  }

  function close() {
    setPicking(false);
    segment = null;
    layer.clearLayers();
    form.hidden = true;
    form.reset();
    form.elements.severity.value = "ALTA";
  }

  async function choose(latlng) {
    if (busy) return;
    busy = true;
    say("Buscando la calle…");
    try {
      segment = await findNearestSegment(latlng.lat, latlng.lng);
      layer.clearLayers();
      L.polyline(segment.path, PICK_STYLE).addTo(layer);
      street.textContent = `${segment.streetName || `Tramo ${segment.segmentId}`} · a ${Math.round(segment.distanceMeters)} m del clic`;
      const twoWay = segment.reverseSegmentId !== null;
      bothWays.checked = twoWay;
      bothWays.disabled = !twoWay;
      bothWaysField.title = twoWay ? "" : "Calle de un solo sentido";
      form.hidden = false;
      setPicking(false);
      say("");
      // The form is in the side panel, away from the map: bring it into view.
      form.scrollIntoView({ behavior: "smooth", block: "center" });
      form.elements.type.focus({ preventScroll: true });
    } catch (error) {
      // 409 "Haga clic sobre una calle": the operator clicks again, still picking.
      say(error.message, true);
    } finally {
      busy = false;
    }
  }

  openButton.addEventListener("click", () => {
    if (picking || !form.hidden) {
      close();
      say("");
      return;
    }
    setPicking(true);
    say("Haga clic sobre la calle del incidente en el mapa. Esc para cancelar.");
    container.scrollIntoView({ behavior: "smooth", block: "center" });
  });

  map.on("click", (event) => {
    if (picking) choose(event.latlng);
  });

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && (picking || !form.hidden)) {
      close();
      say("");
    }
  });

  form.querySelector("[data-cancel]").addEventListener("click", () => {
    close();
    say("");
  });

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (busy || !segment || !form.reportValidity()) return;
    busy = true;
    const submit = form.querySelector("button[type=submit]");
    submit.disabled = true;

    const data = {
      type: form.elements.type.value,
      severity: form.elements.severity.value,
      blocksSegment: form.elements.blocksSegment.checked,
      description: form.elements.description.value.trim() || null,
    };
    const ids = [segment.segmentId];
    if (bothWays.checked && segment.reverseSegmentId !== null) ids.push(segment.reverseSegmentId);
    const place = segment.streetName || `tramo ${segment.segmentId}`;

    let saved = 0;
    try {
      for (const segmentId of ids) {
        await reportIncident({ ...data, segmentId });
        saved += 1;
      }
      close();
      say(`${incidentLabel(data.type)} en ${place} registrado como PENDIENTE`
        + (ids.length > 1 ? " en los dos sentidos" : "")
        + ". Valídelo en la lista para que afecte las rutas.");
      onReported?.();
    } catch (error) {
      // If the first direction was saved and the second failed, the list already shows the first.
      say(saved > 0 ? `${error.message} (se registró ${saved} de ${ids.length} sentidos)` : error.message, true);
      if (saved > 0) onReported?.();
    } finally {
      busy = false;
      submit.disabled = false;
    }
  });

  return true;
}
