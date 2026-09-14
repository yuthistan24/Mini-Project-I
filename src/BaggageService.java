// BaggageService.java

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.ArrayList;

public class BaggageService {
    private HashMap<String, Flight> flights;
    private HashMap<String, LoadingBay> bays;
    private HashMap<String, Baggage> allBaggage;
    
    private PriorityQueue<Baggage> processQueue;
    private ArrayDeque<Baggage> conveyorWaitingQueue; // FIFO for demonstration
    private ConveyorRoutePlanner conveyorPlanner;
    
    private int simulatedTime; // minutes from midnight

    public BaggageService() {
        this.simulatedTime = 540; // Start at 09:00 AM (9 * 60)
        
        flights = new HashMap<>();
        bays = new HashMap<>();
        allBaggage = new HashMap<>();
        conveyorWaitingQueue = new ArrayDeque<>();
        conveyorPlanner = new ConveyorRoutePlanner();
        
        // Custom Comparator without advanced chaining
        Comparator<Baggage> baggageComparator = new Comparator<Baggage>() {
            @Override
            public int compare(Baggage b1, Baggage b2) {
                // 1. Earliest Loading Cutoff
                if (b1.getLoadingCutoff() < b2.getLoadingCutoff()) {
                    return -1;
                }
                if (b1.getLoadingCutoff() > b2.getLoadingCutoff()) {
                    return 1;
                }
                
                // 2. Transfer Status (Transfer > Origin)
                boolean t1 = b1.isTransferBag();
                boolean t2 = b2.isTransferBag();
                if (t1 && !t2) {
                    return -1;
                }
                if (!t1 && t2) {
                    return 1;
                }
                
                // 3. Category Rank (1 is best)
                int rank1 = b1.getPassengerCategory().getRank();
                int rank2 = b2.getPassengerCategory().getRank();
                if (rank1 < rank2) {
                    return -1;
                }
                if (rank1 > rank2) {
                    return 1;
                }
                
                // 4. Arrival Order
                if (b1.getArrivalOrder() < b2.getArrivalOrder()) {
                    return -1;
                }
                if (b1.getArrivalOrder() > b2.getArrivalOrder()) {
                    return 1;
                }
                
                // Deterministic fallback
                return b1.getBagId().compareTo(b2.getBagId());
            }
        };
        
        processQueue = new PriorityQueue<>(baggageComparator);
        
        initializeSyntheticData();
    }
    
    private void initializeSyntheticData() {
        // Create 4 Loading Bays
        bays.put("BAY-A", new LoadingBay("BAY-A"));
        bays.put("BAY-B", new LoadingBay("BAY-B"));
        bays.put("BAY-C", new LoadingBay("BAY-C"));
        bays.put("BAY-D", new LoadingBay("BAY-D"));
        
        // Create 4 Flights with updated destinations
        flights.put("F101", new Flight("F101", "Chennai", 630)); // 10:30
        flights.put("F202", new Flight("F202", "Madurai", 615));  // 10:15
        flights.put("F303", new Flight("F303", "Coimbatore", 640)); // 10:40
        flights.put("F404", new Flight("F404", "Trichy", 620)); // 10:20
        
        // Assign bays to flights
        bays.get("BAY-A").setAssignedFlight(flights.get("F101"));
        bays.get("BAY-B").setAssignedFlight(flights.get("F202"));
        bays.get("BAY-C").setAssignedFlight(flights.get("F303"));
        bays.get("BAY-D").setAssignedFlight(flights.get("F404"));
        
        // Generate 32 Synthetic Baggage Records
        int arrivalCounter = 1;
        String[] flightIds = {"F101", "F202", "F303", "F404"};
        PassengerCategory[] categories = {PassengerCategory.FIRST_CLASS, PassengerCategory.BUSINESS, PassengerCategory.ECONOMY};
        
        for (int i = 1; i <= 32; i++) {
            String bagId = "B";
            if (i < 10) bagId += "00" + i;
            else bagId += "0" + i;
            
            Baggage bag;
            
            if (i % 3 == 0) {
                bag = new TransferBag(bagId, categories[i % 3], arrivalCounter++);
            } else {
                bag = new OriginBag(bagId, categories[i % 3], arrivalCounter++);
            }
            
            Flight assignedFlight = flights.get(flightIds[i % 4]);
            bag.assignFlight(assignedFlight);
            
            if (i % 5 != 0) { 
                bag.setStatus(BaggageStatus.WAITING_CLEARANCE);
                bag.setOperatorClearance(true);
                processQueue.add(bag);
            } else { 
                bag.setStatus(BaggageStatus.WAITING_CLEARANCE);
                bag.setOperatorClearance(false);
            }
            
            allBaggage.put(bagId, bag);
        }
    }
    
