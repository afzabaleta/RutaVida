# Decisiones técnicas del equipo

Registro de las decisiones que afectan a todo el proyecto. Cada decisión dice qué se decidió, por qué, qué cambia y qué queda pendiente. Si una decisión cambia, se agrega una fila nueva con la fecha; no se borra la anterior.

| # | Decisión | Fecha | Estado |
|---|---|---|---|
| DT-01 | La interfaz es **web** (HTML, CSS, JavaScript y Leaflet) y consume una API REST en Java | 15/10/2026 | Decidida por el equipo |
| DT-02 | Base de datos **Oracle XE local** durante el desarrollo; la migración a Oracle Cloud se evalúa después | 15/10/2026 | Decidida por el equipo |
| DT-03 | Tablas y columnas de Oracle **en español**; código Java en inglés | 15/10/2026 | Decidida por el equipo |
| DT-04 | Nombres de **ramas en inglés** (`feature/`, `fix/`, `docs/`, `test/`, `refactor/`, `chore/`) | 15/10/2026 | Decidida por el líder; cambia la regla anterior de ramas en español |
| DT-05 | PL/SQL: Java decide y PL/SQL ejecuta; triggers solo para cambios de estado; tiempo de respuesta = reporte → llegada | 15/10/2026 | Decidida por el líder |
| DT-06 | Servidor HTTP con **Javalin + Jackson**; el mismo servidor entrega `frontend/` y la API (sin CORS) | 05/11/2026 | Decidida por el equipo |
| DT-07 | Tiempo real en la web con consultas periódicas: **cada 5 segundos** las posiciones (`GET /api/tracking`) y **cada 15 segundos** el tráfico | 05/11/2026 | Decidida por el equipo; cambia la propuesta inicial de 2 segundos |
| DT-08 | Sesión web con **token aleatorio** de 32 bytes en memoria, enviado en `Authorization: Bearer`; vence a los **30 minutos sin uso** o a las **8 horas** | 05/11/2026 | Decidida por el equipo; cambia la propuesta inicial de 8 horas fijas |
| DT-09 | Oracle **local** para desarrollar y probar; **Oracle Cloud** (Always Free) como base compartida del equipo y de la sustentación, cuando el núcleo funcione | 05/11/2026 | Decidida por el equipo |
| DT-10 | Alcance final: núcleo, botón de pánico, rol Policía y **patrullas de policía simuladas** en el mapa. Fecha de congelamiento por definir | 05/11/2026 | Decidida por el equipo; falta la fecha |
| DT-11 | Umbrales y parámetros en un archivo `rutavida.properties` | 05/11/2026 | **Propuesta**; falta la opinión de Kendrick |

---

## DT-01 · Interfaz web

**Decisión:** la vista se construye como aplicación web (HTML, CSS, JavaScript y Leaflet para el mapa de Valledupar). No se hace interfaz en Java Swing: no se desarrollan las dos.

**Por qué:**
- El mapa interactivo es el centro de la interfaz, y Leaflet lo resuelve de forma directa en el navegador.
- La misma interfaz sirve en computador y en celular (diseño adaptable), que cubre la "versión móvil" sin una segunda aplicación.

**Qué cambia en la arquitectura:**
- La lógica de negocio, los algoritmos de rutas y el acceso a datos **siguen en Java**, sin cambios.
- Se agrega en el backend un paquete `api` con adaptadores REST que reciben las peticiones del navegador, llaman a los **mismos controladores** y devuelven JSON. `service` y `dao` no cambian (ya estaba previsto en `docs/arquitectura-software.md`, sección 11).
- El navegador **nunca** se conecta a Oracle: solo habla con la API.
- El código de la página web va en una carpeta propia del repositorio (`frontend/`), separada de `backend/`.

**Pendiente (se decide al llegar a las fases 6 y 7):**

| Tema | Opciones | Quién decide |
|---|---|---|
| Servidor HTTP de la API | El servidor que trae el JDK (`com.sun.net.httpserver`, sin dependencias) o una librería ligera como Javalin | Andrés, Sherly y Diego |
| Conversión a JSON | Una librería como Jackson o Gson | Andrés y Sherly |
| Actualización del mapa en tiempo real | Consultas periódicas (*polling*) o eventos del servidor (*Server-Sent Events*) | Andrés y Diego |
| Mapa base de Leaflet | Teselas de OpenStreetMap: revisar su política de uso antes de la demostración; no se asume que el servicio es ilimitado | Diego |
| Paquete `view` del backend | Se reemplaza por `api`; hay que ajustar `ArchitectureTest`, los `package-info` y el diagrama de clases en el mismo PR | Andrés |

