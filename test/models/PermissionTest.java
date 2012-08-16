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
}
