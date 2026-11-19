# RutaVida — Frontend Web

Aplicación web del **Sistema Inteligente de Rutas de Emergencia para Valledupar**.

El frontend está desarrollado con HTML, CSS y JavaScript. Utiliza Leaflet para la visualización geográfica y se comunica con el backend mediante una API REST desarrollada en Java.

## 1. Funcionalidades implementadas

La aplicación web permite:

- Iniciar sesión mediante la API REST.
- Consultar los tipos de emergencia disponibles en Oracle.
- Registrar emergencias y almacenarlas en Oracle.
- Consultar emergencias registradas.
- Consultar unidades de atención desde la API.
- Consultar hospitales y estaciones de bomberos desde la API.
- Visualizar emergencias, unidades y recursos geográficos en el panel principal.
- Identificar los recursos de demostración mediante la etiqueta `EJEMPLO`.
- Ver los indicadores del panel contados en Oracle (`GET /api/dashboard/summary`).
- Ver los incidentes viales abiertos sobre su tramo en el mapa, y validarlos, rechazarlos o cerrarlos (`/api/incidents`).
- Ver cada unidad en el mapa con el ícono de su vehículo (ambulancia, bomberos o patrulla) y un anillo con su estado (verde `DISPONIBLE`, naranja `OCUPADO`, gris en mantenimiento o fuera de servicio), y los hospitales, estaciones y emergencias con su propio ícono (`map-icons.js`). Las unidades se dibujan encima de los demás íconos, y las que están en el mismo punto (por ejemplo, en su estación) se reparten alrededor de él para que todas se vean.
- Despachar una unidad en `despacho.html`: las unidades ordenadas por su tiempo de llegada, las 3 rutas del motor con su explicación, el despacho, el estado de la misión y el cierre de la emergencia (D6b).

**Importante:** las unidades cuyo código comienza por `EJEMPLO-` son datos de demostración. No representan unidades operativas reales. El panel principal utiliza seguimiento D7 desde Oracle y la simulación del servidor. Las rutas de las misiones proceden de la API; no se calculan en el navegador.

El tiempo promedio de respuesta muestra «Sin datos» mientras ninguna unidad haya llegado al sitio hoy (las llegadas las registra el despacho, K7). Nunca se muestra un 0 inventado.

## 2. Estructura del frontend

```text
frontend/
├── css/
│   └── Estilos de la aplicación
├── data/
│   └── Datos geográficos y rutas de demostración
├── js/
│   ├── api-client.js
│   ├── dashboard.js
│   ├── despacho.js
│   ├── dispatch-routes.js
│   ├── emergencia.js
│   ├── map-icons.js
│   ├── page-session.js
│   ├── route-layer.js
│   ├── valledupar-data.js
│   └── Otros módulos JavaScript
├── vendor/
│   └── leaflet-1.9.4/   Copia local de Leaflet (ver sección 3)
├── index.html
├── dashboard.html
├── emergencia.html
├── despacho.html
└── README.md
```

El módulo `api-client.js` centraliza las solicitudes al backend. El módulo `dashboard.js` carga los datos del panel desde la API.

El archivo `example-data.js` fue retirado. Los datos de ejemplo que permanecen en Oracle se identifican mediante el prefijo `EJEMPLO-`.

## 3. Requisitos

Antes de ejecutar la aplicación se necesita:

- Java configurado en el entorno de desarrollo.
- Oracle Database disponible.
- Backend de RutaVida configurado correctamente.
- IntelliJ IDEA o un entorno compatible para ejecutar el servidor Java.

La configuración de conexión a Oracle debe mantenerse en el backend. No se deben publicar credenciales ni contraseñas.

### Leaflet sin depender de un CDN

Leaflet 1.9.4 va dentro del proyecto, en `vendor/leaflet-1.9.4/` (`leaflet.js`, `leaflet.css`, `images/` y su licencia BSD-2-Clause). Las páginas ya no lo descargan de `unpkg.com`, así que la Política de Seguridad de Contenido solo permite scripts y estilos del propio servidor (`'self'`).

