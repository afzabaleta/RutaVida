// Side menu and page header shared by every internal page.
// Built once from startPage() after the session is validated.
// Visual only: it reads the session, it never calls the API.

import { hasPermission } from "./api-client.js";

const PAGE_TITLE = "Centro de coordinación de emergencias de Valledupar";
const MOBILE_QUERY = "(max-width: 899.98px)";
const THEME_KEY = "rutavida.theme";

// Menu options in display order. An option shows only if the role has its permission;
// a group with no visible options is not shown.
const MENU = [
  {
    group: "OPERACIÓN",
    label: "Panel de control",
    icon: "home",
    href: "./dashboard.html",
    permission: "EMERGENCIAS:CONSULTAR",
  },
  {
    group: "OPERACIÓN",
    label: "Registrar emergencia",
    icon: "plus",
    href: "./emergencia.html",
    permission: "EMERGENCIAS:GESTIONAR",
  },
  {
    group: "OPERACIÓN",
    label: "Despacho y rutas",
    icon: "route",
    href: "./despacho.html",
    permission: "DESPACHO:GESTIONAR",
  },
  {
    group: "OPERACIÓN",
    label: "Hospitales",
    icon: "hospital",
    href: "./hospitales.html",
    permission: "HOSPITALES:CONSULTAR",
  },
  {
    group: "OPERACIÓN",
    label: "Unidades",
    icon: "unit",
    href: "./unidades.html",
    permission: "UNIDADES:CONSULTAR",
  },
  {
    group: "ANÁLISIS",
    label: "Historial y reportes",
    icon: "chart",
    href: "./reportes.html",
    permission: "REPORTES:CONSULTAR",
  },
{
    group: "ANÁLISIS",
    label: "Auditoría",
    icon: "clock",
    href: "./auditoria.html",
    permission: "AUDITORIA:CONSULTAR",
},
  {
    group: "CIUDADANOS",
    label: "Botón de pánico",
    icon: "sos",
    href: "./panico.html",
    permission: "PANICO:REPORTAR",
    tag: { text: "SOS", kind: "sos" },
  },
  {
    group: "SISTEMA",
    label: "Administración",
    icon: "gear",
    href: "./admin.html",
    permission: "USUARIOS:GESTIONAR",
  },
];

function el(tag, className = "", text = null) {
  const element = document.createElement(tag);

  if (className) {
    element.className = className;
  }

  if (text !== null) {
    element.textContent = String(text);
  }

  return element;
}

function icon(name) {
  const span = el("span", `shell-icon shell-icon-${name}`);
  span.setAttribute("aria-hidden", "true");
  return span;
}

/** "RutaVida" with "Vida" in red. */
function wordmark(className) {
  const word = el("span", className, "Ruta");
  word.appendChild(el("span", "shell-accent", "Vida"));
  return word;
}

function currentPage() {
  const file = window.location.pathname.split("/").pop();
  return file || "dashboard.html";
}

function initials(user) {
  const first = String(user?.firstNames ?? "").trim();
  const last = String(user?.lastNames ?? "").trim();
  const letters = `${first.charAt(0)}${last.charAt(0)}`;
  return (letters || String(user?.username ?? "?").slice(0, 2)).toUpperCase();
}

function menuItem(option, page) {
  const item = el("li");

  if (!option.href) {
    const disabled = el("span", "shell-item is-disabled");
    disabled.setAttribute("role", "link");
    disabled.setAttribute("aria-disabled", "true");
    disabled.title = option.disabledTitle ?? "";
    disabled.append(icon(option.icon), el("span", "shell-label", option.label));
    appendTag(disabled, option.tag);
    item.appendChild(disabled);
    return item;
  }

  const link = el("a", "shell-item");
  link.href = option.href;
  link.append(icon(option.icon), el("span", "shell-label", option.label));
  appendTag(link, option.tag);

  if (option.href.endsWith(`/${page}`)) {
    link.classList.add("is-active");
    link.setAttribute("aria-current", "page");
  }

  item.appendChild(link);
  return item;
}

