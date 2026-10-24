# Convenciones de la base de datos — RutaVida

Responsable: Andrés (base de datos, DAO e integración).
Fecha: 14 de octubre de 2026.
Estado: **propuesta aplicada en `database/01_esquema.sql`**, pendiente de revisión del equipo.

Este documento corrige las inconsistencias detectadas entre el diccionario de datos, el Anexo A y el documento consolidado. Si un valor de este documento cambia, se debe cambiar también en el script SQL y en los `enum` de Java.

---

## 1. Dónde vive la base de datos

| Concepto | Valor |
|---|---|
| Motor | Oracle Database XE (instalación local de cada integrante) |
| Servicio (PDB) | `XEPDB1` |
| URL JDBC | `jdbc:oracle:thin:@//localhost:1521/XEPDB1` |
| Usuario / esquema | `RUTAVIDA` |
| Contraseña | La define cada integrante; va en `backend/db.properties`, que **no** se sube a GitHub |

**Decisión del equipo (14/10/2026, confirmada el 15/10/2026 como DT-02 en `docs/decisiones-tecnicas.md`):** se trabaja con Oracle local durante el desarrollo; la migración a Oracle Cloud se evalúa después.

**Consecuencias que hay que tener presentes:**

- Cada integrante tiene **su propia** base de datos. Los datos no se comparten: lo que uno inserta no lo ve otro. Por eso el esquema y los datos iniciales viven en scripts versionados en `database/`, y cualquier cambio de estructura se hace en un script nuevo, nunca a mano.
- La propuesta entregada al profesor dice "Oracle alojado en la nube". Antes de la entrega final hay que decidir si se migra o explicarle por qué se mantuvo en local.
- Si más adelante se vuelve a la nube, el código Java no cambia: solo cambian la URL y las credenciales en `db.properties`.

---

## 2. Reglas generales

| Regla | Ejemplo |
|---|---|
| Tablas y columnas en español (diccionario aprobado). El DAO las traduce al Model en inglés. | `TRAMO_VIAL` → `RoadSegment` |
| Valores de estado y catálogo: MAYÚSCULAS, sin tildes y con `_` | `EN_RUTA`, `CRITICA`, `FUERA_SERVICIO` |
| Banderas sí/no: `CHAR(1)` con `'S'` / `'N'` | `es_recomendada`, `leida`, `disponible` |
| Estado de registros maestros: `CHAR(1)` con `'A'` (activo) / `'I'` (inactivo) | `ROL.estado`, `HOSPITAL.estado` |
| Identificadores: secuencia `SEQ_<TABLA>` usada como `DEFAULT` de la columna | `id_rol DEFAULT seq_rol.NEXTVAL` |
| Fechas: `TIMESTAMP` con `DEFAULT SYSTIMESTAMP` cuando es la fecha de creación | `fecha_hora_reporte` |
| Nombres de restricciones | `pk_`, `fk_`, `uq_`, `ck_`; índices `ix_` y `ux_` (únicos) |
| Coordenadas: `NUMBER(9,6)` con `CHECK` de rango | latitud entre -90 y 90 |

---

## 3. Catálogo único de estados y valores

### Severidad (un solo catálogo para EMERGENCIA e INCIDENTE_VIAL)
`BAJA` · `MEDIA` · `ALTA` · `CRITICA`

> Antes: EMERGENCIA tenía 4 niveles (con "crítica", con tilde) e INCIDENTE_VIAL solo 3.

### EMERGENCIA.estado
| Valor | Significado |
|---|---|
| `REPORTADA` | Registrada, sin unidad |
| `ASIGNADA` | Unidad seleccionada |
| `EN_RUTA` | Unidad desplazándose al sitio |
| `EN_SITIO` | Unidad en el lugar |
| `TRASLADANDO` | Unidad llevando al paciente al hospital |
| `CERRADA` | Atención finalizada (exige `fecha_hora_cierre`) |

> Antes: el diccionario decía "en atención", que no existe en el Anexo A.