- Los archivos salen del paquete oficial `leaflet@1.9.4` de npm, sin cambios: su SHA-256 es el mismo `integrity` que tenían las etiquetas del CDN (`leaflet.css`: `sha256-p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY=`, `leaflet.js`: `sha256-20nQCchB9co0qIjJZRGuk2/Z9VM+kNiyxNV1lvTlZBo=`).
- Para comprobarlo en Windows: `certutil -hashfile frontend\vendor\leaflet-1.9.4\leaflet.js SHA256` da el mismo valor en hexadecimal.
- **El mapa base sigue necesitando internet:** las teselas vienen de `tile.openstreetmap.org`. Sin conexión, la aplicación funciona y dibuja unidades, rutas e incidentes, pero sin el fondo de calles. Para la demostración conviene tener un plan B de conexión (por ejemplo, datos del celular).

## 4. Ejecución de la aplicación

El frontend se sirve desde el servidor Java del proyecto. No se debe abrir directamente el archivo HTML ni utilizar otro servidor independiente para estas pruebas.

### Paso 1. Iniciar Oracle

Verifica que Oracle Database esté iniciado y que la conexión configurada para RutaVida esté disponible.

### Paso 2. Iniciar el backend

1. Abre el proyecto en IntelliJ IDEA.
2. Abre el módulo `backend`.
3. Localiza la clase `WebServer`:

   `co.edu.unicesar.rutavida.app.WebServer`

4. Ejecuta su método `main`.
5. Comprueba que la consola muestre el mensaje:

   `[OK] RutaVida está en http://localhost:8080`

Mantén el servidor ejecutándose mientras utilizas la aplicación web.

### Paso 3. Abrir el frontend

En el navegador ingresa a:

`http://localhost:8080/index.html`

La aplicación mostrará la pantalla de inicio de sesión.

Después de iniciar sesión correctamente, podrás acceder al panel principal y a las páginas de registro y consulta.

También puedes comprobar el control de acceso abriendo directamente:

`http://localhost:8080/dashboard.html`

Si no existe una sesión válida, la aplicación debe redirigir al inicio de sesión.

## 5. Datos del panel principal (D5c)

El panel obtiene la información mediante los siguientes endpoints:

| Recurso | Endpoint |
|---|---|
| Emergencias | `GET /api/emergencies` |
| Unidades | `GET /api/units` |
| Hospitales | `GET /api/hospitals` |
| Estaciones | `GET /api/stations` |

Las secciones se cargan independientemente. Si una consulta falla, el panel muestra un mensaje de error en esa sección sin impedir necesariamente la carga de las demás.

### Identificación de los datos

- **ORACLE:** información consultada mediante la API.
- **EJEMPLO:** unidades de demostración identificadas por el prefijo `EJEMPLO-`.
- **D2:** hospitales y estaciones cuentan con información geográfica y fuentes documentadas en `valledupar-data.js`.
- **Rutas:** las misiones activas utilizan rutas obtenidas de la API REST mediante D7.

Cuando una unidad, estación u hospital no tiene coordenadas disponibles, se muestra «Sin posición conocida» y no se agrega un marcador al mapa.

El tiempo promedio de respuesta muestra «Sin datos» mientras ninguna unidad haya llegado al sitio hoy (las llegadas las registra el despacho, K7). Nunca se muestra un 0 inventado.

### Indicadores e incidentes viales (D6)

| Parte del panel | De dónde sale | Cada cuánto |
|---|---|---|
| Las 4 tarjetas de indicadores | `GET /api/dashboard/summary`: Oracle los cuenta en una sola consulta (A14) | 30 s |
| Incidentes viales (lista y líneas en el mapa) | `GET /api/incidents`: los `PENDIENTE` y `ACTIVO`, con su tramo (A12c) | 15 s |
| Validar, rechazar o cerrar un incidente | `PATCH /api/incidents/{id}/status` | Al hacer clic |

- **Línea punteada:** incidente `PENDIENTE`, todavía no afecta las rutas. **Línea continua:** `ACTIVO`. El color es la severidad.
- Los botones solo aparecen si el rol tiene `INCIDENTES:GESTIONAR`. Sin `REPORTES:CONSULTAR`, las tarjetas muestran los conteos de las listas.
- Las consultas automáticas no mantienen viva la sesión (DT-08). Si la sesión vence, el panel vuelve al inicio de sesión.
- Validar un incidente no recalcula nada en la página: el motor de rutas lo tiene en cuenta en el siguiente ciclo de la simulación (K8 y K9).
- Reportar un incidente nuevo desde la página queda para cuando la API pueda ubicar el tramo de un punto del mapa.

### Despacho y misión (D6b)

