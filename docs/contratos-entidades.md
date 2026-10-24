# Contratos de las entidades — Fase 2 (S1)

Acuerdo entre **Andrés** (base de datos y DAO) y **Sherly** (modelo) sobre las entidades que se programan en la tarea S1. Define, para cada clase, sus atributos, el tipo Java, la columna de Oracle que le corresponde y las validaciones del constructor.

- **Fuente:** `database/01_esquema.sql` (columnas y restricciones) y `docs/class-diagram.md`, secciones 1.1 a 1.3 (atributos y métodos).
- **Estado:** **acordado** el 15/10/2026. Sherly aprobó el PR #3 y eligió la propuesta en los 5 puntos de la sección 7.
- Si al programar hace falta cambiar algo, se cambia aquí y en `docs/class-diagram.md` en el mismo PR, y se avisa al grupo.

---

## 1. Reglas comunes

Estas reglas aplican a todas las entidades de S1.

| # | Regla | Motivo |
|---|---|---|
| R1 | Las entidades van en el paquete `model` y **no importan** JDBC, DAO, service, controller, view ni Swing. Sí pueden usar `model.enums` y `exception` | `ArchitectureTest` |
| R2 | Todos los datos se reciben en el **constructor**, que valida y lanza `ValidationException` si algo no cumple. No hay *setters* públicos: el estado solo cambia por métodos con significado (`changeStatus`, `moveTo`) | Una entidad nunca existe en un estado inválido |
| R3 | **Identificador:** `long id`. El valor `0` significa "todavía no está guardado". El DAO, después del `INSERT`, llama a `assignId(long)`, que solo funciona si el id actual es 0 (si no, lanza `IllegalStateException`) | Las secuencias de Oracle generan el id |
| R4 | **Otras entidades se referencian por id** (`roleId`, `emergencyTypeId`), no por el objeto completo. Excepción: `Role.permissions` y `UserSession`, que sí guardan objetos | Evita cargar media base de datos para leer una emergencia |
| R5 | **Campos opcionales** (columnas que aceptan `NULL`): `String` → `null` (nunca `""`); números → `Integer`/`Long`/`Double`; fechas → `LocalDateTime` en `null`. Campos obligatorios: primitivos (`long`, `int`) u objetos no nulos | Igual a la base de datos |
| R6 | **Textos:** el constructor hace `trim()`. Un texto obligatorio vacío o en blanco es inválido. Un texto opcional en blanco se guarda como `null` | Evita guardar espacios |
| R7 | **Longitudes:** el constructor rechaza un texto más largo que su columna `VARCHAR2` | Mejor un mensaje claro que un error de Oracle |
| R8 | **Códigos en mayúsculas:** nombres de rol, de tipo y de módulo/acción se pasan a mayúsculas en el constructor (`toUpperCase(Locale.ROOT)`) | Las restricciones `CHECK (x = UPPER(x))` |
| R9 | **Estados y catálogos:** se usan los enums que **ya existen** en `model.enums` (`RecordStatus`, `Severity`, `EmergencyStatus`, `VehicleOperationalStatus`, `HospitalLevel`). No se crean enums nuevos ni se duplican. La conversión al código de Oracle (`'A'`, `'REPORTADA'`) la hace el DAO con `code()` y `fromCode()` | `EnumSqlContractTest` |
| R10 | **Fechas:** `TIMESTAMP` ↔ `LocalDateTime`. Las fechas de creación (`registeredAt`, `reportedAt`) las pone el **servicio** con el `Clock` de `AppContext` y el DAO las inserta explícitamente (no se depende del `DEFAULT SYSTIMESTAMP`) | Pruebas repetibles |
| R11 | **Coordenadas:** siempre como `GeoPoint`, nunca como dos `double` sueltos | Información en un solo lugar |
| R12 | `equals` y `hashCode`: por `id` si la entidad ya está guardada (id ≠ 0); por identidad si no. Los *records* (`GeoPoint`, `UserSession`) comparan por valor | Comparación coherente |
| R13 | `toString()` **nunca** incluye `passwordHash` | Seguridad |
| R14 | Los mensajes de `ValidationException` van **en español** y sin detalles técnicos (por ejemplo: "La latitud debe estar entre -90 y 90") | Pueden llegar al usuario |

