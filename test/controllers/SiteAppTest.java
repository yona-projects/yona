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

import models.*;
import models.enumeration.UserState;
import org.junit.*;
import play.mvc.Result;
import play.test.FakeApplication;
import play.test.Helpers;

import java.util.HashMap;
import java.util.Map;

import static org.fest.assertions.Assertions.assertThat;
import static play.test.Helpers.*;

public class SiteAppTest {
    protected static FakeApplication app;

    @BeforeClass
    public static void beforeClass() {
        app = support.Helpers.makeTestApplication();
        Helpers.start(app);
    }

    @AfterClass
    public static void afterClass() {
        Helpers.stop(app);
    }


    @Test
    public void testToggleUserAccountLock() {
        //Given
        Map<String,String> data = new HashMap<>();
        final String loginId= "doortts";
        data.put("loginId", loginId);
        User admin = User.find.byId(1L);

        System.out.println(User.findByLoginId(loginId).state);
        //When

        Result result = callAction(
                controllers.routes.ref.SiteApp.toggleAccountLock(loginId, "", ""),
                fakeRequest(POST, routes.SiteApp.toggleAccountLock(loginId, "", "").url())
                        .withFormUrlEncodedBody(data)
                        .withSession("loginId", admin.loginId)
                        .withSession(UserApp.SESSION_USERID, admin.id.toString()));
        //Then
        assertThat(status(result)).isEqualTo(OK);
        assertThat(flash(result)).isEmpty();
        assertThat(User.findByLoginId(loginId).state).isEqualTo(UserState.LOCKED);
    }
}
