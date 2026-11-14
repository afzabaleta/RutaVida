// Routes of the engine on the dispatch map (CU-08).
// The page never calculates a route by itself.

const STYLE = {
  1: { color: "#2f7bff", weight: 7, dashArray: null, name: "Recomendada" },
  2: { color: "#13a89e", weight: 5, dashArray: "10 8", name: "Alternativa 1" },
  3: { color: "#a35cf0", weight: 5, dashArray: "4 8", name: "Alternativa 2" },
};

const MINUTES = new Intl.NumberFormat("es-CO", {
  minimumFractionDigits: 1,
  maximumFractionDigits: 1,
});

const KM = new Intl.NumberFormat("es-CO", {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

function addText(parent, tag, text, className = "") {
  const element = document.createElement(tag);
  element.textContent = String(text ?? "");
  if (className) {
    element.className = className;
  }
  parent.appendChild(element);
  return element;
}

function styleOf(route) {
  return STYLE[route.rank] ?? STYLE[3];
}

function numbersOf(route) {
  return `${KM.format(route.distanceKm)} km · ${MINUTES.format(route.estimatedMinutes)} min · `
    + `${route.trafficLights} semáforos · ${route.congestedSegments} tramos congestionados`;
}

function differenceOf(route, best) {
  if (!best || route.rank === best.rank) {
    return "";
  }

  const minutes = route.estimatedMinutes - best.estimatedMinutes;

  if (Math.abs(minutes) < 0.05) {
    return "Tarda lo mismo que la recomendada";
  }

  const sign = minutes >= 0 ? "+" : "−";
  return `${sign}${MINUTES.format(Math.abs(minutes))} min frente a la recomendada`;
}

function popupOf(route) {
  const popup = document.createElement("div");
  popup.className = "map-popup";

  addText(popup, "strong", `Ruta ${route.rank} · ${styleOf(route).name}`);
  addText(popup, "p", numbersOf(route));
  addText(popup, "p", route.explanation);
  addText(popup, "span", "Calculada por RutaVida", "map-popup-label real-label");

  return popup;
}

export function drawRoutes(map, layer, routes, list) {
  layer?.clearLayers();
  list.replaceChildren();

  if (routes.length === 0) {
    addText(list, "p", "Esta misión no tiene rutas en uso.", "empty-state");
    return;
  }

  const best = routes.find((route) => route.rank === 1) ?? routes[0];
  const lines = new Map();
  const bounds = map ? L.latLngBounds([]) : null;

  // Draw alternatives first so the recommended route stays on top.
  for (const route of [...routes].reverse()) {
    if (!layer || route.path.length < 2) {
      continue;
    }

    const style = styleOf(route);

    L.polyline(route.path, {
      color: "#ffffff",
      weight: style.weight + 4,
      opacity: 0.85,
      interactive: false,
    }).addTo(layer);

    const line = L.polyline(route.path, {
      color: style.color,
      weight: style.weight,
      dashArray: style.dashArray,
      opacity: 0.95,
    }).bindPopup(popupOf(route)).addTo(layer);

    lines.set(route.rank, line);
    bounds.extend(line.getBounds());
  }

  for (const route of routes) {
    const style = styleOf(route);

    const card = document.createElement("article");
    card.className = route.rank === 1 ? "route-card route-best" : "route-card";

    const top = document.createElement("div");
    top.className = "route-card-top";

    const swatch = document.createElement("span");
    swatch.className = "route-swatch";
    swatch.style.background = style.color;
    top.appendChild(swatch);

    addText(top, "h3", `Ruta ${route.rank} · ${style.name}`);

    if (route.status) {
      const tag = addText(top, "span", route.status, "data-tag");

      if (route.status === "ACTIVA") {
        tag.classList.add("real-tag");
      }
    }

    card.appendChild(top);

    addText(card, "p", numbersOf(route), "route-numbers");

    const difference = differenceOf(route, best);

    if (difference) {
      addText(card, "p", difference, "route-difference");
    }

    addText(card, "p", route.explanation, "route-explanation");

    const line = lines.get(route.rank);

    if (line) {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "map-action";
      button.textContent = "Ver en el mapa";

      button.addEventListener("click", () => {
        map.fitBounds(line.getBounds(), { padding: [30, 30] });
        line.openPopup(line.getCenter());
      });

      card.appendChild(button);
    }

    list.appendChild(card);
  }

  if (map && bounds.isValid()) {
    map.fitBounds(bounds, { padding: [30, 30] });
  }
}
