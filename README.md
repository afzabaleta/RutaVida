# RutaVida

**Sistema Inteligente de Rutas de Emergencia para Valledupar**

Proyecto de Programación de Computadores III + Base de Datos — Ingeniería de Sistemas, Universidad Popular del Cesar.

> Prototipo académico. No reemplaza un servicio oficial de despacho y no debe usarse para coordinar emergencias reales.

## Equipo

| Integrante | Responsabilidad |
|---|---|
| Andrés (líder) | Base de datos Oracle, DAO e integración |
| Kendrick | Service y motor de rutas |
| Diego | Vista (interfaz) y versión móvil |
| Sherly | Model, Controller, Util y pruebas |

## Estructura del repositorio

```
RutaVida/
├── backend/            Proyecto Java 21 + Maven (paquete co.edu.unicesar.rutavida)
│   ├── pom.xml
│   ├── db.properties.example   ← plantilla; la copia real (db.properties) no se sube
│   └── src/
├── database/           Scripts SQL y PL/SQL de Oracle (en orden numérico)
├── frontend/           Interfaz web: HTML, CSS, JavaScript y Leaflet (ver frontend/README.md)
└── docs/               Documentación del proyecto (en español)
```

## Estado actual

| Componente | Estado |
|---|---|
| Esquema de base de datos (23 tablas, 21 secuencias, 24 índices, restricciones) | Ejecutado y verificado en Oracle XE 18c con `98_verificar.sql` (14/10/2026) |
| Datos maestros (roles, permisos, tipos de vehículo y de emergencia) | Cargados y verificados |
| Conexión JDBC (`DatabaseConnection`) y verificación (`DatabaseCheck`) | Funciona: "23 de 23" con el driver ojdbc11 21.9 |
| Arquitectura: paquetes, reglas de dependencia, enums, transacciones, `AppContext` | Programado y probado ([`docs/arquitectura-software.md`](docs/arquitectura-software.md)). Desde el 30/10/2026 el modelo solo guarda datos y las reglas están en `service` |
| Vistas, funciones, procedimientos y triggers PL/SQL (`04_plsql.sql`) | Programado; se prueba con la sección 8 de `98_verificar.sql` |
| Entidades, DAO principales, motor de rutas, inicio de sesión y reglas de estado | Programado y probado (detalle en [`docs/class-diagram.md`](docs/class-diagram.md), sección 9) |
| API REST ([`docs/api.md`](docs/api.md)) | Programado y probado: sesión con token (A12a), emergencias y catálogos (A12b). Despacho, rutas, seguimiento y simulación: pendiente (K7) |
| Casos de uso de despacho completos y simulación | Pendiente |
| Vista | **Web** (HTML, CSS, JavaScript y Leaflet + API REST en Java), en `frontend/`. Conectada a la API (D5b): inicio de sesión, registro de emergencias y consulta de emergencias y unidades de Oracle. El panel principal y el botón de despachar (K7) están pendientes |
| Decisiones del equipo (vista web, Oracle local, tablas en español) | Registradas en [`docs/decisiones-tecnicas.md`](docs/decisiones-tecnicas.md) |

## Puesta en marcha (cada integrante, una sola vez)

### 1. Base de datos local

Requisito: Oracle Database XE instalado y en ejecución, con la PDB `XEPDB1` (puerto 1521).

Desde la consola de Windows (`cmd`), con SQL*Plus, que viene con Oracle XE:

```
cd ruta\a\RutaVida\database
sqlplus /nolog
```

Dentro de `SQL>`:

```
CONNECT system/CONTRASEÑA_DE_SYSTEM@localhost:1521/XEPDB1
@00_crear_usuario.sql
CONNECT rutavida/CONTRASEÑA_DE_RUTAVIDA@localhost:1521/XEPDB1
SPOOL instalacion.log
@01_esquema.sql
@02_datos_iniciales.sql
@03_datos_valledupar.sql
@04_plsql.sql
@05_red_vial_valledupar.sql
@06_unidades_ejemplo.sql
@98_verificar.sql
SPOOL OFF
exit
```

