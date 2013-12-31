package controllers;

import models.User;
import models.enumeration.UserState;
import org.junit.*;

import java.util.*;

import play.mvc.*;
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
        running(support.Helpers.makeTestApplication(), new Runnable() {
            @Override
            public void run() {
                //Given
                Map<String,String> data = new HashMap<>();
                data.put("loginId", "nekure");

                //When
                Result result = callAction(
                        controllers.routes.ref.UserApp.isUserExist("nekure"),
                        fakeRequest().withFormUrlEncodedBody(data)
                );  // fakeRequest doesn't need here, but remains for example

                //Then
                assertThat(status(result)).isEqualTo(OK);
                assertThat(contentAsString(result)).contains("\"isExist\":false");
                assertThat(contentType(result)).contains("json");
            }
        });
    }

    @Test
    public void findById_alreadyExist() {
        running(support.Helpers.makeTestApplication(), new Runnable() {
            @Override
            public void run() {
                //Given
                Map<String,String> data = new HashMap<>();
                data.put("loginId", "yobi");

                //When
                Result result = callAction(
                        controllers.routes.ref.UserApp.isUserExist("yobi"),
                        fakeRequest().withFormUrlEncodedBody(data)
                ); // fakeRequest doesn't need here, but remains for example

                //Then
                assertThat(status(result)).isEqualTo(OK);
                assertThat(contentAsString(result)).contains("\"isExist\":true");
                assertThat(contentType(result)).contains("json");
            }
        });
    }

    @Test
    public void isEmailExist() {
        running(support.Helpers.makeTestApplication(), new Runnable() {
            @Override
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
        Map<String, String> config = support.Helpers.makeTestConfig();
        config.put("signup.require.confirm", "true");

        running(support.Helpers.makeTestApplication(config), new Runnable() {
            @Override
            public void run() {
                //Given
                User user = new User(-31l);
                user.loginId = "fakeUser";
                user.email = "fakeuser@fake.com";
                user.name = "racoon";
                user.password = "somefakepassword";
                user.createdDate = JodaDateUtil.now();
                user.state = UserState.LOCKED;
                user.save();

                Map<String, String> data = new HashMap<>();
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

    @Test
    public void newUser_confirmSignUpMode() {
        Map<String, String> config = support.Helpers.makeTestConfig();
        config.put("signup.require.confirm", "true");

        running(support.Helpers.makeTestApplication(config), new Runnable() {
            @Override
            public void run() {
                //Given
                final String loginId = "somefakeuserid";
                Map<String, String> data = new HashMap<>();
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

    @Test
    public void findById_reserved() {
        running(support.Helpers.makeTestApplication(), new Runnable() {
            @Override
            public void run() {
                //Given
                Map<String,String> data = new HashMap<>();
                data.put("loginId", "messages.js");

                //When
                Result result = callAction(controllers.routes.ref.UserApp.isUserExist("messages.js"));

                //Then
                assertThat(status(result)).isEqualTo(OK);
                assertThat(contentAsString(result)).contains("\"isReserved\":true");
                assertThat(contentType(result)).contains("json");
            }
        });
    }
}
