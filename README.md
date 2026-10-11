# Vaanam Airport Intelligent Baggage Handling and Routing System
**Second Project Review (Review 2) — College Mini-Project 10**

A compact, deterministic, and academically grounded airport baggage simulation for a fictional regional hub called **Vaanam Airport**. Implemented with a pure Java backend, persistent SQLite database via JDBC, and a student-built web interface using HTML, CSS, and Vanilla JavaScript.

---

## 1. Problem Statement & Domain Context

Manual baggage processing in regional airports causes serious operational bottlenecks:

1. **Out-of-Order Waiting**: Bags for an imminent flight often wait behind bags for flights departing hours later because check-in queues do not prioritize by departure deadline.
2. **Missed Onward Loading Windows**: Transfer and connecting baggage from arriving flights frequently miss tight connecting departure windows.
3. **Conveyor Hardware Jams**: A mechanical breakdown along a critical conveyor belt interrupts planned routing, causing baggage piles without automated alternate-path recalculation.
4. **Flight Gate & Bay Reassignments**: Gate changes or loading bay switches leave baggage moving toward obsolete positions unless routes dynamically update.
5. **Security Clearance Violations**: Baggage without verified operator clearance must never enter conveyor dispatch to an aircraft.
6. **Cutoff Violations**: Bags whose remaining transit time exceeds the flight's loading deadline must not be reported as loaded.

### Simulation Scope & Assumptions
- **Academic Simulation**: This system is a deterministic college simulation demonstrating Java OOP, data structures, and algorithms. It does not claim to interface with physical hardware, live airport sensors, or external aviation APIs.
- **Conveyor Network Topology**: 10–12 core conveyor nodes connected by directed weighted edges with positive transit times.
- **Scheduled Flights**: 4 regional flights with distinct loading cutoffs and assigned aircraft loading bays.
- **Dataset**: Coherent set of 32 synthetic baggage records across 12 passengers demonstrating all normal and failure scenarios.
- **Two-Way Workflows**: Models Outgoing (Check-in → Bay), Incoming (Aircraft Unload → Baggage Claim Carousel), and Transfer (Transit Hub → Connecting Departure Bay).

---

## 2. System Architecture & Compact File Structure

The project conforms strictly to the **4–5 main Java files** backend structure and **3 frontend files**:

```
Mini-Project-I/
├── src/
│   ├── Main.java             # Entry point, HTTP server initialization, REST handlers & JSON parser
│   ├── Models.java           # Consolidated domain models: Baggage, Flight, Bay, Conveyor, User, Passenger
│   ├── DatabaseManager.java  # SQLite persistence via JDBC, schema initialization, audit logging
│   ├── BaggageService.java   # PriorityQueue comparator, Dijkstra graph routing, clock, cutoff checks
│   ├── Auth.java             # PBKDF2 password hashing, session tokens, role permissions, passenger privacy
│   └── TestRunner.java       # Standalone automated verification suite (16 test suites, 51 assertions)
├── web/
│   ├── index.html            # Clean student-built UI, tables, modals, route tracking
│   ├── style.css             # Light grey background, restrained navy accents, functional tables
│   └── app.js                # Vanilla JavaScript state management and backend REST integration
├── lib/
│   ├── sqlite-jdbc.jar       # SQLite JDBC Driver
│   ├── slf4j-api.jar         # Logging API
│   └── slf4j-simple.jar      # Simple logger implementation
├── data/
│   └── airport.db            # Persistent SQLite database
└── README.md                 # Project documentation and review report
```

### Main Java Components Overview

