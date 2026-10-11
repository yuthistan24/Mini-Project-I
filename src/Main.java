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
            server.createContext("/api/baggage/step", new BaggageStepHandler());
            server.createContext("/api/baggage/track", new BaggageTrackHandler());
            server.createContext("/api/passengers", new PassengerHandler());
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
            server.createContext("/api/system/reset", new SystemResetHandler());

            server.setExecutor(null);
            server.start();
        } catch (IOException e) {
            System.err.println("Failed to start server on port " + PORT + ": " + e.getMessage());
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

    // ==================== HTTP HANDLERS ====================

    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";
            File file = new File("web" + path);

            if (file.exists() && !file.isDirectory()) {
                String contentType = "text/plain";
                if (path.endsWith(".html")) contentType = "text/html; charset=UTF-8";
                else if (path.endsWith(".css")) contentType = "text/css; charset=UTF-8";
                else if (path.endsWith(".js")) contentType = "application/javascript; charset=UTF-8";
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
                DatabaseManager.logAudit(user.getUsername(), "LOGIN", "System", baggageService.getSimulatedTime(), "SUCCESS", "Role: " + user.getRole());

                String passengerId = user.getLinkedPassengerId() != null ? user.getLinkedPassengerId() : "";
                String passengerName = "";
                if (!passengerId.isEmpty()) {
                    Passenger p = baggageService.getPassenger(passengerId);
                    if (p != null) passengerName = p.getFullName();
                }

                sendResponse(exchange, 200, "{\"username\":\"" + escapeJson(user.getUsername()) +
                        "\", \"role\":\"" + user.getRole() +
                        "\", \"clearanceAuthority\":" + user.hasClearanceAuthority() +
                        ", \"linkedPassengerId\":\"" + escapeJson(passengerId) +
                        "\", \"passengerName\":\"" + escapeJson(passengerName) + "\"}");
            } else {
                DatabaseManager.logAudit(username, "LOGIN", "System", baggageService.getSimulatedTime(), "FAILED", "Invalid credentials or inactive account");
                sendResponse(exchange, 401, "{\"error\": \"Invalid username or password, or account is deactivated.\"}");
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

            String passengerId = user.getLinkedPassengerId() != null ? user.getLinkedPassengerId() : "";
            String passengerName = "";
            if (!passengerId.isEmpty()) {
                Passenger p = baggageService.getPassenger(passengerId);
                if (p != null) passengerName = p.getFullName();
            }

            sendResponse(exchange, 200, "{\"username\":\"" + escapeJson(user.getUsername()) +
                    "\", \"role\":\"" + user.getRole() +
                    "\", \"clearanceAuthority\":" + user.hasClearanceAuthority() +
                    ", \"linkedPassengerId\":\"" + escapeJson(passengerId) +
                    "\", \"passengerName\":\"" + escapeJson(passengerName) +
                    "\", \"active\":" + user.isActive() + "}");
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
            String roleStr = data.getOrDefault("role", "PASSENGER").trim().toUpperCase();
            boolean clearance = "true".equalsIgnoreCase(data.getOrDefault("clearanceAuthority", "false"));
            String linkedPassengerId = data.getOrDefault("linkedPassengerId", "").trim();

            if (username.isEmpty() || password.isEmpty()) {
                sendResponse(exchange, 400, "{\"error\": \"Username and password are required.\"}");
                return;
            }

            User current = getSessionUser(exchange);
            int adminCount = DatabaseManager.getActiveAdminCount();

            // First-run setup allows initial admin creation
            if (adminCount == 0) {
                roleStr = "ADMIN";
                clearance = false;
            } else {
                // Otherwise only authenticated ADMIN can provision staff/passenger accounts
                if (current == null || current.getRole() != Role.ADMIN) {
                    sendResponse(exchange, 403, "{\"error\": \"Forbidden: Only administrators can create or provision accounts.\"}");
                    return;
                }
            }

            Role role = Role.fromString(roleStr);

            if (DatabaseManager.getUser(username) != null) {
                sendResponse(exchange, 409, "{\"error\": \"Username already exists.\"}");
                return;
            }

            String[] hashSalt = AuthManager.hashPassword(password);
            User newUser = new User(username, hashSalt[0], hashSalt[1], role, clearance, true,
                    linkedPassengerId.isEmpty() ? null : linkedPassengerId);
            DatabaseManager.saveUser(newUser);
            DatabaseManager.logAudit((current != null ? current.getUsername() : "SYSTEM"), "CREATE_USER", username,
                    baggageService.getSimulatedTime(), "SUCCESS", "Role: " + role + (clearance ? " (Clearance Auth)" : ""));
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

            Map<String, String> q = parseQueryParams(exchange.getRequestURI().getQuery());
            String searchQuery = q.getOrDefault("q", "").trim();
            String dirFilter = q.getOrDefault("direction", "").trim();
            String statusFilter = q.getOrDefault("status", "").trim();

            Collection<Baggage> list;

            // Strict privacy enforcement: PASSENGER can only view their own bags
            if (user.getRole() == Role.PASSENGER) {
                list = baggageService.getBaggageForPassenger(user.getLinkedPassengerId());
            } else {
                if (!searchQuery.isEmpty()) {
                    list = baggageService.searchBaggage(searchQuery);
                } else {
                    list = baggageService.getAllBaggage().values();
                }
            }

            StringBuilder sb = new StringBuilder("[");
            int count = 0;
            for (Baggage b : list) {
                if (!dirFilter.isEmpty() && !b.getDirection().toString().equalsIgnoreCase(dirFilter)) {
                    continue;
                }
                if (!statusFilter.isEmpty() && !b.getStatus().toString().equalsIgnoreCase(statusFilter)) {
                    continue;
                }

                if (count++ > 0) sb.append(",");
                sb.append("{")
                  .append("\"bagId\":\"").append(escapeJson(b.getBagId())).append("\",")
                  .append("\"passengerId\":\"").append(escapeJson(b.getPassengerId())).append("\",")
                  .append("\"passengerName\":\"").append(escapeJson(b.getPassengerName())).append("\",")
                  .append("\"bagType\":\"").append(escapeJson(b.getBagType())).append("\",")
                  .append("\"direction\":\"").append(b.getDirection()).append("\",")
                  .append("\"category\":\"").append(b.getPassengerCategory().getDisplayName()).append("\",")
                  .append("\"flightId\":\"").append(b.getAssignedFlight() != null ? escapeJson(b.getAssignedFlight().getFlightId()) : "UNASSIGNED").append("\",")
                  .append("\"originAirport\":\"").append(escapeJson(b.getOriginAirport())).append("\",")
                  .append("\"destinationAirport\":\"").append(escapeJson(b.getDestinationAirport())).append("\",")
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
                AuthManager.requireUserPermission(user, Role.ADMIN, Role.BAGGAGE_MANAGER);
                Map<String, String> data = parseSimpleJson(readRequestBody(exchange));
                String bagId = data.getOrDefault("bagId", "").trim();
                String passengerId = data.getOrDefault("passengerId", "").trim();
                String passengerName = data.getOrDefault("passengerName", "").trim();
                String dirStr = data.getOrDefault("direction", "OUTGOING").trim().toUpperCase();
                int catRank = Integer.parseInt(data.getOrDefault("categoryRank", "3"));
                String flightId = data.getOrDefault("flightId", "").trim();
                String origin = data.getOrDefault("originAirport", "MAA").trim();
                String dest = data.getOrDefault("destinationAirport", "DEL").trim();
                String destNode = data.getOrDefault("destinationNode", "").trim();

                if (bagId.isEmpty()) {
                    sendResponse(exchange, 400, "{\"error\": \"Baggage ID is required.\"}");
                    return;
                }
                if (passengerId.isEmpty()) {
                    sendResponse(exchange, 400, "{\"error\": \"Passenger ID is required. Every bag must be linked to a passenger.\"}");
                    return;
                }

                BaggageDirection direction = BaggageDirection.OUTGOING;
                if ("INCOMING".equalsIgnoreCase(dirStr)) direction = BaggageDirection.INCOMING;
                else if ("TRANSFER".equalsIgnoreCase(dirStr)) direction = BaggageDirection.TRANSFER;

                Baggage bag = baggageService.registerBaggage(bagId, passengerId, passengerName, direction, catRank,
                        flightId, origin, dest, destNode, user);
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
                AuthManager.requireUserPermission(user, Role.ADMIN, Role.BAGGAGE_MANAGER);
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
                // Must hold certified clearance authority (Admin does not automatically have it)
                AuthManager.requireClearancePermission(user);

                Map<String, String> data = parseSimpleJson(readRequestBody(exchange));
                String bagId = data.getOrDefault("bagId", "").trim();
                boolean approved = "true".equalsIgnoreCase(data.getOrDefault("approved", "false"));
                String reason = data.getOrDefault("reason", "");

                if (approved) {
                    baggageService.grantClearance(bagId, reason, user);
                    sendResponse(exchange, 200, "{\"status\": \"Clearance approved\"}");
                } else {
                    baggageService.rejectClearance(bagId, reason, user);
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
                AuthManager.requireUserPermission(user, Role.ADMIN, Role.BAGGAGE_MANAGER);
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
                  .append("\"passengerId\":\"").append(escapeJson(processed.getPassengerId())).append("\",")
                  .append("\"passengerName\":\"").append(escapeJson(processed.getPassengerName())).append("\",")
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

    static class BaggageStepHandler implements HttpHandler {
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
                AuthManager.requireUserPermission(user, Role.ADMIN, Role.BAGGAGE_MANAGER);
                Map<String, String> data = parseSimpleJson(readRequestBody(exchange));
                String bagId = data.getOrDefault("bagId", "").trim();

                Baggage bag = baggageService.stepBaggageNextNode(bagId, user);
                sendResponse(exchange, 200, "{\"status\": \"Stepped\", \"currentLocation\":\"" +
                        escapeJson(bag.getCurrentLocation()) + "\", \"bagStatus\":\"" + bag.getStatus() + "\"}");
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

            // Server-side privacy verification: Passenger can only view their own bag
            try {
                AuthManager.requirePassengerAccess(user, bag.getPassengerId());
            } catch (SecurityException se) {
                sendResponse(exchange, 403, "{\"error\": \"" + escapeJson(se.getMessage()) + "\"}");
                return;
            }

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
              .append("\"passengerId\":\"").append(escapeJson(bag.getPassengerId())).append("\",")
              .append("\"passengerName\":\"").append(escapeJson(bag.getPassengerName())).append("\",")
              .append("\"bagType\":\"").append(escapeJson(bag.getBagType())).append("\",")
              .append("\"direction\":\"").append(bag.getDirection()).append("\",")
              .append("\"status\":\"").append(bag.getStatus()).append("\",")
              .append("\"currentLocation\":\"").append(escapeJson(bag.getCurrentLocation())).append("\",")
              .append("\"destinationNode\":\"").append(escapeJson(dest != null ? dest : "")).append("\",")
              .append("\"flightId\":\"").append(bag.getAssignedFlight() != null ? escapeJson(bag.getAssignedFlight().getFlightId()) : "N/A").append("\",")
              .append("\"originAirport\":\"").append(escapeJson(bag.getOriginAirport())).append("\",")
              .append("\"destinationAirport\":\"").append(escapeJson(bag.getDestinationAirport())).append("\",")
              .append("\"cutoff\":\"").append(bag.getFormattedCutoff()).append("\",")
              .append("\"estimatedTime\":").append(estTime).append(",")
              .append("\"cleared\":").append(bag.isOperatorCleared()).append(",")
              .append("\"clearanceReason\":\"").append(escapeJson(bag.getClearanceReason())).append("\",")
              .append("\"simulatedTime\":").append(baggageService.getSimulatedTime()).append(",")
              .append("\"formattedSimulatedTime\":\"").append(baggageService.getFormattedSimulatedTime()).append("\",")
              .append("\"route\":[");
            if (route != null) {
                for (int i = 0; i < route.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append("\"").append(escapeJson(route.get(i))).append("\"");
                }
            }
            sb.append("],\"history\":[");
            List<String[]> records = DatabaseManager.getRoutingRecords(bag.getBagId());
            for (int i = 0; i < records.size(); i++) {
                String[] r = records.get(i);
                if (i > 0) sb.append(",");
                sb.append("{\"id\":").append(r[0])
                  .append(",\"from\":\"").append(escapeJson(r[2]))
                  .append("\",\"to\":\"").append(escapeJson(r[3]))
                  .append("\",\"status\":\"").append(escapeJson(r[5]))
                  .append("\",\"time\":").append(r[6])
                  .append(",\"timestamp\":\"").append(escapeJson(r[7]))
                  .append("\"}");
            }
            sb.append("]}");

            sendResponse(exchange, 200, sb.toString());
        }
    }

    static class PassengerHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            User user = getSessionUser(exchange);
            if (user == null || !user.isActive()) {
                sendResponse(exchange, 401, "{\"error\": \"Unauthorized\"}");
                return;
            }

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                try {
                    AuthManager.requireUserPermission(user, Role.ADMIN, Role.BAGGAGE_MANAGER);
                    Map<String, Passenger> map = baggageService.getAllPassengers();
                    StringBuilder sb = new StringBuilder("[");
                    int i = 0;
                    for (Passenger p : map.values()) {
                        if (i++ > 0) sb.append(",");
                        sb.append("{")
                          .append("\"passengerId\":\"").append(escapeJson(p.getPassengerId())).append("\",")
                          .append("\"fullName\":\"").append(escapeJson(p.getFullName())).append("\",")
                          .append("\"username\":\"").append(escapeJson(p.getUsername() != null ? p.getUsername() : "")).append("\",")
                          .append("\"flightId\":\"").append(escapeJson(p.getFlightId() != null ? p.getFlightId() : "")).append("\",")
                          .append("\"category\":\"").append(p.getCategory().getDisplayName()).append("\",")
                          .append("\"contactInfo\":\"").append(escapeJson(p.getContactInfo())).append("\"")
                          .append("}");
                    }
                    sb.append("]");
                    sendResponse(exchange, 200, sb.toString());
                } catch (SecurityException se) {
                    sendResponse(exchange, 403, "{\"error\": \"" + escapeJson(se.getMessage()) + "\"}");
                }
            } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                try {
                    AuthManager.requireUserPermission(user, Role.ADMIN, Role.BAGGAGE_MANAGER);
                    Map<String, String> data = parseSimpleJson(readRequestBody(exchange));
                    String pId = data.getOrDefault("passengerId", "").trim();
                    String name = data.getOrDefault("fullName", "").trim();
                    String uName = data.getOrDefault("username", "").trim();
                    String flId = data.getOrDefault("flightId", "").trim();
                    int catRank = Integer.parseInt(data.getOrDefault("categoryRank", "3"));
                    String contact = data.getOrDefault("contactInfo", "").trim();

                    if (pId.isEmpty() || name.isEmpty()) {
                        sendResponse(exchange, 400, "{\"error\": \"Passenger ID and Full Name are required.\"}");
                        return;
                    }

                    Passenger p = new Passenger(pId, name, uName.isEmpty() ? null : uName, flId,
                            PassengerCategory.fromRank(catRank), contact);
                    baggageService.getAllPassengers().put(pId, p);
                    DatabaseManager.savePassenger(p);
                    DatabaseManager.logAudit(user.getUsername(), "SAVE_PASSENGER", pId, baggageService.getSimulatedTime(), "SUCCESS", name);

                    sendResponse(exchange, 200, "{\"status\": \"Passenger saved\"}");
                } catch (SecurityException se) {
                    sendResponse(exchange, 403, "{\"error\": \"" + escapeJson(se.getMessage()) + "\"}");
                } catch (Exception e) {
                    sendResponse(exchange, 400, "{\"error\": \"" + escapeJson(e.getMessage()) + "\"}");
                }
            } else {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            }
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
                  .append("\"origin\":\"").append(escapeJson(f.getOrigin())).append("\",")
                  .append("\"destination\":\"").append(escapeJson(f.getDestination())).append("\",")
                  .append("\"departure\":").append(f.getDepartureTime()).append(",")
                  .append("\"formattedDeparture\":\"").append(f.getFormattedDeparture()).append("\",")
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
                AuthManager.requireUserPermission(user, Role.ADMIN, Role.BAGGAGE_MANAGER);
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
                AuthManager.requireUserPermission(user, Role.ADMIN, Role.BAGGAGE_MANAGER);
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
                    sendResponse(exchange, 404, "{\"error\": \"Conveyor segment not found\"}");
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
                AuthManager.requireUserPermission(user, Role.ADMIN);
                List<User> list = DatabaseManager.getAllUsers();
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < list.size(); i++) {
                    User u = list.get(i);
                    if (i > 0) sb.append(",");
                    sb.append("{")
                      .append("\"username\":\"").append(escapeJson(u.getUsername())).append("\",")
                      .append("\"role\":\"").append(u.getRole()).append("\",")
                      .append("\"clearanceAuthority\":").append(u.hasClearanceAuthority()).append(",")
                      .append("\"linkedPassengerId\":\"").append(escapeJson(u.getLinkedPassengerId() != null ? u.getLinkedPassengerId() : "")).append("\",")
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
                    Role newRole = Role.fromString(value);
                    if (target.getRole() == Role.ADMIN && newRole != Role.ADMIN && DatabaseManager.getActiveAdminCount() <= 1) {
                        sendResponse(exchange, 400, "{\"error\": \"Cannot demote the only active administrator.\"}");
                        return;
                    }
                    DatabaseManager.updateUserRole(targetUsername, newRole);
                    DatabaseManager.logAudit(user.getUsername(), "UPDATE_USER_ROLE", targetUsername, baggageService.getSimulatedTime(), "SUCCESS", "New role: " + newRole);
                } else if ("clearance".equals(action)) {
                    boolean authority = "true".equalsIgnoreCase(value);
                    DatabaseManager.updateUserClearance(targetUsername, authority);
                    DatabaseManager.logAudit(user.getUsername(), "UPDATE_CLEARANCE_AUTH", targetUsername, baggageService.getSimulatedTime(), "SUCCESS", "Authority: " + authority);
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
                AuthManager.requireUserPermission(user, Role.ADMIN);
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
                AuthManager.requireUserPermission(user, Role.ADMIN, Role.BAGGAGE_MANAGER);
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

    static class SystemResetHandler implements HttpHandler {
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
                boolean confirm = "true".equalsIgnoreCase(data.getOrDefault("confirm", "false"));

                if (!confirm) {
                    sendResponse(exchange, 400, "{\"error\": \"Explicit confirmation parameter required (confirm=true).\"}");
                    return;
                }

                DatabaseManager.cleanResetDatabase();
                baggageService.initializeCleanSampleData();
                DatabaseManager.logAudit(user.getUsername(), "SYSTEM_CLEAN_RESET", "System", baggageService.getSimulatedTime(),
                        "SUCCESS", "Clean database initialisation completed");

                sendResponse(exchange, 200, "{\"status\": \"System database cleanly reset and re-seeded with realistic sample data.\"}");
            } catch (SecurityException e) {
                sendResponse(exchange, 403, "{\"error\": \"" + escapeJson(e.getMessage()) + "\"}");
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"error\": \"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }
}
