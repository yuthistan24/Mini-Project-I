import java.util.*;

public class BaggageService {
    private HashMap<String, Flight> flights;
    private HashMap<String, LoadingBay> bays;
    private HashMap<String, Passenger> passengers;
    private HashMap<String, Baggage> allBaggage;

    private PriorityQueue<Baggage> processQueue;
    private ArrayDeque<Baggage> conveyorWaitingQueue;
    private ConveyorRoutePlanner conveyorPlanner;

    private int simulatedTime; // Minutes from midnight (e.g. 540 = 09:00 AM)
    private AuthManager auth;

    public BaggageService(AuthManager auth) {
        this.simulatedTime = 540; // 09:00 AM
        this.auth = auth;

        flights = new HashMap<>();
        bays = new HashMap<>();
        passengers = new HashMap<>();
        allBaggage = new HashMap<>();
        conveyorWaitingQueue = new ArrayDeque<>();
        conveyorPlanner = new ConveyorRoutePlanner();

        // Deterministic PriorityQueue comparator:
        // 1. Departure loading cutoff (earliest cutoff first)
        // 2. Transfer urgency over origin baggage
        // 3. Passenger category rank (First > Business > Economy)
        // 4. Arrival order
        // 5. Bag ID tie-breaker
        Comparator<Baggage> baggageComparator = new Comparator<Baggage>() {
            @Override
            public int compare(Baggage b1, Baggage b2) {
                // Rule 1: Earliest loading cutoff (for outgoing/transfer with valid cutoffs)
                int c1 = b1.getLoadingCutoff() > 0 ? b1.getLoadingCutoff() : Integer.MAX_VALUE;
                int c2 = b2.getLoadingCutoff() > 0 ? b2.getLoadingCutoff() : Integer.MAX_VALUE;
                if (c1 < c2) return -1;
                if (c1 > c2) return 1;

                // Rule 2: Transfer bags prioritized over origin bags
                boolean t1 = b1.isTransferBag();
                boolean t2 = b2.isTransferBag();
                if (t1 && !t2) return -1;
                if (!t1 && t2) return 1;

                // Rule 3: Passenger Category Rank (Rank 1: First, Rank 2: Business, Rank 3: Economy)
                int rank1 = b1.getPassengerCategory().getRank();
                int rank2 = b2.getPassengerCategory().getRank();
                if (rank1 < rank2) return -1;
                if (rank1 > rank2) return 1;

                // Rule 4: Arrival sequence order
                if (b1.getArrivalOrder() < b2.getArrivalOrder()) return -1;
                if (b1.getArrivalOrder() > b2.getArrivalOrder()) return 1;

                // Rule 5: Deterministic bagId tie-breaker
                return b1.getBagId().compareTo(b2.getBagId());
            }
        };

        processQueue = new PriorityQueue<>(baggageComparator);

        DatabaseManager.initializeDatabase();

        // Initialize standard conveyor topology
        conveyorPlanner.initializeNetwork();
        if (DatabaseManager.hasData("ConveyorSegment")) {
            DatabaseManager.loadConveyors(conveyorPlanner);
        }

        // Initialize standard bays
        bays.put("BAY-A", new LoadingBay("BAY-A"));
        bays.put("BAY-B", new LoadingBay("BAY-B"));
        bays.put("BAY-C", new LoadingBay("BAY-C"));
        bays.put("BAY-D", new LoadingBay("BAY-D"));

        // Initialize clean demonstration dataset ONLY if database tables are empty
        if (!DatabaseManager.hasData("Flight") || !DatabaseManager.hasData("Passenger") || !DatabaseManager.hasData("Baggage")) {
            initializeCleanSampleData();
        } else {
            DatabaseManager.loadFlights(this);
            loadPassengersFromDB();
            DatabaseManager.loadBaggage(this);
            DatabaseManager.validateAndRepairBaggageRecords(this);
        }
    }

    public Flight getFlight(String flightId) { return flights.get(flightId); }
    public LoadingBay getBay(String bayId) { return bays.get(bayId); }
    public Passenger getPassenger(String passengerId) { return passengers.get(passengerId); }
    public Baggage getBaggage(String bagId) { return allBaggage.get(bagId); }
    public Map<String, Flight> getAllFlights() { return flights; }
    public Map<String, LoadingBay> getAllBays() { return bays; }
    public Map<String, Passenger> getAllPassengers() { return passengers; }
    public Map<String, Baggage> getAllBaggage() { return allBaggage; }
    public ConveyorRoutePlanner getConveyorPlanner() { return conveyorPlanner; }
    public AuthManager getAuth() { return auth; }
    public ArrayDeque<Baggage> getConveyorWaitingQueue() { return conveyorWaitingQueue; }

    public void enqueueConveyorWaiting(Baggage bag) {
        if (bag != null && !conveyorWaitingQueue.contains(bag)) {
            conveyorWaitingQueue.addLast(bag);
        }
    }

    public Baggage pollConveyorWaiting() {
        return conveyorWaitingQueue.pollFirst();
    }

    public void addLoadedFlight(Flight flight) {
        flights.put(flight.getFlightId(), flight);
    }

    public void addLoadedPassenger(Passenger p) {
        passengers.put(p.getPassengerId(), p);
    }

    public void addLoadedBaggage(Baggage bag) {
        allBaggage.put(bag.getBagId(), bag);
        if (bag.getStatus() == BaggageStatus.QUEUED) {
            processQueue.add(bag);
            enqueueConveyorWaiting(bag);
        }
    }

    private void loadPassengersFromDB() {
        List<Passenger> list = DatabaseManager.getAllPassengers();
        for (Passenger p : list) {
            passengers.put(p.getPassengerId(), p);
        }
    }

    // ==================== CLEAN DEMONSTRATION DATASET ====================