| Paso en `despacho.html` | Ruta de la API | Permiso |
|---|---|---|
| Unidades que pueden ir, la más rápida primero | `GET /api/emergencies/{id}/candidates` (6.1) | `DESPACHO:GESTIONAR` |
| Las 3 rutas de la unidad elegida, sin guardar nada | `POST /api/routes/preview` (6.2) | `RUTAS:GESTIONAR` |
| Despachar | `POST /api/dispatches` (6.3) | `DESPACHO:GESTIONAR` |
| Rutas de una misión ya despachada | `GET /api/dispatches/{id}/routes` (6.4) | `RUTAS:CONSULTAR` |
| La unidad salió, llegó o se cancela | `PATCH /api/dispatches/{id}/status` (6.6) | `DESPACHO:GESTIONAR` |
| Hospitales para el traslado, el más conveniente primero | `GET /api/dispatches/{id}/hospitals` (6.5) | `DESPACHO:GESTIONAR` |
| Trasladar al hospital elegido y finalizar la misión | `PATCH /api/dispatches/{id}/status` (6.6) con `TRASLADANDO` y `hospitalId`, después `FINALIZADA` | `DESPACHO:GESTIONAR` |
| Cerrar la emergencia | `POST /api/emergencies/{id}/close` (6.7) | `DESPACHO:GESTIONAR` |

- La página no calcula rutas: dibuja las que devuelve el servidor, con la explicación que él escribe.
- Una emergencia que no está `REPORTADA` muestra su misión abierta, que la página busca en el historial (`GET /api/emergencies/{id}/history`).
- Cerrar pide un segundo clic de confirmación. Una emergencia `REPORTADA` se puede cerrar como falsa alarma.
- **Despacho en vivo (D13):** `dispatch-live.js` consulta `GET /api/tracking` cada 5 segundos mientras existe una misión abierta y la pestaña está visible.
- La posición de la unidad se actualiza en el mapa y se identifica como `SIMULACION`.
- La página detecta cambios de estado, recálculos de rutas mediante `routesVersion` y la finalización de la misión.
- Las consultas automáticas de seguimiento no mantienen viva la sesión (DT-08).
- El seguimiento requiere `EMERGENCIAS:CONSULTAR`. Sin este permiso se conserva el botón «Actualizar estado».
- Al llegar una ambulancia a `EN_SITIO`, se actualiza el panel para permitir el traslado al hospital.
- **Traslado al hospital (CU-11):**
  - Con la misión `EN_SITIO` y una ambulancia, el botón «Elegir hospital para el traslado» muestra los hospitales con su tiempo, su distancia y si las urgencias están sin confirmar (suman 10 min al orden, RN-10).
  - Los hospitales se marcan en el mapa. «Trasladar aquí» pone la misión en `TRASLADANDO` y dibuja las rutas al hospital.
  - Al llegar, «Llegó al hospital: finalizar misión» deja la misión `FINALIZADA` y la ambulancia `DISPONIBLE`. Después se cierra la emergencia.
  - Una unidad que no es ambulancia no ve el botón.

## 6. Verificación funcional

Para comprobar la conexión entre el frontend, el backend y Oracle, realiza estas pruebas:

| Prueba | Resultado esperado |
|---|---|
| Abrir `index.html` | Se muestra el inicio de sesión |
| Iniciar sesión | Se accede al panel con el usuario autenticado |
| Consultar tipos de emergencia | Se muestran los tipos provenientes de Oracle |
| Registrar una emergencia | La API confirma el registro y lo almacena en Oracle |
| Consultar emergencias | Se muestran los registros disponibles |
| Consultar unidades | Se muestran los recursos disponibles y las unidades `EJEMPLO-` identificadas |
| Consultar hospitales | Se muestran los hospitales disponibles |
| Consultar estaciones | Se muestran las estaciones activas |
| Ver los indicadores | Las 4 tarjetas muestran los conteos de Oracle; el tiempo promedio dice «Sin datos» si nadie ha llegado hoy |
| Validar un incidente `PENDIENTE` | Pasa a `ACTIVO`, la línea deja de ser punteada y aparece el botón Cerrar |
| Despachar una unidad en `despacho.html` | La emergencia pasa a `ASIGNADA`, la unidad a `OCUPADO` y se ve la ruta `ACTIVA` |
| Cerrar la emergencia en `despacho.html` | Pide confirmación y la emergencia queda `CERRADA` |
| Abrir el panel sin sesión | Se redirige al inicio de sesión |
| Detener el backend | Se muestra un mensaje de error de conexión |

Para revisar posibles errores, abre las herramientas del navegador con `F12` y consulta la pestaña **Console** y la pestaña **Network**.

