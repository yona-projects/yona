package controllers;

import models.*;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import play.test.FakeApplication;
import play.test.Helpers;

import java.util.HashMap;
import java.util.Map;

import static org.fest.assertions.Assertions.assertThat;
import static play.test.Helpers.callAction;
import static play.test.Helpers.fakeRequest;

public class SiteAppTest {
    protected static FakeApplication app;
    private User admin;
    private User notAdmin;

    @BeforeClass
    public static void beforeClass() {
        callAction(
                routes.ref.Application.init()
        );
    }

    @Before
    public void before() {
        app = Helpers.fakeApplication(Helpers.inMemoryDatabase());
        Helpers.start(app);

        admin = User.findByLoginId("admin");
        notAdmin = User.findByLoginId("doortts");
    }

    @After
    public void after() {
        Helpers.stop(app);
    }

    @Test
    public void testToggleUserAccountLock() {
        //Given

        //Given
        Map<String,String> data = new HashMap<String,String>();
        final String loginId= "doortts";
        data.put("loginId", loginId);

        User targetUser = User.findByLoginId(loginId);
        boolean currentIsLocked = targetUser.isLocked;

        //When
        return callAction(
                controllers.routes.ref.SiteApp.toggleAccountLock(),
                fakeRequest()
                        .withFormUrlEncodedBody(data)
                        .withSession(UserApp.SESSION_USERID, targetUser.id)
        );
        //Then
        assertThat(User.findByLoginId(loginId).isLocked).isNotEqualTo(currentIsLocked);
    }
}
