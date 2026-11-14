-- =====================================================================
-- RutaVida · 07_boton_panico.sql
-- Botón de pánico, rol Policía y patrullas (decisiones DT-10 y DT-12).
-- Ejecutar conectado como RUTAVIDA, después de 01, 02 y 04 (y de 03, 05 y 06
-- si se usan), antes de 98_verificar.sql.
--
-- Se puede ejecutar varias veces: lo que ya existe no se vuelve a crear
-- ni se duplica. No borra ni cambia datos que ya estén.
--
-- Crea:
--   1. Tabla REPORTE_PANICO (tabla 24), su secuencia y 3 índices.
--   2. Tipo de vehículo POLICIA (patrullas simuladas, DT-10).
--   3. Rol POLICIA y permisos del módulo PANICO.
--   4. Usuario del sistema (inactivo, sin contraseña válida): queda como
--      quien reporta y despacha lo que hace el botón de pánico solo.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. TABLA REPORTE_PANICO
--    Cada vez que alguien presiona el botón queda una fila. Una alerta
--    se valida cuando hay 3 reportes de dispositivos distintos a menos de
--    300 m en 10 minutos, o 1 reporte de un usuario con PANICO/VALIDAR
--    (Policía). Las reglas las aplica el backend (PanicService).
-- ---------------------------------------------------------------------
DECLARE
    PROCEDURE run(p_sql VARCHAR2) IS
    BEGIN
        EXECUTE IMMEDIATE p_sql;
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE <> -955 THEN RAISE; END IF;   -- -955: el nombre ya existe
    END;
BEGIN
    run('CREATE SEQUENCE seq_reporte_panico START WITH 1 INCREMENT BY 1');

    run('CREATE TABLE reporte_panico (
        id_reporte         NUMBER(12)    DEFAULT seq_reporte_panico.NEXTVAL NOT NULL,
        latitud            NUMBER(9,6)   NOT NULL,
        longitud           NUMBER(9,6)   NOT NULL,
        fecha_hora         TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
        id_dispositivo     VARCHAR2(64)  NOT NULL,
        id_usuario         NUMBER(10),
        id_tipo_emergencia NUMBER(10),
        descripcion        VARCHAR2(200),
        estado             VARCHAR2(20)  DEFAULT ''PENDIENTE'' NOT NULL,
        id_emergencia      NUMBER(10),
        CONSTRAINT pk_reporte_panico            PRIMARY KEY (id_reporte),
        CONSTRAINT fk_reporte_panico_usuario    FOREIGN KEY (id_usuario)         REFERENCES usuario (id_usuario),
        CONSTRAINT fk_reporte_panico_tipo       FOREIGN KEY (id_tipo_emergencia) REFERENCES tipo_emergencia (id_tipo_emergencia),
        CONSTRAINT fk_reporte_panico_emergencia FOREIGN KEY (id_emergencia)      REFERENCES emergencia (id_emergencia),
        CONSTRAINT ck_reporte_panico_latitud    CHECK (latitud  BETWEEN -90  AND 90),
        CONSTRAINT ck_reporte_panico_longitud   CHECK (longitud BETWEEN -180 AND 180),
        CONSTRAINT ck_reporte_panico_estado     CHECK (estado IN (''PENDIENTE'', ''VALIDADO'', ''DESCARTADO'', ''VENCIDO'')),
        CONSTRAINT ck_reporte_panico_validado   CHECK ((estado = ''VALIDADO'' AND id_emergencia IS NOT NULL)
                                                    OR (estado <> ''VALIDADO'' AND id_emergencia IS NULL))
    )');

    -- Reportes pendientes de los últimos minutos (buscar vecinos para validar)
    run('CREATE INDEX ix_reporte_panico_estado_fecha ON reporte_panico (estado, fecha_hora)');
    -- Límite de reportes por dispositivo
    run('CREATE INDEX ix_reporte_panico_dispositivo  ON reporte_panico (id_dispositivo, fecha_hora)');
    -- Reportes que originaron una emergencia
    run('CREATE INDEX ix_reporte_panico_emergencia   ON reporte_panico (id_emergencia)');
END;
/

COMMENT ON TABLE  reporte_panico                IS 'Cada vez que alguien presiona el boton de panico (CU-22)';
COMMENT ON COLUMN reporte_panico.id_dispositivo IS 'Hash SHA-256 del identificador que genera el navegador; no es un dato personal';
COMMENT ON COLUMN reporte_panico.id_usuario     IS 'Solo si quien reporta tiene sesion (por ejemplo, un Policia); NULL si es anonimo';
COMMENT ON COLUMN reporte_panico.id_emergencia  IS 'La emergencia que se creo al validar la alerta';

-- ---------------------------------------------------------------------
-- 2. TIPO DE VEHÍCULO POLICIA (patrullas simuladas, rotuladas SIMULACION)
-- ---------------------------------------------------------------------
INSERT INTO tipo_vehiculo (nombre, prioridad, descripcion)
SELECT 'POLICIA', 7, 'Patrulla de policia (simulada en la demostracion)'
  FROM dual
 WHERE NOT EXISTS (SELECT 1 FROM tipo_vehiculo WHERE nombre = 'POLICIA');

