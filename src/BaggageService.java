import java.util.*;

public final class BaggageService {
    private HashMap<String, Flight> flights;
    private HashMap<String, LoadingBay> bays;
    private HashMap<String, Passenger> passengers;
    private HashMap<String, Baggage> allBaggage;

    private PriorityQueue<Baggage> processQueue;
    private ArrayDeque<Baggage> conveyorWaitingQueue;
    private ConveyorRoutePlanner conveyorPlanner;

    private int simulatedTime; // Minutes from midnight (e.g. 540 = 09:00 AM)

    public BaggageService() {
        this(null);
    }

    public BaggageService(AuthManager auth) {
        this.simulatedTime = 540; // 09:00 AM

        flights = new HashMap<>();
        bays = new HashMap<>();
        passengers = new HashMap<>();
        allBaggage = new HashMap<>();
        conveyorWaitingQueue = new ArrayDeque<>();
        conveyorPlanner = new ConveyorRoutePlanner();

        // Deterministic PriorityQueue comparator:
        // 1. Departure loading cutoff (earliest cutoff first) - ALWAYS PRIMARY
        // 2. Transfer urgency over origin baggage
        // 3. Passenger category rank (First > Business > Economy)
        // 4. Fuzzy urgency score (higher urgency first, only after cutoff ties)
        // 5. Arrival order
        // 6. Bag ID tie-breaker
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

                // Rule 4: Fuzzy urgency, consulted only after the existing cutoff,
                // transfer and category rules have tied. It can never override cutoff.
                int u1 = b1.getUrgencyScore();
                int u2 = b2.getUrgencyScore();
                if (u1 > u2) return -1;
                if (u1 < u2) return 1;

                // Rule 5: Arrival sequence order
                if (b1.getArrivalOrder() < b2.getArrivalOrder()) return -1;
                if (b1.getArrivalOrder() > b2.getArrivalOrder()) return 1;

                // Rule 6: Deterministic bagId tie-breaker
                return b1.getBagId().compareTo(b2.getBagId());
            }
        };

        processQueue = new PriorityQueue<>(baggageComparator);

        DatabaseManager.initializeDatabase();
        DatabaseManager.seedInitialDataIfEmpty();

        loadFromDatabase();
    }

    /**
     * Loads all runtime operational state directly from the persistent SQLite database.
     * Reconstructs bays, conveyors, flights, passengers, baggage, route paths, urgencies
     * and priority queues without relying on hardcoded Java objects.
     */
    public final void loadFromDatabase() {
        bays.clear();
        DatabaseManager.loadLoadingBays(this);

        conveyorPlanner.clear();
        DatabaseManager.loadConveyors(conveyorPlanner);

        flights.clear();
        DatabaseManager.loadFlights(this);

        passengers.clear();
        loadPassengersFromDB();

        allBaggage.clear();
        DatabaseManager.loadBaggage(this);
        DatabaseManager.validateAndRepairBaggageRecords(this);

        refreshUrgencies();
        rebuildProcessQueue();
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
    public ArrayDeque<Baggage> getConveyorWaitingQueue() { return conveyorWaitingQueue; }

    /**
     * Recomputes fuzzy urgency for every bag. Called whenever baggage, flight,
     * route, conveyor or simulated-time data changes.
     */
    public void refreshUrgencies() {
        for (Baggage bag : allBaggage.values()) {
            bag.setUrgency(FuzzyUrgency.evaluate(bag, simulatedTime));
        }
    }

    /**
     * PriorityQueue does not automatically re-heap after a bag's cutoff or
     * urgency changes, so rebuild the queue from the authoritative bag map.
     * Also rebuilds the FIFO conveyor entry waiting queue.
     */
    public void rebuildProcessQueue() {
        processQueue.clear();
        conveyorWaitingQueue.clear();
        for (Baggage bag : allBaggage.values()) {
            if (bag.getStatus() == BaggageStatus.QUEUED) {
                processQueue.add(bag);
                conveyorWaitingQueue.addLast(bag);
            }
        }
    }

    /** Peeks at the next bag without processing it (used by verification tests). */
    public Baggage peekNextBaggage() {
        refreshUrgencies();
        rebuildProcessQueue();
        return processQueue.peek();
    }

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

    public void initializeCleanSampleData() {
        DatabaseManager.seedInitialDataIfEmpty(this);
        loadFromDatabase();
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
            refreshUrgencies();
            rebuildProcessQueue();
            RedisCache.invalidateAllBaggageTracks();
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

        newBag.setQueuedSince(simulatedTime);
        allBaggage.put(bagId, newBag);
        DatabaseManager.saveBaggage(newBag);
        DatabaseManager.logAudit(user.getUsername(), "REGISTER_BAGGAGE", bagId, simulatedTime, "SUCCESS",
                "Passenger: " + passengerId + ", Type: " + newBag.getBagType());
        refreshUrgencies();
        rebuildProcessQueue();
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
        String destination = resolveDestinationNode(bag);
        if (destination != null && !destination.isEmpty()) {
            RoutingResult res = conveyorPlanner.getShortestPath(bag.getCurrentLocation(), destination);
            if (res != null) {
                bag.setCurrentRoute(res.route);
                bag.setEstimatedTime(res.totalTime);
            }
        }
        DatabaseManager.saveBaggage(bag);
        DatabaseManager.logAudit(user.getUsername(), "ASSIGN_BAGGAGE", bagId, simulatedTime, "SUCCESS", "Assigned to flight " + flightId);
        refreshUrgencies();
        rebuildProcessQueue();
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
            if (bag.getQueuedSince() <= 0) {
                bag.setQueuedSince(simulatedTime);
            }
            enqueueConveyorWaiting(bag);
            DatabaseManager.saveBaggage(bag);
            DatabaseManager.logAudit(user.getUsername(), "GRANT_CLEARANCE", bagId, simulatedTime, "SUCCESS", reason);
            refreshUrgencies();
            rebuildProcessQueue();
        } else {
            throw new IllegalStateException("Baggage cannot be cleared in state: " + bag.getStatus());
        }
    }

    public void rejectClearance(String bagId, String reason, User user) {
        AuthManager.requireClearancePermission(user);
        Baggage bag = allBaggage.get(bagId);
        if (bag == null) throw new IllegalArgumentException("Unknown Baggage ID: " + bagId);

        bag.setClearance(false, reason != null && !reason.isEmpty() ? reason : "Security check rejected");
        conveyorWaitingQueue.remove(bag);
        DatabaseManager.saveBaggage(bag);
        DatabaseManager.logAudit(user.getUsername(), "REJECT_CLEARANCE", bagId, simulatedTime, "SUCCESS", reason);
        refreshUrgencies();
        rebuildProcessQueue();
    }

    // ==================== BAGGAGE PROCESSING & ROUTING ====================

    public RoutingResult calculateRoute(String startNode, String endNode) {
        return conveyorPlanner.getShortestPath(startNode, endNode);
    }

    public Baggage processNextBaggage(User user) {
        AuthManager.requireUserPermission(user, Role.ADMIN, Role.BAGGAGE_MANAGER);
        refreshUrgencies();
        rebuildProcessQueue();
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

        refreshUrgencies();
        rebuildProcessQueue();
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
            refreshUrgencies();
            rebuildProcessQueue();
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
            refreshUrgencies();
            rebuildProcessQueue();
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
        refreshUrgencies();
        rebuildProcessQueue();
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
        refreshUrgencies();
        rebuildProcessQueue();
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
            refreshUrgencies();
            rebuildProcessQueue();
            throw new IllegalStateException("Security clearance required before processing bag " + bag.getBagId());
        }

        String startLoc = bag.getCurrentLocation();
        String endLoc = resolveDestinationNode(bag);

        if (endLoc == null || endLoc.isEmpty()) {
            bag.setStatus(BaggageStatus.AWAITING_CLEARANCE);
            DatabaseManager.saveBaggage(bag);
            refreshUrgencies();
            rebuildProcessQueue();
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
            refreshUrgencies();
            rebuildProcessQueue();
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
                refreshUrgencies();
                rebuildProcessQueue();
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
        refreshUrgencies();
        rebuildProcessQueue();
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
        refreshUrgencies();
        rebuildProcessQueue();
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
        refreshUrgencies();
        rebuildProcessQueue();
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
        refreshUrgencies();
        rebuildProcessQueue();
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
        stats.put("conveyorWaitingCount", conveyorWaitingQueue.size());
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

    public void clear() {
        adjacencyList.clear();
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
