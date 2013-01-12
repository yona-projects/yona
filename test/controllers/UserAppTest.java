package controllers;

import org.junit.*;

import java.util.*;

import play.mvc.*;

import static play.test.Helpers.*;
import static org.fest.assertions.Assertions.*;

/**
 * User: doortts
 * Date: 12. 9. 3
 * Time: 오후 5:36
 */
public class UserAppTest {
    @Test
    public void findById_doesntExist() {
        running(fakeApplication(), new Runnable() {
            public void run() {
                Map<String,String> data = new HashMap<>();
                data.put("loginId", "nekure");

                Result result = callAction(
                        controllers.routes.ref.UserApp.isUserExist("nekure"),
                        fakeRequest().withFormUrlEncodedBody(data)
                );  // fakeRequest doesn't need here, but remains for example

                assertThat(status(result)).isEqualTo(OK);
                assertThat(contentAsString(result)).contains("{\"isExist\":\"false\"}");
            }
        });
    }

    @Test
    public void findById_alreadyExist() {
        running(fakeApplication(), new Runnable() {
            public void run() {
                Map<String,String> data = new HashMap<>();
                data.put("loginId", "hobi");

                Result result = callAction(
                        controllers.routes.ref.UserApp.isUserExist("hobi"),
                        fakeRequest().withFormUrlEncodedBody(data)
                ); // fakeRequest doesn't need here, but remains for example

                assertThat(status(result)).isEqualTo(OK);
                assertThat(contentAsString(result)).contains("{\"isExist\":\"true\"}");
            }
        });
    }
}
