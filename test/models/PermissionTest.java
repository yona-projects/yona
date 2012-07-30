package models;

import org.junit.Test;
import static org.fest.assertions.Assertions.assertThat;import static org.fest.assertions.Assertions.assertThat;

public class PermissionTest extends ModelTest<Permission> {
    @Test
    public void findByName() {
        // Given
        // When
        Permission permission = Permission.findByName("project.setting");
        // Then
        assertThat(permission.id).isEqualTo(4l);
    }
}
