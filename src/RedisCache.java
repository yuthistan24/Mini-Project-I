import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Tiny cache-aside helper for Redis.
 *
 * This is intentionally a small RESP (Redis protocol) client built on a plain
 * Socket, so the mini-project does not need a large caching framework or an
 * extra dependency. Each operation opens a short-lived connection; if Redis is
 * stopped or unreachable every method fails quietly and the caller falls back
 * to SQLite/in-memory data.
 */
public class RedisCache {

    public static final String FLIGHTS_KEY = "flights:all";
    public static final String BAYS_KEY = "bays:all";
    public static final String CONVEYORS_KEY = "conveyors:all";

    private static final String KEY_PREFIX = "vaanam:";
    private static final int CONNECT_TIMEOUT_MS = 500;
    private static final long RETRY_BACKOFF_MS = 2000;

    private static String host = envOrDefault("REDIS_HOST", "127.0.0.1");
    private static int port = envInt("REDIS_PORT", 6379);
    private static String password = envOrDefault("REDIS_PASSWORD", "");
    private static int ttlSeconds = envInt("REDIS_TTL", 45);

    private static volatile boolean available = true;
    private static long lastFailureAt = 0;
    private static long hits = 0;
    private static long misses = 0;
    private static long errors = 0;
    private static long invalidations = 0;
    private static String lastError = "";

    private static final Set<String> baggageKeys = ConcurrentHashMap.newKeySet();

    /** Result of a cache lookup; hit is false when SQLite/data loading was used. */
    public static class Lookup {
        public final String value;
        public final boolean hit;

        public Lookup(String value, boolean hit) {
            this.value = value;
            this.hit = hit;
        }
    }

    public static String baggageTrackKey(String bagId) {
        return "baggage:track:" + bagId;
    }

    /**
     * Cache-aside fetch: return Redis value on a hit, otherwise call the loader
     * (the database/data source), store the result for ttlSeconds, and return it.
     */
    public static synchronized Lookup fetch(String key, Supplier<String> loader) {
        String cached = getValue(key);
        if (cached != null) {
            hits++;
            return new Lookup(cached, true);
        }

        misses++;
        String value = loader.get();
        if (value != null) {
            setValue(key, value);
        }
        return new Lookup(value, false);
    }

    public static synchronized void put(String key, String value) {
        if (value != null) {
            setValue(key, value);
        }
    }

    public static synchronized String get(String key) {
        return getValue(key);
    }

    public static synchronized void invalidateBaggage(String bagId) {
        deleteValue(baggageTrackKey(bagId));
        baggageKeys.remove(baggageTrackKey(bagId));
        invalidations++;
    }

    /** Used after the simulated clock changes: every cached tracking view is stale. */
    public static synchronized void invalidateAllBaggageTracks() {
        for (String key : baggageKeys.toArray(new String[0])) {
            deleteValue(key);
        }
        baggageKeys.clear();
        invalidations++;
    }

    /** Flight details and loading-bay assignments changed. */
    public static synchronized void invalidateFlights() {
        deleteValue(FLIGHTS_KEY);
        deleteValue(BAYS_KEY);
        invalidations++;
    }

    /** Conveyor availability or transit times changed. */
    public static synchronized void invalidateConveyors() {
        deleteValue(CONVEYORS_KEY);
        invalidations++;
    }

    public static synchronized void invalidateAll() {
        for (String key : baggageKeys.toArray(new String[0])) {
            deleteValue(key);
        }
        baggageKeys.clear();
        deleteValue(FLIGHTS_KEY);
        deleteValue(BAYS_KEY);
        deleteValue(CONVEYORS_KEY);
        invalidations++;
    }

