-- =====================================================================
-- RutaVida · 01_esquema.sql
-- Esquema relacional (23 tablas) para Oracle Database XE local.
--   Conexión: usuario RUTAVIDA en localhost:1521/XEPDB1
--
-- Convenciones (ver docs/convenciones-base-datos.md):
--   * Tablas y columnas en español, como en el diccionario de datos.
--   * Valores de estado y catálogo en MAYÚSCULAS, sin tildes, con "_".
--   * Banderas CHAR(1): 'S'/'N'. Estado de registros maestros: 'A'/'I'.
--   * Nombres de restricciones: pk_, fk_, uq_, ck_; índices: ix_, ux_.
--   * Un identificador por secuencia SEQ_<TABLA> usada como DEFAULT.
--
-- Orden de ejecución: 00_crear_usuario.sql → 01_esquema.sql →
--                     02_datos_iniciales.sql → 98_verificar.sql
-- Para empezar de cero: 99_eliminar_esquema.sql
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. SECUENCIAS (21: todas las tablas menos ROL_PERMISO y RUTA_TRAMO,
--    que usan clave primaria compuesta)
-- ---------------------------------------------------------------------
CREATE SEQUENCE seq_rol                   START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_permiso               START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_usuario               START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_estacion_emergencia   START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_tipo_vehiculo         START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_vehiculo_emergencia   START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_hospital              START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_servicio_hospital     START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_tipo_emergencia       START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_emergencia            START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_nodo_vial             START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_tramo_vial            START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_semaforo              START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_trafico               START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_incidente_vial        START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_ruta                  START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_asignacion_emergencia START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_ubicacion_vehiculo    START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_alerta                START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_notificacion          START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_auditoria             START WITH 1 INCREMENT BY 1;


-- ---------------------------------------------------------------------
-- 2. SEGURIDAD
-- ---------------------------------------------------------------------

-- 1. ROL
CREATE TABLE rol (
    id_rol        NUMBER(10)    DEFAULT seq_rol.NEXTVAL NOT NULL,
    nombre_rol    VARCHAR2(50)  NOT NULL,
    descripcion   VARCHAR2(200),
    estado        CHAR(1)       DEFAULT 'A' NOT NULL,
    CONSTRAINT pk_rol            PRIMARY KEY (id_rol),
    CONSTRAINT uq_rol_nombre     UNIQUE (nombre_rol),
    CONSTRAINT ck_rol_nombre     CHECK (nombre_rol = UPPER(nombre_rol)),
    CONSTRAINT ck_rol_estado     CHECK (estado IN ('A', 'I'))
);

-- 2. PERMISO
CREATE TABLE permiso (
    id_permiso    NUMBER(10)    DEFAULT seq_permiso.NEXTVAL NOT NULL,
    modulo        VARCHAR2(60)  NOT NULL,
    accion        VARCHAR2(40)  NOT NULL,
    descripcion   VARCHAR2(200),
    CONSTRAINT pk_permiso               PRIMARY KEY (id_permiso),
    CONSTRAINT uq_permiso_modulo_accion UNIQUE (modulo, accion),
    CONSTRAINT ck_permiso_mayusculas    CHECK (modulo = UPPER(modulo) AND accion = UPPER(accion))
);

-- 3. ROL_PERMISO
CREATE TABLE rol_permiso (
    id_rol        NUMBER(10)    NOT NULL,
    id_permiso    NUMBER(10)    NOT NULL,
    CONSTRAINT pk_rol_permiso         PRIMARY KEY (id_rol, id_permiso),
    CONSTRAINT fk_rol_permiso_rol     FOREIGN KEY (id_rol)     REFERENCES rol (id_rol) ON DELETE CASCADE,
    CONSTRAINT fk_rol_permiso_permiso FOREIGN KEY (id_permiso) REFERENCES permiso (id_permiso) ON DELETE CASCADE
);

-- 4. USUARIO
CREATE TABLE usuario (
    id_usuario     NUMBER(10)    DEFAULT seq_usuario.NEXTVAL NOT NULL,
    id_rol         NUMBER(10)    NOT NULL,
    documento      VARCHAR2(20)  NOT NULL,
    nombres        VARCHAR2(80)  NOT NULL,
    apellidos      VARCHAR2(80)  NOT NULL,
    email          VARCHAR2(120) NOT NULL,
    username       VARCHAR2(40)  NOT NULL,
    password_hash  VARCHAR2(255) NOT NULL,
    estado         CHAR(1)       DEFAULT 'A' NOT NULL,
    fecha_registro TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_usuario           PRIMARY KEY (id_usuario),
    CONSTRAINT fk_usuario_rol       FOREIGN KEY (id_rol) REFERENCES rol (id_rol),
    CONSTRAINT uq_usuario_documento UNIQUE (documento),
    CONSTRAINT uq_usuario_email     UNIQUE (email),
    CONSTRAINT uq_usuario_username  UNIQUE (username),
    CONSTRAINT ck_usuario_email     CHECK (email LIKE '%_@_%._%'),
    CONSTRAINT ck_usuario_estado    CHECK (estado IN ('A', 'I'))
);