- `00_crear_usuario.sql` pide la contraseña del nuevo usuario `RUTAVIDA` (solo se ejecuta una vez).
- `06_unidades_ejemplo.sql` crea 5 unidades de **EJEMPLO** (códigos `EJEMPLO-…`, sin placa) y una base de ambulancias de ejemplo para la simulación. No son unidades reales.
- En `98_verificar.sql` todas las columnas `OK` deben decir `SI`, y cada línea de la prueba 8 (PL/SQL) debe terminar en `SI`. Las dos pruebas marcadas "DEBE FALLAR" deben mostrar `ORA-02290`: eso es correcto.
- También sirve SQL Developer (host `localhost`, puerto `1521`, servicio `XEPDB1`, ejecutar cada archivo con F5).
- En los scripts, un comentario nunca va después del `;` en la misma línea: SQL*Plus lo envía como parte de la sentencia. `SqlScriptStyleTest` lo verifica.

`database/99_eliminar_esquema.sql` borra todas las tablas. **Solo se ejecuta con autorización explícita del líder**; después hay que repetir desde `01`.

### 2. Backend

1. En IntelliJ IDEA: *File → Open* → seleccionar la carpeta `backend/` (se abre como proyecto Maven y descarga las dependencias).
2. Copiar `backend/db.properties.example` como `backend/db.properties` y escribir la contraseña.
3. Ejecutar la clase `co.edu.unicesar.rutavida.DatabaseCheck`. Resultado esperado:
   ```
   [OK] Conexión establecida: ...
   Tablas de RutaVida encontradas: 23 de 23
   [OK] El esquema está completo.
   ```
4. Ejecutar las pruebas: clic derecho sobre `src/test/java` → *Run 'All Tests'* (o `mvn test`).

5. Crear el primer administrador (una sola vez por base de datos): ejecutar `co.edu.unicesar.rutavida.app.CreateAdminUser` y responder lo que pregunta. La contraseña queda guardada como hash PBKDF2, y al final la herramienta comprueba que el administrador puede iniciar sesión.

### 3. Red vial (OpenStreetMap)

`database/05_red_vial_valledupar.sql` carga `NODO_VIAL` y `TRAMO_VIAL` con las calles de la zona de la demostración. Lo genera la herramienta `co.edu.unicesar.rutavida.app.OsmImporter` y no se edita a mano. Para volver a generarlo:

1. Descargar los datos con la consulta guardada (desde la carpeta `RutaVida`, en PowerShell):
   ```
   curl.exe -o database\osm\valledupar_demo.osm --data-urlencode "data@database\osm\valledupar_demo.overpassql" https://overpass-api.de/api/interpreter
   ```
2. Ejecutar `OsmImporter` con los argumentos `../database/osm/valledupar_demo.osm ../database/05_red_vial_valledupar.sql AAAA-MM-DD` (fecha de la descarga).

