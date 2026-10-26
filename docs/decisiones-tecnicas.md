# Decisiones tÃ©cnicas del equipo

Registro de las decisiones que afectan a todo el proyecto. Cada decisiÃ³n dice quÃ© se decidiÃ³, por quÃ©, quÃ© cambia y quÃ© queda pendiente. Si una decisiÃ³n cambia, se agrega una fila nueva con la fecha; no se borra la anterior.

| # | DecisiÃ³n | Fecha | Estado |
|---|---|---|---|
| DT-01 | La interfaz es **web** (HTML, CSS, JavaScript y Leaflet) y consume una API REST en Java | 15/10/2026 | Decidida por el equipo |
| DT-02 | Base de datos **Oracle XE local** durante el desarrollo; la migraciÃ³n a Oracle Cloud se evalÃºa despuÃ©s | 15/10/2026 | Decidida por el equipo |
| DT-03 | Tablas y columnas de Oracle **en espaÃ±ol**; cÃ³digo Java en inglÃ©s | 15/10/2026 | Decidida por el equipo |
| DT-04 | Nombres de **ramas en inglÃ©s** (`feature/`, `fix/`, `docs/`, `test/`, `refactor/`, `chore/`) | 15/10/2026 | Decidida por el lÃ­der; cambia la regla anterior de ramas en espaÃ±ol |
| DT-05 | PL/SQL: Java decide y PL/SQL ejecuta; triggers solo para cambios de estado; tiempo de respuesta = reporte â†’ llegada | 15/10/2026 | Decidida por el lÃ­der |

---

## DT-01 Â· Interfaz web

**DecisiÃ³n:** la vista se construye como aplicaciÃ³n web (HTML, CSS, JavaScript y Leaflet para el mapa de Valledupar). No se hace interfaz en Java Swing: no se desarrollan las dos.

**Por quÃ©:**
- El mapa interactivo es el centro de la interfaz, y Leaflet lo resuelve de forma directa en el navegador.
- La misma interfaz sirve en computador y en celular (diseÃ±o adaptable), que cubre la "versiÃ³n mÃ³vil" sin una segunda aplicaciÃ³n.

**QuÃ© cambia en la arquitectura:**
- La lÃ³gica de negocio, los algoritmos de rutas y el acceso a datos **siguen en Java**, sin cambios.
- Se agrega en el backend un paquete `api` con adaptadores REST que reciben las peticiones del navegador, llaman a los **mismos controladores** y devuelven JSON. `service` y `dao` no cambian (ya estaba previsto en `docs/arquitectura-software.md`, secciÃ³n 11).
- El navegador **nunca** se conecta a Oracle: solo habla con la API.
- El cÃ³digo de la pÃ¡gina web va en una carpeta propia del repositorio (`frontend/`), separada de `backend/`.

**Pendiente (se decide al llegar a las fases 6 y 7):**

| Tema | Opciones | QuiÃ©n decide |
|---|---|---|
| Servidor HTTP de la API | El servidor que trae el JDK (`com.sun.net.httpserver`, sin dependencias) o una librerÃ­a ligera como Javalin | AndrÃ©s, Sherly y Diego |
| ConversiÃ³n a JSON | Una librerÃ­a como Jackson o Gson | AndrÃ©s y Sherly |
| ActualizaciÃ³n del mapa en tiempo real | Consultas periÃ³dicas (*polling*) o eventos del servidor (*Server-Sent Events*) | AndrÃ©s y Diego |
| Mapa base de Leaflet | Teselas de OpenStreetMap: revisar su polÃ­tica de uso antes de la demostraciÃ³n; no se asume que el servicio es ilimitado | Diego |
| Paquete `view` del backend | Se reemplaza por `api`; hay que ajustar `ArchitectureTest`, los `package-info` y el diagrama de clases en el mismo PR | AndrÃ©s |

Nada de esto estÃ¡ programado todavÃ­a: el estado actual es **diseÃ±ado**.

---

## DT-02 Â· Oracle local durante el desarrollo

**DecisiÃ³n:** cada integrante trabaja con Oracle XE en su computador (`localhost:1521/XEPDB1`, usuario `RUTAVIDA`). La migraciÃ³n a Oracle Cloud se evalÃºa mÃ¡s adelante.

**Por quÃ©:** permite desarrollar y probar sin depender de internet ni de una cuenta en la nube. El esquema completo estÃ¡ en scripts versionados (`database/`), asÃ­ que cualquier integrante lo recrea igual.

**QuÃ© implica:**
- Cada integrante tiene sus propios datos; no se comparten entre computadores.
- Pasar a la nube no cambia el cÃ³digo Java: solo la URL y las credenciales en `backend/db.properties` (mÃ¡s el *wallet* de Oracle Cloud si se usa, que tampoco se sube a GitHub).
- La propuesta original entregada al profesor menciona Oracle en la nube. Antes de la entrega final hay que decidir si se migra o explicarle al profesor por quÃ© se mantuvo en local.

---

## DT-03 Â· Nombres en espaÃ±ol en Oracle

