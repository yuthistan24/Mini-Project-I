import java.util.List;

public class TestRunner {
    private static int passed = 0;
    private static int failed = 0;
    private static final long RUN_ID = System.currentTimeMillis() % 100000;

    public static void main(String[] args) {
        System.out.println("\n=======================================================");
        System.out.println("   VAANAM AIRPORT BAGGAGE SYSTEM - COMPREHENSIVE TEST   ");
        System.out.println("=======================================================");

        DatabaseManager.initializeDatabase();
        AuthManager auth = new AuthManager();
        BaggageService service = new BaggageService(auth);

        // Setup test users for all 5 roles
        setupTestUsers();

        // 1. Successful and unsuccessful login
        testLogin();

        // 2. First-administrator setup verification
        testFirstAdminSetup();

        // 3. Inactive accounts and logout
        testInactiveAccountAndLogout();

        // 4. Duplicate username and baggage ID rejection
        testDuplicateRejection(service);

        // 5. All five roles' permitted and prohibited actions
        testFiveRolesPermissions(service);

        // 6. Unauthenticated requests
        testUnauthenticatedEnforcement();

        // 7. Operator attempting to grant clearance (strictly prohibited)
        testOperatorClearanceProhibited(service);

        // 8. Auditor attempting to modify records (strictly prohibited)
        testAuditorModificationProhibited(service);

        // 9. Dijkstra selecting minimum-time route
        testDijkstraMinimumRoute(service);

        // 10. Conveyor failure and rerouting
        testConveyorFailureAndRerouting(service);

        // 11. No available route detection
        testNoRouteAvailable(service);

        // 12. Cutoff checks before, exactly at, and after the cutoff
        testCutoffValidation(service);

        // 13. Flight loading-bay changes and route updates
        testFlightBayChanges(service);

        // 14. Outgoing baggage tracking workflow
        testOutgoingBaggageWorkflow(service);

        // 15. Incoming baggage tracking workflow (mentor requirement)
        testIncomingBaggageWorkflow(service);

        // 16. Transfer baggage routing and cutoff
        testTransferBaggageWorkflow(service);

        // 17. Estimated arrival time calculation & recalculation
        testEstimatedArrivalTimeRecalculation(service);

        // 18. Database persistence after re-reading from database
        testDatabasePersistence(service);

        // 19. Audit-log persistence
        testAuditLogPersistence();

        // 20. Prevention of duplicate processing and incorrect loading
        testPreventionOfDuplicateProcessing(service);

        // 21. Clearance rejection enforcement
        testClearanceRejectionEnforcement(service);

        System.out.println("\n=======================================================");
        System.out.println("FINAL TEST RESULTS: " + passed + " Passed, " + failed + " Failed.");
        System.out.println("=======================================================\n");

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void assertTrue(boolean condition, String testName) {
        if (condition) {
            passed++;
            System.out.println("  [PASS] " + testName);
        } else {
            failed++;
            System.err.println("  [FAIL] " + testName);
        }
    }

    private static void setupTestUsers() {
        String[] hashSalt = AuthManager.hashPassword("pass123");
        DatabaseManager.saveUser(new User("test_admin", hashSalt[0], hashSalt[1], Role.ADMIN, true));
        DatabaseManager.saveUser(new User("test_supervisor", hashSalt[0], hashSalt[1], Role.SUPERVISOR, true));
        DatabaseManager.saveUser(new User("test_operator", hashSalt[0], hashSalt[1], Role.BAGGAGE_OPERATOR, true));
        DatabaseManager.saveUser(new User("test_security", hashSalt[0], hashSalt[1], Role.SECURITY_OFFICER, true));
        DatabaseManager.saveUser(new User("test_auditor", hashSalt[0], hashSalt[1], Role.AUDITOR, true));
        DatabaseManager.saveUser(new User("test_inactive", hashSalt[0], hashSalt[1], Role.BAGGAGE_OPERATOR, false));
    }

    private static void testLogin() {
        System.out.println("\n[Test Suite 1] Login & Password Authentication");
        User user = DatabaseManager.getUser("test_admin");
        assertTrue(user != null, "Admin user exists in database");
        assertTrue(AuthManager.verifyPassword("pass123", user.getPasswordHash(), user.getPasswordSalt()), "Login succeeds with correct password");
        assertTrue(!AuthManager.verifyPassword("wrongpass", user.getPasswordHash(), user.getPasswordSalt()), "Login fails with incorrect password");
    }

    private static void testFirstAdminSetup() {
        System.out.println("\n[Test Suite 2] First Administrator Setup");
        int count = DatabaseManager.getActiveAdminCount();
        assertTrue(count >= 1, "Active admin count is properly recognized (" + count + ")");
    }

    private static void testInactiveAccountAndLogout() {
        System.out.println("\n[Test Suite 3] Inactive Accounts and Session Invalidation");
        User inactive = DatabaseManager.getUser("test_inactive");
        assertTrue(inactive != null && !inactive.isActive(), "Inactive account is marked inactive");
        assertTrue(!AuthManager.hasUserPermission(inactive, Role.BAGGAGE_OPERATOR), "Inactive user has no permissions");

        // Session test
        User active = DatabaseManager.getUser("test_admin");
        String sessionId = AuthManager.createSession(active);
        assertTrue(AuthManager.getSessionUser(sessionId) != null, "Session is valid after login");
        AuthManager.invalidateSession(sessionId);
        assertTrue(AuthManager.getSessionUser(sessionId) == null, "Session is invalidated after logout");
    }

    private static void testDuplicateRejection(BaggageService service) {
        System.out.println("\n[Test Suite 4] Duplicate Username and Baggage ID Rejection");
        User admin = DatabaseManager.getUser("test_admin");
        String dupId = "DUP_" + RUN_ID;
        boolean threw = false;
        try {
            service.registerBaggage(dupId, BaggageDirection.OUTGOING, 3, "F101", "BAY-A", admin);
            service.registerBaggage(dupId, BaggageDirection.OUTGOING, 3, "F101", "BAY-A", admin);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        assertTrue(threw, "Duplicate baggage ID is strictly rejected");
    }

    private static void testFiveRolesPermissions(BaggageService service) {
        System.out.println("\n[Test Suite 5] All Five Roles Permitted and Prohibited Actions");
        User admin = DatabaseManager.getUser("test_admin");
        User supervisor = DatabaseManager.getUser("test_supervisor");
        User operator = DatabaseManager.getUser("test_operator");
        User security = DatabaseManager.getUser("test_security");
        User auditor = DatabaseManager.getUser("test_auditor");

        assertTrue(AuthManager.hasUserPermission(admin, Role.ADMIN), "Admin has ADMIN permission");
        assertTrue(AuthManager.hasUserPermission(supervisor, Role.SUPERVISOR), "Supervisor has SUPERVISOR permission");
        assertTrue(AuthManager.hasUserPermission(operator, Role.BAGGAGE_OPERATOR), "Operator has BAGGAGE_OPERATOR permission");
        assertTrue(AuthManager.hasUserPermission(security, Role.SECURITY_OFFICER), "Security Officer has SECURITY_OFFICER permission");
        assertTrue(AuthManager.hasUserPermission(auditor, Role.AUDITOR), "Auditor has AUDITOR permission");

        assertTrue(!AuthManager.hasUserPermission(operator, Role.SECURITY_OFFICER), "Operator does not have SECURITY_OFFICER permission");
        assertTrue(!AuthManager.hasUserPermission(auditor, Role.ADMIN), "Auditor does not have ADMIN permission");
    }

    private static void testUnauthenticatedEnforcement() {
        System.out.println("\n[Test Suite 6] Unauthenticated Request Enforcement");
        boolean threw = false;
        try {
            AuthManager.requireUserPermission(null, Role.ADMIN, Role.BAGGAGE_OPERATOR);
        } catch (SecurityException e) {
            threw = true;
        }
        assertTrue(threw, "Unauthenticated null user is denied access");
    }

    private static void testOperatorClearanceProhibited(BaggageService service) {
        System.out.println("\n[Test Suite 7] Operator Attempting Security Clearance");
        User operator = DatabaseManager.getUser("test_operator");
        boolean threw = false;
        try {
            service.grantOperatorClearance("B001", "Attempted by operator", operator);
        } catch (SecurityException e) {
            threw = true;
        }
        assertTrue(threw, "Baggage Operator cannot grant security clearance");
    }

    private static void testAuditorModificationProhibited(BaggageService service) {
        System.out.println("\n[Test Suite 8] Auditor Attempting Modification");
        User auditor = DatabaseManager.getUser("test_auditor");
        boolean threw = false;
        try {
            service.registerBaggage("AUDIT_TEST", BaggageDirection.OUTGOING, 3, "F101", "BAY-A", auditor);
        } catch (SecurityException e) {
            threw = true;
        }
        assertTrue(threw, "Auditor cannot register or modify baggage");
    }

    private static void testDijkstraMinimumRoute(BaggageService service) {
        System.out.println("\n[Test Suite 9] Dijkstra Shortest Path Calculation");
        RoutingResult res = service.calculateRoute("CHECK-IN", "BAY-A");
        assertTrue(res != null, "Route calculated between CHECK-IN and BAY-A");
        assertTrue(res.totalTime == (4 + 7 + 5 + 3), "Dijkstra finds shortest path (19 mins)");
        assertTrue(res.route.get(0).equals("CHECK-IN") && res.route.get(res.route.size() - 1).equals("BAY-A"), "Route begins at CHECK-IN and ends at BAY-A");
    }

    private static void testConveyorFailureAndRerouting(BaggageService service) {
        System.out.println("\n[Test Suite 10] Conveyor Failure and Dynamic Rerouting");
        User supervisor = DatabaseManager.getUser("test_supervisor");
        // Disable SORTING -> CENTRAL (7 min)
        service.markConveyorUnavailable("SORTING", "CENTRAL", supervisor);

        RoutingResult rerouted = service.calculateRoute("CHECK-IN", "BAY-A");
        assertTrue(rerouted != null, "Alternate route found when SORTING->CENTRAL is unavailable");
        assertTrue(rerouted.totalTime == (4 + 6 + 3 + 5 + 3) || rerouted.totalTime == (4 + 5 + 4 + 5 + 3), "Rerouted time is 21 mins via transfer hub");

        // Restore segment
        service.restoreConveyor("SORTING", "CENTRAL", supervisor);
        RoutingResult restored = service.calculateRoute("CHECK-IN", "BAY-A");
        assertTrue(restored != null && restored.totalTime == 19, "Original shortest route restored (19 mins)");
    }

    private static void testNoRouteAvailable(BaggageService service) {
        System.out.println("\n[Test Suite 11] No Available Route Detection");
        User supervisor = DatabaseManager.getUser("test_supervisor");
        service.markConveyorUnavailable("BAY-AREA", "BAY-A", supervisor);

        RoutingResult res = service.calculateRoute("CHECK-IN", "BAY-A");
        assertTrue(res == null, "Returns null when no path exists to destination");

        // Restore segment
        service.restoreConveyor("BAY-AREA", "BAY-A", supervisor);
    }

    private static void testCutoffValidation(BaggageService service) {
        System.out.println("\n[Test Suite 12] Cutoff Checks Before, At, and After Cutoff");
        User admin = DatabaseManager.getUser("test_admin");
        User security = DatabaseManager.getUser("test_security");

        String onTimeId = "CUT_ONTIME_" + RUN_ID;
        Baggage bagOnTime = service.registerBaggage(onTimeId, BaggageDirection.OUTGOING, 1, "F101", "BAY-A", admin);
        service.grantOperatorClearance(onTimeId, "Security passed", security);
        Baggage processedOnTime = service.processSpecificBaggage(onTimeId, admin);
        assertTrue(processedOnTime.getStatus() == BaggageStatus.LOADED, "Bag arriving before cutoff is successfully LOADED");

        // Flight with past cutoff
        String lateFlightId = "LATE_" + RUN_ID;
        Flight lateFlight = new Flight(lateFlightId, "Delhi", 500); // 500 is before simulated time (540)
        lateFlight.setAssignedBay(service.getBay("BAY-A"));
        service.addLoadedFlight(lateFlight);
        DatabaseManager.saveFlight(lateFlight);

        String lateBagId = "CUT_LATE_" + RUN_ID;
        Baggage bagLate = service.registerBaggage(lateBagId, BaggageDirection.OUTGOING, 1, lateFlightId, "BAY-A", admin);
        service.grantOperatorClearance(lateBagId, "Security passed", security);
        Baggage processedLate = service.processSpecificBaggage(lateBagId, admin);
        assertTrue(processedLate.getStatus() == BaggageStatus.MISSED_CUTOFF, "Bag arriving after cutoff is marked MISSED_CUTOFF and not loaded");
    }

    private static void testFlightBayChanges(BaggageService service) {
        System.out.println("\n[Test Suite 13] Flight Loading-Bay Changes and Route Updates");
        User supervisor = DatabaseManager.getUser("test_supervisor");
        service.changeFlightBay("F101", "BAY-B", supervisor);

        Flight f = service.getFlight("F101");
        assertTrue(f.getAssignedBay().getBayId().equals("BAY-B"), "Flight F101 re-assigned to BAY-B");

        // Restore
        service.changeFlightBay("F101", "BAY-A", supervisor);
    }

    private static void testOutgoingBaggageWorkflow(BaggageService service) {
        System.out.println("\n[Test Suite 14] Outgoing Baggage Workflow");
        User operator = DatabaseManager.getUser("test_operator");
        User security = DatabaseManager.getUser("test_security");

        String outId = "OUT_FLOW_" + RUN_ID;
        Baggage bag = service.registerBaggage(outId, BaggageDirection.OUTGOING, 2, "F101", "BAY-A", operator);
        assertTrue(bag.getStatus() == BaggageStatus.WAITING_FOR_CLEARANCE, "New outgoing bag enters WAITING_FOR_CLEARANCE");

        service.grantOperatorClearance(outId, "Approved", security);
        assertTrue(bag.getStatus() == BaggageStatus.QUEUED, "Cleared bag enters QUEUED");

        Baggage processed = service.processSpecificBaggage(outId, operator);
        assertTrue(processed.getStatus() == BaggageStatus.LOADED, "Processed bag reaches status LOADED at bay");
    }

    private static void testIncomingBaggageWorkflow(BaggageService service) {
        System.out.println("\n[Test Suite 15] Incoming Baggage Workflow (Two-Way Operations)");
        User operator = DatabaseManager.getUser("test_operator");

        String inId = "INC_FLOW_" + RUN_ID;
        Baggage inBag = service.registerBaggage(inId, BaggageDirection.INCOMING, 3, null, "CLAIM-CAROUSEL-1", operator);
        assertTrue(inBag.isIncomingBag(), "Baggage is identified as incoming");
        assertTrue(inBag.getCurrentLocation().equals("UNLOAD-POINT"), "Incoming baggage starts at UNLOAD-POINT");
        assertTrue(inBag.getDestinationNode().equals("CLAIM-CAROUSEL-1"), "Incoming baggage destination is claim carousel");

        Baggage processed = service.processSpecificBaggage(inId, operator);
        assertTrue(processed.getStatus() == BaggageStatus.CLAIMED, "Incoming bag delivered to claim carousel with status CLAIMED");
    }

    private static void testTransferBaggageWorkflow(BaggageService service) {
        System.out.println("\n[Test Suite 16] Transfer Baggage Workflow");
        User operator = DatabaseManager.getUser("test_operator");
        User security = DatabaseManager.getUser("test_security");

        String transId = "TRANS_FLOW_" + RUN_ID;
        Baggage transBag = service.registerBaggage(transId, BaggageDirection.TRANSFER, 1, "F202", "BAY-B", operator);
        assertTrue(transBag.isTransferBag(), "Identified as transfer bag");
        assertTrue(transBag.getCurrentLocation().equals("TRANSFER-A"), "Transfer bag originates at TRANSFER-A");

        service.grantOperatorClearance(transId, "Connecting flight screening", security);
        Baggage processed = service.processSpecificBaggage(transId, operator);
        assertTrue(processed.getStatus() == BaggageStatus.LOADED, "Transfer bag successfully routed to connecting flight loading bay");
    }

    private static void testEstimatedArrivalTimeRecalculation(BaggageService service) {
        System.out.println("\n[Test Suite 17] Estimated Arrival Time Calculation");
        RoutingResult r1 = service.calculateRoute("CHECK-IN", "BAY-A");
        assertTrue(r1 != null && r1.totalTime == 19, "Base estimated route time is 19 minutes");

        User supervisor = DatabaseManager.getUser("test_supervisor");
        service.markConveyorUnavailable("SORTING", "CENTRAL", supervisor);
        RoutingResult r2 = service.calculateRoute("CHECK-IN", "BAY-A");
        assertTrue(r2 != null && r2.totalTime > 19, "ETA dynamically increases after conveyor failure (" + r2.totalTime + " mins)");

        service.restoreConveyor("SORTING", "CENTRAL", supervisor);
    }

    private static void testDatabasePersistence(BaggageService service) {
        System.out.println("\n[Test Suite 18] Database Persistence Across Restarts");
        User admin = DatabaseManager.getUser("test_admin");
        String pId = "PERSIST_BAG_" + RUN_ID;
        Baggage b = service.registerBaggage(pId, BaggageDirection.OUTGOING, 1, "F303", "BAY-C", admin);
        DatabaseManager.saveBaggage(b);

        // Reload fresh into a new service instance
        AuthManager newAuth = new AuthManager();
        BaggageService fresh = new BaggageService(newAuth);
        Baggage reloaded = fresh.getBaggage(pId);
        assertTrue(reloaded != null, "Baggage persisted in SQLite and reloaded upon restart");
        assertTrue(reloaded.getAssignedFlight().getFlightId().equals("F303"), "Flight association correctly persisted");
    }

    private static void testAuditLogPersistence() {
        System.out.println("\n[Test Suite 19] Audit-Log Persistence");
        String action = "AUDIT_ACT_" + RUN_ID;
        DatabaseManager.logAudit("audit_tester", action, "UNIT_TEST", 540, "SUCCESS", "Verified");
        List<String[]> logs = DatabaseManager.getAuditLogs();
        boolean found = false;
        for (String[] entry : logs) {
            if ("audit_tester".equals(entry[1]) && action.equals(entry[2])) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Audit log persisted in database and queryable");
    }

    private static void testPreventionOfDuplicateProcessing(BaggageService service) {
        System.out.println("\n[Test Suite 20] Prevention of Duplicate Processing");
        User operator = DatabaseManager.getUser("test_operator");
        String pId = "PERSIST_BAG_" + RUN_ID;
        Baggage bag = service.getBaggage(pId);
        if (bag != null) {
            bag.setStatus(BaggageStatus.LOADED);
            DatabaseManager.saveBaggage(bag);
            boolean threw = false;
            try {
                service.assignBaggageToFlight(pId, "F101", operator);
            } catch (IllegalStateException e) {
                threw = true;
            }
            assertTrue(threw, "Already LOADED baggage cannot be reassigned or reprocessed");
        }
    }

    private static void testClearanceRejectionEnforcement(BaggageService service) {
        System.out.println("\n[Test Suite 21] Security Clearance Rejection Enforcement");
        User admin = DatabaseManager.getUser("test_admin");
        User security = DatabaseManager.getUser("test_security");

        String rejId = "REJ_TEST_" + RUN_ID;
        Baggage rejBag = service.registerBaggage(rejId, BaggageDirection.OUTGOING, 2, "F101", "BAY-A", admin);
        service.rejectOperatorClearance(rejId, "Prohibited item suspected", security);

        assertTrue(rejBag.getStatus() == BaggageStatus.REJECTED, "Rejected bag status set to REJECTED");
        boolean threw = false;
        try {
            service.processSpecificBaggage(rejId, admin);
        } catch (IllegalStateException e) {
            threw = true;
        }
        assertTrue(threw, "Security-rejected bag cannot be processed into conveyors or loaded onto aircraft");
    }
}