    public void initializeCleanSampleData() {
        flights.clear();
        bays.get("BAY-A").setAssignedFlight(null);
        bays.get("BAY-B").setAssignedFlight(null);
        bays.get("BAY-C").setAssignedFlight(null);
        bays.get("BAY-D").setAssignedFlight(null);
        passengers.clear();
        allBaggage.clear();
        processQueue.clear();

        // 1. Four consistently defined flights covering Tamil Nadu, Indian hubs, and international routes
        Flight f101 = new Flight("AI101", "MAA", "Delhi (DEL) via Madurai (IXM)", 660, 630);    // Dep 11:00 AM, Cutoff 10:30 AM
        Flight f202 = new Flight("6E202", "MAA", "Mumbai (BOM) via Coimbatore (CJB)", 645, 615); // Dep 10:45 AM, Cutoff 10:15 AM
        Flight f303 = new Flight("SG303", "MAA", "Bengaluru (BLR) / Tiruchirappalli (TRZ)", 675, 645);// Dep 11:15 AM, Cutoff 10:45 AM
        Flight f404 = new Flight("UK404", "MAA", "Dubai (DXB) / Singapore (SIN)", 650, 620);    // Dep 10:50 AM, Cutoff 10:20 AM

        flights.put(f101.getFlightId(), f101);
        flights.put(f202.getFlightId(), f202);
        flights.put(f303.getFlightId(), f303);
        flights.put(f404.getFlightId(), f404);

        bays.get("BAY-A").setAssignedFlight(f101);
        bays.get("BAY-B").setAssignedFlight(f202);
        bays.get("BAY-C").setAssignedFlight(f303);
        bays.get("BAY-D").setAssignedFlight(f404);

        f101.setAssignedBay(bays.get("BAY-A"));
        f202.setAssignedBay(bays.get("BAY-B"));
        f303.setAssignedBay(bays.get("BAY-C"));
        f404.setAssignedBay(bays.get("BAY-D"));

        DatabaseManager.saveFlight(f101);
        DatabaseManager.saveFlight(f202);
        DatabaseManager.saveFlight(f303);
        DatabaseManager.saveFlight(f404);
        DatabaseManager.saveLoadingBay(bays.get("BAY-A"));
        DatabaseManager.saveLoadingBay(bays.get("BAY-B"));
        DatabaseManager.saveLoadingBay(bays.get("BAY-C"));
        DatabaseManager.saveLoadingBay(bays.get("BAY-D"));

        // 2. Realistic passengers
        Passenger[] samplePassengers = {
            new Passenger("P101", "Ramesh Kumar", "ramesh", "AI101", PassengerCategory.BUSINESS, "ramesh.k@example.com"),
            new Passenger("P102", "Priya Sharma", "priya", "6E202", PassengerCategory.FIRST_CLASS, "priya.s@example.com"),
            new Passenger("P103", "Rajesh Patel", "rajesh", "SG303", PassengerCategory.ECONOMY, "rajesh.p@example.com"),
            new Passenger("P104", "Ananya Iyer", "ananya", "UK404", PassengerCategory.BUSINESS, "ananya.i@example.com"),
            new Passenger("P105", "Vikram Singh", "vikram", "AI101", PassengerCategory.ECONOMY, "vikram.s@example.com"),
            new Passenger("P106", "Meera Nair", "meera", "6E202", PassengerCategory.ECONOMY, "meera.n@example.com"),
            new Passenger("P107", "Arun Verma", "arun", "SG303", PassengerCategory.BUSINESS, "arun.v@example.com"),
            new Passenger("P108", "Sneha Reddy", "sneha", "UK404", PassengerCategory.FIRST_CLASS, "sneha.r@example.com"),
            new Passenger("P109", "Karthik Raman", "karthik", "AI101", PassengerCategory.ECONOMY, "karthik.r@example.com"),
            new Passenger("P110", "Divya Krishnan", "divya", "6E202", PassengerCategory.ECONOMY, "divya.k@example.com"),
            new Passenger("P111", "David Miller", "david", "AI101", PassengerCategory.BUSINESS, "david.m@example.com"),
            new Passenger("P112", "Fatima Al-Sayed", "fatima", "UK404", PassengerCategory.FIRST_CLASS, "fatima.a@example.com")
        };

        for (Passenger p : samplePassengers) {
            passengers.put(p.getPassengerId(), p);
            DatabaseManager.savePassenger(p);
        }

        // 3. User account creation uses the administrator setup and registration process.
        // Hardcoded demo accounts with known passwords are not created automatically.

        // 4. Realistic synthetic baseline baggage records (32 unique records across workflows)
        // Multi-bag example 1: Ramesh Kumar (P101) has 4 bags
        createSampleBag("BAG-101", "P101", "Ramesh Kumar", BaggageDirection.OUTGOING, f101, PassengerCategory.BUSINESS, 1,
                "CHECK-IN", "BAY-A", false, "", BaggageStatus.AWAITING_CLEARANCE);
        createSampleBag("BAG-102", "P101", "Ramesh Kumar", BaggageDirection.OUTGOING, f101, PassengerCategory.BUSINESS, 2,
                "CHECK-IN", "BAY-A", true, "Screening cleared (Security Hub)", BaggageStatus.QUEUED);
        createSampleBag("BAG-122", "P101", "Ramesh Kumar", BaggageDirection.OUTGOING, f101, PassengerCategory.BUSINESS, 3,
                "BAY-A", "BAY-A", true, "Screening cleared", BaggageStatus.LOADED);
        createSampleBag("BAG-131", "P101", "Ramesh Kumar", BaggageDirection.OUTGOING, f101, PassengerCategory.BUSINESS, 4,
                "SORTING", "BAY-A", true, "Screening cleared", BaggageStatus.IN_TRANSIT);

        // Multi-bag example 2: Priya Sharma (P102) has 3 bags
        createSampleBag("BAG-103", "P102", "Priya Sharma", BaggageDirection.OUTGOING, f202, PassengerCategory.FIRST_CLASS, 5,
                "CENTRAL", "BAY-B", true, "Priority screening cleared", BaggageStatus.IN_TRANSIT);
        createSampleBag("BAG-104", "P102", "Priya Sharma", BaggageDirection.OUTGOING, f202, PassengerCategory.FIRST_CLASS, 6,
                "BAY-B", "BAY-B", true, "Priority screening cleared", BaggageStatus.LOADED);
        createSampleBag("BAG-123", "P102", "Priya Sharma", BaggageDirection.OUTGOING, f202, PassengerCategory.FIRST_CLASS, 7,
                "CHECK-IN", "BAY-B", true, "Priority screening cleared", BaggageStatus.QUEUED);

        // Multi-bag example 3: Rajesh Patel (P103) has 2 bags (including a missed-cutoff demonstration)
        createSampleBag("BAG-105", "P103", "Rajesh Patel", BaggageDirection.OUTGOING, f303, PassengerCategory.ECONOMY, 8,
                "CHECK-IN", "BAY-C", false, "", BaggageStatus.AWAITING_CLEARANCE);
        Baggage missedBag = createSampleBag("BAG-120", "P103", "Rajesh Patel", BaggageDirection.OUTGOING, f202, PassengerCategory.ECONOMY, 9,
                "CHECK-IN", "BAY-B", true, "Screening cleared", BaggageStatus.MISSED_CUTOFF);
        missedBag.setLoadingCutoff(530); // 08:50 AM cutoff, arrived after cutoff at 09:00 AM

        // Multi-bag example 4: Ananya Iyer (P104) has 3 bags
        createSampleBag("BAG-106", "P104", "Ananya Iyer", BaggageDirection.OUTGOING, f404, PassengerCategory.BUSINESS, 10,
                "CHECK-IN", "BAY-D", true, "Screening verified", BaggageStatus.QUEUED);
        createSampleBag("BAG-124", "P104", "Ananya Iyer", BaggageDirection.OUTGOING, f404, PassengerCategory.BUSINESS, 11,
                "BAY-D", "BAY-D", true, "Screening verified", BaggageStatus.LOADED);
        createSampleBag("BAG-132", "P104", "Ananya Iyer", BaggageDirection.OUTGOING, f404, PassengerCategory.BUSINESS, 12,
                "CHECK-IN", "BAY-D", false, "", BaggageStatus.AWAITING_CLEARANCE);

        // Multi-bag example 5: Vikram Singh (P105) has 3 bags (Outbound & Inbound combination)
        createSampleBag("BAG-107", "P105", "Vikram Singh", BaggageDirection.OUTGOING, f101, PassengerCategory.ECONOMY, 13,
                "CHECK-IN", "BAY-A", true, "Screening verified", BaggageStatus.QUEUED);
        createSampleBag("BAG-116", "P105", "Vikram Singh", BaggageDirection.INCOMING, null, PassengerCategory.ECONOMY, 14,
                "CLAIM-CAROUSEL-1", "CLAIM-CAROUSEL-1", true, "Inbound flight baggage", BaggageStatus.AT_COLLECTION_POINT);
        createSampleBag("BAG-125", "P105", "Vikram Singh", BaggageDirection.OUTGOING, f101, PassengerCategory.ECONOMY, 15,
                "BAY-AREA", "BAY-A", true, "Screening verified", BaggageStatus.IN_TRANSIT);

        // Multi-bag example 6: Meera Nair (P106) has 2 bags
        createSampleBag("BAG-108", "P106", "Meera Nair", BaggageDirection.OUTGOING, f202, PassengerCategory.ECONOMY, 16,
                "CHECK-IN", "BAY-B", false, "", BaggageStatus.AWAITING_CLEARANCE);
        createSampleBag("BAG-117", "P106", "Meera Nair", BaggageDirection.INCOMING, null, PassengerCategory.ECONOMY, 17,
                "CLAIM-CAROUSEL-1", "CLAIM-CAROUSEL-1", true, "Inbound flight baggage", BaggageStatus.COLLECTED);

        // Multi-bag example 7: Arun Verma (P107) has 2 bags (Outbound & Transfer)
        createSampleBag("BAG-109", "P107", "Arun Verma", BaggageDirection.OUTGOING, f303, PassengerCategory.BUSINESS, 18,
                "BAY-AREA", "BAY-C", true, "Screening cleared", BaggageStatus.IN_TRANSIT);
        createSampleBag("BAG-118", "P107", "Arun Verma", BaggageDirection.TRANSFER, f101, PassengerCategory.BUSINESS, 19,
                "TRANSFER-A", "BAY-A", true, "Transit security approved", BaggageStatus.QUEUED);

        // Multi-bag example 8: Sneha Reddy (P108) has 3 bags
        createSampleBag("BAG-110", "P108", "Sneha Reddy", BaggageDirection.OUTGOING, f404, PassengerCategory.FIRST_CLASS, 20,
                "CHECK-IN", "BAY-D", true, "VIP screening cleared", BaggageStatus.QUEUED);
        createSampleBag("BAG-111", "P108", "Sneha Reddy", BaggageDirection.OUTGOING, f404, PassengerCategory.FIRST_CLASS, 21,
                "BAY-D", "BAY-D", true, "VIP screening cleared", BaggageStatus.LOADED);
        createSampleBag("BAG-126", "P108", "Sneha Reddy", BaggageDirection.OUTGOING, f404, PassengerCategory.FIRST_CLASS, 22,
                "CENTRAL", "BAY-D", true, "VIP screening cleared", BaggageStatus.IN_TRANSIT);

        // Multi-bag example 9: Karthik Raman (P109) has 3 bags (including security rejection example)
        createSampleBag("BAG-112", "P109", "Karthik Raman", BaggageDirection.OUTGOING, f101, PassengerCategory.ECONOMY, 23,
                "CHECK-IN", "BAY-A", false, "", BaggageStatus.AWAITING_CLEARANCE);
        createSampleBag("BAG-121", "P109", "Karthik Raman", BaggageDirection.OUTGOING, f303, PassengerCategory.ECONOMY, 24,
                "CHECK-IN", "BAY-C", false, "Prohibited lithium battery pack detected in X-ray screening", BaggageStatus.REJECTED);
        createSampleBag("BAG-127", "P109", "Karthik Raman", BaggageDirection.OUTGOING, f101, PassengerCategory.ECONOMY, 25,
                "CHECK-IN", "BAY-A", true, "Screening cleared", BaggageStatus.QUEUED);

        // Multi-bag example 10: Divya Krishnan (P110) has 2 bags
        createSampleBag("BAG-113", "P110", "Divya Krishnan", BaggageDirection.OUTGOING, f202, PassengerCategory.ECONOMY, 26,
                "BAY-B", "BAY-B", true, "Screening cleared", BaggageStatus.LOADED);
        createSampleBag("BAG-128", "P110", "Divya Krishnan", BaggageDirection.OUTGOING, f202, PassengerCategory.ECONOMY, 27,
                "CHECK-IN", "BAY-B", true, "Screening cleared", BaggageStatus.QUEUED);

        // Multi-bag example 11: David Miller (P111) has 3 incoming bags
        createSampleBag("BAG-114", "P111", "David Miller", BaggageDirection.INCOMING, null, PassengerCategory.BUSINESS, 28,
                "UNLOAD-POINT", "CLAIM-CAROUSEL-1", true, "Inbound flight baggage", BaggageStatus.QUEUED);
        createSampleBag("BAG-119", "P111", "David Miller", BaggageDirection.INCOMING, null, PassengerCategory.BUSINESS, 29,
                "CENTRAL", "CLAIM-CAROUSEL-2", true, "Inbound flight baggage", BaggageStatus.IN_TRANSIT);
        createSampleBag("BAG-129", "P111", "David Miller", BaggageDirection.INCOMING, null, PassengerCategory.BUSINESS, 30,
                "CLAIM-CAROUSEL-1", "CLAIM-CAROUSEL-1", true, "Inbound flight baggage", BaggageStatus.AT_COLLECTION_POINT);

        // Multi-bag example 12: Fatima Al-Sayed (P112) has 2 transfer bags
        createSampleBag("BAG-115", "P112", "Fatima Al-Sayed", BaggageDirection.TRANSFER, f404, PassengerCategory.FIRST_CLASS, 31,
                "TRANSFER-A", "BAY-D", true, "Transit security approved", BaggageStatus.QUEUED);
        createSampleBag("BAG-130", "P112", "Fatima Al-Sayed", BaggageDirection.TRANSFER, f404, PassengerCategory.FIRST_CLASS, 32,
                "BAY-D", "BAY-D", true, "Transit security approved", BaggageStatus.LOADED);

        DatabaseManager.logAudit("SYSTEM", "INITIALIZE_SAMPLE_DATA", "System", simulatedTime, "SUCCESS", "32-record demonstration dataset created");
    }

