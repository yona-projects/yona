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

import org.junit.BeforeClass;
import org.junit.Test;
import play.mvc.Result;

import java.util.HashMap;
import java.util.Map;

import static org.fest.assertions.Assertions.assertThat;
import static play.test.Helpers.*;

public class PasswordResetAppTest {
    @Test
    public void testRequestResetPassword_validLoginIdAndEmailAddress() {
        Map<String, String> config = support.Helpers.makeTestConfig();
        config.put("application.langs", "ko-KR");

        running(support.Helpers.makeTestApplication(config), new Runnable() {
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
