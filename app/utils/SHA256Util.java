package utils;

import org.apache.shiro.crypto.hash.Sha256Hash;
import org.joda.time.LocalDateTime;

public class SHA256Util {
    public static String hashBasedNow() {
        return new Sha256Hash(LocalDateTime.now().toString()).toBase64();
    }
}
