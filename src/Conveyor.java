// Conveyor.java
// Contains ConveyorSegment and ConveyorRoutePlanner

import java.util.ArrayList;
import java.util.HashMap;

class ConveyorSegment {
    private String fromNode;
    private String toNode;
    private int processingTime;
    private boolean available;

    public ConveyorSegment(String fromNode, String toNode, int processingTime) {
        this.fromNode = fromNode;
        this.toNode = toNode;
        this.processingTime = processingTime;
        this.available = true; // Available by default
    }

    public String getFromNode() {
        return fromNode;
    }

    public String getToNode() {
        return toNode;
    }

    public int getProcessingTime() {
        return processingTime;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    @Override
    public String toString() {
        return fromNode + " -> " + toNode + " (" + processingTime + " min)";
    }
}

class ConveyorRoutePlanner {
    private HashMap<String, ArrayList<ConveyorSegment>> adjacencyList;

    public ConveyorRoutePlanner() {
        adjacencyList = new HashMap<>();
        initializeNetwork();
    }

    private void initializeNetwork() {
        // Create 8-10 nodes
        String[] nodes = {
            "CHECK-IN", "SORTING", "TRANSFER-A", "TRANSFER-B", 
            "CENTRAL", "BAY-AREA", "BAY-A", "BAY-B", "BAY-C", "BAY-D"
        };

        for (String node : nodes) {
            adjacencyList.put(node, new ArrayList<>());
        }

        // Add directed edges
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
    }

    private void addSegment(String from, String to, int time) {
        ConveyorSegment segment = new ConveyorSegment(from, to, time);
        if (adjacencyList.containsKey(from)) {
            adjacencyList.get(from).add(segment);
        }
    }

    public void displayNetwork() {
        System.out.println("\n--- CONVEYOR NETWORK ---");
        for (String node : adjacencyList.keySet()) {
            ArrayList<ConveyorSegment> edges = adjacencyList.get(node);
            for (ConveyorSegment edge : edges) {
                System.out.println(edge.toString());
            }
        }
    }

    // Method for Review 2 - Dijkstra will be implemented here later
    public ArrayList<String> getShortestPath(String startNode, String endNode) {
        System.out.println("Notice: Shortest-path routing will be implemented in Review 2.");
        System.out.println("Returning a simple direct representation for now.");
        
        // Faking a simple path display for Review 1, not Dijkstra
        ArrayList<String> simpleRoute = new ArrayList<>();
        simpleRoute.add(startNode);
        if (!startNode.equals("BAY-AREA") && !startNode.equals(endNode)) {
            simpleRoute.add("CENTRAL");
            simpleRoute.add("BAY-AREA");
        }
        simpleRoute.add(endNode);
        return simpleRoute;
    }
}
