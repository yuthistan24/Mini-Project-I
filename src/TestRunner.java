import java.io.File;
import java.util.List;

public class TestRunner {
    private static int passed = 0;
    private static int failed = 0;
    private static final String TEST_DB_URL = "jdbc:sqlite:data/test_airport.db";

    public static void main(String[] args) {
        System.out.println("\n=======================================================");
        System.out.println("   VAANAM AIRPORT BAGGAGE SYSTEM - VERIFICATION TEST   ");
        System.out.println("=======================================================");

        // Isolate tests to a clean test database so production data is never polluted
        File testDbFile = new File("data/test_airport.db");
        if (testDbFile.exists()) {
            testDbFile.delete();
        }

        DatabaseManager.setDatabaseUrl(TEST_DB_URL);
        DatabaseManager.initializeDatabase();

        AuthManager auth = new AuthManager();
        BaggageService service = new BaggageService(auth);

        // Run the 16 core functional verification test suites
        testAdminSetupAndLogin();
        testAdminAccountCreation();
        testBaggageManagerPermittedOperations(service);
        testPassengerLoginAndOwnBaggage(service);
        testRejectionOfOtherPassengerRecords(service);
        testBaggageRegistrationWithPassenger(service);
        testTrackingByBaggageId(service);
        testMultipleBagsOnePassenger(service);
        testIncomingOutgoingTransferWorkflows(service);
        testRouteCalculationAndETA(service);
        testConveyorFailureAndRerouting(service);
        testCutoffValidation(service);
        testDatabasePersistence();
        testRoleRestrictionsAndClearanceAuthority(service);
        testDuplicateIdRejectionAndAudit(service);
        testSampleDataConsistency(service);

        // Clean up test database file
        DatabaseManager.resetDatabaseUrl();
        if (testDbFile.exists()) {
            testDbFile.delete();
        }

        System.out.println("\n=======================================================");
        System.out.println("FINAL VERIFICATION RESULTS: " + passed + " Passed, " + failed + " Failed.");
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

    // 1. Initial administrator setup and login
    private static void testAdminSetupAndLogin() {
        System.out.println("\n[Test Suite 1] Initial Administrator Setup and Login");
        User admin = DatabaseManager.getUser("admin");
        assertTrue(admin != null, "Default admin user exists");
        assertTrue(admin.getRole() == Role.ADMIN, "Admin account has ADMIN role");
        assertTrue(AuthManager.verifyPassword("admin123", admin.getPasswordHash(), admin.getPasswordSalt()),
                "Admin authenticates successfully with correct PBKDF2 password");
        assertTrue(!AuthManager.verifyPassword("wrongpass", admin.getPasswordHash(), admin.getPasswordSalt()),
                "Admin login rejected with incorrect password");
    }

    // 2. Administrator account creation
    private static void testAdminAccountCreation() {
        System.out.println("\n[Test Suite 2] Administrator Account Creation");
        String[] hs = AuthManager.hashPassword("newpass123");
        User newStaff = new User("staff_user", hs[0], hs[1], Role.BAGGAGE_MANAGER, true, true, null);
        DatabaseManager.saveUser(newStaff);

        User retrieved = DatabaseManager.getUser("staff_user");
        assertTrue(retrieved != null, "Admin-created staff account saved in database");
        assertTrue(retrieved.getRole() == Role.BAGGAGE_MANAGER, "Role correctly assigned as BAGGAGE_MANAGER");
        assertTrue(retrieved.hasClearanceAuthority(), "Clearance authority flag properly stored");
    }

    // 3. Baggage-manager login and permitted operations
    private static void testBaggageManagerPermittedOperations(BaggageService service) {
        System.out.println("\n[Test Suite 3] Baggage Manager Login and Operations");
        User manager = DatabaseManager.getUser("manager");
        assertTrue(manager != null, "Manager user exists");
        assertTrue(manager.getRole() == Role.BAGGAGE_MANAGER, "Manager has BAGGAGE_MANAGER role");
        assertTrue(AuthManager.hasUserPermission(manager, Role.BAGGAGE_MANAGER), "Manager possesses BAGGAGE_MANAGER permission");

        // Manager can register bags
        Baggage b = service.registerBaggage("TEST-BM-01", "P101", "Ramesh Kumar", BaggageDirection.OUTGOING,
                2, "AI101", "MAA", "DEL", "BAY-A", manager);
        assertTrue(b != null, "Baggage Manager successfully registered baggage record");
    }

    // 4. Passenger login and access to their own baggage
    private static void testPassengerLoginAndOwnBaggage(BaggageService service) {
        System.out.println("\n[Test Suite 4] Passenger Login and Access to Own Baggage");
        User ramesh = DatabaseManager.getUser("ramesh");
        assertTrue(ramesh != null, "Passenger account 'ramesh' exists");
        assertTrue(ramesh.getRole() == Role.PASSENGER, "Role is PASSENGER");
        assertTrue("P101".equals(ramesh.getLinkedPassengerId()), "Account linked to Passenger ID P101");

        List<Baggage> rameshBags = service.getBaggageForPassenger(ramesh.getLinkedPassengerId());
        assertTrue(!rameshBags.isEmpty(), "Passenger P101 can retrieve their baggage");
        boolean allBelongToRamesh = true;
        for (Baggage bag : rameshBags) {
            if (!"P101".equals(bag.getPassengerId())) allBelongToRamesh = false;
        }
        assertTrue(allBelongToRamesh, "All retrieved bags belong strictly to passenger P101");
    }

    // 5. Rejection of access to another passenger's private records
    private static void testRejectionOfOtherPassengerRecords(BaggageService service) {
        System.out.println("\n[Test Suite 5] Rejection of Access to Other Passengers' Records");
        User ramesh = DatabaseManager.getUser("ramesh"); // Linked to P101

        boolean caughtAccessViolation = false;
        try {
            // Attempt to access Priya Sharma's bag (P102)
            AuthManager.requirePassengerAccess(ramesh, "P102");
        } catch (SecurityException e) {
            caughtAccessViolation = true;
        }
        assertTrue(caughtAccessViolation, "Java backend strictly rejects passenger attempting to access another passenger's records");
    }

    // 6. Baggage registration with passenger and flight association
    private static void testBaggageRegistrationWithPassenger(BaggageService service) {
        System.out.println("\n[Test Suite 6] Baggage Registration with Passenger & Flight");
        User manager = DatabaseManager.getUser("manager");
        Baggage bag = service.registerBaggage("BAG-TEST-REG", "P104", "Ananya Iyer", BaggageDirection.OUTGOING,
                2, "UK404", "MAA", "DXB", "BAY-D", manager);

        assertTrue("P104".equals(bag.getPassengerId()), "Baggage record contains valid Passenger ID");
        assertTrue("Ananya Iyer".equals(bag.getPassengerName()), "Baggage record contains correct passenger name");
        assertTrue(bag.getAssignedFlight() != null && "UK404".equals(bag.getAssignedFlight().getFlightId()),
                "Baggage linked to validated flight UK404");
        assertTrue("BAY-D".equals(bag.getDestinationNode()), "Destination node resolves to flight loading bay BAY-D");
    }

    // 7. Tracking by baggage ID
    private static void testTrackingByBaggageId(BaggageService service) {
        System.out.println("\n[Test Suite 7] Tracking by Baggage ID");
        Baggage bag = service.getBaggage("BAG-101");
        assertTrue(bag != null, "Baggage BAG-101 found by ID");
        assertTrue(bag.getCurrentLocation() != null, "Current location is confirmed (" + bag.getCurrentLocation() + ")");
        assertTrue(bag.getStatus() != null, "Valid status reported (" + bag.getStatus() + ")");
    }

    // 8. Multiple bags belonging to one passenger
    private static void testMultipleBagsOnePassenger(BaggageService service) {
        System.out.println("\n[Test Suite 8] Multiple Bags Belonging to One Passenger");
        List<Baggage> priyaBags = service.getBaggageForPassenger("P102"); // Priya Sharma
        assertTrue(priyaBags.size() >= 2, "Passenger P102 has multiple associated bags (" + priyaBags.size() + " bags)");

        List<Baggage> rameshBags = service.getBaggageForPassenger("P101"); // Ramesh Kumar
        assertTrue(rameshBags.size() >= 2, "Passenger P101 has multiple associated bags (" + rameshBags.size() + " bags)");
    }

    // 9. Incoming, outgoing, and transfer workflows
    private static void testIncomingOutgoingTransferWorkflows(BaggageService service) {
        System.out.println("\n[Test Suite 9] Incoming, Outgoing, and Transfer Workflows");
        Baggage outBag = service.getBaggage("BAG-102");
        Baggage inBag = service.getBaggage("BAG-114");
        Baggage transBag = service.getBaggage("BAG-115");

        assertTrue(outBag != null && outBag.isOutgoingBag(), "Outgoing workflow baggage identified");
        assertTrue(inBag != null && inBag.isIncomingBag(), "Incoming workflow baggage identified");
        assertTrue(transBag != null && transBag.isTransferBag(), "Transfer workflow baggage identified");

        assertTrue("UNLOAD-POINT".equals(inBag.getCurrentLocation()), "Incoming bag originates at UNLOAD-POINT");
        assertTrue("CLAIM-CAROUSEL-1".equals(inBag.getDestinationNode()), "Incoming bag routes to Claim Carousel");
        assertTrue("TRANSFER-A".equals(transBag.getCurrentLocation()), "Transfer bag originates at TRANSFER-A");
    }

    // 10. Route display and expected arrival time
    private static void testRouteCalculationAndETA(BaggageService service) {
        System.out.println("\n[Test Suite 10] Dijkstra Route Calculation and ETA");
        RoutingResult res = service.calculateRoute("CHECK-IN", "BAY-A");
        assertTrue(res != null, "Shortest path calculated successfully");
        assertTrue(res.totalTime == 19, "Optimal shortest route processing time is 19 minutes");
        assertTrue(res.route.contains("CHECK-IN") && res.route.contains("BAY-A"),
                "Route traverses from CHECK-IN to BAY-A");
    }

    // 11. Conveyor failure and dynamic rerouting
    private static void testConveyorFailureAndRerouting(BaggageService service) {
        System.out.println("\n[Test Suite 11] Conveyor Failure and Dynamic Rerouting");
        User manager = DatabaseManager.getUser("manager");

        // Mark primary segment offline
        service.markConveyorUnavailable("SORTING", "CENTRAL", manager);
        RoutingResult rerouted = service.calculateRoute("CHECK-IN", "BAY-A");
        assertTrue(rerouted != null, "Dijkstra finds alternate path via transfer hub");
        assertTrue(rerouted.totalTime == 21, "Rerouted processing time is 21 minutes");
        assertTrue(rerouted.route.contains("TRANSFER-B") || rerouted.route.contains("TRANSFER-A"),
                "Rerouted path avoids failed segment and utilizes transit bypass");

        // Restore segment
        service.restoreConveyor("SORTING", "CENTRAL", manager);
        RoutingResult restored = service.calculateRoute("CHECK-IN", "BAY-A");
        assertTrue(restored.totalTime == 19, "Route restores to optimal 19 minutes when segment comes online");
    }

    // 12. Loading cutoff validation
    private static void testCutoffValidation(BaggageService service) {
        System.out.println("\n[Test Suite 12] Loading Cutoff Validation");
        User manager = DatabaseManager.getUser("manager");

        // Outgoing bag on time (Simulated Time 540 (09:00 AM) + 19 min route = 559 <= Cutoff 630 (10:30 AM))
        Baggage onTimeBag = service.registerBaggage("TEST-ONTIME", "P101", "Ramesh Kumar", BaggageDirection.OUTGOING,
                1, "AI101", "MAA", "DEL", "BAY-A", manager);
        service.grantClearance(onTimeBag.getBagId(), "Clearance passed", manager);
        service.processSpecificBaggage(onTimeBag.getBagId(), manager);
        assertTrue(onTimeBag.getStatus() == BaggageStatus.LOADED, "On-time baggage transitions to LOADED");

        // Outgoing bag violating cutoff
        Baggage lateBag = service.registerBaggage("TEST-LATE", "P101", "Ramesh Kumar", BaggageDirection.OUTGOING,
                3, "AI101", "MAA", "DEL", "BAY-A", manager);
        lateBag.setLoadingCutoff(500); // Cutoff 08:20 AM in the past relative to 09:00 AM
        service.grantClearance(lateBag.getBagId(), "Clearance passed", manager);
        service.processSpecificBaggage(lateBag.getBagId(), manager);
        assertTrue(lateBag.getStatus() == BaggageStatus.MISSED_CUTOFF, "Late baggage flagged MISSED_CUTOFF and prevented from loading");
    }

    // 13. Database persistence after restart
    private static void testDatabasePersistence() {
        System.out.println("\n[Test Suite 13] Database Persistence Across Restarts");
        AuthManager newAuth = new AuthManager();
        BaggageService newService = new BaggageService(newAuth);

        Baggage persisted = newService.getBaggage("BAG-101");
        assertTrue(persisted != null, "Baggage record persisted in SQLite and reloaded upon restart");
        assertTrue("P101".equals(persisted.getPassengerId()), "Passenger relationship persisted");
        assertTrue("Ramesh Kumar".equals(persisted.getPassengerName()), "Passenger name persisted");
    }

    // 14. Role restrictions and clearance authority
    private static void testRoleRestrictionsAndClearanceAuthority(BaggageService service) {
        System.out.println("\n[Test Suite 14] Role Restrictions and Clearance Authority");
        User admin = DatabaseManager.getUser("admin");
        User ramesh = DatabaseManager.getUser("ramesh"); // PASSENGER
        User manager = DatabaseManager.getUser("manager");

        // Passenger cannot process baggage
        boolean passengerDenied = false;
        try {
            service.processNextBaggage(ramesh);
        } catch (SecurityException e) {
            passengerDenied = true;
        }
        assertTrue(passengerDenied, "Passenger prohibited from processing baggage");

        // Admin without clearance authority cannot grant clearance
        boolean adminClearanceDenied = false;
        try {
            service.grantClearance("BAG-105", "Admin override", admin);
        } catch (SecurityException e) {
            adminClearanceDenied = true;
        }
        assertTrue(adminClearanceDenied, "Admin without certified clearance authority cannot grant security clearance");

        // Certified manager CAN grant clearance
        service.grantClearance("BAG-105", "Certified clearance granted", manager);
        Baggage b105 = service.getBaggage("BAG-105");
        assertTrue(b105.isOperatorCleared(), "Certified Baggage Manager successfully grants clearance");
    }

    // 15. Duplicate ID rejection and audit logging
    private static void testDuplicateIdRejectionAndAudit(BaggageService service) {
        System.out.println("\n[Test Suite 15] Duplicate ID Rejection and Audit Logging");
        User manager = DatabaseManager.getUser("manager");

        boolean threwDuplicate = false;
        try {
            service.registerBaggage("BAG-101", "P101", "Ramesh Kumar", BaggageDirection.OUTGOING,
                    2, "AI101", "MAA", "DEL", "BAY-A", manager);
        } catch (IllegalArgumentException e) {
            threwDuplicate = true;
        }
        assertTrue(threwDuplicate, "Duplicate Baggage ID rejected with exception");

        List<String[]> auditLogs = DatabaseManager.getAuditLogs();
        assertTrue(!auditLogs.isEmpty(), "Audit log contains persisted operational records (" + auditLogs.size() + " logs)");
    }

    // 16. Sample-data consistency
    private static void testSampleDataConsistency(BaggageService service) {
        System.out.println("\n[Test Suite 16] Sample Data Consistency Verification");
        assertTrue(service.getAllBaggage().size() >= 30,
                "Sample dataset contains at least 30 synthetic baggage records (" + service.getAllBaggage().size() + " records)");

        boolean allValid = true;
        for (Baggage b : service.getAllBaggage().values()) {
            if (b.getPassengerId() == null || b.getPassengerId().isEmpty()) allValid = false;
            if (b.getCurrentLocation() == null || b.getCurrentLocation().isEmpty()) allValid = false;
            if (b.getStatus() == null) allValid = false;
        }
        assertTrue(allValid, "Every sample baggage record contains valid passenger, location, and status attributes");
    }
}
