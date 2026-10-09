import java.util.Scanner;
import java.util.List;

public class Main {
    private static AuthManager auth = new AuthManager();
    private static BaggageService baggageService;
    private static Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        DatabaseManager.initializeDatabase();
        baggageService = new BaggageService(auth);
        
        while (true) {
            List<User> users = DatabaseManager.getAllUsers();
            if (users.isEmpty()) {
                System.out.println("\n--- FIRST TIME SETUP ---");
                System.out.println("No accounts found. Please create an initial Administrator account.");
                System.out.print("Enter username: ");
                if (!scanner.hasNextLine()) break;
                String username = scanner.nextLine().trim();
                System.out.print("Enter password: ");
                if (!scanner.hasNextLine()) break;
                String password = scanner.nextLine().trim();
                
                String[] hashSalt = AuthManager.hashPassword(password);
                User admin = new User(username, hashSalt[0], hashSalt[1], Role.ADMIN, true);
                DatabaseManager.saveUser(admin);
                System.out.println("Administrator account created successfully.");
            }

            System.out.println("\n==================================================");
            System.out.println("      VAANAM AIRPORT BAGGAGE SYSTEM - LOGIN");
            System.out.println("==================================================");
            System.out.print("Username: ");
            if (!scanner.hasNextLine()) break;
            String username = scanner.nextLine().trim();
            System.out.print("Password: ");
            if (!scanner.hasNextLine()) break;
            String password = scanner.nextLine().trim();

            User user = DatabaseManager.getUser(username);
            if (user != null && user.isActive() && AuthManager.verifyPassword(password, user.getPasswordHash(), user.getPasswordSalt())) {
                System.out.println("Login successful. Welcome, " + username + " (" + user.getRole() + ")");
                auth.setCurrentUser(user);
                DatabaseManager.logAudit(username, "LOGIN", "System", 0, "SUCCESS", null);
                
                showMainMenu();
            } else {
                System.out.println("Invalid username, password, or inactive account.");
                DatabaseManager.logAudit(username, "LOGIN", "System", 0, "FAILED", "Invalid credentials or inactive");
            }
        }
    }

    private static void showMainMenu() {
        boolean loggedIn = true;
        
        while (loggedIn && auth.getCurrentUser() != null) {
            Role role = auth.getCurrentUser().getRole();
            System.out.println("\n==================================================");
            System.out.println("          VAANAM AIRPORT BAGGAGE SYSTEM");
            System.out.println("==================================================");
            System.out.println("   [ User: " + auth.getCurrentUser().getUsername() + " | Role: " + role + " ]");
            System.out.println("   [ Simulated Time: " + baggageService.getFormattedSimulatedTime() + " ]");
            System.out.println("--------------------------------------------------");
            
            // Display Role-Specific Options
            if (role == Role.ADMIN || role == Role.SUPERVISOR || role == Role.BAGGAGE_OPERATOR || role == Role.AUDITOR) {
                System.out.println("1. View Flights");
                System.out.println("2. View Baggage");
                System.out.println("3. Track Baggage");
                System.out.println("4. View Loading Bays");
                System.out.println("5. View Priority Queue");
                System.out.println("6. View Conveyor Network");
            }
            
            if (role == Role.ADMIN || role == Role.SUPERVISOR || role == Role.AUDITOR) {
                System.out.println("7. View Routing History");
            }
            
            if (role == Role.ADMIN || role == Role.AUDITOR) {
                System.out.println("8. View Audit Logs");
            }
            
            if (role == Role.ADMIN || role == Role.BAGGAGE_OPERATOR) {
                System.out.println("9. Register Baggage");
                System.out.println("10. Assign Baggage to Flight");
                System.out.println("11. Process Next Baggage");
            }
            
            if (role == Role.ADMIN || role == Role.SECURITY_OFFICER) {
                System.out.println("12. View Pending Baggage");
            }
            
            if (role == Role.SECURITY_OFFICER) {
                System.out.println("13. Grant Operator Clearance");
                System.out.println("14. Revoke Operator Clearance");
            }
            
            if (role == Role.ADMIN || role == Role.SUPERVISOR) {
                System.out.println("15. Mark Conveyor Unavailable");
                System.out.println("16. Restore Conveyor");
                System.out.println("17. Change Flight Loading Bay");
                System.out.println("18. Advance Simulated Clock");
            }
            
            if (role == Role.ADMIN) {
                System.out.println("19. Manage Users");
            }
            
            System.out.println("20. Logout");
            System.out.print("\nEnter choice: ");
            
            if (!scanner.hasNextLine()) break;
            
            String choiceStr = scanner.nextLine().trim();
            try {
                int choice = Integer.parseInt(choiceStr);
                
                try {
                    switch(choice) {
                        case 1: baggageService.displayFlights(); break;
                        case 2: baggageService.displayBaggage(); break;
                        case 3: trackBaggage(); break;
                        case 4: baggageService.displayBays(); break;
                        case 5: baggageService.printPriorityQueue(); break;
                        case 6: baggageService.displayConveyorNetwork(); break;
                        case 7: baggageService.viewRoutingHistory(); break;
                        case 8: baggageService.viewAuditLogs(); break;
                        case 9: registerBaggage(); break;
                        case 10: assignBaggage(); break;
                        case 11: baggageService.processNextBaggage(); break;
                        case 12: baggageService.displayPendingBaggage(); break;
                        case 13: grantClearance(); break;
                        case 14: revokeClearance(); break;
                        case 15: markConveyorUnavailable(); break;
                        case 16: restoreConveyor(); break;
                        case 17: changeFlightBay(); break;
                        case 18: advanceClock(); break;
                        case 19: manageUsers(); break;
                        case 20: 
                            auth.logout(); 
                            loggedIn = false; 
                            System.out.println("Logged out successfully.");
                            break;
                        default:
                            System.out.println("Invalid option.");
                    }
                } catch (SecurityException se) {
                    System.out.println("\n[ERROR] " + se.getMessage());
                }
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number.");
            }
        }
    }
    
    private static void manageUsers() {
        auth.requirePermission(Role.ADMIN);
        while (true) {
            System.out.println("\n--- USER MANAGEMENT ---");
            System.out.println("1. List Users");
            System.out.println("2. Create User");
            System.out.println("3. Change Role");
            System.out.println("4. Reset Password");
            System.out.println("5. Activate/Deactivate User");
            System.out.println("6. Back");
            System.out.print("Enter choice: ");
            if (!scanner.hasNextLine()) return;
            String choice = scanner.nextLine().trim();
            
            if (choice.equals("6")) return;
            
            if (choice.equals("1")) {
                List<User> users = DatabaseManager.getAllUsers();
                System.out.println("\nUsername | Role | Active");
                System.out.println("--------------------------");
                for (User u : users) {
                    System.out.println(u.getUsername() + " | " + u.getRole() + " | " + u.isActive());
                }
            } else if (choice.equals("2")) {
                System.out.print("Enter new username: ");
                String un = scanner.nextLine().trim();
                if (DatabaseManager.getUser(un) != null) {
                    System.out.println("Username already exists.");
                    continue;
                }
                System.out.print("Enter password: ");
                String pw = scanner.nextLine().trim();
                System.out.print("Enter role (ADMIN, SUPERVISOR, BAGGAGE_OPERATOR, SECURITY_OFFICER, AUDITOR): ");
                String rStr = scanner.nextLine().trim();
                try {
                    Role r = Role.valueOf(rStr.toUpperCase());
                    String[] hashSalt = AuthManager.hashPassword(pw);
                    User u = new User(un, hashSalt[0], hashSalt[1], r, true);
                    DatabaseManager.saveUser(u);
                    System.out.println("User created.");
                    DatabaseManager.logAudit(auth.getCurrentUser().getUsername(), "CREATE_USER", un, 0, "SUCCESS", "Role: " + r);
                } catch (IllegalArgumentException e) {
                    System.out.println("Invalid role.");
                }
            } else if (choice.equals("3")) {
                System.out.print("Enter username: ");
                String un = scanner.nextLine().trim();
                User u = DatabaseManager.getUser(un);
                if (u == null) { System.out.println("User not found."); continue; }
                System.out.print("Enter new role: ");
                try {
                    Role r = Role.valueOf(scanner.nextLine().trim().toUpperCase());
                    u.setRole(r);
                    DatabaseManager.saveUser(u);
                    System.out.println("Role updated.");
                    DatabaseManager.logAudit(auth.getCurrentUser().getUsername(), "CHANGE_ROLE", un, 0, "SUCCESS", "New Role: " + r);
                } catch (IllegalArgumentException e) { System.out.println("Invalid role."); }
            } else if (choice.equals("4")) {
                System.out.print("Enter username: ");
                String un = scanner.nextLine().trim();
                User u = DatabaseManager.getUser(un);
                if (u == null) { System.out.println("User not found."); continue; }
                System.out.print("Enter new password: ");
                String pw = scanner.nextLine().trim();
                String[] hashSalt = AuthManager.hashPassword(pw);
                u.setPassword(hashSalt[0], hashSalt[1]);
                DatabaseManager.saveUser(u);
                System.out.println("Password reset.");
                DatabaseManager.logAudit(auth.getCurrentUser().getUsername(), "RESET_PASSWORD", un, 0, "SUCCESS", null);
            } else if (choice.equals("5")) {
                System.out.print("Enter username: ");
                String un = scanner.nextLine().trim();
                User u = DatabaseManager.getUser(un);
                if (u == null) { System.out.println("User not found."); continue; }
                if (u.getRole() == Role.ADMIN && u.isActive()) {
                    if (DatabaseManager.getActiveAdminCount() <= 1) {
                        System.out.println("Cannot deactivate the last active ADMIN.");
                        continue;
                    }
                }
                boolean current = u.isActive();
                u.setActive(!current);
                DatabaseManager.saveUser(u);
                System.out.println("User active status changed to: " + !current);
                DatabaseManager.logAudit(auth.getCurrentUser().getUsername(), "TOGGLE_ACTIVE", un, 0, "SUCCESS", "New status: " + !current);
            }
        }
    }

    private static void registerBaggage() {
        System.out.println("\n--- Register New Baggage ---");
        System.out.print("Enter Baggage ID (e.g., B101): ");
        if (!scanner.hasNextLine()) return;
        String bagId = scanner.nextLine().trim();
        
        System.out.print("Is this a Transfer bag? (yes/no): ");
        if (!scanner.hasNextLine()) return;
        String transferInput = scanner.nextLine().trim();
        boolean isTransfer = transferInput.equalsIgnoreCase("yes");
        
        System.out.print("Category Rank (1: First, 2: Business, 3: Economy): ");
        if (!scanner.hasNextLine()) return;
        String catInput = scanner.nextLine().trim();
        int catRank = 3; // default
        if (catInput.equals("1")) catRank = 1;
        else if (catInput.equals("2")) catRank = 2;
        
        baggageService.registerBaggage(bagId, isTransfer, catRank);
    }

    private static void assignBaggage() {
        System.out.println("\n--- Assign Baggage to Flight ---");
        System.out.print("Enter Baggage ID: ");
        if (!scanner.hasNextLine()) return;
        String bagId = scanner.nextLine().trim();
        System.out.print("Enter Flight ID (e.g., F101): ");
        if (!scanner.hasNextLine()) return;
        String flightId = scanner.nextLine().trim();
        baggageService.assignBaggageToFlight(bagId, flightId);
    }

    private static void trackBaggage() {
        System.out.println("\n--- Track Baggage ---");
        System.out.print("Enter Baggage ID to track: ");
        if (!scanner.hasNextLine()) return;
        String bagId = scanner.nextLine().trim();
        baggageService.trackBaggage(bagId);
    }
    
    private static void grantClearance() {
        System.out.println("\n--- Grant Operator Clearance ---");
        System.out.print("Enter Baggage ID: ");
        if (!scanner.hasNextLine()) return;
        String bagId = scanner.nextLine().trim();
        System.out.print("Enter Reason: ");
        if (!scanner.hasNextLine()) return;
        String reason = scanner.nextLine().trim();
        baggageService.grantOperatorClearance(bagId, reason);
    }
    
    private static void revokeClearance() {
        System.out.println("\n--- Revoke Operator Clearance ---");
        System.out.print("Enter Baggage ID: ");
        if (!scanner.hasNextLine()) return;
        String bagId = scanner.nextLine().trim();
        System.out.print("Enter Reason: ");
        if (!scanner.hasNextLine()) return;
        String reason = scanner.nextLine().trim();
        baggageService.revokeOperatorClearance(bagId, reason);
    }
    
    private static void markConveyorUnavailable() {
        System.out.println("\n--- Mark Conveyor Unavailable ---");
        System.out.print("Enter From Node: ");
        if (!scanner.hasNextLine()) return;
        String from = scanner.nextLine().trim();
        System.out.print("Enter To Node: ");
        if (!scanner.hasNextLine()) return;
        String to = scanner.nextLine().trim();
        baggageService.markConveyorUnavailable(from, to);
    }
    
    private static void restoreConveyor() {
        System.out.println("\n--- Restore Conveyor ---");
        System.out.print("Enter From Node: ");
        if (!scanner.hasNextLine()) return;
        String from = scanner.nextLine().trim();
        System.out.print("Enter To Node: ");
        if (!scanner.hasNextLine()) return;
        String to = scanner.nextLine().trim();
        baggageService.restoreConveyor(from, to);
    }
    
    private static void changeFlightBay() {
        System.out.println("\n--- Change Flight Loading Bay ---");
        System.out.print("Enter Flight ID: ");
        if (!scanner.hasNextLine()) return;
        String flightId = scanner.nextLine().trim();
        System.out.print("Enter New Bay ID: ");
        if (!scanner.hasNextLine()) return;
        String bayId = scanner.nextLine().trim();
        baggageService.changeFlightBay(flightId, bayId);
    }
    
    private static void advanceClock() {
        System.out.println("\n--- Advance Simulated Clock ---");
        System.out.print("Enter minutes to advance: ");
        if (!scanner.hasNextLine()) return;
        String input = scanner.nextLine().trim();
        try {
            int minutes = Integer.parseInt(input);
            baggageService.advanceSimulatedClock(minutes);
        } catch (NumberFormatException e) {
            System.out.println("Invalid number format.");
        }
    }
}
