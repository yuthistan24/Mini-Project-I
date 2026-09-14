// Flight.java
// Contains Flight and LoadingBay classes for simplicity.

class LoadingBay {
    private String bayId;
    private Flight assignedFlight;

    public LoadingBay(String bayId) {
        this.bayId = bayId;
    }

    public String getBayId() {
        return bayId;
    }

    public Flight getAssignedFlight() {
        return assignedFlight;
    }

    public void setAssignedFlight(Flight assignedFlight) {
        this.assignedFlight = assignedFlight;
        if (assignedFlight != null && assignedFlight.getAssignedBay() != this) {
            assignedFlight.setAssignedBay(this);
        }
    }

    @Override
    public String toString() {
        if (assignedFlight != null) {
            return bayId + " (" + assignedFlight.getFlightId() + ")";
        } else {
            return bayId + " (Empty)";
        }
    }
}

public class Flight {
    private String flightId;
    private String destination;
    private int loadingCutoff; // minutes from midnight, e.g., 540 = 09:00
    private LoadingBay assignedBay;

    public Flight(String flightId, String destination, int loadingCutoff) {
        this.flightId = flightId;
        this.destination = destination;
        this.loadingCutoff = loadingCutoff;
    }

    public String getFlightId() {
        return flightId;
    }

    public String getDestination() {
        return destination;
    }

    public int getLoadingCutoff() {
        return loadingCutoff;
    }

    public LoadingBay getAssignedBay() {
        return assignedBay;
    }

    public void setAssignedBay(LoadingBay assignedBay) {
        this.assignedBay = assignedBay;
    }

    // Helper method to display time in HH:MM format
    public String getFormattedCutoff() {
        int hours = loadingCutoff / 60;
        int minutes = loadingCutoff % 60;
        return String.format("%02d:%02d", hours, minutes);
    }

    @Override
    public String toString() {
        return flightId + " (To: " + destination + ", Cutoff: " + getFormattedCutoff() + ")";
    }
}
