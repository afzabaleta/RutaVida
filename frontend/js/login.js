import { currentSession, homeOf, login } from "./api-client.js";

const form = document.getElementById("login-form");
const username = document.getElementById("username");
const password = document.getElementById("password");
const togglePassword = document.getElementById("toggle-password");
const submit = form.querySelector('button[type="submit"]');
const message = document.getElementById("login-message");

function showMessage(text, type) {
  message.textContent = text;
  message.className = `message message-${type}`;
  message.hidden = false;
}

function hideMessage() {
  message.textContent = "";
  message.hidden = true;
  message.className = "message";
}

// The toggle is an icon button: CSS swaps the eye icon from aria-pressed,
// and aria-label carries the accessible name.
function setPasswordVisible(visible) {
  password.type = visible ? "text" : "password";
  togglePassword.setAttribute("aria-pressed", String(visible));
  togglePassword.setAttribute(
    "aria-label",
    visible ? "Ocultar contraseña" : "Mostrar contraseña"
  );
}

function hidePassword() {
  setPasswordVisible(false);
}

togglePassword.addEventListener("click", () => {
  setPasswordVisible(password.type !== "text");
});

form.addEventListener("submit", async (event) => {
  event.preventDefault();

  if (!username.value.trim() || !password.value) {
    showMessage("Escriba el usuario y la contraseña.", "error");

    if (!username.value.trim()) {
      username.focus();
    } else {
      password.focus();
    }

    return;
  }

  submit.disabled = true;
  showMessage("Verificando…", "info");

  try {
    const session = await login(username.value.trim(), password.value);

    password.value = "";
    window.location.replace(homeOf(session));
  } catch (error) {
    password.value = "";
    hidePassword();

    showMessage(error.message, "error");
    password.focus();

    submit.disabled = false;
  }
});

username.addEventListener("input", hideMessage);
password.addEventListener("input", hideMessage);

async function initialize() {
  const params = new URLSearchParams(window.location.search);

  if (params.has("salir")) {
    showMessage("Sesión cerrada.", "info");
  } else if (params.has("expirada")) {
    showMessage(
      "La sesión venció o no es válida. Inicie sesión de nuevo.",
      "info"
    );
  }

  try {
    const session = await currentSession();
    if (session) {
      window.location.replace(homeOf(session));
    }
  } catch {
    // The user sees the error when trying to log in.
  }
}

initialize();