package controllers;

import models.User;
import org.junit.*;

import java.util.*;

import play.Configuration;
import play.GlobalSettings;
import play.i18n.Messages;
import play.mvc.*;
import play.test.Helpers;
import utils.JodaDateUtil;

import static play.test.Helpers.*;
import static org.fest.assertions.Assertions.*;

public class UserAppTest {
    @BeforeClass
    public static void beforeClass() {
        callAction(
                routes.ref.Application.init()
        );
    }

    @Test
    public void findById_doesntExist() {
        running(fakeApplication(Helpers.inMemoryDatabase()), new Runnable() {
            public void run() {
                //Given
                Map<String,String> data = new HashMap<String,String>();
                data.put("loginId", "nekure");

                //When
                Result result = callAction(
                        controllers.routes.ref.UserApp.isUserExist("nekure"),
                        fakeRequest().withFormUrlEncodedBody(data)
                );  // fakeRequest doesn't need here, but remains for example

                //Then
                assertThat(status(result)).isEqualTo(OK);
                assertThat(contentAsString(result)).contains("{\"isExist\":false}");
            }
        });
    }

    @Test
    public void findById_alreadyExist() {
        running(fakeApplication(Helpers.inMemoryDatabase()), new Runnable() {
            public void run() {
                //Given
                Map<String,String> data = new HashMap<String,String>();
                data.put("loginId", "hobi");

                //When
                Result result = callAction(
                        controllers.routes.ref.UserApp.isUserExist("hobi"),
                        fakeRequest().withFormUrlEncodedBody(data)
                ); // fakeRequest doesn't need here, but remains for example

                //Then
                assertThat(status(result)).isEqualTo(OK);
                assertThat(contentAsString(result)).contains("{\"isExist\":true}");
            }
        });
    }

    @Test
    public void isEmailExist() {
        running(fakeApplication(Helpers.inMemoryDatabase()), new Runnable() {
            public void run() {
                //Given
                //When
                Result result = callAction(
                        controllers.routes.ref.UserApp.isEmailExist("doortts@gmail.com")
                );

                //Then
                assertThat(status(result)).isEqualTo(OK);
                assertThat(contentAsString(result)).contains("{\"isExist\":true}");
            }
        });
    }

    @Test
    public void login_notComfirmedUser() {
        Map<String, String> fakeConf = inmemoryWithCustomConfig("signup.require.confirm", "true");

        running(fakeApplication(fakeConf), new Runnable() {
            public void run() {
                //Given
                User user = new User(-31l);
                user.loginId = "fakeUser";
                user.email = "fakeuser@fake.com";
                user.name = "racoon";
                user.password = "somefakepassword";
                user.createdDate = JodaDateUtil.now();
                user.isLocked = true;
                user.save();

                Map<String, String> data = new HashMap<String,String>();
                data.put("loginId", user.loginId);
                data.put("password", user.password);

                //When
                Result result = callAction(
                        controllers.routes.ref.UserApp.login(),
                        fakeRequest().withFormUrlEncodedBody(data)
                );

                //Then
                assertThat(status(result)).describedAs("result status should '303 see other'").isEqualTo(303);
            }
        });
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

    @Test
    public void newUser_confirmSignUpMode() {
        Map<String, String> map = inmemoryWithCustomConfig("signup.require.confirm", "true");
        running(fakeApplication(map), new Runnable() {
            public void run() {
                //Given
                final String loginId = "somefakeuserid";
                Map<String, String> data = new HashMap<String,String>();
                data.put("loginId", loginId);
                data.put("password", "somefakepassword");
                data.put("email", "fakeuser@fake.com");
                data.put("name", "racoon");

                //When
                Result result = callAction(
                        controllers.routes.ref.UserApp.newUser(),
                        fakeRequest().withFormUrlEncodedBody(data)
                );

                //Then
                assertThat(status(result)).describedAs("result status should '303 see other'").isEqualTo(303);
            }
        });
    }
}
