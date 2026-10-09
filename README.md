# Intelligent Airport Baggage Handling and Routing System

## 1. Project Title
Intelligent Airport Baggage Handling and Routing System - Vaanam Airport

## 2. Problem Statement
Baggage staff at Vaanam Airport currently use manual lists to route bags. This causes bags with urgent loading deadlines to get stuck behind less urgent bags. Transfer bags often miss their flights. If a conveyor belt breaks, it is hard to manually recalculate the route. Furthermore, data is lost when the program exits. Lastly, any operator could previously bypass security rules, requiring a strict Role-Based Access Control (RBAC) system.

## 3. Objective
To build a Java-based simulation that tracks baggage, prioritizes them based on their loading deadlines using a Priority Queue, routes them through a conveyor network using Dijkstra's shortest path algorithm, checks for missed cutoffs, dynamically reroutes upon conveyor failure, and stores records in an SQLite database. Additionally, to enforce strict authentication and authorization using 5 distinct roles and securely hash passwords with PBKDF2.

## 4. System Scope
The system provides a comprehensive terminal interface for airport staff. It can:
- Store and manage Flights, Baggage, and Loading Bays using SQLite for persistence.
- Securely authenticate users using hashed passwords and salts (PBKDF2WithHmacSHA256).
- Assign responsibilities through strict Role-Based Access Control (RBAC) across 5 roles.
- Enforce backend security checks so unauthorized roles cannot bypass the menu UI.
- Log sensitive actions into an Audit Log.
- Register and assign baggage to flights.
- Prioritize baggage processing correctly based on their loading cutoff time.
- Calculate the shortest path for baggage using Dijkstra's algorithm.
- Manage conveyor network availability, allowing for dynamic rerouting.

## 5. Classes and Their Responsibilities
1. **`Main.java`**: Contains the Scanner, authentication loop, user management, and dynamic menus.
2. **`BaggageService.java`**: Core controller for baggage operations. Includes backend authorization checks (`auth.requirePermission()`) and triggers audit logs.
3. **`Auth.java`**: Houses the `User` class, `Role` enum, and `AuthManager` for checking permissions, hashing (PBKDF2), and verifying passwords.
4. **`DatabaseManager.java`**: Handles SQLite JDBC database creation, loading, and saving of application records, users, and audit logs.
5. **`Baggage.java`**: Contains `Baggage` base class and concrete `OriginBag`, `TransferBag`.
6. **`Flight.java`**: Contains `Flight` and `LoadingBay`.
7. **`Conveyor.java`**: Contains `ConveyorRoutePlanner` (Dijkstra algorithm implementation).

## 6. Roles and Permission Matrix
1. **ADMIN**: Superuser. Can view all records, manage user accounts (create, change role, reset password, activate/deactivate), change flights/conveyors, and perform operational tasks. Cannot directly grant security clearance to baggage.
2. **SUPERVISOR**: Can view records, manage conveyor availability, change flight loading bays, advance time, and view routing history. Cannot manage users or grant clearance.
3. **BAGGAGE_OPERATOR**: Can register bags, assign to flights, process the priority queue, and track status. Cannot change flights, conveyors, or grant clearance.
4. **SECURITY_OFFICER**: Only views pending clearance bags and grants/revokes operator clearance with a required reason.
5. **AUDITOR**: Read-only access to all operational data and audit logs.

## 7. Authentication and Security
- **Initial Setup**: If no accounts exist in the `Users` table, the system forces the creation of an initial `ADMIN` account.
- **Hashing**: Passwords are NEVER stored in plain text. They are hashed using Java's built-in `SecretKeyFactory` with `PBKDF2WithHmacSHA256`, 10,000 iterations, and a unique 16-byte random salt per user.
- **Backend Authorization**: The `AuthManager#requirePermission()` method is explicitly called at the top of every sensitive backend method in `BaggageService` and `Main`. If an unauthorized role triggers it, a `SecurityException` is thrown and caught cleanly by the terminal loop.
- **Audit Logs**: Actions like login attempts, user creation, baggage processing, clearance changes, and configuration changes are written to the `AuditLog` table.

## 8. Execution Instructions
Ensure you have Java installed (Java 8 or higher).
The SQLite JDBC driver is stored in the `lib` folder.

**To Compile:**
```bash
cmd /c "javac -cp .;lib/* src\*.java"
```
Or in PowerShell:
```powershell
javac -cp ".;lib/*" src/*.java
```

**To Run:**
```bash
cmd /c "java -cp .;lib/*;src Main"
```
Or in PowerShell:
```powershell
java -cp ".;lib/*;src" Main
```

## 9. Testing Guide
1. **First Run**: Start the app with a fresh database. It will prompt you to create the initial admin account.
2. **Login**: Log in with the admin credentials.
3. **Manage Users (Option 19)**: Create a `SUPERVISOR`, `BAGGAGE_OPERATOR`, `SECURITY_OFFICER`, and `AUDITOR`. Log out (Option 20).
4. **Baggage Operator Test**: Log in as the operator. Register a bag (Option 9) and assign it (Option 10). Notice they cannot clear it. Log out.
5. **Security Test**: Log in as the security officer. View pending bags (Option 12), then grant clearance (Option 13) and provide a reason. Log out.
6. **Supervisor Test**: Log in as supervisor. Attempt to change a flight bay (Option 17). This will succeed.
7. **Auditor Test**: Log in as auditor. You can view all records and audit logs (Option 8) but have no write permissions.
8. **Security Enforcement**: If you attempt to modify code to bypass the menu, the backend `requirePermission()` will block you and log the failure.
