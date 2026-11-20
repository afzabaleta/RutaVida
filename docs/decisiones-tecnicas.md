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
| DT-09 | Oracle **local** para desarrollar y probar; **Oracle Cloud** (Always Free) como base compartida del equipo y de la sustentación, cuando el núcleo funcione | 05/11/2026 | Decidida por el equipo. Nube montada el 14/11/2026 (A15): Autonomous Database 19c Always Free, región Bogotá |
| DT-10 | Alcance final: núcleo, botón de pánico, rol Policía y **patrullas de policía simuladas** en el mapa. Fecha de congelamiento por definir | 05/11/2026 | Decidida por el equipo; falta la fecha |
| DT-11 | Umbrales y parámetros en un archivo `rutavida.properties` | 05/11/2026 | **Propuesta**; falta la opinión de Kendrick |
| DT-12 | El **botón de pánico reemplaza el reporte ciudadano por QR** (CU-22). Cualquiera lo presiona sin cuenta; 3 reportes a 300 m en 10 minutos (o 1 de un Policía) validan la alerta, que crea la emergencia y despacha sola la unidad más rápida | 14/11/2026 | Decidida por el líder |
| DT-13 | El **botón de pánico exige una cuenta de ciudadano** (CU-23). La sesión del celular dura **30 días** sin vencer por inactividad; **2 alertas descartadas en 30 días** bloquean la cuenta (RN-16). Cambia "sin cuenta" de DT-12 | 15/11/2026 | Decidida por el líder, a pedido del profesor |
| DT-14 | La red vial cubre **toda el área urbana de Valledupar** (sur 10.405, oeste -73.305, norte 10.510, este -73.215), no solo el centro. Reemplaza la zona de la demostración del 21/10/2026 | 19/11/2026 | Decidida por el líder |

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
| DT-10 | Solo el núcleo, o el núcleo con el botón de pánico, la Policía y las patrullas | Todo es obligatorio para el proyecto final. Las patrullas de policía son unidades simuladas (rotuladas `SIMULACION`), con su propio tipo de vehículo y rol. Se programan después de que el núcleo funcione de punta a punta. La fecha desde la que no se agrega nada nuevo la define el líder. **Programado el 20/11/2026:** `database/11_patrullas_ejemplo.sql` (6 patrullas EJEMPLO) y `PatrolSimulator`, que las hace recorrer la ciudad mientras corre la simulación, con su propio movimiento para no tocar las misiones |
| DT-11 | Umbrales en el código, en un archivo o en una tabla | Propuesta: un archivo `rutavida.properties` se cambia sin recompilar y se explica fácil en la sustentación. Pendiente de Kendrick |

## DT-12 · Botón de pánico en lugar del QR ciudadano

**Decisión (14/11/2026, el líder):** el botón de pánico que recomendó el profesor en la sustentación de avance **reemplaza** el reporte ciudadano por QR. CU-22 pasa a ser "Activar botón de pánico" (`docs/analysis.md`, reglas RN-13 a RN-15).

