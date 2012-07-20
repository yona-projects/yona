package models;

import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;

/**
 * @author "Hwi Ahn"
 *
 */
public class ProjectRoleTest extends ModelTest {
    @Test
    public void findById() {
        // Given
        // When
        ProjectRole projectRole = ProjectRole.findById(1l);
        // Then
        assertThat(projectRole.id).isEqualTo(1l);
    }

    @Test
    public void findByName() {
        // Given
        // When
        ProjectRole projectRole = ProjectRole.findByName("manager");
        // Then
        assertThat(projectRole.id).isEqualTo(1l);
    }
}