function appendTag(parent, tag) {
  if (tag) {
    parent.appendChild(el("span", `shell-tag shell-tag-${tag.kind}`, tag.text));
  }
}

function buildNav(session, page) {
  const nav = el("nav", "shell-nav");
  nav.setAttribute("aria-label", "Menú principal");

  let list = null;
  let group = null;

  for (const option of MENU) {
    if (option.available === false || !hasPermission(session, option.permission)) {
      continue;
    }

    if (option.group !== group) {
      group = option.group;
      const id = `shell-group-${nav.childElementCount}`;
      const heading = el("p", "shell-group", group);
      heading.id = id;
      list = el("ul", "shell-list");
      list.setAttribute("aria-labelledby", id);
      nav.append(heading, list);
    }

    list.appendChild(menuItem(option, page));
  }

  return nav;
}

function buildSidebar(session, page) {
  const sidebar = el("aside", "shell-sidebar");
  sidebar.id = "shell-sidebar";
  sidebar.setAttribute("aria-label", "Menú de RutaVida");

  const brand = el("a", "shell-brand");
  brand.href = "./dashboard.html";
  brand.setAttribute("aria-label", "RutaVida, panel de control");
  const logo = el("img");
  logo.src = "./img/logo.svg";
  logo.alt = "";
  logo.width = 44;
  logo.height = 44;
  const names = el("span", "shell-brand-text");
  names.append(wordmark("shell-brand-name"), el("small", "", "Centro de operaciones"));
  brand.append(logo, names);

  const simulation = el("div", "shell-sim");
  simulation.setAttribute("role", "note");
  const dot = el("span", "shell-sim-dot");
  dot.setAttribute("aria-hidden", "true");
  const simText = el("span", "", "Datos de simulación");
  simText.appendChild(el("small", "", "Datos SIMULACIÓN · EJEMPLO"));
  simulation.append(dot, simText);

  // user-name and user-role are filled by page-session.js.
  const user = el("div", "shell-user");
  const avatar = el("span", "shell-avatar", initials(session.user));
  avatar.setAttribute("aria-hidden", "true");
  const who = el("span", "shell-user-text");
  const name = el("strong", "", "");
  name.id = "user-name";
  const role = el("small", "", "");
  role.id = "user-role";
  who.append(name, role);
  user.append(avatar, who);

  // .logout-link is bound to the existing logout in page-session.js.
  const logout = el("a", "shell-item shell-logout logout-link");
  logout.href = "./index.html";
  logout.append(icon("out"), el("span", "shell-label", "Cerrar sesión"));

  const footer = el("div", "shell-footer");
  footer.append(simulation, user, logout);

  sidebar.append(brand, buildNav(session, page), footer);
  return sidebar;
}

function buildHeader() {
  const header = el("header", "shell-header");

  const burger = el("button", "shell-burger");
  burger.type = "button";
  burger.setAttribute("aria-label", "Abrir menú");
  burger.setAttribute("aria-controls", "shell-sidebar");
  burger.setAttribute("aria-expanded", "false");
  burger.appendChild(icon("menu"));

  const mobileBrand = wordmark("shell-mobile-brand");
  const title = el("p", "shell-title", PAGE_TITLE);
  const actions = el("div", "shell-actions");
  actions.appendChild(buildThemeSwitch());

  header.append(burger, mobileBrand, title, actions);
  return { header, burger, actions };
}

