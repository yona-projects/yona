package utils;

import models.User;
import org.apache.shiro.crypto.SecureRandomNumberGenerator;
import org.apache.shiro.crypto.hash.Sha1Hash;
import org.joda.time.DateTime;
import play.Logger;

import java.util.HashMap;
import java.util.Map;

public class PasswordReset {
    public static Map<String, String> resetHashMap = new HashMap<>();
    public static Map<String, Long> resetHashTimetable = new HashMap<>();
    public static final int HASH_EXPIRE_TIME_MILLISEC = 3600*1000;

    public static String generateResetHash(String loginId) {
        return new Sha1Hash(loginId, new SecureRandomNumberGenerator().nextBytes(), 1).toHex();
    }

    public static void addHashToResetTable(String userId, String hashString) {
        Logger.debug(">> add to HashTable " + userId + ":" + hashString);
        PasswordReset.resetHashMap.put(userId, hashString);
        resetHashTimetable.put(hashString, new DateTime().getMillis());
    }

    public static boolean isValidResetHash(String hashString) {
        Logger.debug("Reset hash entry size:" + resetHashMap.size());
        if( !resetHashMap.containsValue(hashString) ) {
            Logger.debug("HashString doesn't exists in resetHashMap: " + hashString);
            return false;
        }

        if(isExpired(hashString)) {
            Logger.debug("HashString was expired: " + hashString);
            return false;
        }

        return true;
    }

    private static boolean isExpired(String hashString) {
        return resetHashTimetable.get(hashString) + PasswordReset.HASH_EXPIRE_TIME_MILLISEC
                < new DateTime().getMillis();
    }

    private static void removeResetHash(String hashString) {
        String key = getKeyByValue(resetHashMap, hashString);
        resetHashMap.remove(key);
        resetHashTimetable.remove(hashString);
    }

    private static <T, E> T getKeyByValue(Map<T, E> map, E value) {
        for (Map.Entry<T, E> entry : map.entrySet()) {
            if (value.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static boolean resetPassword(String hashString, String newPassword) {
        if( !isValidResetHash(hashString) ) {
            return false;
        }

        String loginId = getKeyByValue(resetHashMap, hashString);
        User.resetPassword(loginId, newPassword);
        removeResetHash(hashString);
        return true;
    }
}
