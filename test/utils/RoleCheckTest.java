package utils;

import models.Role;
import org.junit.Test;
import static org.fest.assertions.Assertions.assertThat;
import models.ModelTest;
import models.enumeration.PermissionOperation;
import models.enumeration.PermissionResource;

public class RoleCheckTest extends ModelTest<Role>{
    @Test
    public void roleCheck() throws Exception {
        // Given
        String userSessionId1 = "1";
        String userSessionId2 = "2";
        Long projectId1 = 1l;
        Long projectId2 = 3l;
        // When

        boolean result1 = RoleCheck.roleCheck(userSessionId1, projectId1, PermissionResource.PROJECT, PermissionOperation.WRITE);
        boolean result2 = RoleCheck.roleCheck(userSessionId2, projectId2, PermissionResource.BOARD, PermissionOperation.READ);

        // Then
        assertThat(result1).isEqualTo(true);
        assertThat(result2).isEqualTo(false);
    }
}
