package controllers;

import org.junit.*;

import java.util.*;

import play.mvc.*;
import play.test.Helpers;

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
}
