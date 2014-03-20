package utils;

import static org.junit.Assert.*;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.MapAssert.entry;
import static org.mockito.Mockito.*;

import static play.test.Helpers.*;

import static support.Helpers.*;

import java.io.UnsupportedEncodingException;

import models.User;

import org.apache.commons.codec.binary.Base64;
import org.junit.*;

import controllers.UserApp;

import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Http.*;
import play.test.FakeApplication;

import support.ContextTest;
import utils.BasicAuthAction;

public class BasicAuthActionTest extends ContextTest {
    private FakeApplication application;

    @Before
    public void before() {
        application = makeTestApplication();
        start(application);
    }

    @After
    public void after() {
        stop(application);
    }

    @Test
    public void parseCredentials() {
        // credentials = "Basic" basic-credentials
        // basic-credentials = base64-user-pass
        // base64-user-pass = <base64 [4] encoding of user-pass,
        // user-pass = userid ":" password
        // userid = *<TEXT excluding ":">
        // password = *TEXT

        // ok
        String userpass = "hello:world";
        String basicCredentials = new String(Base64.encodeBase64(userpass.getBytes()));
        try {
            User user = BasicAuthAction.parseCredentials("Basic " + basicCredentials);
            assertThat(user.loginId).isEqualTo("hello");
            assertThat(user.password).isEqualTo("world");
        } catch (UnsupportedEncodingException e) {
            fail();
        } catch (MalformedCredentialsException e) {
            fail();
        }

        // ok
        userpass = ":";
        basicCredentials = new String(Base64.encodeBase64(userpass.getBytes()));
        try {
            User user = BasicAuthAction.parseCredentials("Basic " + basicCredentials);
            assertThat(user.loginId).isEqualTo("");
            assertThat(user.password).isEqualTo("");
        } catch (UnsupportedEncodingException e) {
            fail();
        } catch (MalformedCredentialsException e) {
            fail();
        }

        // malformed credentials.
        String malformedUserpass = "helloworld";
        String malformedCredentials = new String(Base64.encodeBase64(malformedUserpass.getBytes()));
        try {
            BasicAuthAction.parseCredentials("Basic " + malformedCredentials);
            fail();
        } catch (UnsupportedEncodingException e) {
            fail();
        } catch (MalformedCredentialsException e) {
            // success
        }

        // username and password decoded by only ISO-8859-1
        // NOTE: UnsupportedEncodingException is NOT thrown here. It should be thrown
        // if and only if the Server does not support ISO-8859-1.
        malformedUserpass = "안녕:세상";
        malformedCredentials = new String(Base64.encodeBase64(malformedUserpass.getBytes()));
        try {
            User user = BasicAuthAction.parseCredentials("Basic " + malformedCredentials);
            assertThat(user.loginId).isNotEqualTo("안녕");
            assertThat(user.password).isNotEqualTo("세상");
        } catch (UnsupportedEncodingException e) {
            fail();
        } catch (MalformedCredentialsException e) {
            fail();
        }
    }

    @Test
    public void call() throws Throwable {
        // Given
        String loginId = "kjkmadness";
        String password = "pass";
        String credential = "Basic "
                + new String(Base64.encodeBase64((loginId + ":" + password).getBytes("UTF-8")));
        User user = User.findByLoginId(loginId);
        Context context = context().withHeader(Http.HeaderNames.AUTHORIZATION, credential);
        BasicAuthAction action = new BasicAuthAction();
        action.delegate = mock(Action.class);

        // When
        action.call(context);

        // Then
        assertThat(context.session()).includes(
                entry(UserApp.SESSION_USERID, String.valueOf(user.id)),
                entry(UserApp.SESSION_LOGINID, user.loginId),
                entry(UserApp.SESSION_USERNAME, user.name));
        verify(action.delegate).call(context);
    }

    @Test
    public void callAnonymous() throws Throwable {
        // Given
        Context context = context();
        BasicAuthAction action = new BasicAuthAction();
        action.delegate = mock(Action.class);

        // When
        action.call(context);

        // Then
        assertThat(context.session()).isEmpty();
        verify(action.delegate).call(context);
    }
}
