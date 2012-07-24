package models;

import java.util.List;

import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;

/**
 * @author "Hwi Ahn"
 *
 */
public class RoleTest extends ModelTest {
    @Test
    public void findById() {
        // Given
        // When
        Role role = Role.findById(1l);
        // Then
        assertThat(role.name).isEqualTo("manager");
    }

    @Test
    public void findByName() {
        // Given
        // When
        Role role = Role.findByName("manager");
        // Then
        assertThat(role.id).isEqualTo(1l);
    }
    
    @Test
    public void getPermission() {
        // Given
        Role role = Role.findById(1l);
        // When
        List<Permission> permissions = role.getPermissions();
        // Then
        assertThat(permissions.size()).isEqualTo(5);
        assertThat(permissions.get(2).name).isEqualTo("milestone");
    }
}
