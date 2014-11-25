/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Suwon Chae
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package controllers;

import models.User;
import models.enumeration.UserState;

import org.junit.*;

import java.util.*;

import org.junit.rules.TestWatcher;
import play.mvc.*;
import play.mvc.Http.*;
import play.test.FakeApplication;
import play.test.Helpers;
import support.ExecutionTimeWatcher;
import support.ContextTest;
import utils.JodaDateUtil;

import static play.test.Helpers.*;
import static org.fest.assertions.Assertions.*;
import static org.fest.assertions.MapAssert.entry;
import static org.mockito.Mockito.*;

public class UserAppTest extends ContextTest {
    protected static FakeApplication app;

    @Rule
    public TestWatcher watcher = new ExecutionTimeWatcher();

    @BeforeClass
    public static void beforeClass() {
        Map<String, String> config = support.Helpers.makeTestConfig();
        config.put("signup.require.confirm", "true");

        app = support.Helpers.makeTestApplication(config);
        Helpers.start(app);
    }

    @AfterClass
    public static void afterClass() {
        Helpers.stop(app);
    }

    @Test
    public void findById_doesntExist() {
        //Given
        Map<String,String> data = new HashMap<>();
        data.put("loginId", "nekure");

        //When
        Result result = callAction(
                controllers.routes.ref.UserApp.isUsed("nekure"),
                fakeRequest().withFormUrlEncodedBody(data)
        );  // fakeRequest doesn't need here, but remains for example

        //Then
        assertThat(status(result)).isEqualTo(OK);
        assertThat(contentAsString(result)).contains("\"isExist\":false");
        assertThat(contentType(result)).contains("json");
    }

    @Test
    public void findById_alreadyExist() {
        //Given
        Map<String,String> data = new HashMap<>();
        data.put("loginId", "yobi");

        //When
        Result result = callAction(
                controllers.routes.ref.UserApp.isUsed("yobi"),
                fakeRequest().withFormUrlEncodedBody(data)
        ); // fakeRequest doesn't need here, but remains for example

        //Then
        assertThat(status(result)).isEqualTo(OK);
        assertThat(contentAsString(result)).contains("\"isExist\":true");
        assertThat(contentType(result)).contains("json");
    }

    @Test
    public void findById_alreadyExistGroupName() {
        //Given
        String loginId = "labs";

        //When
        Result result = callAction(controllers.routes.ref.UserApp.isUsed(loginId));

        //Then
        assertThat(status(result)).isEqualTo(OK);
        assertThat(contentAsString(result)).contains("\"isExist\":true");
        assertThat(contentType(result)).contains("json");
    }

    @Test
    public void isEmailExist() {
        //Given
        //When
        Result result = callAction(
                controllers.routes.ref.UserApp.isEmailExist("doortts@gmail.com")
        );

        //Then
        assertThat(status(result)).isEqualTo(OK);
        assertThat(contentAsString(result)).contains("{\"isExist\":true}");
    }

    @Test
    public void login_notComfirmedUser() {
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
        data.put("loginIdOrEmail", user.loginId);
        data.put("password", user.password);

        //When
        Result result = callAction(
                controllers.routes.ref.UserApp.login(),
                fakeRequest().withFormUrlEncodedBody(data)
        );

        //Then
        assertThat(status(result)).describedAs("result status should '303 see other'").isEqualTo(303);
    }

    @Test
    public void newUser_AlreadyExistGroupName() {
        //Given
        Map<String, String> data = new HashMap<>();
        data.put("loginId", "labs");
        data.put("password", "somefakepassword");
        data.put("email", "labs@fake.com");
        data.put("name", "labs");

        //When
        Result result = callAction(
                controllers.routes.ref.UserApp.newUser(),
                fakeRequest().withFormUrlEncodedBody(data)
        );

        //Then
        assertThat(status(result)).describedAs("result status should '400 bad request'").isEqualTo(BAD_REQUEST);
    }

    @Test
    public void newUser_confirmSignUpMode() {
        //Given
        final String loginId = "somefakeuserid";
        Map<String, String> data = new HashMap<>();
        data.put("loginId", loginId);
        data.put("password", "somefakepassword");
        data.put("email", "somefakeuserid@fake.com");
        data.put("name", "racoon");

        //When
        Result result = callAction(
                controllers.routes.ref.UserApp.newUser(),
                fakeRequest().withFormUrlEncodedBody(data)
        );

        //Then
        assertThat(status(result)).describedAs("result status should '303 see other'").isEqualTo(303);
    }