Nada de esto está programado todavía: el estado actual es **diseñado**.

---

## DT-02 · Oracle local durante el desarrollo

**Decisión:** cada integrante trabaja con Oracle XE en su computador (`localhost:1521/XEPDB1`, usuario `RUTAVIDA`). La migración a Oracle Cloud se evalúa más adelante.

**Por qué:** permite desarrollar y probar sin depender de internet ni de una cuenta en la nube. El esquema completo está en scripts versionados (`database/`), así que cualquier integrante lo recrea igual.

**Qué implica:**
- Cada integrante tiene sus propios datos; no se comparten entre computadores.
- Pasar a la nube no cambia el código Java: solo la URL y las credenciales en `backend/db.properties` (más el *wallet* de Oracle Cloud si se usa, que tampoco se sube a GitHub).
- La propuesta original entregada al profesor menciona Oracle en la nube. Antes de la entrega final hay que decidir si se migra o explicarle al profesor por qué se mantuvo en local.

---

## DT-03 · Nombres en español en Oracle

**Decisión:** las tablas y columnas de Oracle quedan en español (`EMERGENCIA`, `TRAMO_VIAL`, `id_emergencia`), tal como están en `database/01_esquema.sql`. Las clases, métodos y variables de Java van en inglés (`Emergency`, `RoadSegment`, `calculateRoute()`).

**Por qué:** el modelo de datos ya fue aprobado y documentado en español, y el código en inglés sigue la convención del equipo.

**Qué implica:** los DAO de `dao.jdbc` son los únicos que traducen entre columnas en español y atributos en inglés. Esa correspondencia queda escrita en el contrato de cada entidad.

---

## DT-04 · Ramas en inglés

**Decisión:** desde el 15/10/2026, los nombres de las ramas nuevas van en inglés. El prefijo es el mismo tipo del commit: `feature/`, `fix/`, `docs/`, `test/`, `refactor/` y `chore/`. Ejemplos: `feature/route-engine`, `fix/incident-validation`, `docs/update-data-model`.

**Por qué:** las ramas y los commits quedan con el mismo idioma y los mismos prefijos, lo que facilita leer el historial.

**Qué cambia respecto a la regla anterior:** el prompt maestro del proyecto pedía ramas en español. Esta decisión la reemplaza. Lo demás se mantiene: commits en inglés, títulos y descripciones de Pull Requests en español, documentación en español.

**Ramas anteriores:** `documentacion/ajustar-convenciones-git`, `documentacion/documento-consolidado-v2` y `documentacion/contratos-entidades` conservan su nombre. No se renombran porque renombrar una rama en GitHub implica borrar la anterior, y la regla del equipo es no eliminar ramas.

---

## DT-05 · Objetos PL/SQL (`database/04_plsql.sql`)

**Decisión:** se crean los objetos planeados en la sección 7.13 del documento consolidado, con tres reglas para no duplicar lógica con el backend Java.

| Objeto | Qué hace |
|---|---|
| `vw_emergencias_activas`, `vw_unidades_disponibles`, `vw_hospitales_operativos` | Consultas del dashboard |
| `fn_tiempo_respuesta(id_asignacion)` | Minutos desde el **reporte** de la emergencia hasta la **llegada** de la unidad |
| `fn_unidades_disponibles(id_tipo_vehiculo)` | Cuántas unidades están `DISPONIBLE` (todas o de un tipo) |
| `sp_asignar_unidad` | Crea la asignación, marca la unidad `OCUPADO` y pasa la emergencia a `ASIGNADA` |
| `sp_cerrar_emergencia` | Finaliza o cancela las asignaciones abiertas, libera las unidades, cierra las rutas y marca la emergencia `CERRADA` |
| `trg_emergencia_estado`, `trg_asignacion_estado`, `trg_incidente_estado` | Guardan en `AUDITORIA` cada cambio de estado (`ANTES -> DESPUES`) |
| `fn_usuario_sesion` | Auxiliar de los triggers: lee el usuario de `CLIENT_IDENTIFIER` |

**Reglas:**