    private Baggage createSampleBag(String bagId, String pId, String pName, BaggageDirection dir, Flight flight,
                                   PassengerCategory cat, int arrOrder, String currentLoc, String destNode,
                                   boolean cleared, String clearanceReason, BaggageStatus status) {
        Baggage bag;
        if (dir == BaggageDirection.INCOMING) {
            bag = new IncomingBag(bagId, pId, pName, cat, arrOrder);
        } else if (dir == BaggageDirection.TRANSFER) {
            bag = new TransferBag(bagId, pId, pName, cat, arrOrder);
        } else {
            bag = new OriginBag(bagId, pId, pName, cat, arrOrder);
        }

        if (flight != null) {
            bag.assignFlight(flight);
        } else if (dir == BaggageDirection.INCOMING) {
            bag.setDestinationNode("CLAIM-CAROUSEL-1");
            bag.setOriginAirport("DXB");
            bag.setDestinationAirport("MAA");
        }

        bag.setCurrentLocation(currentLoc);
        bag.setDestinationNode(destNode);
        bag.setClearance(cleared, clearanceReason);
        bag.setStatus(status);

        // Calculate initial route & ETA
        if (destNode != null && !destNode.isEmpty()) {
            RoutingResult res = conveyorPlanner.getShortestPath(currentLoc, destNode);
            if (res != null) {
                bag.setCurrentRoute(res.route);
                bag.setEstimatedTime(res.totalTime);
            }
        }

        allBaggage.put(bagId, bag);
        if (status == BaggageStatus.QUEUED) {
            processQueue.add(bag);
            enqueueConveyorWaiting(bag);
        }
        DatabaseManager.saveBaggage(bag);
        return bag;
    }

