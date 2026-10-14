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
| Esquema de base de datos (23 tablas, secuencias, índices, restricciones) | Ejecutado y verificado en Oracle XE 18c (14/10/2026) |
| Datos maestros (roles, permisos, tipos de vehículo y de emergencia) | Cargados y verificados |
| Conexión JDBC (`DatabaseConnection`) y verificación (`DatabaseCheck`) | Funciona: "23 de 23" con el driver ojdbc11 21.9 |
| Arquitectura: paquetes, reglas de dependencia, enums, transacciones, eventos, `AppContext` | Programado y probado ([`docs/arquitectura-software.md`](docs/arquitectura-software.md)) |
| Triggers, vistas y procedimientos PL/SQL | Pendiente |
| Entidades, DAO, Service, Controller, vista | Pendiente |
| Vista: Swing o web | **Pendiente de confirmar con el profesor** |

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
@98_verificar.sql
SPOOL OFF
exit
```

- `00_crear_usuario.sql` pide la contraseña del nuevo usuario `RUTAVIDA` (solo se ejecuta una vez).
- En `98_verificar.sql` todas las columnas `OK` deben decir `SI`. Las dos pruebas marcadas "DEBE FALLAR" deben mostrar `ORA-02290`: eso es correcto.
- También sirve SQL Developer (host `localhost`, puerto `1521`, servicio `XEPDB1`, ejecutar cada archivo con F5).
- En los scripts, un comentario nunca va después del `;` en la misma línea: SQL*Plus lo envía como parte de la sentencia. `SqlScriptStyleTest` lo verifica.

Para borrar todo y empezar de cero: `database/99_eliminar_esquema.sql` y luego repetir desde `01`.

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

## Convenciones

- **Idioma:** documentación, commits, ramas y Pull Requests en español. Código fuente (clases, métodos, variables, comentarios) en inglés. Textos que ve el usuario, en español.
- **Casos de uso:** [`docs/analysis.md`](docs/analysis.md). **Diagrama de clases (Mermaid):** [`docs/class-diagram.md`](docs/class-diagram.md). `ClassDiagramSyncTest` falla si una clase del código no aparece en el diagrama.
- **Arquitectura, paquetes, GRASP y SOLID:** ver [`docs/arquitectura-software.md`](docs/arquitectura-software.md). `ArchitectureTest` falla si una capa importa algo que no le corresponde.
- **Base de datos:** ver [`docs/convenciones-base-datos.md`](docs/convenciones-base-datos.md) (estados, catálogos y cambios al diccionario de datos).
- **Ramas:** `main` (estable), `develop` (integración), `funcionalidad/<nombre>`, `correccion/<nombre>`, `documentacion/<nombre>`.
- **Commits:** `tipo: mensaje corto`, por ejemplo `funcionalidad: agrega DAO de emergencias`. Tipos: `funcionalidad`, `correccion`, `documentacion`, `refactorizacion`, `pruebas`, `estructura`.
- Nadie hace push directo a `main` ni a `develop`: todo entra por Pull Request revisado por otra persona.
- Las credenciales nunca se suben al repositorio.
