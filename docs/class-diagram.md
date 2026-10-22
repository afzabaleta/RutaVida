# Class Diagram — RutaVida

Diagrama de clases completo del sistema, organizado por capas de la arquitectura, según los casos de uso definidos en `docs/analysis.md` y la arquitectura de `docs/arquitectura-software.md`.

- Los nombres de clases, atributos y métodos están en inglés (convención del código). Las tablas de Oracle conservan sus nombres en español; la equivalencia está en `docs/convenciones-base-datos.md`.
- Para que se pueda leer, el sistema se divide en 8 diagramas, uno por capa o módulo. La sección 0 muestra cómo se conectan.
- **Getters y setters:** cada atributo tiene su getter. Los setters existen solo donde el dato puede cambiar, y por eso no se repiten en el diagrama. Sí se muestran los métodos con comportamiento.
- **Referencias entre entidades:** una entidad guarda el **id** de otra (`emergencyTypeId : long`), no el objeto completo. Así cada DAO mapea una sola tabla y no se cargan cadenas de objetos desde Oracle. Los servicios combinan los datos cuando hace falta. La multiplicidad de cada relación sigue las llaves foráneas de la base de datos.
- La sección 9 indica qué clases ya están **programadas** y cuáles están **diseñadas** pero pendientes.

---

## 0. Vista general por capas

```mermaid
---
config:
  markdownAutoWrap: false
---
classDiagram
    direction TB

    class MainView {
        <<view - web>>
    }
    class EmergencyController {
        +register(request: EmergencyRequest) OperationResult~Emergency~
    }
    class EmergencyService {
        +register(request: EmergencyRequest, session: UserSession) Emergency
    }
    class EmergencyDAO {
        <<interface>>
        +insert(emergency: Emergency) long
    }
    class JdbcEmergencyDAO {
        +insert(emergency: Emergency) long
    }
    class Emergency {
        -status : EmergencyStatus
        +changeStatus(next: EmergencyStatus, at: LocalDateTime) void
    }
    class TransactionManager {
        <<interface>>
        +runInTransaction(work: Runnable) void
    }
    class EventBus {
        <<interface>>
        +publish(event: DomainEvent) void
    }
    class AppContext {
        +create() AppContext$
    }
    class OracleXE {
        <<database>>
        EMERGENCIA
    }

    MainView ..> EmergencyController : usa
    EmergencyController ..> EmergencyService : delega
    EmergencyService ..> EmergencyDAO : interfaz
    EmergencyService ..> TransactionManager
    EmergencyService ..> EventBus : publica eventos
    EmergencyDAO <|.. JdbcEmergencyDAO : implementa
    JdbcEmergencyDAO ..> OracleXE : SQL / JDBC
    EmergencyService ..> Emergency
    MainView ..> EventBus : se suscribe
    AppContext ..> JdbcEmergencyDAO : crea
    AppContext ..> EmergencyService : crea e inyecta
    AppContext ..> EmergencyController : crea e inyecta
```

---

## 1. Capa Model — entidades

Las 23 tablas se reparten en cuatro diagramas. Una clase sin atributos (por ejemplo `User` en el diagrama 1.3) es una referencia a una clase definida en otro diagrama de esta sección.

### 1.1 Tipos compartidos

`GeoPoint` reemplaza los pares latitud/longitud y sabe calcular distancias (*Information Expert*). Las cinco entidades con ubicación implementan `Locatable`, así que un servicio puede buscar "la más cercana" de cualquiera de ellas con el mismo código (*Polymorphism*).

```mermaid
---
config:
  markdownAutoWrap: false
---
classDiagram
    direction LR

    class Locatable {
        <<interface>>
        +getLocation() GeoPoint
    }
    class GeoPoint {
        <<record>>
        +latitude : double
        +longitude : double
        +distanceMetersTo(other: GeoPoint) double
    }
    class EmergencyStation
    class EmergencyVehicle
    class Hospital
    class Emergency
    class RoadNode

    Locatable <|.. EmergencyStation
    Locatable <|.. EmergencyVehicle
    Locatable <|.. Hospital
    Locatable <|.. Emergency
    Locatable <|.. RoadNode
    Locatable ..> GeoPoint
```

### 1.2 Seguridad, alertas y auditoría

```mermaid
---
config:
  markdownAutoWrap: false
---
classDiagram
    direction LR

    class UserSession {
        <<record>>
        +user : User
        +role : Role
        +startedAt : LocalDateTime
        +hasPermission(module: String, action: String) boolean
        +require(module: String, action: String) void
    }
    class Role {
        -id : long
        -name : String
        -description : String
        -status : RecordStatus
        -permissions : Set~Permission~
        +hasPermission(module: String, action: String) boolean
        +isActive() boolean
        +assignId(id: long) void
    }
    class Permission {
        -id : long
        -module : String
        -action : String
        -description : String
        +matches(module: String, action: String) boolean
        +assignId(id: long) void
    }
    class User {
        -id : long
        -roleId : long
        -document : String
        -firstNames : String
        -lastNames : String
        -email : String
        -username : String
        -passwordHash : String
        -status : RecordStatus
        -registeredAt : LocalDateTime
        +getFullName() String
        +isActive() boolean
        +assignId(id: long) void
    }
    class Alert {
        -id : long
        -emergencyId : Long
        -userId : long
        -type : AlertType
        -message : String
        -createdAt : LocalDateTime
        -read : boolean
        +markAsRead() void
    }
    class Notification {
        -id : long
        -userId : long
        -type : String
        -title : String
        -message : String
        -createdAt : LocalDateTime
        -read : boolean
        +markAsRead() void
    }
    class AuditLog {
        -id : long
        -userId : Long
        -module : String
        -operation : String
        -recordId : Long
        -occurredAt : LocalDateTime
        -result : AuditResult
        -detail : String
        +assignId(id: long) void
    }
    class Emergency

    Role "0..*" o-- "0..*" Permission : ROL_PERMISO
    User "0..*" --> "1" Role : has
    UserSession --> User
    UserSession --> Role
    Alert "0..*" --> "1" User : for
    Alert "0..*" --> "0..1" Emergency : about
    Notification "0..*" --> "1" User : for
    AuditLog "0..*" --> "0..1" User : performed by
```

### 1.3 Emergencias, unidades, hospitales y rutas