-- ---------------------------------------------------------------------
-- 3. OPERACIÓN: ESTACIONES Y UNIDADES
-- ---------------------------------------------------------------------

-- 5. ESTACION_EMERGENCIA
CREATE TABLE estacion_emergencia (
    id_estacion   NUMBER(10)    DEFAULT seq_estacion_emergencia.NEXTVAL NOT NULL,
    nombre        VARCHAR2(100) NOT NULL,
    direccion     VARCHAR2(180) NOT NULL,
    barrio        VARCHAR2(100),
    telefono      VARCHAR2(20),
    latitud       NUMBER(9,6)   NOT NULL,
    longitud      NUMBER(9,6)   NOT NULL,
    estado        CHAR(1)       DEFAULT 'A' NOT NULL,
    CONSTRAINT pk_estacion_emergencia PRIMARY KEY (id_estacion),
    CONSTRAINT ck_estacion_latitud    CHECK (latitud  BETWEEN -90  AND 90),
    CONSTRAINT ck_estacion_longitud   CHECK (longitud BETWEEN -180 AND 180),
    CONSTRAINT ck_estacion_estado     CHECK (estado IN ('A', 'I'))
);

-- 6. TIPO_VEHICULO
CREATE TABLE tipo_vehiculo (
    id_tipo_vehiculo NUMBER(10)    DEFAULT seq_tipo_vehiculo.NEXTVAL NOT NULL,
    nombre           VARCHAR2(50)  NOT NULL,
    prioridad        NUMBER(2)     NOT NULL,
    descripcion      VARCHAR2(200),
    CONSTRAINT pk_tipo_vehiculo        PRIMARY KEY (id_tipo_vehiculo),
    CONSTRAINT uq_tipo_vehiculo_nombre UNIQUE (nombre),
    CONSTRAINT ck_tipo_vehiculo_nombre CHECK (nombre = UPPER(nombre)),
    CONSTRAINT ck_tipo_vehiculo_prior  CHECK (prioridad BETWEEN 1 AND 10)
);

-- 7. VEHICULO_EMERGENCIA
--    estado_operativo describe solo la disponibilidad de la unidad.
--    La fase de la misión (EN_RUTA, EN_SITIO...) vive en ASIGNACION_EMERGENCIA.
CREATE TABLE vehiculo_emergencia (
    id_vehiculo         NUMBER(10)   DEFAULT seq_vehiculo_emergencia.NEXTVAL NOT NULL,
    id_tipo_vehiculo    NUMBER(10)   NOT NULL,
    id_estacion         NUMBER(10)   NOT NULL,
    codigo_unidad       VARCHAR2(30) NOT NULL,
    placa               VARCHAR2(10),
    estado_operativo    VARCHAR2(30) DEFAULT 'DISPONIBLE' NOT NULL,
    latitud             NUMBER(9,6),
    longitud            NUMBER(9,6),
    fecha_actualizacion TIMESTAMP,
    CONSTRAINT pk_vehiculo_emergencia PRIMARY KEY (id_vehiculo),
    CONSTRAINT fk_vehiculo_tipo       FOREIGN KEY (id_tipo_vehiculo) REFERENCES tipo_vehiculo (id_tipo_vehiculo),
    CONSTRAINT fk_vehiculo_estacion   FOREIGN KEY (id_estacion)      REFERENCES estacion_emergencia (id_estacion),
    CONSTRAINT uq_vehiculo_codigo     UNIQUE (codigo_unidad),
    CONSTRAINT uq_vehiculo_placa      UNIQUE (placa),
    CONSTRAINT ck_vehiculo_estado     CHECK (estado_operativo IN ('DISPONIBLE', 'OCUPADO', 'MANTENIMIENTO', 'FUERA_SERVICIO')),
    CONSTRAINT ck_vehiculo_latitud    CHECK (latitud  BETWEEN -90  AND 90),
    CONSTRAINT ck_vehiculo_longitud   CHECK (longitud BETWEEN -180 AND 180),
    CONSTRAINT ck_vehiculo_posicion   CHECK ((latitud IS NULL AND longitud IS NULL)
                                          OR (latitud IS NOT NULL AND longitud IS NOT NULL))
);


-- ---------------------------------------------------------------------
-- 4. SALUD
-- ---------------------------------------------------------------------

-- 8. HOSPITAL  (estado: A = operativo, I = no operativo)
CREATE TABLE hospital (
    id_hospital         NUMBER(10)    DEFAULT seq_hospital.NEXTVAL NOT NULL,
    nombre              VARCHAR2(150) NOT NULL,
    nivel               VARCHAR2(3)   NOT NULL,
    direccion           VARCHAR2(180) NOT NULL,
    telefono            VARCHAR2(20),
    latitud             NUMBER(9,6)   NOT NULL,
    longitud            NUMBER(9,6)   NOT NULL,
    capacidad_urgencias NUMBER(5),
    estado              CHAR(1)       DEFAULT 'A' NOT NULL,
    CONSTRAINT pk_hospital           PRIMARY KEY (id_hospital),
    CONSTRAINT ck_hospital_nivel     CHECK (nivel IN ('I', 'II', 'III', 'IV')),
    CONSTRAINT ck_hospital_latitud   CHECK (latitud  BETWEEN -90  AND 90),
    CONSTRAINT ck_hospital_longitud  CHECK (longitud BETWEEN -180 AND 180),
    CONSTRAINT ck_hospital_capacidad CHECK (capacidad_urgencias >= 0),
    CONSTRAINT ck_hospital_estado    CHECK (estado IN ('A', 'I'))
);

