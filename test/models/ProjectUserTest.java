package models;

import java.util.List;

import org.junit.Test;
import static org.fest.assertions.Assertions.assertThat;

/**
 * @author "Hwi Ahn"
 *
 */
public class ProjectUserTest extends ModelTest {
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
        assertThat(ProjectUser.findByIds(2l, 3l).role.id)
                .isEqualTo(2l);
    }
    
    @Test
    public void assignRole() throws Exception {
        // Given
        // When
        ProjectUser.assignRole(1l, 1l, 2l);
        ProjectUser.assignRole(1l, 3l, 2l);
        // Then
        assertThat(ProjectUser.findByIds(1l, 1l).role.id).isEqualTo(2l);
        assertThat(ProjectUser.findByIds(1l, 3l).role.id).isEqualTo(2l);
    }

    @Test
    public void findProjectsByOwner() throws Exception {
        // Given
        // When
        List<Project> projects = ProjectUser.findProjectsByOwner(1l);
        // Then
        assertThat(projects.size()).isEqualTo(2);
    }
    
    @Test
    public void findUsersByProject() throws Exception {
        // Given
        // When
        List<User> users = ProjectUser.findUsersByProject(2l);
        // Then
        assertThat(users.size()).isEqualTo(2);
    }
    
    @Test
    public void findRoleByIds() throws Exception {
        // Given
        // When
        Role role = ProjectUser.findRoleByIds(1l, 1l);
        // Then
        assertThat(role.name).isEqualTo("manager");
    }

    @Test
    public void isManager() throws Exception {
        // Given
        ProjectUser.assignRole(1l, 3l, 1l);
        // When
        // Then
        assertThat(ProjectUser.isManager(1l)).isEqualTo(false);
        assertThat(ProjectUser.isManager(3l)).isEqualTo(true);
    }
    
    @Test
    public void isMember() throws Exception {
        // Given
        // When
        // Then
        assertThat(ProjectUser.isMember(1l, 2l)).isEqualTo(true);
        assertThat(ProjectUser.isMember(1l, 3l)).isEqualTo(false);
    }
    
    @Test
    public void findPermissionsByIds() throws Exception {
        // Given
        // When
        List<Permission> permission = ProjectUser.findPermissionsByIds(1l, 1l);
        // Then
        assertThat(permission.size()).isEqualTo(11);
    }
    
    @Test
    public void options() throws Exception {
        // Given
        // When
        // Then
        assertThat(ProjectUser.options(1l).containsValue("k16wire")).isEqualTo(true);
    }
    
    @Test
    public void permissionCheck() throws Exception {
        // Given
        // When
        // Then
        assertThat(ProjectUser.permissionCheck(1l, 1l, "project", "setting")).isEqualTo(true);
        assertThat(ProjectUser.permissionCheck(1l, 2l, "project", "setting")).isEqualTo(false);
    }
    
    @Test
    public void findMemberListByProject() throws Exception {
        // Given
        // When
        List<ProjectUser> projectUsers = ProjectUser.findMemberListByProject(1l);
        // Then
        assertThat(projectUsers.size()).isEqualTo(2);
        assertThat(projectUsers.get(0).user.id).isEqualTo(1l);
        assertThat(projectUsers.get(0).user.loginId).isEqualTo("hobi");
        assertThat(projectUsers.get(0).role.name).isEqualTo("manager");
    }
}