### ASIGNACION_EMERGENCIA.estado
`ASIGNADA` · `EN_RUTA` · `EN_SITIO` · `TRASLADANDO` · `FINALIZADA` · `CANCELADA`

- Se agregan `TRASLADANDO` (para seguir la misma secuencia de la emergencia) y `CANCELADA` (cuando una unidad se reemplaza por otra, por ejemplo por una avería).
- Una unidad solo puede tener **una** asignación abierta (índice `ux_asignacion_vehiculo_abierta`).

### VEHICULO_EMERGENCIA.estado_operativo
`DISPONIBLE` · `OCUPADO` · `MANTENIMIENTO` · `FUERA_SERVICIO`

> Antes: "Disponible, en ruta, mantenimiento, fuera de servicio". Se cambió "en ruta" por `OCUPADO` porque la unidad también está ocupada cuando está en el sitio o trasladando. **La fase de la misión se consulta en `ASIGNACION_EMERGENCIA.estado`**; así no se repite el mismo dato en dos tablas.

### TRAMO_VIAL.estado
`ABIERTO` · `CERRADO` · `RESTRINGIDO`

### INCIDENTE_VIAL
| Columna | Valores |
|---|---|
| `tipo_incidente` | `ACCIDENTE` · `OBRA` · `CIERRE` · `MANIFESTACION` · `OTRO` |
| `estado` | `PENDIENTE` · `ACTIVO` · `CERRADO` · `RECHAZADO` |
| `origen` | `OPERADOR` · `QR_CIUDADANO` · `SIMULACION` |
| `bloquea_tramo` | `S` (el motor lo trata como cerrado) · `N` (solo penaliza) |

- **El estado por defecto es `PENDIENTE`.** Un incidente solo afecta las rutas cuando alguien lo pasa explícitamente a `ACTIVO`. Así un reporte ciudadano falso nunca cierra una calle por error.
- `RECHAZADO` sirve para descartar un reporte que no se validó.

### TRAFICO
| Columna | Valores |
|---|---|
| `nivel` | `FLUIDO` · `MODERADO` · `CONGESTIONADO` |
| `fuente` | `MANUAL` · `SIMULACION` · `SENSOR` · `API_EXTERNA` |
| `factor_congestion` | ≥ 1.00 (1.00 = flujo libre; 2.00 = el doble de lento) |

### UBICACION_VEHICULO.fuente (columna nueva)
`SIMULACION` · `GPS`

> Se agrega para cumplir el requisito de **no presentar datos simulados como reales**.

### RUTA
| Columna | Valores |
|---|---|
| `tipo_ruta` | `PRINCIPAL` · `ALTERNATIVA` |
| `estado` | `PROPUESTA` · `ACTIVA` · `FINALIZADA` · `DESCARTADA` |
| `es_recomendada` | `S` · `N` |

- `id_hospital_destino` vacío = trayecto hacia la emergencia; con valor = traslado al hospital.
- Una emergencia y una unidad tienen como máximo una ruta `ACTIVA` (índice `ux_ruta_activa`).

### Otros catálogos
| Tabla.columna | Valores |
|---|---|
| `SEMAFORO.estado_actual` | `VERDE` · `AMARILLO` · `ROJO` · `FUERA_SERVICIO` |
| `NODO_VIAL.tipo_nodo` | `INTERSECCION` · `GLORIETA` · `PUENTE` · `ACCESO` · `PUNTO_VIA` · `OTRO` |
| `HOSPITAL.nivel` | `I` · `II` · `III` · `IV` |
| `ALERTA.tipo_alerta` | `CONGESTION` · `ACCIDENTE_EN_RUTA` · `CIERRE_VIA` · `RECALCULO_RUTA` · `CAMBIO_DISPONIBILIDAD` · `OTRO` |
| `AUDITORIA.resultado` | `EXITO` · `FALLO` |
| Prioridad (`TIPO_EMERGENCIA`, `TIPO_VEHICULO`) | 1 = más baja … 10 = más alta |

---

## 4. Cambios de estructura respecto al diccionario original

