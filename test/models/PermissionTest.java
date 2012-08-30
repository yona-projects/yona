package models;

import models.enumeration.Operation;
import models.enumeration.Resource;
import models.enumeration.RoleType;

import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;

public class PermissionTest extends ModelTest<Permission> {    
    @Test
    public void hasPermission() throws Exception {
        // Given
        Long hobi = 2l;
        Long nForge4java = 1l;
        Long jindo = 2l;
        RoleType anonymous = RoleType.ANONYMOUS;
        // When
        // Then
        assertThat(Permission.hasPermission(hobi, nForge4java, Resource.PROJECT_SETTING, Operation.WRITE)).isEqualTo(true);
        assertThat(Permission.hasPermission(hobi, jindo, Resource.PROJECT_SETTING, Operation.WRITE)).isEqualTo(false);
        assertThat(Permission.hasPermission(anonymous, Resource.BOARD_POST, Operation.READ)).isEqualTo(true);
        assertThat(Permission.hasPermission(anonymous, Resource.BOARD_POST, Operation.DELETE)).isEqualTo(false);
    }
    
    @Test
    public void findPermissionsByRole() throws Exception {
        // Given
        // When
        // Then
        assertThat(Permission.findPermissionsByRole(1l).size()).isEqualTo(63);
    }
}
