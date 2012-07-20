package models;

import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;

/**
 * @author "Hwi Ahn"
 *
 */
public class ProjectUserTest extends ModelTest{
    
    @Test
    public void findById() {
        // Given
        // When
        ProjectRole projectRole = ProjectUser.findById(1l, 1l);
        // Then
        assertThat(projectRole.id).isEqualTo(1l);
    }
}