-- 9. SERVICIO_HOSPITAL
CREATE TABLE servicio_hospital (
    id_servicio         NUMBER(10)    DEFAULT seq_servicio_hospital.NEXTVAL NOT NULL,
    id_hospital         NUMBER(10)    NOT NULL,
    nombre_servicio     VARCHAR2(100) NOT NULL,
    disponible          CHAR(1)       DEFAULT 'S' NOT NULL,
    capacidad           NUMBER(5),
    fecha_actualizacion TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_servicio_hospital    PRIMARY KEY (id_servicio),
    CONSTRAINT fk_servicio_hospital    FOREIGN KEY (id_hospital) REFERENCES hospital (id_hospital),
    CONSTRAINT uq_servicio_hosp_nombre UNIQUE (id_hospital, nombre_servicio),
    CONSTRAINT ck_servicio_nombre      CHECK (nombre_servicio = UPPER(nombre_servicio)),
    CONSTRAINT ck_servicio_disponible  CHECK (disponible IN ('S', 'N')),
    CONSTRAINT ck_servicio_capacidad   CHECK (capacidad >= 0)
);


-- ---------------------------------------------------------------------
-- 5. EMERGENCIAS
-- ---------------------------------------------------------------------

-- 10. TIPO_EMERGENCIA  (prioridad: 1 = más baja, 10 = más alta)
CREATE TABLE tipo_emergencia (
    id_tipo_emergencia NUMBER(10)    DEFAULT seq_tipo_emergencia.NEXTVAL NOT NULL,
    nombre             VARCHAR2(80)  NOT NULL,
    prioridad          NUMBER(2)     NOT NULL,
    descripcion        VARCHAR2(200),
    CONSTRAINT pk_tipo_emergencia        PRIMARY KEY (id_tipo_emergencia),
    CONSTRAINT uq_tipo_emergencia_nombre UNIQUE (nombre),
    CONSTRAINT ck_tipo_emergencia_nombre CHECK (nombre = UPPER(nombre)),
    CONSTRAINT ck_tipo_emergencia_prior  CHECK (prioridad BETWEEN 1 AND 10)
);

-- 11. EMERGENCIA
CREATE TABLE emergencia (
    id_emergencia        NUMBER(10)    DEFAULT seq_emergencia.NEXTVAL NOT NULL,
    id_tipo_emergencia   NUMBER(10)    NOT NULL,
    id_usuario_reporta   NUMBER(10)    NOT NULL,
    direccion_referencia VARCHAR2(200) NOT NULL,
    latitud              NUMBER(9,6)   NOT NULL,
    longitud             NUMBER(9,6)   NOT NULL,
    fecha_hora_reporte   TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    severidad            VARCHAR2(20)  NOT NULL,
    descripcion          VARCHAR2(400),
    estado               VARCHAR2(30)  DEFAULT 'REPORTADA' NOT NULL,
    fecha_hora_cierre    TIMESTAMP,
    CONSTRAINT pk_emergencia            PRIMARY KEY (id_emergencia),
    CONSTRAINT fk_emergencia_tipo       FOREIGN KEY (id_tipo_emergencia) REFERENCES tipo_emergencia (id_tipo_emergencia),
    CONSTRAINT fk_emergencia_usuario    FOREIGN KEY (id_usuario_reporta) REFERENCES usuario (id_usuario),
    CONSTRAINT ck_emergencia_latitud    CHECK (latitud  BETWEEN -90  AND 90),
    CONSTRAINT ck_emergencia_longitud   CHECK (longitud BETWEEN -180 AND 180),
    CONSTRAINT ck_emergencia_severidad  CHECK (severidad IN ('BAJA', 'MEDIA', 'ALTA', 'CRITICA')),
    CONSTRAINT ck_emergencia_estado     CHECK (estado IN ('REPORTADA', 'ASIGNADA', 'EN_RUTA',
                                                          'EN_SITIO', 'TRASLADANDO', 'CERRADA')),
    CONSTRAINT ck_emergencia_cierre     CHECK ((estado = 'CERRADA' AND fecha_hora_cierre IS NOT NULL)
                                            OR (estado <> 'CERRADA' AND fecha_hora_cierre IS NULL)),
    CONSTRAINT ck_emergencia_fechas     CHECK (fecha_hora_cierre IS NULL OR fecha_hora_cierre >= fecha_hora_reporte)
);


-- ---------------------------------------------------------------------
-- 6. RED VIAL (GRAFO)
-- ---------------------------------------------------------------------

