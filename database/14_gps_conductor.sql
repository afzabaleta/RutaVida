-- =====================================================================
-- RutaVida · 14_gps_conductor.sql
-- Seguimiento GPS real desde el teléfono del conductor (DT-15).
-- Ejecutar como RUTAVIDA después de 08_ciudadanos.sql y antes de
-- 98_verificar.sql. Se puede ejecutar más de una vez sin duplicar datos.
--
-- La tabla separa la asignación de una persona y su unidad de la unidad
-- misma: crear una ambulancia no autoriza a cualquier usuario a moverla.
-- =====================================================================

DECLARE
    PROCEDURE run(p_sql VARCHAR2) IS
    BEGIN
        EXECUTE IMMEDIATE p_sql;
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE <> -955 THEN RAISE; END IF;  -- objeto ya existe
    END;
BEGIN
    run('CREATE TABLE unidad_conductor (
        id_usuario   NUMBER(10) NOT NULL,
        id_vehiculo  NUMBER(10) NOT NULL,
        asignado_en  TIMESTAMP  DEFAULT SYSTIMESTAMP NOT NULL,
        CONSTRAINT pk_unidad_conductor          PRIMARY KEY (id_usuario),
        CONSTRAINT uq_unidad_conductor_vehiculo UNIQUE (id_vehiculo),
        CONSTRAINT fk_unidad_conductor_usuario  FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario),
        CONSTRAINT fk_unidad_conductor_vehiculo FOREIGN KEY (id_vehiculo) REFERENCES vehiculo_emergencia (id_vehiculo)
    )');
END;
/

COMMENT ON TABLE unidad_conductor IS 'Una persona conductora autorizada para transmitir GPS de una unidad';
COMMENT ON COLUMN unidad_conductor.asignado_en IS 'Momento de la asignacion administrativa';

-- GPS/REPORTAR: lo usa el navegador del conductor para enviar su propia ubicación.
INSERT INTO permiso (modulo, accion, descripcion)
SELECT 'GPS', 'REPORTAR', 'Compartir la ubicacion GPS de la unidad asignada'
  FROM dual
 WHERE NOT EXISTS (SELECT 1 FROM permiso WHERE modulo = 'GPS' AND accion = 'REPORTAR');

-- El administrador conserva todos los permisos. BOMBERO_PARAMEDICO representa el personal
-- de campo; no puede escoger una unidad: la fila de UNIDAD_CONDUCTOR lo limita a una sola.
INSERT INTO rol_permiso (id_rol, id_permiso)
SELECT r.id_rol, p.id_permiso
  FROM rol r CROSS JOIN permiso p
 WHERE r.nombre_rol IN ('ADMINISTRADOR', 'BOMBERO_PARAMEDICO')
   AND p.modulo = 'GPS' AND p.accion = 'REPORTAR'
   AND NOT EXISTS (SELECT 1 FROM rol_permiso x WHERE x.id_rol = r.id_rol AND x.id_permiso = p.id_permiso);

COMMIT;

-- ---------------------------------------------------------------------
-- Asociar el conductor creado en Administración con la ambulancia ya
-- registrada. Reemplace los dos valores por los identificadores reales.
-- Una nueva asignación del mismo usuario o vehículo se hace primero con:
-- DELETE FROM unidad_conductor WHERE id_usuario = <ID_CONDUCTOR> OR id_vehiculo = <ID_UNIDAD>;
-- INSERT INTO unidad_conductor (id_usuario, id_vehiculo) VALUES (<ID_CONDUCTOR>, <ID_UNIDAD>);
-- ---------------------------------------------------------------------

SELECT 'Tabla UNIDAD_CONDUCTOR' AS objeto, CASE WHEN COUNT(*) = 1 THEN 'SI' ELSE 'NO' END AS ok
  FROM user_tables WHERE table_name = 'UNIDAD_CONDUCTOR'
UNION ALL
SELECT 'Permiso GPS/REPORTAR', CASE WHEN COUNT(*) = 1 THEN 'SI' ELSE 'NO' END
  FROM permiso WHERE modulo = 'GPS' AND accion = 'REPORTAR'
UNION ALL
SELECT 'Rol BOMBERO_PARAMEDICO puede reportar', CASE WHEN COUNT(*) = 1 THEN 'SI' ELSE 'NO' END
  FROM rol_permiso rp JOIN rol r ON r.id_rol = rp.id_rol JOIN permiso p ON p.id_permiso = rp.id_permiso
 WHERE r.nombre_rol = 'BOMBERO_PARAMEDICO' AND p.modulo = 'GPS' AND p.accion = 'REPORTAR';
