package models;

import java.util.List;

import org.junit.Test;
import static org.fest.assertions.Assertions.assertThat;

/**
 * @author "Hwi Ahn"
 *
 */
public class ProjectUserTest extends ModelTest<ProjectUser> {
    @Test
    public void findByIds() throws Exception {
        // Given
        // When
        Role role = ProjectUser.findByIds(1l, 1l).role;
        // Then
        assertThat(role.id).isEqualTo(1l);
    }

    @Test
    public void create() throws Exception {
        // Given
        // When
        ProjectUser.create(2l, 3l, 2l);
        // Then
        assertThat(ProjectUser.findRoleByIds(2l, 3l).name)
                .isEqualTo("member");
    }

    @Test
    public void update() throws Exception {
        // Given
        // When
        ProjectUser.update(1l, 2l, 1l);
        // Then
        assertThat(Role.findById(ProjectUser.findByIds(1l, 2l).role.id).name)
                .isEqualTo("manager");
    }
    
    @Test
    public void findProjectsByOwner() throws Exception {
        // Given
        // When
        List<Project> projects = ProjectUser.findProjectsByOwner(1l);
        // Then
        assertThat(projects.size()).isEqualTo(3);
    }
    
    @Test
    public void findUsersByProject() throws Exception {
        // Given
        // When
        List<User> users = ProjectUser.findUsersByProject(2l);
        // Then
        assertThat(users.size()).isEqualTo(1);
    }
    
    @Test
    public void findRoleByIds() throws Exception {
        // Given
        // When
        Role role = ProjectUser.findRoleByIds(1l, 1l);

        // Then
        assertThat(role).isNotNull();
    }
    
    @Test
    public void delete() throws Exception {
        // Given
        // When
        ProjectUser.delete(2l, 1l);
        // Then
        assertThat(ProjectUser.findByIds(2l, 1l)).isNull();        
    }
    
    @Test
    public void existManager() throws Exception {
        // Given
        // When
        // Then
        assertThat(ProjectUser.isManager(1l)).isEqualTo(false);
    }
    
}
