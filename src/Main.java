import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class Main {
    private static AuthManager auth = new AuthManager();
    public static BaggageService baggageService;
    private static final int PORT = 8080;

    public static void main(String[] args) {
        DatabaseManager.initializeDatabase();
        baggageService = new BaggageService(auth);

        // Ensure default demonstration staff accounts exist for each role
        ensureUserExists("admin", "admin123", Role.ADMIN);
        ensureUserExists("supervisor", "super123", Role.SUPERVISOR);
        ensureUserExists("operator", "op123", Role.BAGGAGE_OPERATOR);
        ensureUserExists("security", "sec123", Role.SECURITY_OFFICER);
        ensureUserExists("auditor", "audit123", Role.AUDITOR);

        System.out.println("\n=======================================================");
        System.out.println("   VAANAM AIRPORT BAGGAGE HANDLING & ROUTING SYSTEM   ");
        System.out.println("=======================================================");
        startServer();
        System.out.println("Backend server running at: http://localhost:" + PORT);
        System.out.println("Open your web browser and navigate to http://localhost:" + PORT);
        System.out.println("=======================================================\n");
    }

    public static void startServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
            server.createContext("/", new StaticFileHandler());
            server.createContext("/api/login", new LoginHandler());
            server.createContext("/api/logout", new LogoutHandler());
            server.createContext("/api/session", new SessionHandler());
            server.createContext("/api/register", new RegisterHandler());
            server.createContext("/api/stats", new StatsHandler());
            server.createContext("/api/baggage", new BaggageHandler());
            server.createContext("/api/baggage/register", new BaggageRegisterHandler());
            server.createContext("/api/baggage/assign", new BaggageAssignHandler());
            server.createContext("/api/baggage/clearance", new BaggageClearanceHandler());
            server.createContext("/api/baggage/process", new BaggageProcessHandler());
            server.createContext("/api/baggage/track", new BaggageTrackHandler());
            server.createContext("/api/flights", new FlightHandler());
            server.createContext("/api/flights/update", new FlightUpdateHandler());
            server.createContext("/api/bays", new BaysHandler());
            server.createContext("/api/conveyors", new ConveyorsHandler());
            server.createContext("/api/conveyors/toggle", new ConveyorToggleHandler());
            server.createContext("/api/users", new UsersHandler());
            server.createContext("/api/users/update", new UserUpdateHandler());
            server.createContext("/api/audit", new AuditHandler());
            server.createContext("/api/time", new TimeHandler());
            server.createContext("/api/time/advance", new TimeAdvanceHandler());

            server.setExecutor(null);
            server.start();
        } catch (IOException e) {
            System.err.println("Failed to start server on port " + PORT + ": " + e.getMessage());
        }
    }

    private static void ensureUserExists(String username, String password, Role role) {
        if (DatabaseManager.getUser(username) == null) {
            String[] hashSalt = AuthManager.hashPassword(password);
            User user = new User(username, hashSalt[0], hashSalt[1], role, true);
            DatabaseManager.saveUser(user);
            DatabaseManager.logAudit("SYSTEM", "INITIAL_SETUP", username, baggageService.getSimulatedTime(), "SUCCESS", "Staff account initialized: " + role);
            System.out.println("Initialized account: " + username + " (" + role + ")");
        }
    }

    public static User getSessionUser(HttpExchange exchange) {
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader != null) {
            for (String cookie : cookieHeader.split(";")) {
                String trimmed = cookie.trim();
                if (trimmed.startsWith("session_id=")) {
                    String sessionId = trimmed.substring("session_id=".length()).trim();
                    return AuthManager.getSessionUser(sessionId);
                }
            }
        }
        return null;
    }

    public static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    public static String readRequestBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = is.read(buffer)) != -1) {
            baos.write(buffer, 0, length);
        }
        return baos.toString(StandardCharsets.UTF_8.name());
    }

    public static Map<String, String> parseSimpleJson(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null || json.trim().isEmpty()) return map;

        String clean = json.trim();
        if (clean.startsWith("{")) clean = clean.substring(1);
        if (clean.endsWith("}")) clean = clean.substring(0, clean.length() - 1);

        // Simple token parser for keys and values
        String[] pairs = clean.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        for (String pair : pairs) {
            int colonIndex = pair.indexOf(':');
            if (colonIndex > 0) {
                String key = pair.substring(0, colonIndex).trim().replace("\"", "");
                String value = pair.substring(colonIndex + 1).trim();
                if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                    value = value.substring(1, value.length() - 1);
                }
                map.put(key, value);
            }
        }
        return map;
    }

    public static Map<String, String> parseQueryParams(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) return map;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            try {
                if (idx > 0) {
                    map.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"),
                            URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
                } else if (idx == 0) {
                    map.put("", URLDecoder.decode(pair.substring(1), "UTF-8"));
                }
            } catch (UnsupportedEncodingException ignored) {}
        }
        return map;
    }

    public static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    // ==================== HANDLERS ====================

    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";
            File file = new File("web" + path);

            if (file.exists() && !file.isDirectory()) {
                String contentType = "text/plain";
                if (path.endsWith(".html")) contentType = "text/html";
                else if (path.endsWith(".css")) contentType = "text/css";
                else if (path.endsWith(".js")) contentType = "application/javascript";
                else if (path.endsWith(".png")) contentType = "image/png";
                else if (path.endsWith(".svg")) contentType = "image/svg+xml";

                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(200, file.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    Files.copy(file.toPath(), os);
                }
            } else {
                sendResponse(exchange, 404, "{\"error\": \"Not Found\"}");
            }
        }
    }

    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
                return;
            }

            Map<String, String> data = parseSimpleJson(readRequestBody(exchange));
            String username = data.getOrDefault("username", "").trim();
            String password = data.getOrDefault("password", "").trim();

            User user = DatabaseManager.getUser(username);
            if (user != null && user.isActive() && AuthManager.verifyPassword(password, user.getPasswordHash(), user.getPasswordSalt())) {
                String sessionId = AuthManager.createSession(user);
                exchange.getResponseHeaders().add("Set-Cookie", "session_id=" + sessionId + "; Path=/; HttpOnly; SameSite=Lax");
                DatabaseManager.logAudit(user.getUsername(), "LOGIN", "System", baggageService.getSimulatedTime(), "SUCCESS", "Logged in");
                sendResponse(exchange, 200, "{\"username\":\"" + escapeJson(user.getUsername()) + "\", \"role\":\"" + user.getRole() + "\"}");
            } else {
                DatabaseManager.logAudit(username, "LOGIN", "System", baggageService.getSimulatedTime(), "FAILED", "Invalid credentials or inactive account");
                sendResponse(exchange, 401, "{\"error\": \"Invalid username or password, or account inactive.\"}");
            }
        }
    }

    static class LogoutHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookieHeader != null) {
                for (String cookie : cookieHeader.split(";")) {
                    String trimmed = cookie.trim();
                    if (trimmed.startsWith("session_id=")) {
                        String sessionId = trimmed.substring("session_id=".length()).trim();
                        AuthManager.invalidateSession(sessionId);
                    }
                }
            }
            exchange.getResponseHeaders().add("Set-Cookie", "session_id=; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT");
            sendResponse(exchange, 200, "{\"status\": \"logged out\"}");
        }
    }

    static class SessionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            User user = getSessionUser(exchange);
            if (user == null || !user.isActive()) {
                sendResponse(exchange, 401, "{\"error\": \"Unauthorized\"}");
                return;
            }
            sendResponse(exchange, 200, "{\"username\":\"" + escapeJson(user.getUsername()) + "\", \"role\":\"" + user.getRole() + "\", \"active\":" + user.isActive() + "}");
        }
    }

    static class RegisterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
                return;
            }

            Map<String, String> data = parseSimpleJson(readRequestBody(exchange));
            String username = data.getOrDefault("username", "").trim();
            String password = data.getOrDefault("password", "").trim();
            String roleStr = data.getOrDefault("role", "").trim().toUpperCase();

            if (username.isEmpty() || password.isEmpty()) {
                sendResponse(exchange, 400, "{\"error\": \"Username and password are required.\"}");
                return;
            }

            User current = getSessionUser(exchange);
            int adminCount = DatabaseManager.getActiveAdminCount();

            // First-run setup: initial admin creation
            if (adminCount == 0) {
                roleStr = "ADMIN";
            } else {
                // Controlled account creation: must be authenticated ADMIN
                if (current == null || current.getRole() != Role.ADMIN) {
                    sendResponse(exchange, 403, "{\"error\": \"Only administrators can create user accounts.\"}");
                    return;
                }
            }

            Role role;
            try {
                role = Role.valueOf(roleStr);
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"error\": \"Invalid role specified.\"}");
                return;
            }

            if (DatabaseManager.getUser(username) != null) {
                sendResponse(exchange, 409, "{\"error\": \"Username already exists.\"}");
                return;
            }

            String[] hashSalt = AuthManager.hashPassword(password);
            User newUser = new User(username, hashSalt[0], hashSalt[1], role, true);
            DatabaseManager.saveUser(newUser);
            DatabaseManager.logAudit((current != null ? current.getUsername() : "SYSTEM"), "CREATE_USER", username, baggageService.getSimulatedTime(), "SUCCESS", "Role: " + role);
            sendResponse(exchange, 200, "{\"status\": \"User created\", \"username\":\"" + escapeJson(username) + "\"}");
        }
    }

    static class StatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            User user = getSessionUser(exchange);
            if (user == null || !user.isActive()) {
                sendResponse(exchange, 401, "{\"error\": \"Unauthorized\"}");
                return;
            }

            Map<String, Object> stats = baggageService.getDashboardStats();
            StringBuilder json = new StringBuilder("{");
            int idx = 0;
            for (Map.Entry<String, Object> entry : stats.entrySet()) {
                if (idx++ > 0) json.append(",");
                json.append("\"").append(entry.getKey()).append("\":");
                if (entry.getValue() instanceof Number || entry.getValue() instanceof Boolean) {
                    json.append(entry.getValue());
                } else {
                    json.append("\"").append(escapeJson(entry.getValue().toString())).append("\"");
                }
            }
            json.append("}");

            sendResponse(exchange, 200, json.toString());
        }
    }

    static class BaggageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            User user = getSessionUser(exchange);
            if (user == null || !user.isActive()) {
                sendResponse(exchange, 401, "{\"error\": \"Unauthorized\"}");
                return;
            }

            Map<String, Baggage> all = baggageService.getAllBaggage();
            StringBuilder sb = new StringBuilder("[");
            int i = 0;
            for (Baggage b : all.values()) {
                if (i++ > 0) sb.append(",");
                sb.append("{")
                  .append("\"bagId\":\"").append(escapeJson(b.getBagId())).append("\",")
                  .append("\"bagType\":\"").append(escapeJson(b.getBagType())).append("\",")
                  .append("\"direction\":\"").append(b.getDirection()).append("\",")
                  .append("\"category\":\"").append(b.getPassengerCategory().getDisplayName()).append("\",")
                  .append("\"flightId\":\"").append(b.getAssignedFlight() != null ? escapeJson(b.getAssignedFlight().getFlightId()) : "UNASSIGNED").append("\",")
                  .append("\"currentLocation\":\"").append(escapeJson(b.getCurrentLocation())).append("\",")
                  .append("\"destinationNode\":\"").append(escapeJson(b.getDestinationNode() != null ? b.getDestinationNode() : "")).append("\",")
                  .append("\"cutoff\":\"").append(b.getFormattedCutoff()).append("\",")
                  .append("\"cleared\":").append(b.isOperatorCleared()).append(",")
                  .append("\"clearanceReason\":\"").append(escapeJson(b.getClearanceReason())).append("\",")
                  .append("\"status\":\"").append(b.getStatus()).append("\",")
                  .append("\"estimatedTime\":").append(b.getEstimatedTime())
                  .append("}");
            }
            sb.append("]");
            sendResponse(exchange, 200, sb.toString());
        }
    }

    static class BaggageRegisterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
                return;
            }

            User user = getSessionUser(exchange);
            if (user == null || !user.isActive()) {
                sendResponse(exchange, 401, "{\"error\": \"Unauthorized\"}");
                return;
            }

            try {
                AuthManager.requireUserPermission(user, Role.ADMIN, Role.BAGGAGE_OPERATOR);
                Map<String, String> data = parseSimpleJson(readRequestBody(exchange));
                String bagId = data.getOrDefault("bagId", "").trim();
                String dirStr = data.getOrDefault("direction", "OUTGOING").trim().toUpperCase();
                int catRank = Integer.parseInt(data.getOrDefault("categoryRank", "3"));
                String flightId = data.getOrDefault("flightId", "").trim();
                String destNode = data.getOrDefault("destinationNode", "").trim();

                BaggageDirection direction = BaggageDirection.OUTGOING;
                if ("INCOMING".equalsIgnoreCase(dirStr)) direction = BaggageDirection.INCOMING;
                else if ("TRANSFER".equalsIgnoreCase(dirStr)) direction = BaggageDirection.TRANSFER;

                Baggage bag = baggageService.registerBaggage(bagId, direction, catRank, flightId, destNode, user);
                sendResponse(exchange, 200, "{\"status\": \"Registered\", \"bagId\":\"" + escapeJson(bag.getBagId()) + "\"}");
            } catch (SecurityException e) {
                sendResponse(exchange, 403, "{\"error\": \"" + escapeJson(e.getMessage()) + "\"}");
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"error\": \"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    static class BaggageAssignHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
                return;
            }

            User user = getSessionUser(exchange);
            if (user == null || !user.isActive()) {
                sendResponse(exchange, 401, "{\"error\": \"Unauthorized\"}");
                return;
            }

            try {
                AuthManager.requireUserPermission(user, Role.ADMIN, Role.BAGGAGE_OPERATOR);
                Map<String, String> data = parseSimpleJson(readRequestBody(exchange));
                String bagId = data.getOrDefault("bagId", "").trim();
                String flightId = data.getOrDefault("flightId", "").trim();

                baggageService.assignBaggageToFlight(bagId, flightId, user);
                sendResponse(exchange, 200, "{\"status\": \"Flight assigned\"}");
            } catch (SecurityException e) {
                sendResponse(exchange, 403, "{\"error\": \"" + escapeJson(e.getMessage()) + "\"}");
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"error\": \"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    static class BaggageClearanceHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
                return;
            }

            User user = getSessionUser(exchange);
            if (user == null || !user.isActive()) {
                sendResponse(exchange, 401, "{\"error\": \"Unauthorized\"}");
                return;
            }

            try {
                AuthManager.requireUserPermission(user, Role.SECURITY_OFFICER);
                Map<String, String> data = parseSimpleJson(readRequestBody(exchange));
                String bagId = data.getOrDefault("bagId", "").trim();
                boolean approved = "true".equalsIgnoreCase(data.getOrDefault("approved", "false"));
                String reason = data.getOrDefault("reason", "");

                if (approved) {
                    baggageService.grantOperatorClearance(bagId, reason, user);
                    sendResponse(exchange, 200, "{\"status\": \"Clearance approved\"}");
                } else {
                    baggageService.rejectOperatorClearance(bagId, reason, user);
                    sendResponse(exchange, 200, "{\"status\": \"Clearance rejected\"}");
                }
            } catch (SecurityException e) {
                sendResponse(exchange, 403, "{\"error\": \"" + escapeJson(e.getMessage()) + "\"}");
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"error\": \"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    static class BaggageProcessHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
                return;
            }

            User user = getSessionUser(exchange);
            if (user == null || !user.isActive()) {
                sendResponse(exchange, 401, "{\"error\": \"Unauthorized\"}");
                return;
            }

            try {
                AuthManager.requireUserPermission(user, Role.ADMIN, Role.BAGGAGE_OPERATOR);
                Map<String, String> data = parseSimpleJson(readRequestBody(exchange));
                String bagId = data.getOrDefault("bagId", "").trim();

                Baggage processed;
                if (!bagId.isEmpty()) {
                    processed = baggageService.processSpecificBaggage(bagId, user);
                } else {
                    processed = baggageService.processNextBaggage(user);
                }

                if (processed == null) {
                    sendResponse(exchange, 200, "{\"status\": \"Queue is empty\"}");
                    return;
                }

                StringBuilder sb = new StringBuilder("{");
                sb.append("\"status\":\"Processed\",")
                  .append("\"bagId\":\"").append(escapeJson(processed.getBagId())).append("\",")
                  .append("\"bagStatus\":\"").append(processed.getStatus()).append("\",")
                  .append("\"currentLocation\":\"").append(escapeJson(processed.getCurrentLocation())).append("\",")
                  .append("\"estimatedTime\":").append(processed.getEstimatedTime()).append(",")
                  .append("\"route\":[");
                List<String> r = processed.getCurrentRoute();
                for (int i = 0; i < r.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append("\"").append(escapeJson(r.get(i))).append("\"");
                }
                sb.append("]}");

                sendResponse(exchange, 200, sb.toString());
            } catch (SecurityException e) {
                sendResponse(exchange, 403, "{\"error\": \"" + escapeJson(e.getMessage()) + "\"}");
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"error\": \"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    static class BaggageTrackHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            User user = getSessionUser(exchange);
            if (user == null || !user.isActive()) {
                sendResponse(exchange, 401, "{\"error\": \"Unauthorized\"}");
                return;
            }

            Map<String, String> q = parseQueryParams(exchange.getRequestURI().getQuery());
            String bagId = q.getOrDefault("id", "").trim();
            Baggage bag = baggageService.getBaggage(bagId);

            if (bag == null) {
                sendResponse(exchange, 404, "{\"error\": \"Baggage not found: " + escapeJson(bagId) + "\"}");
                return;
            }

            // Calculate or fetch route to destination
            String dest = bag.getDestinationNode();
            List<String> route = bag.getCurrentRoute();
            int estTime = bag.getEstimatedTime();

            if ((route == null || route.isEmpty()) && dest != null && !dest.isEmpty()) {
                RoutingResult res = baggageService.calculateRoute(bag.getCurrentLocation(), dest);
                if (res != null) {
                    route = res.route;
                    estTime = res.totalTime;
                }
            }

            StringBuilder sb = new StringBuilder("{");
            sb.append("\"bagId\":\"").append(escapeJson(bag.getBagId())).append("\",")
              .append("\"bagType\":\"").append(escapeJson(bag.getBagType())).append("\",")
              .append("\"direction\":\"").append(bag.getDirection()).append("\",")
              .append("\"status\":\"").append(bag.getStatus()).append("\",")
              .append("\"currentLocation\":\"").append(escapeJson(bag.getCurrentLocation())).append("\",")
              .append("\"destinationNode\":\"").append(escapeJson(dest != null ? dest : "")).append("\",")
              .append("\"flightId\":\"").append(bag.getAssignedFlight() != null ? escapeJson(bag.getAssignedFlight().getFlightId()) : "N/A").append("\",")
              .append("\"cutoff\":\"").append(bag.getFormattedCutoff()).append("\",")
              .append("\"estimatedTime\":").append(estTime).append(",")
              .append("\"cleared\":").append(bag.isOperatorCleared()).append(",")
              .append("\"simulatedTime\":").append(baggageService.getSimulatedTime()).append(",")
              .append("\"formattedSimulatedTime\":\"").append(baggageService.getFormattedSimulatedTime()).append("\",")
              .append("\"route\":[");
            if (route != null) {
                for (int i = 0; i < route.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append("\"").append(escapeJson(route.get(i))).append("\"");
                }
            }
            sb.append("]}");

            sendResponse(exchange, 200, sb.toString());
        }
    }

    static class FlightHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            User user = getSessionUser(exchange);
            if (user == null || !user.isActive()) {
                sendResponse(exchange, 401, "{\"error\": \"Unauthorized\"}");
                return;
            }

            Map<String, Flight> flights = baggageService.getAllFlights();
            StringBuilder sb = new StringBuilder("[");
            int i = 0;
            for (Flight f : flights.values()) {
                if (i++ > 0) sb.append(",");
                sb.append("{")
                  .append("\"flightId\":\"").append(escapeJson(f.getFlightId())).append("\",")
                  .append("\"destination\":\"").append(escapeJson(f.getDestination())).append("\",")
                  .append("\"cutoff\":").append(f.getLoadingCutoff()).append(",")
                  .append("\"formattedCutoff\":\"").append(f.getFormattedCutoff()).append("\",")
                  .append("\"assignedBay\":\"").append(f.getAssignedBay() != null ? escapeJson(f.getAssignedBay().getBayId()) : "NONE").append("\"")
                  .append("}");
            }
            sb.append("]");
            sendResponse(exchange, 200, sb.toString());
        }
    }

    static class FlightUpdateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
                return;
            }

            User user = getSessionUser(exchange);
            if (user == null || !user.isActive()) {
                sendResponse(exchange, 401, "{\"error\": \"Unauthorized\"}");
                return;
            }

            try {
                AuthManager.requireUserPermission(user, Role.ADMIN, Role.SUPERVISOR);
                Map<String, String> data = parseSimpleJson(readRequestBody(exchange));
                String flightId = data.getOrDefault("flightId", "").trim();
                String newBayId = data.getOrDefault("assignedBay", "").trim();
                String cutoffStr = data.getOrDefault("cutoff", "").trim();

                if (!newBayId.isEmpty()) {
                    baggageService.changeFlightBay(flightId, newBayId, user);
                }
                if (!cutoffStr.isEmpty()) {
                    int cutoff = Integer.parseInt(cutoffStr);
                    baggageService.updateFlightCutoff(flightId, cutoff, user);
                }

                sendResponse(exchange, 200, "{\"status\": \"Flight updated\"}");
            } catch (SecurityException e) {
                sendResponse(exchange, 403, "{\"error\": \"" + escapeJson(e.getMessage()) + "\"}");
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"error\": \"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    static class BaysHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            User user = getSessionUser(exchange);
            if (user == null || !user.isActive()) {
                sendResponse(exchange, 401, "{\"error\": \"Unauthorized\"}");
                return;
            }

            Map<String, LoadingBay> bays = baggageService.getAllBays();
            StringBuilder sb = new StringBuilder("[");
            int i = 0;
            for (LoadingBay bay : bays.values()) {
                if (i++ > 0) sb.append(",");
                sb.append("{")
                  .append("\"bayId\":\"").append(escapeJson(bay.getBayId())).append("\",")
                  .append("\"assignedFlight\":\"").append(bay.getAssignedFlight() != null ? escapeJson(bay.getAssignedFlight().getFlightId()) : "NONE").append("\"")
                  .append("}");
            }
            sb.append("]");
            sendResponse(exchange, 200, sb.toString());
        }
    }

    static class ConveyorsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            User user = getSessionUser(exchange);
            if (user == null || !user.isActive()) {
                sendResponse(exchange, 401, "{\"error\": \"Unauthorized\"}");
                return;
            }

            List<ConveyorSegment> segments = baggageService.getConveyorPlanner().getAllSegments();
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < segments.size(); i++) {
                ConveyorSegment s = segments.get(i);
                if (i > 0) sb.append(",");
                sb.append("{")
                  .append("\"fromNode\":\"").append(escapeJson(s.getFromNode())).append("\",")
                  .append("\"toNode\":\"").append(escapeJson(s.getToNode())).append("\",")
                  .append("\"processingTime\":").append(s.getProcessingTime()).append(",")
                  .append("\"available\":").append(s.isAvailable())
                  .append("}");
            }
            sb.append("]");
            sendResponse(exchange, 200, sb.toString());
        }
    }

    static class ConveyorToggleHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
                return;
            }

            User user = getSessionUser(exchange);
            if (user == null || !user.isActive()) {
                sendResponse(exchange, 401, "{\"error\": \"Unauthorized\"}");
                return;
            }

            try {
                AuthManager.requireUserPermission(user, Role.ADMIN, Role.SUPERVISOR);
                Map<String, String> data = parseSimpleJson(readRequestBody(exchange));
                String from = data.getOrDefault("fromNode", "").trim();
                String to = data.getOrDefault("toNode", "").trim();
                boolean avail = "true".equalsIgnoreCase(data.getOrDefault("available", "true"));

                boolean ok;
                if (avail) {
                    ok = baggageService.restoreConveyor(from, to, user);
                } else {
                    ok = baggageService.markConveyorUnavailable(from, to, user);
                }

                if (ok) {
                    sendResponse(exchange, 200, "{\"status\": \"Conveyor updated\", \"available\":" + avail + "}");
                } else {
                    sendResponse(exchange, 404, "{\"error\": \"Segment not found\"}");
                }
            } catch (SecurityException e) {
                sendResponse(exchange, 403, "{\"error\": \"" + escapeJson(e.getMessage()) + "\"}");
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"error\": \"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    static class UsersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            User user = getSessionUser(exchange);
            if (user == null || !user.isActive()) {
                sendResponse(exchange, 401, "{\"error\": \"Unauthorized\"}");
                return;
            }

            try {
                AuthManager.requireUserPermission(user, Role.ADMIN, Role.AUDITOR);
                List<User> list = DatabaseManager.getAllUsers();
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < list.size(); i++) {
                    User u = list.get(i);
                    if (i > 0) sb.append(",");
                    sb.append("{")
                      .append("\"username\":\"").append(escapeJson(u.getUsername())).append("\",")
                      .append("\"role\":\"").append(u.getRole()).append("\",")
                      .append("\"active\":").append(u.isActive())
                      .append("}");
                }
                sb.append("]");
                sendResponse(exchange, 200, sb.toString());
            } catch (SecurityException e) {
                sendResponse(exchange, 403, "{\"error\": \"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    static class UserUpdateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
                return;
            }

            User user = getSessionUser(exchange);
            if (user == null || !user.isActive()) {
                sendResponse(exchange, 401, "{\"error\": \"Unauthorized\"}");
                return;
            }

            try {
                AuthManager.requireUserPermission(user, Role.ADMIN);
                Map<String, String> data = parseSimpleJson(readRequestBody(exchange));
                String targetUsername = data.getOrDefault("username", "").trim();
                String action = data.getOrDefault("action", "").trim().toLowerCase();
                String value = data.getOrDefault("value", "").trim();

                User target = DatabaseManager.getUser(targetUsername);
                if (target == null) {
                    sendResponse(exchange, 404, "{\"error\": \"Target user not found\"}");
                    return;
                }

                if ("status".equals(action)) {
                    boolean active = "true".equalsIgnoreCase(value);
                    if (!active && target.getRole() == Role.ADMIN && DatabaseManager.getActiveAdminCount() <= 1) {
                        sendResponse(exchange, 400, "{\"error\": \"Cannot deactivate the only active administrator.\"}");
                        return;
                    }
                    DatabaseManager.updateUserStatus(targetUsername, active);
                    DatabaseManager.logAudit(user.getUsername(), "UPDATE_USER_STATUS", targetUsername, baggageService.getSimulatedTime(), "SUCCESS", "Active: " + active);
                } else if ("role".equals(action)) {
                    Role newRole = Role.valueOf(value.toUpperCase());
                    if (target.getRole() == Role.ADMIN && newRole != Role.ADMIN && DatabaseManager.getActiveAdminCount() <= 1) {
                        sendResponse(exchange, 400, "{\"error\": \"Cannot demote the only active administrator.\"}");
                        return;
                    }
                    DatabaseManager.updateUserRole(targetUsername, newRole);
                    DatabaseManager.logAudit(user.getUsername(), "UPDATE_USER_ROLE", targetUsername, baggageService.getSimulatedTime(), "SUCCESS", "New role: " + newRole);
                } else if ("password".equals(action)) {
                    String[] hashSalt = AuthManager.hashPassword(value);
                    DatabaseManager.updateUserPassword(targetUsername, hashSalt[0], hashSalt[1]);
                    DatabaseManager.logAudit(user.getUsername(), "RESET_PASSWORD", targetUsername, baggageService.getSimulatedTime(), "SUCCESS", "Password reset by admin");
                }

                sendResponse(exchange, 200, "{\"status\": \"User updated successfully\"}");
            } catch (SecurityException e) {
                sendResponse(exchange, 403, "{\"error\": \"" + escapeJson(e.getMessage()) + "\"}");
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"error\": \"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    static class AuditHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            User user = getSessionUser(exchange);
            if (user == null || !user.isActive()) {
                sendResponse(exchange, 401, "{\"error\": \"Unauthorized\"}");
                return;
            }

            try {
                AuthManager.requireUserPermission(user, Role.ADMIN, Role.AUDITOR);
                List<String[]> logs = DatabaseManager.getAuditLogs();
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < logs.size(); i++) {
                    String[] l = logs.get(i);
                    if (i > 0) sb.append(",");
                    sb.append("{")
                      .append("\"logId\":").append(l[0]).append(",")
                      .append("\"username\":\"").append(escapeJson(l[1])).append("\",")
                      .append("\"action\":\"").append(escapeJson(l[2])).append("\",")
                      .append("\"targetRecord\":\"").append(escapeJson(l[3])).append("\",")
                      .append("\"simulatedTime\":").append(l[4]).append(",")
                      .append("\"result\":\"").append(escapeJson(l[5])).append("\",")
                      .append("\"reason\":\"").append(escapeJson(l[6])).append("\",")
                      .append("\"timestamp\":\"").append(escapeJson(l[7])).append("\"")
                      .append("}");
                }
                sb.append("]");
                sendResponse(exchange, 200, sb.toString());
            } catch (SecurityException e) {
                sendResponse(exchange, 403, "{\"error\": \"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    static class TimeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            int simTime = baggageService.getSimulatedTime();
            String formatted = baggageService.getFormattedSimulatedTime();
            sendResponse(exchange, 200, "{\"simulatedTime\":" + simTime + ", \"formattedTime\":\"" + escapeJson(formatted) + "\"}");
        }
    }

    static class TimeAdvanceHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
                return;
            }

            User user = getSessionUser(exchange);
            if (user == null || !user.isActive()) {
                sendResponse(exchange, 401, "{\"error\": \"Unauthorized\"}");
                return;
            }

            try {
                AuthManager.requireUserPermission(user, Role.ADMIN, Role.SUPERVISOR);
                Map<String, String> data = parseSimpleJson(readRequestBody(exchange));
                int mins = Integer.parseInt(data.getOrDefault("minutes", "15"));
                baggageService.advanceSimulatedClock(mins, user);
                sendResponse(exchange, 200, "{\"status\": \"Clock advanced\", \"formattedTime\":\"" + baggageService.getFormattedSimulatedTime() + "\"}");
            } catch (SecurityException e) {
                sendResponse(exchange, 403, "{\"error\": \"" + escapeJson(e.getMessage()) + "\"}");
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"error\": \"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }
}