    public int getSimulatedTime() { return simulatedTime; }

    public String getFormattedSimulatedTime() {
        int hours = simulatedTime / 60;
        int minutes = simulatedTime % 60;
        return String.format("%02d:%02d", hours, minutes);
    }

    public void advanceSimulatedClock(int minutes, User user) {
        AuthManager.requireUserPermission(user, Role.ADMIN, Role.BAGGAGE_MANAGER);
        if (minutes > 0) {
            simulatedTime += minutes;
            DatabaseManager.logAudit(user.getUsername(), "ADVANCE_TIME", "System", simulatedTime, "SUCCESS",
                    "Advanced by " + minutes + " mins to " + getFormattedSimulatedTime());
        }
    }

    // ==================== BAGGAGE REGISTRATION & ASSIGNMENT ====================

    public Baggage registerBaggage(String bagId, String passengerId, String passengerName, BaggageDirection direction,
                                  int catRank, String flightId, String originAirport, String destAirport,
                                  String destNode, User user) {
        AuthManager.requireUserPermission(user, Role.ADMIN, Role.BAGGAGE_MANAGER);
        if (allBaggage.containsKey(bagId)) {
            throw new IllegalArgumentException("Baggage ID " + bagId + " already exists.");
        }

        // Ensure passenger exists or link
        Passenger passenger = passengers.get(passengerId);
        if (passenger == null && passengerId != null && !passengerId.isEmpty()) {
            passenger = new Passenger(passengerId, passengerName != null ? passengerName : "Passenger " + passengerId,
                    null, flightId, PassengerCategory.fromRank(catRank), "");
            passengers.put(passengerId, passenger);
            DatabaseManager.savePassenger(passenger);
        } else if (passenger != null && (passengerName == null || passengerName.isEmpty())) {
            passengerName = passenger.getFullName();
        }

        PassengerCategory cat = PassengerCategory.fromRank(catRank);
        Baggage newBag;
        int order = allBaggage.size() + 1;

        if (direction == BaggageDirection.INCOMING) {
            newBag = new IncomingBag(bagId, passengerId, passengerName, cat, order);
            newBag.setOriginAirport(originAirport != null ? originAirport : "BLR");
            newBag.setDestinationAirport("MAA");
            newBag.setDestinationNode(destNode != null && !destNode.isEmpty() ? destNode : "CLAIM-CAROUSEL-1");
            newBag.setClearance(true, "Inbound flight baggage");
            processQueue.add(newBag);
        } else if (direction == BaggageDirection.TRANSFER) {
            newBag = new TransferBag(bagId, passengerId, passengerName, cat, order);
            newBag.setOriginAirport(originAirport != null ? originAirport : "DEL");
            if (flightId != null && !flightId.isEmpty()) {
                Flight f = flights.get(flightId);
                if (f != null) newBag.assignFlight(f);
            }
        } else {
            newBag = new OriginBag(bagId, passengerId, passengerName, cat, order);
            newBag.setOriginAirport("MAA");
            if (flightId != null && !flightId.isEmpty()) {
                Flight f = flights.get(flightId);
                if (f != null) newBag.assignFlight(f);
            }
        }

        if (destNode != null && !destNode.isEmpty()) {
            newBag.setDestinationNode(destNode);
        }

        allBaggage.put(bagId, newBag);
        DatabaseManager.saveBaggage(newBag);
        DatabaseManager.logAudit(user.getUsername(), "REGISTER_BAGGAGE", bagId, simulatedTime, "SUCCESS",
                "Passenger: " + passengerId + ", Type: " + newBag.getBagType());
        return newBag;
    }

    public void assignBaggageToFlight(String bagId, String flightId, User user) {
        AuthManager.requireUserPermission(user, Role.ADMIN, Role.BAGGAGE_MANAGER);
        Baggage bag = allBaggage.get(bagId);
        Flight flight = flights.get(flightId);

        if (bag == null) throw new IllegalArgumentException("Unknown Baggage ID: " + bagId);
        if (flight == null) throw new IllegalArgumentException("Unknown Flight ID: " + flightId);

        if (bag.getStatus() == BaggageStatus.LOADED) {
            throw new IllegalStateException("Cannot reassign: Baggage already loaded.");
        }

        bag.assignFlight(flight);
        DatabaseManager.saveBaggage(bag);
        DatabaseManager.logAudit(user.getUsername(), "ASSIGN_BAGGAGE", bagId, simulatedTime, "SUCCESS", "Assigned to flight " + flightId);
    }