```mermaid
---
config:
  markdownAutoWrap: false
---
classDiagram
    direction LR

    class EmergencyType {
        -id : long
        -name : String
        -priority : int
        -description : String
        +assignId(id: long) void
    }
    class Emergency {
        -id : long
        -emergencyTypeId : long
        -reportedByUserId : long
        -addressReference : String
        -location : GeoPoint
        -reportedAt : LocalDateTime
        -severity : Severity
        -description : String
        -status : EmergencyStatus
        -closedAt : LocalDateTime
        +getLocation() GeoPoint
        +changeStatus(next: EmergencyStatus, at: LocalDateTime) void
        +isActive() boolean
        +assignId(id: long) void
    }
    class EmergencyAssignment {
        -id : long
        -emergencyId : long
        -vehicleId : long
        -coordinatorUserId : long
        -assignedAt : LocalDateTime
        -departedAt : LocalDateTime
        -arrivedAt : LocalDateTime
        -status : AssignmentStatus
        +changeStatus(next: AssignmentStatus, at: LocalDateTime) void
        +isOpen() boolean
        +getResponseTime() Duration
    }
    class EmergencyVehicle {
        -id : long
        -vehicleTypeId : long
        -stationId : long
        -unitCode : String
        -plate : String
        -operationalStatus : VehicleOperationalStatus
        -lastLocation : GeoPoint
        -lastLocationAt : LocalDateTime
        +getLocation() GeoPoint
        +isDispatchable() boolean
        +moveTo(point: GeoPoint, at: LocalDateTime) void
        +changeStatus(next: VehicleOperationalStatus) void
        +assignId(id: long) void
    }
    class VehicleType {
        -id : long
        -name : String
        -priority : int
        -description : String
        +assignId(id: long) void
    }
    class EmergencyStation {
        -id : long
        -name : String
        -address : String
        -neighborhood : String
        -phone : String
        -location : GeoPoint
        -status : RecordStatus
        +getLocation() GeoPoint
        +assignId(id: long) void
    }
    class Hospital {
        -id : long
        -name : String
        -level : HospitalLevel
        -address : String
        -phone : String
        -location : GeoPoint
        -emergencyCapacity : Integer
        -status : RecordStatus
        +getLocation() GeoPoint
        +isOperational() boolean
        +assignId(id: long) void
    }
    class HospitalServiceOffering {
        -id : long
        -hospitalId : long
        -serviceName : String
        -available : boolean
        -capacity : Integer
        -updatedAt : LocalDateTime
    }
    class Route {
        -id : long
        -emergencyId : long
        -vehicleId : long
        -destinationHospitalId : Long
        -type : RouteType
        -distanceKm : double
        -estimatedMinutes : double
        -totalCost : double
        -generatedAt : LocalDateTime
        -recommended : boolean
        -status : RouteStatus
        -segments : List~RouteSegment~
        +isToHospital() boolean
        +usesSegment(segmentId: long) boolean
    }
    class RouteSegment {
        -routeId : long
        -order : int
        -segmentId : long
        -estimatedSeconds : double
        -congestionFactor : double
        -trafficLightCount : int
    }
    class VehicleLocation {
        -id : long
        -vehicleId : long
        -recordedAt : LocalDateTime
        -location : GeoPoint
        -speedKmh : Double
        -headingDegrees : Double
        -source : PositionSource
    }
    class User
    class RoadSegment

    Emergency "0..*" --> "1" EmergencyType : classified as
    Emergency "0..*" --> "1" User : reported by
    EmergencyAssignment "0..*" --> "1" Emergency : attends
    EmergencyAssignment "0..*" --> "1" EmergencyVehicle : uses
    EmergencyAssignment "0..*" --> "1" User : coordinated by
    EmergencyVehicle "0..*" --> "1" VehicleType : is a
    EmergencyVehicle "0..*" --> "1" EmergencyStation : based at
    HospitalServiceOffering "0..*" --> "1" Hospital : offered by
    Route "0..*" --> "1" Emergency : for
    Route "0..*" --> "1" EmergencyVehicle : driven by
    Route "0..*" --> "0..1" Hospital : destination
    Route "1" *-- "1..*" RouteSegment : ordered path
    RouteSegment "0..*" --> "1" RoadSegment : crosses
    VehicleLocation "0..*" --> "1" EmergencyVehicle : position of
```

### 1.4 Red vial y tráfico

```mermaid
---
config:
  markdownAutoWrap: false
---
classDiagram
    direction LR

    class RoadNode {
        -id : long
        -osmId : Long
        -reference : String
        -location : GeoPoint
        -nodeType : NodeType
        -status : RecordStatus
        +getLocation() GeoPoint
    }
    class RoadSegment {
        -id : long
        -fromNodeId : long
        -toNodeId : long
        -osmWayId : Long
        -roadName : String
        -lengthMeters : double
        -maxSpeedKmh : double
        -status : RoadSegmentStatus
        +getBaseTimeSeconds() double
        +isOpen() boolean
    }
    class TrafficLight {
        -id : long
        -nodeId : long
        -code : String
        -currentState : TrafficLightState
        -cycleSeconds : Integer
        -synchronizedSignal : boolean
        -status : RecordStatus
    }
    class TrafficReading {
        -id : long
        -segmentId : long
        -measuredAt : LocalDateTime
        -level : TrafficLevel
        -averageSpeedKmh : Double
        -congestionFactor : double
        -source : TrafficSource
    }
    class RoadIncident {
        -id : long
        -segmentId : long
        -type : IncidentType
        -severity : Severity
        -blocksSegment : boolean
        -description : String
        -startedAt : LocalDateTime
        -endedAt : LocalDateTime
        -status : IncidentStatus
        -origin : IncidentOrigin
        +activate(blocksSegment: boolean) void
        +reject(at: LocalDateTime) void
        +close(at: LocalDateTime) void
        +affectsRouting() boolean
    }
    class RouteSegment

    RoadSegment "0..*" --> "1" RoadNode : from
    RoadSegment "0..*" --> "1" RoadNode : to
    TrafficLight "0..*" --> "1" RoadNode : controls
    TrafficReading "0..*" --> "1" RoadSegment : measures
    RoadIncident "0..*" --> "1" RoadSegment : affects
    RouteSegment "0..*" --> "1" RoadSegment : crosses
```

**Correspondencia con las tablas:** cada entidad equivale a una tabla (`Emergency` ↔ `EMERGENCIA`, `RoadSegment` ↔ `TRAMO_VIAL`…). Hay dos excepciones:

- `ROL_PERMISO` no es una clase: se representa con `Role.permissions`.
- `UserSession` no es una tabla: guarda en memoria quién inició sesión.

---

## 2. Capa Model — enumeraciones

Las 19 enumeraciones implementan `CodedEnum`: la constante va en inglés y `code()` devuelve el valor que guarda Oracle (por ejemplo, `EN_ROUTE` → `"EN_RUTA"`). `EnumSqlContractTest` comprueba que coincidan con los CHECK de la base de datos.

### 2.1 Estados con reglas de transición

```mermaid
---
config:
  markdownAutoWrap: false
---
classDiagram
    direction TB

    class CodedEnum {
        <<interface>>
        +code() String
        +fromCode(type: Class~E~, code: String) E$
    }
    class EmergencyStatus {
        <<enumeration>>
        REPORTED
        ASSIGNED
        EN_ROUTE
        ON_SCENE
        TRANSPORTING
        CLOSED
        +nextStatuses() Set~EmergencyStatus~
        +canChangeTo(next: EmergencyStatus) boolean
        +isActive() boolean
    }
    class AssignmentStatus {
        <<enumeration>>
        ASSIGNED
        EN_ROUTE
        ON_SCENE
        TRANSPORTING
        FINISHED
        CANCELLED
        +nextStatuses() Set~AssignmentStatus~
        +canChangeTo(next: AssignmentStatus) boolean
        +isOpen() boolean
    }
    class IncidentStatus {
        <<enumeration>>
        PENDING
        ACTIVE
        CLOSED
        REJECTED
        +affectsRouting() boolean
    }
    class VehicleOperationalStatus {
        <<enumeration>>
        AVAILABLE
        BUSY
        MAINTENANCE
        OUT_OF_SERVICE
        +isDispatchable() boolean
    }
    CodedEnum <|.. EmergencyStatus
    CodedEnum <|.. AssignmentStatus
    CodedEnum <|.. IncidentStatus
    CodedEnum <|.. VehicleOperationalStatus
```

### 2.2 Catálogos

Todas implementan `CodedEnum` (la flecha se omite).

```mermaid
---
config:
  markdownAutoWrap: false
---
classDiagram
    direction TB

    class Severity {
        <<enumeration>>
        LOW
        MEDIUM
        HIGH
        CRITICAL
    }
    class IncidentType {
        <<enumeration>>
        ACCIDENT
        ROADWORK
        CLOSURE
        PROTEST
        OTHER
    }
    class IncidentOrigin {
        <<enumeration>>
        OPERATOR
        CITIZEN_QR
        SIMULATION
    }
    class RoadSegmentStatus {
        <<enumeration>>
        OPEN
        CLOSED
        RESTRICTED
    }
    class NodeType {
        <<enumeration>>
        INTERSECTION
        ROUNDABOUT
        BRIDGE
        ACCESS
        WAY_POINT
        OTHER
    }
    class TrafficLevel {
        <<enumeration>>
        FLUID
        MODERATE
        CONGESTED
    }
    class TrafficSource {
        <<enumeration>>
        MANUAL
        SIMULATION
        SENSOR
        EXTERNAL_API
    }
    class PositionSource {
        <<enumeration>>
        SIMULATION
        GPS
    }
```

