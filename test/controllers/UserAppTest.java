package controllers;

import models.User;
import models.enumeration.UserState;

import org.junit.*;

import java.util.*;

import play.mvc.*;
import play.mvc.Http.*;
import support.ContextTest;
import utils.JodaDateUtil;

import static play.test.Helpers.*;
import static org.fest.assertions.Assertions.*;
import static org.fest.assertions.MapAssert.entry;
import static org.mockito.Mockito.*;

public class UserAppTest extends ContextTest {
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

    @Test
    public void authenticateWithPlainPassword() {
        running(support.Helpers.makeTestApplication(), new Runnable() {
            @Override
            public void run() {
                // Given
                String loginId = "kjkmadness";
                String password = "pass";

                // When
                User user = UserApp.authenticateWithPlainPassword(loginId, password);

                // Then
                assertThat(user).isNotNull();
                assertThat(user.isAnonymous()).isFalse();
                assertThat(user.loginId).isEqualTo(loginId);
            }
        });
    }

    @Test
    public void authenticateWithPlainPasswordWrongPassword() {
        running(support.Helpers.makeTestApplication(), new Runnable() {
            @Override
            public void run() {
                // Given
                String loginId = "kjkmadness";
                String password = "wrong";

                // When
                User user = UserApp.authenticateWithPlainPassword(loginId, password);

                // Then
                assertThat(user).isNotNull();
                assertThat(user.isAnonymous()).isTrue();
            }
        });
    }

    @Test
    public void authenticateWithPlainPasswordNotExist() {
        running(support.Helpers.makeTestApplication(), new Runnable() {
            @Override
            public void run() {
                // Given
                String loginId = "notexist";
                String password = "pass";

                // When
                User user = UserApp.authenticateWithPlainPassword(loginId, password);

                // Then
                assertThat(user).isNotNull();
                assertThat(user.isAnonymous()).isTrue();
            }
        });
    }

    @Test
    public void authenticateWithHashedPassword() {
        running(support.Helpers.makeTestApplication(), new Runnable() {
            @Override
            public void run() {
                // Given
                String loginId = "kjkmadness";
                String password = "ckJUVVaOHhRDNqwbeF+j4RNqXzodXO95+aQRIbJnDK4=";

                // When
                User user = UserApp.authenticateWithHashedPassword(loginId, password);

                // Then
                assertThat(user).isNotNull();
                assertThat(user.isAnonymous()).isFalse();
                assertThat(user.loginId).isEqualTo(loginId);
            }
        });
    }

    @Test
    public void authenticateWithHashedPasswordWrongPassword() {
        running(support.Helpers.makeTestApplication(), new Runnable() {
            @Override
            public void run() {
                // Given
                String loginId = "kjkmadness";
                String password = "wrong";

                // When
                User user = UserApp.authenticateWithHashedPassword(loginId, password);

                // Then
                assertThat(user).isNotNull();
                assertThat(user.isAnonymous()).isTrue();
            }
        });
    }

    @Test
    public void authenticateWithHashedPasswordNotExist() {
        running(support.Helpers.makeTestApplication(), new Runnable() {
            @Override
            public void run() {
                // Given
                String loginId = "notexist";
                String password = "ckJUVVaOHhRDNqwbeF+j4RNqXzodXO95+aQRIbJnDK4=";

                // When
                User user = UserApp.authenticateWithHashedPassword(loginId, password);

                // Then
                assertThat(user).isNotNull();
                assertThat(user.isAnonymous()).isTrue();
            }
        });
    }

    @Test
    public void login() {
        running(support.Helpers.makeTestApplication(), new Runnable() {
            @Override
            public void run() {
                // Given
                String loginId = "kjkmadness";
                String password = "pass";
                User user = User.findByLoginId(loginId);
                Map<String, String> data = new HashMap<>();
                data.put("loginId", loginId);
                data.put("password", password);

                // When
                Result result = callAction(controllers.routes.ref.UserApp.login(), fakeRequest()
                        .withFormUrlEncodedBody(data));

                // Then
                assertThat(status(result)).isEqualTo(SEE_OTHER);
                assertThat(header(LOCATION, result)).isEqualTo(routes.Application.index().url());
                assertThat(session(result)).includes(
                        entry(UserApp.SESSION_USERID, String.valueOf(user.id)),
                        entry(UserApp.SESSION_LOGINID, user.loginId),
                        entry(UserApp.SESSION_USERNAME, user.name));
            }
        });
    }

