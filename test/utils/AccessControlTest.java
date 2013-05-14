package utils;

import models.*;

import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;

import models.enumeration.Operation;

public class AccessControlTest extends ModelTest<Role>{
    @Test
    public void isAllowed_siteAdmin() {
        // Given
        User admin = User.findByLoginId("admin");
        Project nforge4java = Project.findByOwnerAndProjectName("hobi", "nForge4java");

        // When
        boolean canUpdate = AccessControl.isAllowed(admin, nforge4java.asResource(), Operation.UPDATE);
        boolean canRead = AccessControl.isAllowed(admin, nforge4java.asResource(), Operation.READ);
        boolean canDelete = AccessControl.isAllowed(admin, nforge4java.asResource(), Operation.DELETE);

        // Then
        assertThat(canRead).isEqualTo(true);
        assertThat(canUpdate).isEqualTo(true);
        assertThat(canDelete).isEqualTo(true);
    }

    @Test
    public void isAllowed_projectCreator() {
        // Given
        User hobi = User.findByLoginId("hobi");
        Project nforge4java = Project.findByOwnerAndProjectName("hobi", "nForge4java");

        // When
        boolean canUpdate = AccessControl.isAllowed(hobi, nforge4java.asResource(), Operation.UPDATE);
        boolean canRead = AccessControl.isAllowed(hobi, nforge4java.asResource(), Operation.READ);
        boolean canDelete = AccessControl.isAllowed(hobi, nforge4java.asResource(), Operation.DELETE);

        // Then
        assertThat(canRead).isEqualTo(true);
        assertThat(canUpdate).isEqualTo(true);
        assertThat(canDelete).isEqualTo(true);
    }

    @Test
    public void isAllowed_notAMember() {
        // Given
        User notMember = User.findByLoginId("nori");
        Project nforge4java = Project.findByOwnerAndProjectName("hobi", "nForge4java");

        // When
        boolean canUpdate = AccessControl.isAllowed(notMember, nforge4java.asResource(), Operation.UPDATE);
        boolean canRead = AccessControl.isAllowed(notMember, nforge4java.asResource(), Operation.READ);
        boolean canDelete = AccessControl.isAllowed(notMember, nforge4java.asResource(), Operation.DELETE);

        // Then
        assertThat(canRead).isEqualTo(true);
        assertThat(canUpdate).isEqualTo(false);
        assertThat(canDelete).isEqualTo(false);
    }
}
