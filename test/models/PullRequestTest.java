package models;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.fest.assertions.Assertions.assertThat;

/**
 * @author Keesun Baik
 */
public class PullRequestTest {

    @Test
    public void addIssueEvent() {
        // Given
        Pattern issuePattern = Pattern.compile("#\\d+");

        // When
        Matcher m = issuePattern.matcher("blah blah #12, sdl #13 sldkfjsd");

        // Then
        List<String> numberTexts = new ArrayList<>();
        while(m.find()) {
            numberTexts.add(m.group());
        }
        assertThat(numberTexts.size()).isEqualTo(2);
    }

}