| # | Tabla | Cambio | Motivo |
|---|---|---|---|
| 1 | TRAMO_VIAL | Se elimina `sentido`. **Cada fila es un solo sentido de circulación**; una vía de doble sentido son dos filas | El grafo usa aristas dirigidas. Con una sola fila para los dos sentidos, `RUTA_TRAMO` no podría saber en qué dirección se recorrió el tramo |
| 2 | TRAMO_VIAL | `tiempo_base_seg` pasa a ser columna **virtual** (`distancia_m / (velocidad_max_kmh / 3.6)`) y `velocidad_max_kmh` pasa a ser obligatoria | El tiempo base nunca queda desactualizado respecto a la distancia o la velocidad |
| 3 | NODO_VIAL, TRAMO_VIAL | Columnas opcionales `osm_id` y `osm_way_id` | Saber de qué elemento de OpenStreetMap salió cada dato y poder recargar la red sin duplicados |
| 4 | RUTA_TRAMO | Clave primaria `(id_ruta, orden_tramo)` en lugar de `(id_ruta, id_tramo)` | Lo que identifica cada fila es su posición en la secuencia |
| 5 | EMERGENCIA | Columna nueva `fecha_hora_cierre` (obligatoria cuando el estado es `CERRADA`) | Calcular el tiempo total de atención en el dashboard |
| 6 | INCIDENTE_VIAL | Columnas nuevas `origen` y `bloquea_tramo`; estados `PENDIENTE` y `RECHAZADO` | Módulo QR y regla explícita de cierre o penalización |
| 7 | UBICACION_VEHICULO | Columna nueva `fuente` | Distinguir los datos simulados de los reales |
| 8 | ALERTA | `id_emergencia` pasa a ser opcional | Existen alertas generales (cambio de disponibilidad, congestión) |
| 9 | AUDITORIA | `id_usuario` pasa a ser opcional | Registrar inicios de sesión fallidos y acciones del simulador |
| 10 | PERMISO, SERVICIO_HOSPITAL | Restricciones únicas `(modulo, accion)` y `(id_hospital, nombre_servicio)` | Evitar registros duplicados |

Las 23 tablas se conservan.

---

## 5. Tráfico e incidentes en el motor de rutas (acuerdo con Service)

La base de datos guarda el **historial**; el estado vigente de cada tramo vive en memoria, en el grafo. Para que el simulador de tráfico no "reabra" un tramo cerrado por un incidente, el estado que usa el motor se calcula **combinando dos fuentes**:

```
cerrado      = TRAMO_VIAL.estado = 'CERRADO'
               OR existe un INCIDENTE_VIAL ACTIVO con bloquea_tramo = 'S'
congestión   = último factor del simulador o de la fuente real de tráfico
penalización = según la severidad de los incidentes ACTIVO que no bloquean el tramo
```

El simulador solo modifica la congestión y los semáforos; **no cierra tramos**. Los cierres vienen de incidentes (creados por el operador o por un escenario de demostración). Este acuerdo debe reflejarse en el diseño de `EdgeState` y `TrafficUpdater` (Kendrick).

**Persistencia por lotes:** `TRAFICO` y `UBICACION_VEHICULO` no se escriben en cada tick del simulador. Se guardan cada N ticks o solo cuando el cambio es relevante (valor de N por definir).

---

## 6. Pendientes

| Decisión | Responsable |
|---|---|
| Decidir antes de la entrega final si se migra a Oracle Cloud (DT-02) | Equipo |
| Justificar las prioridades de `TIPO_EMERGENCIA` y `TIPO_VEHICULO` (los valores actuales son provisionales) | Equipo |
| Algoritmo de hash de contraseñas (necesario para crear el primer usuario) | Sherly (Util) |
| Cargar hospitales, estaciones y red vial con datos reales de Valledupar | Andrés |
| ~~Triggers de auditoría, vistas y procedimientos~~ Hecho en `database/04_plsql.sql` (DT-05). El backend fija `CLIENT_IDENTIFIER` con el id del usuario desde A3 (`ClientIdentifierConnectionFactory`) | Andrés |