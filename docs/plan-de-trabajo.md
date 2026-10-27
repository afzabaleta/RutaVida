# Plan de trabajo — Sprint 1

**Responsable de la coordinación:** Andrés (líder).
**Fecha de inicio:** 14 de octubre de 2026.
**Objetivo del sprint:** **inicio de sesión funcionando de punta a punta** (CU-01) y **motor de rutas probado** con un grafo de prueba, mientras se preparan los datos reales y el diseño de pantallas.

Cada tarea tiene su rama, sus archivos, de qué depende y **cómo se comprueba que está terminada**. Una tarea solo está terminada cuando su Pull Request fue revisado y entró a `develop`. Que el código exista no basta: tiene que compilar, pasar sus pruebas y estar reflejado en el diagrama de clases.

Las clases y firmas de cada tarea están definidas en `docs/class-diagram.md`; los casos de uso en `docs/analysis.md`. Nadie inventa firmas públicas distintas: si hace falta cambiar una, se acuerda en el grupo y se actualiza el diagrama en el mismo PR.

Las reglas de Git (ramas y commits en inglés, PR hacia `develop`, las ramas no se eliminan) están en `CONTRIBUTING.md`.

---

## Orden de las fases

El orden define **dependencias y entregas**, no obliga a nadie a esperar sin hacer nada. Por ejemplo, Kendrick puede avanzar el grafo en memoria mientras Andrés hace los DAO, y Diego puede diseñar pantallas mientras se confirma la tecnología de la vista. Lo que no se hace es integrar código que dependa de una entidad, firma o decisión que todavía no está acordada.

| Fase | Responsable | Entrega para continuar | Tareas de este sprint |
|---|---|---|---|
| 1. Preparación técnica | Andrés | Estado verificado, modelo Oracle y arquitectura identificados, inconsistencias documentadas, contratos de las entidades acordados | A0 |
| 2. Modelo de dominio | Sherly | Entidades necesarias para la persistencia definidas, compilando y con pruebas | S1 |
| 3. Persistencia Oracle | Andrés | DAO prioritarios definidos y probados contra Oracle | A1, A2 |
| 4. Grafo, tráfico y simulación | Kendrick | Algoritmos y simulación probados con escenarios conocidos | K1, K2 (en paralelo desde el inicio) |
| 5. Servicios de negocio | Kendrick y Sherly | Casos de uso del núcleo con servicios, validaciones y pruebas | S2 |
| 6. Controladores y utilidades | Sherly | Controladores conectados a los servicios, errores en español | S3 |
| 7. Vista web | Diego | Pantallas web conectadas a la API REST | D1, D2 (preparación), D3 (maqueta) |
| 8. Integración | Andrés (todo el equipo) | `AppContext` conecta todo y el flujo completo funciona | A3 |

---

## Fase 1. Andrés — preparación técnica

| # | Tarea | Estado |
|---|---|---|
| A0.1 | Esquema Oracle verificado con `98_verificar.sql`: 23 tablas, 21 secuencias, 24 índices, 0 objetos inválidos, datos iniciales y reglas | Probado (14/10/2026) |
| A0.2 | Conexión JDBC verificada con `DatabaseCheck` ("23 de 23") | Probado (14/10/2026) |
| A0.3 | Infraestructura (Maven, enums, transacciones, eventos, `AppContext`) y pruebas de arquitectura: 37 pruebas en verde | Probado (14/10/2026) |
| A0.4 | Inconsistencias documentadas en `docs/convenciones-base-datos.md` y en el documento consolidado v2 | Hecho |
| A0.5 | Convenciones de Git alineadas (`CONTRIBUTING.md`, `README.md`, este plan) | Hecho: PR #1 fusionado (15/10/2026) |
| A0.6 | Configurar GitHub: colaboradores, `main` como rama por defecto, borrado automático de ramas **desactivado**, protección de `main` y `develop` (ver `CONTRIBUTING.md`, sección 6) | Hecho (15/10/2026) |
| A0.7 | Acordar con Sherly los contratos de las entidades de S1 (`docs/contratos-entidades.md`) | Hecho: PR #3 aprobado por Sherly y fusionado (15/10/2026) |
| A0.8 | Decisiones del equipo: vista web, Oracle local, tablas en español (`docs/decisiones-tecnicas.md`) | Hecho (15/10/2026) |

