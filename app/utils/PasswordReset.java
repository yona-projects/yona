/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Suwon Chae
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package utils;

import models.User;
import org.apache.shiro.crypto.SecureRandomNumberGenerator;
import org.apache.shiro.crypto.hash.Sha1Hash;
import org.joda.time.DateTime;
import play.Logger;

import java.util.HashMap;
import java.util.Map;

public class PasswordReset {
    /**
     *  {@code HashMap<loginId, hashString>}
     */
    public static final Map<String, String> resetHashMap = new HashMap<>();
    /**
     * {@code HashMap<hashString, millisecond>}
     */
    public static final Map<String, Long> resetHashTimetable = new HashMap<>();
    /**
     * hashCode expire time limit, 1 hour
     */
    public static final int HASH_EXPIRE_TIME_MILLISEC = 3600*1000;

    public static String generateResetHash(String loginId) {
        return new Sha1Hash(loginId, new SecureRandomNumberGenerator().nextBytes(), 1).toHex();
    }

    public static void addHashToResetTable(String userId, String hashString) {
        PasswordReset.resetHashMap.put(userId, hashString);
        resetHashTimetable.put(hashString, new DateTime().getMillis());
    }

    public static boolean isValidResetHash(String hashString) {
        if( !resetHashMap.containsValue(hashString) ) {
            return false;
        }

        if(isExpired(hashString)) {
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
