# Decisiones técnicas del equipo

Registro de las decisiones que afectan a todo el proyecto. Cada decisión dice qué se decidió, por qué, qué cambia y qué queda pendiente. Si una decisión cambia, se agrega una fila nueva con la fecha; no se borra la anterior.

| # | Decisión | Fecha | Estado |
|---|---|---|---|
| DT-01 | La interfaz es **web** (HTML, CSS, JavaScript y Leaflet) y consume una API REST en Java | 15/10/2026 | Decidida por el equipo |
| DT-02 | Base de datos **Oracle XE local** durante el desarrollo; la migración a Oracle Cloud se evalúa después | 15/10/2026 | Decidida por el equipo |
| DT-03 | Tablas y columnas de Oracle **en español**; código Java en inglés | 15/10/2026 | Decidida por el equipo |
| DT-04 | Nombres de **ramas en inglés** (`feature/`, `fix/`, `docs/`, `test/`, `refactor/`, `chore/`) | 15/10/2026 | Decidida por el líder; cambia la regla anterior de ramas en español |
| DT-05 | PL/SQL: Java decide y PL/SQL ejecuta; triggers solo para cambios de estado; tiempo de respuesta = reporte → llegada | 15/10/2026 | Decidida por el líder |

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
- `AbstractJdbcDAO` todavía no tiene un método para llamar procedimientos (`CallableStatement`). Se agrega cuando se programe el DAO de despacho (Sprint 2).
- `99_eliminar_esquema.sql` no borra estas vistas, funciones y procedimientos (los triggers sí se borran con sus tablas). Quedan inválidos hasta volver a ejecutar `01` y `04`. Cambiar `99` requiere autorización del líder.

