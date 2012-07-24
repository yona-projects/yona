package utils;

import org.junit.Test;
import static org.fest.assertions.Assertions.assertThat;
import models.ModelTest;
import models.ProjectUser;
import models.Role;

public class RoleCheckTest extends ModelTest{
    @Test
    public void roleCheck() {
        // Given
        // When
        boolean result1 = RoleCheck.roleCheck("1", RoleCheck.PERMISSION_PROJ_SETTING, "4");
        boolean result2 = RoleCheck.roleCheck("4", RoleCheck.PERMISSION_MILESTONE, "1");
        // Then
        assertThat(result1).isEqualTo(false);
        assertThat(result2).isEqualTo(true);
    }
    
    @Test
    public void roleGrant() {
        // Given
        // When
        RoleCheck.roleGrant(1l, "manager", 4l);
        Role role = ProjectUser.findRoleByIds(1l, 4l);
        // Then
        assertThat(role.id).isEqualTo(1l);
    }
}
