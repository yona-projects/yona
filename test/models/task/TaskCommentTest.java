package models.task;

import static org.fest.assertions.Assertions.assertThat;

import models.ModelTest;

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

}
