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
| Esquema de base de datos (23 tablas, secuencias, índices, restricciones) | Script escrito; **pendiente de ejecutar** en Oracle XE |
| Datos maestros (roles, permisos, tipos de vehículo y de emergencia) | Script escrito |
| Conexión JDBC (`DatabaseConnection`) y verificación (`DatabaseCheck`) | Programado |
| Arquitectura: paquetes, reglas de dependencia, enums, transacciones, eventos, `AppContext` | Programado y probado ([`docs/arquitectura-software.md`](docs/arquitectura-software.md)) |
| Triggers, vistas y procedimientos PL/SQL | Pendiente |
| Entidades, DAO, Service, Controller, vista | Pendiente |
| Vista: Swing o web | **Pendiente de confirmar con el profesor** |

## Puesta en marcha (cada integrante, una sola vez)

### 1. Base de datos local

Requisito: Oracle Database XE instalado y en ejecución, con la PDB `XEPDB1` (puerto 1521).

1. Conectarse como `SYSTEM` al servicio `XEPDB1` (en SQL Developer: host `localhost`, puerto `1521`, *Nombre del servicio* `XEPDB1`).
2. Ejecutar `database/00_crear_usuario.sql` (pide la contraseña del usuario `RUTAVIDA`).
3. Crear una conexión nueva con el usuario `RUTAVIDA` en `XEPDB1` y ejecutar, en este orden y como script (F5 en SQL Developer):
   - `database/01_esquema.sql`
   - `database/02_datos_iniciales.sql`
   - `database/98_verificar.sql` → todas las columnas `OK` deben decir `SI`. Las pruebas marcadas "DEBE FALLAR" deben mostrar el error `ORA-02290`.

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
- **Arquitectura, paquetes, GRASP y SOLID:** ver [`docs/arquitectura-software.md`](docs/arquitectura-software.md). `ArchitectureTest` falla si una capa importa algo que no le corresponde.
- **Base de datos:** ver [`docs/convenciones-base-datos.md`](docs/convenciones-base-datos.md) (estados, catálogos y cambios al diccionario de datos).
- **Ramas:** `main` (estable), `develop` (integración), `funcionalidad/<nombre>`, `correccion/<nombre>`, `documentacion/<nombre>`.
- **Commits:** `tipo: mensaje corto`, por ejemplo `funcionalidad: agrega DAO de emergencias`. Tipos: `funcionalidad`, `correccion`, `documentacion`, `refactorizacion`, `pruebas`, `estructura`.
- Nadie hace push directo a `main` ni a `develop`: todo entra por Pull Request revisado por otra persona.
- Las credenciales nunca se suben al repositorio.
