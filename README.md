# RutaVida

**Sistema Inteligente de Rutas de Emergencia para Valledupar**

Proyecto de Programación de Computadores III + Base de Datos — Ingeniería de Sistemas, Universidad Popular del Cesar.

> Prototipo académico. No reemplaza un servicio oficial de despacho y no debe usarse para coordinar emergencias reales.

## Equipo

| Integrante | Responsabilidad |
|---|---|
| Andrés (líder) | Base de datos Oracle, DAO e integración |
| Kendrick | Service y motor de rutas |
| Diego | Vista (interfaz) y versión móvil |
| Sherly | Model, Controller, Util y pruebas |

## Estructura del repositorio

```
RutaVida/
├── backend/            Proyecto Java 21 + Maven (paquete co.edu.unicesar.rutavida)
│   ├── pom.xml
│   ├── db.properties.example   ← plantilla; la copia real (db.properties) no se sube
│   └── src/
├── database/           Scripts SQL y PL/SQL de Oracle (en orden numérico)
└── docs/               Documentación del proyecto (en español)
```

## Estado actual

| Componente | Estado |
|---|---|
| Esquema de base de datos (23 tablas, 21 secuencias, 24 índices, restricciones) | Ejecutado y verificado en Oracle XE 18c con `98_verificar.sql` (14/10/2026) |
| Datos maestros (roles, permisos, tipos de vehículo y de emergencia) | Cargados y verificados |
| Conexión JDBC (`DatabaseConnection`) y verificación (`DatabaseCheck`) | Funciona: "23 de 23" con el driver ojdbc11 21.9 |
| Arquitectura: paquetes, reglas de dependencia, enums, transacciones, eventos, `AppContext` | Programado y probado: 37 pruebas en verde en IntelliJ ([`docs/arquitectura-software.md`](docs/arquitectura-software.md)) |
| Vistas, funciones, procedimientos y triggers PL/SQL (`04_plsql.sql`) | Programado; se prueba con la sección 8 de `98_verificar.sql` |
| Entidades, DAO, Service, Controller, vista | Pendiente |
| Vista | **Decidido: web** (HTML, CSS, JavaScript y Leaflet + API REST en Java). Pendiente de programar |
| Decisiones del equipo (vista web, Oracle local, tablas en español) | Registradas en [`docs/decisiones-tecnicas.md`](docs/decisiones-tecnicas.md) |

## Puesta en marcha (cada integrante, una sola vez)

### 1. Base de datos local

Requisito: Oracle Database XE instalado y en ejecución, con la PDB `XEPDB1` (puerto 1521).

Desde la consola de Windows (`cmd`), con SQL*Plus, que viene con Oracle XE:

```
cd ruta\a\RutaVida\database
sqlplus /nolog
```

Dentro de `SQL>`:

```
CONNECT system/CONTRASEÑA_DE_SYSTEM@localhost:1521/XEPDB1
@00_crear_usuario.sql
CONNECT rutavida/CONTRASEÑA_DE_RUTAVIDA@localhost:1521/XEPDB1
SPOOL instalacion.log
@01_esquema.sql
@02_datos_iniciales.sql
@04_plsql.sql
@98_verificar.sql
SPOOL OFF
exit
```

- `00_crear_usuario.sql` pide la contraseña del nuevo usuario `RUTAVIDA` (solo se ejecuta una vez).
- En `98_verificar.sql` todas las columnas `OK` deben decir `SI`, y cada línea de la prueba 8 (PL/SQL) debe terminar en `SI`. Las dos pruebas marcadas "DEBE FALLAR" deben mostrar `ORA-02290`: eso es correcto.
- También sirve SQL Developer (host `localhost`, puerto `1521`, servicio `XEPDB1`, ejecutar cada archivo con F5).
- En los scripts, un comentario nunca va después del `;` en la misma línea: SQL*Plus lo envía como parte de la sentencia. `SqlScriptStyleTest` lo verifica.

`database/99_eliminar_esquema.sql` borra todas las tablas. **Solo se ejecuta con autorización explícita del líder**; después hay que repetir desde `01`.

### 2. Backend

1. En IntelliJ IDEA: *File → Open* → seleccionar la carpeta `backend/` (se abre como proyecto Maven y descarga las dependencias).
2. Copiar `backend/db.properties.example` como `backend/db.properties` y escribir la contraseña.
3. Ejecutar la clase `co.edu.unicesar.rutavida.DatabaseCheck`. Resultado esperado:
   ```
   [OK] Conexión establecida: ...
   Tablas de RutaVida encontradas: 23 de 23
   [OK] El esquema está completo.
   ```
4. Ejecutar las pruebas: clic derecho sobre `src/test/java` → *Run 'All Tests'* (o `mvn test`).

## Trabajo en equipo

- **Cómo trabajar con Git, ramas y Pull Requests:** [`CONTRIBUTING.md`](CONTRIBUTING.md).
- **Tareas de cada integrante:** [`docs/plan-de-trabajo.md`](docs/plan-de-trabajo.md).

## Convenciones

- **Idioma:** documentación y Pull Requests en español. **Ramas y commits en inglés.** Código fuente (clases, métodos, variables, comentarios) en inglés. Textos que ve el usuario, en español.
- **Casos de uso:** [`docs/analysis.md`](docs/analysis.md). **Diagrama de clases (Mermaid):** [`docs/class-diagram.md`](docs/class-diagram.md). `ClassDiagramSyncTest` falla si una clase del código no aparece en el diagrama.
- **Arquitectura, paquetes, GRASP y SOLID:** ver [`docs/arquitectura-software.md`](docs/arquitectura-software.md). `ArchitectureTest` falla si una capa importa algo que no le corresponde.
- **Base de datos:** ver [`docs/convenciones-base-datos.md`](docs/convenciones-base-datos.md) (estados, catálogos y cambios al diccionario de datos).
- **Ramas (en inglés):** `main` (estable, rama por defecto), `develop` (integración), `feature/<name>`, `fix/<name>`, `docs/<name>`, `test/<name>`, `refactor/<name>`, `chore/<name>`.
- **Commits:** `type: short-description` en inglés, por ejemplo `feature: dao-emergencies`. Tipos: `feature`, `fix`, `docs`, `test`, `refactor`, `chore`.
- Nadie hace commit ni push directo a `main` ni a `develop`: todo entra por Pull Request hacia `develop`, revisado por otra persona.
- **Las ramas no se eliminan**, ni después del merge. Detalles en [`CONTRIBUTING.md`](CONTRIBUTING.md).
- Las credenciales nunca se suben al repositorio.