**DecisiÃ³n:** las tablas y columnas de Oracle quedan en espaÃ±ol (`EMERGENCIA`, `TRAMO_VIAL`, `id_emergencia`), tal como estÃ¡n en `database/01_esquema.sql`. Las clases, mÃ©todos y variables de Java van en inglÃ©s (`Emergency`, `RoadSegment`, `calculateRoute()`).

**Por quÃ©:** el modelo de datos ya fue aprobado y documentado en espaÃ±ol, y el cÃ³digo en inglÃ©s sigue la convenciÃ³n del equipo.

**QuÃ© implica:** los DAO de `dao.jdbc` son los Ãºnicos que traducen entre columnas en espaÃ±ol y atributos en inglÃ©s. Esa correspondencia queda escrita en el contrato de cada entidad.

---

## DT-04 Â· Ramas en inglÃ©s

**DecisiÃ³n:** desde el 15/10/2026, los nombres de las ramas nuevas van en inglÃ©s. El prefijo es el mismo tipo del commit: `feature/`, `fix/`, `docs/`, `test/`, `refactor/` y `chore/`. Ejemplos: `feature/route-engine`, `fix/incident-validation`, `docs/update-data-model`.

**Por quÃ©:** las ramas y los commits quedan con el mismo idioma y los mismos prefijos, lo que facilita leer el historial.

**QuÃ© cambia respecto a la regla anterior:** el prompt maestro del proyecto pedÃ­a ramas en espaÃ±ol. Esta decisiÃ³n la reemplaza. Lo demÃ¡s se mantiene: commits en inglÃ©s, tÃ­tulos y descripciones de Pull Requests en espaÃ±ol, documentaciÃ³n en espaÃ±ol.

**Ramas anteriores:** `documentacion/ajustar-convenciones-git`, `documentacion/documento-consolidado-v2` y `documentacion/contratos-entidades` conservan su nombre. No se renombran porque renombrar una rama en GitHub implica borrar la anterior, y la regla del equipo es no eliminar ramas.

---

## DT-05 Â· Objetos PL/SQL (`database/04_plsql.sql`)

**DecisiÃ³n:** se crean los objetos planeados en la secciÃ³n 7.13 del documento consolidado, con tres reglas para no duplicar lÃ³gica con el backend Java.

| Objeto | QuÃ© hace |
|---|---|
| `vw_emergencias_activas`, `vw_unidades_disponibles`, `vw_hospitales_operativos` | Consultas del dashboard |
| `fn_tiempo_respuesta(id_asignacion)` | Minutos desde el **reporte** de la emergencia hasta la **llegada** de la unidad |
| `fn_unidades_disponibles(id_tipo_vehiculo)` | CuÃ¡ntas unidades estÃ¡n `DISPONIBLE` (todas o de un tipo) |
| `sp_asignar_unidad` | Crea la asignaciÃ³n, marca la unidad `OCUPADO` y pasa la emergencia a `ASIGNADA` |
| `sp_cerrar_emergencia` | Finaliza o cancela las asignaciones abiertas, libera las unidades, cierra las rutas y marca la emergencia `CERRADA` |
| `trg_emergencia_estado`, `trg_asignacion_estado`, `trg_incidente_estado` | Guardan en `AUDITORIA` cada cambio de estado (`ANTES -> DESPUES`) |
| `fn_usuario_sesion` | Auxiliar de los triggers: lee el usuario de `CLIENT_IDENTIFIER` |

**Reglas:**

1. **Java decide, PL/SQL ejecuta.** `DispatchService` valida las reglas de negocio y llama a los procedimientos desde su DAO. Los procedimientos revalidan (errores `-20001` a `-20006`), hacen todos los cambios juntos y **no hacen `COMMIT`**: la transacciÃ³n es del `TransactionManager`.
2. **Los triggers solo auditan cambios de estado.** `AuditService` sigue registrando inicios de sesiÃ³n y operaciones de negocio. El usuario de un cambio se toma de `CLIENT_IDENTIFIER`; si no hay uno vÃ¡lido, se guarda `NULL` y el trigger nunca falla.
3. **Tiempo de respuesta = reporte â†’ llegada al sitio**, que es lo que vive el ciudadano.

**Pendiente:**

- El backend debe fijar `CLIENT_IDENTIFIER` con el id del usuario en cada conexiÃ³n (por ejemplo con `connection.setClientInfo("OCSID.CLIENTID", ...)`). Se hace en la integraciÃ³n del login (A3).
- `- ~~`AbstractJdbcDAO` todavÃ­a no tiene un mÃ©todo para llamar procedimientos~~ Hecho (16/10/2026): `callProcedure` y `callProcedureReturningLong`. Un error `-20000` a `-20999` de un procedimiento llega a Java como `BusinessRuleException` con el mensaje del procedimiento; cualquier otro error, como `DataAccessException`. Las funciones se leen con `SELECT fn_x(?) FROM dual`.
- `99_eliminar_esquema.sql` no borra estas vistas, funciones y procedimientos (los triggers sÃ­ se borran con sus tablas). Quedan invÃ¡lidos hasta volver a ejecutar `01` y `04`. Cambiar `99` requiere autorizaciÃ³n del lÃ­der.
