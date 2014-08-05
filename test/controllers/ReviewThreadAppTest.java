/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Changsung Kim
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

import org.junit.*;
import play.mvc.Result;
import play.test.Helpers;
import play.test.FakeApplication;
import static play.test.Helpers.*;
import static org.fest.assertions.Assertions.*;

/**
 * {@link controllers.ReviewApp}을 테스트
 */
public class ReviewThreadAppTest {

    protected static FakeApplication app;

    /**
     * 테스트를 위해 메모리 DB로 전환
     */
    @BeforeClass
    public static void beforeClass() {
        app = support.Helpers.makeTestApplication();
        Helpers.start(app);
    }

    @AfterClass
    public static void afterClass() {
        Helpers.stop(app);
    }

    /**
     * 잘못된 URL을 입력하여 not found return을 테스트
     */
    @Test
    public void projectNotFound() {
        Result result = callAction(
                routes.ref.ReviewThreadApp.reviewThreads("admin", "dobi")
        );
        assertThat(result).isNotNull();
        assertThat(status(result)).isEqualTo(SEE_OTHER);
    }

    /**
     * 비공개 프로젝트에 관리자와 멤버가 아닌 사용자의 접근으로 forbidden return을 테스트
     */
    @Test
    public void projectForbidden() {
        Result result = callAction(
                controllers.routes.ref.ReviewThreadApp.reviewThreads("laziel", "Jindo"),
                fakeRequest(GET, "pageNum=1&state=OPEN&orderDir=desc&orderBy=createdDate")
        );

        assertThat(status(result)).isEqualTo(SEE_OTHER);
    }
}
