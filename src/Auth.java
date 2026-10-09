import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

enum Role {
    ADMIN,
    SUPERVISOR,
    BAGGAGE_OPERATOR,
    SECURITY_OFFICER,
    AUDITOR
}

class User {
    private String username;
    private String passwordHash;
    private String passwordSalt;
    private Role role;
    private boolean active;

    public User(String username, String passwordHash, String passwordSalt, Role role, boolean active) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.passwordSalt = passwordSalt;
        this.role = role;
        this.active = active;
    }

    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getPasswordSalt() { return passwordSalt; }
    public Role getRole() { return role; }
    public boolean isActive() { return active; }

    public void setRole(Role role) { this.role = role; }
    public void setActive(boolean active) { this.active = active; }
    public void setPassword(String hash, String salt) {
        this.passwordHash = hash;
        this.passwordSalt = salt;
    }
}

class AuthManager {
    private static final int ITERATIONS = 10000;
    private static final int KEY_LENGTH = 256;
    private User currentUser;

    public User getCurrentUser() {
        return currentUser;
    }

    public void logout() {
        currentUser = null;
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    public boolean hasPermission(Role... allowedRoles) {
        if (currentUser == null || !currentUser.isActive()) return false;
        for (Role r : allowedRoles) {
            if (currentUser.getRole() == r) return true;
        }
        return false;
    }
    
    public void requirePermission(Role... allowedRoles) {
        if (!hasPermission(allowedRoles)) {
            throw new SecurityException("Unauthorized: Your role (" + 
                (currentUser != null ? currentUser.getRole() : "GUEST") + 
                ") does not have permission for this action.");
        }
    }

    // Generates a random salt and hashes the password
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

    // Verifies a given password against a stored hash and salt
    public static boolean verifyPassword(String password, String storedHash, String storedSalt) {
        try {
            byte[] salt = Base64.getDecoder().decode(storedSalt);
            byte[] hash = pbkdf2(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
            String computedHash = Base64.getEncoder().encodeToString(hash);
            return computedHash.equals(storedHash);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyLength) throws NoSuchAlgorithmException, InvalidKeySpecException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLength);
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        return skf.generateSecret(spec).getEncoded();
    }
}
