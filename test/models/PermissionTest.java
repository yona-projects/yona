package models;

import models.enumeration.Operation;
import models.enumeration.Resource;

import org.junit.Test;

import com.avaje.ebean.Ebean;

import static org.fest.assertions.Assertions.assertThat;

public class PermissionTest extends ModelTest<Permission> {    
    @Test
    public void permissionCheck() throws Exception {
        // Given
        Long hobi = 2l;
        Long nForge4java = 1l;
        Long jindo = 2l;
        // When
        // Then
        assertThat(Permission.permissionCheck(hobi, nForge4java, Resource.PROJECT_SETTING, Operation.WRITE)).isEqualTo(true);
        assertThat(Permission.permissionCheck(hobi, jindo, Resource.PROJECT_SETTING, Operation.WRITE)).isEqualTo(false);
    }
    
    @Test
    public void findPermissionsByRole() throws Exception {
        // Given
        // When
        // Then
        assertThat(Permission.findPermissionsByRole(1l).size()).isEqualTo(63);
    }
    
    @Test
    public void permissionCheckByRole() throws Exception {
        // Given
        // When
        boolean result1 = Permission.permissionCheckByRole(Role.ANONYMOUS, Resource.BOARD_POST, Operation.READ);
        boolean result2 = Permission.permissionCheckByRole(Role.ANONYMOUS, Resource.BOARD_POST, Operation.DELETE);
        // Then
        assertThat(result1).isEqualTo(true);
        assertThat(result2).isEqualTo(false);
    }
}
