/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author kjkmadness
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

import static org.fest.assertions.Assertions.*;
import static play.test.Helpers.*;

import java.io.File;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import models.Project;
import models.User;

import org.junit.*;

import controllers.routes.ref;

import play.i18n.Lang;
import play.i18n.Messages;
import play.mvc.Http;
import play.mvc.Result;
import play.test.FakeApplication;
import playRepository.GitRepository;
import utils.Constants;

public class ImportAppTest {
    private static FakeApplication application;
    private static User yobi;
    private static Project src;
    private static Project dest;
    private static Map<String, String> formData;

    @BeforeClass
    public static void before() throws Exception {
        GitRepository.setRepoPrefix("resources/test/repo/git/");
        application = support.Helpers.makeTestApplication();
        start(application);
        yobi = User.findByLoginId("yobi");
        src = project(yobi.loginId, "src");
        dest = project(yobi.loginId, "dest");
        createRepository(src);
        formData = new HashMap<>();
    }

    @AfterClass
    public static void after() {
        stop(application);
        support.Files.rm_rf(new File(GitRepository.getRepoPrefix()));
    }

    @Test
    public void importFormAnonymous() {
        // When
        Result result = callAction(ref.ImportApp.importForm(),
                fakeRequest(GET, routes.ImportApp.importForm().url()));

        // Then
        assertThat(status(result)).isEqualTo(SEE_OTHER);
        assertThat(header(LOCATION, result)).isEqualTo(
                routes.UserApp.loginForm().url() + "?redirectUrl="
                        + routes.ImportApp.importForm().url());
    }

    @Test
    public void importForm() {
        // Given
        User user = User.find.byId(2L);

        // When
        Result result = callAction(ref.ImportApp.importForm(),
                fakeRequest().withSession(UserApp.SESSION_USERID, String.valueOf(user.id)));

        // Then
        assertThat(status(result)).isEqualTo(OK);
    }

    @Test
    public void newProject() throws Exception {
        // Given
        formData.put("url", GitRepository.getGitDirectoryURL(src));
        formData.put("owner", yobi.loginId);
        formData.put("name", dest.name);
        formData.put("projectScope", "PUBLIC");
        formData.put("vcs", "GIT");

        // When
        Result result = callAction(ref.ImportApp.newProject(),
                fakeRequest()
                    .withSession(UserApp.SESSION_USERID, String.valueOf(yobi.id))
                    .withFormUrlEncodedBody(formData));

        // Then
        assertThat(status(result)).isEqualTo(SEE_OTHER);
        assertThat(header(LOCATION, result)).isEqualTo(routes.ProjectApp.project(dest.owner, dest.name).url());
        assertThat(Project.findByOwnerAndProjectName(dest.owner, dest.name)).isNotNull();
        assertThat(new File(GitRepository.getGitDirectoryURL(dest)).exists()).isTrue();
    }

    @Test
    public void newProjectAnonymous() {
        // When
        Result result = callAction(ref.ImportApp.newProject());

        // Then
        assertThat(status(result)).isEqualTo(FORBIDDEN);
    }

    @Test
    public void newProjectNoUrl() {
        // Given
        formData.put("owner", yobi.loginId);
        formData.put("name", dest.name);
        formData.put("projectScope", "PUBLIC");
        formData.put("vcs", "GIT");

        // When
        Result result = callAction(ref.ImportApp.newProject(),
                fakeRequest()
                    .withSession(UserApp.SESSION_USERID, String.valueOf(yobi.id))
                    .withFormUrlEncodedBody(formData)
                    .withHeader(Http.HeaderNames.ACCEPT_LANGUAGE, Lang.defaultLang().language()));

        // Then
        assertThat(status(result)).isEqualTo(BAD_REQUEST);
        assertThat(contentAsString(result)).contains(Messages.get(Lang.defaultLang(), "project.import.error.empty.url"));
    }

    @Test
    public void newProjectDuplicatedName() throws Exception {
        // Given
        formData.put("url", GitRepository.getGitDirectoryURL(src));
        formData.put("owner", yobi.loginId);
        formData.put("name", "projectYobi");
        formData.put("projectScope", "PUBLIC");
        formData.put("vcs", "GIT");

        // When
        Result result = callAction(ref.ImportApp.newProject(),
                fakeRequest()
                    .withSession(UserApp.SESSION_USERID, String.valueOf(yobi.id))
                    .withFormUrlEncodedBody(formData)
                    .withHeader(Http.HeaderNames.ACCEPT_LANGUAGE, Lang.defaultLang().language()));

        // Then
        assertThat(status(result)).isEqualTo(BAD_REQUEST);
        assertThat(contentAsString(result)).contains(Messages.get(Lang.defaultLang(), "project.name.duplicate"));
    }

    @Test
    public void newProjectWrongName() throws Exception {
        // Given
        formData.put("url", GitRepository.getGitDirectoryURL(src));
        formData.put("owner", yobi.loginId);
        formData.put("name", "!@#$%");
        formData.put("projectScope", "PUBLIC");
        formData.put("vcs", "GIT");

        // When
        Result result = callAction(ref.ImportApp.newProject(),
                fakeRequest()
                    .withSession(UserApp.SESSION_USERID, String.valueOf(yobi.id))
                    .withFormUrlEncodedBody(formData)
                    .withHeader(Http.HeaderNames.ACCEPT_LANGUAGE, Lang.defaultLang().language()));

        // Then
        assertThat(status(result)).isEqualTo(BAD_REQUEST);
        assertThat(contentAsString(result)).contains(Messages.get(Lang.defaultLang(), "project.name.alert"));
    }

    private static Project project(String owner, String name) {
        Project project = new Project();
        project.owner = owner;
        project.name = name;
        return project;
    }

    private static void createRepository(Project project) throws Exception {
        GitRepository gitRepository = new GitRepository(project);
        gitRepository.create();
        gitRepository.close();
    }
}
