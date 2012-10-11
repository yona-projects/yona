package models.task;

import static org.fest.assertions.Assertions.assertThat;
import models.ModelTest;
import models.Project;

import org.junit.Test;

import controllers.ProjectApp;

public class TaskBoardTest extends ModelTest<TaskBoard> {

    @Test
    public void create() throws Exception {
        // Given
        Project project = ProjectApp.getProject("hobi", "nForge4java");
        // When
        TaskBoard taskboard = TaskBoard.create(project);
        // Then
        assertThat(taskboard.id).isNotNull();
        assertThat(taskboard.lines.size()).isEqualTo(5);
        assertThat(taskboard.labels.size()).isEqualTo(10);
        assertThat(taskboard.project).isEqualTo(project);
    }

    @Test
    public void findByProject() throws Exception {
        // Given
        Project project = ProjectApp.getProject("hobi", "nForge4java");
        // When
        TaskBoard taskboard = TaskBoard.findByProject(project);
        // Then
        assertThat(taskboard).isNotNull();
    }

}
