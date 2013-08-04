package models;

import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.Fail.fail;

import controllers.UserApp;
import org.joda.time.DateTime;
import org.junit.*;
import utils.PasswordReset;

import java.util.Map;

public class PasswordResetTest extends ModelTest<PasswordReset> {

    @Before
    public void setUp() throws Exception {
        PasswordReset.resetHashMap.clear();
    }

    @Test
    public void testGenerateResetHash() throws Exception {
        //Given
        String userId = "doortts";

        //When
        String hashString = PasswordReset.generateResetHash(userId);

        //Then
        assertThat(hashString).describedAs("SHA1 character size").hasSize(40);
    }

    @Test
    public void testAddHashToResetTable() throws Exception {
        //Given
        String userId = "doortts";
        String hashString = PasswordReset.generateResetHash(userId);

        //When
        PasswordReset.addHashToResetTable(userId, hashString);

        //Then
        assertThat(getHashString(userId)).isEqualTo(hashString);
    }

    @Test
    public void testInvalidateResetHash() throws Exception {
        //Given
        String userId = "doortts";
        String hashString = PasswordReset.generateResetHash(userId);
        PasswordReset.addHashToResetTable(userId, hashString);

        //When
        boolean result = invalidateResetHash(userId);

        //Then
        assertThat(result).isTrue();
        assertThat(getHashString(userId)).isNull();
    }
    
    @Test
    public void testIsHashExists() {
        //Given
        String userId = "doortts";
        String hashString = PasswordReset.generateResetHash(userId);
        PasswordReset.addHashToResetTable(userId, hashString);

        //When
        boolean result = hashStringExist(userId);

        //Then
        assertThat(result).isTrue();
    }

    @Test
    public void testIsHashExists_not() {
        //Given
        String userId = "doortts";

        //When
        boolean result = hashStringExist(userId);

        //Then
        assertThat(result).isFalse();
    }

    @Test
    public void testIsValidResetHash_notExists() {
        //Given
        String userId = "doortts";

        // When // Then
        assertThat(PasswordReset.isValidResetHash("sfalkjsd")).isFalse();
    }

    @Test
    public void testIsValidResetHash_expiredHash() {
        //Given
        String userId = "doortts";
        String hashString = PasswordReset.generateResetHash(userId);
        PasswordReset.addHashToResetTable(userId, hashString);
        Map<String, Long> timetable = getResetHashTimetable();
        DateTime current = new DateTime();

        //When //forced assume that time has passed
        timetable.remove(hashString);
        timetable.put(hashString, current.minusSeconds(PasswordReset.HASH_EXPIRE_TIME_MILLISEC +1000).getMillis());

        //Then
        assertThat(PasswordReset.isValidResetHash(hashString)).isFalse();
    }

    @Test
    public void testIsValidResetHash_evertythingOK() {
        //Given
        String userId = "doortts";
        String hashString = PasswordReset.generateResetHash(userId);
        PasswordReset.addHashToResetTable(userId, hashString);

        //When
        boolean result = PasswordReset.isValidResetHash(hashString);

        //Then
        assertThat(result).isTrue();
    }

    @Test
    public void testResetPassword() {
        //Given
        String userId = "doortts";
        String newPassword = "whffudy";
        String hashString = PasswordReset.generateResetHash(userId);
        PasswordReset.addHashToResetTable(userId, hashString);

        //When
        boolean result = PasswordReset.resetPassword(hashString, newPassword);

        //Then
        assertThat(result).isTrue();
        assertThat(UserApp.authenticateWithPlainPassword(userId, newPassword)).isNotNull();
    }

    @Test
    public void testResetPassword_wrongHash() {
        //Given
        String userId = "doortts";
        String newPassword = "whffudy";
        String hashString = PasswordReset.generateResetHash(userId);
        PasswordReset.addHashToResetTable(userId, hashString);

        //When
        String wrongHash = "sfdlkjafslfjsda";
        boolean result = PasswordReset.resetPassword(wrongHash, newPassword);

        //Then
        assertThat(result).isFalse();
        assertThat(UserApp.authenticateWithPlainPassword(userId, newPassword)).isNull();
    }

    private static boolean hashStringExist(String loginId) {
        return getHashString(loginId) != null;
    }

    private static String getHashString(String loginId) {
        return PasswordReset.resetHashMap.get(loginId);
    }

    /**
     * 특정 loginId의 hashString을 제거한다.
     *
     * testability를 위해 만들어졌을 뿐 특별히 사용되는 곳은 없다.
     *
     * @param loginId
     * @return 정상 제거 여부
     */
    public static boolean invalidateResetHash(String loginId) {
        String targetIdToReset = PasswordReset.resetHashMap.get(loginId);
        if (targetIdToReset == null){
            return false;
        }

        PasswordReset.resetHashMap.remove(loginId);
        return true;
    }

    /**
     * {@code HashMap<hashString, millisecond>}을 돌려준다.
     *
     * 현재는 test코드 이외에서는 특별히 사용할 곳이 없다.
     *
     * @return
     */
    private static Map<String, Long> getResetHashTimetable(){
        return PasswordReset.resetHashTimetable;
    }
}
