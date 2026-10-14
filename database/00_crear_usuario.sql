-- =====================================================================
-- RutaVida · 00_crear_usuario.sql
-- Crea el usuario (esquema) RUTAVIDA en la base local de Oracle XE.
--
-- Ejecutar UNA sola vez, conectado como SYSTEM a la PDB XEPDB1:
--   SQL Developer: conexión SYSTEM, host localhost, puerto 1521,
--                  "Nombre del servicio" = XEPDB1
--   SQL*Plus/SQLcl: sql system@//localhost:1521/XEPDB1
--
-- El script pide la contraseña del nuevo usuario (variable rv_password).
-- Esa contraseña NO se escribe en ningún archivo del repositorio:
-- cada integrante la guarda en su backend/db.properties local.
-- =====================================================================

-- Comprobación: debe mostrar XEPDB1. Si muestra CDB$ROOT, la conexión
-- está apuntando al contenedor raíz y el usuario quedaría mal creado.
SELECT SYS_CONTEXT('USERENV', 'CON_NAME') AS contenedor_actual FROM dual;

CREATE USER rutavida IDENTIFIED BY "&&rv_password"
    DEFAULT TABLESPACE users
    TEMPORARY TABLESPACE temp
    QUOTA 500M ON users;

GRANT CREATE SESSION   TO rutavida;
GRANT CREATE TABLE     TO rutavida;
GRANT CREATE SEQUENCE  TO rutavida;
GRANT CREATE VIEW      TO rutavida;
GRANT CREATE PROCEDURE TO rutavida;
GRANT CREATE TRIGGER   TO rutavida;

UNDEFINE rv_password
