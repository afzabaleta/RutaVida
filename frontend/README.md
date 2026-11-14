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
- Ver cada unidad en el mapa con el ícono de su vehículo (ambulancia, bomberos o patrulla) y un anillo con su estado (verde `DISPONIBLE`, naranja `OCUPADO`, gris en mantenimiento o fuera de servicio), y los hospitales, estaciones y emergencias con su propio ícono (`map-icons.js`).
- Despachar una unidad en `despacho.html`: las unidades ordenadas por su tiempo de llegada, las 3 rutas del motor con su explicación, el despacho, el estado de la misión y el cierre de la emergencia (D6b).

**Importante:** las unidades cuyo código comienza por `EJEMPLO-` son datos de demostración. No representan unidades operativas reales. Las rutas del panel principal continúan siendo rutas de demostración (D4); las de `despacho.html` las calcula el motor de rutas en el servidor (D6b).

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
- **Rutas:** continúan siendo rutas de demostración hasta la integración operativa de K7.

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
| Cerrar la emergencia | `POST /api/emergencies/{id}/close` (6.7) | `DESPACHO:GESTIONAR` |

- La página no calcula rutas: dibuja las que devuelve el servidor, con la explicación que él escribe.
- Una emergencia que no está `REPORTADA` muestra su misión abierta, que la página busca en el historial (`GET /api/emergencies/{id}/history`).
- Cerrar pide un segundo clic de confirmación. Una emergencia `REPORTADA` se puede cerrar como falsa alarma.
- La página no se actualiza sola, porque esas consultas mantendrían viva la sesión (DT-08): se usa «Actualizar estado». El seguimiento en vivo llega con `/api/tracking` (A12e).
- El traslado al hospital (`TRASLADANDO`) llega con K7b.
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