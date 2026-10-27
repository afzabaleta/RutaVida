
const SCENARIOS = [
    { leg: "ida", scenario: "inicial", label: "1. Estación → emergencia" },
    {
        leg: "ida",
        scenario: "congestion-simulada",
        label: "2. Tras la congestión simulada"
    },
    {
        leg: "traslado",
        scenario: "congestion-simulada",
        label: "3. Traslado al hospital"
    }
];

const ROUTE_STYLE = {
    1: {
        color: "#2f7bff",
        weight: 7,
        dashArray: null,
        name: "Recomendada"
    },
    2: {
        color: "#13a89e",
        weight: 5,
        dashArray: "10 8",
        name: "Alternativa 1"
    },
    3: {
        color: "#a35cf0",
        weight: 5,
        dashArray: "4 8",
        name: "Alternativa 2"
    }
};

const CONGESTION_COLOR = "#ff3b4e";
const UNIT_COLOR = "#f6b94b";

const NUMBER = new Intl.NumberFormat("es-CO", {
    minimumFractionDigits: 1,
    maximumFractionDigits: 1
});

const KM = new Intl.NumberFormat("es-CO", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
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

// GeoJSON usa [longitud, latitud]; Leaflet usa [latitud, longitud].
function toLatLngs(coordinates) {
    return coordinates.map(([lon, lat]) => [lat, lon]);
}

function isValid(data) {
    return Boolean(data)
        && data.type === "FeatureCollection"
        && Array.isArray(data.features);
}

function routesOf(data, { leg, scenario }) {
    return data.features
        .filter((feature) =>
            feature.properties?.kind === "ruta"
            && feature.properties.leg === leg
            && feature.properties.scenario === scenario
            && feature.geometry?.type === "LineString"
        )
        .sort((a, b) => a.properties.rank - b.properties.rank);
}

function firstOfKind(data, kind) {
    return data.features.find(
        (feature) => feature.properties?.kind === kind
    ) ?? null;
}

function routePopup(properties) {
    const popup = document.createElement("div");
    popup.className = "map-popup";

    addText(
        popup,
        "strong",
        `Ruta ${properties.rank} · ${
            ROUTE_STYLE[properties.rank]?.name ?? "Alternativa"
        }`
    );

    addText(
        popup,
        "p",
        `${KM.format(properties.distanceKm)} km · ${
            NUMBER.format(properties.estimatedMinutes)
        } min estimados`
    );

    addText(popup, "p", properties.explanation);

    addText(
        popup,
        "span",
        "Calculada por RutaVida",
        "map-popup-label real-label"
    );

    return popup;
}

function simulatedPopup(title, source) {
    const popup = document.createElement("div");
    popup.className = "map-popup";

    addText(popup, "strong", title);
    addText(popup, "p", source);

    addText(
        popup,
        "span",
        "SIMULACION · No es un dato real",
        "map-popup-label"
    );

    return popup;
}

/**
 * Muestra las rutas calculadas para el escenario seleccionado.
 *
 * @param {object} map Mapa de Leaflet del dashboard.
 * @param {object} data window.RUTAVIDA_RUTAS_DEMO.
 * @param {object} elements Contenedores tabs, list y note.
 */
export function showRoutes(map, data, elements) {
    const { tabs, list, note } = elements;

    tabs.replaceChildren();
    list.replaceChildren();

    if (!isValid(data)) {
        addText(
            list,
            "p",
            "Todavía no hay rutas calculadas. Andrés las genera con "
                + "RouteDemo (tarea A-Demo) en frontend/data/rutas-demo.js.",
            "empty-state"
        );

        note.textContent = "";
        return;
    }

    note.textContent = data.note ?? "";

    const layer = L.layerGroup().addTo(map);
    const buttons = [];

    function select(index) {
        const chosen = SCENARIOS[index];

        buttons.forEach((button, i) => {
            button.setAttribute("aria-selected", String(i === index));
        });

        layer.clearLayers();
        list.replaceChildren();

        const bounds = L.latLngBounds([]);
        const lines = new Map();

        // La congestión y la unidad solo aparecen en el escenario 2.
        if (
            chosen.scenario === "congestion-simulada"
            && chosen.leg === "ida"
        ) {
            const jam = firstOfKind(data, "congestion");

            if (jam?.geometry?.type === "LineString") {
                L.polyline(toLatLngs(jam.geometry.coordinates), {
                    color: CONGESTION_COLOR,
                    weight: 14,
                    opacity: 0.45,
                    lineCap: "round"
                })
                    .bindPopup(
                        simulatedPopup(
                            jam.properties.name,
                            jam.properties.source
                        )
                    )
                    .addTo(layer);
            }

            const unit = firstOfKind(data, "unidad");

            if (
                unit?.geometry?.type === "Point"
                && Array.isArray(unit.geometry.coordinates)
            ) {
                const [lon, lat] = unit.geometry.coordinates;

                L.circleMarker([lat, lon], {
                    radius: 9,
                    color: "#101923",
                    weight: 3,
                    fillColor: UNIT_COLOR,
                    fillOpacity: 1
                })
                    .bindPopup(
                        simulatedPopup(
                            unit.properties.name,
                            unit.properties.source
                        )
                    )
                    .addTo(layer);
            }
        }

        const routes = routesOf(data, chosen);

        // Dibujar primero las alternativas para dejar la recomendada encima.
        for (const route of [...routes].reverse()) {
            const properties = route.properties;
            const style = ROUTE_STYLE[properties.rank] ?? ROUTE_STYLE[3];
            const latLngs = toLatLngs(route.geometry.coordinates);

            // Borde blanco para mejorar el contraste sobre el mapa.
            L.polyline(latLngs, {
                color: "#ffffff",
                weight: style.weight + 4,
                opacity: 0.85,
                interactive: false
            }).addTo(layer);

            const line = L.polyline(latLngs, {
                color: style.color,
                weight: style.weight,
                dashArray: style.dashArray,
                opacity: 0.95
            })
                .bindPopup(routePopup(properties))
                .addTo(layer);

            lines.set(properties.rank, line);
            bounds.extend(line.getBounds());
        }

        // Crear las tarjetas con distancia, tiempo y explicación.
        for (const route of routes) {
            const properties = route.properties;
            const style = ROUTE_STYLE[properties.rank] ?? ROUTE_STYLE[3];

            const card = document.createElement("article");
            card.className = properties.rank === 1
                ? "route-card route-best"
                : "route-card";

            const top = document.createElement("div");
            top.className = "route-card-top";

            const swatch = document.createElement("span");
            swatch.className = "route-swatch";
            swatch.style.background = style.color;

            top.appendChild(swatch);

            addText(
                top,
                "h3",
                `Ruta ${properties.rank} · ${style.name}`
            );

            card.appendChild(top);

            addText(
                card,
                "p",
                `${KM.format(properties.distanceKm)} km · ${
                    NUMBER.format(properties.estimatedMinutes)
                } min estimados · ${properties.segments} tramos`,
                "route-numbers"
            );

            addText(
                card,
                "p",
                properties.explanation,
                "route-explanation"
            );

            const button = document.createElement("button");
            button.type = "button";
            button.className = "map-action";
            button.textContent = "Ver en el mapa";

            button.addEventListener("click", () => {
                const line = lines.get(properties.rank);

                if (!line) return;

                map.fitBounds(line.getBounds(), {
                    padding: [30, 30]
                });

                line.openPopup(line.getCenter());
            });

            card.appendChild(button);
            list.appendChild(card);
        }

        if (routes.length === 0) {
            addText(
                list,
                "p",
                "El archivo de rutas no trae este escenario.",
                "empty-state"
            );
        } else {
            map.fitBounds(bounds, { padding: [30, 30] });
        }
    }

    SCENARIOS.forEach((scenario, index) => {
        const button = document.createElement("button");
        button.type = "button";
        button.className = "route-tab";
        button.setAttribute("role", "tab");
        button.setAttribute("aria-selected", "false");
        button.textContent = scenario.label;

        button.addEventListener("click", () => select(index));

        tabs.appendChild(button);
        buttons.push(button);
    });

    select(0);
}
