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
        Project projectYobi = Project.findByOwnerAndProjectName("yobi", "projectYobi");

        // When
        boolean canUpdate = AccessControl.isAllowed(admin, projectYobi.asResource(), Operation.UPDATE);
        boolean canRead = AccessControl.isAllowed(admin, projectYobi.asResource(), Operation.READ);
        boolean canDelete = AccessControl.isAllowed(admin, projectYobi.asResource(), Operation.DELETE);

        // Then
        assertThat(canRead).isEqualTo(true);
        assertThat(canUpdate).isEqualTo(true);
        assertThat(canDelete).isEqualTo(true);
    }

    @Test
    public void isAllowed_projectCreator() {
        // Given
        User yobi = User.findByLoginId("yobi");
        Project projectYobi = Project.findByOwnerAndProjectName("yobi", "projectYobi");

        // When
        boolean canUpdate = AccessControl.isAllowed(yobi, projectYobi.asResource(), Operation.UPDATE);
        boolean canRead = AccessControl.isAllowed(yobi, projectYobi.asResource(), Operation.READ);
        boolean canDelete = AccessControl.isAllowed(yobi, projectYobi.asResource(), Operation.DELETE);

        // Then
        assertThat(canRead).isEqualTo(true);
        assertThat(canUpdate).isEqualTo(true);
        assertThat(canDelete).isEqualTo(true);
    }

    @Test
    public void isAllowed_notAMember() {
        // Given
        User notMember = User.findByLoginId("nori");
        Project projectYobi = Project.findByOwnerAndProjectName("yobi", "projectYobi");

        // When
        boolean canUpdate = AccessControl.isAllowed(notMember, projectYobi.asResource(), Operation.UPDATE);
        boolean canRead = AccessControl.isAllowed(notMember, projectYobi.asResource(), Operation.READ);
        boolean canDelete = AccessControl.isAllowed(notMember, projectYobi.asResource(), Operation.DELETE);

        // Then
        assertThat(canRead).isEqualTo(true);
        assertThat(canUpdate).isEqualTo(false);
        assertThat(canDelete).isEqualTo(false);
    }
}
