import java.util.ArrayList;
import java.util.List;

enum Role {
    ADMIN,
    BAGGAGE_MANAGER,
    PASSENGER;

    public static Role fromString(String str) {
        if (str == null) return PASSENGER;
        String s = str.trim().toUpperCase();
        if ("USER".equals(s)) return PASSENGER;
        if ("BAGGAGE_OPERATOR".equals(s) || "SUPERVISOR".equals(s) || "SECURITY_OFFICER".equals(s) || "AUDITOR".equals(s)) {
            return BAGGAGE_MANAGER;
        }
        try {
            return Role.valueOf(s);
        } catch (IllegalArgumentException e) {
            return PASSENGER;
        }
    }
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

    public static PassengerCategory fromRank(int rank) {
        if (rank == 1) return FIRST_CLASS;
        if (rank == 2) return BUSINESS;
        return ECONOMY;
    }
}

enum BaggageDirection {
    OUTGOING,
    INCOMING,
    TRANSFER
}

enum BaggageStatus {
    REGISTERED,
    AWAITING_CLEARANCE,
    CLEARED,
    QUEUED,
    IN_TRANSIT,
    AT_LOADING_BAY,
    LOADED,
    MISSED_CUTOFF,
    ARRIVAL_PENDING,
    UNLOADED,
    AT_COLLECTION_POINT,
    COLLECTED,
    EXCEPTION,
    REJECTED;

    public static BaggageStatus fromString(String str) {
        if (str == null) return REGISTERED;
        String s = str.trim().toUpperCase();
        if ("WAITING_FOR_CLEARANCE".equals(s)) return AWAITING_CLEARANCE;
        if ("WAITING_FOR_ASSIGNMENT".equals(s)) return REGISTERED;
        if ("ROUTING".equals(s) || "REROUTED".equals(s)) return IN_TRANSIT;
        if ("CLAIMED".equals(s)) return COLLECTED;
        if ("NO_ROUTE_AVAILABLE".equals(s)) return EXCEPTION;
        try {
            return BaggageStatus.valueOf(s);
        } catch (IllegalArgumentException e) {
            return EXCEPTION;
        }
    }
}

class User {
    private String username;
    private String passwordHash;
    private String passwordSalt;
    private Role role;
    private boolean clearanceAuthority; // Certified security screening authority
    private boolean active;
    private String linkedPassengerId; // For PASSENGER role

    public User(String username, String passwordHash, String passwordSalt, Role role, boolean clearanceAuthority, boolean active, String linkedPassengerId) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.passwordSalt = passwordSalt;
        this.role = role;
        this.clearanceAuthority = clearanceAuthority;
        this.active = active;
        this.linkedPassengerId = linkedPassengerId;
    }

    public User(String username, String passwordHash, String passwordSalt, Role role, boolean active) {
        this(username, passwordHash, passwordSalt, role, false, active, null);
    }

    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getPasswordSalt() { return passwordSalt; }
    public Role getRole() { return role; }
    public boolean hasClearanceAuthority() { return clearanceAuthority; }
    public boolean isActive() { return active; }
    public String getLinkedPassengerId() { return linkedPassengerId; }

    public void setRole(Role role) { this.role = role; }
    public void setClearanceAuthority(boolean auth) { this.clearanceAuthority = auth; }
    public void setActive(boolean active) { this.active = active; }
    public void setLinkedPassengerId(String id) { this.linkedPassengerId = id; }
    public void setPassword(String hash, String salt) {
        this.passwordHash = hash;
        this.passwordSalt = salt;
    }
}

class Passenger {
    private String passengerId;
    private String fullName;
    private String username; // Optional linked user login
    private String flightId;
    private PassengerCategory category;
    private String contactInfo;

    public Passenger(String passengerId, String fullName, String username, String flightId, PassengerCategory category, String contactInfo) {
        this.passengerId = passengerId;
        this.fullName = fullName;
        this.username = username;
        this.flightId = flightId;
        this.category = category != null ? category : PassengerCategory.ECONOMY;
        this.contactInfo = contactInfo != null ? contactInfo : "";
    }

