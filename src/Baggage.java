// Baggage.java
// Contains Baggage base class, subclasses, and related enums.

enum PassengerCategory {
    FIRST_CLASS(1),
    BUSINESS(2),
    ECONOMY(3);

    private final int rank;

    PassengerCategory(int rank) {
        this.rank = rank;
    }

    public int getRank() {
        return rank;
    }
}

enum BaggageStatus {
    REGISTERED,
    ASSIGNED,
    WAITING_CLEARANCE,
    CLEARED,
    IN_TRANSIT,
    LOADED
}

public abstract class Baggage {
    private String bagId;
    private Flight assignedFlight;
    private String currentLocation;
    private int loadingCutoff; // minutes from midnight
    private PassengerCategory passengerCategory;
    private int arrivalOrder;
    private boolean operatorClearance;
    private BaggageStatus status;

    public Baggage(String bagId, PassengerCategory passengerCategory, int arrivalOrder) {
        this.bagId = bagId;
        this.passengerCategory = passengerCategory;
        this.arrivalOrder = arrivalOrder;
        this.status = BaggageStatus.REGISTERED;
        this.operatorClearance = false;
        this.currentLocation = "CHECK-IN"; // Default start location
    }

    public String getBagId() {
        return bagId;
    }

    public Flight getAssignedFlight() {
        return assignedFlight;
    }

    public String getCurrentLocation() {
        return currentLocation;
    }

    public int getLoadingCutoff() {
        return loadingCutoff;
    }

    public PassengerCategory getPassengerCategory() {
        return passengerCategory;
    }

    public int getArrivalOrder() {
        return arrivalOrder;
    }

    public boolean isOperatorCleared() {
        return operatorClearance;
    }

    public BaggageStatus getStatus() {
        return status;
    }

    public void setOperatorClearance(boolean operatorClearance) {
        this.operatorClearance = operatorClearance;
        if (operatorClearance && this.status == BaggageStatus.WAITING_CLEARANCE) {
            this.status = BaggageStatus.CLEARED;
        }
    }

    public void setStatus(BaggageStatus status) {
        this.status = status;
    }

    public void setCurrentLocation(String currentLocation) {
        this.currentLocation = currentLocation;
    }

    public void assignFlight(Flight flight) {
        this.assignedFlight = flight;
        this.loadingCutoff = flight.getLoadingCutoff();
        if (this.status == BaggageStatus.REGISTERED) {
            this.status = BaggageStatus.ASSIGNED;
        }
    }

    // Abstract method to demonstrate polymorphism
    public abstract boolean isTransferBag();

    public abstract String getBagType();

    public String getFormattedCutoff() {
        int hours = loadingCutoff / 60;
        int minutes = loadingCutoff % 60;
        return String.format("%02d:%02d", hours, minutes);
    }

    @Override
    public String toString() {
        String flightInfo = "UNASSIGNED";
        if (assignedFlight != null) {
            flightInfo = assignedFlight.getFlightId();
        }
        
        String cutoffInfo = "N/A";
        if (assignedFlight != null) {
            cutoffInfo = getFormattedCutoff();
        }

        return bagId + " | Flight: " + flightInfo + 
               " | Cutoff: " + cutoffInfo + 
               " | Type: " + getBagType() + 
               " | Status: " + status + 
               " | Cleared: " + operatorClearance;
    }
}

class OriginBag extends Baggage {
    public OriginBag(String bagId, PassengerCategory passengerCategory, int arrivalOrder) {
        super(bagId, passengerCategory, arrivalOrder);
    }

    @Override
    public boolean isTransferBag() {
        return false;
    }

    @Override
    public String getBagType() {
        return "Origin";
    }
}

class TransferBag extends Baggage {
    public TransferBag(String bagId, PassengerCategory passengerCategory, int arrivalOrder) {
        super(bagId, passengerCategory, arrivalOrder);
        // Transfer bags start at a transfer point
        setCurrentLocation("TRANSFER-A"); 
    }

    @Override
    public boolean isTransferBag() {
        return true;
    }

    @Override
    public String getBagType() {
        return "Transfer";
    }
}