    // ==================== SECURITY SCREENING & CLEARANCE ====================

    public void grantClearance(String bagId, String reason, User user) {
        AuthManager.requireClearancePermission(user);
        Baggage bag = allBaggage.get(bagId);
        if (bag == null) throw new IllegalArgumentException("Unknown Baggage ID: " + bagId);

        if (bag.isIncomingBag()) {
            throw new IllegalStateException("Incoming baggage does not require outbound security clearance.");
        }

        if (bag.getStatus() == BaggageStatus.AWAITING_CLEARANCE || bag.getStatus() == BaggageStatus.REJECTED || bag.getStatus() == BaggageStatus.REGISTERED) {
            bag.setClearance(true, reason != null && !reason.isEmpty() ? reason : "Security screening passed");
            processQueue.remove(bag);
            processQueue.add(bag);
            enqueueConveyorWaiting(bag);
            DatabaseManager.saveBaggage(bag);
            DatabaseManager.logAudit(user.getUsername(), "GRANT_CLEARANCE", bagId, simulatedTime, "SUCCESS", reason);
        } else {
            throw new IllegalStateException("Baggage cannot be cleared in state: " + bag.getStatus());
        }
    }

    public void rejectClearance(String bagId, String reason, User user) {
        AuthManager.requireClearancePermission(user);
        Baggage bag = allBaggage.get(bagId);
        if (bag == null) throw new IllegalArgumentException("Unknown Baggage ID: " + bagId);

        bag.setClearance(false, reason != null && !reason.isEmpty() ? reason : "Security check rejected");
        processQueue.remove(bag);
        conveyorWaitingQueue.remove(bag);
        DatabaseManager.saveBaggage(bag);
        DatabaseManager.logAudit(user.getUsername(), "REJECT_CLEARANCE", bagId, simulatedTime, "SUCCESS", reason);
    }

    // ==================== BAGGAGE PROCESSING & ROUTING ====================

    public RoutingResult calculateRoute(String startNode, String endNode) {
        return conveyorPlanner.getShortestPath(startNode, endNode);
    }

    public Baggage processNextBaggage(User user) {
        AuthManager.requireUserPermission(user, Role.ADMIN, Role.BAGGAGE_MANAGER);
        if (processQueue.isEmpty()) {
            return null;
        }

        Baggage bag = processQueue.poll();
        conveyorWaitingQueue.remove(bag);
        processBaggageInternal(bag, user);
        return bag;
    }

    public Baggage processSpecificBaggage(String bagId, User user) {
        AuthManager.requireUserPermission(user, Role.ADMIN, Role.BAGGAGE_MANAGER);
        Baggage bag = allBaggage.get(bagId);
        if (bag == null) throw new IllegalArgumentException("Unknown Baggage ID: " + bagId);

        processQueue.remove(bag);
        conveyorWaitingQueue.remove(bag);
        processBaggageInternal(bag, user);
        return bag;
    }

    // Step-by-step conveyor node processing
    public Baggage stepBaggageNextNode(String bagId, User user) {
        AuthManager.requireUserPermission(user, Role.ADMIN, Role.BAGGAGE_MANAGER);
        Baggage bag = allBaggage.get(bagId);
        if (bag == null) throw new IllegalArgumentException("Unknown Baggage ID: " + bagId);

        if (!bag.isIncomingBag() && !bag.isOperatorCleared()) {
            throw new IllegalStateException("Security clearance required before advancing baggage.");
        }

        String startLoc = bag.getCurrentLocation();
        String endLoc = resolveDestinationNode(bag);

        RoutingResult result = conveyorPlanner.getShortestPath(startLoc, endLoc);
        if (result == null || result.route.size() < 2) {
            bag.setStatus(BaggageStatus.EXCEPTION);
            DatabaseManager.saveBaggage(bag);
            throw new IllegalStateException("No available path from " + startLoc + " to " + endLoc);
        }

        String nextNode = result.route.get(1);
        int segmentTime = result.totalTime;

        // Validate physical conveyor segment availability
        boolean segmentAvailable = false;
        if (conveyorPlanner.getAdjacencyList().containsKey(startLoc)) {
            for (ConveyorSegment seg : conveyorPlanner.getAdjacencyList().get(startLoc)) {
                if (seg.getToNode().equals(nextNode) && seg.isAvailable()) {
                    segmentAvailable = true;
                    break;
                }
            }
        }
        if (!segmentAvailable) {
            bag.setStatus(BaggageStatus.EXCEPTION);
            DatabaseManager.saveBaggage(bag);
            throw new IllegalStateException("Conveyor segment from " + startLoc + " to " + nextNode + " is currently offline or failed.");
        }

        bag.setCurrentLocation(nextNode);
        bag.setStatus(BaggageStatus.IN_TRANSIT);
        DatabaseManager.saveRoutingRecord(bag.getBagId(), startLoc, nextNode, segmentTime, "IN_TRANSIT", simulatedTime);

        // Check if reached destination
        if (nextNode.equals(endLoc)) {
            if (bag.isIncomingBag()) {
                bag.setStatus(BaggageStatus.AT_COLLECTION_POINT);
            } else {
                bag.setStatus(BaggageStatus.AT_LOADING_BAY);
                int estArrival = simulatedTime + segmentTime;
                if (estArrival > bag.getLoadingCutoff() && bag.getLoadingCutoff() > 0) {
                    bag.setStatus(BaggageStatus.MISSED_CUTOFF);
                } else {
                    bag.setStatus(BaggageStatus.LOADED);
                }
            }
            bag.setCurrentRoute(Collections.singletonList(endLoc));
            bag.setEstimatedTime(0);
        } else {
            // Recalculate remaining route and ETA from the confirmed new node
            RoutingResult remainingRes = conveyorPlanner.getShortestPath(nextNode, endLoc);
            if (remainingRes != null) {
                bag.setCurrentRoute(remainingRes.route);
                bag.setEstimatedTime(remainingRes.totalTime);
                if (!bag.isIncomingBag() && bag.getLoadingCutoff() > 0 && (simulatedTime + remainingRes.totalTime > bag.getLoadingCutoff())) {
                    bag.setStatus(BaggageStatus.MISSED_CUTOFF);
                }
            } else {
                bag.setStatus(BaggageStatus.EXCEPTION);
                bag.setEstimatedTime(-1);
            }
        }

        DatabaseManager.saveBaggage(bag);
        DatabaseManager.logAudit(user.getUsername(), "STEP_BAGGAGE", bagId, simulatedTime, "SUCCESS",
                startLoc + " -> " + nextNode + " (" + bag.getStatus() + ")");
        return bag;
    }

