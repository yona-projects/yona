package utils;

import org.junit.Test;
import static org.fest.assertions.Assertions.assertThat;
import models.ModelTest;

public class RoleCheckTest extends ModelTest{
    @Test
    public void roleCheck() throws Exception {
        // Given
        // When
        boolean result1 = RoleCheck.roleCheck("1", 1l, "project", "setting");
        boolean result2 = RoleCheck.roleCheck("1", 3l, "board", "read");
        // Then
        assertThat(result1).isEqualTo(true);
        assertThat(result2).isEqualTo(false);
    }
}
