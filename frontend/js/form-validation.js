// Shared browser validation for personal and contact data. The backend repeats every rule;
// this module only gives the person an immediate, Spanish explanation before a request is sent.

const NAME = /^\p{L}+(?:\s+\p{L}+)*$/u;
const DOCUMENT = /^\d{10}$/;
const PHONE = /^\d{10}$/;
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const USERNAME = /^[a-z0-9._]{4,40}$/;
const PLATE = /^[A-Z0-9-]{5,10}$/;
const PLACE_NAME = /^\p{L}+(?:[\s-]+\p{L}+)*$/u;

function messageFor(input) {
  const value = input.value.trim();
  const rule = input.dataset.validate;
  if (input.required && !value) return "Este campo es obligatorio.";
  if (!value) return "";
  if (rule === "name" && !NAME.test(value)) {
    return "Usa solo letras, espacios, tildes o ñ.";
  }
  if (rule === "place-name" && !PLACE_NAME.test(value)) {
    return "El nombre solo puede contener letras, espacios o guiones; no se permiten números.";
  }
  if (rule === "document" && !DOCUMENT.test(value)) {
    return "La cédula debe tener exactamente 10 dígitos, sin espacios ni símbolos.";
  }
  if (rule === "phone" && !PHONE.test(value)) {
    return "El celular debe tener exactamente 10 dígitos.";
  }
  if (rule === "email" && !EMAIL.test(value)) {
    return "Escribe un correo electrónico válido.";
  }
  if (rule === "username" && !USERNAME.test(value)) {
    return "Usa de 4 a 40 letras minúsculas, números, puntos o guiones bajos.";
  }
  if (rule === "plate" && !PLATE.test(value.toUpperCase())) {
    return "La placa debe tener de 5 a 10 letras, números o guiones.";
  }
  return "";
}

function sanitize(input) {
  if (input.dataset.validate === "name") {
    input.value = input.value.replace(/[^\p{L}\s]/gu, "").replace(/\s+/g, " ");
  } else if (input.dataset.validate === "document" || input.dataset.validate === "phone") {
    input.value = input.value.replace(/\D/g, "").slice(0, 10);
  } else if (input.dataset.validate === "plate") {
    input.value = input.value.toUpperCase().replace(/[^A-Z0-9-]/g, "").slice(0, 10);
  }
  if (input.dataset.validate === "place-name") {
    input.value = input.value.replace(/[^\p{L}\s-]/gu, "").replace(/\s+/g, " ");
  }
}

function errorNode(input) {
  let error = input.closest(".register-field, .form-field")?.querySelector(`[data-error-for="${input.id}"]`);
  if (error) return error;
  error = document.createElement("small");
  error.className = "field-error";
  error.dataset.errorFor = input.id;
  error.id = `${input.id}-error`;
  input.closest(".register-field, .form-field")?.append(error);
  return error;
}

/** Validates one field and keeps its accessible, inline feedback in sync. */
export function validateField(input, show = true) {
  const message = messageFor(input);
  input.setCustomValidity(message);
  if (!show) return message;
  const error = errorNode(input);
  error.textContent = message;
  error.hidden = !message;
  input.setAttribute("aria-invalid", String(Boolean(message)));
  input.setAttribute("aria-describedby", error.id);
  return message;
}

/** Adds real-time filtering and feedback to every field that declares data-validate. */
export function bindLiveValidation(form) {
  for (const input of form.querySelectorAll("[data-validate]")) {
    input.addEventListener("input", () => {
      sanitize(input);
      validateField(input, true);
    });
    input.addEventListener("blur", () => validateField(input, true));
  }
}

/** Clears inline feedback when a form is reset or switched to another record. */
export function clearLiveValidation(form) {
  for (const input of form.querySelectorAll("[data-validate]")) {
    input.setCustomValidity("");
    input.removeAttribute("aria-invalid");
    input.removeAttribute("aria-describedby");
    const error = input.closest(".register-field, .form-field")?.querySelector(`[data-error-for="${input.id}"]`);
    if (error) {
      error.textContent = "";
      error.hidden = true;
    }
  }
}

/** Returns the first friendly error, or null when browser and RutaVida rules both pass. */
export function validateForm(form) {
  for (const input of form.querySelectorAll("[data-validate]")) {
    sanitize(input);
    const message = validateField(input, true);
    if (message) return { input, message };
  }
  if (!form.checkValidity()) {
    const input = form.querySelector(":invalid");
    return { input, message: input?.validationMessage || "Revisa los datos del formulario." };
  }
  return null;
}