## 7. Detener la aplicación

Cuando termines las pruebas:

1. Regresa a IntelliJ IDEA.
2. Detén la ejecución de `WebServer` mediante el botón **Stop**.
3. Cierra el navegador si ya no necesitas la aplicación.

## 8. Tecnologías utilizadas

- HTML5.
- CSS3.
- JavaScript (módulos ES).
- Leaflet.
- OpenStreetMap.
- API REST desarrollada en Java.
- Oracle Database.

## 9. Documentación relacionada

- `README.md`: documentación general del proyecto.
- `backend/README.md`: configuración y ejecución del backend.
- `docs/`: documentación técnica, arquitectura y registros de implementación.

**Proyecto académico:** Sistema Inteligente de Rutas de Emergencia para Valledupar — RutaVida.
## 10. Mapa en vivo y alertas (D7)

El panel principal incorpora seguimiento de unidades y misiones mediante la API REST.

### Mapa en vivo

- `GET /api/tracking`: obtiene unidades, misiones abiertas, estado de simulación y alertas sin leer.
- La consulta se realiza cada 5 segundos mientras la pestaña está visible.
- Los marcadores se desplazan mediante una transición de 1,2 segundos.
- Las posiciones proceden del servidor; el navegador no calcula movimientos ni posiciones GPS.
- Las unidades de demostración se identifican como `EJEMPLO`.
- `live-map.js` administra los marcadores dinámicos para evitar duplicaciones.
- Las misiones muestran su estado y permiten visualizar su ruta activa.
- `GET /api/dispatches/{id}/routes`: consulta las rutas guardadas para una misión.
- Las rutas se actualizan cuando cambia `routesVersion`.
- La visualización de rutas requiere `RUTAS:CONSULTAR`.
- El seguimiento requiere `EMERGENCIAS:CONSULTAR`.

### Campana de alertas

- El contador de alertas sin leer proviene de `unreadAlerts` en `/api/tracking`.
- `GET /api/alerts`: carga las alertas al abrir la campana.
- `PATCH /api/alerts/{id}/read`: marca una alerta como leída.
- El panel muestra el mensaje, la fecha y la emergencia relacionada cuando existe.
- Las alertas corresponden al usuario autenticado.

### Archivos principales

| Archivo | Responsabilidad |
|---|---|
| `js/live-map.js` | Seguimiento, marcadores y rutas activas |
| `js/alert-bell.js` | Campana, listado y lectura de alertas |
| `js/api-client.js` | Consultas REST de seguimiento y alertas |
| `js/dashboard.js` | Integración del seguimiento con el panel |
| `dashboard.html` | Interfaz del mapa, misiones y campana |
| `css/dashboard.css` | Presentación visual y adaptación a pantallas pequeñas |

### Pruebas funcionales D7

Se verificaron satisfactoriamente:

1. Inicio de sesión y acceso al dashboard.
2. Carga del mapa de Valledupar.
3. Unidades sin marcadores duplicados.
4. Indicador de seguimiento activo y simulación.
5. Apertura de la campana de alertas.
6. Marcación de alertas como leídas.
7. Visualización de misiones y sus rutas activas.
8. Conservación del panel de incidentes e indicadores.

El seguimiento es una visualización de la simulación del servidor; no debe presentarse como rastreo GPS real.

## 11. Botón de pánico y alertas SOS (D9)

La aplicación incorpora una página pública para reportar
situaciones de emergencia y una capa de seguimiento SOS
en el dashboard.

### Página pública

- `panico.html`: interfaz del botón de pánico.
- `js/panico.js`: ubicación, selección de tipo y envío.
- `css/panico.css`: presentación adaptable a celulares.
- `js/api-client.js`: comunicación con la API REST.

El usuario puede utilizar su ubicación GPS, seleccionar
un punto en el mapa o usar la ubicación de demostración
del Parque La Popa.

El navegador genera un identificador de dispositivo
persistido en `localStorage`. No sustituye la
autenticación ni garantiza por sí solo una identidad única:
por eso, desde DT-13, el botón exige una cuenta (sección 12).

El número 123 se presenta como alternativa de contacto
con los servicios de emergencia.

### Validación de reportes

El frontend envía los reportes al backend. No decide
si un reporte es válido ni crea emergencias directamente.

La lógica de validación corresponde a `PanicService`.
El servidor puede validar un reporte mediante la
confirmación de tres personas (cuentas) distintas,
próximos entre sí, dentro de la ventana temporal
definida, o mediante un reporte policial autorizado.