```mermaid
---
config:
  markdownAutoWrap: false
---
classDiagram
    direction TB

    class TrafficLightState {
        <<enumeration>>
        GREEN
        YELLOW
        RED
        OUT_OF_SERVICE
    }
    class RouteType {
        <<enumeration>>
        MAIN
        ALTERNATIVE
    }
    class RouteStatus {
        <<enumeration>>
        PROPOSED
        ACTIVE
        FINISHED
        DISCARDED
    }
    class HospitalLevel {
        <<enumeration>>
        LEVEL_I
        LEVEL_II
        LEVEL_III
        LEVEL_IV
    }
    class AlertType {
        <<enumeration>>
        CONGESTION
        ACCIDENT_ON_ROUTE
        ROAD_CLOSURE
        ROUTE_RECALCULATION
        AVAILABILITY_CHANGE
        OTHER
    }
    class AuditResult {
        <<enumeration>>
        SUCCESS
        FAILURE
    }
    class RecordStatus {
        <<enumeration>>
        ACTIVE
        INACTIVE
    }
```

---

## 3. Capa DAO — interfaces

Cada interfaz declara **solo** los métodos que necesitan los casos de uso (SOLID: segregación de interfaces). Las tablas de historial (`TRAFICO`, `UBICACION_VEHICULO`, `AUDITORIA`) solo admiten inserción. Cada DAO depende de la entidad que lee o escribe.

```mermaid
---
config:
  markdownAutoWrap: false
---
classDiagram
    class TransactionManager {
        <<interface>>
        +callInTransaction(work: Supplier~T~) T
        +runInTransaction(work: Runnable) void
    }
```

`TransactionManager` agrupa varias operaciones de DAO en una sola transacción.

### 3.1 Seguridad, emergencias y despacho

```mermaid
---
config:
  markdownAutoWrap: false
---
classDiagram
    direction LR

    class UserDAO {
        <<interface>>
        +findById(id: long) Optional~User~
        +findByUsername(username: String) Optional~User~
        +findAll() List~User~
        +insert(user: User) long
        +update(user: User) void
    }
    class RoleDAO {
        <<interface>>
        +findById(id: long) Optional~Role~
        +findAll() List~Role~
    }
    class AuditDAO {
        <<interface>>
        +insert(log: AuditLog) void
        +findByRecord(module: String, recordId: long) List~AuditLog~
        +findByPeriod(from: LocalDateTime, to: LocalDateTime) List~AuditLog~
    }
    class EmergencyTypeDAO {
        <<interface>>
        +findById(id: long) Optional~EmergencyType~
        +findAll() List~EmergencyType~
    }
    class EmergencyDAO {
        <<interface>>
        +insert(emergency: Emergency) long
        +findById(id: long) Optional~Emergency~
        +findActive() List~Emergency~
        +findByPeriod(from: LocalDateTime, to: LocalDateTime) List~Emergency~
        +updateStatus(id: long, status: EmergencyStatus, closedAt: LocalDateTime) void
    }
    class EmergencyAssignmentDAO {
        <<interface>>
        +insert(assignment: EmergencyAssignment) long
        +findById(id: long) Optional~EmergencyAssignment~
        +findOpenByEmergency(emergencyId: long) Optional~EmergencyAssignment~
        +findOpenByVehicle(vehicleId: long) Optional~EmergencyAssignment~
        +findByEmergency(emergencyId: long) List~EmergencyAssignment~
        +update(assignment: EmergencyAssignment) void
    }
    class User
    class Role
    class AuditLog
    class EmergencyType
    class Emergency
    class EmergencyAssignment

    UserDAO ..> User
    RoleDAO ..> Role
    AuditDAO ..> AuditLog
    EmergencyTypeDAO ..> EmergencyType
    EmergencyDAO ..> Emergency
    EmergencyAssignmentDAO ..> EmergencyAssignment
```

### 3.2 Flota y hospitales

```mermaid
---
config:
  markdownAutoWrap: false
---
classDiagram
    direction LR

    class VehicleTypeDAO {
        <<interface>>
        +findAll() List~VehicleType~
    }
    class EmergencyStationDAO {
        <<interface>>
        +findById(id: long) Optional~EmergencyStation~
        +findAll() List~EmergencyStation~
        +insert(station: EmergencyStation) long
        +update(station: EmergencyStation) void
    }
    class EmergencyVehicleDAO {
        <<interface>>
        +findById(id: long) Optional~EmergencyVehicle~
        +findAll() List~EmergencyVehicle~
        +findAvailable() List~EmergencyVehicle~
        +insert(vehicle: EmergencyVehicle) long
        +update(vehicle: EmergencyVehicle) void
        +updateStatus(id: long, status: VehicleOperationalStatus) void
        +updateLocation(id: long, location: GeoPoint, at: LocalDateTime) void
    }
    class HospitalDAO {
        <<interface>>
        +findById(id: long) Optional~Hospital~
        +findAll() List~Hospital~
        +findOperational() List~Hospital~
        +insert(hospital: Hospital) long
        +update(hospital: Hospital) void
        +findServices(hospitalId: long) List~HospitalServiceOffering~
        +updateService(offering: HospitalServiceOffering) void
    }
    class VehicleType
    class EmergencyStation
    class EmergencyVehicle
    class Hospital
    class HospitalServiceOffering

    VehicleTypeDAO ..> VehicleType
    EmergencyStationDAO ..> EmergencyStation
    EmergencyVehicleDAO ..> EmergencyVehicle
    HospitalDAO ..> Hospital
    HospitalDAO ..> HospitalServiceOffering
```

### 3.3 Red vial, tráfico e incidentes

```mermaid
---
config:
  markdownAutoWrap: false
---
classDiagram
    direction LR

    class RoadNodeDAO {
        <<interface>>
        +findAll() List~RoadNode~
        +insertBatch(nodes: List~RoadNode~) void
    }
    class RoadSegmentDAO {
        <<interface>>
        +findAll() List~RoadSegment~
        +insertBatch(segments: List~RoadSegment~) void
        +updateStatus(id: long, status: RoadSegmentStatus) void
    }
    class TrafficLightDAO {
        <<interface>>
        +findAll() List~TrafficLight~
    }
    class TrafficReadingDAO {
        <<interface>>
        +insertBatch(readings: List~TrafficReading~) void
    }
    class RoadIncidentDAO {
        <<interface>>
        +insert(incident: RoadIncident) long
        +findById(id: long) Optional~RoadIncident~
        +findByStatus(status: IncidentStatus) List~RoadIncident~
        +update(incident: RoadIncident) void
    }
    class RoadNode
    class RoadSegment
    class TrafficLight
    class TrafficReading
    class RoadIncident

    RoadNodeDAO ..> RoadNode
    RoadSegmentDAO ..> RoadSegment
    TrafficLightDAO ..> TrafficLight
    TrafficReadingDAO ..> TrafficReading
    RoadIncidentDAO ..> RoadIncident
```

### 3.4 Rutas, seguimiento y alertas

