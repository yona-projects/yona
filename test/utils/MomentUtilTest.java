package utils;

import org.joda.time.DateTime;
import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;

/**
 * @author Keesun Baik
 */
public class MomentUtilTest {

    @Test
    public void moment() {
        // Given
        DateTime now = new DateTime();
        DateTime oneDayBefore = now.minusDays(1);

        // When
        JSInvocable invocable = MomentUtil.newMoment(oneDayBefore.getMillis());
        String result = invocable.invoke("fromNow");

        // Then
        assertThat(result).isEqualTo("하루 전");
    }
}