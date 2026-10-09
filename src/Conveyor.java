// Conveyor.java
// Contains ConveyorSegment and ConveyorRoutePlanner

import java.util.ArrayList;
import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.Collections;

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
        return fromNode + " -> " + toNode + " (" + processingTime + " min)" + (available ? "" : " [UNAVAILABLE]");
    }
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

class ConveyorRoutePlanner {
    private HashMap<String, ArrayList<ConveyorSegment>> adjacencyList;

    public ConveyorRoutePlanner() {
        adjacencyList = new HashMap<>();
    }
    
    public void addSegmentFromDB(String from, String to, int time, boolean available) {
        adjacencyList.putIfAbsent(from, new ArrayList<>());
        adjacencyList.putIfAbsent(to, new ArrayList<>());
        ConveyorSegment segment = new ConveyorSegment(from, to, time);
        segment.setAvailable(available);
        adjacencyList.get(from).add(segment);
    }
    
    public void initializeNetwork() {
        String[] nodes = {
            "CHECK-IN", "SORTING", "TRANSFER-A", "TRANSFER-B", 
            "CENTRAL", "BAY-AREA", "BAY-A", "BAY-B", "BAY-C", "BAY-D"
        };

        for (String node : nodes) {
            adjacencyList.putIfAbsent(node, new ArrayList<>());
        }

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
        adjacencyList.get(from).add(segment);
        DatabaseManager.saveConveyorSegment(segment);
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

    // Dijkstra's Algorithm
    public RoutingResult getShortestPath(String startNode, String endNode) {
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
                if (!edge.isAvailable()) continue; // Skip unavailable segments
                
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
            return null; // No route found
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
