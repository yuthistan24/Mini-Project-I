# Intelligent Airport Baggage Handling and Routing System

## 1. Project Title
Intelligent Airport Baggage Handling and Routing System - Vaanam Airport

## 2. Problem Statement
Baggage staff at Vaanam Airport currently use manual lists to route bags. This causes bags with urgent loading deadlines to get stuck behind less urgent bags. Transfer bags often miss their flights. If a conveyor belt breaks, it is hard to manually recalculate the route.

## 3. Objective
To build a Java-based simulation that tracks baggage, prioritizes them based on their loading deadlines using a Priority Queue, and ultimately (in future reviews) routes them through a conveyor network using Dijkstra's shortest path algorithm.

## 4. Review 1 Scope
For the first review (30% implementation), we have built the foundation. The system can:
- Store and manage Flights, Baggage, and Loading Bays.
- Register and assign baggage to flights.
- Ensure only operator-cleared baggage is processed.
- Prioritize baggage processing correctly based on their loading cutoff time (earliest first).
- Represent the conveyor network as a directed weighted graph.

## 5. Current Features
- **Console Menu**: Simple 10-option text menu.
- **Baggage Priority**: Uses a `PriorityQueue` to sort bags based on Cutoff Time -> Transfer Status -> Category Rank -> Arrival Order.
- **Conveyor Graph**: Built using an Adjacency List (`HashMap<String, ArrayList<ConveyorSegment>>`).
- **Simulated Clock**: Basic integer-based simulated time in minutes (e.g. 540 = 09:00).
- **FIFO Queue**: Uses an `ArrayDeque` to demonstrate a basic waiting queue at the conveyor entry.

## 6. Classes and Their Responsibilities
1. **`Main.java`**: Contains the Scanner, while loop for the menu, and handles user input.
2. **`BaggageService.java`**: The core controller. Manages the HashMaps for fast lookup, the PriorityQueue for sorting, and coordinates operations.
3. **`Baggage.java`**: Contains the abstract `Baggage` class and the concrete `OriginBag` and `TransferBag` classes, plus basic Enums.
4. **`Flight.java`**: Contains `Flight` and `LoadingBay`. Represents the destinations and deadlines.
5. **`Conveyor.java`**: Contains `ConveyorSegment` (edges) and `ConveyorRoutePlanner` (the graph manager).

## 7. OOP Concepts Demonstrated
- **Classes & Objects**: We modeled real-world entities like `Flight` and `Baggage` as classes.
- **Encapsulation**: All fields are `private` and accessed via `public` getters/setters.
- **Inheritance**: `OriginBag` and `TransferBag` `extends` the base `Baggage` class.
- **Polymorphism**: The `isTransferBag()` and `getBagType()` methods are overridden in the subclasses to provide specific behavior.

## 8. Data Structures Used
- **`HashMap`**: Used for O(1) fast lookups by ID (e.g., `HashMap<String, Baggage>`).
- **`PriorityQueue`**: Used to automatically sort the cleared baggage by their strict loading deadlines.
- **`ArrayDeque`**: Used as a simple FIFO queue for bags waiting to enter the actual conveyor.
- **`ArrayList`**: Used to store the edges in our graph's adjacency list.

## 9. How to Compile
Make sure you have Java installed (Java 8 or higher). Open a terminal (Command Prompt or PowerShell) and run:
```bash
cd Mini-Project-I/src
javac *.java
```

## 10. How to Run
After compiling successfully, start the application with:
```bash
java Main
```

## 11. Sample Output
```
========================================
     VAANAM AIRPORT BAGGAGE SYSTEM
========================================
Simulated Time: 09:00
1. View Flights
2. View Baggage
3. Register Baggage
4. Assign Baggage to Flight
5. View Priority Queue
6. Process Next Baggage
7. Track Baggage
8. View Loading Bays
9. View Conveyor Network
10. Exit

Enter choice: 5
========================================
         BAGGAGE PRIORITY QUEUE
========================================
Rank   Bag ID   Flight   Cutoff   Type      
----------------------------------------
1      B002     F202     10:15    Origin    
2      B005     F202     10:15    Origin    
```

## 12. What is Intentionally Incomplete
- **Dijkstra's Algorithm**: The graph exists, but we are not finding the shortest path yet. 
- **Conveyor Failures**: We have an `available` boolean, but we don't simulate failures yet.
- **Real-Time Clock**: We just use an integer for simulated time.
- **GUI**: We are sticking to the terminal for now.

## 13. Planned Review 2 Features
In the next review, we will implement Dijkstra's Algorithm in the `ConveyorRoutePlanner` to actually find the shortest time path through the network. We will also introduce random conveyor failures to demonstrate automatic route recalculation.
