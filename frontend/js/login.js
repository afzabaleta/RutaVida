import { currentSession, login } from "./api-client.js";

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

function hidePassword() {
  password.type = "password";
  togglePassword.textContent = "Mostrar";
  togglePassword.setAttribute("aria-pressed", "false");
}

togglePassword.addEventListener("click", () => {
  const isVisible = password.type === "text";

  password.type = isVisible ? "password" : "text";
  togglePassword.textContent = isVisible ? "Mostrar" : "Ocultar";
  togglePassword.setAttribute("aria-pressed", String(!isVisible));
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
    await login(username.value.trim(), password.value);

    password.value = "";
    window.location.replace("./dashboard.html");
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
    if (await currentSession()) {
      window.location.replace("./dashboard.html");
    }
  } catch {
    // The user sees the error when trying to log in.
  }
}

initialize();