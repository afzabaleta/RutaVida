# Plan de trabajo â€” Sprint 1

Responsable de la coordinaciÃ³n: AndrÃ©s (lÃ­der).
Fecha de inicio: 14 de octubre de 2026.
Objetivo del sprint: **iniciar sesiÃ³n funcionando de punta a punta** (CU-01) y **motor de rutas probado** con un grafo de prueba, mientras se preparan los datos reales y el diseÃ±o de pantallas.

Cada tarea tiene su rama, sus archivos, de quÃ© depende y **cÃ³mo se comprueba que estÃ¡ terminada**. Una tarea solo estÃ¡ terminada cuando su Pull Request fue revisado y entrÃ³ a `develop`. Que el cÃ³digo exista no basta: tiene que compilar, pasar sus pruebas y estar reflejado en el diagrama de clases.

Las clases y firmas de cada tarea estÃ¡n definidas en `docs/class-diagram.md`; los casos de uso en `docs/analysis.md`. Nadie inventa firmas pÃºblicas distintas: si hace falta cambiar una, se acuerda en el grupo y se actualiza el diagrama en el mismo PR.

Las reglas de Git (ramas y commits en inglÃ©s, PR hacia `develop`, las ramas no se eliminan) estÃ¡n en `CONTRIBUTING.md`.

---

## Orden de las fases

El orden define **dependencias y entregas**, no obliga a nadie a esperar sin hacer nada. Por ejemplo, Kendrick puede avanzar el grafo en memoria mientras AndrÃ©s hace los DAO, y Diego puede diseÃ±ar pantallas mientras se confirma la tecnologÃ­a de la vista. Lo que no se hace es integrar cÃ³digo que dependa de una entidad, firma o decisiÃ³n que todavÃ­a no estÃ¡ acordada.

| Fase | Responsable | Entrega para continuar | Tareas de este sprint |
|---|---|---|---|
| 1. PreparaciÃ³n tÃ©cnica | AndrÃ©s | Estado verificado, modelo Oracle y arquitectura identificados, inconsistencias documentadas, contratos de las entidades acordados | A0 |
| 2. Modelo de dominio | Sherly | Entidades necesarias para la persistencia definidas, compilando y con pruebas | S1 |
| 3. Persistencia Oracle | AndrÃ©s | DAO prioritarios definidos y probados contra Oracle | A1, A2 |
| 4. Grafo, trÃ¡fico y simulaciÃ³n | Kendrick | Algoritmos y simulaciÃ³n probados con escenarios conocidos | K1, K2 (en paralelo desde el inicio) |
| 5. Servicios de negocio | Kendrick y Sherly | Casos de uso del nÃºcleo con servicios, validaciones y pruebas | S2 |
| 6. Controladores y utilidades | Sherly | Controladores conectados a los servicios, errores en espaÃ±ol | S3 |
| 7. Vista web | Diego | Pantallas web conectadas a la API REST | D1, D2 (preparaciÃ³n) |
| 8. IntegraciÃ³n | AndrÃ©s (todo el equipo) | `AppContext` conecta todo y el flujo completo funciona | A3 |

---

## Fase 1. AndrÃ©s â€” preparaciÃ³n tÃ©cnica

| # | Tarea | Estado |
|---|---|---|
| A0.1 | Esquema Oracle verificado con `98_verificar.sql`: 23 tablas, 21 secuencias, 24 Ã­ndices, 0 objetos invÃ¡lidos, datos iniciales y reglas | Probado (14/10/2026) |
| A0.2 | ConexiÃ³n JDBC verificada con `DatabaseCheck` ("23 de 23") | Probado (14/10/2026) |
| A0.3 | Infraestructura (Maven, enums, transacciones, eventos, `AppContext`) y pruebas de arquitectura: 37 pruebas en verde | Probado (14/10/2026) |
| A0.4 | Inconsistencias documentadas en `docs/convenciones-base-datos.md` y en el documento consolidado v2 | Hecho |
| A0.5 | Convenciones de Git alineadas (`CONTRIBUTING.md`, `README.md`, este plan) | Hecho: PR #1 fusionado (15/10/2026) |
| A0.6 | Configurar GitHub: colaboradores, `main` como rama por defecto, borrado automÃ¡tico de ramas **desactivado**, protecciÃ³n de `main` y `develop` (ver `CONTRIBUTING.md`, secciÃ³n 6) | Hecho (15/10/2026) |
| A0.7 | Acordar con Sherly los contratos de las entidades de S1 (`docs/contratos-entidades.md`) | Hecho: PR #3 aprobado por Sherly y fusionado (15/10/2026) |
| A0.8 | Decisiones del equipo: vista web, Oracle local, tablas en espaÃ±ol (`docs/decisiones-tecnicas.md`) | Hecho (15/10/2026) |