**Terminado cuando:** A0.5 está en `develop` por PR revisado, A0.6 está configurado y Sherly aprueba los contratos de A0.7.

**Fase 1 terminada el 15/10/2026.** Los PR #1, #2 y #3 están fusionados en `develop`.

---

## Fase 2. Sherly — modelo de dominio

| # | Tarea | Rama | Depende de |
|---|---|---|---|
| S1 | Entidades del modelo: `GeoPoint`, `Locatable`, `User`, `Role`, `Permission`, `UserSession`, `Emergency`, `EmergencyType`, `EmergencyVehicle`, `VehicleType`, `EmergencyStation`, `Hospital`, y la excepción `AccessDeniedException` (la usa `UserSession`). Siguen `docs/contratos-entidades.md` | `feature/base-model` | A0.7 |

**Terminado cuando:**

- Las entidades validan sus datos en el constructor (por ejemplo, `GeoPoint` rechaza una latitud fuera de -90 a 90) y tienen pruebas JUnit.
- No dependen de JDBC, DAO, service, controller ni vista (`ArchitectureTest` en verde).
- Están en `docs/class-diagram.md` (`ClassDiagramSyncTest` en verde) y no duplican los enums que ya existen en `model.enums`.

**Prioridad:** S1 primero y rápido, porque A2 depende de las entidades.

---

## Fase 3. Andrés — persistencia Oracle

| # | Tarea | Rama | Depende de |
|---|---|---|---|
| A1 | Base común de los DAO: `RowMapper` y `AbstractJdbcDAO` (`AccessDeniedException` pasó a S1 por acuerdo del contrato) | `feature/dao-base` | — (puede empezar ya) |
| A2 | `UserDAO`, `RoleDAO`, `AuditDAO` (interfaces en `dao` + `JdbcXxxDAO` en `dao.jdbc`) con pruebas de integración contra Oracle. Se entrega en **dos PR**: primero las interfaces y la entidad `AuditLog` (`feature/security-dao-interfaces`), para que Sherly avance S2 sin esperar; después las implementaciones JDBC (`feature/security-dao`) | `feature/security-dao-interfaces`, `feature/security-dao` | A1, S1 |

**Terminado cuando:** todas las pruebas pasan. Las pruebas de los DAO insertan, leen y actualizan en el Oracle local y **se saltan solas** si no existe `db.properties`, para que no fallen en otros equipos. Los DAO usan consultas parametrizadas y no hacen `commit` ni `close` de la conexión del `TransactionManager`.

---

## Fase 4. Kendrick — motor de rutas y tráfico (en paralelo)

| # | Tarea | Rama | Depende de |
|---|---|---|---|
| K1 | Paquete `graph`: `Node`, `Edge`, `EdgeState`, `VehicleProfile`, `RoadGraph`, `CostFunction`, `EmergencyCost`, `DistanceOnlyCost`, `RouteResult`, `AStarRouter`, `AlternativeRouteFinder` | `feature/route-engine` | — |
| K2 | Paquete `traffic`: `TrafficProvider`, `EdgeUpdate`, `SimulatedTrafficProvider` con semilla fija (resultados repetibles) | `feature/simulated-traffic` | K1 |
| K3 | `RouteOption` (dto) y `RouteExplanationBuilder` (service): datos y explicación en español de cada ruta (CU-08, paso 5) | `feature/route-explanation` | K1 |
| K4 | Red vial real de la zona de la demostración: consulta de Overpass, `OsmImporter` (paquete `osm`) y `database/05_red_vial_valledupar.sql` | `feature/osm-road-network` | Zona acordada (21/10/2026) |

