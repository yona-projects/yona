package utils;

import models.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.Assert;

import play.test.Helpers;

import static org.fest.assertions.Assertions.assertThat;

import models.enumeration.Operation;
import models.enumeration.State;

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

    // AccessControl.isAllowed throws IllegalStateException if the resource
    // belongs to a project but the project is missing.
    @Test
    public void isAllowed_lostProject() {
        // Given
        User author = User.findByLoginId("nori");
        Project projectYobi = Project.findByOwnerAndProjectName("yobi", "projectYobi");
        Issue issue = new Issue();
        issue.setProject(projectYobi);
        issue.setTitle("hello");
        issue.setBody("world");
        issue.setAuthor(author);
        issue.state = State.OPEN;
        issue.save();

        // When
        issue.project = null;

        // Then
        try {
            AccessControl.isAllowed(author, issue.asResource(), Operation.READ);
            Assert.fail();
        } catch (IllegalStateException e) {
        }
    }
}
