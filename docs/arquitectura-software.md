# Arquitectura de software — RutaVida (backend Java)

Responsable: Andrés (líder, DAO e integración).
Fecha: 14 de octubre de 2026.
Estado: **estructura de paquetes e infraestructura programadas y probadas**; las clases de negocio están pendientes.

Paquete raíz: `co.edu.unicesar.rutavida` · Java 21 · Maven · Oracle XE local (`XEPDB1`).

---

## 1. Idea general

RutaVida usa una **arquitectura en capas** con tres decisiones que la hacen defendible:

1. **Las capas superiores dependen de interfaces, no de implementaciones.** Un servicio conoce `EmergencyDAO`, no `JdbcEmergencyDAO`. La única clase que conecta interfaces con implementaciones es `app.AppContext` (*composition root*, inyección de dependencias manual, sin framework).
2. **El tiempo real se resuelve con eventos.** El simulador y los servicios publican eventos (`TrafficChanged`, `RouteRecalculated`…) y quien los necesite se suscribe. Nadie llama directamente a la vista.
3. **Las reglas se verifican con pruebas.** `ArchitectureTest` falla si alguien rompe una regla de dependencia, y `EnumSqlContractTest` falla si los estados de Java y los CHECK de Oracle dejan de coincidir.

```mermaid
flowchart TB
    VIEW["view → api REST<br/>(vista web con Leaflet)"]
    CTRL["controller<br/>Controladores de casos de uso"]
    SRV["service<br/>Reglas de negocio"]
    ENG["graph · traffic · simulation<br/>Motor de rutas y simulación"]
    DAOI["dao<br/>Interfaces + TransactionManager"]
    JDBC["dao.jdbc<br/>Implementaciones Oracle (SQL)"]
    DB[("Oracle XE<br/>XEPDB1")]
    MODEL["model · model.enums · dto"]
    EVT["event<br/>EventBus"]
    APP["app<br/>AppContext (composition root)"]

    VIEW --> CTRL --> SRV
    SRV --> ENG
    SRV --> DAOI
    JDBC -. implementa .-> DAOI
    JDBC --> DB
    SRV -- publica --> EVT
    EVT -- notifica --> VIEW
    APP -. crea y conecta .-> JDBC
    APP -. crea y conecta .-> SRV
    APP -. crea y conecta .-> CTRL
    MODEL -. usado por todas las capas .- SRV
```

---

## 2. Paquetes

| Paquete | Capa | Responsabilidad | Dueño |
|---|---|---|---|
| `app` | Arranque | `AppContext`: crea los objetos y les inyecta sus dependencias. Puntos de entrada | Andrés |
| `model` | Model | Entidades, una por tabla, con nombres en inglés (`Emergency`, `RoadSegment`…) | Sherly |
| `model.enums` | Model | Estados y catálogos. Cada constante en inglés guarda su código en español de Oracle | Sherly (códigos: Andrés) |
| `dto` | Model | Objetos de transferencia (records) entre el controlador y la vista: opción de ruta con explicación, resumen del dashboard | Sherly |
| `dao` | DAO | **Interfaces** de persistencia (una por agregado) y `TransactionManager`. Sin tipos JDBC | Andrés |
| `dao.jdbc` | DAO | Implementaciones con JDBC y SQL de Oracle, transacciones, mapeo fila → objeto | Andrés |
| `service` | Service | Reglas de negocio: validar, aplicar reglas, coordinar DAOs en una transacción y publicar eventos | Kendrick (rutas, despacho, seguimiento) · Sherly (usuarios, hospitales, alertas, auditoría) |
| `graph` | Service | Motor de rutas en memoria: grafo, función de costo, A*/Dijkstra, rutas alternativas | Kendrick |
| `traffic` | Service | Fuentes de tráfico detrás de `TrafficProvider`: simulador (fase 1) y fuente externa (fase 2) | Kendrick |
| `simulation` | Service | Ciclo de tiempo real, movimiento simulado de unidades y escenarios de demostración | Kendrick |
| `event` | Transversal | Publicación y suscripción de eventos para el tiempo real | Andrés |
| `controller` | Controller | Controladores de casos de uso: reciben la acción del usuario y delegan en los servicios | Sherly |
| `view` | View | Interfaz de usuario. **Decidido: web** (DT-01); en el backend este paquete pasará a ser `api` (adaptadores REST) y la página web irá en `frontend/` | Diego (página web), Andrés y Sherly (`api`) |
| `util` | Util | Configuración de la conexión, hash de contraseñas, validaciones y mensajes en español | Sherly (`DatabaseConnection`: Andrés) |
| `exception` | Transversal | Jerarquía de excepciones compartida | Andrés |

