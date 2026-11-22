// Bell of alerts in the header (D7).
// The counter comes from /api/tracking every 5 seconds.
// The list is requested only when the user opens the bell.

import { listAlerts, markAlertRead } from "./api-client.js";

/** "14/11 17:20" from "2026-11-14T17:20:05". */
function shortTime(value) {
  const match = /^\d{4}-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(value ?? "");
  return match ? `${match[2]}/${match[1]} ${match[3]}:${match[4]}` : "";
}

/** Starts the bell. Returns { setCount(n) }. */
export function startAlertBell({ button, count, panel, list }) {
  if (!button || !count || !panel || !list) {
    return { setCount() {} };
  }

  function setCount(unread) {
    const value = Number(unread) || 0;
    count.textContent = value > 99 ? "99+" : String(value);
    count.hidden = value === 0;
    button.setAttribute(
      "aria-label",
      value === 0
        ? "Alertas: ninguna sin leer"
        : `Alertas: ${value} sin leer`
    );
  }

  function say(text) {
    list.replaceChildren();
    const message = document.createElement("p");
    message.className = "empty-state";
    message.textContent = text;
    list.appendChild(message);
  }

  async function load() {
    say("Cargando alertas…");

    try {
      const alerts = await listAlerts();
      list.replaceChildren();

      if (alerts.length === 0) {
        say("No tienes alertas.");
        return;
      }

      setCount(alerts.filter((alert) => !alert.read).length);

      for (const alert of alerts) {
        list.appendChild(alertItem(alert));
      }
    } catch (error) {
      say(`No fue posible cargar las alertas: ${error.message}`);
    }
  }

  function alertItem(alert) {
    const item = document.createElement("article");
    item.className = alert.read
      ? "bell-item"
      : "bell-item bell-unread";

    const text = document.createElement("p");
    text.textContent = alert.message;

    const meta = document.createElement("small");
    meta.textContent =
      `${shortTime(alert.createdAt)}` +
      `${alert.emergencyId ? ` · Emergencia #${alert.emergencyId}` : ""}`;

    item.append(text, meta);

    if (!alert.read) {
      const read = document.createElement("button");
      read.type = "button";
      read.className = "map-action bell-read";
      read.textContent = "Marcar como leída";

      read.addEventListener("click", async () => {
        read.disabled = true;

        try {
          await markAlertRead(alert.id);
        } catch (error) {
          read.disabled = false;
          read.textContent = error.message;
          return;
        }

        await load();
      });

      item.appendChild(read);
    }

    return item;
  }

  function close() {
    panel.hidden = true;
    button.setAttribute("aria-expanded", "false");
  }

  button.addEventListener("click", () => {
    const opening = panel.hidden;
    panel.hidden = !opening;
    button.setAttribute("aria-expanded", String(opening));

    if (opening) {
      load();
    }
  });

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && !panel.hidden) {
      close();
      button.focus();
    }
  });

  document.addEventListener("click", (event) => {
    if (
      !panel.hidden &&
      !panel.contains(event.target) &&
      !button.contains(event.target)
    ) {
      close();
    }
  });

  setCount(0);
  return { setCount };
}