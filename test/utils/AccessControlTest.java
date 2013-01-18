package utils;

import models.Issue;
import models.Post;
import models.Project;
import models.Role;
import models.User;

import org.junit.Test;

import controllers.UserApp;
import static org.fest.assertions.Assertions.assertThat;
import models.ModelTest;
import models.enumeration.Operation;
import play.db.ebean.Model.Finder;

public class AccessControlTest extends ModelTest<Role>{
    @Test
    public void isAllowed() {
        // Given
        User admin = User.findByLoginId("admin");
        User hobi = User.findByLoginId("hobi");
        Project nforge4java = Project.findByNameAndOwner("hobi", "nForge4java");
        Project jindo = Project.findByNameAndOwner("k16wire", "Jindo");

        // When
        boolean result1 = AccessControl.isAllowed(admin, nforge4java.asResource(), Operation.UPDATE);
        boolean result2 = AccessControl.isAllowed(hobi, jindo.asResource(), Operation.UPDATE);
        boolean result3 = AccessControl.isAllowed(UserApp.anonymous, jindo.asResource(), Operation.READ);

        // Then
        assertThat(result1).isEqualTo(true);
        assertThat(result2).isEqualTo(false);
        assertThat(result3).isEqualTo(false);
    }

    @Test
    public void isAuthor() throws Exception {
        // Given
        Long userId1 = 2l;
        Long resourceId1 = 1l;
        Finder<Long, Post> postFinder = new Finder<Long, Post>(Long.class, Post.class);
        Long userId2 = 3l;
        Long resourceId2 = 1l;
        Finder<Long, Issue> issueFinder = new Finder<Long, Issue>(Long.class, Issue.class);
        // When
        boolean result1 = AccessControl.isAuthor(userId1, resourceId1, postFinder);
        boolean result2 = AccessControl.isAuthor(userId2, resourceId2, issueFinder);
        // Then
        assertThat(result1).isEqualTo(true);
        assertThat(result2).isEqualTo(false);
    }
}
