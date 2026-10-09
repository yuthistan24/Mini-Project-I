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
                + "flightId TEXT,"
                + "categoryRank INTEGER,"
                + "arrivalOrder INTEGER,"
                + "currentLocation TEXT,"
                + "operatorClearance INTEGER,"
                + "status TEXT"
                + ");";

        String createFlightTable = "CREATE TABLE IF NOT EXISTS Flight ("
                + "flightId TEXT PRIMARY KEY,"
                + "destination TEXT,"
                + "loadingCutoff INTEGER,"
                + "assignedBay TEXT"
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
                + "simulatedTime INTEGER"
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
                + "reason TEXT"
                + ");";

        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            if (conn != null) {
                stmt.execute(createBaggageTable);
                stmt.execute(createFlightTable);
                stmt.execute(createConveyorTable);
                stmt.execute(createRoutingTable);
                stmt.execute(createUsersTable);
                stmt.execute(createAuditTable);
            }
        } catch (SQLException e) {
            System.out.println("Database initialization error: " + e.getMessage());
        }
    }
    
    // Auth & Users
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
        String sql = "SELECT username, role, active FROM Users";
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                users.add(new User(
                    rs.getString("username"),
                    "", "", // Do not load hashes for listing
                    Role.valueOf(rs.getString("role")),
                    rs.getInt("active") == 1
                ));
            }
        } catch (SQLException e) {
            System.out.println("Error listing users: " + e.getMessage());
        }
        return users;
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

    // Audit Log
    public static void logAudit(String username, String action, String targetRecord, int simulatedTime, String result, String reason) {
        String sql = "INSERT INTO AuditLog (username, action, targetRecord, simulatedTime, result, reason) VALUES(?,?,?,?,?,?)";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, action);
            pstmt.setString(3, targetRecord);
            pstmt.setInt(4, simulatedTime);
            pstmt.setString(5, result);
            pstmt.setString(6, reason != null ? reason : "");
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error saving audit log: " + e.getMessage());
        }
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

    // Baggage Persistence
    public static void saveBaggage(Baggage bag) {
        String sql = "INSERT OR REPLACE INTO Baggage (bagId, bagType, flightId, categoryRank, arrivalOrder, currentLocation, operatorClearance, status) VALUES(?,?,?,?,?,?,?,?)";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, bag.getBagId());
            pstmt.setString(2, bag.getBagType());
            pstmt.setString(3, bag.getAssignedFlight() != null ? bag.getAssignedFlight().getFlightId() : null);
            pstmt.setInt(4, bag.getPassengerCategory().getRank());
            pstmt.setInt(5, bag.getArrivalOrder());
            pstmt.setString(6, bag.getCurrentLocation());
            pstmt.setInt(7, bag.isOperatorCleared() ? 1 : 0);
            pstmt.setString(8, bag.getStatus().toString());
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
                String bagType = rs.getString("bagType");
                String flightId = rs.getString("flightId");
                int catRank = rs.getInt("categoryRank");
                int arrOrder = rs.getInt("arrivalOrder");
                String currentLoc = rs.getString("currentLocation");
                boolean cleared = rs.getInt("operatorClearance") == 1;
                String statusStr = rs.getString("status");

                PassengerCategory cat = PassengerCategory.ECONOMY;
                if (catRank == 1) cat = PassengerCategory.FIRST_CLASS;
                else if (catRank == 2) cat = PassengerCategory.BUSINESS;

                Baggage bag;
                if ("Transfer".equals(bagType)) {
                    bag = new TransferBag(bagId, cat, arrOrder);
                } else {
                    bag = new OriginBag(bagId, cat, arrOrder);
                }
                
                bag.setCurrentLocation(currentLoc);
                if (flightId != null) {
                    Flight f = service.getFlight(flightId);
                    if (f != null) bag.assignFlight(f);
                }
                
                bag.setStatus(BaggageStatus.valueOf(statusStr));
                bag.setOperatorClearance(cleared);
                
                service.addLoadedBaggage(bag);
            }
        } catch (SQLException e) {
            System.out.println("Error loading baggage: " + e.getMessage());
        }
    }
    
    // Flight Persistence
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
                    }
                }
                service.addLoadedFlight(f);
            }
        } catch (SQLException e) {
            System.out.println("Error loading flights: " + e.getMessage());
        }
    }
    
    // Conveyor Segment Persistence
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

    // Routing Record
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