El documento consolidado (Parte IV) tiene código de referencia de K1. Hay que adaptarlo a los cambios de `docs/class-diagram.md`, sección 6: `dbId` en `Node` y `Edge`, `incidentFactor` en `EdgeState`, `EdgeUpdate` sin `closed` y el nombre `AlternativeRouteFinder`.

**Terminado cuando:** hay pruebas JUnit con un grafo de 6 nodos hecho a mano, sin Oracle, que demuestran que:

1. A* y Dijkstra (`useHeuristic = false`) dan el mismo costo.
2. Un tramo cerrado se evita.
3. Con congestión alta, la ruta más rápida no es la más corta (`EmergencyCost` contra `DistanceOnlyCost`).
4. Las alternativas cumplen la regla RN-08 (máximo 80 % de tramos en común y máximo 1,5 veces el tiempo de la mejor).
5. `SimulatedTrafficProvider` con la misma semilla produce los mismos cambios, nunca cierra tramos y marca sus datos como `SIMULACION`.

Además, una prueba de rendimiento que mida en milisegundos el tiempo de A* contra Dijkstra y la cantidad de nodos explorados por cada uno (métrica para la sustentación).

---

## Fase 5. Sherly — servicios de usuarios y autenticación

| # | Tarea | Rama | Depende de |
|---|---|---|---|
| S2 | `PasswordHasher` + `Pbkdf2PasswordHasher` (PBKDF2 con sal, viene en Java), `AuditService` y `AuthService` | `feature/auth-service` | S1; usa las interfaces DAO de A2 |

**Terminado cuando:** `AuthService` tiene pruebas **con DAOs falsos**, sin Oracle, que cubren: login correcto, contraseña incorrecta, usuario inexistente (el mismo mensaje en ambos casos), usuario inactivo y auditoría registrada en cada caso. El hash de la misma contraseña da resultados distintos (por la sal) y `matches` funciona. Los servicios no importan `dao.jdbc`.

Mientras Andrés hace A2, Sherly puede avanzar S2 usando las interfaces DAO del diagrama.

Los servicios de Kendrick (`RouteService`, `DispatchService`, `TrackingService`…) son del Sprint 2.

---

## Fase 6. Sherly — controlador de inicio de sesión

| # | Tarea | Rama | Depende de |
|---|---|---|---|
| S3 | `SessionContext`, `OperationResult`, `LoginController` y `Messages` (textos en español) | `feature/login-controller` | S2 |

**Terminado cuando:** `LoginController` delega en `AuthService`, no tiene SQL y convierte los errores en mensajes en español sin mostrar excepciones técnicas. Tiene pruebas con un `AuthService` falso.

---

## Fase 7. Diego — vista y datos reales (preparación)

La vista es **web** (HTML, CSS, JavaScript y Leaflet), decisión DT-01 en `docs/decisiones-tecnicas.md`. Mientras se programan los servicios y la API, Diego prepara el diseño y los datos:

| # | Tarea | Rama | Depende de |
|---|---|---|---|
| D1 | Mockups de 5 pantallas: inicio de sesión, pantalla principal (mapa + tarjetas de indicadores + panel de emergencias), registrar emergencia, despacho con las 3 rutas y su explicación, y control de la simulación. Interfaz oscura con detalles rojos y azules; logo arriba a la izquierda, usuario y rol arriba a la derecha | `docs/mockups` | — |
| D2 | Datos reales de Valledupar: hospitales y clínicas con urgencias, estaciones de bomberos y bases de ambulancias, con dirección, coordenadas y **fuente de cada dato**. Entregarlos como `database/03_datos_valledupar.sql` | `feature/valledupar-data` | — |
| D3 | Maqueta web en `frontend/`: inicio de sesión y pantalla principal con el mapa de Valledupar (Leaflet), los lugares reales de D2 y datos marcados como ejemplo | `feature/web-mockup` | D1, D2 |
| D4 | Rutas del motor en el mapa: tres escenarios de demostración, alternativas, distancias, tiempos estimados, explicaciones y congestión simulada | `feature/route-map-layer` | D3, A-Demo |