| Tema | Opciones evaluadas | Qué se decidió y por qué |
|---|---|---|
| QR o botón | Hacer los dos, solo el QR o solo el botón | Solo el botón. El documento del proyecto dejaba el QR como secundario ("se evaluará") y el botón cubre la misma idea (el ciudadano reporta y el reporte se valida antes de afectar al sistema). Hacer los dos duplicaba trabajo. Diferencia a explicar: el QR reportaba **incidentes viales** (afectaban las rutas); el botón reporta **emergencias** (se despachan). Los incidentes los siguen registrando el operador y la simulación. El valor `QR_CIUDADANO` de `INCIDENTE_VIAL.origen` queda sin uso |
| Quién lo presiona | Cualquiera sin cuenta, o solo ciudadanos registrados | Cualquiera, sin cuenta: en una emergencia nadie va a crear una cuenta. Para evitar abuso, cada dispositivo puede enviar como máximo 3 reportes cada 10 minutos, y una sola persona nunca valida una alerta (salvo un Policía) |
| Cuándo es válida | 3 reportes a 300 m en 10 min; 3 a 500 m en 15 min; o la valida un operador | 3 reportes de dispositivos distintos a menos de 300 m en 10 minutos, como dijo el profesor ("3 o más personas cercanas"). Un reporte de un usuario con `PANICO/VALIDAR` (rol `POLICIA`) la valida solo, porque su reporte tiene más credibilidad. Los valores son parámetros a calibrar (DT-11) |
| Qué pasa al validarse | Crear la emergencia `REPORTADA`, o crearla y despachar solo | Crearla y **despachar sola la unidad disponible más rápida**, con sus 3 rutas (mismo `DispatchService` de CU-09). Es lo más útil en una emergencia real y lo más claro en la demostración. Riesgo: una alerta falsa ocupa una unidad; por eso hacen falta 3 dispositivos y un coordinador puede descartarla y cerrar la emergencia como falsa alarma |
| Quién figura como responsable | Dejar el usuario vacío, o un usuario del sistema | Un **usuario del sistema** (`sistema.rutavida`, inactivo y sin contraseña válida, nadie puede iniciar sesión con él): `EMERGENCIA.id_usuario_reporta` y `ASIGNACION_EMERGENCIA.id_usuario_coordinador` son obligatorios, y así la auditoría muestra que lo hizo el sistema |
| Privacidad | Guardar el identificador del dispositivo o su hash | Solo el **hash SHA-256** del identificador que genera el navegador. No se piden nombre ni teléfono |

**Qué cambia:** `database/07_boton_panico.sql` agrega la tabla `REPORTE_PANICO` (tabla 24), el tipo de vehículo `POLICIA`, el rol `POLICIA`, los permisos `PANICO/CONSULTAR`, `PANICO/GESTIONAR` y `PANICO/VALIDAR`, y el usuario del sistema. En Java: `PanicReport`, `PanicReportStatus`, `PanicReportDAO` y `JdbcPanicReportDAO` (A20). Faltan el servicio con las reglas (S11), sus rutas de la API (A20b, `docs/api.md` 16) y la página del botón (D9).

## DT-13 · Cuenta obligatoria para el botón de pánico

**Decisión (15/11/2026, el líder, a pedido del profesor):** el botón de pánico solo funciona con una cuenta registrada, para que cada alerta tenga un responsable y quien haga bromas se pueda bloquear. Cambia la fila "Quién lo presiona" de DT-12. Casos de uso CU-22 y CU-23, reglas RN-13, RN-14 y RN-16 (`docs/analysis.md`); rutas en `docs/api.md` (sección 2 y 16).

| Tema | Opciones evaluadas | Qué se decidió y por qué |
|---|---|---|
| Cuenta | Seguir sin cuenta, o exigirla | Exigirla, como pidió el profesor. Sin cuenta, el identificador del dispositivo lo genera el navegador y se cambia borrando los datos, así que no sirve para bloquear a nadie. Con cuenta, el límite de 3 reportes y las "3 personas distintas" de RN-14 se cuentan por usuario. El costo: en una emergencia real nadie se registra; por eso la persona se registra **antes**, una sola vez, y la página siempre muestra "Llamar al 123" |
| Duración de la sesión | 8 horas como el personal, 30 días, o sin vencimiento | **30 días sin vencer por inactividad**: el profesor pidió que el módulo del pánico no tenga temporizador de sesión. Sin vencimiento era más cómodo, pero un celular perdido quedaría conectado para siempre. Las sesiones del personal no cambian (30 minutos / 8 horas, DT-08) |
| Dónde vive la sesión | En memoria, como el personal, o en Oracle | En la tabla nueva **`SESION_CIUDADANO`**: una sesión de 30 días no puede perderse cada vez que se reinicia el servidor. Se guarda solo el hash SHA-256 del token, igual que el dispositivo en DT-12: quien lea la tabla no puede usar las sesiones. El celular guarda el token en `localStorage` |
| Cuándo bloquear | 1, 2 o 3 alertas falsas; contar también las `VENCIDO` | **2 alertas `DESCARTADO` en 30 días**. Con 1, un error de buena fe bloquearía a alguien. Las `VENCIDO` no cuentan: que nadie confirmara no prueba que fuera falsa. Una alerta ya validada que después se cierra como falsa alarma tampoco cuenta todavía (limitación conocida). El bloqueo deja el usuario inactivo, cierra sus sesiones, queda en la auditoría y avisa a los coordinadores; un administrador puede reactivarlo. Policías y administradores nunca se bloquean así |
| Datos que se piden | Solo usuario y contraseña; o los datos de una persona | Nombres, apellidos, documento, correo, usuario y contraseña, los mismos de `USUARIO`, para poder responder por una alerta. Con **autorización expresa** del tratamiento de datos (Ley 1581 de 2012), que queda en la auditoría. No se pide teléfono ni dirección |
| Abuso del registro | Sin límite, captcha, o límite por dirección de red | Máximo **10 cuentas por hora por dirección de red**, en memoria. Un captcha necesitaría un servicio externo. Sin verificar el documento o el correo, alguien puede crear varias cuentas: es una limitación del prototipo |
| "Doble verificación" para lo demás | Código por correo o SMS, segundo factor, o volver a pedir la contraseña | **Volver a pedir la contraseña** en las acciones sensibles: el cambio de contraseña ya pide la actual (`POST /api/auth/password`). Un código por correo o SMS necesitaría un servicio externo que el proyecto no tiene |

