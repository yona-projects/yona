package controllers;

import models.*;
import org.junit.*;
import play.Configuration;
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

    private Map<String, String> inmemoryWithCustomConfig(String additionalKey, String value) {
        Map<String, String> dbHelper = Helpers.inMemoryDatabase();
        Map<String, String> fakeConf = new HashMap<String, String>();
        for(String key: dbHelper.keySet()) {
            fakeConf.put(key, dbHelper.get(key));
        }
        fakeConf.put(additionalKey, value);
        return fakeConf;
    }

    @Before
    public void before() {
        Map<String, String> config = inmemoryWithCustomConfig("signup.require.confirm", "true");
        app = Helpers.fakeApplication(config);
        Helpers.start(app);

        admin = User.findByLoginId("admin");
        notAdmin = User.findByLoginId("doortts");
    }

    @After
    public void after() {
        Helpers.stop(app);
    }

    @Test @Ignore   //FixMe I don't know how to make a assert
    public void testToggleUserAccountLock() {
        //Given
        Map<String,String> data = new HashMap<String,String>();
        final String loginId= "doortts";
        data.put("loginId", loginId);

        User targetUser = User.findByLoginId(loginId);
        System.out.println(targetUser.isLocked);
        boolean currentIsLocked = targetUser.isLocked;

        //When
        callAction(
                controllers.routes.ref.SiteApp.toggleAccountLock(loginId),
                fakeRequest()
                        .withFormUrlEncodedBody(data)
                        .withSession("loginId", "admin")
        );
        //Then
        assertThat(User.findByLoginId(loginId).isLocked).isNotEqualTo(currentIsLocked);
    }
}
