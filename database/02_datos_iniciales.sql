-- =====================================================================
-- RutaVida · 02_datos_iniciales.sql
-- Datos maestros mínimos (Anexo A de la propuesta).
-- Ejecutar conectado como RUTAVIDA en localhost:1521/XEPDB1,
-- después de 01_esquema.sql.
--
-- No incluye usuarios: el hash de contraseñas lo genera el backend
-- (SecurityUtil), así que el primer usuario se crea desde Java.
-- No incluye hospitales, estaciones ni red vial: deben cargarse con
-- datos reales y verificados de Valledupar (no inventados).
-- =====================================================================

-- ---------------------------------------------------------------------
-- ROLES
-- ---------------------------------------------------------------------
INSERT INTO rol (nombre_rol, descripcion) VALUES ('ADMINISTRADOR',      'Administra usuarios, catalogos y configuracion');
INSERT INTO rol (nombre_rol, descripcion) VALUES ('OPERADOR_CENTRAL',   'Registra emergencias y consulta rutas');
INSERT INTO rol (nombre_rol, descripcion) VALUES ('COORDINADOR',        'Despacha unidades y supervisa la operacion');
INSERT INTO rol (nombre_rol, descripcion) VALUES ('BOMBERO_PARAMEDICO', 'Personal en la unidad; consulta su mision y su ruta');

-- ---------------------------------------------------------------------
-- PERMISOS: dos acciones por módulo
--   CONSULTAR = ver información; GESTIONAR = crear, editar y cambiar estados
-- ---------------------------------------------------------------------
INSERT INTO permiso (modulo, accion, descripcion) VALUES ('USUARIOS',    'CONSULTAR', 'Ver usuarios y roles');
INSERT INTO permiso (modulo, accion, descripcion) VALUES ('USUARIOS',    'GESTIONAR', 'Crear y editar usuarios, roles y permisos');
INSERT INTO permiso (modulo, accion, descripcion) VALUES ('EMERGENCIAS', 'CONSULTAR', 'Ver emergencias');
INSERT INTO permiso (modulo, accion, descripcion) VALUES ('EMERGENCIAS', 'GESTIONAR', 'Registrar, editar y cerrar emergencias');
INSERT INTO permiso (modulo, accion, descripcion) VALUES ('UNIDADES',    'CONSULTAR', 'Ver unidades y estaciones');
INSERT INTO permiso (modulo, accion, descripcion) VALUES ('UNIDADES',    'GESTIONAR', 'Registrar y editar unidades y estaciones');
INSERT INTO permiso (modulo, accion, descripcion) VALUES ('HOSPITALES',  'CONSULTAR', 'Ver hospitales y servicios');
INSERT INTO permiso (modulo, accion, descripcion) VALUES ('HOSPITALES',  'GESTIONAR', 'Editar hospitales y disponibilidad de servicios');
INSERT INTO permiso (modulo, accion, descripcion) VALUES ('RED_VIAL',    'CONSULTAR', 'Ver nodos, tramos y semaforos');
INSERT INTO permiso (modulo, accion, descripcion) VALUES ('RED_VIAL',    'GESTIONAR', 'Editar nodos, tramos y semaforos');
INSERT INTO permiso (modulo, accion, descripcion) VALUES ('RUTAS',       'CONSULTAR', 'Ver rutas calculadas');
INSERT INTO permiso (modulo, accion, descripcion) VALUES ('RUTAS',       'GESTIONAR', 'Calcular y recalcular rutas');
INSERT INTO permiso (modulo, accion, descripcion) VALUES ('TRAFICO',     'CONSULTAR', 'Ver estado del trafico');
INSERT INTO permiso (modulo, accion, descripcion) VALUES ('TRAFICO',     'GESTIONAR', 'Registrar trafico y controlar el simulador');
INSERT INTO permiso (modulo, accion, descripcion) VALUES ('INCIDENTES',  'CONSULTAR', 'Ver incidentes viales');
INSERT INTO permiso (modulo, accion, descripcion) VALUES ('INCIDENTES',  'GESTIONAR', 'Registrar, validar y cerrar incidentes');
INSERT INTO permiso (modulo, accion, descripcion) VALUES ('DESPACHO',    'CONSULTAR', 'Ver asignaciones');
INSERT INTO permiso (modulo, accion, descripcion) VALUES ('DESPACHO',    'GESTIONAR', 'Asignar unidades y actualizar el estado de la mision');
INSERT INTO permiso (modulo, accion, descripcion) VALUES ('REPORTES',    'CONSULTAR', 'Ver dashboard y reportes');
INSERT INTO permiso (modulo, accion, descripcion) VALUES ('AUDITORIA',   'CONSULTAR', 'Ver el registro de auditoria');

