-- =====================================================================
-- RutaVida · 99_eliminar_esquema.sql
-- Borra TODAS las tablas y secuencias de RutaVida para volver a crear
-- el esquema desde cero. Se pierden los datos.
-- Ejecutar como RUTAVIDA. Solo toca los objetos de esta lista.
-- =====================================================================
BEGIN
    FOR t IN (SELECT column_value AS nombre FROM TABLE(sys.odcivarchar2list(
                'AUDITORIA', 'NOTIFICACION', 'ALERTA', 'UBICACION_VEHICULO',
                'ASIGNACION_EMERGENCIA', 'RUTA_TRAMO', 'RUTA', 'INCIDENTE_VIAL',
                'TRAFICO', 'SEMAFORO', 'TRAMO_VIAL', 'NODO_VIAL', 'EMERGENCIA',
                'TIPO_EMERGENCIA', 'SERVICIO_HOSPITAL', 'HOSPITAL',
                'VEHICULO_EMERGENCIA', 'TIPO_VEHICULO', 'ESTACION_EMERGENCIA',
                'USUARIO', 'ROL_PERMISO', 'PERMISO', 'ROL'))) LOOP
        BEGIN
            EXECUTE IMMEDIATE 'DROP TABLE ' || t.nombre || ' CASCADE CONSTRAINTS PURGE';
        EXCEPTION
            WHEN OTHERS THEN
                IF SQLCODE <> -942 THEN RAISE; END IF;   -- -942: la tabla no existe
        END;
    END LOOP;

    FOR s IN (SELECT column_value AS nombre FROM TABLE(sys.odcivarchar2list(
                'SEQ_ROL', 'SEQ_PERMISO', 'SEQ_USUARIO', 'SEQ_ESTACION_EMERGENCIA',
                'SEQ_TIPO_VEHICULO', 'SEQ_VEHICULO_EMERGENCIA', 'SEQ_HOSPITAL',
                'SEQ_SERVICIO_HOSPITAL', 'SEQ_TIPO_EMERGENCIA', 'SEQ_EMERGENCIA',
                'SEQ_NODO_VIAL', 'SEQ_TRAMO_VIAL', 'SEQ_SEMAFORO', 'SEQ_TRAFICO',
                'SEQ_INCIDENTE_VIAL', 'SEQ_RUTA', 'SEQ_ASIGNACION_EMERGENCIA',
                'SEQ_UBICACION_VEHICULO', 'SEQ_ALERTA', 'SEQ_NOTIFICACION',
                'SEQ_AUDITORIA'))) LOOP
        BEGIN
            EXECUTE IMMEDIATE 'DROP SEQUENCE ' || s.nombre;
        EXCEPTION
            WHEN OTHERS THEN
                IF SQLCODE <> -2289 THEN RAISE; END IF;  -- -2289: la secuencia no existe
        END;
    END LOOP;
END;
/
