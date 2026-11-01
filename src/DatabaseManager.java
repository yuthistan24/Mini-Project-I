import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    private static String dbUrl = "jdbc:sqlite:data/airport.db";

    public static void setDatabaseUrl(String url) {
        dbUrl = url;
    }

    public static void resetDatabaseUrl() {
        dbUrl = "jdbc:sqlite:data/airport.db";
    }

    public static Connection connect() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            System.err.println("SQLite JDBC driver not found: " + e.getMessage());
        }
        try {
            File dataDir = new File("data");
            if (!dataDir.exists()) {
                dataDir.mkdirs();
            }
            return DriverManager.getConnection(dbUrl);
        } catch (SQLException e) {
            System.err.println("Database connection error: " + e.getMessage());
            return null;
        }
    }

    public static void initializeDatabase() {
        String createUsersTable = "CREATE TABLE IF NOT EXISTS Users ("
                + "username TEXT PRIMARY KEY,"
                + "passwordHash TEXT NOT NULL,"
                + "passwordSalt TEXT NOT NULL,"
                + "role TEXT NOT NULL,"
                + "clearanceAuthority INTEGER DEFAULT 0,"
                + "active INTEGER DEFAULT 1,"
                + "linkedPassengerId TEXT,"
                + "createdAt DATETIME DEFAULT CURRENT_TIMESTAMP"
                + ");";

        String createPassengerTable = "CREATE TABLE IF NOT EXISTS Passenger ("
                + "passengerId TEXT PRIMARY KEY,"
                + "fullName TEXT NOT NULL,"
                + "username TEXT,"
                + "flightId TEXT,"
                + "categoryRank INTEGER DEFAULT 3,"
                + "contactInfo TEXT"
                + ");";

        String createFlightTable = "CREATE TABLE IF NOT EXISTS Flight ("
                + "flightId TEXT PRIMARY KEY,"
                + "origin TEXT DEFAULT 'MAA',"
                + "destination TEXT NOT NULL,"
                + "departureTime INTEGER DEFAULT 660,"
                + "loadingCutoff INTEGER NOT NULL,"
                + "assignedBay TEXT"
                + ");";

        String createLoadingBayTable = "CREATE TABLE IF NOT EXISTS LoadingBay ("
                + "bayId TEXT PRIMARY KEY,"
                + "assignedFlight TEXT"
                + ");";

        String createConveyorTable = "CREATE TABLE IF NOT EXISTS ConveyorSegment ("
                + "fromNode TEXT NOT NULL,"
                + "toNode TEXT NOT NULL,"
                + "processingTime INTEGER NOT NULL,"
                + "available INTEGER DEFAULT 1,"
                + "PRIMARY KEY (fromNode, toNode)"
                + ");";

        String createBaggageTable = "CREATE TABLE IF NOT EXISTS Baggage ("
                + "bagId TEXT PRIMARY KEY,"
                + "passengerId TEXT NOT NULL,"
                + "passengerName TEXT,"
                + "bagType TEXT,"
                + "direction TEXT NOT NULL,"
                + "flightId TEXT,"
                + "categoryRank INTEGER DEFAULT 3,"
                + "arrivalOrder INTEGER DEFAULT 1,"
                + "originAirport TEXT DEFAULT 'MAA',"
                + "destinationAirport TEXT DEFAULT 'DEL',"
                + "currentLocation TEXT NOT NULL,"
                + "destinationNode TEXT,"
                + "loadingCutoff INTEGER DEFAULT 0,"
                + "operatorClearance INTEGER DEFAULT 0,"
                + "clearanceReason TEXT DEFAULT '',"
                + "status TEXT NOT NULL,"
                + "estimatedTime INTEGER DEFAULT 0,"
                + "queuedSince INTEGER DEFAULT 540,"
                + "createdAt DATETIME DEFAULT CURRENT_TIMESTAMP,"
                + "updatedAt DATETIME DEFAULT CURRENT_TIMESTAMP"
                + ");";

        String createRoutingTable = "CREATE TABLE IF NOT EXISTS RoutingRecord ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "bagId TEXT NOT NULL,"
                + "fromNode TEXT NOT NULL,"
                + "toNode TEXT NOT NULL,"
                + "estimatedTime INTEGER,"
                + "status TEXT,"
                + "simulatedTime INTEGER,"
                + "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP"
                + ");";

        String createAuditTable = "CREATE TABLE IF NOT EXISTS AuditLog ("
                + "logId INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "username TEXT,"
                + "action TEXT NOT NULL,"
                + "targetRecord TEXT,"
                + "simulatedTime INTEGER,"
                + "result TEXT,"
                + "reason TEXT,"
                + "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP"
                + ");";

        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            if (conn != null) {
                stmt.execute(createUsersTable);
                stmt.execute(createPassengerTable);
                stmt.execute(createFlightTable);
                stmt.execute(createLoadingBayTable);
                stmt.execute(createConveyorTable);
                stmt.execute(createBaggageTable);
                stmt.execute(createRoutingTable);
                stmt.execute(createAuditTable);

                // Seamless migrations for existing tables
                addColumnIfNotExists(stmt, "Users", "clearanceAuthority", "INTEGER DEFAULT 0");
                addColumnIfNotExists(stmt, "Users", "linkedPassengerId", "TEXT");
                addColumnIfNotExists(stmt, "Passenger", "contactInfo", "TEXT DEFAULT ''");
                addColumnIfNotExists(stmt, "Flight", "origin", "TEXT DEFAULT 'MAA'");
                addColumnIfNotExists(stmt, "Flight", "departureTime", "INTEGER DEFAULT 660");
                addColumnIfNotExists(stmt, "Baggage", "passengerId", "TEXT DEFAULT 'P101'");
                addColumnIfNotExists(stmt, "Baggage", "passengerName", "TEXT DEFAULT 'Passenger'");
                addColumnIfNotExists(stmt, "Baggage", "originAirport", "TEXT DEFAULT 'MAA'");
                addColumnIfNotExists(stmt, "Baggage", "destinationAirport", "TEXT DEFAULT 'DEL'");
                addColumnIfNotExists(stmt, "Baggage", "createdAt", "TEXT DEFAULT ''");
                addColumnIfNotExists(stmt, "Baggage", "updatedAt", "TEXT DEFAULT ''");
                addColumnIfNotExists(stmt, "Baggage", "queuedSince", "INTEGER DEFAULT 540");
            }
        } catch (SQLException e) {
            System.err.println("Database initialization error: " + e.getMessage());
        }
    }

    private static void addColumnIfNotExists(Statement stmt, String table, String column, String colDef) {
        try {
            stmt.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + colDef + ";");
        } catch (SQLException ignored) {
            // Column already exists
        }
    }

    // ==================== USER MANAGEMENT ====================

    public static void saveUser(User user) {
        String sql = "INSERT OR REPLACE INTO Users (username, passwordHash, passwordSalt, role, clearanceAuthority, active, linkedPassengerId) VALUES(?,?,?,?,?,?,?)";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, user.getUsername());
            pstmt.setString(2, user.getPasswordHash());
            pstmt.setString(3, user.getPasswordSalt());
            pstmt.setString(4, user.getRole().toString());
            pstmt.setInt(5, user.hasClearanceAuthority() ? 1 : 0);
            pstmt.setInt(6, user.isActive() ? 1 : 0);
            pstmt.setString(7, user.getLinkedPassengerId());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error saving user: " + e.getMessage());
        }
    }

    public static User getUser(String username) {
        if (username == null) return null;
        String sql = "SELECT * FROM Users WHERE username = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new User(
                    rs.getString("username"),
                    rs.getString("passwordHash"),
                    rs.getString("passwordSalt"),
                    Role.fromString(rs.getString("role")),
                    rs.getInt("clearanceAuthority") == 1,
                    rs.getInt("active") == 1,
                    rs.getString("linkedPassengerId")
                );
            }
        } catch (SQLException e) {
            System.err.println("Error fetching user: " + e.getMessage());
        }
        return null;
    }

    public static List<User> getAllUsers() {
        List<User> users = new ArrayList<>();
        String sql = "SELECT username, role, clearanceAuthority, active, linkedPassengerId FROM Users ORDER BY username ASC";
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                users.add(new User(
                    rs.getString("username"),
                    "", "", // Hashes withheld for safety
                    Role.fromString(rs.getString("role")),
                    rs.getInt("clearanceAuthority") == 1,
                    rs.getInt("active") == 1,
                    rs.getString("linkedPassengerId")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error listing users: " + e.getMessage());
        }
        return users;
    }

    public static void updateUserStatus(String username, boolean active) {
        String sql = "UPDATE Users SET active = ? WHERE username = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, active ? 1 : 0);
            pstmt.setString(2, username);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error updating user status: " + e.getMessage());
        }
    }

    public static void updateUserRole(String username, Role role) {
        String sql = "UPDATE Users SET role = ? WHERE username = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, role.toString());
            pstmt.setString(2, username);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error updating user role: " + e.getMessage());
        }
    }

    public static void updateUserClearance(String username, boolean authority) {
        String sql = "UPDATE Users SET clearanceAuthority = ? WHERE username = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, authority ? 1 : 0);
            pstmt.setString(2, username);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error updating user clearance authority: " + e.getMessage());
        }
    }

    public static void updateUserPassword(String username, String hash, String salt) {
        String sql = "UPDATE Users SET passwordHash = ?, passwordSalt = ? WHERE username = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, hash);
            pstmt.setString(2, salt);
            pstmt.setString(3, username);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error resetting user password: " + e.getMessage());
        }
    }

    public static int getActiveAdminCount() {
        String sql = "SELECT COUNT(*) FROM Users WHERE role = 'ADMIN' AND active = 1";
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException ignored) {}
        return 0;
    }

    // ==================== PASSENGERS ====================

    public static void savePassenger(Passenger p) {
        String sql = "INSERT OR REPLACE INTO Passenger (passengerId, fullName, username, flightId, categoryRank, contactInfo) VALUES(?,?,?,?,?,?)";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, p.getPassengerId());
            pstmt.setString(2, p.getFullName());
            pstmt.setString(3, p.getUsername());
            pstmt.setString(4, p.getFlightId());
            pstmt.setInt(5, p.getCategory().getRank());
            pstmt.setString(6, p.getContactInfo());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error saving passenger: " + e.getMessage());
        }
    }

    public static Passenger getPassenger(String passengerId) {
        if (passengerId == null) return null;
        String sql = "SELECT * FROM Passenger WHERE passengerId = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, passengerId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new Passenger(
                    rs.getString("passengerId"),
                    rs.getString("fullName"),
                    rs.getString("username"),
                    rs.getString("flightId"),
                    PassengerCategory.fromRank(rs.getInt("categoryRank")),
                    rs.getString("contactInfo")
                );
            }
        } catch (SQLException e) {
            System.err.println("Error fetching passenger: " + e.getMessage());
        }
        return null;
    }

    public static List<Passenger> getAllPassengers() {
        List<Passenger> list = new ArrayList<>();
        String sql = "SELECT * FROM Passenger ORDER BY passengerId ASC";
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Passenger(
                    rs.getString("passengerId"),
                    rs.getString("fullName"),
                    rs.getString("username"),
                    rs.getString("flightId"),
                    PassengerCategory.fromRank(rs.getInt("categoryRank")),
                    rs.getString("contactInfo")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching all passengers: " + e.getMessage());
        }
        return list;
    }

    // ==================== BAGGAGE ====================

    public static void saveBaggage(Baggage bag) {
        String sql = "INSERT OR REPLACE INTO Baggage (bagId, passengerId, passengerName, bagType, direction, flightId, categoryRank, arrivalOrder, originAirport, destinationAirport, currentLocation, destinationNode, loadingCutoff, operatorClearance, clearanceReason, status, estimatedTime, queuedSince, updatedAt) "
                + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?, datetime('now'))";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, bag.getBagId());
            pstmt.setString(2, bag.getPassengerId());
            pstmt.setString(3, bag.getPassengerName());
            pstmt.setString(4, bag.getBagType());
            pstmt.setString(5, bag.getDirection().toString());
            pstmt.setString(6, bag.getAssignedFlight() != null ? bag.getAssignedFlight().getFlightId() : null);
            pstmt.setInt(7, bag.getPassengerCategory().getRank());
            pstmt.setInt(8, bag.getArrivalOrder());
            pstmt.setString(9, bag.getOriginAirport());
            pstmt.setString(10, bag.getDestinationAirport());
            pstmt.setString(11, bag.getCurrentLocation());
            pstmt.setString(12, bag.getDestinationNode());
            pstmt.setInt(13, bag.getLoadingCutoff());
            pstmt.setInt(14, bag.isOperatorCleared() ? 1 : 0);
            pstmt.setString(15, bag.getClearanceReason());
            pstmt.setString(16, bag.getStatus().toString());
            pstmt.setInt(17, bag.getEstimatedTime());
            pstmt.setInt(18, bag.getQueuedSince());
            pstmt.executeUpdate();
            // SQLite is the source of truth: only invalidate after a successful commit.
            RedisCache.invalidateBaggage(bag.getBagId());
        } catch (SQLException e) {
            System.err.println("Error saving baggage: " + e.getMessage());
        }
    }

    public static void loadBaggage(BaggageService service) {
        String sql = "SELECT * FROM Baggage ORDER BY arrivalOrder ASC";
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String bagId = rs.getString("bagId");
                String passengerId = rs.getString("passengerId");
                String passengerName = rs.getString("passengerName");
                String directionStr = rs.getString("direction");
                String flightId = rs.getString("flightId");
                int catRank = rs.getInt("categoryRank");
                int arrOrder = rs.getInt("arrivalOrder");
                String origin = rs.getString("originAirport");
                String destAirport = rs.getString("destinationAirport");
                String currentLoc = rs.getString("currentLocation");
                String destNode = rs.getString("destinationNode");
                int cutoff = rs.getInt("loadingCutoff");
                boolean cleared = rs.getInt("operatorClearance") == 1;
                String clearanceReason = rs.getString("clearanceReason");
                String statusStr = rs.getString("status");
                int estTime = rs.getInt("estimatedTime");
                int queuedSince = rs.getInt("queuedSince");
                String createdAt = rs.getString("createdAt");
                String updatedAt = rs.getString("updatedAt");

                PassengerCategory cat = PassengerCategory.fromRank(catRank);
                BaggageDirection direction = BaggageDirection.OUTGOING;
                if ("INCOMING".equalsIgnoreCase(directionStr)) direction = BaggageDirection.INCOMING;
                else if ("TRANSFER".equalsIgnoreCase(directionStr)) direction = BaggageDirection.TRANSFER;

                Baggage bag;
                if (direction == BaggageDirection.TRANSFER) {
                    bag = new TransferBag(bagId, passengerId, passengerName, cat, arrOrder);
                } else if (direction == BaggageDirection.INCOMING) {
                    bag = new IncomingBag(bagId, passengerId, passengerName, cat, arrOrder);
                } else {
                    bag = new OriginBag(bagId, passengerId, passengerName, cat, arrOrder);
                }

                bag.setOriginAirport(origin);
                bag.setDestinationAirport(destAirport);
                bag.setCurrentLocation(currentLoc);
                bag.setEstimatedTime(estTime);
                bag.setQueuedSince(queuedSince);
                if (createdAt != null) bag.setCreatedAt(createdAt);
                if (updatedAt != null) bag.setUpdatedAt(updatedAt);

                if (flightId != null) {
                    Flight f = service.getFlight(flightId);
                    if (f != null) bag.assignFlight(f);
                }

                if (destNode != null && !destNode.isEmpty()) {
                    bag.setDestinationNode(destNode);
                }
                bag.setLoadingCutoff(cutoff);
                bag.setClearance(cleared, clearanceReason);
                bag.setStatus(BaggageStatus.fromString(statusStr));

                // Synchronize route and ETA from conveyor graph if active
                if (currentLoc != null && destNode != null && !destNode.isEmpty()) {
                    RoutingResult res = service.getConveyorPlanner().getShortestPath(currentLoc, destNode);
                    if (res != null) {
                        bag.setCurrentRoute(res.route);
                        if (bag.getStatus() != BaggageStatus.LOADED && bag.getStatus() != BaggageStatus.COLLECTED
                                && bag.getStatus() != BaggageStatus.MISSED_CUTOFF) {
                            bag.setEstimatedTime(res.totalTime);
                        }
                    }
                }

                service.addLoadedBaggage(bag);
            }
        } catch (SQLException e) {
            System.err.println("Error loading baggage: " + e.getMessage());
        }
    }

    // ==================== FLIGHTS & BAYS ====================

    public static void saveFlight(Flight flight) {
        String sql = "INSERT OR REPLACE INTO Flight (flightId, origin, destination, departureTime, loadingCutoff, assignedBay) VALUES(?,?,?,?,?,?)";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, flight.getFlightId());
            pstmt.setString(2, flight.getOrigin());
            pstmt.setString(3, flight.getDestination());
            pstmt.setInt(4, flight.getDepartureTime());
            pstmt.setInt(5, flight.getLoadingCutoff());
            pstmt.setString(6, flight.getAssignedBay() != null ? flight.getAssignedBay().getBayId() : null);
            pstmt.executeUpdate();
            RedisCache.invalidateFlights();
        } catch (SQLException e) {
            System.err.println("Error saving flight: " + e.getMessage());
        }
    }

    public static void loadFlights(BaggageService service) {
        String sql = "SELECT * FROM Flight";
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String flightId = rs.getString("flightId");
                String origin = rs.getString("origin");
                String destination = rs.getString("destination");
                int depTime = rs.getInt("departureTime");
                int cutoff = rs.getInt("loadingCutoff");
                String bayId = rs.getString("assignedBay");

                Flight f = new Flight(flightId, origin, destination, depTime, cutoff);
                if (bayId != null) {
                    LoadingBay bay = service.getBay(bayId);
                    if (bay != null) {
                        bay.setAssignedFlight(f);
                        f.setAssignedBay(bay);
                    }
                }
                service.addLoadedFlight(f);
            }
        } catch (SQLException e) {
            System.err.println("Error loading flights: " + e.getMessage());
        }
    }

    public static void saveLoadingBay(LoadingBay bay) {
        String sql = "INSERT OR REPLACE INTO LoadingBay (bayId, assignedFlight) VALUES(?,?)";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, bay.getBayId());
            pstmt.setString(2, bay.getAssignedFlight() != null ? bay.getAssignedFlight().getFlightId() : null);
            pstmt.executeUpdate();
            RedisCache.invalidateFlights();
        } catch (SQLException e) {
            System.err.println("Error saving loading bay: " + e.getMessage());
        }
    }

    public static void loadLoadingBays(BaggageService service) {
        String sql = "SELECT * FROM LoadingBay";
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String bayId = rs.getString("bayId");
                LoadingBay bay = service.getBay(bayId);
                if (bay == null) {
                    bay = new LoadingBay(bayId);
                    service.getAllBays().put(bayId, bay);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error loading loading bays: " + e.getMessage());
        }
    }

    // ==================== CONVEYOR NETWORK ====================

    public static void saveConveyorSegment(ConveyorSegment segment) {
        String sql = "INSERT OR REPLACE INTO ConveyorSegment (fromNode, toNode, processingTime, available) VALUES(?,?,?,?)";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, segment.getFromNode());
            pstmt.setString(2, segment.getToNode());
            pstmt.setInt(3, segment.getProcessingTime());
            pstmt.setInt(4, segment.isAvailable() ? 1 : 0);
            pstmt.executeUpdate();
            RedisCache.invalidateConveyors();
            RedisCache.invalidateAllBaggageTracks();
        } catch (SQLException e) {
            System.err.println("Error saving conveyor segment: " + e.getMessage());
        }
    }

    public static void loadConveyors(ConveyorRoutePlanner planner) {
        String sql = "SELECT * FROM ConveyorSegment";
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String from = rs.getString("fromNode");
                String to = rs.getString("toNode");
                int time = rs.getInt("processingTime");
                boolean available = rs.getInt("available") == 1;

                planner.addSegmentFromDB(from, to, time, available);
            }
        } catch (SQLException e) {
            System.err.println("Error loading conveyors: " + e.getMessage());
        }
    }

    // ==================== ROUTING RECORDS ====================

    public static void saveRoutingRecord(String bagId, String fromNode, String toNode, int estTime, String status, int simTime) {
        String sql = "INSERT INTO RoutingRecord (bagId, fromNode, toNode, estimatedTime, status, simulatedTime, timestamp) VALUES(?,?,?,?,?,?, datetime('now'))";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, bagId);
            pstmt.setString(2, fromNode);
            pstmt.setString(3, toNode);
            pstmt.setInt(4, estTime);
            pstmt.setString(5, status);
            pstmt.setInt(6, simTime);
            pstmt.executeUpdate();
            RedisCache.invalidateBaggage(bagId);
        } catch (SQLException e) {
            System.err.println("Error saving routing record: " + e.getMessage());
        }
    }

    public static List<String[]> getRoutingRecords(String bagId) {
        List<String[]> records = new ArrayList<>();
        String sql = "SELECT id, bagId, fromNode, toNode, estimatedTime, status, simulatedTime, timestamp FROM RoutingRecord WHERE bagId = ? ORDER BY id DESC";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, bagId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                records.add(new String[]{
                    String.valueOf(rs.getInt("id")),
                    rs.getString("bagId"),
                    rs.getString("fromNode"),
                    rs.getString("toNode"),
                    String.valueOf(rs.getInt("estimatedTime")),
                    rs.getString("status"),
                    String.valueOf(rs.getInt("simulatedTime")),
                    rs.getString("timestamp")
                });
            }
        } catch (SQLException e) {
            System.err.println("Error reading routing records: " + e.getMessage());
        }
        return records;
    }

    // ==================== AUDIT LOGS ====================

    public static void logAudit(String username, String action, String targetRecord, int simulatedTime, String result, String reason) {
        String sql = "INSERT INTO AuditLog (username, action, targetRecord, simulatedTime, result, reason, timestamp) VALUES(?,?,?,?,?,?, datetime('now'))";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username != null ? username : "SYSTEM");
            pstmt.setString(2, action);
            pstmt.setString(3, targetRecord != null ? targetRecord : "");
            pstmt.setInt(4, simulatedTime);
            pstmt.setString(5, result != null ? result : "SUCCESS");
            pstmt.setString(6, reason != null ? reason : "");
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error saving audit log: " + e.getMessage());
        }
    }

    public static List<String[]> getAuditLogs() {
        List<String[]> logs = new ArrayList<>();
        String sql = "SELECT logId, username, action, targetRecord, simulatedTime, result, reason, timestamp FROM AuditLog ORDER BY logId DESC LIMIT 100";
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                logs.add(new String[]{
                    String.valueOf(rs.getInt("logId")),
                    rs.getString("username"),
                    rs.getString("action"),
                    rs.getString("targetRecord"),
                    String.valueOf(rs.getInt("simulatedTime")),
                    rs.getString("result"),
                    rs.getString("reason"),
                    rs.getString("timestamp")
                });
            }
        } catch (SQLException e) {
            System.err.println("Error reading audit logs: " + e.getMessage());
        }
        return logs;
    }

    public static boolean hasData(String table) {
        String sql = "SELECT COUNT(*) FROM " + table;
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            return false;
        }
        return false;
    }

    // ==================== SAFE DATABASE CLEANUP & RESET ====================

    public static void cleanResetDatabase() {
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            if (conn != null) {
                stmt.execute("DROP TABLE IF EXISTS Baggage;");
                stmt.execute("DROP TABLE IF EXISTS Flight;");
                stmt.execute("DROP TABLE IF EXISTS LoadingBay;");
                stmt.execute("DROP TABLE IF EXISTS ConveyorSegment;");
                stmt.execute("DROP TABLE IF EXISTS RoutingRecord;");
                stmt.execute("DROP TABLE IF EXISTS Users;");
                stmt.execute("DROP TABLE IF EXISTS AuditLog;");
                stmt.execute("DROP TABLE IF EXISTS Passenger;");
            }
        } catch (SQLException e) {
            System.err.println("Clean reset error: " + e.getMessage());
        }
        initializeDatabase();
        seedInitialDataIfEmpty(null);
        RedisCache.invalidateAll();
    }

    public static int validateAndRepairBaggageRecords(BaggageService service) {
        int repairedCount = 0;
        String sql = "SELECT bagId, direction, currentLocation, destinationNode, loadingCutoff, operatorClearance, status FROM Baggage";
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            List<String[]> inconsistent = new ArrayList<>();
            while (rs.next()) {
                String bagId = rs.getString("bagId");
                String dir = rs.getString("direction");
                String loc = rs.getString("currentLocation");
                String dest = rs.getString("destinationNode");
                int cutoff = rs.getInt("loadingCutoff");
                int cleared = rs.getInt("operatorClearance");
                String status = rs.getString("status");

                // Check 1: OUTGOING LOADED but not at destination bay
                if ("OUTGOING".equalsIgnoreCase(dir) && "LOADED".equalsIgnoreCase(status)) {
                    if (dest != null && !dest.equals(loc)) {
                        inconsistent.add(new String[]{bagId, "currentLocation", dest, "LOADED bag location synchronized to assigned bay"});
                    }
                }
                // Check 2: INCOMING COLLECTED/AT_COLLECTION_POINT but not at claim carousel
                if ("INCOMING".equalsIgnoreCase(dir) && ("COLLECTED".equalsIgnoreCase(status) || "AT_COLLECTION_POINT".equalsIgnoreCase(status))) {
                    if (loc == null || (!loc.startsWith("CLAIM-CAROUSEL") && !loc.equals("CLAIM-AREA"))) {
                        String targetLoc = (dest != null && !dest.isEmpty()) ? dest : "CLAIM-CAROUSEL-1";
                        inconsistent.add(new String[]{bagId, "currentLocation", targetLoc, "Claimed incoming baggage location set to claim carousel"});
                    }
                }
                // Check 3: OUTGOING in transit/queued without screening clearance
                if ("OUTGOING".equalsIgnoreCase(dir) && cleared == 0) {
                    if ("IN_TRANSIT".equalsIgnoreCase(status) || "QUEUED".equalsIgnoreCase(status)) {
                        inconsistent.add(new String[]{bagId, "status", "AWAITING_CLEARANCE", "Uncleared baggage halted in AWAITING_CLEARANCE"});
                    }
                }
                // Check 4: OUTGOING or TRANSFER bag with exceeded loading cutoff
                if (("OUTGOING".equalsIgnoreCase(dir) || "TRANSFER".equalsIgnoreCase(dir)) && cutoff > 0 && service != null) {
                    if (service.getSimulatedTime() > cutoff && ("IN_TRANSIT".equalsIgnoreCase(status) || "QUEUED".equalsIgnoreCase(status))) {
                        inconsistent.add(new String[]{bagId, "status", "MISSED_CUTOFF", "Baggage exceeded loading cutoff (" + cutoff + "m)"});
                    }
                }
            }

            for (String[] fix : inconsistent) {
                String updateSql = "UPDATE Baggage SET " + fix[1] + " = ?, updatedAt = datetime('now') WHERE bagId = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
                    pstmt.setString(1, fix[2]);
                    pstmt.setString(2, fix[0]);
                    pstmt.executeUpdate();
                    repairedCount++;
                    RedisCache.invalidateBaggage(fix[0]);
                    logAudit("SYSTEM", "DATA_REPAIR", fix[0], (service != null ? service.getSimulatedTime() : 540), "REPAIRED", fix[3]);
                }
            }
        } catch (SQLException e) {
            System.err.println("Data repair error: " + e.getMessage());
        }
        return repairedCount;
    }

    public static void seedInitialDataIfEmpty() {
        seedInitialDataIfEmpty(null);
    }

    public static void seedInitialDataIfEmpty(BaggageService service) {
        boolean needFlights = !hasData("Flight");
        boolean needBays = !hasData("LoadingBay");
        boolean needConveyors = !hasData("ConveyorSegment");
        boolean needPassengers = !hasData("Passenger");
        boolean needUsers = !hasData("Users");
        boolean needBaggage = !hasData("Baggage");

        if (!needFlights && !needBays && !needConveyors && !needPassengers && !needUsers && !needBaggage) {
            return; // Data already exists; do not overwrite or reset operational changes
        }

        try (Connection conn = connect()) {
            if (conn == null) return;
            conn.setAutoCommit(false);

            // 1. Conveyor Network Segments (Weighted Directed Graph Topology)
            if (needConveyors) {
                String[][] segments = {
                    {"CHECK-IN", "SORTING", "4"},
                    {"SORTING", "TRANSFER-A", "5"},
                    {"SORTING", "TRANSFER-B", "6"},
                    {"SORTING", "CENTRAL", "7"},
                    {"TRANSFER-A", "CENTRAL", "4"},
                    {"TRANSFER-B", "CENTRAL", "3"},
                    {"CENTRAL", "BAY-AREA", "5"},
                    {"BAY-AREA", "BAY-A", "3"},
                    {"BAY-AREA", "BAY-B", "4"},
                    {"BAY-AREA", "BAY-C", "3"},
                    {"BAY-AREA", "BAY-D", "5"},
                    {"UNLOAD-POINT", "CENTRAL", "5"},
                    {"CENTRAL", "CLAIM-AREA", "4"},
                    {"CLAIM-AREA", "CLAIM-CAROUSEL-1", "3"},
                    {"CLAIM-AREA", "CLAIM-CAROUSEL-2", "4"},
                    {"UNLOAD-POINT", "TRANSFER-A", "6"}
                };
                String segSql = "INSERT OR IGNORE INTO ConveyorSegment (fromNode, toNode, processingTime, available) VALUES(?,?,?,1)";
                try (PreparedStatement pstmt = conn.prepareStatement(segSql)) {
                    for (String[] s : segments) {
                        pstmt.setString(1, s[0]);
                        pstmt.setString(2, s[1]);
                        pstmt.setInt(3, Integer.parseInt(s[2]));
                        pstmt.executeUpdate();
                    }
                }
            }

            // 2. Loading Bays
            if (needBays) {
                String[][] bays = {
                    {"BAY-A", "AI101"},
                    {"BAY-B", "6E202"},
                    {"BAY-C", "SG303"},
                    {"BAY-D", "UK404"}
                };
                String baySql = "INSERT OR IGNORE INTO LoadingBay (bayId, assignedFlight) VALUES(?,?)";
                try (PreparedStatement pstmt = conn.prepareStatement(baySql)) {
                    for (String[] b : bays) {
                        pstmt.setString(1, b[0]);
                        pstmt.setString(2, b[1]);
                        pstmt.executeUpdate();
                    }
                }
            }

            // 3. Flights
            if (needFlights) {
                String[][] flights = {
                    {"AI101", "MAA", "Delhi (DEL) via Madurai (IXM)", "660", "630", "BAY-A"},
                    {"6E202", "MAA", "Mumbai (BOM) via Coimbatore (CJB)", "645", "615", "BAY-B"},
                    {"SG303", "MAA", "Bengaluru (BLR) / Tiruchirappalli (TRZ)", "675", "645", "BAY-C"},
                    {"UK404", "MAA", "Dubai (DXB) / Singapore (SIN)", "650", "620", "BAY-D"}
                };
                String flSql = "INSERT OR IGNORE INTO Flight (flightId, origin, destination, departureTime, loadingCutoff, assignedBay) VALUES(?,?,?,?,?,?)";
                try (PreparedStatement pstmt = conn.prepareStatement(flSql)) {
                    for (String[] f : flights) {
                        pstmt.setString(1, f[0]);
                        pstmt.setString(2, f[1]);
                        pstmt.setString(3, f[2]);
                        pstmt.setInt(4, Integer.parseInt(f[3]));
                        pstmt.setInt(5, Integer.parseInt(f[4]));
                        pstmt.setString(6, f[5]);
                        pstmt.executeUpdate();
                    }
                }
            }

            // 4. Passengers
            if (needPassengers) {
                String[][] passengers = {
                    {"P101", "Ramesh Kumar", "ramesh", "AI101", "2", "ramesh.k@example.com"},
                    {"P102", "Priya Sharma", "priya", "6E202", "1", "priya.s@example.com"},
                    {"P103", "Rajesh Patel", "rajesh", "SG303", "3", "rajesh.p@example.com"},
                    {"P104", "Ananya Iyer", "ananya", "UK404", "2", "ananya.i@example.com"},
                    {"P105", "Vikram Singh", "vikram", "AI101", "3", "vikram.s@example.com"},
                    {"P106", "Meera Nair", "meera", "6E202", "3", "meera.n@example.com"},
                    {"P107", "Arun Verma", "arun", "SG303", "2", "arun.v@example.com"},
                    {"P108", "Sneha Reddy", "sneha", "UK404", "1", "sneha.r@example.com"},
                    {"P109", "Karthik Raman", "karthik", "AI101", "3", "karthik.r@example.com"},
                    {"P110", "Divya Krishnan", "divya", "6E202", "3", "divya.k@example.com"},
                    {"P111", "David Miller", "david", "AI101", "2", "david.m@example.com"},
                    {"P112", "Fatima Al-Sayed", "fatima", "UK404", "1", "fatima.a@example.com"}
                };
                String pSql = "INSERT OR IGNORE INTO Passenger (passengerId, fullName, username, flightId, categoryRank, contactInfo) VALUES(?,?,?,?,?,?)";
                try (PreparedStatement pstmt = conn.prepareStatement(pSql)) {
                    for (String[] p : passengers) {
                        pstmt.setString(1, p[0]);
                        pstmt.setString(2, p[1]);
                        pstmt.setString(3, p[2]);
                        pstmt.setString(4, p[3]);
                        pstmt.setInt(5, Integer.parseInt(p[4]));
                        pstmt.setString(6, p[5]);
                        pstmt.executeUpdate();
                    }
                }
            }

            // 5. User Accounts
            if (needUsers) {
                String[][] users = {
                    {"admin", "admin123", "ADMIN", "0", null},
                    {"manager", "manager123", "BAGGAGE_MANAGER", "1", null},
                    {"ramesh", "pass123", "PASSENGER", "0", "P101"},
                    {"priya", "pass123", "PASSENGER", "0", "P102"},
                    {"rajesh", "pass123", "PASSENGER", "0", "P103"},
                    {"ananya", "pass123", "PASSENGER", "0", "P104"},
                    {"vikram", "pass123", "PASSENGER", "0", "P105"},
                    {"meera", "pass123", "PASSENGER", "0", "P106"},
                    {"arun", "pass123", "PASSENGER", "0", "P107"},
                    {"sneha", "pass123", "PASSENGER", "0", "P108"},
                    {"karthik", "pass123", "PASSENGER", "0", "P109"},
                    {"divya", "pass123", "PASSENGER", "0", "P110"},
                    {"david", "pass123", "PASSENGER", "0", "P111"},
                    {"fatima", "pass123", "PASSENGER", "0", "P112"}
                };
                String uSql = "INSERT OR IGNORE INTO Users (username, passwordHash, passwordSalt, role, clearanceAuthority, active, linkedPassengerId) VALUES(?,?,?,?,?,1,?)";
                try (PreparedStatement pstmt = conn.prepareStatement(uSql)) {
                    for (String[] u : users) {
                        String[] hs = AuthManager.hashPassword(u[1]);
                        pstmt.setString(1, u[0]);
                        pstmt.setString(2, hs[0]);
                        pstmt.setString(3, hs[1]);
                        pstmt.setString(4, u[2]);
                        pstmt.setInt(5, Integer.parseInt(u[3]));
                        pstmt.setString(6, u[4]);
                        pstmt.executeUpdate();
                    }
                }
            }

            // 6. Synthetic Baggage Dataset (32 unique records)
            if (needBaggage) {
                Object[][] bags = {
                    {"BAG-101", "P101", "Ramesh Kumar", "Outgoing", "OUTGOING", "AI101", 2, 1, "MAA", "Delhi (DEL) via Madurai (IXM)", "CHECK-IN", "BAY-A", 630, 0, "", "AWAITING_CLEARANCE", 19, 540},
                    {"BAG-102", "P101", "Ramesh Kumar", "Outgoing", "OUTGOING", "AI101", 2, 2, "MAA", "Delhi (DEL) via Madurai (IXM)", "CHECK-IN", "BAY-A", 630, 1, "Screening cleared (Security Hub)", "QUEUED", 19, 535},
                    {"BAG-122", "P101", "Ramesh Kumar", "Outgoing", "OUTGOING", "AI101", 2, 3, "MAA", "Delhi (DEL) via Madurai (IXM)", "BAY-A", "BAY-A", 630, 1, "Screening cleared", "LOADED", 0, 530},
                    {"BAG-131", "P101", "Ramesh Kumar", "Outgoing", "OUTGOING", "AI101", 2, 4, "MAA", "Delhi (DEL) via Madurai (IXM)", "SORTING", "BAY-A", 630, 1, "Screening cleared", "IN_TRANSIT", 15, 540},
                    {"BAG-103", "P102", "Priya Sharma", "Outgoing", "OUTGOING", "6E202", 1, 5, "MAA", "Mumbai (BOM) via Coimbatore (CJB)", "CENTRAL", "BAY-B", 615, 1, "Priority screening cleared", "IN_TRANSIT", 9, 535},
                    {"BAG-104", "P102", "Priya Sharma", "Outgoing", "OUTGOING", "6E202", 1, 6, "MAA", "Mumbai (BOM) via Coimbatore (CJB)", "BAY-B", "BAY-B", 615, 1, "Priority screening cleared", "LOADED", 0, 530},
                    {"BAG-123", "P102", "Priya Sharma", "Outgoing", "OUTGOING", "6E202", 1, 7, "MAA", "Mumbai (BOM) via Coimbatore (CJB)", "CHECK-IN", "BAY-B", 615, 1, "Priority screening cleared", "QUEUED", 20, 525},
                    {"BAG-105", "P103", "Rajesh Patel", "Outgoing", "OUTGOING", "SG303", 3, 8, "MAA", "Bengaluru (BLR) / Tiruchirappalli (TRZ)", "CHECK-IN", "BAY-C", 645, 0, "", "AWAITING_CLEARANCE", 19, 540},
                    {"BAG-120", "P103", "Rajesh Patel", "Outgoing", "OUTGOING", "6E202", 3, 9, "MAA", "Mumbai (BOM) via Coimbatore (CJB)", "CHECK-IN", "BAY-B", 530, 1, "Screening cleared", "MISSED_CUTOFF", 20, 535},
                    {"BAG-106", "P104", "Ananya Iyer", "Outgoing", "OUTGOING", "UK404", 2, 10, "MAA", "Dubai (DXB) / Singapore (SIN)", "CHECK-IN", "BAY-D", 620, 1, "Screening verified", "QUEUED", 21, 530},
                    {"BAG-124", "P104", "Ananya Iyer", "Outgoing", "OUTGOING", "UK404", 2, 11, "MAA", "Dubai (DXB) / Singapore (SIN)", "BAY-D", "BAY-D", 620, 1, "Screening verified", "LOADED", 0, 525},
                    {"BAG-132", "P104", "Ananya Iyer", "Outgoing", "OUTGOING", "UK404", 2, 12, "MAA", "Dubai (DXB) / Singapore (SIN)", "CHECK-IN", "BAY-D", 620, 0, "", "AWAITING_CLEARANCE", 21, 540},
                    {"BAG-107", "P105", "Vikram Singh", "Outgoing", "OUTGOING", "AI101", 3, 13, "MAA", "Delhi (DEL) via Madurai (IXM)", "CHECK-IN", "BAY-A", 630, 1, "Screening verified", "QUEUED", 19, 535},
                    {"BAG-116", "P105", "Vikram Singh", "Incoming", "INCOMING", null, 3, 14, "DXB", "MAA", "CLAIM-CAROUSEL-1", "CLAIM-CAROUSEL-1", 0, 1, "Inbound flight baggage", "AT_COLLECTION_POINT", 0, 530},
                    {"BAG-125", "P105", "Vikram Singh", "Outgoing", "OUTGOING", "AI101", 3, 15, "MAA", "Delhi (DEL) via Madurai (IXM)", "BAY-AREA", "BAY-A", 630, 1, "Screening verified", "IN_TRANSIT", 3, 525},
                    {"BAG-108", "P106", "Meera Nair", "Outgoing", "OUTGOING", "6E202", 3, 16, "MAA", "Mumbai (BOM) via Coimbatore (CJB)", "CHECK-IN", "BAY-B", 615, 0, "", "AWAITING_CLEARANCE", 20, 540},
                    {"BAG-117", "P106", "Meera Nair", "Incoming", "INCOMING", null, 3, 17, "DXB", "MAA", "CLAIM-CAROUSEL-1", "CLAIM-CAROUSEL-1", 0, 1, "Inbound flight baggage", "COLLECTED", 0, 535},
                    {"BAG-109", "P107", "Arun Verma", "Outgoing", "OUTGOING", "SG303", 2, 18, "MAA", "Bengaluru (BLR) / Tiruchirappalli (TRZ)", "BAY-AREA", "BAY-C", 645, 1, "Screening cleared", "IN_TRANSIT", 3, 530},
                    {"BAG-118", "P107", "Arun Verma", "Transfer", "TRANSFER", "AI101", 2, 19, "DEL", "Delhi (DEL) via Madurai (IXM)", "TRANSFER-A", "BAY-A", 630, 1, "Transit security approved", "QUEUED", 12, 525},
                    {"BAG-110", "P108", "Sneha Reddy", "Outgoing", "OUTGOING", "UK404", 1, 20, "MAA", "Dubai (DXB) / Singapore (SIN)", "CHECK-IN", "BAY-D", 620, 1, "VIP screening cleared", "QUEUED", 21, 540},
                    {"BAG-111", "P108", "Sneha Reddy", "Outgoing", "OUTGOING", "UK404", 1, 21, "MAA", "Dubai (DXB) / Singapore (SIN)", "BAY-D", "BAY-D", 620, 1, "VIP screening cleared", "LOADED", 0, 535},
                    {"BAG-126", "P108", "Sneha Reddy", "Outgoing", "OUTGOING", "UK404", 1, 22, "MAA", "Dubai (DXB) / Singapore (SIN)", "CENTRAL", "BAY-D", 620, 1, "VIP screening cleared", "IN_TRANSIT", 10, 530},
                    {"BAG-112", "P109", "Karthik Raman", "Outgoing", "OUTGOING", "AI101", 3, 23, "MAA", "Delhi (DEL) via Madurai (IXM)", "CHECK-IN", "BAY-A", 630, 0, "", "AWAITING_CLEARANCE", 19, 525},
                    {"BAG-121", "P109", "Karthik Raman", "Outgoing", "OUTGOING", "SG303", 3, 24, "MAA", "Bengaluru (BLR) / Tiruchirappalli (TRZ)", "CHECK-IN", "BAY-C", 645, 0, "Prohibited lithium battery pack detected in X-ray screening", "REJECTED", 19, 540},
                    {"BAG-127", "P109", "Karthik Raman", "Outgoing", "OUTGOING", "AI101", 3, 25, "MAA", "Delhi (DEL) via Madurai (IXM)", "CHECK-IN", "BAY-A", 630, 1, "Screening cleared", "QUEUED", 19, 535},
                    {"BAG-113", "P110", "Divya Krishnan", "Outgoing", "OUTGOING", "6E202", 3, 26, "MAA", "Mumbai (BOM) via Coimbatore (CJB)", "BAY-B", "BAY-B", 615, 1, "Screening cleared", "LOADED", 0, 530},
                    {"BAG-128", "P110", "Divya Krishnan", "Outgoing", "OUTGOING", "6E202", 3, 27, "MAA", "Mumbai (BOM) via Coimbatore (CJB)", "CHECK-IN", "BAY-B", 615, 1, "Screening cleared", "QUEUED", 20, 525},
                    {"BAG-114", "P111", "David Miller", "Incoming", "INCOMING", null, 2, 28, "DXB", "MAA", "UNLOAD-POINT", "CLAIM-CAROUSEL-1", 0, 1, "Inbound flight baggage", "QUEUED", 12, 540},
                    {"BAG-119", "P111", "David Miller", "Incoming", "INCOMING", null, 2, 29, "DXB", "MAA", "CENTRAL", "CLAIM-CAROUSEL-2", 0, 1, "Inbound flight baggage", "IN_TRANSIT", 8, 535},
                    {"BAG-129", "P111", "David Miller", "Incoming", "INCOMING", null, 2, 30, "DXB", "MAA", "CLAIM-CAROUSEL-1", "CLAIM-CAROUSEL-1", 0, 1, "Inbound flight baggage", "AT_COLLECTION_POINT", 0, 530},
                    {"BAG-115", "P112", "Fatima Al-Sayed", "Transfer", "TRANSFER", "UK404", 1, 31, "DEL", "Dubai (DXB) / Singapore (SIN)", "TRANSFER-A", "BAY-D", 620, 1, "Transit security approved", "QUEUED", 14, 525},
                    {"BAG-130", "P112", "Fatima Al-Sayed", "Transfer", "TRANSFER", "UK404", 1, 32, "DEL", "Dubai (DXB) / Singapore (SIN)", "BAY-D", "BAY-D", 620, 1, "Transit security approved", "LOADED", 0, 540}
                };
                String bSql = "INSERT OR IGNORE INTO Baggage (bagId, passengerId, passengerName, bagType, direction, flightId, categoryRank, arrivalOrder, originAirport, destinationAirport, currentLocation, destinationNode, loadingCutoff, operatorClearance, clearanceReason, status, estimatedTime, queuedSince, createdAt, updatedAt) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?, datetime('now'), datetime('now'))";
                try (PreparedStatement pstmt = conn.prepareStatement(bSql)) {
                    for (Object[] b : bags) {
                        pstmt.setString(1, (String) b[0]);
                        pstmt.setString(2, (String) b[1]);
                        pstmt.setString(3, (String) b[2]);
                        pstmt.setString(4, (String) b[3]);
                        pstmt.setString(5, (String) b[4]);
                        pstmt.setString(6, (String) b[5]);
                        pstmt.setInt(7, (Integer) b[6]);
                        pstmt.setInt(8, (Integer) b[7]);
                        pstmt.setString(9, (String) b[8]);
                        pstmt.setString(10, (String) b[9]);
                        pstmt.setString(11, (String) b[10]);
                        pstmt.setString(12, (String) b[11]);
                        pstmt.setInt(13, (Integer) b[12]);
                        pstmt.setInt(14, (Integer) b[13]);
                        pstmt.setString(15, (String) b[14]);
                        pstmt.setString(16, (String) b[15]);
                        pstmt.setInt(17, (Integer) b[16]);
                        pstmt.setInt(18, (Integer) b[17]);
                        pstmt.executeUpdate();
                    }
                }
            }

            conn.commit();
            logAudit("SYSTEM", "INITIALIZE_SEED_DATA", "System", 540, "SUCCESS", "Pristine demonstration dataset seeded into SQLite");
            RedisCache.invalidateAll();
        } catch (SQLException e) {
            System.err.println("Error seeding database: " + e.getMessage());
        }
    }
}