**Terminado cuando:**

- D1: las imágenes de los mockups están en `docs/mockups/` y aparecen en `docs/mockups.md`, con una línea por pantalla que indique qué caso de uso cubre.
- D2: el script se ejecuta sin errores en Oracle y `98_verificar.sql` sigue saliendo bien. Cada fila tiene un comentario con su fuente. Ningún dato inventado: si no se encuentra la coordenada de un lugar, ese lugar no se incluye. Andrés revisa este PR porque toca `database/`.

*Diego prepara la maqueta web con datos de ejemplo (claramente marcados como ejemplo) en `frontend/`, en la rama `feature/web-mockup`. La conexión con el backend se realizará cuando exista la API REST.*

- D3: la maqueta web permite abrir el inicio de sesión y el dashboard con Leaflet, los lugares documentados en D2 y los datos de ejemplo identificados.
- D4: el dashboard presenta las rutas generadas por RouteDemo en tres escenarios; permite cambiar de escenario, consultar alternativas y abrir una ruta en el mapa. La congestión y la unidad simuladas se identifican como demostrativas. Los cambios se prueban en el navegador antes de integrar el PR.

---

## Fase 8. Andrés — integración del inicio de sesión

| # | Tarea | Rama | Depende de |
|---|---|---|---|
| A3 | Conectar el login en `AppContext` (inyección manual) y la herramienta `CreateAdminUser`, que crea el primer administrador con la contraseña en hash | `feature/login-integration` | A2, S2, S3 |

**Terminado cuando:** el administrador creado con `CreateAdminUser` inicia sesión y queda un registro `LOGIN / EXITO` en `AUDITORIA`.

---

## Calendario sugerido

| Días | Andrés | Sherly | Kendrick | Diego |
|---|---|---|---|---|
| 1 | Cierre de la Fase 1 (A0.5–A0.8) | Acordar contratos con Andrés (A0.7) | K1 (grafo y A*) | D1 |
| 2–3 | A1 | S1 | K1 (alternativas y pruebas) | D1, D2 |
| 4–5 | A2 | S2 | K2 | D2 |
| 6 | A3 + revisión de PRs | S3 + pruebas | Pruebas de rendimiento | D2 |
| Cierre | Demostración interna: login real + motor de rutas en pruebas | | | |

**Reunión corta cada 2 días** (10 minutos): qué terminé, qué sigue, qué me bloquea.

---

## Decisiones

**Tomadas por el equipo (15/10/2026)**, detalle en `docs/decisiones-tecnicas.md`:

- DT-01: vista **web** con Leaflet y API REST en Java.
- DT-02: **Oracle local** durante el desarrollo; Oracle Cloud se evalúa antes de la entrega final.
- DT-03: tablas y columnas de Oracle **en español**, código Java en inglés.

**Tomada por Andrés y Kendrick (21/10/2026):** zona de la red vial de la demostración: sur 10.435, oeste -73.280, norte 10.480, este -73.235 (unos 5 × 4,9 km). Incluye la estación de bomberos y los dos hospitales de `03_datos_valledupar.sql` y el Parque La Popa (lugar de la emergencia de la demostración) con al menos 1 km de margen.

**Pendientes:**

| Decisión | Quién | Afecta a |
|---|---|---|
| Servidor HTTP y librería JSON de la API REST | Andrés, Sherly y Diego | Fases 6 y 7 |
| Tiempo real en el navegador (consultas periódicas o *Server-Sent Events*) | Andrés y Diego | Fase 7 |

---

## Sprint 2 (próximo)

Red vial real (OpenStreetMap → `NODO_VIAL` / `TRAMO_VIAL` → `GraphLoader`), registrar emergencia (CU-06), calcular rutas sobre el mapa real (CU-08) y despachar unidad (CU-09), con los servicios de Kendrick (`RouteService`, `DispatchService`).