    public String getPassengerId() { return passengerId; }
    public String getFullName() { return fullName; }
    public String getUsername() { return username; }
    public String getFlightId() { return flightId; }
    public PassengerCategory getCategory() { return category; }
    public String getContactInfo() { return contactInfo; }

    public void setFullName(String fullName) { this.fullName = fullName; }
    public void setUsername(String username) { this.username = username; }
    public void setFlightId(String flightId) { this.flightId = flightId; }
    public void setCategory(PassengerCategory category) { this.category = category; }
    public void setContactInfo(String contactInfo) { this.contactInfo = contactInfo; }
}

abstract class Baggage {
    private String bagId;
    private String passengerId;
    private String passengerName;
    private BaggageDirection direction;
    private Flight assignedFlight;
    private String originAirport;
    private String destinationAirport;
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
    private String createdAt;
    private String updatedAt;

    public Baggage(String bagId, String passengerId, String passengerName, BaggageDirection direction, PassengerCategory passengerCategory, int arrivalOrder) {
        this.bagId = bagId;
        this.passengerId = passengerId != null ? passengerId : "UNLINKED";
        this.passengerName = passengerName != null ? passengerName : "Unknown Passenger";
        this.direction = direction;
        this.passengerCategory = passengerCategory != null ? passengerCategory : PassengerCategory.ECONOMY;
        this.arrivalOrder = arrivalOrder;
        this.operatorClearance = false;
        this.clearanceReason = "";
        this.estimatedTime = 0;
        this.currentRoute = new ArrayList<>();
        this.originAirport = "MAA";
        this.destinationAirport = "DEL";
        this.createdAt = "";
        this.updatedAt = "";

        if (direction == BaggageDirection.INCOMING) {
            this.currentLocation = "UNLOAD-POINT";
            this.destinationNode = "CLAIM-CAROUSEL-1";
            this.operatorClearance = true;
            this.status = BaggageStatus.QUEUED;
        } else if (direction == BaggageDirection.TRANSFER) {
            this.currentLocation = "TRANSFER-A";
            this.status = BaggageStatus.AWAITING_CLEARANCE;
        } else {
            this.currentLocation = "CHECK-IN";
            this.status = BaggageStatus.REGISTERED;
        }
    }

    public String getBagId() { return bagId; }
    public String getPassengerId() { return passengerId; }
    public String getPassengerName() { return passengerName; }
    public BaggageDirection getDirection() { return direction; }
    public Flight getAssignedFlight() { return assignedFlight; }
    public String getOriginAirport() { return originAirport; }
    public String getDestinationAirport() { return destinationAirport; }
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
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }

    public void setPassengerId(String passengerId) { this.passengerId = passengerId; }
    public void setPassengerName(String passengerName) { this.passengerName = passengerName; }
    public void setOriginAirport(String origin) { this.originAirport = origin; }
    public void setDestinationAirport(String dest) { this.destinationAirport = dest; }
    public void setCurrentLocation(String loc) { this.currentLocation = loc; }
    public void setDestinationNode(String dest) { this.destinationNode = dest; }
    public void setStatus(BaggageStatus status) { this.status = status; }
    public void setEstimatedTime(int time) { this.estimatedTime = time; }
    public void setCurrentRoute(List<String> route) { this.currentRoute = route != null ? route : new ArrayList<>(); }
    public void setCreatedAt(String time) { this.createdAt = time; }
    public void setUpdatedAt(String time) { this.updatedAt = time; }
    public void setLoadingCutoff(int cutoff) { this.loadingCutoff = cutoff; }

    public void setClearance(boolean cleared, String reason) {
        this.operatorClearance = cleared;
        this.clearanceReason = reason != null ? reason : "";
        if (cleared) {
            if (this.status == BaggageStatus.AWAITING_CLEARANCE || this.status == BaggageStatus.REGISTERED || this.status == BaggageStatus.REJECTED) {
                this.status = BaggageStatus.QUEUED;
            }
        } else {
            this.status = BaggageStatus.REJECTED;
        }
    }

    public void assignFlight(Flight flight) {
        this.assignedFlight = flight;
        if (flight != null) {
            this.loadingCutoff = flight.getLoadingCutoff();
            this.destinationAirport = flight.getDestination();
            this.originAirport = flight.getOrigin();
            if (flight.getAssignedBay() != null) {
                this.destinationNode = flight.getAssignedBay().getBayId();
            }
            if (direction != BaggageDirection.INCOMING) {
                if (this.status == BaggageStatus.REGISTERED || !this.operatorClearance) {
                    this.status = BaggageStatus.AWAITING_CLEARANCE;
                }
            }
        }
    }

    public List<String> getCompletedRoute() {
        List<String> list = new ArrayList<>();
        if (currentRoute == null || currentRoute.isEmpty()) return list;
        int idx = currentRoute.indexOf(currentLocation);
        if (idx >= 0) {
            for (int i = 0; i <= idx; i++) {
                list.add(currentRoute.get(i));
            }
        }
        return list;
    }

    public List<String> getRemainingRoute() {
        List<String> list = new ArrayList<>();
        if (currentRoute == null || currentRoute.isEmpty()) return list;
        int idx = currentRoute.indexOf(currentLocation);
        if (idx >= 0) {
            for (int i = idx; i < currentRoute.size(); i++) {
                list.add(currentRoute.get(i));
            }
        } else {
            list.addAll(currentRoute);
        }
        return list;
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
    public OriginBag(String bagId, String passengerId, String passengerName, PassengerCategory passengerCategory, int arrivalOrder) {
        super(bagId, passengerId, passengerName, BaggageDirection.OUTGOING, passengerCategory, arrivalOrder);
    }

    public OriginBag(String bagId, PassengerCategory passengerCategory, int arrivalOrder) {
        this(bagId, "P-DEFAULT", "Passenger", passengerCategory, arrivalOrder);
    }
}

