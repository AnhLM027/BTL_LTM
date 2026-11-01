package fruit.server.features.accounts;

import static fruit.common.Protocol.bool;
import static fruit.common.Protocol.integer;
import static fruit.common.Protocol.obj;
import static fruit.common.Protocol.str;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class User {
    public static final String PASSWORD_ALGORITHM = "PBKDF2WithHmacSHA256";
    public static final int PASSWORD_ITERATIONS = 120_000;
    public final int id;
    public final String username;
    public String displayName, role = "PLAYER", salt = "", hash = "";
    public int wins, losses, draws, totalScore, avatarId;
    public boolean locked;

    public User(int id, String username, String displayName) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
    }

    public void changePassword(String password) throws GeneralSecurityException {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        salt = Base64.getEncoder().encodeToString(bytes);
        hash = Base64.getEncoder().encodeToString(passwordHash(password, bytes));
    }

    public boolean checkPassword(String password) throws GeneralSecurityException {
        return MessageDigest.isEqual(
                Base64.getDecoder().decode(hash),
                passwordHash(password, Base64.getDecoder().decode(salt)));
    }

    public static byte[] passwordHash(String password, byte[] salt)
            throws GeneralSecurityException {
        PBEKeySpec key = new PBEKeySpec(password.toCharArray(), salt, PASSWORD_ITERATIONS, 256);
        try {
            return SecretKeyFactory.getInstance(PASSWORD_ALGORITHM)
                    .generateSecret(key)
                    .getEncoded();
        } finally {
            key.clearPassword();
        }
    }

    public Map<String, Object> view() {
        return obj(
                "id",
                id,
                "username",
                username,
                "displayName",
                displayName,
                "avatarId",
                avatarId,
                "role",
                role,
                "wins",
                wins,
                "losses",
                losses,
                "draws",
                draws,
                "totalScore",
                totalScore,
                "locked",
                locked);
    }

    public Map<String, Object> saved() {
        Map<String, Object> saved = view();
        saved.put("salt", salt);
        saved.put("hash", hash);
        return saved;
    }

    public static User load(Map<String, Object> map) {
        User user = new User(integer(map, "id"), str(map, "username"), str(map, "displayName"));
        user.role = str(map, "role", "PLAYER");
        user.avatarId = integer(map, "avatarId");
        user.salt = str(map, "salt");
        user.hash = str(map, "hash");
        user.wins = integer(map, "wins");
        user.losses = integer(map, "losses");
        user.draws = integer(map, "draws");
        user.totalScore = integer(map, "totalScore");
        user.locked = bool(map, "locked");
        return user;
    }
}