```mermaid
---
config:
  markdownAutoWrap: false
---
classDiagram
    direction LR

    class RouteDAO {
        <<interface>>
        +insert(route: Route) long
        +findById(id: long) Optional~Route~
        +findByEmergency(emergencyId: long) List~Route~
        +findActive() List~Route~
        +findActiveUsingSegments(segmentIds: Set~Long~) List~Route~
        +updateStatus(id: long, status: RouteStatus) void
    }
    class VehicleLocationDAO {
        <<interface>>
        +insertBatch(locations: List~VehicleLocation~) void
        +findByVehicle(vehicleId: long, from: LocalDateTime, to: LocalDateTime) List~VehicleLocation~
    }
    class AlertDAO {
        <<interface>>
        +insert(alert: Alert) long
        +findUnreadByUser(userId: long) List~Alert~
        +markAsRead(id: long) void
    }
    class NotificationDAO {
        <<interface>>
        +insert(notification: Notification) long
        +findUnreadByUser(userId: long) List~Notification~
        +markAsRead(id: long) void
    }
    class Route
    class VehicleLocation
    class Alert
    class Notification

    RouteDAO ..> Route
    VehicleLocationDAO ..> VehicleLocation
    AlertDAO ..> Alert
    NotificationDAO ..> Notification
```

---

## 4. Capa DAO — implementación JDBC (Oracle)

Toda implementación hereda de `AbstractJdbcDAO`, que reúne el manejo repetido de JDBC: obtener la conexión, ejecutar con parámetros y convertir `SQLException`. Cada `JdbcXxxDAO` solo escribe su SQL y su mapeo fila → objeto (patrón *Template Method*).

```mermaid
---
config:
  markdownAutoWrap: false
---
classDiagram
    direction TB

    class TransactionManager {
        <<interface>>
        +callInTransaction(work: Supplier~T~) T
        +runInTransaction(work: Runnable) void
    }
    class ConnectionProvider {
        <<interface>>
        +withConnection(work: SqlWork~T~) T
    }
    class SqlWork~T~ {
        <<interface>>
        +apply(connection: Connection) T
    }
    class ConnectionFactory {
        <<interface>>
        +open() Connection
    }
    
    class ClientIdentifierConnectionFactory {
        -delegate : ConnectionFactory
        -currentUserId : Supplier~Long~
        +open() Connection
    }
    
    class JdbcTransactionManager {
        -factory : ConnectionFactory
        -current : ThreadLocal~Connection~
        +callInTransaction(work: Supplier~T~) T
        +withConnection(work: SqlWork~T~) T
        +isInTransaction() boolean
    }
    class RowMapper~T~ {
        <<interface>>
        +map(row: ResultSet) T
    }
    class AbstractJdbcDAO {
        <<abstract>>
        #connections : ConnectionProvider
        #queryList(sql: String, mapper: RowMapper~T~, params: Object[]) List~T~
        #queryOne(sql: String, mapper: RowMapper~T~, params: Object[]) Optional~T~
        #execute(sql: String, params: Object[]) int
        #insertReturningId(sql: String, idColumn: String, params: Object[]) long
        #bind(statement: PreparedStatement, params: Object[]) void$
        #getLocalDateTime(row: ResultSet, column: String) LocalDateTime$
        #getNullableLong(row: ResultSet, column: String) Long$
        #getNullableInt(row: ResultSet, column: String) Integer$
        #getFlag(row: ResultSet, column: String) boolean$
        #callProcedure(call: String, params: Object[]) void
        #callProcedureReturningLong(call: String, inParams: Object[]) long
    }
    class DatabaseConnection {
        +fromEnvironment() DatabaseConnection$
        +open() Connection
        +url() String
        +user() String
    }

    TransactionManager <|.. JdbcTransactionManager
    ConnectionProvider <|.. JdbcTransactionManager
    ConnectionProvider ..> SqlWork~T~
    JdbcTransactionManager --> ConnectionFactory
    ConnectionFactory <|.. ClientIdentifierConnectionFactory
    ClientIdentifierConnectionFactory --> ConnectionFactory : decora
    ConnectionFactory <.. DatabaseConnection : open() cumple el contrato
    AbstractJdbcDAO --> ConnectionProvider
    AbstractJdbcDAO ..> RowMapper~T~

    class JdbcUserDAO
    class JdbcRoleDAO
    class JdbcAuditDAO
    class JdbcEmergencyTypeDAO
    class JdbcEmergencyDAO
    class JdbcEmergencyAssignmentDAO
    class JdbcVehicleTypeDAO
    class JdbcEmergencyStationDAO
    class JdbcEmergencyVehicleDAO
    class JdbcHospitalDAO
    class JdbcRoadNodeDAO
    class JdbcRoadSegmentDAO
    class JdbcTrafficLightDAO
    class JdbcTrafficReadingDAO
    class JdbcRoadIncidentDAO
    class JdbcRouteDAO
    class JdbcVehicleLocationDAO
    class JdbcAlertDAO
    class JdbcNotificationDAO

    AbstractJdbcDAO <|-- JdbcUserDAO
    AbstractJdbcDAO <|-- JdbcRoleDAO
    AbstractJdbcDAO <|-- JdbcAuditDAO
    AbstractJdbcDAO <|-- JdbcEmergencyTypeDAO
    AbstractJdbcDAO <|-- JdbcEmergencyDAO
    AbstractJdbcDAO <|-- JdbcEmergencyAssignmentDAO
    AbstractJdbcDAO <|-- JdbcVehicleTypeDAO
    AbstractJdbcDAO <|-- JdbcEmergencyStationDAO
    AbstractJdbcDAO <|-- JdbcEmergencyVehicleDAO
    AbstractJdbcDAO <|-- JdbcHospitalDAO
    AbstractJdbcDAO <|-- JdbcRoadNodeDAO
    AbstractJdbcDAO <|-- JdbcRoadSegmentDAO
    AbstractJdbcDAO <|-- JdbcTrafficLightDAO
    AbstractJdbcDAO <|-- JdbcTrafficReadingDAO
    AbstractJdbcDAO <|-- JdbcRoadIncidentDAO
    AbstractJdbcDAO <|-- JdbcRouteDAO
    AbstractJdbcDAO <|-- JdbcVehicleLocationDAO
    AbstractJdbcDAO <|-- JdbcAlertDAO
    AbstractJdbcDAO <|-- JdbcNotificationDAO
```

Cada `JdbcXxxDAO` implementa la interfaz `XxxDAO` del mismo nombre de la sección 3 (por ejemplo, `JdbcEmergencyDAO` implementa `EmergencyDAO`). Esas 19 flechas de realización no se dibujan para que el diagrama se pueda leer.

---

## 5. Capa Service — reglas de negocio, DTO y utilidades

### 5.1 Servicios

Cada servicio agrupa las reglas de un área del negocio (*High Cohesion*) y recibe sus dependencias por constructor.

