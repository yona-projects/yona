package utils;

import models.*;
import models.Posting;

import org.junit.Test;

import controllers.UserApp;
import static org.fest.assertions.Assertions.assertThat;

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
}
