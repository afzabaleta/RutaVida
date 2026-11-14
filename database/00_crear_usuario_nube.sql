-- =====================================================================
-- RutaVida · 00_crear_usuario_nube.sql
-- Crea el usuario (esquema) RUTAVIDA en Oracle Autonomous Database
-- (Oracle Cloud, plan Always Free). Es la versión para la nube de
-- 00_crear_usuario.sql: allá no hay SYSTEM ni tablespace USERS.
--
-- Ejecutar UNA sola vez, conectado como ADMIN con el wallet de la base
-- (SQL Developer: tipo de conexión "Cartera de nube", servicio *_low).
--
-- El script pide la contraseña del nuevo usuario (variable rv_password).
-- Autonomous Database exige de 12 a 30 caracteres, con mayúscula,
-- minúscula y número, sin comillas dobles y sin el nombre del usuario
-- (no puede contener "rutavida" ni "admin").
-- Esa contraseña NO se escribe en ningún archivo del repositorio:
-- cada integrante la guarda en su backend/db.properties local.
-- =====================================================================

-- No mostrar en la salida el CREATE USER con la contraseña reemplazada.
SET VERIFY OFF

-- Comprobación: debe mostrar ADMIN.
SELECT USER AS usuario_actual FROM dual;

CREATE USER rutavida IDENTIFIED BY "&&rv_password"
    DEFAULT TABLESPACE data
    TEMPORARY TABLESPACE temp
    QUOTA 500M ON data;

GRANT CREATE SESSION   TO rutavida;
GRANT CREATE TABLE     TO rutavida;
GRANT CREATE SEQUENCE  TO rutavida;
GRANT CREATE VIEW      TO rutavida;
GRANT CREATE PROCEDURE TO rutavida;
GRANT CREATE TRIGGER   TO rutavida;

UNDEFINE rv_password

-- Después: crear una conexión nueva como RUTAVIDA (misma cartera) y
-- ejecutar 01, 02, 03, 04, 05, 06, 07 y 98, igual que en local.