```mermaid
---
config:
  markdownAutoWrap: false
---
classDiagram
    direction LR

    class AuthService {
        -userDao : UserDAO
        -roleDao : RoleDAO
        -passwordHasher : PasswordHasher
        -auditService : AuditService
        -clock : Clock
        +login(username: String, password: char[]) UserSession
        +logout(session: UserSession) void
    }
    class UserService {
        -userDao : UserDAO
        -roleDao : RoleDAO
        -passwordHasher : PasswordHasher
        -transactions : TransactionManager
        -auditService : AuditService
        +create(request: UserRequest, session: UserSession) User
        +listAll(session: UserSession) List~User~
        +deactivate(userId: long, session: UserSession) void
        +changePassword(userId: long, current: char[], next: char[]) void
    }
    class AuditService {
        -auditDao : AuditDAO
        -clock : Clock
        +record(userId: Long, module: String, operation: String, recordId: Long, result: AuditResult, detail: String) void
        +findByRecord(module: String, recordId: long) List~AuditLog~
    }
    
    class AdminBootstrapService {
        -userDao : UserDAO
        -roleDao : RoleDAO
        -passwordHasher : PasswordHasher
        -auditService : AuditService
        -transactions : TransactionManager
        -clock : Clock
        +countAdmins() long
        +createAdmin(document: String, firstNames: String, lastNames: String, email: String, username: String, password: char[]) User
    }
    
    class EmergencyService {
        -emergencyDao : EmergencyDAO
        -emergencyTypeDao : EmergencyTypeDAO
        -routeService : RouteService
        -transactions : TransactionManager
        -auditService : AuditService
        -events : EventPublisher
        -clock : Clock
        +register(request: EmergencyRequest, session: UserSession) Emergency
        +listActive() List~Emergency~
        +findById(id: long) Emergency
        +getHistory(id: long) EmergencyHistory
    }
    class DispatchService {
        -emergencyDao : EmergencyDAO
        -vehicleDao : EmergencyVehicleDAO
        -assignmentDao : EmergencyAssignmentDAO
        -routeService : RouteService
        -hospitalService : HospitalService
        -transactions : TransactionManager
        -auditService : AuditService
        -events : EventPublisher
        -clock : Clock
        +suggestUnits(emergencyId: long) List~UnitSuggestion~
        +dispatch(emergencyId: long, vehicleId: long, session: UserSession) EmergencyAssignment
        +dispatchOptimal(emergencyIds: List~Long~, session: UserSession) List~EmergencyAssignment~
        +advanceMission(assignmentId: long, next: AssignmentStatus, session: UserSession) void
        +startTransfer(assignmentId: long, hospitalId: long, session: UserSession) void
        +cancelAssignment(assignmentId: long, reason: String, session: UserSession) void
        +closeEmergency(emergencyId: long, session: UserSession) void
    }
    class HungarianAlgorithm {
        +solve(cost: double[][]) int[]$
    }
    class FleetService {
        -vehicleDao : EmergencyVehicleDAO
        -stationDao : EmergencyStationDAO
        -vehicleTypeDao : VehicleTypeDAO
        -transactions : TransactionManager
        -auditService : AuditService
        +listAvailable() List~EmergencyVehicle~
        +listAll() List~EmergencyVehicle~
        +register(vehicle: EmergencyVehicle, session: UserSession) EmergencyVehicle
        +changeStatus(vehicleId: long, status: VehicleOperationalStatus, session: UserSession) void
    }
    class HospitalService {
        -hospitalDao : HospitalDAO
        -routeService : RouteService
        +listOperational() List~Hospital~
        +rankForTransfer(from: GeoPoint, requiredService: String, vehicleTypeId: long) List~HospitalOption~
        +updateServiceAvailability(offering: HospitalServiceOffering, session: UserSession) void
    }
    class DashboardService {
        -emergencyDao : EmergencyDAO
        -vehicleDao : EmergencyVehicleDAO
        -hospitalDao : HospitalDAO
        -incidentDao : RoadIncidentDAO
        -assignmentDao : EmergencyAssignmentDAO
        +getSummary() DashboardSummary
    }
    class AlertService {
        -alertDao : AlertDAO
        -notificationDao : NotificationDAO
        -userDao : UserDAO
        +onRouteRecalculated(event: RouteRecalculated) void
        +onUnitDispatched(event: UnitDispatched) void
        +listUnread(session: UserSession) List~Alert~
        +markAsRead(alertId: long, session: UserSession) void
    }
    class PasswordHasher {
        <<interface>>
        +hash(raw: char[]) String
        +matches(raw: char[], stored: String) boolean
    }
    class Pbkdf2PasswordHasher {
        -iterations : int
        +hash(raw: char[]) String
        +matches(raw: char[], stored: String) boolean
    }
    class RouteService

    PasswordHasher <|.. Pbkdf2PasswordHasher
    AuthService ..> PasswordHasher
    UserService ..> PasswordHasher
    AdminBootstrapService ..> PasswordHasher
    AuthService ..> AuditService
    AdminBootstrapService ..> AuditService
    UserService ..> AuditService
    EmergencyService ..> AuditService
    DispatchService ..> AuditService
    FleetService ..> AuditService
    EmergencyService ..> RouteService
    DispatchService ..> RouteService
    DispatchService ..> HospitalService
    DispatchService ..> HungarianAlgorithm
    HospitalService ..> RouteService
```

### 5.2 DTO y utilidades

Los DTO (paquete `dto`) son *records* sin lógica que viajan entre el controlador y la vista. Las utilidades (paquete `util`) no tienen reglas de negocio.

```mermaid
---
config:
  markdownAutoWrap: false
---
classDiagram
    direction LR

    class EmergencyRequest {
        <<record>>
        +emergencyTypeId : long
        +addressReference : String
        +location : GeoPoint
        +severity : Severity
        +description : String
    }
    class UnitSuggestion {
        <<record>>
        +vehicle : EmergencyVehicle
        +etaSeconds : double
        +distanceKm : double
    }
    class HospitalOption {
        <<record>>
        +hospital : Hospital
        +etaSeconds : double
        +availabilityPenalty : double
        +score : double
    }
    class EmergencyHistory {
        <<record>>
        +emergency : Emergency
        +assignments : List~EmergencyAssignment~
        +routes : List~Route~
        +auditTrail : List~AuditLog~
    }
    class DashboardSummary {
        <<record>>
        +activeEmergencies : int
        +availableUnits : int
        +operationalHospitals : int
        +activeIncidents : int
        +averageResponseMinutes : double
    }
    class UserRequest {
        <<record>>
        +roleId : long
        +document : String
        +firstNames : String
        +lastNames : String
        +email : String
        +username : String
        +password : char[]
    }
class ValidationUtil {
    +requireText(value: String, field: String, maxLength: int) String$
    +optionalText(value: String, field: String, maxLength: int) String$
    +requirePositiveId(id: long, field: String) long$
    +requireNonNull(value: T, field: String) T$
    +requireInRange(value: double, min: double, max: double, field: String) double$
}



class Messages {
    +get(key: String) String$
    +userMessage(error: RuntimeException) String$
}


    class AuthService
    class UserService
    class EmergencyService
    class DispatchService
    class HospitalService
    class DashboardService

    DispatchService ..> HospitalService
    EmergencyService ..> EmergencyRequest
    EmergencyService ..> EmergencyHistory
    DispatchService ..> UnitSuggestion
    HospitalService ..> HospitalOption
    DashboardService ..> DashboardSummary
    UserService ..> UserRequest
    EmergencyService ..> ValidationUtil
```

Los atributos de cada servicio son sus dependencias, que recibe por constructor. Los tipos `XxxDAO` son las interfaces de la sección 3, nunca las clases `JdbcXxxDAO`.

---

## 6. Motor de rutas, tráfico y simulación

### 6.1 Grafo y algoritmo de rutas (paquete `graph`, Kendrick)

El grafo vive en memoria: la topología (`Node`, `Edge`) no cambia y el estado de cada tramo (`EdgeState`) se reemplaza completo en cada actualización. `CostFunction` permite cambiar el criterio de costo sin tocar `AStarRouter` (SOLID: abierto/cerrado).

