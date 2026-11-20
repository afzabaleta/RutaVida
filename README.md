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
| Botón de pánico en la base de datos (`07_boton_panico.sql`: tabla 24 `REPORTE_PANICO`, rol y tipo de vehículo `POLICIA`, permisos `PANICO`, usuario del sistema) y su DAO (A20) | Programado; prueba con Oracle pendiente |
| Cuentas de ciudadano del botón de pánico (`08_ciudadanos.sql`: tabla 25 `SESION_CIUDADANO`, rol `CIUDADANO`, permiso `PANICO/REPORTAR`; DT-13) | Programado y probado sin Oracle; prueba con Oracle pendiente |
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
@07_boton_panico.sql
@08_ciudadanos.sql
@10_hospitales_y_unidades.sql
@11_patrullas_ejemplo.sql
@98_verificar.sql
SPOOL OFF
exit
```

- `00_crear_usuario.sql` pide la contraseña del nuevo usuario `RUTAVIDA` (solo se ejecuta una vez).
- `06_unidades_ejemplo.sql` crea 5 unidades de **EJEMPLO** (códigos `EJEMPLO-…`, sin placa) y una base de ambulancias de ejemplo para la simulación. No son unidades reales.
- `07_boton_panico.sql` crea la tabla `REPORTE_PANICO`, el rol y el tipo de vehículo `POLICIA`, los permisos del módulo `PANICO` y el usuario `sistema.rutavida` (inactivo: nadie inicia sesión con él). Al final muestra 7 líneas que deben decir `SI`.
- `08_ciudadanos.sql` (DT-13) crea el rol `CIUDADANO`, el permiso `PANICO/REPORTAR` y la tabla `SESION_CIUDADANO` (tabla 25), donde viven las sesiones de 30 días del celular. Se puede ejecutar dos veces sin error. Al final muestra 6 líneas que deben decir `SI`.
- `10_hospitales_y_unidades.sql` (DT-14) agrega 5 hospitales y clínicas **reales**, con su posición de OpenStreetMap y la fuente de cada dato. También crea 4 bases de **EJEMPLO** (norte, occidente, oriente y sur) con 10 unidades `EJEMPLO-…` más, para que el despacho se reparta por la ciudad. Necesita la red vial de toda el área urbana.
- `11_patrullas_ejemplo.sql` (DT-10) crea 6 patrullas de policía de **EJEMPLO** (`EJEMPLO-POL-01` a `06`) en las bases de la ciudad. Mientras la simulación está en marcha recorren la ciudad solas: cada una va a un punto al azar a 0,8–3 km y al llegar elige otro. Sus posiciones son SIMULACION y no se despachan a emergencias. Para el usuario de policía (su reporte de pánico valida la alerta solo), créelo con el rol `POLICIA` desde Administración.
- En `98_verificar.sql` todas las columnas `OK` deben decir `SI`, y cada línea de la prueba 8 (PL/SQL) debe terminar en `SI`. Las dos pruebas marcadas "DEBE FALLAR" deben mostrar `ORA-02290`: eso es correcto.
- También sirve SQL Developer (host `localhost`, puerto `1521`, servicio `XEPDB1`, ejecutar cada archivo con F5).
- En los scripts, un comentario nunca va después del `;` en la misma línea: SQL*Plus lo envía como parte de la sentencia. `SqlScriptStyleTest` lo verifica.

`database/99_eliminar_esquema.sql` borra todas las tablas. **Solo se ejecuta con autorización explícita del líder**; después hay que repetir desde `01`.

`database/09_reiniciar_demo.sql` deja la base lista para una demostración. Borra los datos de operación: emergencias, despachos, rutas, posiciones, tráfico, incidentes viales, alertas, notificaciones y reportes de pánico. Además devuelve las unidades `EJEMPLO-` a su estación.
- **Qué conserva:** usuarios, auditoría, red vial, hospitales, estaciones y unidades.
- **Cómo se usa:** se detiene `WebServer` antes, el script pide escribir `REINICIAR` y después se vuelve a iniciar el servidor.
- **Cuándo:** antes de cada ensayo y antes de la exposición.

#### Base de datos en Oracle Cloud (DT-09)

La base compartida del equipo y de la sustentación es una **Autonomous Database Always Free** (Oracle 19c, región Colombia Central - Bogotá). Se conecta con el *wallet* de la base (mTLS). El código no cambia: solo `backend/db.properties`.

1. Pedirle al líder, **por privado**, el archivo `Wallet_rutavida.zip` y la contraseña de `RUTAVIDA`. Nunca se suben a GitHub ni se mandan al grupo.
2. Guardar el zip **fuera del repositorio**, por ejemplo en `C:\oracle\wallet\`, y descomprimirlo ahí mismo: queda la carpeta `C:\oracle\wallet\Wallet_rutavida\`.
3. **SQL Developer:** conexión nueva con usuario `RUTAVIDA`, tipo de conexión *Cartera de Cloud*, el archivo `.zip` y el servicio `rutavida_low`.
4. **Backend:** en `db.properties` activar la línea de la nube de `db.properties.example`, con la ruta de la carpeta descomprimida y barras `/`:
   ```
   db.url=jdbc:oracle:thin:@rutavida_low?TNS_ADMIN=C:/oracle/wallet/Wallet_rutavida
   ```
   y ejecutar `DatabaseCheck`: debe decir "25 de 25" y "Oracle Database 19c".
   Si dice "Database password not configured", la configuración de ejecución de IntelliJ (*Run → Edit Configurations → Working directory*) debe terminar en `\backend`, donde está `db.properties`.

Solo el líder instala o reinstala el esquema en la nube: conectado como `ADMIN`, `00_crear_usuario_nube.sql`; luego, como `RUTAVIDA`, los scripts `01` a `08` y `98`. El `05` se ejecuta desde una hoja de trabajo con `SET FEEDBACK OFF` antes de `@05_red_vial_valledupar.sql`, porque son casi 17 000 inserciones por internet.

Límites del plan gratuito: 30 sesiones simultáneas, **se detiene sola después de 7 días sin uso** (se vuelve a iniciar desde la consola, *Start*) y puede borrarse si queda detenida 90 días. Las pruebas se corren contra la base **local**; la nube es para la demostración.

### 2. Backend

1. En IntelliJ IDEA: *File → Open* → seleccionar la carpeta `backend/` (se abre como proyecto Maven y descarga las dependencias).
2. Copiar `backend/db.properties.example` como `backend/db.properties` y escribir la contraseña.
3. Ejecutar la clase `co.edu.unicesar.rutavida.DatabaseCheck`. Resultado esperado:
   ```
   [OK] Conexión establecida: ...
   Tablas de RutaVida encontradas: 25 de 25
   [OK] El esquema está completo.
   ```
4. Ejecutar las pruebas: clic derecho sobre `src/test/java` → *Run 'All Tests'* (o `mvn test`).

5. Crear el primer administrador (una sola vez por base de datos): ejecutar `co.edu.unicesar.rutavida.app.CreateAdminUser` y responder lo que pregunta. La contraseña queda guardada como hash PBKDF2, y al final la herramienta comprueba que el administrador puede iniciar sesión.

### 3. Red vial (OpenStreetMap)

`database/05_red_vial_valledupar.sql` carga `NODO_VIAL` y `TRAMO_VIAL` con las calles del **área urbana de Valledupar** (DT-14): sur 10.405, oeste -73.305, norte 10.510, este -73.215. Lo genera la herramienta `co.edu.unicesar.rutavida.app.OsmImporter` y no se edita a mano. Las filas van en bloques PL/SQL de 200, para que cargue rápido también en Oracle Cloud.

**Para volver a generarlo:**

1. Descargar los datos con la consulta guardada (desde la carpeta `RutaVida`, en PowerShell):
   ```
   curl.exe -o database\osm\valledupar_ciudad.osm --data-urlencode "data@database\osm\valledupar_ciudad.overpassql" https://overpass-api.de/api/interpreter
   ```
2. Ejecutar `OsmImporter` (carpeta de trabajo `backend/`) con los argumentos `../database/osm/valledupar_ciudad.osm ../database/05_red_vial_valledupar.sql AAAA-MM-DD` (fecha de la descarga).

**Para cambiar la red de una base que ya tiene una cargada**, en este orden:
1. Detener `WebServer`.
2. Ejecutar `09_reiniciar_demo.sql`.
3. Ejecutar `database/osm/borrar_red_vial.sql`, que pide escribir `BORRAR RED`.
4. Ejecutar `05_red_vial_valledupar.sql`.
5. Ejecutar `98_verificar.sql`.
6. Iniciar `WebServer`. **Siempre** hay que reiniciarlo después de cambiar la red: el servidor carga las calles una sola vez, al arrancar. Si sigue con la red vieja, una emergencia fuera de esa red queda sin unidades ("No hay unidades disponibles que lleguen por la red vial cargada").

El archivo `.osm` no se sube al repositorio (`.gitignore`). Datos de la red vial: © OpenStreetMap contributors, licencia [ODbL](https://www.openstreetmap.org/copyright).

### 4. Demostración del motor de rutas

Con la red vial cargada (paso 3) y `03_datos_valledupar.sql` ejecutado, la clase `co.edu.unicesar.rutavida.app.RouteDemo` (carpeta de trabajo `backend/`) calcula la ruta recomendada y dos alternativas desde la estación de bomberos hasta una emergencia de EJEMPLO en el Parque La Popa, compara A* con Dijkstra, aplica una congestión SIMULADA, recalcula y calcula el traslado al Hospital Rosario Pumarejo. No escribe nada en Oracle.

Deja el resultado en `frontend/data/rutas-demo.geojson` y en `frontend/data/rutas-demo.js` (el mismo contenido, para abrir la página sin servidor). Los tiempos son estimados con las velocidades supuestas del importador; no son tiempos medidos.

### 5. Servidor web y API (A12a y A12b)

Con Oracle encendido, ejecutar `co.edu.unicesar.rutavida.app.WebServer` (carpeta de trabajo `backend/`). Debe mostrar `[OK] RutaVida está en http://localhost:8080`. El mismo servidor entrega las páginas de `frontend/` y la API de [`docs/api.md`](docs/api.md); se detiene con el botón *Stop* de IntelliJ. Si el puerto 8080 está ocupado, se puede pasar otro como argumento (por ejemplo `8081`).

**Sin IntelliJ (computador de la presentación):** en IntelliJ, *Maven → Lifecycle → package* (o `mvn -f backend/pom.xml package`) deja `backend/target/rutavida.jar` y sus librerías en `backend/target/lib/`. Después, doble clic en `iniciar-servidor.bat` (Windows) o `./iniciar-servidor.sh` (Linux o macOS), desde la raíz del repositorio. Necesita Java 21 y `backend/db.properties`. Para otro puerto: `iniciar-servidor.bat 8081`.

**Pruebas automáticas en GitHub:** `.github/workflows/pruebas.yml` corre `mvn package` (todas las pruebas y el JAR) en cada Pull Request y en cada push a `develop` o `main`. Allí no hay Oracle, así que las pruebas de integración se omiten solas; esas se siguen corriendo en el computador de cada uno.

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
