import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Auth {
    // Utility and container for authentication mechanisms
}

class AuthManager {
    private static final int ITERATIONS = 10000;
    private static final int KEY_LENGTH = 256;
    private static final Map<String, User> sessions = new ConcurrentHashMap<>();
    private User currentUser;

    public User getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    public void logout() {
        this.currentUser = null;
    }

    public static String createSession(User user) {
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, user);
        return sessionId;
    }

    public static User getSessionUser(String sessionId) {
        if (sessionId == null) return null;
        return sessions.get(sessionId);
    }

    public static void invalidateSession(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }

    public boolean hasPermission(Role... allowedRoles) {
        return hasUserPermission(currentUser, allowedRoles);
    }

    public void requirePermission(Role... allowedRoles) {
        requireUserPermission(currentUser, allowedRoles);
    }

    public static boolean hasUserPermission(User user, Role... allowedRoles) {
        if (user == null || !user.isActive()) return false;
        for (Role r : allowedRoles) {
            if (user.getRole() == r) return true;
        }
        return false;
    }

    public static void requireUserPermission(User user, Role... allowedRoles) {
        if (!hasUserPermission(user, allowedRoles)) {
            String roleName = (user != null ? user.getRole().toString() : "GUEST");
            throw new SecurityException("Unauthorized: Role (" + roleName + ") does not have permission for this action.");
        }
    }

    // Security clearance authority: Must NOT be granted merely because the user is an admin.
    public static boolean hasClearanceAuthority(User user) {
        if (user == null || !user.isActive()) return false;
        return user.hasClearanceAuthority();
    }

    public static void requireClearancePermission(User user) {
        if (!hasClearanceAuthority(user)) {
            String roleName = (user != null ? user.getRole().toString() : "GUEST");
            throw new SecurityException("Unauthorized: User (" + (user != null ? user.getUsername() : "GUEST") +
                    ", " + roleName + ") does not hold certified security clearance authority.");
        }
    }

    // Enforce passenger data privacy server-side
    public static void requirePassengerAccess(User user, String passengerId) {
        if (user == null || !user.isActive()) {
            throw new SecurityException("Unauthorized: Authentication required.");
        }
        if (user.getRole() == Role.ADMIN || user.getRole() == Role.BAGGAGE_MANAGER) {
            return; // Staff can view authorized baggage
        }
        if (user.getRole() == Role.PASSENGER) {
            if (passengerId == null || !passengerId.equalsIgnoreCase(user.getLinkedPassengerId())) {
                throw new SecurityException("Forbidden: Passengers may only view or track their own baggage records.");
            }
            return;
        }
        throw new SecurityException("Forbidden: Access restricted.");
    }

    // Generates a random 16-byte salt and hashes password using PBKDF2WithHmacSHA256
    public static String[] hashPassword(String password) {
        try {
            SecureRandom random = new SecureRandom();
            byte[] salt = new byte[16];
            random.nextBytes(salt);

            byte[] hash = pbkdf2(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);

            return new String[] {
                Base64.getEncoder().encodeToString(hash),
                Base64.getEncoder().encodeToString(salt)
            };
        } catch (Exception e) {
            throw new RuntimeException("Error hashing password", e);
        }
    }

    // Verifies password against stored PBKDF2 hash and salt
    public static boolean verifyPassword(String password, String storedHash, String storedSalt) {
        try {
            if (password == null || storedHash == null || storedSalt == null) return false;
            byte[] salt = Base64.getDecoder().decode(storedSalt);
            byte[] hash = pbkdf2(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
            String computedHash = Base64.getEncoder().encodeToString(hash);
            return computedHash.equals(storedHash);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyLength)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLength);
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        return skf.generateSecret(spec).getEncoded();
    }
}