```mermaid
---
config:
  markdownAutoWrap: false
---
classDiagram
    direction LR

    class Node {
        <<record>>
        +index : int
        +dbId : long
        +lat : double
        +lon : double

        +distanceMetersTo(other: Node) double
        +distanceMetersTo(lat: double, lon: double) double
    }
    class Edge {
        <<record>>
        +index : int
        +dbId : long
        +from : int
        +to : int
        +lengthM : double
        +freeFlowKmh : double
        +name : String
    }
    class EdgeState {
        <<record>>
        +congestion : double
        +closed : boolean
        +signalDelaySec : double
        +incidentFactor : double
        +updatedAtMs : long
        +free() EdgeState$
    }
    class VehicleProfile {
        <<record>>
        +type : String
        +congestionRelief : double
        +signalRelief : double
        +maxKmh : double
        +AMBULANCE : VehicleProfile$
        +FIRE_TRUCK : VehicleProfile$
    }
    class RoadGraph {
        -nodes : Node[]
        -edges : Edge[]
        -outgoing : List~Edge~[]
        -states : AtomicReferenceArray~EdgeState~
        -version : long

        +nodeCount() int
        +edgeCount() int

        +outgoing(nodeIndex: int) List~Edge~
        +node(index: int) Node
        +edge(index: int) Edge
        +state(edgeIndex: int) EdgeState
        +update(edgeIndex: int, state: EdgeState) void
        +nodeIndexOf(dbId: long) int
        +edgeIndexOf(dbId: long) int
        +nearestNode(lat: double, lon: double) int
        +version() long
    }
    class CostFunction {
        <<interface>>
        +seconds(edge: Edge, state: EdgeState, vehicle: VehicleProfile) double
    }
    class EmergencyCost {
        +seconds(edge: Edge, state: EdgeState, vehicle: VehicleProfile) double
    }
    class DistanceOnlyCost {
        +seconds(edge: Edge, state: EdgeState, vehicle: VehicleProfile) double
    }
    class RouteResult {
        <<record>>
        +edgeIds : List~Integer~
        +seconds : double
        +meters : double
        +exploredNodes : int
    }
    class AStarRouter {
        +route(graph: RoadGraph, src: int, dst: int, vehicle: VehicleProfile, cost: CostFunction, penalty: Map~Integer,Double~, useHeuristic: boolean) Optional~RouteResult~
    }
    class AlternativeRouteFinder {
        -router : AStarRouter
        -cost : CostFunction
        -maxOverlap : double
        -maxSlowdown : double
        +find(graph: RoadGraph, src: int, dst: int, vehicle: VehicleProfile, maxRoutes: int) List~RouteResult~
    }

    RoadGraph "1" *-- "1..*" Node
    RoadGraph "1" *-- "1..*" Edge
    RoadGraph "1" *-- "1..*" EdgeState
    CostFunction <|.. EmergencyCost
    CostFunction <|.. DistanceOnlyCost
    AStarRouter ..> CostFunction
    AStarRouter ..> RouteResult
    AlternativeRouteFinder --> AStarRouter
```

### 6.2 Rutas, tráfico, incidentes y recálculo (paquetes `service` y `traffic`)

`RoadConditionService` es el único que modifica el grafo. `TrafficProvider` aísla de dónde viene el tráfico: en la fase 1 lo genera el simulador y en la fase 2 una fuente externa, sin cambiar el resto (*Protected Variations*).

```mermaid
---
config:
  markdownAutoWrap: false
---
classDiagram
    direction LR

    class GraphLoader {
        -nodeDao : RoadNodeDAO
        -segmentDao : RoadSegmentDAO
        -trafficLightDao : TrafficLightDAO
        +load() RoadGraph
    }
    class RouteService {
        -graph : RoadGraph
        -router : AStarRouter
        -alternatives : AlternativeRouteFinder
        -cost : CostFunction
        -explainer : RouteExplanationBuilder
        -routeDao : RouteDAO
        +calculateOptions(from: GeoPoint, to: GeoPoint, vehicleTypeId: long) List~RouteOption~
        +estimateSeconds(from: GeoPoint, to: GeoPoint, vehicleTypeId: long) double
        +isWithinNetwork(point: GeoPoint) boolean
        +saveForMission(emergencyId: long, vehicleId: long, hospitalId: Long, options: List~RouteOption~) List~Route~
    }
    class RouteExplanationBuilder {
        +CONGESTED_FROM : double$
        +explain(option: RouteResult, best: RouteResult, graph: RoadGraph) String
        +toOption(rank: int, route: RouteResult, best: RouteResult, graph: RoadGraph) RouteOption
    }
    class RouteOption {
        <<record>>
        +rank : int
        +type : RouteType
        +path : List~GeoPoint~
        +segmentIds : List~Long~
        +distanceKm : double
        +estimatedMinutes : double
        +trafficLights : int
        +congestedSegments : int
        +explanation : String
    }
    class RoadConditionService {
        -graph : RoadGraph
        -trafficProvider : TrafficProvider
        -incidentDao : RoadIncidentDAO
        -readingDao : TrafficReadingDAO
        -events : EventPublisher
        -clock : Clock
        +loadActiveIncidents() void
        +refreshTraffic() void
        +applyIncident(incident: RoadIncident) void
        +removeIncident(incident: RoadIncident) void
    }
    class TrafficProvider {
        <<interface>>
        +poll() List~EdgeUpdate~
        +source() TrafficSource
    }
    class EdgeUpdate {
        <<record>>
        +edgeIndex : int
        +congestion : double
        +signalDelaySec : double
    }
    class SimulatedTrafficProvider {
        -edgeCount : int
        -forced : Map~Integer,Double~
        -random : Random
        -clock : Clock

        +poll() List~EdgeUpdate~
        +forceCongestion(edgeIndex: int, congestion: double) void
        +source() TrafficSource
    }
    class ExternalTrafficProvider {
        <<fase 2>>
        +poll() List~EdgeUpdate~
        +source() TrafficSource
    }
    class IncidentService {
        -incidentDao : RoadIncidentDAO
        -roadConditions : RoadConditionService
        -transactions : TransactionManager
        -auditService : AuditService
        -events : EventPublisher
        -clock : Clock
        +report(request: IncidentRequest, origin: IncidentOrigin, session: UserSession) RoadIncident
        +activate(id: long, blocksSegment: boolean, session: UserSession) void
        +reject(id: long, session: UserSession) void
        +close(id: long, session: UserSession) void
        +listPending() List~RoadIncident~
        +listActive() List~RoadIncident~
    }
    class IncidentRequest {
        <<record>>
        +segmentId : long
        +type : IncidentType
        +severity : Severity
        +description : String
    }
    class RouteMonitor {
        -routeDao : RouteDAO
        -routeService : RouteService
        -trackingService : TrackingService
        -alertService : AlertService
        -events : EventPublisher
        -improvementThreshold : double
        -minimumSecondsSaved : double
        +onRoadConditionsChanged(event: DomainEvent) void
        +recalculate(route: Route) Optional~Route~
    }
    class RoadGraph
    class AlternativeRouteFinder
    class TrackingService

    TrafficProvider <|.. SimulatedTrafficProvider
    TrafficProvider <|.. ExternalTrafficProvider
    TrafficProvider ..> EdgeUpdate
    GraphLoader ..> RoadGraph : builds
    RoadConditionService --> RoadGraph : único que escribe
    RoadConditionService --> TrafficProvider
    RouteService --> RoadGraph : lee
    RouteService --> AlternativeRouteFinder
    RouteService --> RouteExplanationBuilder
    RouteService ..> RouteOption
    IncidentService --> RoadConditionService
    IncidentService ..> IncidentRequest
    RouteMonitor --> RouteService
    RouteMonitor --> TrackingService
```

### 6.3 Simulación y seguimiento (paquetes `simulation` y `service`)

`VehiclePositionSource` cumple el mismo papel que `TrafficProvider` para las posiciones: hoy las produce `VehicleMovementSimulator` y en la fase 2 un GPS real. La interfaz se define en `service` (quien la usa) y la implementa `simulation` (SOLID: inversión de dependencias).