**Terminado cuando:** A0.5 estÃ¡ en `develop` por PR revisado, A0.6 estÃ¡ configurado y Sherly aprueba los contratos de A0.7.

**Fase 1 terminada el 15/10/2026.** Los PR #1, #2 y #3 estÃ¡n fusionados en `develop`.

---

## Fase 2. Sherly â€” modelo de dominio

| # | Tarea | Rama | Depende de |
|---|---|---|---|
| S1 | Entidades del modelo: `GeoPoint`, `Locatable`, `User`, `Role`, `Permission`, `UserSession`, `Emergency`, `EmergencyType`, `EmergencyVehicle`, `VehicleType`, `EmergencyStation`, `Hospital`, y la excepciÃ³n `AccessDeniedException` (la usa `UserSession`). Siguen `docs/contratos-entidades.md` | `feature/base-model` | A0.7 |

**Terminado cuando:**
- Las entidades validan sus datos en el constructor (por ejemplo, `GeoPoint` rechaza una latitud fuera de -90 a 90) y tienen pruebas JUnit.
- No dependen de JDBC, DAO, service, controller ni vista (`ArchitectureTest` en verde).
- EstÃ¡n en `docs/class-diagram.md` (`ClassDiagramSyncTest` en verde) y no duplican los enums que ya existen en `model.enums`.

**Prioridad:** S1 primero y rÃ¡pido, porque A2 depende de las entidades.

---

## Fase 3. AndrÃ©s â€” persistencia Oracle

| # | Tarea | Rama | Depende de |
|---|---|---|---|
| A1 | Base comÃºn de los DAO: `RowMapper` y `AbstractJdbcDAO` (`AccessDeniedException` pasÃ³ a S1 por acuerdo del contrato) | `feature/dao-base` | â€” (puede empezar ya) |
| A2 | `UserDAO`, `RoleDAO`, `AuditDAO` (interfaces en `dao` + `JdbcXxxDAO` en `dao.jdbc`) con pruebas de integraciÃ³n contra Oracle. Se entrega en **dos PR**: primero las interfaces y la entidad `AuditLog` (`feature/security-dao-interfaces`), para que Sherly avance S2 sin esperar; despuÃ©s las implementaciones JDBC (`feature/security-dao`) | `feature/security-dao-interfaces`, `feature/security-dao` | A1, S1 |

**Terminado cuando:** todas las pruebas pasan. Las pruebas de los DAO insertan, leen y actualizan en el Oracle local y **se saltan solas** si no existe `db.properties`, para que no fallen en otros equipos. Los DAO usan consultas parametrizadas y no hacen `commit` ni `close` de la conexiÃ³n del `TransactionManager`.

---

## Fase 4. Kendrick â€” motor de rutas y trÃ¡fico (en paralelo)

| # | Tarea | Rama | Depende de |
|---|---|---|---|
| K1 | Paquete `graph`: `Node`, `Edge`, `EdgeState`, `VehicleProfile`, `RoadGraph`, `CostFunction`, `EmergencyCost`, `DistanceOnlyCost`, `RouteResult`, `AStarRouter`, `AlternativeRouteFinder` | `feature/route-engine` | â€” |
| K2 | Paquete `traffic`: `TrafficProvider`, `EdgeUpdate`, `SimulatedTrafficProvider` con semilla fija (resultados repetibles) | `feature/simulated-traffic` | K1 |
| K3 | `RouteOption` (dto) y `RouteExplanationBuilder` (service): datos y explicaciÃ³n en espaÃ±ol de cada ruta (CU-08, paso 5) | `feature/route-explanation` | K1 |
| K4 | Red vial real de la zona de la demostraciÃ³n: consulta de Overpass, `OsmImporter` (paquete `osm`) y `database/05_red_vial_valledupar.sql` | `feature/osm-road-network` | Zona acordada (21/10/2026) |

