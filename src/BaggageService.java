// BaggageService.java

import java.util.*;

public class BaggageService {
    private HashMap<String, Flight> flights;
    private HashMap<String, LoadingBay> bays;
    private HashMap<String, Baggage> allBaggage;

    private PriorityQueue<Baggage> processQueue;
    private ArrayDeque<Baggage> conveyorWaitingQueue; // FIFO for demonstration
    private ConveyorRoutePlanner conveyorPlanner;

    private int simulatedTime; // minutes from midnight
    private AuthManager auth;

    public BaggageService(AuthManager auth) {
        this.simulatedTime = 540; // Start at 09:00 AM (9 * 60)
        this.auth = auth;

        flights = new HashMap<>();
        bays = new HashMap<>();
        allBaggage = new HashMap<>();
        conveyorWaitingQueue = new ArrayDeque<>();
        conveyorPlanner = new ConveyorRoutePlanner();

        // Custom Comparator without advanced chaining
        Comparator<Baggage> baggageComparator = new Comparator<Baggage>() {
            @Override
            public int compare(Baggage b1, Baggage b2) {
                if (b1.getLoadingCutoff() < b2.getLoadingCutoff()) return -1;
                if (b1.getLoadingCutoff() > b2.getLoadingCutoff()) return 1;

                boolean t1 = b1.isTransferBag();
                boolean t2 = b2.isTransferBag();
                if (t1 && !t2) return -1;
                if (!t1 && t2) return 1;

                int rank1 = b1.getPassengerCategory().getRank();
                int rank2 = b2.getPassengerCategory().getRank();
                if (rank1 < rank2) return -1;
                if (rank1 > rank2) return 1;

                if (b1.getArrivalOrder() < b2.getArrivalOrder()) return -1;
                if (b1.getArrivalOrder() > b2.getArrivalOrder()) return 1;

                return b1.getBagId().compareTo(b2.getBagId());
            }
        };

        processQueue = new PriorityQueue<>(baggageComparator);
        
        DatabaseManager.initializeDatabase();
        
        if (!DatabaseManager.hasData("ConveyorSegment")) {
            conveyorPlanner.initializeNetwork();
        } else {
            DatabaseManager.loadConveyors(conveyorPlanner);
        }
        
        if (!DatabaseManager.hasData("Flight")) {
            initializeSyntheticData();
        } else {
            bays.put("BAY-A", new LoadingBay("BAY-A"));
            bays.put("BAY-B", new LoadingBay("BAY-B"));
            bays.put("BAY-C", new LoadingBay("BAY-C"));
            bays.put("BAY-D", new LoadingBay("BAY-D"));
            
            DatabaseManager.loadFlights(this);
            DatabaseManager.loadBaggage(this);
        }
    }
    
    public Flight getFlight(String flightId) { return flights.get(flightId); }
    public LoadingBay getBay(String bayId) { return bays.get(bayId); }
    
    public void addLoadedFlight(Flight flight) {
        flights.put(flight.getFlightId(), flight);
    }
    
    public void addLoadedBaggage(Baggage bag) {
        allBaggage.put(bag.getBagId(), bag);
        if (bag.getStatus() == BaggageStatus.CLEARED || bag.getStatus() == BaggageStatus.QUEUED) {
            processQueue.add(bag);
        }
    }