-- 12. NODO_VIAL
CREATE TABLE nodo_vial (
    id_nodo       NUMBER(10)    DEFAULT seq_nodo_vial.NEXTVAL NOT NULL,
    osm_id        NUMBER(19),
    referencia    VARCHAR2(120),
    latitud       NUMBER(9,6)   NOT NULL,
    longitud      NUMBER(9,6)   NOT NULL,
    tipo_nodo     VARCHAR2(30)  DEFAULT 'INTERSECCION' NOT NULL,
    estado        CHAR(1)       DEFAULT 'A' NOT NULL,
    CONSTRAINT pk_nodo_vial          PRIMARY KEY (id_nodo),
    CONSTRAINT uq_nodo_vial_osm      UNIQUE (osm_id),
    CONSTRAINT ck_nodo_vial_latitud  CHECK (latitud  BETWEEN -90  AND 90),
    CONSTRAINT ck_nodo_vial_longitud CHECK (longitud BETWEEN -180 AND 180),
    CONSTRAINT ck_nodo_vial_tipo     CHECK (tipo_nodo IN ('INTERSECCION', 'GLORIETA', 'PUENTE',
                                                          'ACCESO', 'PUNTO_VIA', 'OTRO')),
    CONSTRAINT ck_nodo_vial_estado   CHECK (estado IN ('A', 'I'))
);

-- 13. TRAMO_VIAL
--    Cada fila es UN sentido de circulación (arista dirigida del grafo).
--    Una vía de doble sentido se guarda como dos filas (A→B y B→A).
--    tiempo_base_seg es una columna virtual: siempre coincide con
--    distancia y velocidad, así no puede quedar desactualizada.
CREATE TABLE tramo_vial (
    id_tramo          NUMBER(10)    DEFAULT seq_tramo_vial.NEXTVAL NOT NULL,
    id_nodo_origen    NUMBER(10)    NOT NULL,
    id_nodo_destino   NUMBER(10)    NOT NULL,
    osm_way_id        NUMBER(19),
    nombre_via        VARCHAR2(120) NOT NULL,
    distancia_m       NUMBER(10,2)  NOT NULL,
    velocidad_max_kmh NUMBER(5,2)   NOT NULL,
    tiempo_base_seg   NUMBER(10,2)  GENERATED ALWAYS AS
                          (ROUND(distancia_m / (velocidad_max_kmh / 3.6), 2)) VIRTUAL,
    estado            VARCHAR2(30)  DEFAULT 'ABIERTO' NOT NULL,
    CONSTRAINT pk_tramo_vial           PRIMARY KEY (id_tramo),
    CONSTRAINT fk_tramo_nodo_origen    FOREIGN KEY (id_nodo_origen)  REFERENCES nodo_vial (id_nodo),
    CONSTRAINT fk_tramo_nodo_destino   FOREIGN KEY (id_nodo_destino) REFERENCES nodo_vial (id_nodo),
    CONSTRAINT ck_tramo_nodos          CHECK (id_nodo_origen <> id_nodo_destino),
    CONSTRAINT ck_tramo_distancia      CHECK (distancia_m > 0),
    CONSTRAINT ck_tramo_velocidad      CHECK (velocidad_max_kmh > 0 AND velocidad_max_kmh <= 120),
    CONSTRAINT ck_tramo_estado         CHECK (estado IN ('ABIERTO', 'CERRADO', 'RESTRINGIDO'))
);

-- 14. SEMAFORO
CREATE TABLE semaforo (
    id_semaforo      NUMBER(10)   DEFAULT seq_semaforo.NEXTVAL NOT NULL,
    id_nodo          NUMBER(10)   NOT NULL,
    codigo           VARCHAR2(30) NOT NULL,
    estado_actual    VARCHAR2(20) DEFAULT 'VERDE' NOT NULL,
    tiempo_ciclo_seg NUMBER(4),
    sincronizado     CHAR(1)      DEFAULT 'N' NOT NULL,
    estado           CHAR(1)      DEFAULT 'A' NOT NULL,
    CONSTRAINT pk_semaforo              PRIMARY KEY (id_semaforo),
    CONSTRAINT fk_semaforo_nodo         FOREIGN KEY (id_nodo) REFERENCES nodo_vial (id_nodo),
    CONSTRAINT uq_semaforo_codigo       UNIQUE (codigo),
    CONSTRAINT ck_semaforo_estado_act   CHECK (estado_actual IN ('VERDE', 'AMARILLO', 'ROJO', 'FUERA_SERVICIO')),
    CONSTRAINT ck_semaforo_ciclo        CHECK (tiempo_ciclo_seg > 0),
    CONSTRAINT ck_semaforo_sincronizado CHECK (sincronizado IN ('S', 'N')),
    CONSTRAINT ck_semaforo_estado       CHECK (estado IN ('A', 'I'))
);


-- ---------------------------------------------------------------------
-- 7. MOVILIDAD: TRÁFICO E INCIDENTES
-- ---------------------------------------------------------------------