El documento consolidado (Parte IV) tiene cÃ³digo de referencia de K1. Hay que adaptarlo a los cambios de `docs/class-diagram.md`, secciÃ³n 6: `dbId` en `Node` y `Edge`, `incidentFactor` en `EdgeState`, `EdgeUpdate` sin `closed` y el nombre `AlternativeRouteFinder`.

**Terminado cuando:** hay pruebas JUnit con un grafo de 6 nodos hecho a mano, sin Oracle, que demuestran que:
1. A* y Dijkstra (`useHeuristic = false`) dan el mismo costo.
2. Un tramo cerrado se evita.
3. Con congestiÃ³n alta, la ruta mÃ¡s rÃ¡pida no es la mÃ¡s corta (`EmergencyCost` contra `DistanceOnlyCost`).
4. Las alternativas cumplen la regla RN-08 (mÃ¡ximo 80 % de tramos en comÃºn y mÃ¡ximo 1,5 veces el tiempo de la mejor).
5. `SimulatedTrafficProvider` con la misma semilla produce los mismos cambios, nunca cierra tramos y marca sus datos como `SIMULACION`.

AdemÃ¡s, una prueba de rendimiento que mida en milisegundos el tiempo de A* contra Dijkstra y la cantidad de nodos explorados por cada uno (mÃ©trica para la sustentaciÃ³n).

---

## Fase 5. Sherly â€” servicios de usuarios y autenticaciÃ³n

| # | Tarea | Rama | Depende de |
|---|---|---|---|
| S2 | `PasswordHasher` + `Pbkdf2PasswordHasher` (PBKDF2 con sal, viene en Java), `AuditService` y `AuthService` | `feature/auth-service` | S1; usa las interfaces DAO de A2 |

**Terminado cuando:** `AuthService` tiene pruebas **con DAOs falsos**, sin Oracle, que cubren: login correcto, contraseÃ±a incorrecta, usuario inexistente (el mismo mensaje en ambos casos), usuario inactivo y auditorÃ­a registrada en cada caso. El hash de la misma contraseÃ±a da resultados distintos (por la sal) y `matches` funciona. Los servicios no importan `dao.jdbc`.

Mientras AndrÃ©s hace A2, Sherly puede avanzar S2 usando las interfaces DAO del diagrama.

Los servicios de Kendrick (`RouteService`, `DispatchService`, `TrackingService`â€¦) son del Sprint 2.

---

## Fase 6. Sherly â€” controlador de inicio de sesiÃ³n

| # | Tarea | Rama | Depende de |
|---|---|---|---|
| S3 | `SessionContext`, `OperationResult`, `LoginController` y `Messages` (textos en espaÃ±ol) | `feature/login-controller` | S2 |

**Terminado cuando:** `LoginController` delega en `AuthService`, no tiene SQL y convierte los errores en mensajes en espaÃ±ol sin mostrar excepciones tÃ©cnicas. Tiene pruebas con un `AuthService` falso.

---

## Fase 7. Diego â€” vista y datos reales (preparaciÃ³n)

La vista es **web** (HTML, CSS, JavaScript y Leaflet), decisiÃ³n DT-01 en `docs/decisiones-tecnicas.md`. Mientras se programan los servicios y la API, Diego prepara el diseÃ±o y los datos:

| # | Tarea | Rama | Depende de |
|---|---|---|---|
| D1 | Mockups de 5 pantallas: inicio de sesiÃ³n, pantalla principal (mapa + tarjetas de indicadores + panel de emergencias), registrar emergencia, despacho con las 3 rutas y su explicaciÃ³n, y control de la simulaciÃ³n. Interfaz oscura con detalles rojos y azules; logo arriba a la izquierda, usuario y rol arriba a la derecha | `docs/mockups` | â€” |
| D2 | Datos reales de Valledupar: hospitales y clÃ­nicas con urgencias, estaciones de bomberos y bases de ambulancias, con direcciÃ³n, coordenadas y **fuente de cada dato**. Entregarlos como `database/03_datos_valledupar.sql` | `feature/valledupar-data` | â€” |

