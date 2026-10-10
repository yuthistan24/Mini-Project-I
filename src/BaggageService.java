import java.util.*;

public class BaggageService {
    private HashMap<String, Flight> flights;
    private HashMap<String, LoadingBay> bays;
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
        allBaggage = new HashMap<>();
        conveyorWaitingQueue = new ArrayDeque<>();
        conveyorPlanner = new ConveyorRoutePlanner();

        // Strict deterministic priority queue comparator based on:
        // 1. Earliest loading cutoff (for outgoing/transfer bags)
        // 2. Transfer status and passenger category priority
        // 3. Arrival order
        // 4. Bag ID tie-breaker
        Comparator<Baggage> baggageComparator = new Comparator<Baggage>() {
            @Override
            public int compare(Baggage b1, Baggage b2) {
                // Rule 1: Earliest loading cutoff (only applicable if both have valid cutoffs)
                int c1 = b1.getLoadingCutoff() > 0 ? b1.getLoadingCutoff() : Integer.MAX_VALUE;
                int c2 = b2.getLoadingCutoff() > 0 ? b2.getLoadingCutoff() : Integer.MAX_VALUE;
                if (c1 < c2) return -1;
                if (c1 > c2) return 1;

                // Rule 2: Transfer bags given higher precedence over origin bags
                boolean t1 = b1.isTransferBag();
                boolean t2 = b2.isTransferBag();
                if (t1 && !t2) return -1;
                if (!t1 && t2) return 1;

                // Passenger Category Priority (Rank 1: First Class, Rank 2: Business, Rank 3: Economy)
                int rank1 = b1.getPassengerCategory().getRank();
                int rank2 = b2.getPassengerCategory().getRank();
                if (rank1 < rank2) return -1;
                if (rank1 > rank2) return 1;

                // Rule 3: Arrival order
                if (b1.getArrivalOrder() < b2.getArrivalOrder()) return -1;
                if (b1.getArrivalOrder() > b2.getArrivalOrder()) return 1;

                // Rule 4: Deterministic bagId tie-breaker
                return b1.getBagId().compareTo(b2.getBagId());
            }
        };

        processQueue = new PriorityQueue<>(baggageComparator);

        DatabaseManager.initializeDatabase();

        // Always ensure standard network nodes and segments are present
        conveyorPlanner.initializeNetwork();
        if (DatabaseManager.hasData("ConveyorSegment")) {
            DatabaseManager.loadConveyors(conveyorPlanner);
        }

        // Initialize standard bays
        bays.put("BAY-A", new LoadingBay("BAY-A"));
        bays.put("BAY-B", new LoadingBay("BAY-B"));
        bays.put("BAY-C", new LoadingBay("BAY-C"));
        bays.put("BAY-D", new LoadingBay("BAY-D"));

