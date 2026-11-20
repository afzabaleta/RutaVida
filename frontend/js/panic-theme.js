// Visual theme control for the panic page. It shares the dashboard preference.
(function () {
  var buttons = document.querySelectorAll("[data-panic-theme]");
  function currentTheme() { return document.documentElement.getAttribute("data-theme") === "light" ? "light" : "dark"; }
  function sync() {
    var theme = currentTheme();
    buttons.forEach(function (button) { button.setAttribute("aria-pressed", String(button.dataset.panicTheme === theme)); });
  }
  buttons.forEach(function (button) {
    button.addEventListener("click", function () {
      var theme = button.dataset.panicTheme;
      document.documentElement.setAttribute("data-theme", theme);
      try { window.localStorage.setItem("rutavida.theme", theme); } catch (error) { /* Keep the in-page choice. */ }
      sync();
    });
  });
  sync();
})();
