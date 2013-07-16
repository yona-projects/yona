package utils;

import models.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import play.test.Helpers;

import static org.fest.assertions.Assertions.assertThat;

import models.enumeration.Operation;

public class AccessControlTest extends ModelTest<Role>{
    @Before
    public void before() {
        app = Helpers.fakeApplication(support.Config.makeTestConfig());
        Helpers.start(app);
    }

    @After
    public void after() {
        Helpers.stop(app);
    }

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