-- 15. TRAFICO  (historial; el estado vigente vive en memoria en el grafo)
--    factor_congestion: 1.00 = flujo libre, 2.00 = el doble de lento.
--    fuente: identifica si el dato es simulado o real.
CREATE TABLE trafico (
    id_trafico             NUMBER(12)   DEFAULT seq_trafico.NEXTVAL NOT NULL,
    id_tramo               NUMBER(10)   NOT NULL,
    fecha_hora             TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL,
    nivel                  VARCHAR2(20) NOT NULL,
    velocidad_promedio_kmh NUMBER(6,2),
    factor_congestion      NUMBER(5,2)  NOT NULL,
    fuente                 VARCHAR2(40) DEFAULT 'SIMULACION' NOT NULL,
    CONSTRAINT pk_trafico            PRIMARY KEY (id_trafico),
    CONSTRAINT fk_trafico_tramo      FOREIGN KEY (id_tramo) REFERENCES tramo_vial (id_tramo),
    CONSTRAINT ck_trafico_nivel      CHECK (nivel IN ('FLUIDO', 'MODERADO', 'CONGESTIONADO')),
    CONSTRAINT ck_trafico_velocidad  CHECK (velocidad_promedio_kmh >= 0),
    CONSTRAINT ck_trafico_factor     CHECK (factor_congestion >= 1),
    CONSTRAINT ck_trafico_fuente     CHECK (fuente IN ('MANUAL', 'SIMULACION', 'SENSOR', 'API_EXTERNA'))
);

-- 16. INCIDENTE_VIAL
--    estado por defecto PENDIENTE: un reporte nunca afecta las rutas
--    hasta que alguien lo pasa a ACTIVO de forma explícita.
--    bloquea_tramo = 'S' → el motor de rutas trata el tramo como cerrado;
--    'N' → solo penaliza según la severidad.
CREATE TABLE incidente_vial (
    id_incidente   NUMBER(10)    DEFAULT seq_incidente_vial.NEXTVAL NOT NULL,
    id_tramo       NUMBER(10)    NOT NULL,
    tipo_incidente VARCHAR2(60)  NOT NULL,
    severidad      VARCHAR2(20)  NOT NULL,
    bloquea_tramo  CHAR(1)       DEFAULT 'N' NOT NULL,
    descripcion    VARCHAR2(300),
    fecha_inicio   TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    fecha_fin      TIMESTAMP,
    estado         VARCHAR2(20)  DEFAULT 'PENDIENTE' NOT NULL,
    origen         VARCHAR2(20)  DEFAULT 'OPERADOR' NOT NULL,
    CONSTRAINT pk_incidente_vial      PRIMARY KEY (id_incidente),
    CONSTRAINT fk_incidente_tramo     FOREIGN KEY (id_tramo) REFERENCES tramo_vial (id_tramo),
    CONSTRAINT ck_incidente_tipo      CHECK (tipo_incidente IN ('ACCIDENTE', 'OBRA', 'CIERRE', 'MANIFESTACION', 'OTRO')),
    CONSTRAINT ck_incidente_severidad CHECK (severidad IN ('BAJA', 'MEDIA', 'ALTA', 'CRITICA')),
    CONSTRAINT ck_incidente_bloquea   CHECK (bloquea_tramo IN ('S', 'N')),
    CONSTRAINT ck_incidente_estado    CHECK (estado IN ('PENDIENTE', 'ACTIVO', 'CERRADO', 'RECHAZADO')),
    CONSTRAINT ck_incidente_origen    CHECK (origen IN ('OPERADOR', 'QR_CIUDADANO', 'SIMULACION')),
    CONSTRAINT ck_incidente_fechas    CHECK (fecha_fin IS NULL OR fecha_fin >= fecha_inicio)
);


-- ---------------------------------------------------------------------
-- 8. RUTAS
-- ---------------------------------------------------------------------

-- 17. RUTA
--    Principal y alternativas son filas de esta misma tabla.
--    id_hospital_destino NULL → trayecto hacia la emergencia;
--    con valor → traslado al hospital.
CREATE TABLE ruta (
    id_ruta             NUMBER(12)   DEFAULT seq_ruta.NEXTVAL NOT NULL,
    id_emergencia       NUMBER(10)   NOT NULL,
    id_vehiculo         NUMBER(10)   NOT NULL,
    id_hospital_destino NUMBER(10),
    tipo_ruta           VARCHAR2(20) NOT NULL,
    distancia_km        NUMBER(8,2)  NOT NULL,
    tiempo_estimado_min NUMBER(8,2)  NOT NULL,
    costo_total         NUMBER(12,2) NOT NULL,
    fecha_generacion    TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL,
    es_recomendada      CHAR(1)      DEFAULT 'N' NOT NULL,
    estado              VARCHAR2(20) DEFAULT 'PROPUESTA' NOT NULL,
    CONSTRAINT pk_ruta              PRIMARY KEY (id_ruta),
    CONSTRAINT fk_ruta_emergencia   FOREIGN KEY (id_emergencia)       REFERENCES emergencia (id_emergencia),
    CONSTRAINT fk_ruta_vehiculo     FOREIGN KEY (id_vehiculo)         REFERENCES vehiculo_emergencia (id_vehiculo),
    CONSTRAINT fk_ruta_hospital     FOREIGN KEY (id_hospital_destino) REFERENCES hospital (id_hospital),
    CONSTRAINT ck_ruta_tipo         CHECK (tipo_ruta IN ('PRINCIPAL', 'ALTERNATIVA')),
    CONSTRAINT ck_ruta_valores      CHECK (distancia_km >= 0 AND tiempo_estimado_min >= 0 AND costo_total >= 0),
    CONSTRAINT ck_ruta_recomendada  CHECK (es_recomendada IN ('S', 'N')),
    CONSTRAINT ck_ruta_estado       CHECK (estado IN ('PROPUESTA', 'ACTIVA', 'FINALIZADA', 'DESCARTADA'))
);

