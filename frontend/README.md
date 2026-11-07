
# Frontend de RutaVida (maqueta D3, rutas D4 y pantallas de operación D5a)

Interfaz web de RutaVida (decisión DT-01): HTML, CSS, JavaScript y Leaflet.

**Todavía no está conectada al backend:** la API REST se desarrolla en la fase 7.

| Archivo | Qué es |
|---|---|
| `index.html`, `js/login.js` | Inicio de sesión de ejemplo. No envía ni guarda contraseñas. |
| `dashboard.html`, `js/dashboard.js`, `css/dashboard.css` | Panel principal, mapa, indicadores, emergencias, unidades, hospitales y rutas. |
| `js/route-layer.js` | Capa visual de rutas D4: escenarios, alternativas, distancias, tiempos y explicaciones. |
| `data/rutas-demo.js` | Datos de rutas generados para la demostración por el motor RouteDemo. |
| `css/rutavida.css` | Colores, tipografía y componentes comunes. |
| `js/valledupar-data.js` | Estación y hospitales documentados en D2, con sus fuentes. |
| `js/example-data.js` | Usuario, emergencias y unidades de ejemplo; no son datos reales. |
| `img/logo.svg` | Logo de RutaVida. |
| `emergencia.html`, `js/emergencia.js` | Registrar emergencia (CU-06): formulario y punto en el mapa. Datos de EJEMPLO, no se guardan en Oracle. |
| `despacho.html`, `js/despacho.js` | Despacho (CU-08 y CU-09): emergencia, unidades disponibles, rutas y confirmación. Datos de EJEMPLO. |
| `js/api-client.js` | Único lugar donde las páginas piden y envían datos. Hoy responde con EJEMPLOS; con la API (A12) usará `fetch` según `docs/api.md`. |
| `js/map-base.js` | Mapa base, marcadores y utilidades comunes de las páginas de operación. |
| `css/operacion.css` | Estilos de los formularios de operación. |

## Cómo abrir la maqueta

Desde la raíz del repositorio, inicia un servidor local:

```powershell
jwebserver -d "$PWD\frontend" -p 8000
```

Abre <http://localhost:8000/dashboard.html> para ver el panel principal, o <http://localhost:8000/> para abrir la página de inicio si está configurada como página predeterminada.

No abras los HTML con doble clic: los módulos JavaScript y el mapa requieren un servidor local.

Para detener el servidor, pulsa `Ctrl+C`.

## Rutas de demostración (D4)

La capa de rutas utiliza los datos de `data/rutas-demo.js`, generados para la demostración por RouteDemo. Presenta tres escenarios:

1. Estación hacia la emergencia.
2. Rutas después de una congestión simulada.
3. Traslado desde la emergencia hacia el hospital.

Las rutas muestran alternativas, distancias, tiempos estimados y explicaciones disponibles en los datos. La congestión y la unidad simuladas son elementos demostrativos, no información operativa en tiempo real.

## Reglas

- La maqueta no realiza autenticación real ni se conecta al backend.
- Los datos de ejemplo y simulación deben estar identificados como tales.
- Los datos documentados como reales conservan sus fuentes.
- El mapa usa Leaflet y OpenStreetMap con su atribución visible.
- No se deben inventar métricas de respuesta ni presentar datos de ejemplo como reales.
