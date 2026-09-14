import java.util.Scanner;

public class Main {
    private static BaggageService baggageService = new BaggageService();
    private static Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        boolean exit = false;
        
        while (!exit) {
            System.out.println("\n==================================================");
            System.out.println("          VAANAM AIRPORT BAGGAGE SYSTEM");
            System.out.println("==================================================");
            System.out.println("   [ Simulated Time: " + baggageService.getFormattedSimulatedTime() + " ]");
            System.out.println("--------------------------------------------------");
            System.out.println("1. View Flights");
            System.out.println("2. View Baggage");
            System.out.println("3. Register Baggage");
            System.out.println("4. Assign Baggage to Flight");
            System.out.println("5. View Priority Queue");
            System.out.println("6. Process Next Baggage");
            System.out.println("7. Track Baggage");
            System.out.println("8. View Loading Bays");
            System.out.println("9. View Conveyor Network");
            System.out.println("10. Exit");
            System.out.print("\nEnter choice: ");
            
            if (!scanner.hasNextLine()) {
                System.out.println("No input available. Exiting.");
                break;
            }
            
            String choice = scanner.nextLine().trim();
            
            if (choice.equals("1")) {
                baggageService.displayFlights();
            } else if (choice.equals("2")) {
                baggageService.displayBaggage();
            } else if (choice.equals("3")) {
                registerBaggage();
            } else if (choice.equals("4")) {
                assignBaggage();
            } else if (choice.equals("5")) {
                baggageService.printPriorityQueue();
            } else if (choice.equals("6")) {
                baggageService.processNextBaggage();
            } else if (choice.equals("7")) {
                trackBaggage();
            } else if (choice.equals("8")) {
                baggageService.displayBays();
            } else if (choice.equals("9")) {
                baggageService.displayConveyorNetwork();
            } else if (choice.equals("10")) {
                exit = true;
                System.out.println("Exiting System. Goodbye!");
            } else {
                System.out.println("Invalid choice. Please enter a number between 1 and 10.");
            }
        }
    }

    private static void registerBaggage() {
        System.out.println("\n--- Register New Baggage ---");
        System.out.print("Enter Baggage ID (e.g., B101): ");
        if (!scanner.hasNextLine()) return;
        String bagId = scanner.nextLine().trim();
        
        System.out.print("Is this a Transfer bag? (yes/no): ");
        if (!scanner.hasNextLine()) return;
        String transferInput = scanner.nextLine().trim();
        boolean isTransfer = transferInput.equalsIgnoreCase("yes");
        
        System.out.print("Category Rank (1: First, 2: Business, 3: Economy): ");
        if (!scanner.hasNextLine()) return;
        String catInput = scanner.nextLine().trim();
        int catRank = 3; // default
        if (catInput.equals("1")) {
            catRank = 1;
        } else if (catInput.equals("2")) {
            catRank = 2;
        }
        
        baggageService.registerBaggage(bagId, isTransfer, catRank);
    }

    private static void assignBaggage() {
        System.out.println("\n--- Assign Baggage to Flight ---");
        System.out.print("Enter Baggage ID: ");
        if (!scanner.hasNextLine()) return;
        String bagId = scanner.nextLine().trim();
        
        System.out.print("Enter Flight ID (e.g., F101): ");
        if (!scanner.hasNextLine()) return;
        String flightId = scanner.nextLine().trim();
        
        baggageService.assignBaggageToFlight(bagId, flightId);
    }

    private static void trackBaggage() {
        System.out.println("\n--- Track Baggage ---");
        System.out.print("Enter Baggage ID to track: ");
        if (!scanner.hasNextLine()) return;
        String bagId = scanner.nextLine().trim();
        baggageService.trackBaggage(bagId);
    }
}
