/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Wansoon Park
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
package playRepository;

import static org.fest.assertions.Assertions.assertThat;

import java.io.IOException;

import javax.servlet.ServletException;

import models.Project;

import org.junit.Test;

public class RepositoryServiceTest {

    @Test
    public void testGetRepositoryWhenProjectIsNull() throws UnsupportedOperationException, IOException, ServletException {
        Project project = null;
        PlayRepository repository = RepositoryService.getRepository(project);
        assertThat(repository).isNull();
    }

    @Test
    public void testGetSVNRepository() throws UnsupportedOperationException, IOException, ServletException {
        Project project = createProject("wansoon", "testProject", RepositoryService.VCS_GIT);
        PlayRepository repository = RepositoryService.getRepository(project);

        assertThat(repository).isNotNull();
    }

    @Test
    public void testGetGitRepository() throws UnsupportedOperationException, IOException, ServletException {
        Project project = createProject("wansoon", "testProject", RepositoryService.VCS_SUBVERSION);
        PlayRepository repository = RepositoryService.getRepository(project);

        assertThat(repository).isNotNull();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetRepositoryThrowUnsupportedOperationException() throws UnsupportedOperationException, IOException, ServletException {
        Project project = createProject("wansoon", "testProject", "Mercurial");

        PlayRepository repository = RepositoryService.getRepository(project);

        assertThat(repository).isNull();
    }

    private Project createProject(String owner, String name, String vcs) {
        Project project = new Project();
        project.owner = owner;
        project.name = name;
        project.vcs = vcs;
        return project;
    }
}