Cada paquete tiene un `package-info.java` con su responsabilidad, sus dependencias permitidas y su dueño. En IntelliJ se ven con *Ctrl+Q* sobre el nombre del paquete.

---

## 3. Reglas de dependencia (verificadas por `ArchitectureTest`)

| Paquete | **No** puede importar |
|---|---|
| `model`, `model.enums` | `java.sql`, Swing/AWT, `dao`, `service`, `controller`, `view`, `graph`, `traffic`, `simulation`, `event`, `app` |
| `dao` (interfaces) | `java.sql`, `javax.sql`, `oracle.*`, `dao.jdbc`, `service`, `controller`, `view` |
| `dao.jdbc` | Swing/AWT, `service`, `controller`, `view`, `simulation`, `app` |
| `service` | `java.sql`, `javax.sql`, `oracle.*`, Swing/AWT, `dao.jdbc`, `controller`, `view`, `app` |
| `graph`, `traffic` | JDBC, Swing/AWT, `dao`, `service`, `controller`, `view`, `simulation`, `app` |
| `event` | JDBC, Swing/AWT, `dao`, `service`, `controller`, `view`, `app` |
| `controller` | JDBC, Swing/AWT, `dao`, `view`, `app` |
| `view` | JDBC, `dao`, `service`, `graph`, `traffic`, `simulation` |
| Todos, salvo `dao.jdbc` y `app` | `dao.jdbc` |

En resumen: **la vista solo habla con controladores, los controladores solo con servicios, y solo los DAO hablan con Oracle.**

---

## 4. Cómo fluye un caso de uso: despachar una unidad

```mermaid
sequenceDiagram
    actor Op as Coordinador
    participant V as view
    participant C as DispatchController
    participant S as DispatchService
    participant T as TransactionManager
    participant D as DAOs (interfaces)
    participant E as EventBus

    Op->>V: Selecciona unidad y "Despachar"
    V->>C: dispatch(emergencyId, vehicleId)
    C->>S: dispatch(emergencyId, vehicleId, userId)
    S->>D: buscar emergencia y unidad
    S->>S: validar reglas (unidad DISPONIBLE, emergencia REPORTADA)
    S->>T: runInTransaction(...)
    T->>D: insertar ASIGNACION_EMERGENCIA
    T->>D: EMERGENCIA → ASIGNADA
    T->>D: VEHICULO_EMERGENCIA → OCUPADO
    T->>D: insertar AUDITORIA
    T-->>S: commit (o rollback si algo falla)
    S->>E: publish(UnitDispatched)
    E-->>V: actualizar mapa y paneles
    S-->>C: asignación creada
    C-->>V: resultado / mensaje en español
```

- **Transacción:** las cuatro escrituras se guardan juntas o no se guarda ninguna. Los DAOs no hacen `commit` ni `close`; de eso se encarga `JdbcTransactionManager`.
- **Errores:** si una regla no se cumple, el servicio lanza `BusinessRuleException`; si falla Oracle, el DAO lanza `DataAccessException`. El controlador convierte ambas en un mensaje en español para el usuario.

## 5. Cómo fluye el tiempo real

```
simulation (cada tick) ──► TrafficProvider.poll() ──► actualiza el grafo en memoria
        │
        └──► publica TrafficChanged ──► RouteMonitor recalcula las rutas ACTIVAS afectadas
                                              │
                                              └──► si mejora más del umbral: publica RouteRecalculated
                                                         │
                                                         └──► view redibuja la ruta
```

- El historial (`TRAFICO`, `UBICACION_VEHICULO`) se guarda **por lotes**, no en cada tick.
- `InMemoryEventBus` entrega los eventos en el hilo que publica. Como la vista es web, la capa `api` reenviará los eventos al navegador (por consultas periódicas o *Server-Sent Events*, pendiente de decidir).