---

## 2. Tipos compartidos

### `GeoPoint` (*record*)

| Atributo | Tipo | Columnas Oracle | Validación |
|---|---|---|---|
| `latitude` | `double` | `latitud NUMBER(9,6)` | Entre -90 y 90 |
| `longitude` | `double` | `longitud NUMBER(9,6)` | Entre -180 y 180 |

Métodos: `distanceMetersTo(GeoPoint other)` con la fórmula de Haversine (radio de la Tierra 6 371 000 m).

`GeoPoint` acepta cualquier punto válido del planeta. Validar que una emergencia esté **dentro de Valledupar** es una regla del servicio, no de esta clase.

### `Locatable` (interfaz)


`GeoPoint getLocation()`. La implementan `EmergencyStation`,`EmergencyVehicle`, `Hospital`, `Emergency` y `RoadNode` (sección 9).


`EmergencyVehicle.getLocation()` puede devolver `null` si la unidad todavía no tiene posición.

---

## 3. Seguridad

### `Role` ↔ `ROL`

| Atributo | Tipo | Columna | Obligatorio | Validación |
|---|---|---|---|---|
| `id` | `long` | `id_rol NUMBER(10)` | Sí | R3 |
| `name` | `String` | `nombre_rol VARCHAR2(50)` | Sí | Mayúsculas, máx. 50, único (lo garantiza Oracle) |
| `description` | `String` | `descripcion VARCHAR2(200)` | No | Máx. 200 |
| `status` | `RecordStatus` | `estado CHAR(1)` | Sí | `ACTIVE` ↔ `'A'`, `INACTIVE` ↔ `'I'` |
| `permissions` | `Set<Permission>` | Tabla `ROL_PERMISO` | Sí (puede estar vacío) | Copia inmutable |

Métodos:
- `isActive()`.
- `hasPermission(module, action)`: `true` solo si el rol está activo **y** tiene un permiso con ese módulo y esa acción exactos. `GESTIONAR` **no** incluye `CONSULTAR`: en los datos iniciales cada rol tiene los dos cuando corresponde.

`ROL_PERMISO` no tiene clase propia: el DAO la usa para llenar `permissions`.

### `Permission` ↔ `PERMISO`

| Atributo | Tipo | Columna | Obligatorio | Validación |
|---|---|---|---|---|
| `id` | `long` | `id_permiso NUMBER(10)` | Sí | R3 |
| `module` | `String` | `modulo VARCHAR2(60)` | Sí | Mayúsculas, máx. 60 |
| `action` | `String` | `accion VARCHAR2(40)` | Sí | Mayúsculas, máx. 40 (`CONSULTAR` o `GESTIONAR` en los datos actuales) |
| `description` | `String` | `descripcion VARCHAR2(200)` | No | Máx. 200 |

Métodos: `matches(module, action)` compara sin importar mayúsculas o minúsculas.

### `User` ↔ `USUARIO`

| Atributo | Tipo | Columna | Obligatorio | Validación |
|---|---|---|---|---|
| `id` | `long` | `id_usuario NUMBER(10)` | Sí | R3 |
| `roleId` | `long` | `id_rol NUMBER(10)` | Sí | Mayor que 0 |
| `document` | `String` | `documento VARCHAR2(20)` | Sí | Solo letras y números, de 5 a 20 caracteres |
| `firstNames` | `String` | `nombres VARCHAR2(80)` | Sí | Máx. 80 |
| `lastNames` | `String` | `apellidos VARCHAR2(80)` | Sí | Máx. 80 |
| `email` | `String` | `email VARCHAR2(120)` | Sí | Se pasa a minúsculas; formato `texto@texto.texto`; máx. 120 |
| `username` | `String` | `username VARCHAR2(40)` | Sí | Se pasa a minúsculas; letras, números, `.`, `_`; de 4 a 40 |
| `passwordHash` | `String` | `password_hash VARCHAR2(255)` | Sí | No vacío, máx. 255. **Nunca** una contraseña en texto plano |
| `status` | `RecordStatus` | `estado CHAR(1)` | Sí | — |
| `registeredAt` | `LocalDateTime` | `fecha_registro TIMESTAMP` | Sí | R10 |

Métodos: `getFullName()` (nombres + apellidos) e `isActive()`. `toString()` sin `passwordHash` (R13).

