import { currentSession, hasToken, logout } from "./api-client.js";

function goToLogin(reason) {
  window.location.replace(
    reason ? `./index.html?${reason}=1` : "./index.html"
  );
}

function fullName(user) {
  return `${user.firstNames ?? ""} ${user.lastNames ?? ""}`.trim()
    || user.username;
}

export async function startPage() {
  if (!hasToken()) {
    goToLogin(null);
    return null;
  }

  let session;

  try {
    session = await currentSession();
  } catch (error) {
    console.error("Session check failed:", error);

    document.body.replaceChildren();

    const message = document.createElement("p");
    message.className = "message message-error";
    message.textContent = error.message;

    document.body.appendChild(message);
    return null;
  }

  if (!session) {
    goToLogin("expirada");
    return null;
  }

  const name = document.getElementById("user-name");
  const role = document.getElementById("user-role");

  if (name) {
    name.textContent = fullName(session.user);
  }

  if (role) {
    role.textContent = session.role;
  }

  for (const link of document.querySelectorAll(".logout-link")) {
    link.addEventListener("click", async (event) => {
      event.preventDefault();
      await logout();
      goToLogin("salir");
    });
  }

  return session;
}