El archivo `.osm` no se sube al repositorio (`.gitignore`). Datos de la red vial: © OpenStreetMap contributors, licencia [ODbL](https://www.openstreetmap.org/copyright).

### 4. Demostración del motor de rutas

Con la red vial cargada (paso 3) y `03_datos_valledupar.sql` ejecutado, la clase `co.edu.unicesar.rutavida.app.RouteDemo` (carpeta de trabajo `backend/`) calcula la ruta recomendada y dos alternativas desde la estación de bomberos hasta una emergencia de EJEMPLO en el Parque La Popa, compara A* con Dijkstra, aplica una congestión SIMULADA, recalcula y calcula el traslado al Hospital Rosario Pumarejo. No escribe nada en Oracle.

Deja el resultado en `frontend/data/rutas-demo.geojson` y en `frontend/data/rutas-demo.js` (el mismo contenido, para abrir la página sin servidor). Los tiempos son estimados con las velocidades supuestas del importador; no son tiempos medidos.

### 5. Servidor web y API (A12a y A12b)

Con Oracle encendido, ejecutar `co.edu.unicesar.rutavida.app.WebServer` (carpeta de trabajo `backend/`). Debe mostrar `[OK] RutaVida está en http://localhost:8080`. El mismo servidor entrega las páginas de `frontend/` y la API de [`docs/api.md`](docs/api.md); se detiene con el botón *Stop* de IntelliJ. Si el puerto 8080 está ocupado, se puede pasar otro como argumento (por ejemplo `8081`).

Prueba rápida desde PowerShell, con un usuario creado con `CreateAdminUser`. El usuario y la contraseña se escriben cuando la consola los pide, nunca en un archivo del repositorio:

```powershell
$user = Read-Host "Usuario"
$pass = Read-Host "Contraseña"
$body = @{ username = $user; password = $pass } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/auth/login -ContentType "application/json" -Body $body
$token = $login.value.token
$auth = @{ Authorization = "Bearer $token" }
Invoke-RestMethod -Uri http://localhost:8080/api/emergency-types -Headers $auth
```

Registrar una emergencia de prueba y leerla (la emergencia queda guardada en Oracle; la descripción la marca como prueba):

```powershell
$emergencia = @{
  emergencyTypeId  = 3
  addressReference = "Frente al Parque La Popa"
  location         = @{ latitude = 10.467172; longitude = -73.265099 }
  severity         = "CRITICA"
  description      = "PRUEBA de la API, no es una emergencia real"
} | ConvertTo-Json
$nueva = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/emergencies -Headers $auth -ContentType "application/json; charset=utf-8" -Body $emergencia
$id = $nueva.value.id
Invoke-RestMethod -Uri http://localhost:8080/api/emergencies/$id/history -Headers $auth | ConvertTo-Json -Depth 6
Invoke-RestMethod -Uri http://localhost:8080/api/units -Headers $auth | ConvertTo-Json -Depth 4
```

## Trabajo en equipo

- **Cómo trabajar con Git, ramas y Pull Requests:** [`CONTRIBUTING.md`](CONTRIBUTING.md).
- **Tareas de cada integrante:** [`docs/plan-de-trabajo.md`](docs/plan-de-trabajo.md).

## Convenciones

- **Idioma:** documentación y Pull Requests en español. **Ramas y commits en inglés.** Código fuente (clases, métodos, variables, comentarios) en inglés. Textos que ve el usuario, en español.
- **Casos de uso:** [`docs/analysis.md`](docs/analysis.md). **Diagrama de clases (Mermaid):** [`docs/class-diagram.md`](docs/class-diagram.md). `ClassDiagramSyncTest` falla si una clase del código no aparece en el diagrama.
- **Arquitectura, paquetes, GRASP y SOLID:** ver [`docs/arquitectura-software.md`](docs/arquitectura-software.md). `ArchitectureTest` falla si una capa importa algo que no le corresponde.
- **Base de datos:** ver [`docs/convenciones-base-datos.md`](docs/convenciones-base-datos.md) (estados, catálogos y cambios al diccionario de datos).
- **Contrato de la API REST:** [`docs/api.md`](docs/api.md) (rutas, permisos, JSON y errores). La web y el servidor programan contra este documento.
- **Ramas (en inglés):** `main` (estable, rama por defecto), `develop` (integración), `feature/<name>`, `fix/<name>`, `docs/<name>`, `test/<name>`, `refactor/<name>`, `chore/<name>`.
- **Commits:** `type: short-description` en inglés, por ejemplo `feature: dao-emergencies`. Tipos: `feature`, `fix`, `docs`, `test`, `refactor`, `chore`.
- Nadie hace commit ni push directo a `main` ni a `develop`: todo entra por Pull Request hacia `develop`, revisado por otra persona.
- **Las ramas no se eliminan**, ni después del merge. Detalles en [`CONTRIBUTING.md`](CONTRIBUTING.md).
- Las credenciales nunca se suben al repositorio.

## Actualización D5c — Panel principal conectado a la API

El panel principal (`frontend/dashboard.html`) consulta los datos mediante la API REST:

| Recurso | Endpoint |
|---|---|
| Emergencias | `GET /api/emergencies` |
| Unidades | `GET /api/units` |
| Hospitales | `GET /api/hospitals` |
| Estaciones y bases | `GET /api/stations` |

- Las emergencias se identifican como datos de Oracle.
- Las unidades con códigos `EJEMPLO-` se identifican como datos de EJEMPLO.
- Las unidades sin coordenadas muestran "Sin posición conocida" y no generan marcadores en el mapa.
- Hospitales y estaciones se consultan desde la API.
- Las rutas demostrativas de D4 se conservan y se identifican como ejemplos.
- El tiempo de respuesta permanece como "Sin datos" hasta implementar el despacho K7.
- Cada sección del panel puede cargarse independientemente si falla otra consulta.
- El despacho de unidades y el cálculo de tiempos reales siguen pendientes de K7.

La documentación específica del frontend está en [`frontend/README.md`](frontend/README.md).
