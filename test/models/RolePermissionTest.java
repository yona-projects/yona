package models;

import java.util.List;

import org.junit.Test;
import static org.fest.assertions.Assertions.assertThat;

public class RolePermissionTest extends ModelTest {
    @Test
    public void findPermissionsByRole() throws Exception {
        // Given
        // When
        List<Permission> permissions = RolePermission.findPermissionsByRole(1l);
        // Then
        assertThat(permissions.size()).isEqualTo(11);
        assertThat(permissions.get(0).resource).isEqualTo("board");
    }
    
    @Test
    public void findById() throws Exception {
        // Given
        // When
        RolePermission rolePermission = RolePermission.findById(1l);
        // Then
        assertThat(rolePermission.role.id).isEqualTo(1l);
        assertThat(rolePermission.permission.id).isEqualTo(1l);
    }
    
    @Test
    public void findRoleById() throws Exception {
        // Given
        // When
        Role role = RolePermission.findRoleById(1l);
        // Then
        assertThat(role.name).isEqualTo("manager");
    }
    
    @Test
    public void findPermissionsById() throws Exception {
        // Given
        // When
        List<Permission> permissions = RolePermission.findPermissionsById(1l);
        // Then
        assertThat(permissions.size()).isEqualTo(11);
    }
    
    @Test
    public void findByRole() throws Exception {
        // Given
        // When
        // Then
        assertThat(RolePermission.findByRole(1l).size()).isEqualTo(11); 
    }
}