**Terminado cuando:**
- D1: las imÃ¡genes de los mockups estÃ¡n en `docs/mockups/` y aparecen en `docs/mockups.md`, con una lÃ­nea por pantalla que indique quÃ© caso de uso cubre.
- D2: el script se ejecuta sin errores en Oracle y `98_verificar.sql` sigue saliendo bien. Cada fila tiene un comentario con su fuente. NingÃºn dato inventado: si no se encuentra la coordenada de un lugar, ese lugar no se incluye. AndrÃ©s revisa este PR porque toca `database/`.

Diego puede empezar la maqueta web con datos de ejemplo (claramente marcados como ejemplo) en `frontend/` (`feature/login-view`, `feature/map-view`). La conexiÃ³n con datos reales se hace cuando exista la API REST.

---

## Fase 8. AndrÃ©s â€” integraciÃ³n del inicio de sesiÃ³n

| # | Tarea | Rama | Depende de |
|---|---|---|---|
| A3 | Conectar el login en `AppContext` (inyecciÃ³n manual) y la herramienta `CreateAdminUser`, que crea el primer administrador con la contraseÃ±a en hash | `feature/login-integration` | A2, S2, S3 |

**Terminado cuando:** el administrador creado con `CreateAdminUser` inicia sesiÃ³n y queda un registro `LOGIN / EXITO` en `AUDITORIA`.

---

## Calendario sugerido

| DÃ­as | AndrÃ©s | Sherly | Kendrick | Diego |
|---|---|---|---|---|
| 1 | Cierre de la Fase 1 (A0.5â€“A0.8) | Acordar contratos con AndrÃ©s (A0.7) | K1 (grafo y A*) | D1 |
| 2â€“3 | A1 | S1 | K1 (alternativas y pruebas) | D1, D2 |
| 4â€“5 | A2 | S2 | K2 | D2 |
| 6 | A3 + revisiÃ³n de PRs | S3 + pruebas | Pruebas de rendimiento | D2 |
| Cierre | DemostraciÃ³n interna: login real + motor de rutas en pruebas | | | |

**ReuniÃ³n corta cada 2 dÃ­as** (10 minutos): quÃ© terminÃ©, quÃ© sigue, quÃ© me bloquea.

---

## Decisiones

**Tomadas por el equipo (15/10/2026)**, detalle en `docs/decisiones-tecnicas.md`:
- DT-01: vista **web** con Leaflet y API REST en Java.
- DT-02: **Oracle local** durante el desarrollo; Oracle Cloud se evalÃºa antes de la entrega final.
- DT-03: tablas y columnas de Oracle **en espaÃ±ol**, cÃ³digo Java en inglÃ©s.

**Tomada por AndrÃ©s y Kendrick (21/10/2026):** zona de la red vial de la demostraciÃ³n: sur 10.435, oeste -73.280, norte 10.480, este -73.235 (unos 5 Ã— 4,9 km). Incluye la estaciÃ³n de bomberos y los dos hospitales de `03_datos_valledupar.sql` y el Parque La Popa (lugar de la emergencia de la demostraciÃ³n) con al menos 1 km de margen.

**Pendientes:**

| DecisiÃ³n | QuiÃ©n | Afecta a |
|---|---|---|
| Servidor HTTP y librerÃ­a JSON de la API REST | AndrÃ©s, Sherly y Diego | Fases 6 y 7 |
| Tiempo real en el navegador (consultas periÃ³dicas o *Server-Sent Events*) | AndrÃ©s y Diego | Fase 7 |

---

## Sprint 2 (prÃ³ximo)

Red vial real (OpenStreetMap â†’ `NODO_VIAL` / `TRAMO_VIAL` â†’ `GraphLoader`), registrar emergencia (CU-06), calcular rutas sobre el mapa real (CU-08) y despachar unidad (CU-09), con los servicios de Kendrick (`RouteService`, `DispatchService`).