---

## 6. GRASP aplicado a RutaVida

| Patrón | Dónde se aplica | Estado |
|---|---|---|
| **Information Expert** | `EmergencyStatus.canChangeTo()`, `AssignmentStatus.isOpen()`, `IncidentStatus.affectsRouting()`, `VehicleOperationalStatus.isDispatchable()`: cada estado sabe sus propias reglas | Programado |
| **Information Expert** | `RoadGraph` conoce sus tramos y su estado; la entidad `Route` calcula su tiempo total | Por programar |
| **Creator** | `DispatchService` crea la `EmergencyAssignment` (tiene la emergencia, la unidad y el usuario); `RouteService` crea las `Route` | Por programar |
| **Controller** | `LoginController`, `EmergencyController`, `DispatchController`… reciben los eventos del sistema que genera la vista | Por programar |
| **Low Coupling** | La vista solo conoce controladores; los servicios solo interfaces DAO; los eventos desacoplan a quien publica de quien escucha | Reglas programadas y probadas |
| **High Cohesion** | Un servicio por área de negocio (`EmergencyService`, `DispatchService`, `RouteService`…); un DAO por agregado | Estructura definida |
| **Polymorphism** | `CostFunction` (costo de emergencia, solo distancia…), `TrafficProvider` (simulado o real) | Diseñado en el documento consolidado |
| **Pure Fabrication** | `JdbcTransactionManager`, `AppContext`, `DatabaseConnection`, los DAOs, `InMemoryEventBus`, `GraphLoader` | Los cuatro primeros programados |
| **Indirection** | `TransactionManager` entre servicios y JDBC; `EventBus` entre simulador, servicios y vista; los controladores entre vista y servicios | Programado |
| **Protected Variations** | Interfaces DAO (cambiar de Oracle local a la nube o a otra base), `TrafficProvider` (simulación → fuente real), `ConnectionFactory`, `Clock` | Programado en DAO y conexión |

## 7. SOLID aplicado a RutaVida

| Principio | Cómo se cumple | Ejemplo |
|---|---|---|
| **S** — Responsabilidad única | Cada clase tiene un solo motivo para cambiar: el SQL solo cambia en `dao.jdbc`, las reglas en `service`, los textos en `util` | `JdbcTransactionManager` solo maneja transacciones; `DatabaseConnection` solo la configuración |
| **O** — Abierto/cerrado | Se agregan comportamientos con clases nuevas sin modificar las existentes | Nueva `CostFunction` o nuevo `TrafficProvider` sin tocar `AStarRouter` |
| **L** — Sustitución de Liskov | Toda implementación respeta el contrato de su interfaz | Un `TrafficProvider` real o simulado nunca devuelve `null` y solo reporta tramos que existen; un DAO falso de prueba se comporta como el real |
| **I** — Segregación de interfaces | Interfaces pequeñas: no hay un `CrudDAO` genérico con métodos que no aplican | `TRAFICO`, `UBICACION_VEHICULO` y `AUDITORIA` son solo de inserción: sus DAOs no tienen `update` ni `delete`. Los servicios que solo publican usan `EventPublisher`, no todo el `EventBus` |
| **D** — Inversión de dependencias | Los módulos de alto nivel dependen de abstracciones y `AppContext` elige la implementación | `DispatchService(EmergencyDAO, VehicleDAO, TransactionManager, EventPublisher, Clock)` |

---

## 8. Convenciones de nombres

| Elemento | Convención | Ejemplo |
|---|---|---|
| Entidad | Sustantivo en inglés, singular (tabla V.4 del documento consolidado) | `Emergency`, `RoadSegment`, `EmergencyAssignment` |
| Enum de estado | `<Entidad>Status` o nombre del catálogo, implementa `CodedEnum` | `EmergencyStatus.EN_ROUTE` → `"EN_RUTA"` |
| Interfaz DAO | `<Entidad>DAO` | `EmergencyDAO` |
| Implementación DAO | `Jdbc<Entidad>DAO` en `dao.jdbc` | `JdbcEmergencyDAO` |
| Servicio | `<Área>Service` | `DispatchService`, `RouteService` |
| Controlador | `<Área>Controller` | `EmergencyController` |
| DTO | Record con nombre descriptivo | `RouteOptionDto`, `DashboardSummaryDto` |
| Evento | Record en pasado | `EmergencyStatusChanged`, `RouteRecalculated` |
| Excepción | `<Motivo>Exception`, hereda de `RutaVidaException` | `BusinessRuleException` |
| Prueba | `<Clase>Test` en el mismo paquete, en `src/test/java` | `DispatchServiceTest` |