    private void initializeSyntheticData() {
        // Create 4 Loading Bays
        bays.put("BAY-A", new LoadingBay("BAY-A"));
        bays.put("BAY-B", new LoadingBay("BAY-B"));
        bays.put("BAY-C", new LoadingBay("BAY-C"));
        bays.put("BAY-D", new LoadingBay("BAY-D"));

        Flight f101 = new Flight("F101", "Chennai", 630);
        Flight f202 = new Flight("F202", "Madurai", 615);
        Flight f303 = new Flight("F303", "Coimbatore", 640);
        Flight f404 = new Flight("F404", "Trichy", 620);
        
        flights.put("F101", f101); 
        flights.put("F202", f202); 
        flights.put("F303", f303); 
        flights.put("F404", f404);

        bays.get("BAY-A").setAssignedFlight(flights.get("F101"));
        bays.get("BAY-B").setAssignedFlight(flights.get("F202"));
        bays.get("BAY-C").setAssignedFlight(flights.get("F303"));
        bays.get("BAY-D").setAssignedFlight(flights.get("F404"));
        
        DatabaseManager.saveFlight(f101);
        DatabaseManager.saveFlight(f202);
        DatabaseManager.saveFlight(f303);
        DatabaseManager.saveFlight(f404);

        int arrivalCounter = 1;
        String[] flightIds = { "F101", "F202", "F303", "F404" };
        PassengerCategory[] categories = { PassengerCategory.FIRST_CLASS, PassengerCategory.BUSINESS, PassengerCategory.ECONOMY };

        for (int i = 1; i <= 32; i++) {
            String bagId = "B";
            if (i < 10) bagId += "00" + i;
            else bagId += "0" + i;

            Baggage bag;
            if (i % 3 == 0) bag = new TransferBag(bagId, categories[i % 3], arrivalCounter++);
            else bag = new OriginBag(bagId, categories[i % 3], arrivalCounter++);

            Flight assignedFlight = flights.get(flightIds[i % 4]);
            bag.assignFlight(assignedFlight);

            if (i % 5 != 0) {
                bag.setOperatorClearance(true);
                bag.setStatus(BaggageStatus.QUEUED);
                processQueue.add(bag);
            } else {
                bag.setStatus(BaggageStatus.WAITING_FOR_CLEARANCE);
                bag.setOperatorClearance(false);
            }

            allBaggage.put(bagId, bag);
            DatabaseManager.saveBaggage(bag);
        }
    }

    public String getFormattedSimulatedTime() {
        int hours = simulatedTime / 60;
        int minutes = simulatedTime % 60;
        return String.format("%02d:%02d", hours, minutes);
    }
    
    public void advanceSimulatedClock(int minutes) {
        auth.requirePermission(Role.ADMIN, Role.SUPERVISOR);
        if (minutes > 0) {
            simulatedTime += minutes;
            System.out.println("Clock advanced by " + minutes + " minutes. Current time: " + getFormattedSimulatedTime());
            DatabaseManager.logAudit(auth.getCurrentUser().getUsername(), "ADVANCE_TIME", "System", simulatedTime, "SUCCESS", "Advanced by " + minutes + " mins");
        }
    }

    public void displayFlights() {
        auth.requirePermission(Role.ADMIN, Role.SUPERVISOR, Role.BAGGAGE_OPERATOR, Role.AUDITOR);
        System.out.println("\n==================================================");
        System.out.println("               FLIGHT SCHEDULE");
        System.out.println("==================================================");
        for (Flight f : flights.values()) {
            System.out.println(" ✈ " + f.toString());
        }
        System.out.println("==================================================");
    }

    public void displayBaggage() {
        auth.requirePermission(Role.ADMIN, Role.SUPERVISOR, Role.BAGGAGE_OPERATOR, Role.AUDITOR);
        System.out.println("\n==================================================");
        System.out.println("               ALL BAGGAGE RECORDS");
        System.out.println("==================================================");
        for (Baggage b : allBaggage.values()) {
            System.out.println(" 🧳 " + b.toString());
        }
        System.out.println("==================================================");
    }
    
    public void displayPendingBaggage() {
        auth.requirePermission(Role.ADMIN, Role.SECURITY_OFFICER);
        System.out.println("\n==================================================");
        System.out.println("         BAGGAGE AWAITING CLEARANCE");
        System.out.println("==================================================");
        for (Baggage b : allBaggage.values()) {
            if (b.getStatus() == BaggageStatus.WAITING_FOR_CLEARANCE) {
                System.out.println(" 🧳 " + b.toString());
            }
        }
        System.out.println("==================================================");
    }

    public void displayBays() {
        auth.requirePermission(Role.ADMIN, Role.SUPERVISOR, Role.BAGGAGE_OPERATOR, Role.AUDITOR);
        System.out.println("\n==================================================");
        System.out.println("                 LOADING BAYS");
        System.out.println("==================================================");
        for (LoadingBay bay : bays.values()) {
            System.out.println(" 🚛 " + bay.toString());
        }
        System.out.println("==================================================");
    }

    public void displayConveyorNetwork() {
        auth.requirePermission(Role.ADMIN, Role.SUPERVISOR, Role.BAGGAGE_OPERATOR, Role.AUDITOR);
        conveyorPlanner.displayNetwork();
    }
    