El formato exacto del hash (algoritmo, iteraciones, sal) lo define Sherly en S2 con `Pbkdf2PasswordHasher`. La entidad solo guarda el texto resultante.

### `AuditLog` ↔ `AUDITORIA`

Agregada el 16/10/2026: no estaba en S1, pero la necesitan `AuditDAO` (A2) y `AuditService` (S2). La programa Andrés con las mismas reglas R1–R14, para que no la creen dos personas.

| Atributo | Tipo | Columna | Obligatorio | Validación |
|---|---|---|---|---|
| `id` | `long` | `id_auditoria NUMBER(12)` | Sí | R3 |
| `userId` | `Long` | `id_usuario NUMBER(10)` | No | Mayor que 0 si tiene valor. `null` = acción sin usuario autenticado (inicio de sesión fallido, simulador) |
| `module` | `String` | `modulo VARCHAR2(60)` | Sí | Mayúsculas, máx. 60 (`SEGURIDAD`, `EMERGENCIAS`, `DESPACHO`…) |
| `operation` | `String` | `operacion VARCHAR2(40)` | Sí | Mayúsculas, máx. 40 (`LOGIN`, `REGISTRAR`, `CAMBIO_ESTADO`…) |
| `recordId` | `Long` | `registro_id NUMBER(12)` | No | Mayor que 0 si tiene valor |
| `occurredAt` | `LocalDateTime` | `fecha_hora TIMESTAMP` | Sí | R10 |
| `result` | `AuditResult` | `resultado VARCHAR2(20)` | Sí | `SUCCESS` ↔ `EXITO`, `FAILURE` ↔ `FALLO` |
| `detail` | `String` | `detalle VARCHAR2(500)` | No | Máx. 500 |

Es historial: no tiene métodos que la cambien después de crearla (solo `assignId`). Los cambios de estado de emergencias, asignaciones e incidentes los escriben los triggers de `04_plsql.sql` (DT-05); `AuditService` registra el resto.

### `UserSession` (*record*, no es tabla)

| Atributo | Tipo |
|---|---|
| `user` | `User` |
| `role` | `Role` (con sus permisos) |
| `startedAt` | `LocalDateTime` |

Métodos:
- `hasPermission(module, action)` delega en `role`.
- `require(module, action)` lanza `AccessDeniedException` si no tiene el permiso.

---

## 4. Emergencias

### `EmergencyType` ↔ `TIPO_EMERGENCIA`

| Atributo | Tipo | Columna | Obligatorio | Validación |
|---|---|---|---|---|
| `id` | `long` | `id_tipo_emergencia NUMBER(10)` | Sí | R3 |
| `name` | `String` | `nombre VARCHAR2(80)` | Sí | Mayúsculas, máx. 80 |
| `priority` | `int` | `prioridad NUMBER(2)` | Sí | Entre 1 y 10 |
| `description` | `String` | `descripcion VARCHAR2(200)` | No | Máx. 200 |

### `Emergency` ↔ `EMERGENCIA`

| Atributo | Tipo | Columna | Obligatorio | Validación |
|---|---|---|---|---|
| `id` | `long` | `id_emergencia NUMBER(10)` | Sí | R3 |
| `emergencyTypeId` | `long` | `id_tipo_emergencia` | Sí | Mayor que 0 |
| `reportedByUserId` | `long` | `id_usuario_reporta` | Sí | Mayor que 0 |
| `addressReference` | `String` | `direccion_referencia VARCHAR2(200)` | Sí | Máx. 200 |
| `location` | `GeoPoint` | `latitud`, `longitud NUMBER(9,6)` | Sí | No nulo |
| `reportedAt` | `LocalDateTime` | `fecha_hora_reporte TIMESTAMP` | Sí | R10 |
| `severity` | `Severity` | `severidad VARCHAR2(20)` | Sí | `LOW`/`MEDIUM`/`HIGH`/`CRITICAL` ↔ `BAJA`/`MEDIA`/`ALTA`/`CRITICA` |
| `description` | `String` | `descripcion VARCHAR2(400)` | No | Máx. 400 |
| `status` | `EmergencyStatus` | `estado VARCHAR2(30)` | Sí | Una emergencia nueva empieza en `REPORTED` |
| `closedAt` | `LocalDateTime` | `fecha_hora_cierre TIMESTAMP` | No | Con valor **si y solo si** `status` es `CLOSED`; no puede ser anterior a `reportedAt` |

