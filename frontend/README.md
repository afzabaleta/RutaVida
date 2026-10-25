# Frontend de RutaVida (maqueta D3)

Interfaz web de RutaVida (decisión DT-01): HTML, CSS, JavaScript y Leaflet.

**Todavía no está conectada al backend**: la API REST se desarrolla en la fase 7.

| Archivo | Qué es |
|---|---|
| `index.html`, `js/login.js` | Inicio de sesión de ejemplo. No envía ni guarda contraseñas. |
| `dashboard.html`, `js/dashboard.js`, `css/dashboard.css` | Panel principal, mapa, indicadores, emergencias, unidades y hospitales. |
| `css/rutavida.css` | Colores, tipografía y componentes comunes. |
| `js/valledupar-data.js` | Estación y hospitales reales documentados en D2, con sus fuentes. |
| `js/example-data.js` | Usuario, emergencias y unidades de ejemplo; no son datos reales. |
| `img/logo.svg` | Logo de RutaVida. |

## Cómo abrir la maqueta

Desde la raíz del repositorio, inicia un servidor local:

```powershell
jwebserver -d "$PWD\frontend" -p 8000
```

Abre <http://localhost:8000/> en el navegador. No abras los HTML con doble clic, porque los módulos JavaScript y el mapa requieren un servidor local.

Para detener el servidor, pulsa `Ctrl+C`.

## Reglas

- La maqueta no realiza autenticación real ni se conecta al backend.
- Los datos de ejemplo deben estar identificados como tales.
- Los datos reales conservan sus fuentes.
- El mapa usa Leaflet y OpenStreetMap con su atribución visible.
- No se deben inventar métricas de respuesta ni presentar datos de ejemplo como reales.