-- 18. RUTA_TRAMO
--    La clave es (id_ruta, orden_tramo): cada posición de la secuencia
--    es única, aunque un mismo tramo pudiera repetirse.
CREATE TABLE ruta_tramo (
    id_ruta             NUMBER(12)   NOT NULL,
    orden_tramo         NUMBER(5)    NOT NULL,
    id_tramo            NUMBER(10)   NOT NULL,
    tiempo_estimado_seg NUMBER(10,2),
    factor_congestion   NUMBER(5,2),
    semaforos_afectados NUMBER(3),
    CONSTRAINT pk_ruta_tramo           PRIMARY KEY (id_ruta, orden_tramo),
    CONSTRAINT fk_ruta_tramo_ruta      FOREIGN KEY (id_ruta)  REFERENCES ruta (id_ruta) ON DELETE CASCADE,
    CONSTRAINT fk_ruta_tramo_tramo     FOREIGN KEY (id_tramo) REFERENCES tramo_vial (id_tramo),
    CONSTRAINT ck_ruta_tramo_orden     CHECK (orden_tramo >= 1),
    CONSTRAINT ck_ruta_tramo_tiempo    CHECK (tiempo_estimado_seg >= 0),
    CONSTRAINT ck_ruta_tramo_factor    CHECK (factor_congestion >= 1),
    CONSTRAINT ck_ruta_tramo_semaforos CHECK (semaforos_afectados >= 0)
);


-- ---------------------------------------------------------------------
-- 9. DESPACHO Y SEGUIMIENTO
-- ---------------------------------------------------------------------

-- 19. ASIGNACION_EMERGENCIA
CREATE TABLE asignacion_emergencia (
    id_asignacion          NUMBER(10)   DEFAULT seq_asignacion_emergencia.NEXTVAL NOT NULL,
    id_emergencia          NUMBER(10)   NOT NULL,
    id_vehiculo            NUMBER(10)   NOT NULL,
    id_usuario_coordinador NUMBER(10)   NOT NULL,
    fecha_asignacion       TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL,
    hora_salida            TIMESTAMP,
    hora_llegada           TIMESTAMP,
    estado                 VARCHAR2(30) DEFAULT 'ASIGNADA' NOT NULL,
    CONSTRAINT pk_asignacion_emergencia PRIMARY KEY (id_asignacion),
    CONSTRAINT fk_asignacion_emergencia FOREIGN KEY (id_emergencia)          REFERENCES emergencia (id_emergencia),
    CONSTRAINT fk_asignacion_vehiculo   FOREIGN KEY (id_vehiculo)            REFERENCES vehiculo_emergencia (id_vehiculo),
    CONSTRAINT fk_asignacion_usuario    FOREIGN KEY (id_usuario_coordinador) REFERENCES usuario (id_usuario),
    CONSTRAINT ck_asignacion_estado     CHECK (estado IN ('ASIGNADA', 'EN_RUTA', 'EN_SITIO',
                                                          'TRASLADANDO', 'FINALIZADA', 'CANCELADA')),
    CONSTRAINT ck_asignacion_salida     CHECK (hora_salida  IS NULL OR hora_salida  >= fecha_asignacion),
    CONSTRAINT ck_asignacion_llegada    CHECK (hora_llegada IS NULL OR (hora_salida IS NOT NULL AND hora_llegada >= hora_salida))
);

-- 20. UBICACION_VEHICULO  (historial; se guarda por lotes, no en cada tick)
CREATE TABLE ubicacion_vehiculo (
    id_ubicacion     NUMBER(12)   DEFAULT seq_ubicacion_vehiculo.NEXTVAL NOT NULL,
    id_vehiculo      NUMBER(10)   NOT NULL,
    fecha_hora       TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL,
    latitud          NUMBER(9,6)  NOT NULL,
    longitud         NUMBER(9,6)  NOT NULL,
    velocidad_kmh    NUMBER(7,2),
    direccion_grados NUMBER(6,2),
    fuente           VARCHAR2(20) DEFAULT 'SIMULACION' NOT NULL,
    CONSTRAINT pk_ubicacion_vehiculo PRIMARY KEY (id_ubicacion),
    CONSTRAINT fk_ubicacion_vehiculo FOREIGN KEY (id_vehiculo) REFERENCES vehiculo_emergencia (id_vehiculo),
    CONSTRAINT ck_ubicacion_latitud  CHECK (latitud  BETWEEN -90  AND 90),
    CONSTRAINT ck_ubicacion_longitud CHECK (longitud BETWEEN -180 AND 180),
    CONSTRAINT ck_ubicacion_vel      CHECK (velocidad_kmh >= 0),
    CONSTRAINT ck_ubicacion_rumbo    CHECK (direccion_grados >= 0 AND direccion_grados < 360),
    CONSTRAINT ck_ubicacion_fuente   CHECK (fuente IN ('SIMULACION', 'GPS'))
);


