/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @Author Ahn Hyeok Jun
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
package models;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import play.test.FakeApplication;
import play.test.Helpers;

public class ModelTest<T> {
    protected static FakeApplication app;
    protected Class<T> type;

    @SuppressWarnings("unchecked")
    public ModelTest() {
    }

    @BeforeClass
    public static void startApp() {
        app = support.Helpers.makeTestApplication();
        Helpers.start(app);
    }

    @AfterClass
    public static void stopApp() {
        Helpers.stop(app);
    }
    /**
     * Returns the first user. (id : 2 / name : yobi)
     *
     * @return User
     */
    protected User getTestUser() {
        return User.find.byId(2l);
    }

    /**
     * Returns user.
     *
     * @param userId
     * @return
     */
    protected User getTestUser(Long userId) {
        return User.find.byId(userId);
    }

    /**
     * Returns the first project. (id : 1 / name : nForge4java)
     *
     * @return Project
     */
    protected Project getTestProject() {
        return Project.find.byId(1l);
    }

    /**
     * Returns project.
     *
     * @return Project
     */
    protected Project getTestProject(Long projectId) {
        return Project.find.byId(projectId);
    }

    @SuppressWarnings("unchecked")
    protected void flush(T model) {
//        ebeanUiUtil.flush(model);
    }

    protected void flush(Long id) {
//        ebeanUiUtil.flush(id);
    }

    protected void flush() {
        flush(1l);
    }

}