    public Baggage collectBaggage(String bagId, User user) {
        Baggage bag = allBaggage.get(bagId);
        if (bag == null) throw new IllegalArgumentException("Unknown Baggage ID: " + bagId);

        if (!bag.isIncomingBag()) {
            throw new IllegalStateException("Only incoming baggage can be claimed at collection point.");
        }

        String loc = bag.getCurrentLocation();
        if (loc == null || (!loc.startsWith("CLAIM-CAROUSEL") && !loc.equals("CLAIM-AREA"))) {
            throw new IllegalStateException("Baggage has not yet reached a collection carousel. Current location: " + loc);
        }

        AuthManager.requirePassengerAccess(user, bag.getPassengerId());

        bag.setStatus(BaggageStatus.COLLECTED);
        DatabaseManager.saveBaggage(bag);
        DatabaseManager.saveRoutingRecord(bagId, loc, loc, 0, "COLLECTED", simulatedTime);
        DatabaseManager.logAudit(user.getUsername(), "COLLECT_BAGGAGE", bagId, simulatedTime, "SUCCESS", "Claimed at " + loc);
        return bag;
    }

    private String resolveDestinationNode(Baggage bag) {
        String endLoc = bag.getDestinationNode();
        if (endLoc == null || endLoc.isEmpty()) {
            if (bag.getAssignedFlight() != null && bag.getAssignedFlight().getAssignedBay() != null) {
                endLoc = bag.getAssignedFlight().getAssignedBay().getBayId();
                bag.setDestinationNode(endLoc);
            } else if (bag.isIncomingBag()) {
                endLoc = "CLAIM-CAROUSEL-1";
                bag.setDestinationNode(endLoc);
            }
        }
        return endLoc;
    }

    private void processBaggageInternal(Baggage bag, User user) {
        // Enforce clearance for outgoing & transfer
        if (!bag.isIncomingBag() && !bag.isOperatorCleared()) {
            bag.setStatus(BaggageStatus.AWAITING_CLEARANCE);
            DatabaseManager.saveBaggage(bag);
            DatabaseManager.logAudit(user.getUsername(), "PROCESS_BAG", bag.getBagId(), simulatedTime, "FAILED", "CLEARANCE_REQUIRED");
            throw new IllegalStateException("Security clearance required before processing bag " + bag.getBagId());
        }

        String startLoc = bag.getCurrentLocation();
        String endLoc = resolveDestinationNode(bag);

        if (endLoc == null || endLoc.isEmpty()) {
            bag.setStatus(BaggageStatus.AWAITING_CLEARANCE);
            DatabaseManager.saveBaggage(bag);
            throw new IllegalStateException("No assigned destination or loading bay for bag " + bag.getBagId());
        }

        RoutingResult result = conveyorPlanner.getShortestPath(startLoc, endLoc);

        if (result == null) {
            bag.setStatus(BaggageStatus.EXCEPTION);
            bag.setEstimatedTime(-1);
            bag.setCurrentRoute(new ArrayList<>());
            DatabaseManager.saveBaggage(bag);
            DatabaseManager.saveRoutingRecord(bag.getBagId(), startLoc, endLoc, -1, "EXCEPTION_NO_ROUTE", simulatedTime);
            DatabaseManager.logAudit(user.getUsername(), "PROCESS_BAG", bag.getBagId(), simulatedTime, "FAILED", "NO_ROUTE_AVAILABLE");
            return;
        }

        bag.setCurrentRoute(result.route);
        bag.setEstimatedTime(result.totalTime);

        // Loading Cutoff Validation for Outgoing & Transfer baggage
        if (!bag.isIncomingBag() && bag.getLoadingCutoff() > 0) {
            int estimatedArrival = simulatedTime + result.totalTime;
            if (estimatedArrival > bag.getLoadingCutoff()) {
                bag.setStatus(BaggageStatus.MISSED_CUTOFF);
                DatabaseManager.saveBaggage(bag);
                DatabaseManager.saveRoutingRecord(bag.getBagId(), startLoc, endLoc, result.totalTime, "MISSED_CUTOFF", simulatedTime);
                DatabaseManager.logAudit(user.getUsername(), "PROCESS_BAG", bag.getBagId(), simulatedTime, "FAILED",
                        "MISSED_CUTOFF (Arrival: " + estimatedArrival + " > Cutoff: " + bag.getLoadingCutoff() + ")");
                return;
            }
        }

        // Record node-by-node movement transitions
        for (int i = 0; i < result.route.size() - 1; i++) {
            String from = result.route.get(i);
            String to = result.route.get(i + 1);
            DatabaseManager.saveRoutingRecord(bag.getBagId(), from, to, result.totalTime, "IN_TRANSIT", simulatedTime);
        }

        bag.setCurrentLocation(endLoc);

        if (bag.isIncomingBag()) {
            bag.setStatus(BaggageStatus.COLLECTED);
            DatabaseManager.saveRoutingRecord(bag.getBagId(), startLoc, endLoc, result.totalTime, "COLLECTED", simulatedTime);
        } else {
            bag.setStatus(BaggageStatus.LOADED);
            DatabaseManager.saveRoutingRecord(bag.getBagId(), startLoc, endLoc, result.totalTime, "LOADED", simulatedTime);
        }

        DatabaseManager.saveBaggage(bag);
        DatabaseManager.logAudit(user.getUsername(), "PROCESS_BAG", bag.getBagId(), simulatedTime, "SUCCESS",
                "Destination reached: " + endLoc + " (" + bag.getStatus() + ")");
    }

    // ==================== CONVEYOR NETWORK MANAGEMENT ====================

    public boolean markConveyorUnavailable(String from, String to, User user) {
        AuthManager.requireUserPermission(user, Role.ADMIN, Role.BAGGAGE_MANAGER);
        boolean success = conveyorPlanner.markConveyorUnavailable(from, to);
        if (success) {
            DatabaseManager.logAudit(user.getUsername(), "DISABLE_CONVEYOR", from + "->" + to, simulatedTime, "SUCCESS", "Segment marked offline");
            recalculateActiveRoutes();
        }
        return success;
    }

