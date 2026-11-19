// Citizen registration of the panic button (CU-23, DT-13). After creating the account the page
// logs in once: the phone stays logged in for 30 days and goes straight to the button.
import { login, registerCitizen } from "./api-client.js";

const form = document.getElementById("register-form");
const message = document.getElementById("register-message");
const button = document.getElementById("register-button");

function field(id) {
  return document.getElementById(id);
}

function say(text, isError) {
  message.textContent = text;
  message.className = isError ? "register-message is-error" : "register-message";
  message.hidden = false;
  message.scrollIntoView({ block: "nearest" });
}

/** The first problem a person can fix before asking the server, or null. */
function problemOf() {
  const required = [
    ["first-names", "Escribe tus nombres."],
    ["last-names", "Escribe tus apellidos."],
    ["document", "Escribe tu número de documento."],
    ["email", "Escribe tu correo electrónico."],
    ["username", "Escribe un usuario."],
    ["password", "Escribe una contraseña."],
  ];
  for (const [id, text] of required) {
    if (!field(id).value.trim()) {
      return [id, text];
    }
  }
  if (field("password").value !== field("password-again").value) {
    return ["password-again", "Las contraseñas no coinciden."];
  }
  if (!field("data-consent").checked) {
    return ["data-consent", "Para crear la cuenta debes autorizar el tratamiento de tus datos."];
  }
  return null;
}

form.addEventListener("submit", async (event) => {
  event.preventDefault();

  const problem = problemOf();
  if (problem) {
    say(problem[1], true);
    field(problem[0]).focus();
    return;
  }

  const username = field("username").value.trim().toLowerCase();
  const password = field("password").value;

  button.disabled = true;
  say("Creando tu cuenta…", false);

  try {
    await registerCitizen({
      firstNames: field("first-names").value.trim(),
      lastNames: field("last-names").value.trim(),
      document: field("document").value.trim(),
      email: field("email").value.trim(),
      username,
      password,
      dataConsent: field("data-consent").checked,
    });

    say("Cuenta creada. Entrando al botón de pánico…", false);
    await login(username, password);
    window.location.replace("./panico.html");
  } catch (error) {
    say(error.message, true);
    button.disabled = false;
  } finally {
    field("password").value = "";
    field("password-again").value = "";
  }
});