**Qué cambia:** `database/08_ciudadanos.sql` agrega el permiso `PANICO/REPORTAR` (para `CIUDADANO`, `POLICIA` y `ADMINISTRADOR`), el rol `CIUDADANO`, la tabla `SESION_CIUDADANO` (tabla 25) con su secuencia, y los índices `ix_sesion_ciudadano_usuario` e `ix_reporte_panico_usuario`. En Java: `CitizenSession`, `CitizenSessionDAO`, `JdbcCitizenSessionDAO`, `CitizenService`, `CitizenController`, `CitizenRequest` y `CitizenLogin`; `PanicService` exige la sesión, limita y cuenta por persona y bloquea (RN-16); `WebSessionController` entrega y reconoce las sesiones de 30 días. En la web: `registro.html`, la cuenta en `panico.html` y el token del ciudadano en `localStorage`. `POST /api/panic` ya no es pública y aparece `POST /api/citizens`.

**Pendiente:** confirmar que `08_ciudadanos.sql` está ejecutado en la base local y en la nube, y correr `98_verificar.sql` en cada una. `SESION_CIUDADANO` y `SEQ_SESION_CIUDADANO` ya están en `99_eliminar_esquema.sql`, con autorización del líder del 19/11/2026.

## DT-14 · Red vial de toda el área urbana

**Decisión (19/11/2026, el líder):** las ambulancias y los hospitales deben estar repartidos por toda la ciudad, no solo en el centro. Como una unidad o un hospital fuera de la red vial no puede recibir rutas, la red vial pasa de la zona de la demostración (unos 5 × 4,9 km) al área urbana completa (unos 9,8 × 11,6 km).

| Tema | Opciones evaluadas | Qué se decidió y por qué |
|---|---|---|
| Zona | Mantener el centro y repartir todo ahí, o ampliar | **Ampliar**: así el mapa de la sustentación es la ciudad real, y una emergencia en un barrio del norte o del sur también recibe ruta. El costo es volver a cargar la red en las dos bases |
| Rectángulo | Límite oficial del municipio, o un rectángulo del área urbana | **Rectángulo** del Anillo Vial de Occidente al río Guatapurí y del sur del aeropuerto al norte de la Avenida Sierra Nevada, con margen. El límite del municipio incluye zona rural sin calles útiles para el despacho |
| Carga en Oracle Cloud | Una sentencia por fila, o bloques PL/SQL | **Bloques de 200 filas**: el script tiene decenas de miles de filas y, por la latencia de la nube, una sentencia por fila tardaría demasiado |
| Cambiar la red de una base existente | Borrar todo el esquema (99), o solo la red | **Solo la red**, con `database/osm/borrar_red_vial.sql`. Se ejecuta después de `09_reiniciar_demo.sql` y pide escribir `BORRAR RED`; si quedan misiones abiertas, no borra nada |

**Riesgos:** el script de la red crece unas tres veces y la primera carga en la nube puede tardar varios minutos. El grafo en memoria y A* soportan ese tamaño sin cambios.
