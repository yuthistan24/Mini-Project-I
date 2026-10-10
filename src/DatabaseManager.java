import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    private static final String URL = "jdbc:sqlite:data/airport.db";

    public static Connection connect() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            System.out.println("SQLite JDBC driver not found: " + e.getMessage());
        }
        try {
            java.io.File dataDir = new java.io.File("data");
            if (!dataDir.exists()) {
                dataDir.mkdirs();
            }
            return DriverManager.getConnection(URL);
        } catch (SQLException e) {
            System.out.println("Database connection error: " + e.getMessage());
            return null;
        }
    }

    public static void initializeDatabase() {
        String createBaggageTable = "CREATE TABLE IF NOT EXISTS Baggage ("
                + "bagId TEXT PRIMARY KEY,"
                + "bagType TEXT,"
                + "direction TEXT,"
                + "flightId TEXT,"
                + "categoryRank INTEGER,"
                + "arrivalOrder INTEGER,"
                + "currentLocation TEXT,"
                + "destinationNode TEXT,"
                + "loadingCutoff INTEGER,"
                + "operatorClearance INTEGER,"
                + "clearanceReason TEXT,"
                + "status TEXT,"
                + "estimatedTime INTEGER"
                + ");";

        String createFlightTable = "CREATE TABLE IF NOT EXISTS Flight ("
                + "flightId TEXT PRIMARY KEY,"
                + "destination TEXT,"
                + "loadingCutoff INTEGER,"
                + "assignedBay TEXT"
                + ");";

        String createLoadingBayTable = "CREATE TABLE IF NOT EXISTS LoadingBay ("
                + "bayId TEXT PRIMARY KEY,"
                + "assignedFlight TEXT"
                + ");";

        String createConveyorTable = "CREATE TABLE IF NOT EXISTS ConveyorSegment ("
                + "fromNode TEXT,"
                + "toNode TEXT,"
                + "processingTime INTEGER,"
                + "available INTEGER,"
                + "PRIMARY KEY (fromNode, toNode)"
                + ");";

        String createRoutingTable = "CREATE TABLE IF NOT EXISTS RoutingRecord ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "bagId TEXT,"
                + "fromNode TEXT,"
                + "toNode TEXT,"
                + "estimatedTime INTEGER,"
                + "status TEXT,"
                + "simulatedTime INTEGER,"
                + "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP"
                + ");";

        String createUsersTable = "CREATE TABLE IF NOT EXISTS Users ("
                + "username TEXT PRIMARY KEY,"
                + "passwordHash TEXT,"
                + "passwordSalt TEXT,"
                + "role TEXT,"
                + "active INTEGER"
                + ");";

        String createAuditTable = "CREATE TABLE IF NOT EXISTS AuditLog ("
                + "logId INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "username TEXT,"
                + "action TEXT,"
                + "targetRecord TEXT,"
                + "simulatedTime INTEGER,"
                + "result TEXT,"
                + "reason TEXT,"
                + "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP"
                + ");";

        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            if (conn != null) {
                stmt.execute(createBaggageTable);
                stmt.execute(createFlightTable);
                stmt.execute(createLoadingBayTable);
                stmt.execute(createConveyorTable);
                stmt.execute(createRoutingTable);
                stmt.execute(createUsersTable);
                stmt.execute(createAuditTable);

                // Seamless migration for existing database files
                addColumnIfNotExists(stmt, "Baggage", "direction", "TEXT DEFAULT 'OUTGOING'");
                addColumnIfNotExists(stmt, "Baggage", "destinationNode", "TEXT");
                addColumnIfNotExists(stmt, "Baggage", "loadingCutoff", "INTEGER DEFAULT 0");
                addColumnIfNotExists(stmt, "Baggage", "clearanceReason", "TEXT DEFAULT ''");
                addColumnIfNotExists(stmt, "Baggage", "estimatedTime", "INTEGER DEFAULT 0");
                addColumnIfNotExists(stmt, "AuditLog", "timestamp", "TEXT DEFAULT ''");
                addColumnIfNotExists(stmt, "RoutingRecord", "timestamp", "TEXT DEFAULT ''");
            }
        } catch (SQLException e) {
            System.out.println("Database initialization error: " + e.getMessage());
        }
    }

    private static void addColumnIfNotExists(Statement stmt, String table, String column, String colDef) {
        try {
            stmt.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + colDef + ";");
        } catch (SQLException ignored) {
            // Column already exists
        }
    }

    // ==================== AUTH & USERS ====================

    public static void saveUser(User user) {
        String sql = "INSERT OR REPLACE INTO Users (username, passwordHash, passwordSalt, role, active) VALUES(?,?,?,?,?)";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, user.getUsername());
            pstmt.setString(2, user.getPasswordHash());
            pstmt.setString(3, user.getPasswordSalt());
            pstmt.setString(4, user.getRole().toString());
            pstmt.setInt(5, user.isActive() ? 1 : 0);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error saving user: " + e.getMessage());
        }
    }

    public static User getUser(String username) {
        String sql = "SELECT * FROM Users WHERE username = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new User(
                    rs.getString("username"),
                    rs.getString("passwordHash"),
                    rs.getString("passwordSalt"),
                    Role.valueOf(rs.getString("role")),
                    rs.getInt("active") == 1
                );
            }
        } catch (SQLException e) {
            System.out.println("Error fetching user: " + e.getMessage());
        }
        return null;
    }

    public static List<User> getAllUsers() {
        List<User> users = new ArrayList<>();
        String sql = "SELECT username, role, active FROM Users ORDER BY username ASC";
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                users.add(new User(
                    rs.getString("username"),
                    "", "", // Hashes withheld for safety
                    Role.valueOf(rs.getString("role")),
                    rs.getInt("active") == 1
                ));
            }
        } catch (SQLException e) {
            System.out.println("Error listing users: " + e.getMessage());
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
            System.out.println("Error updating user status: " + e.getMessage());
        }
    }

    public static void updateUserRole(String username, Role role) {
        String sql = "UPDATE Users SET role = ? WHERE username = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, role.toString());
            pstmt.setString(2, username);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error updating user role: " + e.getMessage());
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
            System.out.println("Error resetting user password: " + e.getMessage());
        }
    }

    public static int getActiveAdminCount() {
        String sql = "SELECT COUNT(*) FROM Users WHERE role = 'ADMIN' AND active = 1";
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            // ignore
        }
        return 0;
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
            System.out.println("Error saving audit log: " + e.getMessage());
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
            System.out.println("Error reading audit logs: " + e.getMessage());
        }
        return logs;
    }

    public static void printAuditLogs() {
        String sql = "SELECT * FROM AuditLog ORDER BY logId ASC";
        System.out.println("\n==========================================================================================");
        System.out.println("                                      AUDIT LOGS");
        System.out.println("==========================================================================================");
        System.out.printf(" %-5s | %-12s | %-20s | %-12s | %-6s | %-10s | %-15s\n", "ID", "User", "Action", "Target", "Time", "Result", "Reason");
        System.out.println("------------------------------------------------------------------------------------------");
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                System.out.printf(" %-5d | %-12s | %-20s | %-12s | %-6d | %-10s | %-15s\n",
                        rs.getInt("logId"), rs.getString("username"), rs.getString("action"),
                        rs.getString("targetRecord"), rs.getInt("simulatedTime"),
                        rs.getString("result"), rs.getString("reason"));
            }
        } catch (SQLException e) {
            System.out.println("Error reading audit history: " + e.getMessage());
        }
        System.out.println("==========================================================================================");
    }

    // ==================== BAGGAGE PERSISTENCE ====================

    public static void saveBaggage(Baggage bag) {
        String sql = "INSERT OR REPLACE INTO Baggage (bagId, bagType, direction, flightId, categoryRank, arrivalOrder, currentLocation, destinationNode, loadingCutoff, operatorClearance, clearanceReason, status, estimatedTime) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, bag.getBagId());
            pstmt.setString(2, bag.getBagType());
            pstmt.setString(3, bag.getDirection().toString());
            pstmt.setString(4, bag.getAssignedFlight() != null ? bag.getAssignedFlight().getFlightId() : null);
            pstmt.setInt(5, bag.getPassengerCategory().getRank());
            pstmt.setInt(6, bag.getArrivalOrder());
            pstmt.setString(7, bag.getCurrentLocation());
            pstmt.setString(8, bag.getDestinationNode());
            pstmt.setInt(9, bag.getLoadingCutoff());
            pstmt.setInt(10, bag.isOperatorCleared() ? 1 : 0);
            pstmt.setString(11, bag.getClearanceReason());
            pstmt.setString(12, bag.getStatus().toString());
            pstmt.setInt(13, bag.getEstimatedTime());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error saving baggage: " + e.getMessage());
        }
    }

    public static void loadBaggage(BaggageService service) {
        String sql = "SELECT * FROM Baggage";
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String bagId = rs.getString("bagId");
                String directionStr = rs.getString("direction");
                String flightId = rs.getString("flightId");
                int catRank = rs.getInt("categoryRank");
                int arrOrder = rs.getInt("arrivalOrder");
                String currentLoc = rs.getString("currentLocation");
                String destNode = rs.getString("destinationNode");
                int cutoff = rs.getInt("loadingCutoff");
                boolean cleared = rs.getInt("operatorClearance") == 1;
                String clearanceReason = rs.getString("clearanceReason");
                String statusStr = rs.getString("status");
                int estTime = rs.getInt("estimatedTime");

                PassengerCategory cat = PassengerCategory.ECONOMY;
                if (catRank == 1) cat = PassengerCategory.FIRST_CLASS;
                else if (catRank == 2) cat = PassengerCategory.BUSINESS;

                BaggageDirection direction = BaggageDirection.OUTGOING;
                if ("INCOMING".equalsIgnoreCase(directionStr)) direction = BaggageDirection.INCOMING;
                else if ("TRANSFER".equalsIgnoreCase(directionStr)) direction = BaggageDirection.TRANSFER;

                Baggage bag;
                if (direction == BaggageDirection.TRANSFER) {
                    bag = new TransferBag(bagId, cat, arrOrder);
                } else if (direction == BaggageDirection.INCOMING) {
                    bag = new IncomingBag(bagId, cat, arrOrder);
                } else {
                    bag = new OriginBag(bagId, cat, arrOrder);
                }

                bag.setCurrentLocation(currentLoc);
                bag.setDestinationNode(destNode);
                bag.setEstimatedTime(estTime);

                if (flightId != null) {
                    Flight f = service.getFlight(flightId);
                    if (f != null) bag.assignFlight(f);
                }

                bag.setStatus(BaggageStatus.valueOf(statusStr));
                bag.setClearance(cleared, clearanceReason);

                service.addLoadedBaggage(bag);
            }
        } catch (SQLException e) {
            System.out.println("Error loading baggage: " + e.getMessage());
        }
    }

    // ==================== FLIGHT PERSISTENCE ====================

    public static void saveFlight(Flight flight) {
        String sql = "INSERT OR REPLACE INTO Flight (flightId, destination, loadingCutoff, assignedBay) VALUES(?,?,?,?)";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, flight.getFlightId());
            pstmt.setString(2, flight.getDestination());
            pstmt.setInt(3, flight.getLoadingCutoff());
            pstmt.setString(4, flight.getAssignedBay() != null ? flight.getAssignedBay().getBayId() : null);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error saving flight: " + e.getMessage());
        }
    }

    public static void loadFlights(BaggageService service) {
        String sql = "SELECT * FROM Flight";
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String flightId = rs.getString("flightId");
                String destination = rs.getString("destination");
                int cutoff = rs.getInt("loadingCutoff");
                String bayId = rs.getString("assignedBay");

                Flight f = new Flight(flightId, destination, cutoff);
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
            System.out.println("Error loading flights: " + e.getMessage());
        }
    }

    // ==================== CONVEYOR PERSISTENCE ====================

    public static void saveConveyorSegment(ConveyorSegment segment) {
        String sql = "INSERT OR REPLACE INTO ConveyorSegment (fromNode, toNode, processingTime, available) VALUES(?,?,?,?)";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, segment.getFromNode());
            pstmt.setString(2, segment.getToNode());
            pstmt.setInt(3, segment.getProcessingTime());
            pstmt.setInt(4, segment.isAvailable() ? 1 : 0);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error saving conveyor segment: " + e.getMessage());
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
            System.out.println("Error loading conveyors: " + e.getMessage());
        }
    }

    // ==================== ROUTING RECORD ====================

    public static void saveRoutingRecord(String bagId, String fromNode, String toNode, int estTime, String status, int simTime) {
        String sql = "INSERT INTO RoutingRecord (bagId, fromNode, toNode, estimatedTime, status, simulatedTime) VALUES(?,?,?,?,?,?)";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, bagId);
            pstmt.setString(2, fromNode);
            pstmt.setString(3, toNode);
            pstmt.setInt(4, estTime);
            pstmt.setString(5, status);
            pstmt.setInt(6, simTime);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error saving routing record: " + e.getMessage());
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
            System.out.println("Error reading routing records: " + e.getMessage());
        }
        return records;
    }

    public static void printRoutingHistory() {
        String sql = "SELECT * FROM RoutingRecord ORDER BY id ASC";
        System.out.println("\n=====================================================================");
        System.out.println("                         ROUTING HISTORY");
        System.out.println("=====================================================================");
        System.out.printf(" %-4s | %-6s | %-10s | %-10s | %-6s | %-12s\n", "ID", "Bag ID", "From", "To", "Time", "Status");
        System.out.println("---------------------------------------------------------------------");
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                System.out.printf(" %-4d | %-6s | %-10s | %-10s | %-6d | %-12s\n",
                        rs.getInt("id"), rs.getString("bagId"), rs.getString("fromNode"),
                        rs.getString("toNode"), rs.getInt("estimatedTime"), rs.getString("status"));
            }
        } catch (SQLException e) {
            System.out.println("Error reading routing history: " + e.getMessage());
        }
        System.out.println("=====================================================================");
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
}