Métodos:
- `changeStatus(next, at)`:
  - Usa `EmergencyStatus.canChangeTo(next)`, que ya existe. Si la transición no está permitida, lanza `BusinessRuleException`.
  - Al pasar a `CLOSED`, asigna `closedAt = at`.
- `isActive()` delega en `status.isActive()`.
- `getLocation()`.

Estas reglas repiten en Java las restricciones `ck_emergencia_cierre` y `ck_emergencia_fechas` de Oracle, para que el error se detecte antes de llegar a la base de datos.

---

## 5. Unidades y estaciones

### `EmergencyStation` ↔ `ESTACION_EMERGENCIA`

| Atributo | Tipo | Columna | Obligatorio | Validación |
|---|---|---|---|---|
| `id` | `long` | `id_estacion NUMBER(10)` | Sí | R3 |
| `name` | `String` | `nombre VARCHAR2(100)` | Sí | Máx. 100 |
| `address` | `String` | `direccion VARCHAR2(180)` | Sí | Máx. 180 |
| `neighborhood` | `String` | `barrio VARCHAR2(100)` | No | Máx. 100 |
| `phone` | `String` | `telefono VARCHAR2(20)` | No | Dígitos, espacios, `+` y `-`; de 7 a 20 |
| `location` | `GeoPoint` | `latitud`, `longitud` | Sí | No nulo |
| `status` | `RecordStatus` | `estado CHAR(1)` | Sí | — |

### `VehicleType` ↔ `TIPO_VEHICULO`

| Atributo | Tipo | Columna | Obligatorio | Validación |
|---|---|---|---|---|
| `id` | `long` | `id_tipo_vehiculo NUMBER(10)` | Sí | R3 |
| `name` | `String` | `nombre VARCHAR2(50)` | Sí | Mayúsculas, máx. 50 (`AMBULANCIA`, `BOMBERO`) |
| `priority` | `int` | `prioridad NUMBER(2)` | Sí | Entre 1 y 10 |
| `description` | `String` | `descripcion VARCHAR2(200)` | No | Máx. 200 |

### `EmergencyVehicle` ↔ `VEHICULO_EMERGENCIA`

| Atributo | Tipo | Columna | Obligatorio | Validación |
|---|---|---|---|---|
| `id` | `long` | `id_vehiculo NUMBER(10)` | Sí | R3 |
| `vehicleTypeId` | `long` | `id_tipo_vehiculo` | Sí | Mayor que 0 |
| `stationId` | `long` | `id_estacion` | Sí | Mayor que 0 |
| `unitCode` | `String` | `codigo_unidad VARCHAR2(30)` | Sí | Mayúsculas, máx. 30 |
| `plate` | `String` | `placa VARCHAR2(10)` | No | Mayúsculas y sin espacios, máx. 10 |
| `operationalStatus` | `VehicleOperationalStatus` | `estado_operativo VARCHAR2(30)` | Sí | Una unidad nueva empieza en `AVAILABLE` |
| `lastLocation` | `GeoPoint` | `latitud`, `longitud` | No | Las dos columnas juntas o ninguna (`ck_vehiculo_posicion`) |
| `lastLocationAt` | `LocalDateTime` | `fecha_actualizacion TIMESTAMP` | No | Si hay `lastLocation`, debe tener fecha |

Métodos:
- `getLocation()` devuelve `lastLocation` (puede ser `null`).
- `isDispatchable()` delega en `operationalStatus.isDispatchable()`.
- `moveTo(point, at)`: actualiza `lastLocation` y `lastLocationAt`. Rechaza `null` y una fecha anterior a la última conocida.
- `changeStatus(next)`: cualquier cambio entre los cuatro estados es válido (el enum no define transiciones). No cambia si `next` es igual al actual.

La **fase de la misión** (despachada, en ruta, en sitio) no está aquí: está en `ASIGNACION_EMERGENCIA` y es de la Fase 5.

---

## 6. Hospitales

### `Hospital` ↔ `HOSPITAL`

