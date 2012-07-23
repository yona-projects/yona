package models;

import org.junit.Test;
import static org.fest.assertions.Assertions.assertThat;

public class ProjectUserTest extends ModelTest {
    @Test
    public void findRolebyIds() throws Exception {
        // Given
        // When
        Role role = ProjectUser.findRolebyIds(1l, 1l);
        // Then
        assertThat(role.id).isEqualTo(1l);
    }

    @Test
    public void create() throws Exception {
        // Given
        // When
        ProjectUser.create(1l, 3l, 2l);
        // Then
        assertThat(Role.findById(ProjectUser.findRolebyIds(1l, 3l).id).name)
                .isEqualTo("member");
    }
}