    @Test
    public void findById_reserved() {
        //Given
        Map<String,String> data = new HashMap<>();
        data.put("loginId", "messages.js");

        //When
        Result result = callAction(controllers.routes.ref.UserApp.isUsed("messages.js"));

        //Then
        assertThat(status(result)).isEqualTo(OK);
        assertThat(contentAsString(result)).contains("\"isReserved\":true");
        assertThat(contentType(result)).contains("json");
    }

    @Test
    public void authenticateWithPlainPassword() {
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

    @Test
    public void authenticateWithPlainPasswordWrongPassword() {
        // Given
        String loginId = "kjkmadness";
        String password = "wrong";

        // When
        User user = UserApp.authenticateWithPlainPassword(loginId, password);

        // Then
        assertThat(user).isNotNull();
        assertThat(user.isAnonymous()).isTrue();
    }

    @Test
    public void authenticateWithPlainPasswordNotExist() {
        // Given
        String loginId = "notexist";
        String password = "pass";

        // When
        User user = UserApp.authenticateWithPlainPassword(loginId, password);

        // Then
        assertThat(user).isNotNull();
        assertThat(user.isAnonymous()).isTrue();
    }

    @Test
    public void authenticateWithHashedPassword() {
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

    @Test
    public void authenticateWithHashedPasswordWrongPassword() {
        // Given
        String loginId = "kjkmadness";
        String password = "wrong";

        // When
        User user = UserApp.authenticateWithHashedPassword(loginId, password);

        // Then
        assertThat(user).isNotNull();
        assertThat(user.isAnonymous()).isTrue();
    }

    @Test
    public void authenticateWithHashedPasswordNotExist() {
        // Given
        String loginId = "notexist";
        String password = "ckJUVVaOHhRDNqwbeF+j4RNqXzodXO95+aQRIbJnDK4=";

        // When
        User user = UserApp.authenticateWithHashedPassword(loginId, password);

        // Then
        assertThat(user).isNotNull();
        assertThat(user.isAnonymous()).isTrue();
    }

    @Test
    public void login() {
        // Given
        String loginId = "kjkmadness";
        String password = "pass";
        User user = User.findByLoginId(loginId);
        Map<String, String> data = new HashMap<>();
        data.put("loginIdOrEmail", loginId);
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

    @Test
    public void loginWrongPassword() {
        // Given
        String loginId = "kjkmadness";
        String password = "wrong";
        Map<String, String> data = new HashMap<>();
        data.put("loginIdOrEmail", loginId);
        data.put("password", password);

        // When
        Result result = callAction(controllers.routes.ref.UserApp.login(), fakeRequest()
                .withFormUrlEncodedBody(data));

        // Then
        assertThat(status(result)).isEqualTo(SEE_OTHER);
        assertThat(header(LOCATION, result)).isEqualTo(routes.UserApp.loginForm().url());
        assertThat(session(result)).isEmpty();
    }

    @Test
    public void currentUserContext() {
        // Given
        User expected = User.find.byId(1L);
        context().withArg(UserApp.TOKEN_USER, expected);

        // When
        User user = UserApp.currentUser();

        // Then
        assertThat(user).isEqualTo(expected);
    }

    @Test
    public void currentUserSession() {
        // Given
        Long id = 1L;
        context().withSession(UserApp.SESSION_USERID, String.valueOf(id));

        // When
        User user = UserApp.currentUser();

        // Then
        assertThat(user).isNotEqualTo(User.anonymous);
        assertThat(user.id).isEqualTo(id);
    }

    @Test
    public void currentUserSessionNotNumeric() {
        // Given
        Context context = context().withSession(UserApp.SESSION_USERID, "string");

        // When
        User user = UserApp.currentUser();

        // Then
        assertThat(user).isEqualTo(User.anonymous);
        assertThat(context.session()).isEmpty();
    }

    @Test
    public void currentUserSessionNoUser() {
        // Given
        Context context = context().withSession(UserApp.SESSION_USERID, "0");

        // When
        User user = UserApp.currentUser();

        // Then
        assertThat(user).isEqualTo(User.anonymous);
        assertThat(context.session()).isEmpty();
    }

    @Test
    public void currentUserToken() {
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

    @Test
    public void currentUserTokenInvalidLength() {
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

    @Test
    public void currentUserTokenNoUser() {
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

    @Test
    public void currentUserAnonymous() {
        // Given
        Context context = context();

        // When
        User user = UserApp.currentUser();

        // Then
        assertThat(user).isEqualTo(User.anonymous);
        assertThat(context.session()).isEmpty();
    }
}
