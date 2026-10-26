# RutaVida

**Sistema Inteligente de Rutas de Emergencia para Valledupar**

Proyecto de ProgramaciÃ³n de Computadores III + Base de Datos â€” IngenierÃ­a de Sistemas, Universidad Popular del Cesar.

> Prototipo acadÃ©mico. No reemplaza un servicio oficial de despacho y no debe usarse para coordinar emergencias reales.

## Equipo

| Integrante | Responsabilidad |
|---|---|
| AndrÃ©s (lÃ­der) | Base de datos Oracle, DAO e integraciÃ³n |
| Kendrick | Service y motor de rutas |
| Diego | Vista (interfaz) y versiÃ³n mÃ³vil |
| Sherly | Model, Controller, Util y pruebas |

## Estructura del repositorio

```
RutaVida/
â”œâ”€â”€ backend/            Proyecto Java 21 + Maven (paquete co.edu.unicesar.rutavida)
â”‚   â”œâ”€â”€ pom.xml
â”‚   â”œâ”€â”€ db.properties.example   â† plantilla; la copia real (db.properties) no se sube
â”‚   â””â”€â”€ src/
â”œâ”€â”€ database/           Scripts SQL y PL/SQL de Oracle (en orden numÃ©rico)
â””â”€â”€ docs/               DocumentaciÃ³n del proyecto (en espaÃ±ol)
```

## Estado actual

| Componente | Estado |
|---|---|
| Esquema de base de datos (23 tablas, 21 secuencias, 24 Ã­ndices, restricciones) | Ejecutado y verificado en Oracle XE 18c con `98_verificar.sql` (14/10/2026) |
| Datos maestros (roles, permisos, tipos de vehÃ­culo y de emergencia) | Cargados y verificados |
| ConexiÃ³n JDBC (`DatabaseConnection`) y verificaciÃ³n (`DatabaseCheck`) | Funciona: "23 de 23" con el driver ojdbc11 21.9 |
| Arquitectura: paquetes, reglas de dependencia, enums, transacciones, eventos, `AppContext` | Programado y probado: 37 pruebas en verde en IntelliJ ([`docs/arquitectura-software.md`](docs/arquitectura-software.md)) |
| Vistas, funciones, procedimientos y triggers PL/SQL (`04_plsql.sql`) | Programado; se prueba con la secciÃ³n 8 de `98_verificar.sql` |
| Entidades, DAO, Service, Controller, vista | Pendiente |
| Vista | **Decidido: web** (HTML, CSS, JavaScript y Leaflet + API REST en Java). Maqueta en `frontend/` (D3), todavía sin conexión al backend |
| Decisiones del equipo (vista web, Oracle local, tablas en espaÃ±ol) | Registradas en [`docs/decisiones-tecnicas.md`](docs/decisiones-tecnicas.md) |

## Puesta en marcha (cada integrante, una sola vez)

### 1. Base de datos local

Requisito: Oracle Database XE instalado y en ejecuciÃ³n, con la PDB `XEPDB1` (puerto 1521).

Desde la consola de Windows (`cmd`), con SQL*Plus, que viene con Oracle XE:

```
cd ruta\a\RutaVida\database
sqlplus /nolog
```

Dentro de `SQL>`:

```
CONNECT system/CONTRASEÃ‘A_DE_SYSTEM@localhost:1521/XEPDB1
@00_crear_usuario.sql
CONNECT rutavida/CONTRASEÃ‘A_DE_RUTAVIDA@localhost:1521/XEPDB1
SPOOL instalacion.log
@01_esquema.sql
@02_datos_iniciales.sql
@03_datos_valledupar.sql
@04_plsql.sql
@05_red_vial_valledupar.sql
@98_verificar.sql
SPOOL OFF
exit
```

- `00_crear_usuario.sql` pide la contraseÃ±a del nuevo usuario `RUTAVIDA` (solo se ejecuta una vez).
- En `98_verificar.sql` todas las columnas `OK` deben decir `SI`, y cada lÃ­nea de la prueba 8 (PL/SQL) debe terminar en `SI`. Las dos pruebas marcadas "DEBE FALLAR" deben mostrar `ORA-02290`: eso es correcto.
- TambiÃ©n sirve SQL Developer (host `localhost`, puerto `1521`, servicio `XEPDB1`, ejecutar cada archivo con F5).
- En los scripts, un comentario nunca va despuÃ©s del `;` en la misma lÃ­nea: SQL*Plus lo envÃ­a como parte de la sentencia. `SqlScriptStyleTest` lo verifica.

