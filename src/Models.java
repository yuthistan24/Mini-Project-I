import java.util.ArrayList;
import java.util.List;

enum Role {
    ADMIN,
    SUPERVISOR,
    BAGGAGE_OPERATOR,
    SECURITY_OFFICER,
    AUDITOR
}

enum PassengerCategory {
    FIRST_CLASS(1, "First Class"),
    BUSINESS(2, "Business Class"),
    ECONOMY(3, "Economy Class");

    private final int rank;
    private final String displayName;

    PassengerCategory(int rank, String displayName) {
        this.rank = rank;
        this.displayName = displayName;
    }

    public int getRank() { return rank; }
    public String getDisplayName() { return displayName; }
}

enum BaggageDirection {
    OUTGOING,
    INCOMING,
    TRANSFER
}

enum BaggageStatus {
    REGISTERED,
    WAITING_FOR_ASSIGNMENT,
    WAITING_FOR_CLEARANCE,
    CLEARED,
    REJECTED,
    QUEUED,
    ROUTING,
    REROUTED,
    LOADED,
    CLAIMED,
    MISSED_CUTOFF,
    NO_ROUTE_AVAILABLE
}

class User {
    private String username;
    private String passwordHash;
    private String passwordSalt;
    private Role role;
    private boolean active;

    public User(String username, String passwordHash, String passwordSalt, Role role, boolean active) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.passwordSalt = passwordSalt;
        this.role = role;
        this.active = active;
    }

    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getPasswordSalt() { return passwordSalt; }
    public Role getRole() { return role; }
    public boolean isActive() { return active; }

    public void setRole(Role role) { this.role = role; }
    public void setActive(boolean active) { this.active = active; }
    public void setPassword(String hash, String salt) {
        this.passwordHash = hash;
        this.passwordSalt = salt;
    }
}

abstract class Baggage {
    private String bagId;
    private BaggageDirection direction;
    private Flight assignedFlight;
    private String currentLocation;
    private String destinationNode;
    private int loadingCutoff; // Minutes from midnight
    private PassengerCategory passengerCategory;
    private int arrivalOrder;
    private boolean operatorClearance;
    private String clearanceReason;
    private BaggageStatus status;
    private int estimatedTime;
    private List<String> currentRoute;

    public Baggage(String bagId, BaggageDirection direction, PassengerCategory passengerCategory, int arrivalOrder) {
        this.bagId = bagId;
        this.direction = direction;
        this.passengerCategory = passengerCategory;
        this.arrivalOrder = arrivalOrder;
        this.status = BaggageStatus.REGISTERED;
        this.operatorClearance = false;
        this.clearanceReason = "";
        this.estimatedTime = 0;
        this.currentRoute = new ArrayList<>();

        if (direction == BaggageDirection.INCOMING) {
            this.currentLocation = "UNLOAD-POINT";
            this.destinationNode = "CLAIM-CAROUSEL-1";
            // Incoming bags do not require security clearance before carousel delivery
            this.operatorClearance = true;
            this.status = BaggageStatus.QUEUED;
        } else if (direction == BaggageDirection.TRANSFER) {
            this.currentLocation = "TRANSFER-A";
            this.status = BaggageStatus.WAITING_FOR_ASSIGNMENT;
        } else {
            this.currentLocation = "CHECK-IN";
            this.status = BaggageStatus.REGISTERED;
        }
    }

    public String getBagId() { return bagId; }
    public BaggageDirection getDirection() { return direction; }
    public Flight getAssignedFlight() { return assignedFlight; }
    public String getCurrentLocation() { return currentLocation; }
    public String getDestinationNode() { return destinationNode; }
    public int getLoadingCutoff() { return loadingCutoff; }
    public PassengerCategory getPassengerCategory() { return passengerCategory; }
    public int getArrivalOrder() { return arrivalOrder; }
    public boolean isOperatorCleared() { return operatorClearance; }
    public String getClearanceReason() { return clearanceReason; }
    public BaggageStatus getStatus() { return status; }
    public int getEstimatedTime() { return estimatedTime; }
    public List<String> getCurrentRoute() { return currentRoute; }

    public void setCurrentLocation(String loc) { this.currentLocation = loc; }
    public void setDestinationNode(String dest) { this.destinationNode = dest; }
    public void setStatus(BaggageStatus status) { this.status = status; }
    public void setEstimatedTime(int time) { this.estimatedTime = time; }
    public void setCurrentRoute(List<String> route) { this.currentRoute = route != null ? route : new ArrayList<>(); }

