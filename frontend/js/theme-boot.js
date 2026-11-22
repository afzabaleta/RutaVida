// Sets the colour theme before the page paints, so it does not flash.
// Loaded in <head> as a classic script (no defer, no module).
// Saved choice (localStorage "rutavida.theme") first, then the system theme.
(function () {
  var theme = null;

  try {
    theme = window.localStorage.getItem("rutavida.theme");
  } catch (error) {
    // Storage blocked: fall back to the system theme.
  }

  if (theme !== "light" && theme !== "dark") {
    var prefersLight = window.matchMedia
      && window.matchMedia("(prefers-color-scheme: light)").matches;
    theme = prefersLight ? "light" : "dark";
  }

  document.documentElement.setAttribute("data-theme", theme);
})();
