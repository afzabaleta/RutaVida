-- =====================================================================
-- RutaVida · 08_ciudadanos.sql
-- Botón de pánico con cuenta (decisión DT-13, recomendación del profesor):
-- solo lo presiona una persona registrada, que queda conectada en su
-- celular y se bloquea si acumula alertas falsas.
-- Ejecutar conectado como RUTAVIDA, después de 07_boton_panico.sql y
-- antes de 98_verificar.sql.
--
-- Se puede ejecutar varias veces: lo que ya existe no se vuelve a crear
-- ni se duplica. No borra ni cambia datos que ya estén.
--
-- Crea:
--   1. Permiso PANICO/REPORTAR (presionar el botón).
--   2. Rol CIUDADANO, que solo tiene ese permiso.
--   3. El permiso para CIUDADANO, POLICIA y ADMINISTRADOR.
--   4. Tabla SESION_CIUDADANO (tabla 25), su secuencia y su índice: la
--      sesión de 30 días del celular del ciudadano.
--   5. Índice de REPORTE_PANICO por usuario (límite por persona y
--      alertas falsas de cada persona).
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. PERMISO PANICO/REPORTAR
-- ---------------------------------------------------------------------
INSERT INTO permiso (modulo, accion, descripcion)
SELECT 'PANICO', 'REPORTAR', 'Presionar el boton de panico con su cuenta'
  FROM dual
 WHERE NOT EXISTS (SELECT 1 FROM permiso WHERE modulo = 'PANICO' AND accion = 'REPORTAR');

-- ---------------------------------------------------------------------
-- 2. ROL CIUDADANO
--    Persona que se registra sola en la web para usar el botón de pánico.
--    No ve emergencias, unidades ni el mapa del centro de operaciones.
-- ---------------------------------------------------------------------
INSERT INTO rol (nombre_rol, descripcion)
SELECT 'CIUDADANO', 'Persona registrada que usa el boton de panico'
  FROM dual
 WHERE NOT EXISTS (SELECT 1 FROM rol WHERE nombre_rol = 'CIUDADANO');

-- ---------------------------------------------------------------------
-- 3. QUIÉN PUEDE PRESIONAR EL BOTÓN
--    CIUDADANO: su único permiso. POLICIA: reporta y su reporte valida
--    la alerta solo (PANICO/VALIDAR). ADMINISTRADOR: tiene todos.
-- ---------------------------------------------------------------------
INSERT INTO rol_permiso (id_rol, id_permiso)
SELECT r.id_rol, p.id_permiso
  FROM rol r CROSS JOIN permiso p
 WHERE r.nombre_rol IN ('CIUDADANO', 'POLICIA', 'ADMINISTRADOR')
   AND p.modulo = 'PANICO' AND p.accion = 'REPORTAR'
   AND NOT EXISTS (SELECT 1 FROM rol_permiso x WHERE x.id_rol = r.id_rol AND x.id_permiso = p.id_permiso);

-- ---------------------------------------------------------------------
-- 4. TABLA SESION_CIUDADANO
--    El ciudadano inicia sesión una vez y su celular queda conectado 30
--    días, aunque el servidor se reinicie. Solo se guarda el hash
--    SHA-256 del token, nunca el token. Una sesión revocada (cerró
--    sesión o lo bloquearon) ya no sirve.
-- ---------------------------------------------------------------------
DECLARE
    PROCEDURE run(p_sql VARCHAR2) IS
    BEGIN
        EXECUTE IMMEDIATE p_sql;
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE NOT IN (-955, -1408) THEN RAISE; END IF;   -- -955: ya existe; -1408: columnas ya indexadas
    END;