    public String getFormattedSimulatedTime() {
        int hours = simulatedTime / 60;
        int minutes = simulatedTime % 60;
        return String.format("%02d:%02d", hours, minutes);
    }
    
    public void displayFlights() {
        System.out.println("\n==================================================");
        System.out.println("               FLIGHT SCHEDULE");
        System.out.println("==================================================");
        for (Flight f : flights.values()) {
            System.out.println(" ✈ " + f.toString());
        }
        System.out.println("==================================================");
    }
    
    public void displayBaggage() {
        System.out.println("\n==================================================");
        System.out.println("               ALL BAGGAGE RECORDS");
        System.out.println("==================================================");
        for (Baggage b : allBaggage.values()) {
            System.out.println(" 🧳 " + b.toString());
        }
        System.out.println("==================================================");
    }
    
    public void displayBays() {
        System.out.println("\n==================================================");
        System.out.println("                 LOADING BAYS");
        System.out.println("==================================================");
        for (LoadingBay bay : bays.values()) {
            System.out.println(" 🚛 " + bay.toString());
        }
        System.out.println("==================================================");
    }

    public void displayConveyorNetwork() {
        conveyorPlanner.displayNetwork();
    }
    
    public void registerBaggage(String bagId, boolean isTransfer, int catRank) {
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
        System.out.println("Successfully registered: " + bagId);
    }
    
    public void assignBaggageToFlight(String bagId, String flightId) {
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
        
        bag.assignFlight(flight);
        bag.setStatus(BaggageStatus.WAITING_CLEARANCE);
        System.out.println("Assigned " + bagId + " to flight " + flightId + ".");
        System.out.println("Waiting for operator clearance.");
    }
    
    public void printPriorityQueue() {
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
        
        // Demonstrate ArrayDeque FIFO usage before processing
        conveyorWaitingQueue.add(nextBag);
        Baggage processingBag = conveyorWaitingQueue.poll(); // Taking it out immediately
        
        processingBag.setStatus(BaggageStatus.IN_TRANSIT);
        System.out.println("========== PROCESSING BAGGAGE ==========");
        System.out.println("Bag ID       : " + processingBag.getBagId());
        System.out.println("Flight       : " + processingBag.getAssignedFlight().getFlightId());
        System.out.println("Type         : " + processingBag.getBagType());
        System.out.println("Cutoff       : " + processingBag.getFormattedCutoff());
        System.out.println("Clearance    : APPROVED");
        System.out.println("Loading Bay  : " + processingBag.getAssignedFlight().getAssignedBay().getBayId());
        System.out.println("\nRoute to Loading Bay:");
        
        String startLoc = processingBag.getCurrentLocation();
        String endLoc = processingBag.getAssignedFlight().getAssignedBay().getBayId();
        
        ArrayList<String> route = conveyorPlanner.getShortestPath(startLoc, endLoc);
        
        for (String location : route) {
            System.out.println(location + "  [ARRIVED]");
            processingBag.setCurrentLocation(location);
            // Simulate processing time
            simulatedTime += 4; 
        }
        
        processingBag.setStatus(BaggageStatus.LOADED);
        System.out.println("\nSTATUS: SUCCESSFULLY LOADED");
    }
    
    public void trackBaggage(String bagId) {
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