`database/99_eliminar_esquema.sql` borra todas las tablas. **Solo se ejecuta con autorizaciÃ³n explÃ­cita del lÃ­der**; despuÃ©s hay que repetir desde `01`.

### 2. Backend

1. En IntelliJ IDEA: *File â†’ Open* â†’ seleccionar la carpeta `backend/` (se abre como proyecto Maven y descarga las dependencias).
2. Copiar `backend/db.properties.example` como `backend/db.properties` y escribir la contraseÃ±a.
3. Ejecutar la clase `co.edu.unicesar.rutavida.DatabaseCheck`. Resultado esperado:
   ```
   [OK] ConexiÃ³n establecida: ...
   Tablas de RutaVida encontradas: 23 de 23
   [OK] El esquema estÃ¡ completo.
   ```
4. Ejecutar las pruebas: clic derecho sobre `src/test/java` â†’ *Run 'All Tests'* (o `mvn test`).

5. Crear el primer administrador (una sola vez por base de datos): ejecutar `co.edu.unicesar.rutavida.app.CreateAdminUser` y responder lo que pregunta. La contraseÃ±a queda guardada como hash PBKDF2, y al final la herramienta comprueba que el administrador puede iniciar sesiÃ³n.

### 3. Red vial (OpenStreetMap)

`database/05_red_vial_valledupar.sql` carga `NODO_VIAL` y `TRAMO_VIAL` con las calles de la zona de la demostraciÃ³n. Lo genera la herramienta `co.edu.unicesar.rutavida.app.OsmImporter` y no se edita a mano. Para volver a generarlo:

1. Descargar los datos con la consulta guardada (desde la carpeta `RutaVida`, en PowerShell):
   ```
   curl.exe -o database\osm\valledupar_demo.osm --data-urlencode "data@database\osm\valledupar_demo.overpassql" https://overpass-api.de/api/interpreter
   ```
2. Ejecutar `OsmImporter` con los argumentos `../database/osm/valledupar_demo.osm ../database/05_red_vial_valledupar.sql AAAA-MM-DD` (fecha de la descarga).

El archivo `.osm` no se sube al repositorio (`.gitignore`). Datos de la red vial: Â© OpenStreetMap contributors, licencia [ODbL](https://www.openstreetmap.org/copyright).

## Trabajo en equipo

- **CÃ³mo trabajar con Git, ramas y Pull Requests:** [`CONTRIBUTING.md`](CONTRIBUTING.md).
- **Tareas de cada integrante:** [`docs/plan-de-trabajo.md`](docs/plan-de-trabajo.md).

## Convenciones

- **Idioma:** documentaciÃ³n y Pull Requests en espaÃ±ol. **Ramas y commits en inglÃ©s.** CÃ³digo fuente (clases, mÃ©todos, variables, comentarios) en inglÃ©s. Textos que ve el usuario, en espaÃ±ol.
- **Casos de uso:** [`docs/analysis.md`](docs/analysis.md). **Diagrama de clases (Mermaid):** [`docs/class-diagram.md`](docs/class-diagram.md). `ClassDiagramSyncTest` falla si una clase del cÃ³digo no aparece en el diagrama.
- **Arquitectura, paquetes, GRASP y SOLID:** ver [`docs/arquitectura-software.md`](docs/arquitectura-software.md). `ArchitectureTest` falla si una capa importa algo que no le corresponde.
- **Base de datos:** ver [`docs/convenciones-base-datos.md`](docs/convenciones-base-datos.md) (estados, catÃ¡logos y cambios al diccionario de datos).
- **Ramas (en inglÃ©s):** `main` (estable, rama por defecto), `develop` (integraciÃ³n), `feature/<name>`, `fix/<name>`, `docs/<name>`, `test/<name>`, `refactor/<name>`, `chore/<name>`.
- **Commits:** `type: short-description` en inglÃ©s, por ejemplo `feature: dao-emergencies`. Tipos: `feature`, `fix`, `docs`, `test`, `refactor`, `chore`.
- Nadie hace commit ni push directo a `main` ni a `develop`: todo entra por Pull Request hacia `develop`, revisado por otra persona.
- **Las ramas no se eliminan**, ni despuÃ©s del merge. Detalles en [`CONTRIBUTING.md`](CONTRIBUTING.md).
- Las credenciales nunca se suben al repositorio.