    public boolean restoreConveyor(String from, String to, User user) {
        AuthManager.requireUserPermission(user, Role.ADMIN, Role.BAGGAGE_MANAGER);
        boolean success = conveyorPlanner.restoreConveyor(from, to);
        if (success) {
            DatabaseManager.logAudit(user.getUsername(), "ENABLE_CONVEYOR", from + "->" + to, simulatedTime, "SUCCESS", "Segment restored online");
            recalculateActiveRoutes();
        }
        return success;
    }

    public void recalculateActiveRoutes() {
        for (Baggage bag : allBaggage.values()) {
            if (bag.getStatus() == BaggageStatus.QUEUED || bag.getStatus() == BaggageStatus.AWAITING_CLEARANCE ||
                bag.getStatus() == BaggageStatus.IN_TRANSIT || bag.getStatus() == BaggageStatus.EXCEPTION) {
                String dest = resolveDestinationNode(bag);
                if (dest != null && !dest.isEmpty()) {
                    RoutingResult res = conveyorPlanner.getShortestPath(bag.getCurrentLocation(), dest);
                    if (res != null) {
                        bag.setCurrentRoute(res.route);
                        bag.setEstimatedTime(res.totalTime);
                        if (bag.getStatus() == BaggageStatus.EXCEPTION) {
                            bag.setStatus(bag.isOperatorCleared() ? BaggageStatus.QUEUED : BaggageStatus.AWAITING_CLEARANCE);
                        }
                    } else {
                        bag.setEstimatedTime(-1);
                        bag.setStatus(BaggageStatus.EXCEPTION);
                    }
                    DatabaseManager.saveBaggage(bag);
                }
            }
        }
    }

    // ==================== FLIGHT & BAY UPDATES ====================

    public void changeFlightBay(String flightId, String newBayId, User user) {
        AuthManager.requireUserPermission(user, Role.ADMIN, Role.BAGGAGE_MANAGER);
        Flight f = flights.get(flightId);
        LoadingBay newBay = bays.get(newBayId);

        if (f == null) throw new IllegalArgumentException("Invalid flight ID: " + flightId);
        if (newBay == null) throw new IllegalArgumentException("Invalid bay ID: " + newBayId);

        if (f.getAssignedBay() != null) {
            f.getAssignedBay().setAssignedFlight(null);
        }

        newBay.setAssignedFlight(f);
        f.setAssignedBay(newBay);
        DatabaseManager.saveFlight(f);
        DatabaseManager.saveLoadingBay(newBay);

        // Update destination and reroute pending bags assigned to this flight
        for (Baggage bag : allBaggage.values()) {
            if (bag.getAssignedFlight() != null && bag.getAssignedFlight().getFlightId().equals(flightId)) {
                if (bag.getStatus() != BaggageStatus.LOADED && bag.getStatus() != BaggageStatus.MISSED_CUTOFF) {
                    bag.setDestinationNode(newBayId);
                    RoutingResult res = conveyorPlanner.getShortestPath(bag.getCurrentLocation(), newBayId);
                    if (res != null) {
                        bag.setCurrentRoute(res.route);
                        bag.setEstimatedTime(res.totalTime);
                    }
                    DatabaseManager.saveBaggage(bag);
                }
            }
        }

        DatabaseManager.logAudit(user.getUsername(), "CHANGE_FLIGHT_BAY", flightId, simulatedTime, "SUCCESS", "Assigned to " + newBayId);
    }

    public void updateFlightCutoff(String flightId, int newCutoff, User user) {
        AuthManager.requireUserPermission(user, Role.ADMIN, Role.BAGGAGE_MANAGER);
        Flight f = flights.get(flightId);
        if (f == null) throw new IllegalArgumentException("Invalid flight ID: " + flightId);

        f.setLoadingCutoff(newCutoff);
        DatabaseManager.saveFlight(f);

        for (Baggage bag : allBaggage.values()) {
            if (bag.getAssignedFlight() != null && bag.getAssignedFlight().getFlightId().equals(flightId)) {
                bag.setLoadingCutoff(newCutoff);
                DatabaseManager.saveBaggage(bag);
            }
        }

        DatabaseManager.logAudit(user.getUsername(), "UPDATE_FLIGHT_CUTOFF", flightId, simulatedTime, "SUCCESS", "New cutoff: " + f.getFormattedCutoff());
    }

    // ==================== PASSENGER SEARCH & LOOKUP ====================

    public List<Baggage> getBaggageForPassenger(String passengerId) {
        List<Baggage> list = new ArrayList<>();
        if (passengerId == null) return list;
        for (Baggage b : allBaggage.values()) {
            if (passengerId.equalsIgnoreCase(b.getPassengerId())) {
                list.add(b);
            }
        }
        return list;
    }

    public List<Baggage> searchBaggage(String query) {
        List<Baggage> list = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) {
            list.addAll(allBaggage.values());
            return list;
        }
        String q = query.trim().toLowerCase();
        for (Baggage b : allBaggage.values()) {
            boolean match = b.getBagId().toLowerCase().contains(q)
                    || b.getPassengerName().toLowerCase().contains(q)
                    || b.getPassengerId().toLowerCase().contains(q)
                    || (b.getAssignedFlight() != null && b.getAssignedFlight().getFlightId().toLowerCase().contains(q))
                    || b.getDestinationAirport().toLowerCase().contains(q);
            if (match) list.add(b);
        }
        return list;
    }

    // ==================== DASHBOARD STATS ====================

    public Map<String, Object> getDashboardStats() {
        int total = allBaggage.size();
        int incomingCount = 0;
        int outgoingCount = 0;
        int transferCount = 0;
        int awaitingClearance = 0;
        int queued = 0;
        int inTransit = 0;
        int loaded = 0;
        int collected = 0;
        int missedCutoff = 0;
        int exceptions = 0;

        for (Baggage b : allBaggage.values()) {
            if (b.isIncomingBag()) incomingCount++;
            else if (b.isTransferBag()) transferCount++;
            else outgoingCount++;

            switch (b.getStatus()) {
                case AWAITING_CLEARANCE: awaitingClearance++; break;
                case QUEUED: queued++; break;
                case IN_TRANSIT: inTransit++; break;
                case AT_LOADING_BAY:
                case LOADED: loaded++; break;
                case AT_COLLECTION_POINT:
                case COLLECTED: collected++; break;
                case MISSED_CUTOFF: missedCutoff++; break;
                case EXCEPTION:
                case REJECTED: exceptions++; break;
                default: break;
            }
        }

        int availableConveyors = 0;
        int totalConveyors = 0;
        for (List<ConveyorSegment> list : conveyorPlanner.getAdjacencyList().values()) {
            for (ConveyorSegment s : list) {
                totalConveyors++;
                if (s.isAvailable()) availableConveyors++;
            }
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("incomingCount", incomingCount);
        stats.put("outgoingCount", outgoingCount);
        stats.put("transferCount", transferCount);
        stats.put("awaitingClearance", awaitingClearance);
        stats.put("queued", queued);
        stats.put("inTransit", inTransit);
        stats.put("loaded", loaded);
        stats.put("collected", collected);
        stats.put("missedCutoff", missedCutoff);
        stats.put("exceptions", exceptions);
        stats.put("flightCount", flights.size());
        stats.put("passengerCount", passengers.size());
        stats.put("availableConveyors", availableConveyors);
        stats.put("totalConveyors", totalConveyors);
        stats.put("simulatedTime", simulatedTime);
        stats.put("formattedTime", getFormattedSimulatedTime());

        return stats;
    }
}

