-- =====================================================================
-- RutaVida · 15_operacion_conductor.sql
-- Operación desde Compartir ubicación: llegada, finalización y evidencia.
-- Ejecutar como RUTAVIDA después de 14_gps_conductor.sql.
-- Es seguro ejecutarlo más de una vez.
-- =====================================================================

DECLARE
    v_column_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_column_count
      FROM user_tab_columns
     WHERE table_name = 'ASIGNACION_EMERGENCIA'
       AND column_name = 'HORA_FINALIZACION';

    IF v_column_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE asignacion_emergencia ADD (
            hora_finalizacion TIMESTAMP,
            CONSTRAINT ck_asignacion_finalizacion CHECK (
                hora_finalizacion IS NULL OR
                (hora_llegada IS NOT NULL AND hora_finalizacion >= hora_llegada)
            )
        )';
    END IF;
END;
/

COMMENT ON COLUMN asignacion_emergencia.hora_finalizacion IS
    'Hora en la que el conductor confirmó que la atención terminó';

-- El conductor puede operar únicamente la misión de su propia unidad; la API valida el vínculo
-- de UNIDAD_CONDUCTOR antes de permitir llegada o finalización.
INSERT INTO permiso (modulo, accion, descripcion)
SELECT 'GPS', 'OPERAR', 'Confirmar llegada y finalizar la atención de la unidad asignada'
  FROM dual
 WHERE NOT EXISTS (SELECT 1 FROM permiso WHERE modulo = 'GPS' AND accion = 'OPERAR');

INSERT INTO rol_permiso (id_rol, id_permiso)
SELECT r.id_rol, p.id_permiso
  FROM rol r CROSS JOIN permiso p
 WHERE r.nombre_rol IN ('ADMINISTRADOR', 'BOMBERO_PARAMEDICO')
   AND p.modulo = 'GPS' AND p.accion = 'OPERAR'
   AND NOT EXISTS (
       SELECT 1 FROM rol_permiso x WHERE x.id_rol = r.id_rol AND x.id_permiso = p.id_permiso
   );

-- Permite al conductor ver únicamente los marcadores de bases necesarias en su mapa operativo.
INSERT INTO rol_permiso (id_rol, id_permiso)
SELECT r.id_rol, p.id_permiso
  FROM rol r CROSS JOIN permiso p
 WHERE r.nombre_rol = 'BOMBERO_PARAMEDICO'
   AND p.modulo = 'UNIDADES' AND p.accion = 'CONSULTAR'
   AND NOT EXISTS (
       SELECT 1 FROM rol_permiso x WHERE x.id_rol = r.id_rol AND x.id_permiso = p.id_permiso
   );

COMMIT;

-- Verificación breve para la hoja de trabajo de Oracle.
SELECT 'HORA_FINALIZACION' AS objeto, CASE WHEN COUNT(*) = 1 THEN 'SI' ELSE 'NO' END AS ok
  FROM user_tab_columns
 WHERE table_name = 'ASIGNACION_EMERGENCIA' AND column_name = 'HORA_FINALIZACION'
UNION ALL
SELECT 'Permiso GPS/OPERAR', CASE WHEN COUNT(*) = 1 THEN 'SI' ELSE 'NO' END
  FROM permiso WHERE modulo = 'GPS' AND accion = 'OPERAR'
UNION ALL
SELECT 'Rol BOMBERO_PARAMEDICO puede operar GPS', CASE WHEN COUNT(*) = 1 THEN 'SI' ELSE 'NO' END
  FROM rol_permiso rp JOIN rol r ON r.id_rol = rp.id_rol JOIN permiso p ON p.id_permiso = rp.id_permiso
 WHERE r.nombre_rol = 'BOMBERO_PARAMEDICO' AND p.modulo = 'GPS' AND p.accion = 'OPERAR';