    @Test
    public void loginWrongPassword() {
        running(support.Helpers.makeTestApplication(), new Runnable() {
            @Override
            public void run() {
                // Given
                String loginId = "kjkmadness";
                String password = "wrong";
                Map<String, String> data = new HashMap<>();
                data.put("loginId", loginId);
                data.put("password", password);

                // When
                Result result = callAction(controllers.routes.ref.UserApp.login(), fakeRequest()
                        .withFormUrlEncodedBody(data));

                // Then
                assertThat(status(result)).isEqualTo(SEE_OTHER);
                assertThat(header(LOCATION, result)).isEqualTo(routes.UserApp.loginForm().url());
                assertThat(session(result)).isEmpty();
            }
        });
    }

    @Test
    public void currentUserContext() {
        running(support.Helpers.makeTestApplication(), new Runnable() {
            @Override
            public void run() {
                // Given
                User expected = User.find.byId(1L);
                context().withArg(UserApp.TOKEN_USER, expected);

                // When
                User user = UserApp.currentUser();

                // Then
                assertThat(user).isEqualTo(expected);
            }
        });
    }

    @Test
    public void currentUserSession() {
        running(support.Helpers.makeTestApplication(), new Runnable() {
            @Override
            public void run() {
                // Given
                Long id = 1L;
                context().withSession(UserApp.SESSION_USERID, String.valueOf(id));

                // When
                User user = UserApp.currentUser();

                // Then
                assertThat(user).isNotEqualTo(User.anonymous);
                assertThat(user.id).isEqualTo(id);
            }
        });
    }

    @Test
    public void currentUserSessionNotNumeric() {
        running(support.Helpers.makeTestApplication(), new Runnable() {
            @Override
            public void run() {
                // Given
                Context context = context().withSession(UserApp.SESSION_USERID, "string");

                // When
                User user = UserApp.currentUser();

                // Then
                assertThat(user).isEqualTo(User.anonymous);
                assertThat(context.session()).isEmpty();
            }
        });
    }

    @Test
    public void currentUserSessionNoUser() {
        running(support.Helpers.makeTestApplication(), new Runnable() {
            @Override
            public void run() {
                // Given
                Context context = context().withSession(UserApp.SESSION_USERID, "0");

                // When
                User user = UserApp.currentUser();

                // Then
                assertThat(user).isEqualTo(User.anonymous);
                assertThat(context.session()).isEmpty();
            }
        });
    }

    @Test
    public void currentUserToken() {
        running(support.Helpers.makeTestApplication(), new Runnable() {
            @Override
            public void run() {
                // Given
                String loginId = "kjkmadness";
                String password = "ckJUVVaOHhRDNqwbeF+j4RNqXzodXO95+aQRIbJnDK4=";
                String token = loginId + UserApp.TOKEN_SEPARATOR + password;
                Context context = context().withCookie(UserApp.TOKEN, token);

                // When
                User user = UserApp.currentUser();

                // Then
                assertThat(user).isNotEqualTo(User.anonymous);
                assertThat(user.loginId).isEqualTo(loginId);
                assertThat(context.session()).includes(
                        entry(UserApp.SESSION_USERID, String.valueOf(user.id)),
                        entry(UserApp.SESSION_LOGINID, user.loginId),
                        entry(UserApp.SESSION_USERNAME, user.name));
            }
        });
    }

    @Test
    public void currentUserTokenInvalidLength() {
        running(support.Helpers.makeTestApplication(), new Runnable() {
            @Override
            public void run() {
                // Given
                String loginId = "kjkmadness";
                String password = "ckJUVVaOHhRDNqwbeF+j4RNqXzodXO95+aQRIbJnDK4=";
                String token = loginId + UserApp.TOKEN_SEPARATOR + password
                        + UserApp.TOKEN_SEPARATOR + "dummy";
                Context context = context().withCookie(UserApp.TOKEN, token);

                // When
                User user = UserApp.currentUser();

                // Then
                assertThat(user).isEqualTo(User.anonymous);
                assertThat(context.session()).isEmpty();
                verify(context.response()).discardCookie(UserApp.TOKEN);
            }
        });
    }

    @Test
    public void currentUserTokenNoUser() {
        running(support.Helpers.makeTestApplication(), new Runnable() {
            @Override
            public void run() {
                // Given
                String loginId = "kjkmadness";
                String password = "dummy";
                String token = loginId + UserApp.TOKEN_SEPARATOR + password;
                Context context = context().withCookie(UserApp.TOKEN, token);

                // When
                User user = UserApp.currentUser();

                // Then
                assertThat(user).isEqualTo(User.anonymous);
                assertThat(context.session()).isEmpty();
                verify(context.response()).discardCookie(UserApp.TOKEN);
            }
        });
    }

    @Test
    public void currentUserAnonymous() {
        running(support.Helpers.makeTestApplication(), new Runnable() {
            @Override
            public void run() {
                // Given
                Context context = context();

                // When
                User user = UserApp.currentUser();

                // Then
                assertThat(user).isEqualTo(User.anonymous);
                assertThat(context.session()).isEmpty();
            }
        });
    }
}