| File | Responsibilities |
|---|---|
| [`src/Main.java`](file:///c:/Users/yuthg/Mini-Project-I/src/Main.java) | Application startup on port 8080 using `com.sun.net.httpserver.HttpServer`. Serves static files from `web/` and routes REST endpoints (`/api/login`, `/api/baggage`, `/api/flights`, `/api/conveyors`, `/api/users`, `/api/audit`, etc.). |
| [`src/Models.java`](file:///c:/Users/yuthg/Mini-Project-I/src/Models.java) | Domain classes: `Role`, `PassengerCategory`, `BaggageDirection`, `BaggageStatus`, `User`, `Passenger`, `Baggage` (`OriginBag`, `TransferBag`, `IncomingBag`), `LoadingBay`, `Flight`, `ConveyorSegment`, `DijkstraNode`, `RoutingResult`. |
| [`src/DatabaseManager.java`](file:///c:/Users/yuthg/Mini-Project-I/src/DatabaseManager.java) | SQLite JDBC integration with prepared statements. Manages schemas (`Users`, `Passenger`, `Flight`, `LoadingBay`, `ConveyorSegment`, `Baggage`, `RoutingRecord`, `AuditLog`), transactions, and configurable database URL for test isolation. |
| [`src/BaggageService.java`](file:///c:/Users/yuthg/Mini-Project-I/src/BaggageService.java) | Core algorithms: deterministic `PriorityQueue` with tie-breaking comparator, `ConveyorRoutePlanner` implementing Dijkstra's algorithm, conveyor toggle & failure rerouting, simulated clock, and 32-record synthetic dataset. |
| [`src/Auth.java`](file:///c:/Users/yuthg/Mini-Project-I/src/Auth.java) | Cryptographic PBKDF2WithHmacSHA256 password hashing (10,000 iterations, 16-byte random salt), session tokens, certified clearance authority checks, and server-side passenger privacy enforcement. |
| [`src/TestRunner.java`](file:///c:/Users/yuthg/Mini-Project-I/src/TestRunner.java) | Standalone verification runner testing all 16 evaluation suites using isolated test database `data/test_airport.db`. |

---

## 3. Core Data Structures & Algorithms

### 3.1 Directed Weighted Graph (Conveyor Network)
The airport conveyor network is modeled as a directed graph $G = (V, E)$, where $V$ represents processing and transfer checkpoints, and $E$ represents directed conveyor belts with positive transit times (weights in minutes).

- **14 Nodes**: `CHECK-IN`, `SORTING`, `TRANSFER-A`, `TRANSFER-B`, `CENTRAL`, `BAY-AREA`, `BAY-A`, `BAY-B`, `BAY-C`, `BAY-D`, `UNLOAD-POINT`, `CLAIM-AREA`, `CLAIM-CAROUSEL-1`, `CLAIM-CAROUSEL-2`.
- **Adjacency Representation**: Implemented as `HashMap<String, ArrayList<ConveyorSegment>>`.

### 3.2 Dijkstra's Shortest Path Algorithm
The routing planner finds the minimum transit-time path from the bag's current conveyor position to its destination loading bay or carousel:
1. Initializes distances to $\infty$, start node to $0$.
2. Uses a min-priority queue (`PriorityQueue<DijkstraNode>`).
3. **Hardware Jam Exclusion**: Skips any edge where `edge.isAvailable() == false`.
4. Reconstructs the optimal route by tracking predecessors (`predecessors.get(curr)`).
5. **Dynamic Rerouting**: When a conveyor segment fails, the system automatically recalculates the route from the bag's *last confirmed node*. If no path exists, the bag is flagged with status `EXCEPTION` and transit time `-1`.

### 3.3 PriorityQueue for Deadline-Based Processing
Eligible baggage awaiting dispatch is processed using a custom `Comparator<Baggage>` enforcing the exact tie-breaking hierarchy:
1. **Earliest Flight Loading Cutoff**: The bag with the smallest departure deadline cutoff is prioritized first.
2. **Transfer Urgency**: Transfer baggage takes precedence over origin baggage when cutoffs are equal.
3. **Passenger Category Rank**: Rank 1 (`FIRST_CLASS`) > Rank 2 (`BUSINESS`) > Rank 3 (`ECONOMY`).
4. **Arrival Order**: Earlier baggage check-in sequence number is processed first.
5. **Baggage ID**: Lexicographical tie-breaker (`b1.getBagId().compareTo(b2.getBagId())`) ensuring completely deterministic ordering.

### 3.4 HashMap & ArrayDeque
- **`HashMap`**: Provides $O(1)$ lookup for baggage by ID, flight by flight number, passenger by passenger ID, and loading bay by bay ID.
- **`ArrayDeque`**: Represents the FIFO queue for physical conveyor entry points before routing.

---

## 4. Role-Based Access Control (RBAC) & Privacy

The system implements 3 primary user-facing roles enforced entirely in Java backend logic (`Auth.java` and `Main.java`):

| Role | Permitted Actions | Restrictions |
|---|---|---|
| **ADMIN** | System setup, create staff accounts, reset passwords, activate/deactivate accounts, view all manifest data, flight/bay reassignment, audit logs, clean data reset. | **Cannot** grant security clearance merely by virtue of admin role (requires explicit certified clearance authority). |
| **BAGGAGE_MANAGER** | Register baggage, assign flights, step and run conveyor simulation, view clearance queue, grant/reject clearance (with authority), toggle conveyor segments offline/online to test rerouting. | **Cannot** create admin accounts, alter user roles, or bypass cutoff and screening validation. |
| **PASSENGER** | Log in, search by tag, view strictly their own checked bags, inspect live conveyor checkpoints and dynamic ETA. | **Cannot** access another passenger's records (rejected with 403 Forbidden). Cannot access staff endpoints or alter flights/conveyors. |

### Password Security & Session Management
- **PBKDF2**: Passwords are never stored in plaintext. They are hashed using `PBKDF2WithHmacSHA256` with a unique 16-byte cryptographically secure random salt and 10,000 iterations.
- **Server-Managed Sessions**: Authenticated requests exchange a session cookie mapped to a server-side `User` object. Role claims from the browser are never trusted.

---

## 5. Workflows: Outgoing, Incoming, and Transfer

```mermaid
graph TD
    subgraph Outgoing Baggage Workflow
        A1[Check-in: REGISTERED] --> A2{Security Screening}
        A2 -- Pass --> A3[Status: QUEUED in PriorityQueue]
        A2 -- Prohibited Item --> A4[Status: REJECTED]
        A3 --> A5[Conveyor Transit: IN_TRANSIT]
        A5 --> A6{At Loading Bay: Check Cutoff}
        A6 -- ETA <= Cutoff --> A7[Status: LOADED]
        A6 -- ETA > Cutoff --> A8[Status: MISSED_CUTOFF]
    end

    subgraph Incoming Baggage Workflow
        B1[Aircraft Arrival: UNLOAD-POINT] --> B2[Inbound Conveyor Transit]
        B2 --> B3[Status: AT_COLLECTION_POINT]
        B3 --> B4[Claim Carousel: COLLECTED]
    end

    subgraph Transfer Baggage Workflow
        C1[Transit Inbound Hub: TRANSFER-A] --> C2[Transfer Security Verification]
        C2 --> C3[Dijkstra Route to Connecting Bay]
        C3 --> C4[Validated Onward Cutoff -> LOADED]
    end
```

---

## 6. Synthetic Demonstration Dataset

The system includes a pre-seeded, consistent dataset of **32 synthetic baggage records**, **4 flights**, and **12 passengers**:

### Flights
| Flight | Origin | Destination | Departure | Loading Cutoff | Assigned Bay |
|---|---|---|---|---|---|
| **AI101** | MAA | Delhi (DEL) | 11:00 AM (660m) | 10:30 AM (630m) | BAY-A |
| **6E202** | MAA | Mumbai (BOM) | 10:45 AM (645m) | 10:15 AM (615m) | BAY-B |
| **SG303** | MAA | Bengaluru (BLR) | 11:15 AM (675m) | 10:45 AM (645m) | BAY-C |
| **UK404** | MAA | Dubai (DXB) | 10:50 AM (650m) | 10:20 AM (620m) | BAY-D |

### Sample Baggage Scenarios
- **Multi-Bag Passengers**:
  - `P101` (Ramesh Kumar): 4 bags (`BAG-101` awaiting clearance, `BAG-102` queued, `BAG-122` loaded at Bay A, `BAG-131` in transit).
  - `P102` (Priya Sharma): 3 bags (`BAG-103` in transit, `BAG-104` loaded at Bay B, `BAG-123` queued).
  - `P108` (Sneha Reddy): 3 bags (`BAG-110` queued, `BAG-111` loaded at Bay D, `BAG-126` in transit).
- **Incoming Arrivals**: `BAG-114`, `BAG-116`, `BAG-117`, `BAG-119`, `BAG-129` routed to claim carousels.
- **Transfer Connections**: `BAG-115`, `BAG-118`, `BAG-130` connecting through transit hubs to onward departure bays.
- **Missed Cutoff Demonstration**: `BAG-120` (Rajesh Patel) arrives after the flight cutoff and is flagged `MISSED_CUTOFF`.
- **Screening Rejection**: `BAG-121` flagged `REJECTED` due to prohibited lithium battery pack detected during screening.

---

## 7. Database Schema (SQLite JDBC)

Persisted in [`data/airport.db`](file:///c:/Users/yuthg/Mini-Project-I/data/airport.db):

```mermaid
erDiagram
    USERS {
        string username PK
        string passwordHash
        string passwordSalt
        string role
        int clearanceAuthority
        int active
        string linkedPassengerId
    }
    PASSENGER {
        string passengerId PK
        string fullName
        string username
        string flightId
        string category
        string contact
    }
    BAGGAGE {
        string bagId PK
        string passengerId FK
        string passengerName
        string bagType
        string direction
        string flightId FK
        int categoryRank
        int arrivalOrder
        string currentLocation
        string destinationNode
        int loadingCutoff
        int operatorClearance
        string clearanceReason
        string status
        int estimatedTime
        string originAirport
        string destinationAirport
    }
    FLIGHT {
        string flightId PK
        string origin
        string destination
        int departureTime
        int loadingCutoff
        string assignedBay FK
    }
    LOADING_BAY {
        string bayId PK
        string assignedFlightId
    }
    CONVEYOR_SEGMENT {
        string fromNode PK
        string toNode PK
        int processingTime
        int available
    }
    AUDIT_LOG {
        int logId PK
        string timestamp
        string actor
        string actionType
        string targetRecord
        int simulatedTime
        string status
        string details
    }

    USERS ||--o| PASSENGER : links
    PASSENGER ||--o{ BAGGAGE : owns
    FLIGHT ||--o{ BAGGAGE : carries
    FLIGHT ||--o| LOADING_BAY : occupies
```

---

## 8. Verification & Test Suite Results

The automated test runner (`TestRunner.java`) tests all core requirements across 16 test suites using an isolated test database (`data/test_airport.db`).

### Verification Test Summary
```
=======================================================
   VAANAM AIRPORT BAGGAGE SYSTEM - VERIFICATION TEST   
=======================================================

[Test Suite 1] Initial Administrator Setup and Login
  [PASS] Default admin user exists
  [PASS] Admin account has ADMIN role
  [PASS] Admin authenticates successfully with correct PBKDF2 password
  [PASS] Admin login rejected with incorrect password

[Test Suite 2] Administrator Account Creation
  [PASS] Admin-created staff account saved in database
  [PASS] Role correctly assigned as BAGGAGE_MANAGER
  [PASS] Clearance authority flag properly stored

[Test Suite 3] Baggage Manager Login and Operations
  [PASS] Manager user exists
  [PASS] Manager has BAGGAGE_MANAGER role
  [PASS] Manager possesses BAGGAGE_MANAGER permission
  [PASS] Baggage Manager successfully registered baggage record

[Test Suite 4] Passenger Login and Access to Own Baggage
  [PASS] Passenger account 'ramesh' exists
  [PASS] Role is PASSENGER
  [PASS] Account linked to Passenger ID P101
  [PASS] Passenger P101 can retrieve their baggage
  [PASS] All retrieved bags belong strictly to passenger P101

[Test Suite 5] Rejection of Access to Other Passengers' Records
  [PASS] Java backend strictly rejects passenger attempting to access another passenger's records

[Test Suite 6] Baggage Registration with Passenger & Flight
  [PASS] Baggage record contains valid Passenger ID
  [PASS] Baggage record contains correct passenger name
  [PASS] Baggage linked to validated flight UK404
  [PASS] Destination node resolves to flight loading bay BAY-D

[Test Suite 7] Tracking by Baggage ID
  [PASS] Baggage BAG-101 found by ID
  [PASS] Current location is confirmed (CHECK-IN)
  [PASS] Valid status reported (AWAITING_CLEARANCE)

[Test Suite 8] Multiple Bags Belonging to One Passenger
  [PASS] Passenger P102 has multiple associated bags (3 bags)
  [PASS] Passenger P101 has multiple associated bags (5 bags)

[Test Suite 9] Incoming, Outgoing, and Transfer Workflows
  [PASS] Outgoing workflow baggage identified
  [PASS] Incoming workflow baggage identified
  [PASS] Transfer workflow baggage identified
  [PASS] Incoming bag originates at UNLOAD-POINT
  [PASS] Incoming bag routes to Claim Carousel
  [PASS] Transfer bag originates at TRANSFER-A

[Test Suite 10] Dijkstra Route Calculation and ETA
  [PASS] Shortest path calculated successfully
  [PASS] Optimal shortest route processing time is 19 minutes
  [PASS] Route traverses from CHECK-IN to BAY-A

[Test Suite 11] Conveyor Failure and Dynamic Rerouting
  [PASS] Dijkstra finds alternate path via transfer hub
  [PASS] Rerouted processing time is 21 minutes
  [PASS] Rerouted path avoids failed segment and utilizes transit bypass
  [PASS] Route restores to optimal 19 minutes when segment comes online

[Test Suite 12] Loading Cutoff Validation
  [PASS] On-time baggage transitions to LOADED
  [PASS] Late baggage flagged MISSED_CUTOFF and prevented from loading

[Test Suite 13] Database Persistence Across Restarts
  [PASS] Baggage record persisted in SQLite and reloaded upon restart
  [PASS] Passenger relationship persisted
  [PASS] Passenger name persisted

[Test Suite 14] Role Restrictions and Clearance Authority
  [PASS] Passenger prohibited from processing baggage
  [PASS] Admin without certified clearance authority cannot grant security clearance
  [PASS] Certified Baggage Manager successfully grants clearance

[Test Suite 15] Duplicate ID Rejection and Audit Logging
  [PASS] Duplicate Baggage ID rejected with exception
  [PASS] Audit log contains persisted operational records (12 logs)

[Test Suite 16] Sample Data Consistency Verification
  [PASS] Sample dataset contains at least 30 synthetic baggage records (36 records)
  [PASS] Every sample baggage record contains valid passenger, location, and status attributes

=======================================================
FINAL VERIFICATION RESULTS: 51 Passed, 0 Failed.
=======================================================
```

---

## 9. Setup & Run Instructions

### Prerequisites
- Java Development Kit (JDK 17 or higher, tested with JDK 25)
- Windows PowerShell, macOS Terminal, or Linux Shell

### 1. Compile the Java Source Files
```powershell
javac -d bin -cp "lib/*" src/*.java
```

### 2. Run the Automated Verification Test Suite
```powershell
java -cp "bin;lib/*" TestRunner
```

### 3. Start the Application Server
```powershell
java -cp "bin;lib/*" Main
```

### 4. Open in Web Browser
Navigate to:
```
http://localhost:8080
```

### Initial Accounts for Evaluation
- **Administrator**: `admin` / `admin123`
- **Baggage Manager**: `manager` / `manager123`
- **Passenger**: `ramesh` / `pass123` (Linked to Passenger P101)
- **Passenger**: `priya` / `pass123` (Linked to Passenger P102)

---

## 10. Object-Oriented Programming (OOP) Principles

- **Encapsulation**: Models keep fields private (`private String bagId; private BaggageStatus status;`) and expose guarded mutators (e.g. `setClearance` enforces status transitions, `assignFlight` validates cutoff).
- **Inheritance**: Base class `Baggage` is extended by `OriginBag`, `TransferBag`, and `IncomingBag` to model distinct workflow behaviors without duplicating code.
- **Polymorphism**: The priority processing queue treats all subclasses as `Baggage`, invoking polymorphic getters (`isTransferBag()`, `isIncomingBag()`) during priority comparison and routing.
- **Abstraction**: `ConveyorRoutePlanner` abstracts graph representation and Dijkstra shortest-path calculations behind simple method calls (`getShortestPath`, `markConveyorUnavailable`).

---

## 11. Sustainable Development Goals (SDG) Alignment

- **SDG 9: Industry, Innovation, and Infrastructure**: Automated routing algorithms reduce mechanical conveyor idle time and optimize electric belt usage.
- **SDG 11: Sustainable Cities and Communities**: Reliable regional airport baggage flow reduces passenger flight delays, missed connections, and airport congestion.
- **SDG 12: Responsible Consumption and Production**: Proactive failure rerouting prevents baggage pileups and mechanical wear on overloaded conveyor segments.

---

## 12. Viva Presentation & Demonstration Checklist

1. **Architecture Explanation**: Walk through the 5 core Java files in `src/` and explain how models, routing, database, and auth are cleanly partitioned.
2. **Dijkstra Routing Demonstration**: Show route `CHECK-IN` → `BAY-A` (19 mins). In the Conveyor Network tab, disable `SORTING` → `CENTRAL`. Show how the system dynamically recalculates the route via `TRANSFER-A` (21 mins).
3. **PriorityQueue Tie-Breaking**: Demonstrate why `BAG-103` (Cutoff 10:15 AM) is dispatched ahead of `BAG-102` (Cutoff 10:30 AM), proving deadline-first prioritization.
4. **Role Security & Passenger Privacy**: Log in as `ramesh` and show that only his 4 bags are visible. Show that accessing other passenger records is blocked by Java backend authorization.
5. **Persistence**: Advance the simulated clock, restart the Java application, and show that all baggage statuses and audit logs remain intact in SQLite.