-- ---------------------------------------------------------------------
-- 3. ROL POLICIA Y PERMISOS DEL MÓDULO PANICO
--    PANICO/CONSULTAR = ver los reportes de pánico en el mapa
--    PANICO/GESTIONAR = descartar una alerta falsa
--    PANICO/VALIDAR   = su reporte valida la alerta solo (Policía)
-- ---------------------------------------------------------------------
INSERT INTO rol (nombre_rol, descripcion)
SELECT 'POLICIA', 'Policia en la calle; su reporte de panico valida la alerta'
  FROM dual
 WHERE NOT EXISTS (SELECT 1 FROM rol WHERE nombre_rol = 'POLICIA');

INSERT INTO permiso (modulo, accion, descripcion)
SELECT p.modulo, p.accion, p.descripcion
  FROM (SELECT 'PANICO' AS modulo, 'CONSULTAR' AS accion, 'Ver los reportes del boton de panico' AS descripcion FROM dual
        UNION ALL SELECT 'PANICO', 'GESTIONAR', 'Descartar una alerta de panico falsa' FROM dual
        UNION ALL SELECT 'PANICO', 'VALIDAR', 'Su reporte de panico valida la alerta sin esperar a otros' FROM dual) p
 WHERE NOT EXISTS (SELECT 1 FROM permiso x WHERE x.modulo = p.modulo AND x.accion = p.accion);

INSERT INTO rol_permiso (id_rol, id_permiso)
SELECT r.id_rol, p.id_permiso
  FROM rol r CROSS JOIN permiso p
 WHERE (   (r.nombre_rol = 'ADMINISTRADOR'    AND (p.modulo, p.accion) IN (('PANICO', 'CONSULTAR'), ('PANICO', 'GESTIONAR')))
        OR (r.nombre_rol = 'OPERADOR_CENTRAL' AND (p.modulo, p.accion) IN (('PANICO', 'CONSULTAR')))
        OR (r.nombre_rol = 'COORDINADOR'      AND (p.modulo, p.accion) IN (('PANICO', 'CONSULTAR'), ('PANICO', 'GESTIONAR')))
        OR (r.nombre_rol = 'POLICIA'          AND (p.modulo, p.accion) IN (('EMERGENCIAS', 'CONSULTAR'),
                                                                           ('PANICO', 'CONSULTAR'), ('PANICO', 'VALIDAR'))))
   AND NOT EXISTS (SELECT 1 FROM rol_permiso x WHERE x.id_rol = r.id_rol AND x.id_permiso = p.id_permiso);

-- ---------------------------------------------------------------------
-- 4. USUARIO DEL SISTEMA
--    Las emergencias que crea el botón de pánico y su despacho automático
--    necesitan un usuario (EMERGENCIA.id_usuario_reporta y
--    ASIGNACION_EMERGENCIA.id_usuario_coordinador son obligatorios).
--    Está INACTIVO y su password_hash no es un hash: nadie puede iniciar
--    sesión con él. Tiene el rol COORDINADOR para que la auditoría
--    muestre quién despachó.
-- ---------------------------------------------------------------------
INSERT INTO usuario (id_rol, documento, nombres, apellidos, email, username, password_hash, estado)
SELECT r.id_rol, 'SISTEMA-0001', 'Sistema', 'RutaVida', 'sistema@rutavida.local', 'sistema.rutavida',
       'SIN_ACCESO: usuario del sistema, no inicia sesion', 'I'
  FROM rol r
 WHERE r.nombre_rol = 'COORDINADOR'
   AND NOT EXISTS (SELECT 1 FROM usuario WHERE username = 'sistema.rutavida');

COMMIT;

-- ---------------------------------------------------------------------
-- Verificación rápida: todo debe decir SI
-- ---------------------------------------------------------------------
SELECT 'Tabla REPORTE_PANICO' AS objeto,
       CASE WHEN COUNT(*) = 1 THEN 'SI' ELSE 'NO' END AS ok
  FROM user_tables WHERE table_name = 'REPORTE_PANICO'
UNION ALL
SELECT 'Indices de REPORTE_PANICO', CASE WHEN COUNT(*) = 3 THEN 'SI' ELSE 'NO' END
  FROM user_indexes WHERE table_name = 'REPORTE_PANICO' AND index_name LIKE 'IX\_%' ESCAPE '\'
UNION ALL
SELECT 'Tipo de vehiculo POLICIA', CASE WHEN COUNT(*) = 1 THEN 'SI' ELSE 'NO' END
  FROM tipo_vehiculo WHERE nombre = 'POLICIA'
UNION ALL
SELECT 'Rol POLICIA', CASE WHEN COUNT(*) = 1 THEN 'SI' ELSE 'NO' END
  FROM rol WHERE nombre_rol = 'POLICIA'
UNION ALL
SELECT 'Permisos PANICO', CASE WHEN COUNT(*) = 3 THEN 'SI' ELSE 'NO' END
  FROM permiso WHERE modulo = 'PANICO'
UNION ALL
SELECT 'Permisos asignados de PANICO y POLICIA', CASE WHEN COUNT(*) = 8 THEN 'SI' ELSE 'NO' END
  FROM rol_permiso rp JOIN rol r ON r.id_rol = rp.id_rol JOIN permiso p ON p.id_permiso = rp.id_permiso
 WHERE p.modulo = 'PANICO' OR r.nombre_rol = 'POLICIA'
UNION ALL
SELECT 'Usuario del sistema inactivo', CASE WHEN COUNT(*) = 1 THEN 'SI' ELSE 'NO' END
  FROM usuario WHERE username = 'sistema.rutavida' AND estado = 'I';
