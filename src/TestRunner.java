import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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
        testFuzzyUrgencyScoring();
        testCutoffRemainsPrimaryPriority(service);
        testRedisCache(service);

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
        // Initial setup creates administrator via security provisioning
        String[] hs = AuthManager.hashPassword("admin123");
        User initialAdmin = new User("admin", hs[0], hs[1], Role.ADMIN, false, true, null);
        DatabaseManager.saveUser(initialAdmin);

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

        // Administrator provisions operational manager and passenger accounts
        String[] hsMgr = AuthManager.hashPassword("manager123");
        DatabaseManager.saveUser(new User("manager", hsMgr[0], hsMgr[1], Role.BAGGAGE_MANAGER, true, true, null));

        String[] hsPass = AuthManager.hashPassword("pass123");
        DatabaseManager.saveUser(new User("ramesh", hsPass[0], hsPass[1], Role.PASSENGER, false, true, "P101"));
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

    // 17. Fuzzy logic urgency scoring
    private static void testFuzzyUrgencyScoring() {
        System.out.println("\n[Test Suite 17] Fuzzy Logic Urgency Scoring");

        Flight f = new Flight("FUZZ", "MAA", "Test", 720, 650);

        OriginBag criticalBag = new OriginBag("FUZZ-CRIT", "P101", "Ramesh Kumar", PassengerCategory.ECONOMY, 1);
        criticalBag.assignFlight(f);
        criticalBag.setLoadingCutoff(560); // 20 minutes remaining at 09:00
        criticalBag.setEstimatedTime(35);
        criticalBag.setQueuedSince(540);
        FuzzyUrgency.Result critical = FuzzyUrgency.evaluate(criticalBag, 540);
        assertTrue(critical.eligible, "Outgoing baggage with a cutoff is urgency-eligible");
        assertTrue(critical.score >= 85 && "CRITICAL".equals(critical.label),
                "Low remaining time + high travel time -> CRITICAL urgency (" + critical.score + ")");

        OriginBag easyBag = new OriginBag("FUZZ-EASY", "P101", "Ramesh Kumar", PassengerCategory.ECONOMY, 2);
        easyBag.setLoadingCutoff(720); // 180 minutes remaining
        easyBag.setEstimatedTime(8);
        easyBag.setQueuedSince(540);
        FuzzyUrgency.Result easy = FuzzyUrgency.evaluate(easyBag, 540);
        assertTrue(easy.score < 35 && "LOW".equals(easy.label),
                "High remaining time + low travel time -> LOW urgency (" + easy.score + ")");

        OriginBag time20 = urgencyBag("FUZZ-T20", 560, 35, 540);
        OriginBag time40 = urgencyBag("FUZZ-T40", 580, 35, 540);
        OriginBag time180 = urgencyBag("FUZZ-T180", 720, 35, 540);
        assertTrue(time20.getUrgencyScore() >= time40.getUrgencyScore()
                        && time40.getUrgencyScore() >= time180.getUrgencyScore(),
                "Urgency does not decrease when the remaining cutoff time increases");

        OriginBag travel5 = urgencyBag("FUZZ-V5", 560, 5, 540);
        OriginBag travel25 = urgencyBag("FUZZ-V25", 560, 25, 540);
        OriginBag travel35 = urgencyBag("FUZZ-V35", 560, 35, 540);
        assertTrue(travel5.getUrgencyScore() <= travel25.getUrgencyScore()
                        && travel25.getUrgencyScore() <= travel35.getUrgencyScore(),
                "Urgency does not decrease when travel time increases");

        OriginBag outgoing = urgencyBag("FUZZ-OUT", 600, 19, 540);
        TransferBag transfer = new TransferBag("FUZZ-TR", "P101", "Ramesh Kumar", PassengerCategory.ECONOMY, 9);
        transfer.setLoadingCutoff(600);
        transfer.setEstimatedTime(19);
        transfer.setQueuedSince(540);
        int outgoingScore = FuzzyUrgency.evaluate(outgoing, 540).score;
        int transferScore = FuzzyUrgency.evaluate(transfer, 540).score;
        assertTrue(transferScore > outgoingScore,
                "High transfer urgency raises the score above an equivalent outgoing bag (" + transferScore + " > " + outgoingScore + ")");

        OriginBag noWait = urgencyBag("FUZZ-W0", 660, 19, 540);
        OriginBag longWait = urgencyBag("FUZZ-W50", 660, 19, 490);
        int noWaitScore = FuzzyUrgency.evaluate(noWait, 540).score;
        int longWaitScore = FuzzyUrgency.evaluate(longWait, 540).score;
        assertTrue(longWaitScore > noWaitScore,
                "A longer processing/waiting delay raises urgency (" + longWaitScore + " > " + noWaitScore + ")");

        IncomingBag incoming = new IncomingBag("FUZZ-IN", "P101", "Ramesh Kumar", PassengerCategory.ECONOMY, 10);
        incoming.setEstimatedTime(12);
        assertTrue(!FuzzyUrgency.evaluate(incoming, 540).eligible,
                "Incoming baggage is not eligible for departure-cutoff urgency");
    }

    private static OriginBag urgencyBag(String id, int cutoff, int travel, int queuedSince) {
        OriginBag bag = new OriginBag(id, "P101", "Ramesh Kumar", PassengerCategory.ECONOMY, 1);
        bag.setLoadingCutoff(cutoff);
        bag.setEstimatedTime(travel);
        bag.setQueuedSince(queuedSince);
        bag.setUrgency(FuzzyUrgency.evaluate(bag, 540));
        return bag;
    }

    // 18. Earliest cutoff must remain the primary PriorityQueue key
    private static void testCutoffRemainsPrimaryPriority(BaggageService service) {
        System.out.println("\n[Test Suite 18] Earliest Loading Cutoff Remains Primary Priority");
        User manager = DatabaseManager.getUser("manager");

        Baggage earliest = service.registerBaggage("TEST-PRI-LOW", "P101", "Ramesh Kumar",
                BaggageDirection.OUTGOING, 3, "AI101", "MAA", "DEL", "BAY-A", manager);
        Baggage later = service.registerBaggage("TEST-PRI-HIGH", "P101", "Ramesh Kumar",
                BaggageDirection.OUTGOING, 3, "AI101", "MAA", "DEL", "BAY-A", manager);
        service.grantClearance("TEST-PRI-LOW", "Priority test clearance", manager);
        service.grantClearance("TEST-PRI-HIGH", "Priority test clearance", manager);

        // Earliest cutoff gets a deliberately low fuzzy score; later cutoff gets high urgency.
        earliest.setLoadingCutoff(600);
        earliest.setEstimatedTime(5);
        earliest.setQueuedSince(540);
        later.setLoadingCutoff(601);
        later.setEstimatedTime(35);
        later.setQueuedSince(480);
        DatabaseManager.saveBaggage(earliest);
        DatabaseManager.saveBaggage(later);
        service.refreshUrgencies();

        assertTrue(earliest.getUrgencyScore() < later.getUrgencyScore(),
                "Test setup produces a lower fuzzy score for the earliest-cutoff bag");

        Baggage next = service.peekNextBaggage();
        assertTrue(next != null && "TEST-PRI-LOW".equals(next.getBagId()),
                "Earliest loading cutoff remains first even when fuzzy urgency is lower");

        service.processSpecificBaggage("TEST-PRI-LOW", manager);
        Baggage nextAfter = service.peekNextBaggage();
        assertTrue(nextAfter != null && "TEST-PRI-HIGH".equals(nextAfter.getBagId()),
                "Within later cutoffs, the higher fuzzy urgency bag is dispatched next");
        service.processSpecificBaggage("TEST-PRI-HIGH", manager);
    }

    // 19. Redis cache-aside, invalidation and graceful fallback
    private static void testRedisCache(BaggageService service) {
        System.out.println("\n[Test Suite 19] Redis Cache-Aside, Invalidation and Fallback");
        assertTrue(RedisCache.getTtlSeconds() >= 30 && RedisCache.getTtlSeconds() <= 60,
                "Redis TTL is a short 30-60 second fallback (" + RedisCache.getTtlSeconds() + "s)");

        MockRedisServer server = null;
        try {
            server = new MockRedisServer();
            RedisCache.setEndpointForTesting("127.0.0.1", server.getPort());
            RedisCache.resetStatsForTesting();

            AtomicInteger loads = new AtomicInteger();
            String key = RedisCache.baggageTrackKey("CACHE-BAG-1");
            RedisCache.Lookup first = RedisCache.fetch(key, () -> {
                loads.incrementAndGet();
                return "{\"currentLocation\":\"CHECK-IN\"}";
            });
            RedisCache.Lookup second = RedisCache.fetch(key, () -> {
                loads.incrementAndGet();
                return "{\"currentLocation\":\"CHECK-IN\"}";
            });
            assertTrue(!first.hit, "First baggage tracking request is a cache miss");
            assertTrue(second.hit, "Second baggage tracking request is a cache hit");
            assertTrue(loads.get() == 1, "Cache hit avoided a second database/data load");
            assertTrue(((Number) RedisCache.getStats().get("hits")).longValue() == 1,
                    "Redis hit counter records the served cached record");

            // Position update through the real service must invalidate the old cache entry.
            User manager = DatabaseManager.getUser("manager");
            Baggage moving = service.registerBaggage("TEST-CACHE-BAG", "P101", "Ramesh Kumar",
                    BaggageDirection.OUTGOING, 2, "AI101", "MAA", "DEL", "BAY-A", manager);
            service.grantClearance("TEST-CACHE-BAG", "Cache test clearance", manager);
            String movingKey = RedisCache.baggageTrackKey("TEST-CACHE-BAG");
            RedisCache.put(movingKey, "{\"currentLocation\":\"CHECK-IN\"}");
            assertTrue(RedisCache.fetch(movingKey, () -> "{}").hit,
                    "Old baggage position was cached before the update");

            service.stepBaggageNextNode("TEST-CACHE-BAG", manager);
            assertTrue("SORTING".equals(moving.getCurrentLocation()),
                    "Baggage position updated through the simulated conveyor step");

            RedisCache.Lookup fresh = RedisCache.fetch(movingKey,
                    () -> "{\"currentLocation\":\"" + moving.getCurrentLocation() + "\"}");
            assertTrue(!fresh.hit && fresh.value.contains("SORTING") && !fresh.value.contains("CHECK-IN"),
                    "Cache invalidation prevents an old baggage position from being displayed");

            // Flight and conveyor caches follow the same cache-aside pattern.
            RedisCache.put(RedisCache.FLIGHTS_KEY, "[{\"flightId\":\"AI101\"}]");
            assertTrue(RedisCache.fetch(RedisCache.FLIGHTS_KEY, () -> "[]").hit,
                    "Flight details are served from Redis on a hit");
            RedisCache.invalidateFlights();
            RedisCache.Lookup flightMiss = RedisCache.fetch(RedisCache.FLIGHTS_KEY, () -> "[]");
            assertTrue(!flightMiss.hit && "[]".equals(flightMiss.value),
                    "Flight changes invalidate cached flight details");

            RedisCache.put(RedisCache.CONVEYORS_KEY, "[{\"fromNode\":\"CHECK-IN\"}]");
            assertTrue(RedisCache.fetch(RedisCache.CONVEYORS_KEY, () -> "[]").hit,
                    "Conveyor network information is served from Redis on a hit");
            RedisCache.invalidateConveyors();
            RedisCache.Lookup conveyorMiss = RedisCache.fetch(RedisCache.CONVEYORS_KEY, () -> "[]");
            assertTrue(!conveyorMiss.hit && "[]".equals(conveyorMiss.value),
                    "Conveyor availability changes invalidate cached network information");

            // Reloading from SQLite shows the new position, so tracking still follows the DB.
            BaggageService reloaded = new BaggageService(new AuthManager());
            Baggage persisted = reloaded.getBaggage("TEST-CACHE-BAG");
            assertTrue(persisted != null && "SORTING".equals(persisted.getCurrentLocation()),
                    "Passenger tracking reflects the updated position after a SQLite reload");
        } catch (IOException e) {
            assertTrue(false, "Mock Redis server could not start: " + e.getMessage());
        } finally {
            if (server != null) server.stop();
            RedisCache.resetEndpointForTesting();
        }

        // Point at a closed port to prove the application keeps working without Redis.
        RedisCache.setEndpointForTesting("127.0.0.1", 1);
        AtomicInteger fallbackLoads = new AtomicInteger();
        RedisCache.Lookup fallback = RedisCache.fetch("fallback:verification",
                () -> {
                    fallbackLoads.incrementAndGet();
                    return "{\"source\":\"SQLITE\"}";
                });
        assertTrue(!fallback.hit && fallback.value.contains("SQLITE") && fallbackLoads.get() == 1,
                "Application continues with the SQLite fallback when Redis is unavailable");
        RedisCache.resetEndpointForTesting();
    }

    // Minimal in-process RESP server used only by the verification suite.
    static class MockRedisServer {
        private final ServerSocket serverSocket;
        private final Map<String, String> store = new ConcurrentHashMap<>();
        private volatile boolean running = true;

        MockRedisServer() throws IOException {
            serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            Thread thread = new Thread(() -> {
                while (running) {
                    try (Socket socket = serverSocket.accept()) {
                        handle(socket);
                    } catch (IOException e) {
                        if (running) System.err.println("Mock Redis error: " + e.getMessage());
                    }
                }
            }, "mock-redis-server");
            thread.setDaemon(true);
            thread.start();
        }

        int getPort() {
            return serverSocket.getLocalPort();
        }

        void stop() {
            running = false;
            try {
                serverSocket.close();
            } catch (IOException ignored) {}
        }

        private void handle(Socket socket) throws IOException {
            InputStream in = new BufferedInputStream(socket.getInputStream());
            OutputStream out = new BufferedOutputStream(socket.getOutputStream());

            while (running) {
                String[] command = readCommand(in);
                if (command == null || command.length == 0) return;

                String name = command[0].toUpperCase();
                String reply;
                if ("GET".equals(name)) {
                    String value = store.get(command[1]);
                    reply = value == null ? "$-1\r\n" : bulk(value);
                } else if ("SETEX".equals(name)) {
                    store.put(command[1], command[3]);
                    reply = "+OK\r\n";
                } else if ("DEL".equals(name)) {
                    int removed = 0;
                    for (int i = 1; i < command.length; i++) {
                        if (store.remove(command[i]) != null) removed++;
                    }
                    reply = ":" + removed + "\r\n";
                } else if ("QUIT".equals(name)) {
                    out.write("+OK\r\n".getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    return;
                } else {
                    reply = "+OK\r\n";
                }

                out.write(reply.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        }

        private String[] readCommand(InputStream in) throws IOException {
            int first = in.read();
            if (first == -1) return null;
            if (first != '*') return null;

            int argc = Integer.parseInt(readLine(in));
            String[] parts = new String[argc];
            for (int i = 0; i < argc; i++) {
                int type = in.read();
                if (type != '$') throw new IOException("Expected bulk string");
                int length = Integer.parseInt(readLine(in));
                byte[] data = readFully(in, length);
                readFully(in, 2);
                parts[i] = new String(data, StandardCharsets.UTF_8);
            }
            return parts;
        }

        private static String readLine(InputStream in) throws IOException {
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            int b;
            while ((b = in.read()) != -1) {
                if (b == '\r') {
                    int next = in.read();
                    if (next != '\n') throw new IOException("Malformed mock Redis line ending");
                    break;
                }
                line.write(b);
            }
            if (b == -1) throw new EOFException("Mock Redis connection closed");
            return line.toString(StandardCharsets.UTF_8.name());
        }

        private static byte[] readFully(InputStream in, int length) throws IOException {
            byte[] data = new byte[length];
            int offset = 0;
            while (offset < length) {
                int read = in.read(data, offset, length - offset);
                if (read == -1) throw new EOFException("Mock Redis connection closed");
                offset += read;
            }
            return data;
        }

        private static String bulk(String value) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            return "$" + bytes.length + "\r\n" + value + "\r\n";
        }
    }
}