    public void setClearance(boolean cleared, String reason) {
        this.operatorClearance = cleared;
        this.clearanceReason = reason != null ? reason : "";
        if (cleared) {
            this.status = BaggageStatus.QUEUED;
        } else {
            this.status = BaggageStatus.REJECTED;
        }
    }

    public void assignFlight(Flight flight) {
        this.assignedFlight = flight;
        if (flight != null) {
            this.loadingCutoff = flight.getLoadingCutoff();
            if (flight.getAssignedBay() != null) {
                this.destinationNode = flight.getAssignedBay().getBayId();
            }
            if (direction != BaggageDirection.INCOMING) {
                if (this.status == BaggageStatus.REGISTERED || this.status == BaggageStatus.WAITING_FOR_ASSIGNMENT) {
                    this.status = BaggageStatus.WAITING_FOR_CLEARANCE;
                }
            }
        }
    }

    public boolean isTransferBag() { return direction == BaggageDirection.TRANSFER; }
    public boolean isIncomingBag() { return direction == BaggageDirection.INCOMING; }
    public boolean isOutgoingBag() { return direction == BaggageDirection.OUTGOING; }

    public String getBagType() {
        switch (direction) {
            case TRANSFER: return "Transfer";
            case INCOMING: return "Incoming";
            default: return "Outgoing";
        }
    }

    public String getFormattedCutoff() {
        if (loadingCutoff <= 0) return "N/A";
        int hours = loadingCutoff / 60;
        int minutes = loadingCutoff % 60;
        return String.format("%02d:%02d", hours, minutes);
    }
}

class OriginBag extends Baggage {
    public OriginBag(String bagId, PassengerCategory passengerCategory, int arrivalOrder) {
        super(bagId, BaggageDirection.OUTGOING, passengerCategory, arrivalOrder);
    }
}

class TransferBag extends Baggage {
    public TransferBag(String bagId, PassengerCategory passengerCategory, int arrivalOrder) {
        super(bagId, BaggageDirection.TRANSFER, passengerCategory, arrivalOrder);
    }
}

class IncomingBag extends Baggage {
    public IncomingBag(String bagId, PassengerCategory passengerCategory, int arrivalOrder) {
        super(bagId, BaggageDirection.INCOMING, passengerCategory, arrivalOrder);
    }
}

class LoadingBay {
    private String bayId;
    private Flight assignedFlight;

    public LoadingBay(String bayId) {
        this.bayId = bayId;
    }

    public String getBayId() { return bayId; }
    public Flight getAssignedFlight() { return assignedFlight; }

    public void setAssignedFlight(Flight assignedFlight) {
        this.assignedFlight = assignedFlight;
        if (assignedFlight != null && assignedFlight.getAssignedBay() != this) {
            assignedFlight.setAssignedBay(this);
        }
    }
}

class Flight {
    private String flightId;
    private String destination;
    private int loadingCutoff; // minutes from midnight
    private LoadingBay assignedBay;

    public Flight(String flightId, String destination, int loadingCutoff) {
        this.flightId = flightId;
        this.destination = destination;
        this.loadingCutoff = loadingCutoff;
    }

    public String getFlightId() { return flightId; }
    public String getDestination() { return destination; }
    public int getLoadingCutoff() { return loadingCutoff; }
    public LoadingBay getAssignedBay() { return assignedBay; }

    public void setLoadingCutoff(int cutoff) { this.loadingCutoff = cutoff; }
    public void setAssignedBay(LoadingBay assignedBay) { this.assignedBay = assignedBay; }

    public String getFormattedCutoff() {
        return String.format("%02d:%02d", loadingCutoff / 60, loadingCutoff % 60);
    }
}

class ConveyorSegment {
    private String fromNode;
    private String toNode;
    private int processingTime;
    private boolean available;

    public ConveyorSegment(String fromNode, String toNode, int processingTime) {
        this.fromNode = fromNode;
        this.toNode = toNode;
        this.processingTime = processingTime;
        this.available = true;
    }

    public String getFromNode() { return fromNode; }
    public String getToNode() { return toNode; }
    public int getProcessingTime() { return processingTime; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
}

class DijkstraNode implements Comparable<DijkstraNode> {
    String name;
    int dist;

    public DijkstraNode(String name, int dist) {
        this.name = name;
        this.dist = dist;
    }

    @Override
    public int compareTo(DijkstraNode other) {
        return Integer.compare(this.dist, other.dist);
    }
}

class RoutingResult {
    public ArrayList<String> route;
    public int totalTime;

    public RoutingResult(ArrayList<String> route, int totalTime) {
        this.route = route;
        this.totalTime = totalTime;
    }
}
