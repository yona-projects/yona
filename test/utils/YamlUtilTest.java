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
package utils;

import static org.fest.assertions.Assertions.*;
import static play.test.Helpers.*;

import java.util.List;

import models.Project;
import models.User;

import org.junit.*;

import com.avaje.ebean.Ebean;

import play.test.FakeApplication;
import support.Helpers;

public class YamlUtilTest {
    private FakeApplication application;

    @Before
    public void before() {
        application = fakeApplication(Helpers.makeTestConfig(), fakeGlobal());
        start(application);
    }

    @After
    public void after() {
        stop(application);
    }

    @Test
    public void insertDataFromYaml() {
        // Given
        // When
        YamlUtil.insertDataFromYaml("test-data.yml", new String[] {"users"});
        List<User> users = Ebean.find(User.class).findList();
        List<Project> projects = Ebean.find(Project.class).findList();

        // Then
        assertThat(users).isNotEmpty();
        assertThat(projects).isEmpty();
    }

    @Test(expected = RuntimeException.class)
    public void unkownEntityName() {
        YamlUtil.insertDataFromYaml("test-data.yml", new String[] {"hello"});
    }
}