    public void markConveyorUnavailable(String from, String to) {
        auth.requirePermission(Role.ADMIN, Role.SUPERVISOR);
        if (conveyorPlanner.markConveyorUnavailable(from, to)) {
            System.out.println("Segment " + from + " -> " + to + " marked UNAVAILABLE.");
            DatabaseManager.logAudit(auth.getCurrentUser().getUsername(), "DISABLE_CONVEYOR", from + "->" + to, simulatedTime, "SUCCESS", null);
        } else {
            System.out.println("Error: Segment not found.");
        }
    }
    
    public void restoreConveyor(String from, String to) {
        auth.requirePermission(Role.ADMIN, Role.SUPERVISOR);
        if (conveyorPlanner.restoreConveyor(from, to)) {
            System.out.println("Segment " + from + " -> " + to + " marked AVAILABLE.");
            DatabaseManager.logAudit(auth.getCurrentUser().getUsername(), "ENABLE_CONVEYOR", from + "->" + to, simulatedTime, "SUCCESS", null);
        } else {
            System.out.println("Error: Segment not found.");
        }
    }
    
    public void changeFlightBay(String flightId, String newBayId) {
        auth.requirePermission(Role.ADMIN, Role.SUPERVISOR);
        Flight f = flights.get(flightId);
        LoadingBay newBay = bays.get(newBayId);
        
        if (f == null || newBay == null) {
            System.out.println("Error: Invalid flight ID or bay ID.");
            return;
        }
        
        if (f.getAssignedBay() != null) {
            f.getAssignedBay().setAssignedFlight(null);
        }
        
        newBay.setAssignedFlight(f);
        f.setAssignedBay(newBay);
        DatabaseManager.saveFlight(f);
        System.out.println("Flight " + flightId + " is now assigned to " + newBayId);
        DatabaseManager.logAudit(auth.getCurrentUser().getUsername(), "CHANGE_FLIGHT_BAY", flightId, simulatedTime, "SUCCESS", "New bay: " + newBayId);
    }
    
    public void viewRoutingHistory() {
        auth.requirePermission(Role.ADMIN, Role.SUPERVISOR, Role.AUDITOR);
        DatabaseManager.printRoutingHistory();
    }
    
    public void viewAuditLogs() {
        auth.requirePermission(Role.ADMIN, Role.AUDITOR);
        DatabaseManager.printAuditLogs();
    }

    public void registerBaggage(String bagId, boolean isTransfer, int catRank) {
        auth.requirePermission(Role.ADMIN, Role.BAGGAGE_OPERATOR);
        if (allBaggage.containsKey(bagId)) {
            System.out.println("Error: Baggage ID " + bagId + " already exists.");
            return;
        }

        PassengerCategory cat = PassengerCategory.ECONOMY;
        if (catRank == 1) cat = PassengerCategory.FIRST_CLASS;
        else if (catRank == 2) cat = PassengerCategory.BUSINESS;

        Baggage newBag;
        if (isTransfer) {
            newBag = new TransferBag(bagId, cat, allBaggage.size() + 1);
        } else {
            newBag = new OriginBag(bagId, cat, allBaggage.size() + 1);
        }

        allBaggage.put(bagId, newBag);
        DatabaseManager.saveBaggage(newBag);
        System.out.println("Successfully registered: " + bagId);
        DatabaseManager.logAudit(auth.getCurrentUser().getUsername(), "REGISTER_BAGGAGE", bagId, simulatedTime, "SUCCESS", null);
    }

    public void assignBaggageToFlight(String bagId, String flightId) {
        auth.requirePermission(Role.ADMIN, Role.BAGGAGE_OPERATOR);
        Baggage bag = allBaggage.get(bagId);
        Flight flight = flights.get(flightId);

        if (bag == null) {
            System.out.println("Error: Unknown Baggage ID.");
            return;
        }
        if (flight == null) {
            System.out.println("Error: Unknown Flight ID.");
            return;
        }
        
        if (bag.getStatus() == BaggageStatus.LOADED) {
            System.out.println("Error: Baggage already loaded.");
            return;
        }

        bag.assignFlight(flight);
        DatabaseManager.saveBaggage(bag);
        System.out.println("Assigned " + bagId + " to flight " + flightId + ".");
        System.out.println("Waiting for operator clearance.");
        DatabaseManager.logAudit(auth.getCurrentUser().getUsername(), "ASSIGN_BAGGAGE", bagId, simulatedTime, "SUCCESS", "Assigned to " + flightId);
    }
    
