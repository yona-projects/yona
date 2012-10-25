package models.task;

import static org.fest.assertions.Assertions.assertThat;
import static play.test.Helpers.fakeApplication;
import static play.test.Helpers.running;

import models.ModelTest;

import org.codehaus.jackson.JsonNode;
import org.junit.Test;

public class TaskCommentTest extends ModelTest<TaskComment>{
    @Test
    public void findById() throws Exception {
        //Given
        //When
        TaskComment comment = TaskComment.findById(1l);
        //Then
        assertThat(comment).isNotNull();
        assertThat(comment.body).isEqualTo("test comment");
    }
    @Test
    public void toJSON() throws Exception {
        //Given
        TaskComment comment = TaskComment.findById(1l);
        //When
        JsonNode json = comment.toJSON();
        //Then
        assertThat(json.get("body").asText()).isEqualTo("test comment");
    }

}
