package models;

import java.util.List;

import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;

/**
 * @author "Hwi Ahn"
 *
 */
public class RoleTest extends ModelTest<Role> {
    @Test
    public void findById() throws Exception {
        // Given
        // When
        Role role = Role.findById(1l);
        // Then
        assertThat(role.name).isEqualTo("manager");
    }

    @Test
    public void findByName() throws Exception {
        // Given
        // When
        Role role = Role.findByName("manager");
        // Then
        assertThat(role.id).isEqualTo(1l);
    }
    
    @Test
    public void getPermission() throws Exception {
        // Given
        Role role = Role.findById(1l);
        // When
        List<Permission> permissions = role.getPermissions();
        // Then
        assertThat(permissions.size()).isEqualTo(5);
        assertThat(permissions.get(2).name).isEqualTo("milestone");
    }
    
    @Test
    public void getAllProjectRoles() throws Exception {
        // Given
        // When
        List<Role> roles = Role.getAllProjectRoles();
        // Then
        assertThat(roles.contains(Role.findByName("siteManager"))).isEqualTo(false);
        assertThat(roles.contains(Role.findByName("manager"))).isEqualTo(true);
    }
}
