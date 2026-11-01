# Vaanam Airport Baggage Handling and Routing System

An academic Java mini-project that models how airport baggage is registered, screened, prioritized, routed through conveyor segments, and tracked. The example airport is fictional. A Java HTTP server provides the backend and serves a plain HTML, CSS, and JavaScript interface. SQLite stores the project data; Redis is optional for selected read requests.

> **Scope:** This is a local simulation for coursework and demonstration. Baggage movement is recorded through the interface. The project does not connect to airport equipment, live sensors, security scanners, airline systems, or external flight services, and it is not intended for production use.

## Contents

- [Project at a glance](#project-at-a-glance)
- [Features](#features)
- [How the system works](#how-the-system-works)
- [Project structure](#project-structure)
- [Requirements](#requirements)
- [Build and run](#build-and-run)
- [Accounts and first-run setup](#accounts-and-first-run-setup)
- [Configuration](#configuration)
- [Data and database](#data-and-database)
- [HTTP API](#http-api)
- [Verification](#verification)
- [Known limits and data notes](#known-limits-and-data-notes)
- [Troubleshooting](#troubleshooting)

## Project at a glance

| Area | Implementation |
|---|---|
| Backend | Java and the JDK `HttpServer` module |
| Frontend | HTML, CSS, and browser JavaScript; no frontend framework or package manager |
| Persistent storage | SQLite through the bundled JDBC driver |
| Routing | Directed, weighted conveyor graph and Dijkstra’s shortest-path algorithm |
| Queue | Java `PriorityQueue` with a deterministic deadline-first comparator |
| Optional cache | Redis cache-aside helper; the application still runs when Redis is unavailable |
| Web address | `http://localhost:8080` |
| Included database snapshot | 32 baggage records, 12 passengers, 4 flights, 4 bays, 16 conveyor segments, and 14 modeled nodes |

## Features

- Register outgoing, incoming, and transfer baggage and associate it with passenger and flight records.
- Record screening approval or rejection. Outgoing and transfer bags require a user with clearance authority before they can be advanced.
- Order queued departure bags by earliest loading cutoff, with fixed tie-breakers. Fuzzy urgency is considered only after the cutoff, transfer type, and passenger category tie.
- Find a minimum-time route through available directed conveyor segments using Dijkstra’s algorithm and a min-heap.
- Compare a route estimate with the loading cutoff and mark a bag `MISSED_CUTOFF` when it cannot arrive in time.
- Record baggage movement one conveyor node at a time. If a conveyor segment becomes unavailable, recalculate active routes from each bag’s current confirmed location.
- Update flight loading bays and cutoffs. Pending baggage assigned to an updated flight is recalculated where applicable.
- Track a bag by tag or view the signed-in passenger’s own bags. Staff can view the all-bag manifest and a conveyor graph. The browser refreshes tracking data from the API every 2.5 seconds; it does not receive live sensor events.
- Manage passenger, flight, bay, user, and audit records through role-specific screens.
- Calculate a small rule-based urgency score from remaining cutoff time, estimated route time, transfer urgency, and simulated waiting time. The score is supporting information and never overrides an earlier cutoff.
- Optionally cache selected baggage tracking, flight, bay, and conveyor responses in Redis. SQLite remains the persistent record.

## How the system works

### Main workflow

1. A staff member registers a bag or selects an existing bag and assigns a flight when needed.
2. A clearance-authorized operator approves or rejects screening for an outgoing or transfer bag.
3. The service orders eligible queued bags using the priority rules below.
4. The route planner finds the shortest available path to the assigned bay or claim carousel.
5. The service checks the estimated route time against the applicable loading cutoff.
6. A staff member records each movement step. The saved location, status, route, and history are then visible in the tracking screens.
7. If a conveyor or flight-bay assignment changes, the service recalculates affected routes from the bag’s current confirmed location.

### Queue ordering

The comparator applies these keys in order:

1. Earliest loading cutoff.
2. Transfer baggage before origin baggage when cutoffs tie.
3. Passenger category: First Class, Business, then Economy.
4. Higher fuzzy urgency score, only after the earlier keys tie.
5. Earlier arrival order.
6. Baggage ID in lexical order.

The cutoff is always the primary key. Incoming bags and bags without a departure cutoff are not eligible for the departure urgency score.

### Conveyor routing

The conveyor network is a directed graph: an edge from one node to another does not imply a reverse edge. Each edge has a positive travel-time weight and an availability state. Dijkstra’s algorithm ignores unavailable edges and reports no route when the destination cannot be reached. The included example models 14 nodes, including check-in, sorting, transfer hubs, central routing, loading bays, unload, and claim carousels.

### Roles and access

| Role | Typical access |
|---|---|
| `ADMIN` | Manage accounts and system records, update flights and bays, inspect audit logs, and perform a confirmed clean reset. Admin status alone does not grant baggage-clearance authority. |
| `BAGGAGE_MANAGER` | Register and process baggage, manage passengers, update flights, advance the simulated clock, and change conveyor availability. Clearance decisions also require the separate clearance-authority flag. |
| `PASSENGER` | View and track baggage linked to that passenger account. The Java backend checks ownership for individual tracking requests. |

Passwords are stored as PBKDF2-HMAC-SHA256 hashes with random salts. Sessions are kept in memory and use an `HttpOnly` cookie. Sessions are cleared when the server process stops.

### Fuzzy urgency

`FuzzyUrgency.java` maps four values to LOW, MEDIUM, and HIGH memberships, evaluates a fixed rule set, and returns a 0–100 score and label. The inputs are minutes to cutoff, estimated route time, transfer urgency, and time spent waiting in the simulation. The score is recalculated when relevant baggage, route, flight, or simulated-time data changes. It is a small hand-written rule system, not a trained model.

### Redis cache

Redis is an optional cache-aside layer. The default key prefix is `vaanam:` and the default expiry is 45 seconds. Selected tracking, flight, bay, and conveyor reads can return an `X-Cache: HIT` or `X-Cache: MISS` response header. The application writes SQLite first and invalidates related cache entries after supported updates. If Redis is stopped or unreachable, the application falls back to its normal data path; Redis is not required to start the server.

## Project structure

```text
Mini-Project-I/
├── src/
│   ├── Main.java             # HTTP server, static files, and API handlers
│   ├── Models.java           # Roles, baggage, passenger, flight, bay, and graph models
│   ├── Auth.java             # Password hashing, sessions, and permission checks
│   ├── BaggageService.java   # Baggage workflow, queue, simulation, and routing planner
│   ├── DatabaseManager.java  # SQLite schema, loading, saving, and audit records
│   ├── FuzzyUrgency.java     # Rule-based urgency score
│   ├── RedisCache.java       # Optional Redis client and cache operations
│   └── TestRunner.java       # Standalone verification runner
├── web/
│   ├── index.html            # Login and role-based application screens
│   ├── style.css             # Interface styling
│   └── app.js                # Browser behavior and API requests
├── lib/                      # SQLite JDBC and SLF4J JAR files
├── data/
│   ├── airport.db            # Included SQLite database snapshot
│   └── airport.db.*          # Backup snapshots supplied with the repository
├── bin/                      # Local Java compiler output; ignored by Git
└── README.md
```

## Requirements

- JDK 17 or newer.
- A modern browser.
- Redis only if you want to demonstrate cache hits; it is optional.

The SQLite JDBC and SLF4J dependencies are already in `lib/`. No Maven, Gradle, Node.js, or Docker setup is required.

## Build and run

Run all commands from the repository root so the application can find `web/`, `lib/`, and `data/` by their relative paths.

### Windows PowerShell

```powershell
New-Item -ItemType Directory -Force bin | Out-Null
$javaSources = Get-ChildItem src -Filter *.java | ForEach-Object FullName
javac -cp "lib/*" -d bin $javaSources
java -cp "bin;lib/*" Main
```

### macOS or Linux

```sh
mkdir -p bin
javac -cp 'lib/*' -d bin src/*.java
java -cp 'bin:lib/*' Main
```

When the server starts, open [http://localhost:8080](http://localhost:8080). Stop it with `Ctrl+C` in the terminal. The port is currently fixed at `8080` in `src/Main.java`.

### Run the verification suite

Compile the project first, then use the platform-specific classpath separator:

```powershell
# Windows PowerShell
java -cp "bin;lib/*" TestRunner
```

```sh
# macOS or Linux
java -cp 'bin:lib/*' TestRunner
```

`TestRunner` runs 19 suites covering authentication, role access, baggage workflows, routing, cutoffs, persistence, fuzzy priority, and Redis behavior. It deletes `data/test_airport.db` before and after the run; do not store unrelated data at that path.

## Accounts and first-run setup

The included `data/airport.db` snapshot contains local demonstration accounts. These credentials are for local coursework evaluation only:

| Role | Username | Password |
|---|---|---|
| Administrator | `admin` | `admin123` |
| Baggage manager | `manager` | `manager123` |
| Passenger | `ramesh` | `pass123` |
| Passenger | `priya` | `pass123` |

Change or remove demonstration credentials before sharing a deployment, and do not expose this HTTP server to an untrusted network. The application is not designed as a production authentication service.

If using a database with no active administrator, the Account Setup Portal can create the first admin account. The server forces the first account to the `ADMIN` role and does not grant clearance authority automatically. After an administrator exists, only an admin can provision accounts. Passenger accounts should be linked to the appropriate passenger record.

## Configuration

The only optional runtime configuration is for Redis:

| Environment variable | Default | Purpose |
|---|---|---|
| `REDIS_HOST` | `127.0.0.1` | Redis host name or address |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_PASSWORD` | Empty | Password for Redis `AUTH`, when required |
| `REDIS_TTL` | `45` | Cache expiry in seconds |

To use Redis, start it in another terminal before launching the Java server:

```sh
redis-server --port 6379
```

The application reads Redis settings from environment variables at startup. If Redis is unavailable, tracking and other application functions continue without the cache.

## Data and database

`DatabaseManager.java` stores the project data in `data/airport.db` by default. The path is relative to the current working directory. Database initialization creates the project tables and adds selected missing columns when older database files are opened.

| Table | Purpose |
|---|---|
| `Users` | Login hashes, roles, active state, and clearance authority |
| `Passenger` | Passenger identity and account association |
| `Flight` | Origin, destination, departure, cutoff, and assigned bay |
| `LoadingBay` | Bay-to-flight assignment |
| `ConveyorSegment` | Directed edge, travel time, and availability |
| `Baggage` | Passenger/flight link, direction, location, status, clearance, and estimate |
| `RoutingRecord` | Recorded movement and route history |
| `AuditLog` | Selected account and operational actions |

The simulation clock starts at 09:00 when the server process starts and advances through staff actions. Baggage, flight, passenger, conveyor, routing, and audit records are stored in SQLite. Make a copy of `data/airport.db` before experimenting with the application. The administrator’s **Clean Reset** action clears the database and reseeds the demonstration records; it also removes user accounts, so the first-admin setup must be completed again afterward.

## HTTP API

The browser uses JSON request and response bodies and a `session_id` cookie. Except for login, first-admin registration, static files, and the read-only simulated-time endpoint, API calls require an active session. Passengers are restricted to their own baggage by server-side checks.

### Authentication and overview

| Method and path | Access | Purpose / request fields |
|---|---|---|
| `POST /api/login` | Public | Log in with `username`, `password`; sets the session cookie. |
| `POST /api/logout` | Session | End the current session. |
| `GET /api/session` | Session | Return the signed-in account and role. |
| `POST /api/register` | First admin, then admin only | Create an account with `username`, `password`, `role`, optional `clearanceAuthority`, and optional `linkedPassengerId`. |
| `GET /api/stats` | Session | Dashboard counts and current summary. |
| `GET /api/time` | Public | Read the simulated time. |
| `POST /api/time/advance` | Admin or baggage manager | Advance the simulation; body: `minutes`. |

### Baggage, passengers, and tracking

| Method and path | Access | Purpose / request fields |
|---|---|---|
| `GET /api/baggage` | Session | List baggage. Optional query parameters: `q`, `direction`, `status`. Passenger accounts receive only their own records. |
| `POST /api/baggage/register` | Admin or baggage manager | Register a bag. Fields: `bagId`, `passengerId`, `passengerName`, `direction`, `categoryRank`, `flightId`, `originAirport`, `destinationAirport`, `destinationNode`. |
| `POST /api/baggage/assign` | Admin or baggage manager | Assign a bag to a flight: `bagId`, `flightId`. |
| `POST /api/baggage/clearance` | Clearance-authorized user | Approve or reject screening: `bagId`, `approved`, optional `reason`. |
| `POST /api/baggage/process` | Admin or baggage manager | Process the next queued bag; optionally pass `bagId` to process a specific bag. |
| `POST /api/baggage/step` | Admin or baggage manager | Advance a bag by one route step: `bagId`. |
| `POST /api/baggage/collect` | Session, ownership/role checks apply | Record collection: `bagId`. |
| `GET /api/baggage/track?id={bagId}` | Session | Return one bag’s status, route, estimate, and movement history. Passengers can track only their own bags. |
| `GET /api/tracking/live` | Session | Return the caller’s baggage (all baggage for staff), conveyor segments, and simulated time. |
| `GET /api/passengers` | Admin or baggage manager | List passenger records. |
| `POST /api/passengers` | Admin or baggage manager | Save a passenger: `passengerId`, `fullName`, optional `username`, `flightId`, `categoryRank`, and `contactInfo`. |

Directions are `OUTGOING`, `INCOMING`, or `TRANSFER`. Passenger category ranks are `1` for First Class, `2` for Business, and `3` for Economy.

### Flights, conveyors, and administration

| Method and path | Access | Purpose / request fields |
|---|---|---|
| `GET /api/flights` | Session | List flights and assigned bays. |
| `POST /api/flights/update` | Admin or baggage manager | Update a flight’s `assignedBay` and/or `cutoff`; body also includes `flightId`. |
| `GET /api/bays` | Session | List loading bays and assignments. |
| `GET /api/conveyors` | Session | List directed conveyor segments and availability. |
| `POST /api/conveyors/toggle` | Admin or baggage manager | Change a segment’s availability: `fromNode`, `toNode`, `available`. |
| `GET /api/users` | Admin | List user accounts and roles. |
| `POST /api/users/update` | Admin | Update a user using `username`, `action` (`status`, `role`, `clearance`, or `password`), and `value`. |
| `GET /api/audit` | Admin | Read audit events. |
| `GET /api/cache` | Admin | Read Redis availability and cache counters. |
| `POST /api/system/reset` | Admin | Clear and reseed the database. Requires `confirm: true`; removes user accounts. |

Protected endpoints return an error response for missing sessions or insufficient permissions. Tracking, flights, bays, and conveyor responses may include `X-Cache: HIT` or `X-Cache: MISS` when Redis caching is enabled.

### Example: log in and track a bag

```sh
curl -i -c session.txt \
  -H 'Content-Type: application/json' \
  -d '{"username":"ramesh","password":"pass123"}' \
  http://localhost:8080/api/login

curl -b session.txt \
  'http://localhost:8080/api/baggage/track?id=BAG-101'
```

The tracking response contains the current location and status, estimated route time, current and remaining route, urgency fields, and recorded movement history. Keep `session.txt` local and delete it when finished.

## Verification

`src/TestRunner.java` provides an executable verification suite without a separate testing framework. It covers:

- Administrator setup, account creation, login, and role permissions.
- Passenger access to their own bags and rejection of another passenger’s records.
- Registration, tag lookup, duplicate IDs, and multiple bags per passenger.
- Incoming, outgoing, and transfer workflows.
- Dijkstra routing, alternate routes after a conveyor is disabled, and route restoration.
- Loading-cutoff handling and SQLite persistence.
- Audit logging, fuzzy urgency calculations, and cutoff-first queue ordering.
- Redis hits, invalidation, and fallback when Redis is unavailable.

Run it using the platform-specific command in [Build and run](#build-and-run). The runner uses an isolated database and removes that file before and after execution.

## Known limits and data notes

- All passenger, baggage, flight, and conveyor information is synthetic. The graph represents a small demonstration network, not an airport layout.
- Movement is a manager-recorded simulation. The 2.5-second browser refresh reads the Java API; there are no live belt sensors or external integrations.
- The included `data/airport.db` and the empty-database seed in `BaggageService.java` currently differ. The database snapshot lists MAA flights directly to Delhi, Mumbai, Bengaluru, and Dubai. The Java seed uses destination labels that mention Madurai, Coimbatore, Tiruchirappalli, and Singapore. These places are not separate nodes in the conveyor graph, so the labels do not represent modeled connections. Align the seed and database before presenting the wider routes as implemented.
- The simulated clock resets to 09:00 when the Java process restarts; it is not persisted as a separate database value.
- Redis improves repeat-read behavior only when available. No measured performance comparison is included.
- The service uses plain HTTP and is designed for local demonstration. It has no HTTPS setup, no airline integration, and no production deployment or high-availability configuration.

## Troubleshooting

| Issue | Check |
|---|---|
| `Main` cannot find the web files or database | Start the Java process from the repository root. |
| `Address already in use` on startup | Port `8080` is occupied. Stop the other process or change `PORT` in `src/Main.java`, then recompile. |
| SQLite driver error | Confirm `lib/sqlite-jdbc.jar` is present and included with `-cp "lib/*"`. |
| Redis connection message | Redis is optional. Check `REDIS_HOST` and `REDIS_PORT`, or continue without Redis. |
| Login fails | Confirm the account exists in the active database snapshot. The first-admin setup is available only when there is no active administrator. |
| Reset removed logins | The clean reset removes all users; create a new first administrator through the Account Setup Portal. |