-- ---------------------------------------------------------------------
-- ROL_PERMISO (propuesta inicial; ajustar en equipo)
-- ---------------------------------------------------------------------

-- ADMINISTRADOR: todos los permisos
INSERT INTO rol_permiso (id_rol, id_permiso)
SELECT r.id_rol, p.id_permiso
  FROM rol r CROSS JOIN permiso p
 WHERE r.nombre_rol = 'ADMINISTRADOR';

-- OPERADOR_CENTRAL
INSERT INTO rol_permiso (id_rol, id_permiso)
SELECT r.id_rol, p.id_permiso
  FROM rol r CROSS JOIN permiso p
 WHERE r.nombre_rol = 'OPERADOR_CENTRAL'
   AND (p.modulo, p.accion) IN (
        ('EMERGENCIAS', 'CONSULTAR'), ('EMERGENCIAS', 'GESTIONAR'),
        ('UNIDADES',    'CONSULTAR'),
        ('HOSPITALES',  'CONSULTAR'),
        ('RED_VIAL',    'CONSULTAR'),
        ('RUTAS',       'CONSULTAR'), ('RUTAS',       'GESTIONAR'),
        ('TRAFICO',     'CONSULTAR'),
        ('INCIDENTES',  'CONSULTAR'), ('INCIDENTES',  'GESTIONAR'),
        ('DESPACHO',    'CONSULTAR'),
        ('REPORTES',    'CONSULTAR'));

-- COORDINADOR
INSERT INTO rol_permiso (id_rol, id_permiso)
SELECT r.id_rol, p.id_permiso
  FROM rol r CROSS JOIN permiso p
 WHERE r.nombre_rol = 'COORDINADOR'
   AND (p.modulo, p.accion) IN (
        ('EMERGENCIAS', 'CONSULTAR'), ('EMERGENCIAS', 'GESTIONAR'),
        ('UNIDADES',    'CONSULTAR'), ('UNIDADES',    'GESTIONAR'),
        ('HOSPITALES',  'CONSULTAR'), ('HOSPITALES',  'GESTIONAR'),
        ('RED_VIAL',    'CONSULTAR'),
        ('RUTAS',       'CONSULTAR'), ('RUTAS',       'GESTIONAR'),
        ('TRAFICO',     'CONSULTAR'), ('TRAFICO',     'GESTIONAR'),
        ('INCIDENTES',  'CONSULTAR'), ('INCIDENTES',  'GESTIONAR'),
        ('DESPACHO',    'CONSULTAR'), ('DESPACHO',    'GESTIONAR'),
        ('REPORTES',    'CONSULTAR'),
        ('AUDITORIA',   'CONSULTAR'));

-- BOMBERO_PARAMEDICO
INSERT INTO rol_permiso (id_rol, id_permiso)
SELECT r.id_rol, p.id_permiso
  FROM rol r CROSS JOIN permiso p
 WHERE r.nombre_rol = 'BOMBERO_PARAMEDICO'
   AND (p.modulo, p.accion) IN (
        ('EMERGENCIAS', 'CONSULTAR'),
        ('HOSPITALES',  'CONSULTAR'),
        ('RUTAS',       'CONSULTAR'),
        ('DESPACHO',    'CONSULTAR'));

-- ---------------------------------------------------------------------
-- TIPOS DE VEHÍCULO Y DE EMERGENCIA
-- Prioridad: 1 = más baja, 10 = más alta.
-- Los valores son PROVISIONALES: el equipo debe justificarlos.
-- ---------------------------------------------------------------------
INSERT INTO tipo_vehiculo (nombre, prioridad, descripcion) VALUES ('AMBULANCIA', 8, 'Unidad de atencion medica y traslado');
INSERT INTO tipo_vehiculo (nombre, prioridad, descripcion) VALUES ('BOMBERO',    8, 'Vehiculo del cuerpo de bomberos');

INSERT INTO tipo_emergencia (nombre, prioridad, descripcion) VALUES ('EMERGENCIA_MEDICA', 9, 'Urgencia medica sin accidente de transito');
INSERT INTO tipo_emergencia (nombre, prioridad, descripcion) VALUES ('INCENDIO',          9, 'Incendio estructural, vehicular o forestal');
INSERT INTO tipo_emergencia (nombre, prioridad, descripcion) VALUES ('ACCIDENTE_VIAL',    8, 'Accidente de transito con posibles lesionados');
INSERT INTO tipo_emergencia (nombre, prioridad, descripcion) VALUES ('RESCATE',           7, 'Rescate de personas atrapadas o en riesgo');

COMMIT;
