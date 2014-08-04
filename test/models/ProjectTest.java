/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @Author Hwi Ahn
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

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.*;

import java.util.List;

import models.enumeration.ProjectScope;

import org.junit.Test;

public class ProjectTest extends ModelTest<Project> {

    @Test
    public void create() throws Exception {
        // Given
        Project project = getNewProject();
        // When
        Project.create(project);
        // Then
        Project actualProject = Project.find.byId(project.id);

        assertThat(actualProject).isNotNull();
        assertThat(actualProject.name).isEqualTo("prj_test");
        assertThat(actualProject.siteurl).isEqualTo("http://localhost:9000/prj_test");
    }

    private Project getNewProject() {
        Project project = new Project();
        project.name = "prj_test";
        project.overview = "Overview for prj_test";
        project.projectScope = ProjectScope.PRIVATE;
        project.vcs = "GIT";
        return project;
    }

    @Test
    public void findMilestonesById() throws Exception {
        // Given
        // When
        Project sut = Project.find.byId(1l);
        // Then
        assertThat(sut.milestones.size()).isEqualTo(2);
    }

    @Test
    public void findIssueById() throws Exception {
        // Given
        // When
        Project sut = Project.find.byId(1l);
        // Then
        assertThat(sut.issues.size()).isEqualTo(7);
    }

    @Test
    public void delete() throws Exception {
        // Given
        Project project = getNewProject();
        Project.create(project);
        long projectId = project.id;
        // When
        Project.find.byId(projectId).delete();

        // Then
        assertThat(Project.find.byId(projectId)).isNull();
        assertThat(ProjectUser.findMemberListByProject(projectId)).isEmpty();
        assertThat(Issue.finder.where().eq("project.id", projectId).findList()).isEmpty();
        assertThat(Milestone.findByProjectId(projectId)).isEmpty();
    }

    @Test
    public void findById() throws Exception {
        // Given
        // When
        Project project = Project.find.byId(1l);
        // Then
        assertThat(project.name).isEqualTo("projectYobi");
        assertThat(project.overview).isEqualTo("Yobi는 소프트웨어 개발에 필요한 기능들을 사용하기 편리하게 웹으로 묶은 협업 개발 플랫폼입니다.");
        assertThat(project.projectScope).isEqualTo(ProjectScope.PUBLIC);
        assertThat(project.vcs).isEqualTo("GIT");
        assertThat(project.siteurl).isEqualTo("http://localhost:9000/projectYobi");

    }

    @Test
    public void isOnlyManager() throws Exception {
        // Given
        // When
        boolean yobiIsOnlyManager = Project.isOnlyManager(2l);
        boolean EungjunIsOnlyManager = Project.isOnlyManager(5l);
        // Then
        assertTrue(yobiIsOnlyManager);
        assertFalse(EungjunIsOnlyManager);
    }

    @Test
    public void findProjectsByMember() throws Exception {
        // Given
        // When
        List<Project> projects = Project.findProjectsByMember(2l);
        // Then
        assertThat(projects.size()).isEqualTo(2);
    }

    @Test
    public void findByNameAndOwner() throws Exception {
        // Given
        String userName = "yobi";
        String projectName = "projectYobi";
        // When
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        // Then
        assertThat(project.id).isEqualTo(1l);
    }

    @Test
    public void isProject() throws Exception {
        // Given
        String userName = "yobi";
        String projectName = "projectYobi";
        String newProjectName = "NanumFont";
        // When
        boolean result1 = Project.exists(userName, projectName);
        boolean result2 = Project.exists(userName, newProjectName);

        // Then
        assertThat(result1).isEqualTo(true);
        assertThat(result2).isEqualTo(false);
    }

    @Test
    public void projectNameChangeable() throws Exception {
        // Given
        String userName = "yobi";
        Long projectId = 1l;
        String newProjectName1 = "HelloSocialApp";
        String newProjectName2 = "NanumFont";
        // When
        boolean result1 = Project.projectNameChangeable(projectId, userName, newProjectName1);
        boolean result2 = Project.projectNameChangeable(projectId, userName, newProjectName2);
        // Then
        assertThat(result1).isEqualTo(false);
        assertThat(result2).isEqualTo(true);
    }
}
