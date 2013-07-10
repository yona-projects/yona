package controllers;

import org.junit.BeforeClass;
import org.junit.Test;
import play.mvc.Result;
import play.test.Helpers;

import java.util.HashMap;
import java.util.Map;

import static org.fest.assertions.Assertions.assertThat;
import static play.test.Helpers.*;

public class PasswordResetAppTest {
    @BeforeClass
    public static void beforeClass() {
        callAction(
                routes.ref.Application.init()
        );
    }

    @Test
    public void testRequestResetPassword_validLoginIdAndEmailAddress() {
        Map<String, String> config = new HashMap<>(Helpers.inMemoryDatabase());
        config.put("application.secret", "foo");

        running(fakeApplication(config), new Runnable() {
            public void run() {
                //Given
                Map<String,String> data = new HashMap<>();
                data.put("loginId", "doortts");
                data.put("emailAddress", "doortts@gmail.com");

                //When
                Result result = callAction(
                        routes.ref.PasswordResetApp.requestResetPasswordEmail(),
                        fakeRequest().withFormUrlEncodedBody(data)
                );

                //Then
                assertThat(status(result)).isEqualTo(OK);
                assertThat(contentAsString(result)).contains("메일을 발송하였습니다.");
            }
        });
    }
}