    public void grantOperatorClearance(String bagId, String reason) {
        auth.requirePermission(Role.SECURITY_OFFICER);
        Baggage bag = allBaggage.get(bagId);
        if (bag == null) {
            System.out.println("Error: Unknown Baggage ID.");
            return;
        }
        
        if (bag.getStatus() == BaggageStatus.WAITING_FOR_CLEARANCE) {
            bag.setOperatorClearance(true);
            bag.setStatus(BaggageStatus.QUEUED);
            processQueue.remove(bag);
            processQueue.add(bag);
            DatabaseManager.saveBaggage(bag);
            System.out.println("Operator clearance granted for " + bagId);
            DatabaseManager.logAudit(auth.getCurrentUser().getUsername(), "GRANT_CLEARANCE", bagId, simulatedTime, "SUCCESS", reason);
        } else {
            System.out.println("Baggage cannot be cleared in its current state.");
            DatabaseManager.logAudit(auth.getCurrentUser().getUsername(), "GRANT_CLEARANCE", bagId, simulatedTime, "FAILED", "Invalid state");
        }
    }
    
    public void revokeOperatorClearance(String bagId, String reason) {
        auth.requirePermission(Role.SECURITY_OFFICER);
        Baggage bag = allBaggage.get(bagId);
        if (bag == null) {
            System.out.println("Error: Unknown Baggage ID.");
            return;
        }
        
        if (bag.isOperatorCleared()) {
            bag.setOperatorClearance(false);
            if (bag.getStatus() == BaggageStatus.QUEUED) {
                bag.setStatus(BaggageStatus.WAITING_FOR_CLEARANCE);
                processQueue.remove(bag);
            }
            DatabaseManager.saveBaggage(bag);
            System.out.println("Operator clearance revoked for " + bagId);
            DatabaseManager.logAudit(auth.getCurrentUser().getUsername(), "REVOKE_CLEARANCE", bagId, simulatedTime, "SUCCESS", reason);
        } else {
            System.out.println("Baggage is not currently cleared.");
        }
    }

    public void printPriorityQueue() {
        auth.requirePermission(Role.ADMIN, Role.SUPERVISOR, Role.BAGGAGE_OPERATOR, Role.AUDITOR);
        System.out.println("\n==================================================");
        System.out.println("             BAGGAGE PRIORITY QUEUE");
        System.out.println("==================================================");
        System.out.printf(" %-6s | %-8s | %-8s | %-8s | %-10s\n", "Rank", "Bag ID", "Flight", "Cutoff", "Type");
        System.out.println("--------------------------------------------------");

        PriorityQueue<Baggage> copyQueue = new PriorityQueue<>(processQueue);
        int rank = 1;
        while (!copyQueue.isEmpty()) {
            Baggage b = copyQueue.poll();
            System.out.printf(" %-6d | %-8s | %-8s | %-8s | %-10s\n",
                    rank++, b.getBagId(), b.getAssignedFlight().getFlightId(), b.getFormattedCutoff(), b.getBagType());
        }
        System.out.println("==================================================");
    }

