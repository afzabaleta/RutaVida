# RutaVida

**Sistema Inteligente de Rutas de Emergencia para Valledupar**

RutaVida es un sistema orientado a apoyar la gestión de emergencias en Valledupar mediante el registro de incidentes, la consulta de recursos de atención y la visualización de información geográfica para facilitar la coordinación de la respuesta.

## Estado del proyecto

El proyecto se desarrolla por etapas académicas. Los módulos implementados y pendientes se identifican en la documentación correspondiente.

| Componente | Estado |
|---|---|
| Backend | API REST desarrollada en Java |
| Base de datos | Oracle |
| Vista | **Web** (HTML, CSS, JavaScript y Leaflet + API REST en Java), en `frontend/`. Conectada a la API (D5b): inicio de sesión, registro de emergencias y consulta de emergencias y unidades de Oracle. El panel principal y el botón de despachar (K7) están pendientes |
| Rutas geográficas | Visualización de rutas de demostración; integración operativa pendiente de K7 |
| Despacho | Pendiente de integración operativa K7 |

## Funcionalidades de la aplicación web

La aplicación web permite:

- Iniciar sesión mediante la API.
- Consultar los tipos de emergencia disponibles en Oracle.
- Registrar emergencias y almacenarlas en Oracle.
- Consultar emergencias y unidades disponibles.
- Visualizar información geográfica de demostración.

**Importante:** las unidades y los datos que aparecen identificados como `EJEMPLO` son datos de demostración. No representan recursos operativos reales.

El despacho operativo de unidades, el seguimiento y la simulación de rutas continúan pendientes de la etapa K7.

## Tecnologías

- Java.
- Oracle Database.
- HTML5.
- CSS3.
- JavaScript.
- Leaflet.
- OpenStreetMap.

## Estructura del proyecto

```text
RutaVida/
├── backend/
│   └── Código de la API REST y configuración del servidor
├── frontend/
│   ├── css/
│   ├── data/
│   ├── js/
│   ├── dashboard.html
│   ├── despacho.html
│   ├── emergencia.html
│   ├── login.html
│   └── README.md
├── database/
│   └── Scripts SQL de la base de datos
├── docs/
│   └── Documentación técnica y diagramas
└── README.md
```

## Ejecución

La configuración y las instrucciones para ejecutar el backend y el frontend se encuentran en sus respectivos archivos README.

Consulta:

- `backend/README.md`
- `frontend/README.md`

No publiques archivos con credenciales, contraseñas o información privada de conexión a la base de datos.

## Documentación

La documentación técnica del proyecto se encuentra en `docs/` e incluye la arquitectura del software, diagramas y registros de implementación.

## Proyecto académico

Desarrollado como parte de las asignaturas de Programación de Computadores III y Base de Datos.

**Proyecto:** Sistema Inteligente de Rutas de Emergencia para Valledupar — RutaVida.