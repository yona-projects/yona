package models;

import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.Fail.fail;

import controllers.UserApp;
import org.joda.time.DateTime;
import org.junit.*;

import java.util.Map;

/**
 * User: doortts
 * Date: 4/4/13
 * Time: 5:11 PM
 */
public class PasswordResetTest extends ModelTest<PasswordReset> {

    @Before
    public void setUp() throws Exception {
        PasswordReset.resetHashTable();
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
        assertThat(PasswordReset.getResetHash(userId)).isEqualTo(hashString);
    }

    @Test
    public void testInvalidateResetHash() throws Exception {
        //Given
        String userId = "doortts";
        String hashString = PasswordReset.generateResetHash(userId);
        PasswordReset.addHashToResetTable(userId, hashString);

        //When
        boolean result = PasswordReset.invalidateResetHash(userId);

        //Then
        assertThat(result).isTrue();
        assertThat(PasswordReset.getResetHash(userId)).isNull();
    }
    
    @Test
    public void testIsHashExists() {
        //Given
        String userId = "doortts";
        String hashString = PasswordReset.generateResetHash(userId);
        PasswordReset.addHashToResetTable(userId, hashString);

        //When
        boolean result = PasswordReset.isHashExist(userId);

        //Then
        assertThat(result).isTrue();
    }

    @Test
    public void testIsHashExists_not() {
        //Given
        String userId = "doortts";

        //When
        boolean result = PasswordReset.isHashExist(userId);

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
        Map<String, DateTime> timetable = PasswordReset.getResetHashTimetable();
        DateTime current = new DateTime();

        //When //forced assume that time has passed
        timetable.remove(hashString);
        timetable.put(hashString, current.minusSeconds(PasswordReset.HASH_EXPIRE_TIME_SEC+1));

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
    public void testRemoveResetHash() {
        //Given
        String userId = "doortts";
        String hashString = PasswordReset.generateResetHash(userId);
        PasswordReset.addHashToResetTable(userId, hashString);

        //When
        PasswordReset.removeResetHash(hashString);

        //Then
        assertThat(PasswordReset.isValidResetHash(hashString)).isFalse();
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
}