1. **Java decide, PL/SQL ejecuta.** `DispatchService` valida las reglas de negocio y llama a los procedimientos desde su DAO. Los procedimientos revalidan (errores `-20001` a `-20006`), hacen todos los cambios juntos y **no hacen `COMMIT`**: la transacción es del `TransactionManager`.
2. **Los triggers solo auditan cambios de estado.** `AuditService` sigue registrando inicios de sesión y operaciones de negocio. El usuario de un cambio se toma de `CLIENT_IDENTIFIER`; si no hay uno válido, se guarda `NULL` y el trigger nunca falla.
3. **Tiempo de respuesta = reporte → llegada al sitio**, que es lo que vive el ciudadano.

**Pendiente:**

- El backend debe fijar `CLIENT_IDENTIFIER` con el id del usuario en cada conexión (por ejemplo con `connection.setClientInfo("OCSID.CLIENTID", ...)`). Se hace en la integración del login (A3).
- `- ~~`AbstractJdbcDAO` todavía no tiene un método para llamar procedimientos~~ Hecho (16/10/2026): `callProcedure` y `callProcedureReturningLong`. Un error `-20000` a `-20999` de un procedimiento llega a Java como `BusinessRuleException` con el mensaje del procedimiento; cualquier otro error, como `DataAccessException`. Las funciones se leen con `SELECT fn_x(?) FROM dual`.
- `99_eliminar_esquema.sql` no borra estas vistas, funciones y procedimientos (los triggers sí se borran con sus tablas). Quedan inválidos hasta volver a ejecutar `01` y `04`. Cambiar `99` requiere autorización del líder.

---

## DT-06 a DT-11 · API, sesión, nube y alcance final

**Reunión del equipo del 05/11/2026.** Se aprobaron DT-06 a DT-10. DT-11 sigue como propuesta hasta que Kendrick, que la usa en la simulación, dé su opinión. El contrato de la API (`docs/api.md`) aplica DT-06, DT-07 y DT-08.

| # | Opciones evaluadas | Qué se decidió y por qué |
|---|---|---|
| DT-06 | Servidor del JDK (`com.sun.net.httpserver`) + Gson, o Javalin + Jackson | Javalin. Define una ruta en una línea y sirve también `frontend/`, así la web y la API quedan en el mismo origen y no hace falta CORS. El servidor del JDK no agrega dependencias, pero obliga a escribir a mano el ruteo y los encabezados. La versión se verifica en Maven Central antes de agregarla |
| DT-07 | Consultas periódicas (cada 2, 5 o 10 segundos) o *Server-Sent Events* | Consultas: son lo más simple de programar, explicar y depurar. 5 segundos para las posiciones: con 10, una unidad simulada a 50 km/h salta unos 140 m entre consultas y el recálculo se ve tarde en la demostración; la web anima el marcador entre posiciones. 15 segundos para el tráfico, que cambia menos |
| DT-08 | Token aleatorio en memoria, cookie de sesión o JWT; vencimiento fijo o por inactividad | Token aleatorio: con un solo servidor, JWT agrega complejidad sin aportar nada. El equipo consideró que 8 horas fijas era demasiado: la sesión vence a los 30 minutos sin uso (las consultas automáticas del mapa no cuentan como uso) y como máximo a las 8 horas, un turno. Si el servidor se reinicia, los usuarios vuelven a iniciar sesión |
| DT-09 | Solo local, solo nube, o local para desarrollar y nube compartida | Las dos: el código no cambia, solo `db.properties` (y el *wallet* de la nube, que nunca se sube al repositorio). Local para desarrollar y correr las pruebas; nube como la base del equipo y de la sustentación, como pidió el profesor. Límites del plan Always Free a tener en cuenta: 2 bases de 20 GB, 30 sesiones simultáneas, se detiene sola después de 7 días sin uso y puede borrarse si queda detenida 90 días, y no permite copias manuales (los scripts `01` a `06` son la copia) |
| DT-10 | Solo el núcleo, o el núcleo con el botón de pánico, la Policía y las patrullas | Todo es obligatorio para el proyecto final. Las patrullas de policía son unidades simuladas (rotuladas `SIMULACION`), con su propio tipo de vehículo y rol. Se programan después de que el núcleo funcione de punta a punta. La fecha desde la que no se agrega nada nuevo la define el líder |
| DT-11 | Umbrales en el código, en un archivo o en una tabla | Propuesta: un archivo `rutavida.properties` se cambia sin recompilar y se explica fácil en la sustentación. Pendiente de Kendrick |