    public void processNextBaggage() {
        auth.requirePermission(Role.ADMIN, Role.BAGGAGE_OPERATOR);
        if (processQueue.isEmpty()) {
            System.out.println("No cleared baggage waiting for processing.");
            return;
        }

        Baggage nextBag = processQueue.poll();

        if (!nextBag.isOperatorCleared()) {
            System.out.println("STATUS: WAITING");
            System.out.println("REASON: OPERATOR CLEARANCE REQUIRED for " + nextBag.getBagId());
            return;
        }

        conveyorWaitingQueue.add(nextBag);
        Baggage processingBag = conveyorWaitingQueue.poll();

        System.out.println("========== PROCESSING BAGGAGE ==========");
        System.out.println("Bag ID       : " + processingBag.getBagId());
        System.out.println("Flight       : " + processingBag.getAssignedFlight().getFlightId());
        System.out.println("Type         : " + processingBag.getBagType());
        System.out.println("Cutoff       : " + processingBag.getFormattedCutoff());
        System.out.println("Clearance    : APPROVED");
        System.out.println("Loading Bay  : " + processingBag.getAssignedFlight().getAssignedBay().getBayId());
        
        String startLoc = processingBag.getCurrentLocation();
        String endLoc = processingBag.getAssignedFlight().getAssignedBay().getBayId();

        RoutingResult result = conveyorPlanner.getShortestPath(startLoc, endLoc);
        
        if (result == null) {
            System.out.println("\nSTATUS: NO_ROUTE_AVAILABLE");
            processingBag.setStatus(BaggageStatus.NO_ROUTE_AVAILABLE);
            DatabaseManager.saveBaggage(processingBag);
            DatabaseManager.saveRoutingRecord(processingBag.getBagId(), startLoc, endLoc, -1, "NO_ROUTE", simulatedTime);
            DatabaseManager.logAudit(auth.getCurrentUser().getUsername(), "PROCESS_BAG", processingBag.getBagId(), simulatedTime, "FAILED", "NO_ROUTE");
            return;
        }

        System.out.println("\nRoute to Loading Bay: " + String.join(" -> ", result.route));
        System.out.println("Estimated Travel Time: " + result.totalTime + " mins");
        
        int estimatedArrival = simulatedTime + result.totalTime;
        if (estimatedArrival > processingBag.getLoadingCutoff()) {
            System.out.println("\nSTATUS: MISSED_CUTOFF. Arrival: " + (estimatedArrival/60) + ":" + String.format("%02d", estimatedArrival%60) + " > Cutoff: " + processingBag.getFormattedCutoff());
            processingBag.setStatus(BaggageStatus.MISSED_CUTOFF);
            DatabaseManager.saveBaggage(processingBag);
            DatabaseManager.saveRoutingRecord(processingBag.getBagId(), startLoc, endLoc, result.totalTime, "MISSED", simulatedTime);
            DatabaseManager.logAudit(auth.getCurrentUser().getUsername(), "PROCESS_BAG", processingBag.getBagId(), simulatedTime, "FAILED", "MISSED_CUTOFF");
            return;
        }
        
        processingBag.setStatus(BaggageStatus.ROUTING);

        for (int i = 1; i < result.route.size(); i++) {
            String nextLoc = result.route.get(i);
            System.out.println(nextLoc + "  [ARRIVED]");
            processingBag.setCurrentLocation(nextLoc);
        }
        
        simulatedTime += result.totalTime;

        processingBag.setStatus(BaggageStatus.LOADED);
        System.out.println("\nSTATUS: SUCCESSFULLY LOADED");
        DatabaseManager.saveBaggage(processingBag);
        DatabaseManager.saveRoutingRecord(processingBag.getBagId(), startLoc, endLoc, result.totalTime, "LOADED", simulatedTime);
        DatabaseManager.logAudit(auth.getCurrentUser().getUsername(), "PROCESS_BAG", processingBag.getBagId(), simulatedTime, "SUCCESS", null);
    }

    public void trackBaggage(String bagId) {
        auth.requirePermission(Role.ADMIN, Role.SUPERVISOR, Role.BAGGAGE_OPERATOR, Role.AUDITOR);
        Baggage bag = allBaggage.get(bagId);
        if (bag == null) {
            System.out.println("Error: Baggage ID " + bagId + " not found.");
            return;
        }

        System.out.println("\n==================================================");
        System.out.println("                  BAG TRACKING");
        System.out.println("==================================================");
        System.out.printf(" Bag ID       : %s\n", bag.getBagId());
        if (bag.getAssignedFlight() != null) {
            System.out.printf(" Flight       : %s\n", bag.getAssignedFlight().getFlightId());
            System.out.printf(" Destination  : %s\n", bag.getAssignedFlight().getDestination());
            System.out.printf(" Cutoff       : %s\n", bag.getFormattedCutoff());
            if (bag.getAssignedFlight().getAssignedBay() != null) {
                System.out.printf(" Loading Bay  : %s\n", bag.getAssignedFlight().getAssignedBay().getBayId());
            }
        } else {
            System.out.println(" Flight       : UNASSIGNED");
        }
        System.out.printf(" Type         : %s\n", bag.getBagType());
        System.out.printf(" Current Loc  : %s\n", bag.getCurrentLocation());
        System.out.printf(" Status       : %s\n", bag.getStatus());
        System.out.println("==================================================");
    }
}