BEGIN
    run('CREATE SEQUENCE seq_sesion_ciudadano START WITH 1 INCREMENT BY 1');

    run('CREATE TABLE sesion_ciudadano (
        id_sesion         NUMBER(12)   DEFAULT seq_sesion_ciudadano.NEXTVAL NOT NULL,
        id_usuario        NUMBER(10)   NOT NULL,
        token_hash        VARCHAR2(64) NOT NULL,
        fecha_creacion    TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL,
        fecha_vencimiento TIMESTAMP    NOT NULL,
        revocada          CHAR(1)      DEFAULT ''N'' NOT NULL,
        CONSTRAINT pk_sesion_ciudadano          PRIMARY KEY (id_sesion),
        CONSTRAINT fk_sesion_ciudadano_usuario  FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario),
        CONSTRAINT uq_sesion_ciudadano_token    UNIQUE (token_hash),
        CONSTRAINT ck_sesion_ciudadano_revocada CHECK (revocada IN (''S'', ''N'')),
        CONSTRAINT ck_sesion_ciudadano_fechas   CHECK (fecha_vencimiento > fecha_creacion)
    )');

    -- Revocar todas las sesiones de una persona (cerrar sesión o bloquearla)
    run('CREATE INDEX ix_sesion_ciudadano_usuario ON sesion_ciudadano (id_usuario)');

    -- ---------------------------------------------------------------
    -- 5. Reportes de pánico de cada persona: límite de 3 en 10 minutos
    --    y alertas falsas de los últimos 30 días (bloqueo, RN-16)
    -- ---------------------------------------------------------------
    run('CREATE INDEX ix_reporte_panico_usuario ON reporte_panico (id_usuario, fecha_hora)');
END;
/

COMMENT ON TABLE  sesion_ciudadano            IS 'Sesion de 30 dias del celular de un ciudadano para el boton de panico (DT-13)';
COMMENT ON COLUMN sesion_ciudadano.token_hash IS 'Hash SHA-256 del token; el token solo lo tiene el celular';
COMMENT ON COLUMN sesion_ciudadano.revocada   IS 'S si cerro sesion o la cuenta se bloqueo';
COMMENT ON COLUMN reporte_panico.id_usuario   IS 'Quien presiono el boton (obligatorio desde DT-13; NULL solo en reportes anonimos anteriores)';

COMMIT;

-- ---------------------------------------------------------------------
-- Verificación rápida: todo debe decir SI
-- ---------------------------------------------------------------------
SELECT 'Permiso PANICO/REPORTAR' AS objeto,
       CASE WHEN COUNT(*) = 1 THEN 'SI' ELSE 'NO' END AS ok
  FROM permiso WHERE modulo = 'PANICO' AND accion = 'REPORTAR'
UNION ALL
SELECT 'Rol CIUDADANO', CASE WHEN COUNT(*) = 1 THEN 'SI' ELSE 'NO' END
  FROM rol WHERE nombre_rol = 'CIUDADANO'
UNION ALL
SELECT 'CIUDADANO, POLICIA y ADMINISTRADOR pueden reportar', CASE WHEN COUNT(*) = 3 THEN 'SI' ELSE 'NO' END
  FROM rol_permiso rp JOIN rol r ON r.id_rol = rp.id_rol JOIN permiso p ON p.id_permiso = rp.id_permiso
 WHERE p.modulo = 'PANICO' AND p.accion = 'REPORTAR'
   AND r.nombre_rol IN ('CIUDADANO', 'POLICIA', 'ADMINISTRADOR')
UNION ALL
SELECT 'CIUDADANO solo tiene un permiso', CASE WHEN COUNT(*) = 1 THEN 'SI' ELSE 'NO' END
  FROM rol_permiso rp JOIN rol r ON r.id_rol = rp.id_rol
 WHERE r.nombre_rol = 'CIUDADANO'
UNION ALL
SELECT 'Tabla SESION_CIUDADANO', CASE WHEN COUNT(*) = 1 THEN 'SI' ELSE 'NO' END
  FROM user_tables WHERE table_name = 'SESION_CIUDADANO'
UNION ALL
SELECT 'Indices nuevos', CASE WHEN COUNT(*) = 2 THEN 'SI' ELSE 'NO' END
  FROM user_indexes WHERE index_name IN ('IX_SESION_CIUDADANO_USUARIO', 'IX_REPORTE_PANICO_USUARIO');
