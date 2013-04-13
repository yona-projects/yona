package models;

import org.apache.shiro.crypto.SecureRandomNumberGenerator;
import org.apache.shiro.crypto.hash.Sha1Hash;
import org.joda.time.DateTime;
import play.Logger;

import java.util.HashMap;
import java.util.Map;

public class PasswordReset {
    private static Map<String, String> resetHashMap = new HashMap<String, String>();
    private static Map<String, DateTime> resetHashTimetable = new HashMap<String, DateTime>();
    public static final int HASH_EXPIRE_TIME_SEC = 3600;

    public static String generateResetHash(String userId) {
        return new Sha1Hash(userId, new SecureRandomNumberGenerator().nextBytes(), 1).toHex();
    }

    public static void addHashToResetTable(String userId, String hashString) {
        Logger.debug(">> add to HashTable " + userId + ":" + hashString);
        PasswordReset.resetHashMap.put(userId, hashString);
        resetHashTimetable.put(hashString, new DateTime());
    }

    public static String getResetHash(String userId) {
        return PasswordReset.resetHashMap.get(userId);
    }

    public static boolean invalidateResetHash(String userId) {
        String targetIdToReset = PasswordReset.resetHashMap.get(userId);
        if (targetIdToReset == null){
            return false;
        }

        PasswordReset.resetHashMap.remove(userId);
        return true;
    }

    public static boolean isHashExist(String userId) {
        return PasswordReset.resetHashMap.get(userId) != null;
    }

    public static void resetHashTable() {
        resetHashMap.clear();
    }

    public static boolean isValidResetHash(String hashString) {
        Logger.debug("Reset hash entry size:" + resetHashMap.size());
        for(Map.Entry<String, String> entry: resetHashMap.entrySet()){
            Logger.debug(">> " + entry.getKey());
        }
        if( !resetHashMap.containsValue(hashString) ) {
            Logger.debug("HashString doesn't exists in resetHashMap: " + hashString);
            return false;
        }

        if(isExpiredHashString(hashString)) {
            Logger.debug("HashString was expired: " + hashString);
            return false;
        }

        return true;
    }

    private static boolean isExpiredHashString(String hashString) {
        return resetHashTimetable.get(hashString).getMillis() < new DateTime().minusSeconds(PasswordReset.HASH_EXPIRE_TIME_SEC).getMillis();
    }

    public static void removeResetHash(String hashString) {
        String key = getKeyByValue(resetHashMap, hashString);
        resetHashMap.remove(key);
        resetHashTimetable.remove(hashString);
    }

    static Map<String, DateTime> getResetHashTimetable(){
        return resetHashTimetable;
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
