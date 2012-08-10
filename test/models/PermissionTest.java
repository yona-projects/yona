package models;

import models.enumeration.PermissionOperation;
import models.enumeration.PermissionResource;

import org.junit.Test;
import static org.fest.assertions.Assertions.assertThat;

public class PermissionTest extends ModelTest<Permission> {    
    @Test
    public void permissionCheck() throws Exception {
        // Given
        // When
        // Then
        assertThat(Permission.permissionCheck(2l, 1l, PermissionResource.PROJECT, PermissionOperation.WRITE)).isEqualTo(true);
        assertThat(Permission.permissionCheck(2l, 2l, PermissionResource.PROJECT, PermissionOperation.WRITE)).isEqualTo(false);
    }
    
    @Test
    public void findPermissionsByRole() throws Exception {
        // Given
        // When
        // Then
        assertThat(Permission.findPermissionsByRole(1l).size()).isEqualTo(11);
    }
}