class TransferBag extends Baggage {
    public TransferBag(String bagId, String passengerId, String passengerName, PassengerCategory passengerCategory, int arrivalOrder) {
        super(bagId, passengerId, passengerName, BaggageDirection.TRANSFER, passengerCategory, arrivalOrder);
    }

    public TransferBag(String bagId, PassengerCategory passengerCategory, int arrivalOrder) {
        this(bagId, "P-DEFAULT", "Transfer Passenger", passengerCategory, arrivalOrder);
    }
}

class IncomingBag extends Baggage {
    public IncomingBag(String bagId, String passengerId, String passengerName, PassengerCategory passengerCategory, int arrivalOrder) {
        super(bagId, passengerId, passengerName, BaggageDirection.INCOMING, passengerCategory, arrivalOrder);
    }

    public IncomingBag(String bagId, PassengerCategory passengerCategory, int arrivalOrder) {
        this(bagId, "P-DEFAULT", "Arriving Passenger", passengerCategory, arrivalOrder);
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
    private String origin;
    private String destination;
    private int departureTime; // minutes from midnight
    private int loadingCutoff; // minutes from midnight
    private LoadingBay assignedBay;

    public Flight(String flightId, String origin, String destination, int departureTime, int loadingCutoff) {
        this.flightId = flightId;
        this.origin = origin != null && !origin.isEmpty() ? origin : "MAA";
        this.destination = destination;
        this.departureTime = departureTime > 0 ? departureTime : loadingCutoff + 30;
        this.loadingCutoff = loadingCutoff;
    }

    public Flight(String flightId, String destination, int loadingCutoff) {
        this(flightId, "MAA", destination, loadingCutoff + 30, loadingCutoff);
    }

    public String getFlightId() { return flightId; }
    public String getOrigin() { return origin; }
    public String getDestination() { return destination; }
    public int getDepartureTime() { return departureTime; }
    public int getLoadingCutoff() { return loadingCutoff; }
    public LoadingBay getAssignedBay() { return assignedBay; }

    public void setOrigin(String origin) { this.origin = origin; }
    public void setDestination(String destination) { this.destination = destination; }
    public void setDepartureTime(int departureTime) { this.departureTime = departureTime; }
    public void setLoadingCutoff(int cutoff) { this.loadingCutoff = cutoff; }
    public void setAssignedBay(LoadingBay assignedBay) { this.assignedBay = assignedBay; }

    public String getFormattedCutoff() {
        return String.format("%02d:%02d", loadingCutoff / 60, loadingCutoff % 60);
    }

    public String getFormattedDeparture() {
        return String.format("%02d:%02d", departureTime / 60, departureTime % 60);
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