-- ---------------------------------------------------------------------
-- 10. NOTIFICACIONES Y AUDITORÍA
-- ---------------------------------------------------------------------

-- 21. ALERTA  (id_emergencia opcional: hay alertas generales, p. ej. disponibilidad)
CREATE TABLE alerta (
    id_alerta     NUMBER(12)    DEFAULT seq_alerta.NEXTVAL NOT NULL,
    id_emergencia NUMBER(10),
    id_usuario    NUMBER(10)    NOT NULL,
    tipo_alerta   VARCHAR2(40)  NOT NULL,
    mensaje       VARCHAR2(300) NOT NULL,
    fecha_hora    TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    leida         CHAR(1)       DEFAULT 'N' NOT NULL,
    CONSTRAINT pk_alerta            PRIMARY KEY (id_alerta),
    CONSTRAINT fk_alerta_emergencia FOREIGN KEY (id_emergencia) REFERENCES emergencia (id_emergencia),
    CONSTRAINT fk_alerta_usuario    FOREIGN KEY (id_usuario)    REFERENCES usuario (id_usuario),
    CONSTRAINT ck_alerta_tipo       CHECK (tipo_alerta IN ('CONGESTION', 'ACCIDENTE_EN_RUTA', 'CIERRE_VIA',
                                                           'RECALCULO_RUTA', 'CAMBIO_DISPONIBILIDAD', 'OTRO')),
    CONSTRAINT ck_alerta_leida      CHECK (leida IN ('S', 'N'))
);

-- 22. NOTIFICACION
CREATE TABLE notificacion (
    id_notificacion NUMBER(12)    DEFAULT seq_notificacion.NEXTVAL NOT NULL,
    id_usuario      NUMBER(10)    NOT NULL,
    tipo            VARCHAR2(40)  NOT NULL,
    titulo          VARCHAR2(120) NOT NULL,
    mensaje         VARCHAR2(400) NOT NULL,
    fecha_hora      TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    leida           CHAR(1)       DEFAULT 'N' NOT NULL,
    CONSTRAINT pk_notificacion         PRIMARY KEY (id_notificacion),
    CONSTRAINT fk_notificacion_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario),
    CONSTRAINT ck_notificacion_tipo    CHECK (tipo = UPPER(tipo)),
    CONSTRAINT ck_notificacion_leida   CHECK (leida IN ('S', 'N'))
);

-- 23. AUDITORIA  (id_usuario opcional: login fallido de un usuario inexistente,
--                 o acciones del sistema/simulador)
CREATE TABLE auditoria (
    id_auditoria  NUMBER(12)    DEFAULT seq_auditoria.NEXTVAL NOT NULL,
    id_usuario    NUMBER(10),
    modulo        VARCHAR2(60)  NOT NULL,
    operacion     VARCHAR2(40)  NOT NULL,
    registro_id   NUMBER(12),
    fecha_hora    TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    resultado     VARCHAR2(20)  NOT NULL,
    detalle       VARCHAR2(500),
    CONSTRAINT pk_auditoria           PRIMARY KEY (id_auditoria),
    CONSTRAINT fk_auditoria_usuario   FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario),
    CONSTRAINT ck_auditoria_mayus     CHECK (modulo = UPPER(modulo) AND operacion = UPPER(operacion)),
    CONSTRAINT ck_auditoria_resultado CHECK (resultado IN ('EXITO', 'FALLO'))
);