### Visualización en el dashboard

- `js/panic-layer.js`: consulta y dibuja reportes SOS.
- `js/dashboard.js`: inicializa la capa independiente.
- `js/map-icons.js`: iconos SOS pendientes y validados.
- `dashboard.html`: panel de alertas y leyenda.
- `css/dashboard.css`: estilos del panel.

Los reportes pendientes se representan en ámbar y los
validados en rojo. La capa consulta periódicamente la API
cada 15 segundos.

El descarte se solicita al backend. La autorización y
las transiciones de estado se verifican en el servidor.

### API utilizada

| Operación | Endpoint |
|---|---|
| Tipos disponibles | `GET /api/panic/emergency-types` |
| Enviar SOS (con sesión, DT-13) | `POST /api/panic` |
| Crear la cuenta de ciudadano | `POST /api/citizens` |
| Consultar reportes | `GET /api/panic/reports` |
| Cambiar estado | `PATCH /api/panic/reports/{id}/status` |

### Pruebas D9

- Página pública `panico.html` cargada visualmente.
- Dashboard con panel Alertas SOS visible.
- Mapa, unidades e incidentes anteriores conservados.
- Integración de la capa SOS en `dashboard.js`.

Pendiente de verificación específica: respuestas reales
de la API, estados recibidos, permisos de descarte y
flujo completo de validación automática.

## 12. Cuenta del ciudadano para el botón de pánico (DT-13)

El profesor pidió que cada alerta tenga un responsable. Desde DT-13 el botón solo funciona con una cuenta.

### Archivos

- `registro.html` y `js/registro.js`: formulario para crear la cuenta (nombres, apellidos, documento, correo, usuario, contraseña y autorización de datos, Ley 1581 de 2012). Al crearla inicia sesión sola y lleva a `panico.html`.
- `panico.html` y `js/panico.js`: la sección `#panic-account` dice quién está conectado y permite cerrar sesión. Sin cuenta, el botón ENVIAR SOS queda apagado y aparecen "Iniciar sesión" y "Crear cuenta".
- `js/api-client.js`: `registerCitizen()`; el token del ciudadano se guarda en `localStorage` (el del personal sigue en `sessionStorage`); `homeOf()` decide a qué página va cada rol después del login.
- `index.html`, `js/login.js` y `css/login.css`: enlace "Crea tu cuenta de ciudadano"; un ciudadano que inicia sesión llega a `panico.html`, no al panel.

### Comportamiento

- La sesión del celular dura **30 días** y no vence por inactividad: la persona inicia sesión una vez.
- Las alertas quedan a nombre de la persona. **Dos alertas descartadas** por un coordinador en 30 días bloquean la cuenta (RN-16); el login responde "El usuario está inactivo".
- El policía inicia sesión con su cuenta y llega también a `panico.html`: su reporte valida la alerta solo.

### Cómo probarlo

1. Ejecutar `database/08_ciudadanos.sql` (una vez) y arrancar el servidor.
2. Abrir `http://localhost:8080/panico.html`: el botón está apagado y pide la cuenta.
3. "Crear cuenta", llenar los datos y marcar la autorización: la página vuelve al botón con "Conectado como …".
4. Cerrar el navegador y abrir de nuevo `panico.html`: sigue conectado.
5. Enviar una alerta con "Ubicación de prueba"; desde el panel, como coordinador, descartarla. Repetir: a la segunda, la cuenta queda bloqueada y el panel muestra la alerta del bloqueo.

Probado con un servidor falso de la API en Chromium (registro, sesión persistente, envío, cierre de sesión, login del ciudadano). Falta la prueba con el servidor real y Oracle.

## 13. Rediseño visual con menú lateral

Cambio solo visual: la lógica, las llamadas a la API y los datos no cambian.