class ConveyorRoutePlanner {
    private HashMap<String, ArrayList<ConveyorSegment>> adjacencyList;

    public ConveyorRoutePlanner() {
        adjacencyList = new HashMap<>();
    }

    public HashMap<String, ArrayList<ConveyorSegment>> getAdjacencyList() {
        return adjacencyList;
    }

    public void addSegmentFromDB(String from, String to, int time, boolean available) {
        adjacencyList.putIfAbsent(from, new ArrayList<>());
        adjacencyList.putIfAbsent(to, new ArrayList<>());
        boolean found = false;
        for (ConveyorSegment s : adjacencyList.get(from)) {
            if (s.getToNode().equals(to)) {
                s.setAvailable(available);
                found = true;
                break;
            }
        }
        if (!found) {
            ConveyorSegment segment = new ConveyorSegment(from, to, time);
            segment.setAvailable(available);
            adjacencyList.get(from).add(segment);
        }
    }

    public void initializeNetwork() {
        String[] nodes = {
            "CHECK-IN", "SORTING", "TRANSFER-A", "TRANSFER-B",
            "CENTRAL", "BAY-AREA", "BAY-A", "BAY-B", "BAY-C", "BAY-D",
            "UNLOAD-POINT", "CLAIM-AREA", "CLAIM-CAROUSEL-1", "CLAIM-CAROUSEL-2"
        };
        for (String node : nodes) {
            adjacencyList.putIfAbsent(node, new ArrayList<>());
        }

        // Outbound & Core conveyor paths
        addSegment("CHECK-IN", "SORTING", 4);
        addSegment("SORTING", "TRANSFER-A", 5);
        addSegment("SORTING", "TRANSFER-B", 6);
        addSegment("SORTING", "CENTRAL", 7);
        addSegment("TRANSFER-A", "CENTRAL", 4);
        addSegment("TRANSFER-B", "CENTRAL", 3);
        addSegment("CENTRAL", "BAY-AREA", 5);
        addSegment("BAY-AREA", "BAY-A", 3);
        addSegment("BAY-AREA", "BAY-B", 4);
        addSegment("BAY-AREA", "BAY-C", 3);
        addSegment("BAY-AREA", "BAY-D", 5);

        // Inbound & Claim conveyor paths (Two-way operations)
        addSegment("UNLOAD-POINT", "CENTRAL", 5);
        addSegment("CENTRAL", "CLAIM-AREA", 4);
        addSegment("CLAIM-AREA", "CLAIM-CAROUSEL-1", 3);
        addSegment("CLAIM-AREA", "CLAIM-CAROUSEL-2", 4);
        addSegment("UNLOAD-POINT", "TRANSFER-A", 6);
    }

    private void addSegment(String from, String to, int time) {
        ConveyorSegment segment = new ConveyorSegment(from, to, time);
        adjacencyList.get(from).add(segment);
        DatabaseManager.saveConveyorSegment(segment);
    }

    public List<ConveyorSegment> getAllSegments() {
        List<ConveyorSegment> list = new ArrayList<>();
        for (ArrayList<ConveyorSegment> edges : adjacencyList.values()) {
            list.addAll(edges);
        }
        return list;
    }

    public boolean markConveyorUnavailable(String from, String to) {
        if (adjacencyList.containsKey(from)) {
            for (ConveyorSegment edge : adjacencyList.get(from)) {
                if (edge.getToNode().equals(to)) {
                    edge.setAvailable(false);
                    DatabaseManager.saveConveyorSegment(edge);
                    return true;
                }
            }
        }
        return false;
    }

    public boolean restoreConveyor(String from, String to) {
        if (adjacencyList.containsKey(from)) {
            for (ConveyorSegment edge : adjacencyList.get(from)) {
                if (edge.getToNode().equals(to)) {
                    edge.setAvailable(true);
                    DatabaseManager.saveConveyorSegment(edge);
                    return true;
                }
            }
        }
        return false;
    }

    public RoutingResult getShortestPath(String startNode, String endNode) {
        if (startNode == null || endNode == null) return null;
        if (!adjacencyList.containsKey(startNode) || !adjacencyList.containsKey(endNode)) {
            return null;
        }

        HashMap<String, Integer> distances = new HashMap<>();
        HashMap<String, String> predecessors = new HashMap<>();
        PriorityQueue<DijkstraNode> pq = new PriorityQueue<>();

        for (String node : adjacencyList.keySet()) {
            distances.put(node, Integer.MAX_VALUE);
        }

        distances.put(startNode, 0);
        pq.add(new DijkstraNode(startNode, 0));

        while (!pq.isEmpty()) {
            DijkstraNode current = pq.poll();
            String u = current.name;

            if (u.equals(endNode)) break;
            if (current.dist > distances.get(u)) continue;

            for (ConveyorSegment edge : adjacencyList.get(u)) {
                if (!edge.isAvailable()) continue; // Skip broken/offline conveyor segment

                String v = edge.getToNode();
                int weight = edge.getProcessingTime();

                if (distances.get(u) != Integer.MAX_VALUE && distances.get(u) + weight < distances.get(v)) {
                    distances.put(v, distances.get(u) + weight);
                    predecessors.put(v, u);
                    pq.add(new DijkstraNode(v, distances.get(v)));
                }
            }
        }

        if (distances.get(endNode) == Integer.MAX_VALUE) {
            return null; // No available route found
        }

        ArrayList<String> route = new ArrayList<>();
        String curr = endNode;
        while (curr != null) {
            route.add(curr);
            curr = predecessors.get(curr);
        }
        Collections.reverse(route);

        return new RoutingResult(route, distances.get(endNode));
    }
}