/** Sun / moon switch. theme-boot.js already set data-theme before paint. */
function buildThemeSwitch() {
  const group = el("div", "theme-switch");
  group.setAttribute("role", "group");
  group.setAttribute("aria-label", "Tema de colores");

  const options = [
    { theme: "light", icon: "sun", label: "Modo claro" },
    { theme: "dark", icon: "moon", label: "Modo oscuro" },
  ];

  const buttons = options.map((option) => {
    const button = el("button");
    button.type = "button";
    button.dataset.theme = option.theme;
    button.setAttribute("aria-label", option.label);
    button.title = option.label;
    button.appendChild(icon(option.icon));
    button.addEventListener("click", () => applyTheme(option.theme));
    group.appendChild(button);
    return button;
  });

  function sync() {
    const current = document.documentElement.getAttribute("data-theme") === "light"
      ? "light"
      : "dark";

    for (const button of buttons) {
      button.setAttribute("aria-pressed", String(button.dataset.theme === current));
    }
  }

  function applyTheme(theme) {
    document.documentElement.setAttribute("data-theme", theme);

    try {
      window.localStorage.setItem(THEME_KEY, theme);
    } catch {
      // Storage blocked: the theme still applies to this page.
    }

    sync();
  }

  sync();
  return group;
}

/** Mobile drawer: opens from the left over a dark backdrop. */
function startDrawer(sidebar, burger, backdrop) {
  const mobile = window.matchMedia(MOBILE_QUERY);

  function isOpen() {
    return sidebar.classList.contains("is-open");
  }

  function sync() {
    // Closed drawer must not be reachable by keyboard or screen reader.
    sidebar.inert = mobile.matches && !isOpen();
  }

  function open() {
    sidebar.classList.add("is-open");
    backdrop.hidden = false;
    document.body.classList.add("shell-locked");
    burger.setAttribute("aria-expanded", "true");
    burger.setAttribute("aria-label", "Cerrar menú");
    sync();
    sidebar.querySelector("a, [tabindex]")?.focus();
  }

  function close(returnFocus = true) {
    if (!isOpen()) {
      return;
    }

    sidebar.classList.remove("is-open");
    backdrop.hidden = true;
    document.body.classList.remove("shell-locked");
    burger.setAttribute("aria-expanded", "false");
    burger.setAttribute("aria-label", "Abrir menú");
    sync();

    if (returnFocus) {
      burger.focus();
    }
  }

  burger.addEventListener("click", () => (isOpen() ? close() : open()));
  backdrop.addEventListener("click", () => close());

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && isOpen()) {
      close();
    }
  });

  sidebar.addEventListener("click", (event) => {
    if (event.target.closest("a")) {
      close(false);
    }
  });

  mobile.addEventListener("change", () => {
    close(false);
    sync();
  });

  sync();
}

/**
 * Wraps the page in the shell: side menu, header and drawer.
 * Elements marked data-shell-slot="header-actions" (the alert bell)
 * move into the header with their ids intact.
 */
/**
 * Shows whether the simulation really runs (D12).
 * Pages without the simulation panel only identify simulated data.
 */
export function showSimulationState(running) {
  const box = document.querySelector(".shell-sim");
  if (!box) return;

  box.classList.toggle("is-stopped", !running);
  const title = box.querySelector("span:not(.shell-sim-dot)");
  if (title?.firstChild) {
    title.firstChild.textContent = running ? "Simulación en marcha" : "Simulación detenida";
  }
}

export function renderAppShell(session) {
  if (document.body.classList.contains("has-shell")) {
    return;
  }

  const page = currentPage();
  const sidebar = buildSidebar(session, page);
  const { header, burger, actions } = buildHeader();

  for (const slot of document.querySelectorAll('[data-shell-slot="header-actions"]')) {
    actions.appendChild(slot);
  }

  const backdrop = el("div", "shell-backdrop");
  backdrop.hidden = true;

  const content = el("div", "shell-content");
  content.appendChild(header);

  for (const child of [...document.body.children]) {
    if (child.tagName !== "SCRIPT") {
      content.appendChild(child);
    }
  }

  document.body.prepend(sidebar, backdrop, content);
  document.body.classList.add("has-shell");

  startDrawer(sidebar, burger, backdrop);
}