```mermaid
---
config:
  markdownAutoWrap: false
---
classDiagram
    direction LR

    class SimulationEngine {
        -scheduler : ScheduledExecutorService
        -tickMillis : long
        -roadConditions : RoadConditionService
        -tracking : TrackingService
        -running : boolean
        +start() void
        +stop() void
        +isRunning() boolean
        +tick() void
    }
    class TrackingService {
        -positionSource : VehiclePositionSource
        -vehicleDao : EmergencyVehicleDAO
        -locationDao : VehicleLocationDAO
        -events : EventPublisher
        -clock : Clock
        +refreshPositions() void
        +currentPosition(vehicleId: long) GeoPoint
    }
    class VehiclePositionSource {
        <<interface>>
        +poll() List~VehicleLocation~
    }
    class VehicleMovementSimulator {
        -graph : RoadGraph
        -routeDao : RouteDAO
        -clock : Clock
        +poll() List~VehicleLocation~
        +follow(vehicleId: long, route: Route) void
        +stopFollowing(vehicleId: long) void
    }
    class GpsPositionSource {
        <<fase 2>>
        +poll() List~VehicleLocation~
    }
    class DemoScenario {
        <<interface>>
        +name() String
        +apply() void
    }
    class RoadClosureScenario {
        -incidentService : IncidentService
        -segmentId : long
        +name() String
        +apply() void
    }
    class RushHourScenario {
        -trafficProvider : SimulatedTrafficProvider
        +name() String
        +apply() void
    }
    class RoadConditionService
    class IncidentService
    class SimulatedTrafficProvider

    VehiclePositionSource <|.. VehicleMovementSimulator
    VehiclePositionSource <|.. GpsPositionSource
    DemoScenario <|.. RoadClosureScenario
    DemoScenario <|.. RushHourScenario
    IncidentService --> RoadConditionService
    TrackingService --> VehiclePositionSource
    SimulationEngine --> RoadConditionService
    SimulationEngine --> TrackingService
    RoadClosureScenario --> IncidentService
    RushHourScenario --> SimulatedTrafficProvider
```

**Cambios respecto al diseño del documento consolidado (Parte IV):**

- **`Node` y `Edge` tienen `dbId`.** El grafo usa índices `0..n-1`, pero necesita volver al `id` de Oracle para guardar `RUTA_TRAMO`.
- **`EdgeState` incorpora `incidentFactor`.** Así un incidente puede penalizar un tramo sin cerrarlo.
- **`EdgeUpdate` ya no tiene `closed`.** El simulador no cierra tramos (regla RN-06).
- **`AlternativeRoutesService` se llama ahora `AlternativeRouteFinder`.** Así no se confunde con la capa Service.
- **Hay un `DistanceOnlyCost` nuevo.** Permite comparar en la sustentación la ruta más corta con la más rápida.
- **`RoadConditionService` es el único que modifica el grafo.** Combina tráfico e incidentes según la sección 5 de `docs/convenciones-base-datos.md`.

---

## 7. Eventos de tiempo real y excepciones

```mermaid
---
config:
  markdownAutoWrap: false
---
classDiagram
    direction LR

    %% ===== EVENTS (package event) =====
    class DomainEvent {
        <<interface>>
        +occurredAt() Instant
    }
    class EventPublisher {
        <<interface>>
        +publish(event: DomainEvent) void
    }
    class EventBus {
        <<interface>>
        +subscribe(type: Class~E~, listener: Consumer~E~) Subscription
    }
    class Subscription {
        <<interface>>
        +cancel() void
    }
    class InMemoryEventBus {
        -registrations : List~Registration~
        +publish(event: DomainEvent) void
        +subscribe(type: Class~E~, listener: Consumer~E~) Subscription
        +subscriberCount() int
    }
    class EmergencyRegistered {
        <<record>>
        +emergencyId : long
        +occurredAt : Instant
    }
    class EmergencyStatusChanged {
        <<record>>
        +emergencyId : long
        +status : EmergencyStatus
        +occurredAt : Instant
    }
    class UnitDispatched {
        <<record>>
        +emergencyId : long
        +vehicleId : long
        +assignmentId : long
        +occurredAt : Instant
    }
    class TrafficChanged {
        <<record>>
        +changedEdges : Set~Integer~
        +graphVersion : long
        +occurredAt : Instant
    }
    class IncidentActivated {
        <<record>>
        +incidentId : long
        +segmentId : long
        +occurredAt : Instant
    }
    class VehiclePositionUpdated {
        <<record>>
        +vehicleId : long
        +location : GeoPoint
        +source : PositionSource
        +occurredAt : Instant
    }
    class RouteRecalculated {
        <<record>>
        +emergencyId : long
        +oldRouteId : long
        +newRouteId : long
        +secondsSaved : double
        +occurredAt : Instant
    }

    EventPublisher <|-- EventBus
    EventBus <|.. InMemoryEventBus
    EventBus ..> Subscription
    DomainEvent <|.. EmergencyRegistered
    DomainEvent <|.. EmergencyStatusChanged
    DomainEvent <|.. UnitDispatched
    DomainEvent <|.. TrafficChanged
    DomainEvent <|.. IncidentActivated
    DomainEvent <|.. VehiclePositionUpdated
    DomainEvent <|.. RouteRecalculated

    %% ===== EXCEPTIONS (package exception) =====
    class RuntimeException
    class RutaVidaException
    class ValidationException
    class NotFoundException
    class BusinessRuleException
    class DataAccessException
    class AccessDeniedException

    RuntimeException <|-- RutaVidaException
    RutaVidaException <|-- ValidationException
    RutaVidaException <|-- NotFoundException
    RutaVidaException <|-- BusinessRuleException
    RutaVidaException <|-- DataAccessException
    RutaVidaException <|-- AccessDeniedException
```

| Evento | Lo publica | Lo escuchan |
|---|---|---|
| `EmergencyRegistered` | `EmergencyService` | Vista (mapa y lista) |
| `UnitDispatched` | `DispatchService` | Vista, `AlertService`, `VehicleMovementSimulator` |
| `EmergencyStatusChanged` | `DispatchService` | Vista |
| `TrafficChanged` | `RoadConditionService` | `RouteMonitor`, vista (tramos congestionados) |
| `IncidentActivated` | `IncidentService` | `RouteMonitor`, vista |
| `VehiclePositionUpdated` | `TrackingService` | Vista (mueve el ícono de la unidad) |
| `RouteRecalculated` | `RouteMonitor` | Vista (nueva ruta), `AlertService` |

---

## 8. Capas Controller, View y App

### 8.1 Controladores

Un controlador por grupo de casos de uso (*GRASP Controller*). Devuelven `OperationResult` para que la vista muestre el mensaje sin manejar excepciones.

