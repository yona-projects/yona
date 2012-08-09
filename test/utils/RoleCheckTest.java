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
        // When
        boolean result1 = RoleCheck.roleCheck("1", 1l, PermissionResource.PROJECT.resource(), PermissionOperation.WRITE.operation());
        boolean result2 = RoleCheck.roleCheck("2", 3l, PermissionResource.BOARD.resource(), PermissionOperation.READ.operation());
        // Then
        assertThat(result1).isEqualTo(true);
        assertThat(result2).isEqualTo(false);
    }
}