        // Initialize flights & sample baggage if not present
        if (!DatabaseManager.hasData("Flight")) {
            initializeSyntheticData();
        } else {
            DatabaseManager.loadFlights(this);
            DatabaseManager.loadBaggage(this);
        }
    }

    public Flight getFlight(String flightId) { return flights.get(flightId); }
    public LoadingBay getBay(String bayId) { return bays.get(bayId); }
    public Baggage getBaggage(String bagId) { return allBaggage.get(bagId); }
    public Map<String, Flight> getAllFlights() { return flights; }
    public Map<String, LoadingBay> getAllBays() { return bays; }
    public Map<String, Baggage> getAllBaggage() { return allBaggage; }
    public ConveyorRoutePlanner getConveyorPlanner() { return conveyorPlanner; }

    public void addLoadedFlight(Flight flight) {
        flights.put(flight.getFlightId(), flight);
    }

    public void addLoadedBaggage(Baggage bag) {
        allBaggage.put(bag.getBagId(), bag);
        if (bag.getStatus() == BaggageStatus.QUEUED) {
            processQueue.add(bag);
        }
    }

    private void initializeSyntheticData() {
        Flight f101 = new Flight("F101", "Chennai", 630); // 10:30 AM
        Flight f202 = new Flight("F202", "Madurai", 615); // 10:15 AM
        Flight f303 = new Flight("F303", "Coimbatore", 640); // 10:40 AM
        Flight f404 = new Flight("F404", "Trichy", 620); // 10:20 AM

        flights.put("F101", f101);
        flights.put("F202", f202);
        flights.put("F303", f303);
        flights.put("F404", f404);

        bays.get("BAY-A").setAssignedFlight(f101);
        bays.get("BAY-B").setAssignedFlight(f202);
        bays.get("BAY-C").setAssignedFlight(f303);
        bays.get("BAY-D").setAssignedFlight(f404);

        DatabaseManager.saveFlight(f101);
        DatabaseManager.saveFlight(f202);
        DatabaseManager.saveFlight(f303);
        DatabaseManager.saveFlight(f404);

        int arrivalCounter = 1;
        String[] flightIds = { "F101", "F202", "F303", "F404" };
        PassengerCategory[] categories = {
            PassengerCategory.FIRST_CLASS,
            PassengerCategory.BUSINESS,
            PassengerCategory.ECONOMY
        };

        // Create sample bags for outgoing, incoming, and transfer workflows
        for (int i = 1; i <= 24; i++) {
            String bagId = String.format("B%03d", i);
            PassengerCategory cat = categories[i % 3];

            Baggage bag;
            if (i % 6 == 0) {
                // Incoming bag
                bag = new IncomingBag(bagId, cat, arrivalCounter++);
                bag.setDestinationNode("CLAIM-CAROUSEL-1");
                bag.setStatus(BaggageStatus.QUEUED);
                processQueue.add(bag);
            } else if (i % 4 == 0) {
                // Transfer bag
                bag = new TransferBag(bagId, cat, arrivalCounter++);
                Flight assignedFlight = flights.get(flightIds[i % 4]);
                bag.assignFlight(assignedFlight);
                bag.setClearance(true, "Auto-cleared sample data");
                processQueue.add(bag);
            } else {
                // Outgoing origin bag
                bag = new OriginBag(bagId, cat, arrivalCounter++);
                Flight assignedFlight = flights.get(flightIds[i % 4]);
                bag.assignFlight(assignedFlight);

                if (i % 5 != 0) {
                    bag.setClearance(true, "Security screening passed");
                    processQueue.add(bag);
                } else {
                    bag.setStatus(BaggageStatus.WAITING_FOR_CLEARANCE);
                }
            }

            allBaggage.put(bagId, bag);
            DatabaseManager.saveBaggage(bag);
        }
    }

    public int getSimulatedTime() { return simulatedTime; }

    public String getFormattedSimulatedTime() {
        int hours = simulatedTime / 60;
        int minutes = simulatedTime % 60;
        return String.format("%02d:%02d", hours, minutes);
    }

    public void advanceSimulatedClock(int minutes, User user) {
        AuthManager.requireUserPermission(user, Role.ADMIN, Role.SUPERVISOR);
        if (minutes > 0) {
            simulatedTime += minutes;
            DatabaseManager.logAudit(user.getUsername(), "ADVANCE_TIME", "System", simulatedTime, "SUCCESS", "Advanced by " + minutes + " mins to " + getFormattedSimulatedTime());
        }
    }

    // ==================== BAGGAGE REGISTRATION & ASSIGNMENT ====================

    public Baggage registerBaggage(String bagId, BaggageDirection direction, int catRank, String flightId, String destNode, User user) {
        AuthManager.requireUserPermission(user, Role.ADMIN, Role.BAGGAGE_OPERATOR);
        if (allBaggage.containsKey(bagId)) {
            throw new IllegalArgumentException("Baggage ID " + bagId + " already exists.");
        }

        PassengerCategory cat = PassengerCategory.ECONOMY;
        if (catRank == 1) cat = PassengerCategory.FIRST_CLASS;
        else if (catRank == 2) cat = PassengerCategory.BUSINESS;

        Baggage newBag;
        if (direction == BaggageDirection.INCOMING) {
            newBag = new IncomingBag(bagId, cat, allBaggage.size() + 1);
            if (destNode != null && !destNode.isEmpty()) {
                newBag.setDestinationNode(destNode);
            }
            processQueue.add(newBag);
        } else if (direction == BaggageDirection.TRANSFER) {
            newBag = new TransferBag(bagId, cat, allBaggage.size() + 1);
            if (flightId != null && !flightId.isEmpty()) {
                Flight f = flights.get(flightId);
                if (f != null) newBag.assignFlight(f);
            }
        } else {
            newBag = new OriginBag(bagId, cat, allBaggage.size() + 1);
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
        DatabaseManager.logAudit(user.getUsername(), "REGISTER_BAGGAGE", bagId, simulatedTime, "SUCCESS", "Type: " + newBag.getBagType());
        return newBag;
    }

    public void assignBaggageToFlight(String bagId, String flightId, User user) {
        AuthManager.requireUserPermission(user, Role.ADMIN, Role.BAGGAGE_OPERATOR);
        Baggage bag = allBaggage.get(bagId);
        Flight flight = flights.get(flightId);

        if (bag == null) throw new IllegalArgumentException("Unknown Baggage ID: " + bagId);
        if (flight == null) throw new IllegalArgumentException("Unknown Flight ID: " + flightId);

        if (bag.getStatus() == BaggageStatus.LOADED) {
            throw new IllegalStateException("Cannot reassign: Baggage already loaded.");
        }

        bag.assignFlight(flight);
        DatabaseManager.saveBaggage(bag);
        DatabaseManager.logAudit(user.getUsername(), "ASSIGN_BAGGAGE", bagId, simulatedTime, "SUCCESS", "Assigned to " + flightId);
    }

    // ==================== SECURITY CLEARANCE ====================

    public void grantOperatorClearance(String bagId, String reason, User user) {
        AuthManager.requireUserPermission(user, Role.SECURITY_OFFICER);
        Baggage bag = allBaggage.get(bagId);
        if (bag == null) throw new IllegalArgumentException("Unknown Baggage ID: " + bagId);

        if (bag.isIncomingBag()) {
            throw new IllegalStateException("Incoming baggage does not require outbound security clearance.");
        }

        if (bag.getStatus() == BaggageStatus.WAITING_FOR_CLEARANCE || bag.getStatus() == BaggageStatus.REJECTED) {
            bag.setClearance(true, reason != null ? reason : "Security screening passed");
            processQueue.remove(bag);
            processQueue.add(bag);
            DatabaseManager.saveBaggage(bag);
            DatabaseManager.logAudit(user.getUsername(), "GRANT_CLEARANCE", bagId, simulatedTime, "SUCCESS", reason);
        } else {
            throw new IllegalStateException("Baggage cannot be cleared in its current state (" + bag.getStatus() + ").");
        }
    }

    public void rejectOperatorClearance(String bagId, String reason, User user) {
        AuthManager.requireUserPermission(user, Role.SECURITY_OFFICER);
        Baggage bag = allBaggage.get(bagId);
        if (bag == null) throw new IllegalArgumentException("Unknown Baggage ID: " + bagId);

        bag.setClearance(false, reason != null ? reason : "Security check failed");
        processQueue.remove(bag);
        DatabaseManager.saveBaggage(bag);
        DatabaseManager.logAudit(user.getUsername(), "REJECT_CLEARANCE", bagId, simulatedTime, "SUCCESS", reason);
    }

    // ==================== BAGGAGE PROCESSING & ROUTING ====================

    public RoutingResult calculateRoute(String startNode, String endNode) {
        return conveyorPlanner.getShortestPath(startNode, endNode);
    }

    public Baggage processNextBaggage(User user) {
        AuthManager.requireUserPermission(user, Role.ADMIN, Role.BAGGAGE_OPERATOR);
        if (processQueue.isEmpty()) {
            return null;
        }

        Baggage bag = processQueue.poll();
        processBaggageInternal(bag, user);
        return bag;
    }

    public Baggage processSpecificBaggage(String bagId, User user) {
        AuthManager.requireUserPermission(user, Role.ADMIN, Role.BAGGAGE_OPERATOR);
        Baggage bag = allBaggage.get(bagId);
        if (bag == null) throw new IllegalArgumentException("Unknown Baggage ID: " + bagId);

        processQueue.remove(bag);
        processBaggageInternal(bag, user);
        return bag;
    }

    private void processBaggageInternal(Baggage bag, User user) {
        // Enforce clearance for outbound/transfer
        if (!bag.isIncomingBag() && !bag.isOperatorCleared()) {
            bag.setStatus(BaggageStatus.WAITING_FOR_CLEARANCE);
            DatabaseManager.saveBaggage(bag);
            DatabaseManager.logAudit(user.getUsername(), "PROCESS_BAG", bag.getBagId(), simulatedTime, "FAILED", "CLEARANCE_REQUIRED");
            throw new IllegalStateException("Security clearance required before processing bag " + bag.getBagId());
        }

        String startLoc = bag.getCurrentLocation();
        String endLoc = bag.getDestinationNode();

        if (endLoc == null || endLoc.isEmpty()) {
            if (bag.getAssignedFlight() != null && bag.getAssignedFlight().getAssignedBay() != null) {
                endLoc = bag.getAssignedFlight().getAssignedBay().getBayId();
                bag.setDestinationNode(endLoc);
            } else if (bag.isIncomingBag()) {
                endLoc = "CLAIM-CAROUSEL-1";
                bag.setDestinationNode(endLoc);
            } else {
                bag.setStatus(BaggageStatus.WAITING_FOR_ASSIGNMENT);
                DatabaseManager.saveBaggage(bag);
                throw new IllegalStateException("No assigned destination or loading bay for bag " + bag.getBagId());
            }
        }

        RoutingResult result = conveyorPlanner.getShortestPath(startLoc, endLoc);

        if (result == null) {
            bag.setStatus(BaggageStatus.NO_ROUTE_AVAILABLE);
            bag.setEstimatedTime(-1);
            bag.setCurrentRoute(new ArrayList<>());
            DatabaseManager.saveBaggage(bag);
            DatabaseManager.saveRoutingRecord(bag.getBagId(), startLoc, endLoc, -1, "NO_ROUTE", simulatedTime);
            DatabaseManager.logAudit(user.getUsername(), "PROCESS_BAG", bag.getBagId(), simulatedTime, "FAILED", "NO_ROUTE_AVAILABLE");
            return;
        }

        bag.setCurrentRoute(result.route);
        bag.setEstimatedTime(result.totalTime);

        // Cutoff validation for Outgoing & Transfer baggage
        if (!bag.isIncomingBag()) {
            int estimatedArrival = simulatedTime + result.totalTime;
            if (estimatedArrival > bag.getLoadingCutoff()) {
                bag.setStatus(BaggageStatus.MISSED_CUTOFF);
                DatabaseManager.saveBaggage(bag);
                DatabaseManager.saveRoutingRecord(bag.getBagId(), startLoc, endLoc, result.totalTime, "MISSED_CUTOFF", simulatedTime);
                DatabaseManager.logAudit(user.getUsername(), "PROCESS_BAG", bag.getBagId(), simulatedTime, "FAILED", "MISSED_CUTOFF (Arrival: " + estimatedArrival + " > Cutoff: " + bag.getLoadingCutoff() + ")");
                return;
            }
        }

        // Travel along conveyor path
        bag.setStatus(BaggageStatus.ROUTING);
        for (int i = 1; i < result.route.size(); i++) {
            bag.setCurrentLocation(result.route.get(i));
        }

        // Final completion status
        if (bag.isIncomingBag()) {
            bag.setStatus(BaggageStatus.CLAIMED);
            DatabaseManager.saveRoutingRecord(bag.getBagId(), startLoc, endLoc, result.totalTime, "CLAIMED", simulatedTime);
        } else {
            bag.setStatus(BaggageStatus.LOADED);
            DatabaseManager.saveRoutingRecord(bag.getBagId(), startLoc, endLoc, result.totalTime, "LOADED", simulatedTime);
        }

        DatabaseManager.saveBaggage(bag);
        DatabaseManager.logAudit(user.getUsername(), "PROCESS_BAG", bag.getBagId(), simulatedTime, "SUCCESS", "Final destination reached: " + endLoc);
    }

    // ==================== CONVEYOR NETWORK MANAGEMENT ====================

    public boolean markConveyorUnavailable(String from, String to, User user) {
        AuthManager.requireUserPermission(user, Role.ADMIN, Role.SUPERVISOR);
        boolean success = conveyorPlanner.markConveyorUnavailable(from, to);
        if (success) {
            DatabaseManager.logAudit(user.getUsername(), "DISABLE_CONVEYOR", from + "->" + to, simulatedTime, "SUCCESS", null);
            recalculateActiveRoutes();
        }
        return success;
    }

    public boolean restoreConveyor(String from, String to, User user) {
        AuthManager.requireUserPermission(user, Role.ADMIN, Role.SUPERVISOR);
        boolean success = conveyorPlanner.restoreConveyor(from, to);
        if (success) {
            DatabaseManager.logAudit(user.getUsername(), "ENABLE_CONVEYOR", from + "->" + to, simulatedTime, "SUCCESS", null);
            recalculateActiveRoutes();
        }
        return success;
    }

    private void recalculateActiveRoutes() {
        for (Baggage bag : allBaggage.values()) {
            if (bag.getStatus() == BaggageStatus.QUEUED || bag.getStatus() == BaggageStatus.WAITING_FOR_CLEARANCE || bag.getStatus() == BaggageStatus.NO_ROUTE_AVAILABLE) {
                String dest = bag.getDestinationNode();
                if (dest != null && !dest.isEmpty()) {
                    RoutingResult res = conveyorPlanner.getShortestPath(bag.getCurrentLocation(), dest);
                    if (res != null) {
                        bag.setCurrentRoute(res.route);
                        bag.setEstimatedTime(res.totalTime);
                        if (bag.getStatus() == BaggageStatus.NO_ROUTE_AVAILABLE) {
                            bag.setStatus(bag.isOperatorCleared() ? BaggageStatus.QUEUED : BaggageStatus.WAITING_FOR_CLEARANCE);
                        }
                    } else {
                        bag.setEstimatedTime(-1);
                    }
                    DatabaseManager.saveBaggage(bag);
                }
            }
        }
    }

    // ==================== FLIGHT & BAY UPDATES ====================

    public void changeFlightBay(String flightId, String newBayId, User user) {
        AuthManager.requireUserPermission(user, Role.ADMIN, Role.SUPERVISOR);
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

        // Update destination on all bags assigned to this flight that are not yet loaded
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
        AuthManager.requireUserPermission(user, Role.ADMIN, Role.SUPERVISOR);
        Flight f = flights.get(flightId);
        if (f == null) throw new IllegalArgumentException("Invalid flight ID: " + flightId);

        f.setLoadingCutoff(newCutoff);
        DatabaseManager.saveFlight(f);

        for (Baggage bag : allBaggage.values()) {
            if (bag.getAssignedFlight() != null && bag.getAssignedFlight().getFlightId().equals(flightId)) {
                bag.assignFlight(f);
                DatabaseManager.saveBaggage(bag);
            }
        }

        DatabaseManager.logAudit(user.getUsername(), "UPDATE_FLIGHT_CUTOFF", flightId, simulatedTime, "SUCCESS", "New cutoff: " + f.getFormattedCutoff());
    }

    // ==================== DASHBOARD STATS ====================

    public Map<String, Object> getDashboardStats() {
        int total = allBaggage.size();
        int awaitingClearance = 0;
        int queued = 0;
        int loaded = 0;
        int claimed = 0;
        int missedCutoff = 0;

        for (Baggage b : allBaggage.values()) {
            switch (b.getStatus()) {
                case WAITING_FOR_CLEARANCE: awaitingClearance++; break;
                case QUEUED: queued++; break;
                case LOADED: loaded++; break;
                case CLAIMED: claimed++; break;
                case MISSED_CUTOFF: missedCutoff++; break;
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
        stats.put("awaitingClearance", awaitingClearance);
        stats.put("queued", queued);
        stats.put("loaded", loaded);
        stats.put("claimed", claimed);
        stats.put("missedCutoff", missedCutoff);
        stats.put("flightCount", flights.size());
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

        // Inbound & Claim conveyor paths (Mentor recommendation for two-way operations)
        addSegment("UNLOAD-POINT", "CENTRAL", 5);
        addSegment("CENTRAL", "CLAIM-AREA", 4);
        addSegment("CLAIM-AREA", "CLAIM-CAROUSEL-1", 3);
        addSegment("CLAIM-AREA", "CLAIM-CAROUSEL-2", 4);
        addSegment("UNLOAD-POINT", "TRANSFER-A", 6); // Arriving bags transferring to another flight
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
                if (!edge.isAvailable()) continue; // Skip failed/disabled conveyor segments

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