-- ---------------------------------------------------------------------
-- 11. ÍNDICES
--    Oracle no indexa las claves foráneas automáticamente.
-- ---------------------------------------------------------------------
CREATE INDEX ix_usuario_rol              ON usuario (id_rol);
CREATE INDEX ix_vehiculo_estacion        ON vehiculo_emergencia (id_estacion);
CREATE INDEX ix_vehiculo_estado          ON vehiculo_emergencia (estado_operativo);
CREATE INDEX ix_emergencia_estado        ON emergencia (estado);
CREATE INDEX ix_emergencia_fecha         ON emergencia (fecha_hora_reporte);
CREATE INDEX ix_emergencia_tipo          ON emergencia (id_tipo_emergencia);
CREATE INDEX ix_tramo_origen             ON tramo_vial (id_nodo_origen);
CREATE INDEX ix_tramo_destino            ON tramo_vial (id_nodo_destino);
CREATE INDEX ix_semaforo_nodo            ON semaforo (id_nodo);
CREATE INDEX ix_trafico_tramo_fecha      ON trafico (id_tramo, fecha_hora);
CREATE INDEX ix_incidente_tramo          ON incidente_vial (id_tramo);
CREATE INDEX ix_incidente_estado         ON incidente_vial (estado);
CREATE INDEX ix_ruta_emergencia          ON ruta (id_emergencia);
CREATE INDEX ix_ruta_vehiculo            ON ruta (id_vehiculo);
-- ix_ruta_tramo_tramo responde: ¿qué rutas pasan por este tramo? (recálculo)
CREATE INDEX ix_ruta_tramo_tramo         ON ruta_tramo (id_tramo);
CREATE INDEX ix_asignacion_emergencia    ON asignacion_emergencia (id_emergencia);
CREATE INDEX ix_asignacion_vehiculo      ON asignacion_emergencia (id_vehiculo);
CREATE INDEX ix_ubicacion_vehiculo_fecha ON ubicacion_vehiculo (id_vehiculo, fecha_hora);
CREATE INDEX ix_alerta_usuario_leida     ON alerta (id_usuario, leida);
CREATE INDEX ix_notificacion_usuario     ON notificacion (id_usuario, leida);
CREATE INDEX ix_auditoria_fecha          ON auditoria (fecha_hora);
CREATE INDEX ix_auditoria_usuario        ON auditoria (id_usuario);

-- Reglas de negocio garantizadas por índices únicos parciales
-- (Oracle no indexa las filas donde todas las expresiones son NULL):

-- Una unidad no puede tener dos asignaciones abiertas al mismo tiempo.
CREATE UNIQUE INDEX ux_asignacion_vehiculo_abierta ON asignacion_emergencia (
    CASE WHEN estado NOT IN ('FINALIZADA', 'CANCELADA') THEN id_vehiculo END
);

-- Una emergencia y una unidad tienen como máximo una ruta ACTIVA.
CREATE UNIQUE INDEX ux_ruta_activa ON ruta (
    CASE WHEN estado = 'ACTIVA' THEN id_emergencia END,
    CASE WHEN estado = 'ACTIVA' THEN id_vehiculo END
);


-- ---------------------------------------------------------------------
-- 12. COMENTARIOS (diccionario de datos dentro de Oracle)
-- ---------------------------------------------------------------------
COMMENT ON TABLE rol                   IS 'Roles del sistema';
COMMENT ON TABLE permiso               IS 'Catalogo de permisos por modulo y accion';
COMMENT ON TABLE rol_permiso           IS 'Relacion N:M entre roles y permisos';
COMMENT ON TABLE usuario               IS 'Usuarios que operan el sistema';
COMMENT ON TABLE estacion_emergencia   IS 'Estaciones de bomberos y bases de ambulancias';
COMMENT ON TABLE tipo_vehiculo         IS 'Catalogo de tipos de unidad';
COMMENT ON TABLE vehiculo_emergencia   IS 'Ambulancias y vehiculos de bomberos; ultima posicion conocida';
COMMENT ON TABLE hospital              IS 'Destinos hospitalarios de Valledupar';
COMMENT ON TABLE servicio_hospital     IS 'Servicios disponibles por hospital';
COMMENT ON TABLE tipo_emergencia       IS 'Clasificacion de emergencias; prioridad 1 (baja) a 10 (alta)';
COMMENT ON TABLE emergencia            IS 'Registro central de cada emergencia';
COMMENT ON TABLE nodo_vial             IS 'Vertices del grafo vial (intersecciones y puntos de via)';
COMMENT ON TABLE tramo_vial            IS 'Aristas dirigidas del grafo vial: una fila por sentido';
COMMENT ON TABLE semaforo              IS 'Semaforos asociados a intersecciones';
COMMENT ON TABLE trafico               IS 'Historial de mediciones de trafico por tramo';
COMMENT ON TABLE incidente_vial        IS 'Accidentes, obras y cierres que afectan tramos';
COMMENT ON TABLE ruta                  IS 'Rutas calculadas: principal y alternativas';
COMMENT ON TABLE ruta_tramo            IS 'Secuencia ordenada de tramos de una ruta';
COMMENT ON TABLE asignacion_emergencia IS 'Unidad asignada a una emergencia y tiempos del servicio';
COMMENT ON TABLE ubicacion_vehiculo    IS 'Historial de posiciones de las unidades';
COMMENT ON TABLE alerta                IS 'Alertas operativas para los usuarios';
COMMENT ON TABLE notificacion          IS 'Mensajes internos para los usuarios';
COMMENT ON TABLE auditoria             IS 'Trazabilidad de operaciones criticas';

COMMENT ON COLUMN tramo_vial.tiempo_base_seg     IS 'Columna virtual: distancia_m / (velocidad_max_kmh / 3.6)';
COMMENT ON COLUMN trafico.factor_congestion      IS '1.00 = flujo libre; 2.00 = el doble de lento';
COMMENT ON COLUMN incidente_vial.bloquea_tramo   IS 'S = el motor trata el tramo como cerrado; N = solo penaliza';
COMMENT ON COLUMN vehiculo_emergencia.estado_operativo IS 'Disponibilidad de la unidad; la fase de la mision esta en ASIGNACION_EMERGENCIA';
