package models;

import java.util.List;

import org.junit.Test;
import static org.fest.assertions.Assertions.assertThat;import static org.fest.assertions.Assertions.assertThat;

public class PermissionTest extends ModelTest {
    @Test
    public void findById() throws Exception {
        // Given
        // When
        Permission permission = Permission.findById(1l);
        // Then
        assertThat(permission.resource).isEqualTo("board");
        assertThat(permission.operation).isEqualTo("read");
    }
    
    @Test
    public void findIdByResOp() throws Exception {
        // Given
        // When
        Long id = Permission.findIdByResOp("board", "write");
        // Then
        assertThat(id).isEqualTo(2l);
    }
    
    @Test
    public void findByResource() throws Exception {
        // Given
        // When
        List<Permission> permissions = Permission.findByResource("wiki");
        // Then
        assertThat(permissions.size()).isEqualTo(2);
        assertThat(permissions.get(0).operation).isEqualTo("read");
    }
}