Reglas de código obligatorias:

- **Inyección por constructor.** Nadie hace `new JdbcXxxDAO(...)` fuera de `AppContext`.
- **SQL siempre con `PreparedStatement`**, nunca concatenando valores.
- **Sin textos de usuario en la lógica:** los mensajes en español van en una clase de mensajes de `util`.
- **Tiempo con `Clock` inyectado** (`LocalDateTime.now(clock)`), así las pruebas y el simulador controlan la hora.
- **Los datos simulados se marcan como `SIMULACION`** (`TrafficSource`, `PositionSource`, `IncidentOrigin`).

---

## 9. Cómo agregar una funcionalidad (receta para el equipo)

Ejemplo: *consultar los hospitales operativos*.

1. **Model** (Sherly): `model/Hospital.java` y, si hace falta, un enum en `model.enums`.
2. **DAO** (Andrés): interfaz `dao/HospitalDAO.java` con **solo** los métodos que se necesitan (`findOperational()`) e implementación `dao/jdbc/JdbcHospitalDAO.java` que traduce `HOSPITAL` → `Hospital`.
3. **Service**: `service/HospitalService.java` recibe `HospitalDAO` por constructor y aplica las reglas.
4. **Controller**: `controller/HospitalController.java` recibe `HospitalService`.
5. **AppContext** (Andrés): crear y conectar las tres clases.
6. **Pruebas**: el servicio se prueba con un `HospitalDAO` falso (sin Oracle); el DAO, contra la base local.
7. Ejecutar `mvn test`. Si `ArchitectureTest` falla, alguna capa importó algo que no debía.

---

## 10. Qué está programado y qué falta

| Componente | Estado |
|---|---|
| 15 paquetes con `package-info.java` | Programado |
| 19 enums de estados y catálogos alineados con los CHECK de Oracle | Programado y probado (`EnumSqlContractTest`) |
| Excepciones (`RutaVidaException` y 4 subclases) | Programado |
| `TransactionManager`, `ConnectionProvider`, `JdbcTransactionManager` | Programado y probado con conexiones falsas |
| `EventBus`, `InMemoryEventBus`, `DomainEvent` | Programado y probado |
| `AppContext` (composition root) | Programado; se completa a medida que aparecen DAOs y servicios |
| `DatabaseConnection`, `DatabaseCheck` | Programado y probado contra Oracle XE 18c ("23 de 23") |
| Pruebas de arquitectura (`ArchitectureTest`) | Programadas y probadas |
| Entidades, DAOs, servicios, controladores | **Pendiente** |
| Motor de rutas (`graph`, `traffic`) | Diseñado en el documento consolidado; **no está en el repositorio** |
| Vista web y API REST | **Pendiente** (decidido: web, ver `docs/decisiones-tecnicas.md`, DT-01) |

## 11. Decisiones tomadas y pendientes

- **Sin framework de inyección (sin Spring) por ahora.** `AppContext` cumple esa función en pocas líneas y es fácil de explicar en la sustentación.
- **Vista web (DT-01, 15/10/2026).** Se agregará un paquete `api` con adaptadores REST que llamen a los mismos controladores, sin tocar `service` ni `dao`. El paquete `view` del backend se reemplazará por `api` cuando se programe, ajustando `ArchitectureTest` y el diagrama de clases en el mismo PR. El servidor HTTP, la librería JSON y el mecanismo de tiempo real están pendientes (ver `docs/decisiones-tecnicas.md`).
- **Pendiente:** contratos de los servicios principales (`DispatchService`, `RouteService`), a acordar entre Kendrick, Sherly y Andrés antes de programarlos.