    public static synchronized Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("available", available);
        stats.put("host", host);
        stats.put("port", port);
        stats.put("ttlSeconds", ttlSeconds);
        stats.put("hits", hits);
        stats.put("misses", misses);
        stats.put("errors", errors);
        stats.put("invalidations", invalidations);
        stats.put("cachedBaggageKeys", baggageKeys.size());
        stats.put("lastError", lastError);
        return Collections.unmodifiableMap(stats);
    }

    public static int getTtlSeconds() {
        return ttlSeconds;
    }

    public static synchronized void resetStatsForTesting() {
        hits = 0;
        misses = 0;
        errors = 0;
        invalidations = 0;
        lastError = "";
        baggageKeys.clear();
    }

    public static synchronized void setEndpointForTesting(String testHost, int testPort) {
        host = testHost;
        port = testPort;
        available = true;
        lastFailureAt = 0;
        lastError = "";
    }

    public static synchronized void resetEndpointForTesting() {
        host = envOrDefault("REDIS_HOST", "127.0.0.1");
        port = envInt("REDIS_PORT", 6379);
        available = true;
        lastFailureAt = 0;
        lastError = "";
    }

    // ==================== CACHE-ASIDE INTERNALS ====================

    private static String getValue(String key) {
        return execute("GET", KEY_PREFIX + key);
    }

    private static boolean setValue(String key, String value) {
        String reply = execute("SETEX", KEY_PREFIX + key, String.valueOf(ttlSeconds), value);
        if (reply != null) {
            if (key.startsWith("baggage:track:")) {
                baggageKeys.add(key);
            }
            return true;
        }
        return false;
    }

    private static void deleteValue(String key) {
        execute("DEL", KEY_PREFIX + key);
    }

    private static boolean shouldAttempt() {
        return available || (System.currentTimeMillis() - lastFailureAt) >= RETRY_BACKOFF_MS;
    }

    private static synchronized String execute(String... parts) {
        if (!shouldAttempt()) {
            return null;
        }

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(CONNECT_TIMEOUT_MS);

            OutputStream out = new BufferedOutputStream(socket.getOutputStream());
            InputStream in = new BufferedInputStream(socket.getInputStream());

            if (password != null && !password.isEmpty()) {
                writeCommand(out, "AUTH", password);
                out.flush();
                readReply(in);
            }

            writeCommand(out, parts);
            out.flush();
            Object reply = readReply(in);
            markAvailable();
            return reply != null ? reply.toString() : null;
        } catch (Exception e) {
            markFailure(e);
            return null;
        }
    }

    private static void markAvailable() {
        if (!available) {
            System.out.println("[Redis] Connected to " + host + ":" + port + " - cache enabled.");
        }
        available = true;
        lastError = "";
    }

    private static void markFailure(Exception e) {
        boolean wasAvailable = available;
        available = false;
        lastFailureAt = System.currentTimeMillis();
        errors++;
        lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
        if (wasAvailable) {
            System.out.println("[Redis] Unavailable (" + lastError + ") - continuing with SQLite fallback.");
        }
    }

    // ==================== MINIMAL RESP CLIENT ====================

    private static void writeCommand(OutputStream out, String... parts) throws IOException {
        out.write(("*" + parts.length + "\r\n").getBytes(StandardCharsets.UTF_8));
        for (String part : parts) {
            byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
            out.write(("$" + bytes.length + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(bytes);
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
    }

    private static Object readReply(InputStream in) throws IOException {
        int type = in.read();
        if (type == -1) throw new EOFException("Redis closed the connection");

        switch (type) {
            case '+':
                return readLine(in);
            case '-':
                throw new IOException("Redis error: " + readLine(in));
            case ':':
                return Long.parseLong(readLine(in));
            case '$': {
                int length = Integer.parseInt(readLine(in));
                if (length < 0) return null;
                byte[] data = readFully(in, length);
                readFully(in, 2); // trailing CRLF
                return new String(data, StandardCharsets.UTF_8);
            }
            case '*': {
                int count = Integer.parseInt(readLine(in));
                if (count < 0) return null;
                Object[] values = new Object[count];
                for (int i = 0; i < count; i++) {
                    values[i] = readReply(in);
                }
                return values;
            }
            default:
                throw new IOException("Unexpected Redis reply type: " + (char) type);
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\r') {
                int next = in.read();
                if (next != '\n') {
                    throw new IOException("Malformed Redis line ending");
                }
                break;
            }
            line.write(b);
        }
        if (b == -1) throw new EOFException("Redis connection ended mid-reply");
        return line.toString(StandardCharsets.UTF_8.name());
    }

    private static byte[] readFully(InputStream in, int length) throws IOException {
        byte[] data = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = in.read(data, offset, length - offset);
            if (read == -1) throw new EOFException("Redis connection ended mid-reply");
            offset += read;
        }
        return data;
    }

    // ==================== CONFIGURATION ====================

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value != null && !value.trim().isEmpty() ? value.trim() : fallback;
    }

    private static int envInt(String name, int fallback) {
        try {
            String value = System.getenv(name);
            return value != null && !value.trim().isEmpty() ? Integer.parseInt(value.trim()) : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
