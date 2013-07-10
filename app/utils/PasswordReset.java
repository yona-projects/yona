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

    /**
     * loginId를 기준으로 HashString을 만든다.
     *
     * password reset용 링크를 만들때 이 hashString이 사용된다.
     *
     * @param loginId
     * @return sha1 hash string
     */
    public static String generateResetHash(String loginId) {
        return new Sha1Hash(loginId, new SecureRandomNumberGenerator().nextBytes(), 1).toHex();
    }

    /**
     * password reest을 위한 hashstring과 해당 hashString의 유효시간을 저장한다.
     *
     * @param userId
     * @param hashString
     */
    public static void addHashToResetTable(String userId, String hashString) {
        Logger.debug(">> add to HashTable " + userId + ":" + hashString);
        PasswordReset.resetHashMap.put(userId, hashString);
        resetHashTimetable.put(hashString, new DateTime().getMillis());
    }

    /**
     * 유요한 hashString인지 검증한다.
     *
     * when: password reset전에 유효한 hashString으로 요청되었는지 확인할 때
     *
     * - resetHash내에 해당 hashString이 존재하는지 확인
     * - 유효기간이 만료되지 않았는지 확인
     *
     * @param hashString
     * @return hashString의 유효여부
     */
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

    /**
     * 특정 hashString의 유효기간 만료 여부 검증
     *
     * millisecond 단위로 검증한다.
     *
     * @param hashString
     * @return
     */
    private static boolean isExpired(String hashString) {
        return resetHashTimetable.get(hashString) + PasswordReset.HASH_EXPIRE_TIME_MILLISEC
                < new DateTime().getMillis();
    }

    /**
     * 메모리에 저장되어 있는 특정 hashString을 제거한다.
     *
     * when: 유효시간 만료, password reset 완료 등의 상황에서 사용한다.
     * @param hashString
     */
    private static void removeResetHash(String hashString) {
        String key = getKeyByValue(resetHashMap, hashString);
        resetHashMap.remove(key);
        resetHashTimetable.remove(hashString);
    }

    /**
     * Map에서 value값으로 key를 찾아낸다.
     *
     * when: hashString으로 loginId를 찾아낼 때 사용한다.
     *
     * 동작방식은 해당 map을 쭉 돌면서 value 비교를 해서 값이 같으면 해당 키 값을 돌려준다.
     * value가 중복되어 있고 key가 다른 경우에는 사용하면 안된다.
     *
     * @param map
     * @param value
     * @return value에 해당하는 key
     */
    private static <T, E> T getKeyByValue(Map<T, E> map, E value) {
        for (Map.Entry<T, E> entry : map.entrySet()) {
            if (value.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * 새로운 password로 사용자의 password를 변경한다.
     *
     * when: reset password 기능으로 만들어진 hashString을 사용한 password를 변경할때
     *
     * - hashString의 유효여부를 검사한 다음 hashString을 이용해 password reset을 요청한
     * 사용자의 loginId를 찾아낸다.
     * - 새로 입력받은 password로 reset한다.
     * - hashString을 제거한다.
     *
     * @param hashString
     * @param newPassword
     * @return 새로운 password로 정상 변경되었는지 여부
     */
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