- `js/app-shell.js` arma el menú lateral y el encabezado de todas las páginas internas. Lo llama `startPage()` (`page-session.js`) después de validar la sesión. Estilos en `css/app-shell.css`.
- Cada opción del menú aparece solo si el rol tiene su permiso (`hasPermission`); un grupo sin opciones no se muestra. La opción activa lleva `aria-current="page"`.
- «Historial y reportes» está en la lista con `available: false` hasta que se fusione `reportes.html` (D10). «Administración» se ve gris y no es un enlace hasta D11.
- En pantallas de menos de 900 px el menú es un cajón que se abre con ☰ y se cierra con el fondo, con Escape o al elegir una opción.
- La campana de alertas (`alert-bell.js`, sin cambios en su lógica) está en el encabezado del panel, de `emergencia.html` y de `despacho.html`. Fuera del panel, el contador se actualiza al abrir la campana.
- El mapa oscuro es un filtro CSS sobre `.leaflet-tile-pane`: solo cambian las teselas de OpenStreetMap; los íconos, las rutas y la atribución conservan sus colores.
- Los íconos son SVG propios en `img/icons/`. No se agregaron librerías, fuentes ni CDN.
- **Modo claro y oscuro:** el botón ☀/☾ del encabezado cambia el tema. La primera vez se usa el tema del sistema; la elección se guarda en `localStorage` (`rutavida.theme`), y si el navegador lo bloquea, el botón igual funciona. `js/theme-boot.js` pone el tema en el `<head>` antes de pintar, para que no parpadee, y `css/theme.css` redefine las variables de color del modo claro. En modo claro el mapa va sin filtro. Solo aplica a las páginas internas; el login, el botón de pánico y el registro del ciudadano (`registro.html`, que usa `css/panico.css`) conservan su diseño.
- Ningún texto funcional mide menos de 12 px, para que el panel se lea proyectado.


## D10. Historial y reportes

La pagina reportes.html permite consultar indicadores historicos desde Oracle.

- Archivos: reportes.html, css/reportes.css y js/reportes.js.
- API: GET /api/reports/operations?from=&to=
- API: GET /api/emergencies?from=&to=
- API: GET /api/emergencies/{id}/history
- Permisos: REPORTES:CONSULTAR y EMERGENCIAS:CONSULTAR.
- Periodos: hoy, 7 dias, 30 dias y fechas personalizadas.
- Indicadores: emergencias, tiempo de respuesta, despachos e incidentes.
- Graficos por dia, tipo, gravedad y tipo de vehiculo.
- Historial de emergencias, misiones, rutas y auditoria.
- Exportacion CSV e impresion en PDF.
- Interfaz responsive para computadores y celulares.
- Los indicadores proceden de Oracle; no se inventan resultados.

Pendiente: verificar el funcionamiento real y la visualizacion movil.


## D12. Panel de simulación

El centro de operaciones permite controlar la simulación de unidades y visualizar el tráfico simulado sobre el mapa de Valledupar.

### Funcionalidades

- Consultar el estado de la simulación cada 5 segundos.
- Iniciar y detener la simulación.
- Congestionar el tercio central de una ruta activa.
- Liberar los tramos congestionados desde el panel.
- Actualizar la capa de tráfico cada 15 segundos.
- Mostrar avisos de recálculo de rutas durante 10 segundos.
- Adaptar los controles a computadores, tabletas y celulares.

### Archivos

| Archivo | Responsabilidad |
|---|---|
| `dashboard.html` | Estructura del panel y leyenda de tráfico. |
| `js/simulation-panel.js` | Control de la simulación, tráfico, permisos y avisos. |
| `js/dashboard.js` | Integración del panel con el centro de operaciones. |
| `js/live-map.js` | Detección de cambios en las rutas activas. |
| `js/api-client.js` | Comunicación con la API REST de simulación. |
| `js/app-shell.js` | Indicador de estado en el menú lateral. |
| `css/app-shell.css` | Estilos del indicador de simulación. |
| `css/simulacion.css` | Estilos del panel y adaptación responsive. |

### Endpoints

| Método | Endpoint | Función |
|---|---|---|
| GET | `/api/simulation` | Consultar estado. |
| POST | `/api/simulation/start` | Iniciar simulación. |
| POST | `/api/simulation/stop` | Detener simulación. |
| POST | `/api/simulation/traffic` | Modificar tráfico de un tramo. |
| GET | `/api/traffic/segments` | Consultar tramos con tráfico. |

### Permisos

- `TRAFICO:CONSULTAR`: visualizar el estado y la capa de tráfico.
- `TRAFICO:GESTIONAR`: iniciar y detener la simulación.
- `TRAFICO:GESTIONAR` y `RUTAS:CONSULTAR`: congestionar rutas y liberar los tramos modificados.

### Colores del tráfico

- Naranja (`#f59e0b`): tráfico moderado.
- Rojo (`#e11d48`): tráfico congestionado.

Todos los movimientos y datos de tráfico son simulados. No corresponden a GPS ni a tráfico real.

El cálculo y recálculo de rutas es responsabilidad del backend. El frontend consulta y representa los resultados.