| Atributo | Tipo | Columna | Obligatorio | Validación |
|---|---|---|---|---|
| `id` | `long` | `id_hospital NUMBER(10)` | Sí | R3 |
| `name` | `String` | `nombre VARCHAR2(150)` | Sí | Máx. 150 |
| `level` | `HospitalLevel` | `nivel VARCHAR2(3)` | Sí | `LEVEL_I`…`LEVEL_IV` ↔ `'I'`…`'IV'` |
| `address` | `String` | `direccion VARCHAR2(180)` | Sí | Máx. 180 |
| `phone` | `String` | `telefono VARCHAR2(20)` | No | Igual que en `EmergencyStation` |
| `location` | `GeoPoint` | `latitud`, `longitud` | Sí | No nulo |
| `emergencyCapacity` | `Integer` | `capacidad_urgencias NUMBER(5)` | No | Mayor o igual a 0 |
| `status` | `RecordStatus` | `estado CHAR(1)` | Sí | — |

Métodos: `getLocation()` e `isOperational()` (`status` activo).

`HospitalServiceOffering` (`SERVICIO_HOSPITAL`) **no** es parte de S1; se programa con el módulo de hospitales.

---

## 7. Puntos decididos en la revisión

Sherly eligió la propuesta en los cinco puntos (comentario en el PR #3, 15/10/2026).

| # | Tema | Decisión | Alternativa descartada |
|---|---|---|---|
| P1 | **`AccessDeniedException`** la necesita `UserSession.require()` (S1), pero en el plan está en la tarea A1 de Andrés **Sherly la crea en S1** (es una clase de 5 líneas en `exception`); se quitó de A1 | Andrés la sube antes, en un PR aparte |
| P2 | Identificador de una entidad sin guardar | `long id = 0` + `assignId()` (R3) | `Long id = null` |
| P3 | Formato del documento de identidad | Letras y números, de 5 a 20 | Solo dígitos (excluye pasaportes) |
| P4 | Email y `username` | Se guardan en minúsculas | Se guardan como se escriben |
| P5 | Clases de prueba | Una por entidad (`EmergencyTest`, `GeoPointTest`…), con al menos un caso válido y uno inválido por regla | — |

---

## 8. Cómo se comprueba que S1 cumple este contrato

1. Cada entidad tiene sus pruebas JUnit: datos válidos, cada validación que falla, y los métodos con reglas (`changeStatus`, `moveTo`, `hasPermission`, `distanceMetersTo`).
2. `ArchitectureTest` y `ClassDiagramSyncTest` en verde. El método `assignId(long)` (R3) todavía no aparece en `docs/class-diagram.md`: se agrega a las entidades del diagrama en el mismo PR de S1.
3. Revisión de Andrés en el PR de S1: los nombres de atributos y tipos de este documento son los que va a usar el `RowMapper` de cada DAO en la Fase 3.


---

## 9. Red vial y tráfico (S4)

Agregada el 22/10/2026 para la tarea S4. Se aplican las mismas reglas R1–R14.

Estas cinco clases las usan los DAO de la red vial (A4) y `GraphLoader` y `RoadConditionService` (Kendrick, Sprint 2). La aprueba Andrés en el PR de S4.

### `RoadNode` ↔ `NODO_VIAL` (implementa `Locatable`)

| Atributo | Tipo | Columna | Obligatorio | Validación |
|---|---|---|---|---|
| `id` | `long` | `id_nodo NUMBER(10)` | Sí | R3 |
| `osmId` | `Long` | `osm_id NUMBER(19)` | No | Mayor que 0 si tiene valor (único, lo garantiza Oracle) |
| `reference` | `String` | `referencia VARCHAR2(120)` | No | Máx. 120 |
| `location` | `GeoPoint` | `latitud`, `longitud NUMBER(9,6)` | Sí | No nulo |
| `nodeType` | `NodeType` | `tipo_nodo VARCHAR2(30)` | Sí | — |
| `status` | `RecordStatus` | `estado CHAR(1)` | Sí | — |

Métodos: `getLocation()` e `isActive()`.


### `RoadSegment` ↔ `TRAMO_VIAL`

| Atributo | Tipo | Columna | Obligatorio | Validación |
|---|---|---|---|---|
| `id` | `long` | `id_tramo NUMBER(10)` | Sí | R3 |
| `fromNodeId` | `long` | `id_nodo_origen` | Sí | Mayor que 0 |
| `toNodeId` | `long` | `id_nodo_destino` | Sí | Mayor que 0 y distinto de `fromNodeId` (`ck_tramo_nodos`) |
| `osmWayId` | `Long` | `osm_way_id NUMBER(19)` | No | Mayor que 0 si tiene valor |
| `roadName` | `String` | `nombre_via VARCHAR2(120)` | Sí | Máx. 120 |
| `lengthMeters` | `double` | `distancia_m NUMBER(10,2)` | Sí | Entre 0.01 y 99 999 999.99 |
| `maxSpeedKmh` | `double` | `velocidad_max_kmh NUMBER(5,2)` | Sí | Entre 0.01 y 120 |
| `status` | `RoadSegmentStatus` | `estado VARCHAR2(30)` | Sí | — |

`tiempo_base_seg` no es un atributo: es una columna virtual que Oracle calcula.

Métodos:
- `getBaseTimeSeconds()`: distancia / (velocidad / 3.6), la misma fórmula de la columna virtual.
- `isOpen()`: falso solo si el estado es `CERRADO`; `RESTRINGIDO` deja pasar.


### `TrafficLight` ↔ `SEMAFORO`

| Atributo | Tipo | Columna | Obligatorio | Validación |
|---|---|---|---|---|
| `id` | `long` | `id_semaforo NUMBER(10)` | Sí | R3 |
| `nodeId` | `long` | `id_nodo` | Sí | Mayor que 0 |
| `code` | `String` | `codigo VARCHAR2(30)` | Sí | Mayúsculas, máx. 30 (único, lo garantiza Oracle) |
| `currentState` | `TrafficLightState` | `estado_actual VARCHAR2(20)` | Sí | — |
| `cycleSeconds` | `Integer` | `tiempo_ciclo_seg NUMBER(4)` | No | Entre 1 y 9999 |
| `synchronizedSignal` | `boolean` | `sincronizado CHAR(1)` | Sí | `true` ↔ `'S'`, `false` ↔ `'N'` (lo convierte el DAO) |
| `status` | `RecordStatus` | `estado CHAR(1)` | Sí | — |

Métodos: `isActive()` y `changeState(next)` (el simulador cambia la luz; RutaVida nunca controla un semáforo real).


### `TrafficReading` ↔ `TRAFICO`

| Atributo | Tipo | Columna | Obligatorio | Validación |
|---|---|---|---|---|
| `id` | `long` | `id_trafico NUMBER(12)` | Sí | R3 |
| `segmentId` | `long` | `id_tramo` | Sí | Mayor que 0 |
| `measuredAt` | `LocalDateTime` | `fecha_hora TIMESTAMP` | Sí | R10 |
| `level` | `TrafficLevel` | `nivel VARCHAR2(20)` | Sí | — |
| `averageSpeedKmh` | `Double` | `velocidad_promedio_kmh NUMBER(6,2)` | No | Entre 0 y 9999.99 |
| `congestionFactor` | `double` | `factor_congestion NUMBER(5,2)` | Sí | Entre 1 y 999.99 (1 = flujo libre) |
| `source` | `TrafficSource` | `fuente VARCHAR2(40)` | Sí | — |

Es historial: no cambia después de crearla (solo `assignId`).

Método `isSimulated()`: los datos con fuente `SIMULACION` nunca se muestran como reales.


### `RoadIncident` ↔ `INCIDENTE_VIAL`

| Atributo | Tipo | Columna | Obligatorio | Validación |
|---|---|---|---|---|
| `id` | `long` | `id_incidente NUMBER(10)` | Sí | R3 |
| `segmentId` | `long` | `id_tramo` | Sí | Mayor que 0 |
| `type` | `IncidentType` | `tipo_incidente VARCHAR2(60)` | Sí | — |
| `severity` | `Severity` | `severidad VARCHAR2(20)` | Sí | — |
| `blocksSegment` | `boolean` | `bloquea_tramo CHAR(1)` | Sí | `true` ↔ `'S'` (lo convierte el DAO) |
| `description` | `String` | `descripcion VARCHAR2(300)` | No | Máx. 300 |
| `startedAt` | `LocalDateTime` | `fecha_inicio TIMESTAMP` | Sí | R10 |
| `endedAt` | `LocalDateTime` | `fecha_fin TIMESTAMP` | No | Con valor si y solo si el estado es `CERRADO` o `RECHAZADO`; no puede ser anterior a `startedAt` |
| `status` | `IncidentStatus` | `estado VARCHAR2(20)` | Sí | Un incidente nuevo empieza en `PENDING` (RN-05) |
| `origin` | `IncidentOrigin` | `origen VARCHAR2(20)` | Sí | — |

Métodos (los cambios no permitidos lanzan `BusinessRuleException`):

- `activate(blocks)`: solo desde `PENDIENTE`; pasa a `ACTIVO` y fija `blocksSegment` (CU-14).
- `reject(at)`: solo desde `PENDIENTE`; pasa a `RECHAZADO` y fija `endedAt`.
- `close(at)`: solo desde `ACTIVO`; pasa a `CERRADO` y fija `endedAt`.
- `affectsRouting()`: delega en `status.affectsRouting()` (solo `ACTIVO`).
- `blocksTraffic()`: `affectsRouting()` y `blocksSegment`; el motor trata el tramo como cerrado (sección 5 de `docs/convenciones-base-datos.md`).

El trigger `trg_incidente_estado` de `04_plsql.sql` audita los cambios de estado: la entidad no llama a `AuditService`.



---

## 10. Despacho, rutas y seguimiento (S5)

Agregada el 23/10/2026 para la tarea S5. Se aplican las mismas reglas R1–R14.

Estas cuatro clases las usan los DAO de despacho y rutas (Andrés) y
`DispatchService`, `RouteService`, `RouteMonitor` y `TrackingService`
(Kendrick, Sprint 2). La aprueba Andrés en el PR de S5.

### `EmergencyAssignment` ↔ `ASIGNACION_EMERGENCIA`

| Atributo | Tipo | Columna | Obligatorio | Validación |
|---|---|---|---|---|
| `id` | `long` | `id_asignacion NUMBER(10)` | Sí | R3 |
| `emergencyId` | `long` | `id_emergencia` | Sí | Mayor que 0 |
| `vehicleId` | `long` | `id_vehiculo` | Sí | Mayor que 0 |
| `coordinatorUserId` | `long` | `id_usuario_coordinador` | Sí | Mayor que 0 |
| `assignedAt` | `LocalDateTime` | `fecha_asignacion TIMESTAMP` | Sí | R10 |
| `departedAt` | `LocalDateTime` | `hora_salida TIMESTAMP` | No | No anterior a `assignedAt`. Con valor desde `EN_RUTA`; nulo en `ASIGNADA` |
| `arrivedAt` | `LocalDateTime` | `hora_llegada TIMESTAMP` | No | Necesita `departedAt` y no es anterior a ella. Con valor si y solo si el estado es `EN_SITIO`, `TRASLADANDO` o `FINALIZADA` |
| `status` | `AssignmentStatus` | `estado VARCHAR2(30)` | Sí | Una asignación nueva empieza en `ASSIGNED` |

Métodos:
- `changeStatus(next, at)`: usa `AssignmentStatus.canChangeTo(next)`, que ya existe; si no se permite, lanza `BusinessRuleException`. Al pasar a `EN_RUTA` fija `departedAt = at`; al pasar a `EN_SITIO` fija `arrivedAt = at`. Si la hora va hacia atrás, lanza `ValidationException` y no cambia nada.
- `isOpen()`: delega en `status.isOpen()`.
- `getTimeToArrival()`: `Duration` desde `assignedAt` hasta `arrivedAt`; `null` si todavía no llegó.

**Cambio frente al diagrama:** `getResponseTime()` pasa a llamarse `getTimeToArrival()`. Según DT-05, el tiempo de respuesta oficial va desde el **reporte** de la emergencia hasta la llegada (`fn_tiempo_respuesta`), y la asignación no conoce la hora del reporte. Con el nombre anterior, el dashboard podría mostrar dos "tiempos de respuesta" distintos.

La asignación la crea `sp_asignar_unidad` y `trg_asignacion_estado` audita sus cambios de estado: la entidad no llama a `AuditService`.


### `Route` ↔ `RUTA`

| Atributo | Tipo | Columna | Obligatorio | Validación |
|---|---|---|---|---|
| `id` | `long` | `id_ruta NUMBER(12)` | Sí | R3 |
| `emergencyId` | `long` | `id_emergencia` | Sí | Mayor que 0 |
| `vehicleId` | `long` | `id_vehiculo` | Sí | Mayor que 0 |
| `destinationHospitalId` | `Long` | `id_hospital_destino` | No | Mayor que 0. `null` = ruta hacia la emergencia; con valor = traslado |
| `type` | `RouteType` | `tipo_ruta VARCHAR2(20)` | Sí | — |
| `distanceKm` | `double` | `distancia_km NUMBER(8,2)` | Sí | Entre 0 y 999999.99 |
| `estimatedMinutes` | `double` | `tiempo_estimado_min NUMBER(8,2)` | Sí | Entre 0 y 999999.99 |
| `totalCost` | `double` | `costo_total NUMBER(12,2)` | Sí | Entre 0 y 9999999999.99 |
| `generatedAt` | `LocalDateTime` | `fecha_generacion TIMESTAMP` | Sí | R10 |
| `recommended` | `boolean` | `es_recomendada CHAR(1)` | Sí | `true` ↔ `'S'` (lo convierte el DAO) |
| `status` | `RouteStatus` | `estado VARCHAR2(20)` | Sí | — |
| `segments` | `List<RouteSegment>` | filas de `RUTA_TRAMO` | Sí | Al menos uno, con `order` 1, 2, 3… sin saltos. Se guarda una copia que no se puede modificar |

Métodos:
- `isToHospital()`: devuelve `true` cuando `destinationHospitalId != null`.
- `usesSegment(segmentId)`: permite a `RouteMonitor` comprobar si un cambio de tráfico afecta la ruta.

La ruta no cambia después de creada: `RouteDAO.updateStatus` cambia su estado en Oracle y un recálculo crea una ruta nueva.


### `RouteSegment` (*record*) ↔ `RUTA_TRAMO`

| Atributo | Tipo | Columna | Obligatorio | Validación |
|---|---|---|---|---|
| `order` | `int` | `orden_tramo NUMBER(5)` | Sí | Entre 1 y 99999 |
| `segmentId` | `long` | `id_tramo` | Sí | Mayor que 0 |
| `estimatedSeconds` | `Double` | `tiempo_estimado_seg NUMBER(10,2)` | No | Entre 0 y 99999999.99 |
| `congestionFactor` | `Double` | `factor_congestion NUMBER(5,2)` | No | Entre 1 y 999.99 |
| `trafficLightCount` | `Integer` | `semaforos_afectados NUMBER(3)` | No | Entre 0 y 999 |

**Cambios frente al diagrama:**
- Es un *record* sin `routeId`. La fila no tiene id propio (su clave es `id_ruta` + `orden_tramo`) y pertenece a su `Route`, así que el DAO escribe el id de la ruta. Si `RouteSegment` guardara `routeId`, quedaría en 0 después de `Route.assignId`.
- Los tres valores estimados son `Double`/`Integer` porque sus columnas aceptan `NULL` (R5).


### `VehicleLocation` ↔ `UBICACION_VEHICULO`

| Atributo | Tipo | Columna | Obligatorio | Validación |
|---|---|---|---|---|
| `id` | `long` | `id_ubicacion NUMBER(12)` | Sí | R3 |
| `vehicleId` | `long` | `id_vehiculo` | Sí | Mayor que 0 |
| `recordedAt` | `LocalDateTime` | `fecha_hora TIMESTAMP` | Sí | R10 |
| `location` | `GeoPoint` | `latitud`, `longitud NUMBER(9,6)` | Sí | No nulo (R11) |
| `speedKmh` | `Double` | `velocidad_kmh NUMBER(7,2)` | No | Entre 0 y 99999.99 |
| `headingDegrees` | `Double` | `direccion_grados NUMBER(6,2)` | No | Desde 0 y menor que 360 |
| `source` | `PositionSource` | `fuente VARCHAR2(20)` | Sí | — |

Es historial: no cambia después de crearla (solo `assignId`). Método `isSimulated()`: en la primera versión todas las posiciones vienen del simulador y se muestran como `SIMULACION`, nunca como GPS real.
