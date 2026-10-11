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

    public static Passenger getPassengerByUsername(String username) {
        if (username == null) return null;
        String sql = "SELECT * FROM Passenger WHERE username = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
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
            System.err.println("Error fetching passenger by username: " + e.getMessage());
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
        String sql = "INSERT OR REPLACE INTO Baggage (bagId, passengerId, passengerName, bagType, direction, flightId, categoryRank, arrivalOrder, originAirport, destinationAirport, currentLocation, destinationNode, loadingCutoff, operatorClearance, clearanceReason, status, estimatedTime, updatedAt) "
                + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?, datetime('now'))";
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
            pstmt.executeUpdate();
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
                bag.setDestinationNode(destNode);
                bag.setEstimatedTime(estTime);
                bag.setLoadingCutoff(cutoff);

                if (flightId != null) {
                    Flight f = service.getFlight(flightId);
                    if (f != null) bag.assignFlight(f);
                }

                bag.setClearance(cleared, clearanceReason);
                bag.setStatus(BaggageStatus.fromString(statusStr));

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
        } catch (SQLException e) {
            System.err.println("Error saving loading bay: " + e.getMessage());
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
    }
}
