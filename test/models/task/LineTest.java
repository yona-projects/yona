package models.task;

import static org.fest.assertions.Assertions.assertThat;
import static play.test.Helpers.fakeApplication;
import static play.test.Helpers.running;

import org.codehaus.jackson.JsonNode;
import org.junit.Test;

public class LineTest {
    @Test
    public void toJson() throws Exception {
        running(fakeApplication(), new Runnable() {
            @Override
            public void run() {
                // Given
                Line line = Line.findById(1l);
                // When
                JsonNode json = line.toJSON();
                // Then
                assertThat(json.findValue("title").asText()).isEqualTo("Box");
                assertThat(json.findValue("cards").size()).isEqualTo(3);
            }
        });
    }
}