```mermaid
---
config:
  markdownAutoWrap: false
---
classDiagram
    direction LR

    class SessionContext {
        -current : UserSession
        +start(session: UserSession) void
        +current() UserSession
        +end() void
        +isActive() boolean
    }
    class OperationResult~T~ {
        <<record>>
        +success : boolean
        +message : String
        +value : T
        +ok(value: T) OperationResult~T~$
        +error(message: String) OperationResult~T~$
    }
    class LoginController {
        -authService : AuthService
        -session : SessionContext
        +login(username: String, password: char[]) OperationResult~UserSession~
        +logout() void
    }
    class EmergencyController {
        -emergencyService : EmergencyService
        -session : SessionContext
        +register(request: EmergencyRequest) OperationResult~Emergency~
        +listActive() List~Emergency~
        +history(emergencyId: long) OperationResult~EmergencyHistory~
    }
    class DispatchController {
        -dispatchService : DispatchService
        -hospitalService : HospitalService
        -session : SessionContext
        +suggestUnits(emergencyId: long) OperationResult~List~UnitSuggestion~~
        +dispatch(emergencyId: long, vehicleId: long) OperationResult~EmergencyAssignment~
        +advance(assignmentId: long, next: AssignmentStatus) OperationResult~Void~
        +rankHospitals(assignmentId: long, requiredService: String) OperationResult~List~HospitalOption~~
        +startTransfer(assignmentId: long, hospitalId: long) OperationResult~Void~
        +close(emergencyId: long) OperationResult~Void~
    }
    class RouteController {
        -routeService : RouteService
        -session : SessionContext
        +options(emergencyId: long, vehicleId: long) OperationResult~List~RouteOption~~
    }
    class IncidentController {
        -incidentService : IncidentService
        -session : SessionContext
        +report(request: IncidentRequest) OperationResult~RoadIncident~
        +activate(incidentId: long, blocksSegment: boolean) OperationResult~Void~
        +reject(incidentId: long) OperationResult~Void~
        +listPending() List~RoadIncident~
    }
    class DashboardController {
        -dashboardService : DashboardService
        +summary() DashboardSummary
    }
    class AlertController {
        -alertService : AlertService
        -session : SessionContext
        +unread() List~Alert~
        +markAsRead(alertId: long) void
    }
    class SimulationController {
        -engine : SimulationEngine
        -scenarios : List~DemoScenario~
        -session : SessionContext
        +start() OperationResult~Void~
        +stop() OperationResult~Void~
        +scenarioNames() List~String~
        +run(scenarioName: String) OperationResult~Void~
    }
    class UserController {
        -userService : UserService
        -session : SessionContext
        +create(request: UserRequest) OperationResult~User~
        +list() List~User~
        +deactivate(userId: long) OperationResult~Void~
    }
    class FleetController {
        -fleetService : FleetService
        -session : SessionContext
        +listAll() List~EmergencyVehicle~
        +changeStatus(vehicleId: long, status: VehicleOperationalStatus) OperationResult~Void~
    }
    class HospitalController {
        -hospitalService : HospitalService
        -session : SessionContext
        +listOperational() List~Hospital~
        +updateService(offering: HospitalServiceOffering) OperationResult~Void~
    }

    LoginController --> SessionContext
    LoginController --> AuthService
    EmergencyController --> SessionContext
    DispatchController --> SessionContext
    LoginController ..> OperationResult~T~
    EmergencyController ..> OperationResult~T~
    DispatchController ..> OperationResult~T~
```

### 8.2 Vista y arranque

La vista solo usa controladores. `AppContext` crea todos los objetos e inyecta las dependencias.

```mermaid
---
config:
  markdownAutoWrap: false
---
classDiagram
    direction LR

    class LoginView {
        <<view>>
    }
    class MainView {
        <<view>>
    }
    class MapView {
        <<view>>
    }
    class EmergencyFormView {
        <<view>>
    }
    class DispatchView {
        <<view>>
    }
    class AppContext {
        -database : DatabaseConnection
        -jdbc : JdbcTransactionManager
        -events : EventBus
        -clock : Clock
        -session : SessionContext
        -adminBootstrap : AdminBootstrapService
        -loginController : LoginController
        +create() AppContext$
        +transactions() TransactionManager
        +events() EventBus
        +clock() Clock
        +database() DatabaseConnection
        +session() SessionContext
        +loginController() LoginController
        +adminBootstrap() AdminBootstrapService
    }
    class Main {
        +main(args: String[]) void$
    }
    class DatabaseCheck {
        +main(args: String[]) void$
    }
    class CreateAdminUser {
        +main(args: String[]) void$
    }
    class LoginController
    class EmergencyController
    class DispatchController
    class RouteController
    class IncidentController
    class DashboardController
    class AlertController
    class SimulationController

    LoginView ..> LoginController
    MainView ..> DashboardController
    MainView ..> AlertController
    MainView ..> SimulationController
    MapView ..> RouteController
    MapView ..> IncidentController
    EmergencyFormView ..> EmergencyController
    DispatchView ..> DispatchController
    MainView *-- MapView
    Main ..> AppContext : crea
    Main ..> LoginView : muestra
    CreateAdminUser ..> AppContext : usa
    CreateAdminUser ..> AdminBootstrapService : crea el administrador
    AppContext ..> LoginController : crea e inyecta
    AppContext ..> EmergencyController : crea e inyecta
    AppContext ..> DispatchController : crea e inyecta
```

Los controladores atrapan las `RutaVidaException` y devuelven un `OperationResult` con el mensaje en español de `Messages`. La vista no maneja excepciones técnicas.

La vista es web (decisión DT-01 en `docs/decisiones-tecnicas.md`). Las clases de esta sección son provisionales: en el backend se reemplazarán por los adaptadores REST del paquete `api`, que solo pueden usar controladores, DTO, modelo y eventos (`ArchitectureTest`). La página web (HTML, CSS, JavaScript y Leaflet) no forma parte de este diagrama de clases Java.

---

## 9. Estado de implementación

| Clase o grupo | Paquete | Estado |
|---|---|---|
| `CodedEnum` y las 19 enumeraciones | `model.enums` | **Programado y probado** |
| `TransactionManager`, `ConnectionProvider`, `ConnectionFactory`, `JdbcTransactionManager` | `dao`, `dao.jdbc` | **Programado y probado** |
| `DomainEvent`, `EventPublisher`, `EventBus`, `InMemoryEventBus` | `event` | **Programado y probado** |
| `RutaVidaException`, `ValidationException`, `NotFoundException`, `BusinessRuleException`, `DataAccessException` | `exception` | **Programado** |
| `DatabaseConnection`, `AppContext`, `DatabaseCheck` | `util`, `app` | **Programado y probado** contra Oracle XE 18c |
| `RowMapper`, `AbstractJdbcDAO` | `dao.jdbc` | **Programado y probado** contra Oracle XE 18c (8 pruebas de integración) |
| `AccessDeniedException` | `exception` | **Programado y probado** |
| `CreateAdminUser`, `AdminBootstrapService`, `ClientIdentifierConnectionFactory` y el inicio de sesión conectado en `AppContext` | `app`, `service`, `dao.jdbc` | **Programado y probado** contra Oracle XE 18c (A3) |
| `GeoPoint`, `Locatable`, `UserSession`, `ValidationUtil`, `EmergencyType`, `VehicleType`, `Permission`, `Role`, `User`, `EmergencyStation`, `Hospital`, `EmergencyVehicle`, `Emergency` | `model`, `util` | **Programado y probado** |
| Otras entidades de la sección 1, pendientes de implementación | `model` | Diseñado |
| `AuditLog` | `model` | **Programado y probado** (creado por Andrés con el contrato de S1; ver `docs/contratos-entidades.md`, sección 3) |
| `UserDAO`, `RoleDAO`, `AuditDAO` (interfaces) | `dao` | **Programado** (A2) |
| `JdbcUserDAO`, `JdbcRoleDAO`, `JdbcAuditDAO` | `dao.jdbc` | **Programado y probado** contra Oracle XE 18c (17 pruebas de integración) |
| Demás interfaces DAO y `JdbcXxxDAO` | `dao`, `dao.jdbc` | Diseñado |
| `AuthService`, `AuditService`, `PasswordHasher`, `Pbkdf2PasswordHasher` | `service`, `util` | **Programado y probado** (S2) |
| Demás servicios y DTO | `service`, `dto` | Diseñado |
| Motor de rutas | `graph` | **Programado y probado** |
| Tráfico | `traffic` | **Programado y probado** |
| `RouteOption`, `RouteExplanationBuilder` | `dto`, `service` | **Programado y probado** (K3) |
| Simulación | `simulation` | Diseñado |
| Eventos concretos (`UnitDispatched`…) | `event` | Diseñado |
| `LoginController`, `SessionContext`, `OperationResult`, `Messages` | `controller`, `dto`, `util` | **Programado y probado** (S3) |
| Demás controladores | `controller` | Diseñado |
| Vista web y adaptadores REST | `view` → `api`, `frontend/` | Decidido (web); pendiente de programar |

Estos son los contratos que el equipo debe respetar. Si alguien necesita cambiar una firma pública, lo acuerda con el equipo y actualiza este diagrama en el mismo Pull Request.
