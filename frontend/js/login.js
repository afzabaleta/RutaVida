"use strict";

const form = document.getElementById("login-form");
const username = document.getElementById("username");
const password = document.getElementById("password");
const togglePassword = document.getElementById("toggle-password");
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

togglePassword.addEventListener("click", () => {
  const isVisible = password.type === "text";

  password.type = isVisible ? "password" : "text";
  togglePassword.textContent = isVisible ? "Mostrar" : "Ocultar";
  togglePassword.setAttribute("aria-pressed", String(!isVisible));
});

form.addEventListener("submit", (event) => {
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

  password.value = "";
  password.type = "password";
  togglePassword.textContent = "Mostrar";
  togglePassword.setAttribute("aria-pressed", "false");

  showMessage(
    "La autenticación aún no está conectada. Esta pantalla es una maqueta.",
    "info"
  );
});

username.addEventListener("input", hideMessage);
password.addEventListener("input", hideMessage);
