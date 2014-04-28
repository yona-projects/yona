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
package utils;

import models.*;

import models.enumeration.*;
import models.resource.Resource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.Assert;

import play.test.Helpers;

import static org.fest.assertions.Assertions.assertThat;

public class AccessControlTest extends ModelTest<Role>{
    @Test
    public void isAllowed_siteAdmin() {
        // Given
        User admin = User.findByLoginId("admin");
        Project projectYobi = Project.findByOwnerAndProjectName("yobi", "projectYobi");

        // When
        boolean canUpdate = AccessControl.isAllowed(admin, projectYobi.asResource(), Operation.UPDATE);
        boolean canRead = AccessControl.isAllowed(admin, projectYobi.asResource(), Operation.READ);
        boolean canDelete = AccessControl.isAllowed(admin, projectYobi.asResource(), Operation.DELETE);

        // Then
        assertThat(canRead).isEqualTo(true);
        assertThat(canUpdate).isEqualTo(true);
        assertThat(canDelete).isEqualTo(true);
    }

    @Test
    public void isAllowed_projectCreator() {
        // Given
        User yobi = User.findByLoginId("yobi");
        Project projectYobi = Project.findByOwnerAndProjectName("yobi", "projectYobi");

        // When
        boolean canUpdate = AccessControl.isAllowed(yobi, projectYobi.asResource(), Operation.UPDATE);
        boolean canRead = AccessControl.isAllowed(yobi, projectYobi.asResource(), Operation.READ);
        boolean canDelete = AccessControl.isAllowed(yobi, projectYobi.asResource(), Operation.DELETE);

        // Then
        assertThat(canRead).isEqualTo(true);
        assertThat(canUpdate).isEqualTo(true);
        assertThat(canDelete).isEqualTo(true);
    }

    @Test
    public void isAllowed_notAMember() {
        // Given
        User notMember = User.findByLoginId("nori");
        Project projectYobi = Project.findByOwnerAndProjectName("yobi", "projectYobi");

        // When
        boolean canUpdate = AccessControl.isAllowed(notMember, projectYobi.asResource(), Operation.UPDATE);
        boolean canRead = AccessControl.isAllowed(notMember, projectYobi.asResource(), Operation.READ);
        boolean canDelete = AccessControl.isAllowed(notMember, projectYobi.asResource(), Operation.DELETE);

        // Then
        assertThat(canRead).isEqualTo(true);
        assertThat(canUpdate).isEqualTo(false);
        assertThat(canDelete).isEqualTo(false);
    }

    // AccessControl.isAllowed throws IllegalStateException if the resource
    // belongs to a project but the project is missing.
    @Test
    public void isAllowed_lostProject() {
        // Given
        User author = User.findByLoginId("nori");
        Project projectYobi = Project.findByOwnerAndProjectName("yobi", "projectYobi");
        Issue issue = new Issue();
        issue.setProject(projectYobi);
        issue.setTitle("hello");
        issue.setBody("world");
        issue.setAuthor(author);
        issue.state = State.OPEN;
        issue.save();

        // When
        issue.project = null;

        // Then
        try {
            AccessControl.isAllowed(author, issue.asResource(), Operation.READ);
            Assert.fail();
        } catch (IllegalStateException e) {
        }
    }

    @Test
    public void isResourceCreatable_to_group_member() {
        // Given
        User doortts = User.findByLoginId("doortts");
        User nori = User.findByLoginId("nori");
        Organization organization = createOrganization("TestOrganization");
        OrganizationUser.assignRole(doortts.id, organization.id, RoleType.ORG_MEMBER.roleType());
        Project protectedProject = createProject("protectedProject", ProjectScope.PROTECTED, organization);
        Project publicProject = createProject("publicProject", ProjectScope.PROTECTED, organization);

        // When & Then
        assertThat(AccessControl.isResourceCreatable(doortts, protectedProject.asResource(), ResourceType.ISSUE_POST)).describedAs("ISSUE_POST").isTrue();
        assertThat(AccessControl.isResourceCreatable(doortts, protectedProject.asResource(), ResourceType.BOARD_POST)).describedAs("BOARD_POST").isTrue();
        assertThat(AccessControl.isResourceCreatable(doortts, protectedProject.asResource(), ResourceType.COMMENT_THREAD)).describedAs("COMMENT_THREAD").isTrue();
        assertThat(AccessControl.isResourceCreatable(doortts, protectedProject.asResource(), ResourceType.ATTACHMENT)).describedAs("ATTACHMENT").isTrue();
        assertThat(AccessControl.isResourceCreatable(doortts, protectedProject.asResource(), ResourceType.COMMIT)).describedAs("COMMIT").isTrue();
        assertThat(AccessControl.isResourceCreatable(doortts, protectedProject.asResource(), ResourceType.COMMIT_COMMENT)).describedAs("COMMIT_COMMENT").isTrue();
        assertThat(AccessControl.isResourceCreatable(doortts, protectedProject.asResource(), ResourceType.MILESTONE)).describedAs("MILESTONE").isTrue();
        assertThat(AccessControl.isResourceCreatable(doortts, protectedProject.asResource(), ResourceType.LABEL)).describedAs("LABEL").isTrue();
        assertThat(AccessControl.isResourceCreatable(doortts, protectedProject.asResource(), ResourceType.CODE)).describedAs("CODE").isTrue();
        assertThat(AccessControl.isResourceCreatable(doortts, protectedProject.asResource(), ResourceType.PULL_REQUEST)).describedAs("PULL_REQUEST").isTrue();
        assertThat(AccessControl.isResourceCreatable(doortts, protectedProject.asResource(), ResourceType.ISSUE_COMMENT)).describedAs("ISSUE_COMMENT").isTrue();
        assertThat(AccessControl.isResourceCreatable(doortts, protectedProject.asResource(), ResourceType.ISSUE_STATE)).describedAs("ISSUE_STATE").isTrue();
        assertThat(AccessControl.isResourceCreatable(doortts, protectedProject.asResource(), ResourceType.ISSUE_ASSIGNEE)).describedAs("ISSUE_ASSIGNEE").isTrue();

        assertThat(AccessControl.isResourceCreatable(doortts, publicProject.asResource(), ResourceType.ISSUE_POST)).describedAs("ISSUE_POST").isTrue();
        assertThat(AccessControl.isResourceCreatable(doortts, publicProject.asResource(), ResourceType.BOARD_POST)).describedAs("BOARD_POST").isTrue();
        assertThat(AccessControl.isResourceCreatable(doortts, publicProject.asResource(), ResourceType.COMMENT_THREAD)).describedAs("COMMENT_THREAD").isTrue();
        assertThat(AccessControl.isResourceCreatable(doortts, publicProject.asResource(), ResourceType.ATTACHMENT)).describedAs("ATTACHMENT").isTrue();
        assertThat(AccessControl.isResourceCreatable(doortts, publicProject.asResource(), ResourceType.COMMIT)).describedAs("COMMIT").isTrue();
        assertThat(AccessControl.isResourceCreatable(doortts, publicProject.asResource(), ResourceType.COMMIT_COMMENT)).describedAs("COMMIT_COMMENT").isTrue();
        assertThat(AccessControl.isResourceCreatable(doortts, publicProject.asResource(), ResourceType.MILESTONE)).describedAs("MILESTONE").isTrue();
        assertThat(AccessControl.isResourceCreatable(doortts, publicProject.asResource(), ResourceType.LABEL)).describedAs("LABEL").isTrue();
        assertThat(AccessControl.isResourceCreatable(doortts, publicProject.asResource(), ResourceType.CODE)).describedAs("CODE").isTrue();
        assertThat(AccessControl.isResourceCreatable(doortts, publicProject.asResource(), ResourceType.PULL_REQUEST)).describedAs("PULL_REQUEST").isTrue();
        assertThat(AccessControl.isResourceCreatable(doortts, publicProject.asResource(), ResourceType.ISSUE_COMMENT)).describedAs("ISSUE_COMMENT").isTrue();
        assertThat(AccessControl.isResourceCreatable(doortts, publicProject.asResource(), ResourceType.ISSUE_STATE)).describedAs("ISSUE_STATE").isTrue();
        assertThat(AccessControl.isResourceCreatable(doortts, publicProject.asResource(), ResourceType.ISSUE_ASSIGNEE)).describedAs("ISSUE_ASSIGNEE").isTrue();

        // A user that is not a member of a group, is not allowed to create resource on the projects of the group.
        assertThat(AccessControl.isResourceCreatable(nori, protectedProject.asResource(), ResourceType.ISSUE_POST)).describedAs("ISSUE_POST").isFalse();
    }

    @Test
    public void isAllowed_resource_to_group_member() {
        // Given
        User doortts = User.findByLoginId("doortts"); // a member of the organization, not admin
        User nori = User.findByLoginId("nori"); // not a member of the organization
        User laziel = User.findByLoginId("laziel");
        Organization organization = createOrganization("TestOrganization");
        OrganizationUser.assignRole(doortts.id, organization.id, RoleType.ORG_MEMBER.roleType());
        Project protectedProject = createProject("protectedProject", ProjectScope.PROTECTED, organization);
        Project publicProject = createProject("publicProject", ProjectScope.PUBLIC, organization);
        Issue protectedIssue = createIssue(protectedProject, nori);
        Issue publicIssue = createIssue(publicProject, nori);

        // When & Then
        assertThat(AccessControl.isAllowed(doortts, protectedProject.asResource(), Operation.READ)).describedAs("doortts can read protected protectedProject").isTrue();
        assertThat(AccessControl.isAllowed(nori, protectedProject.asResource(), Operation.READ)).describedAs("nori cann't read protected protectedProject").isFalse();

        // group member is allowed
        assertThat(AccessControl.isAllowed(doortts, protectedIssue.asResource(), Operation.UPDATE)).describedAs("doortts can update protectedIssue").isTrue();
        assertThat(AccessControl.isAllowed(doortts, protectedIssue.asResource(), Operation.DELETE)).describedAs("doortts can delete protectedIssue").isTrue();
        // author is allowed
        assertThat(AccessControl.isAllowed(nori, protectedIssue.asResource(), Operation.UPDATE)).describedAs("nori can update protectedIssue").isTrue();
        assertThat(AccessControl.isAllowed(nori, protectedIssue.asResource(), Operation.DELETE)).describedAs("nori can delete protectedIssue").isTrue();
        // guest is not allowed
        assertThat(AccessControl.isAllowed(laziel, protectedIssue.asResource(), Operation.UPDATE)).describedAs("laziel cann't update protectedIssue").isFalse();
        assertThat(AccessControl.isAllowed(laziel, protectedIssue.asResource(), Operation.DELETE)).describedAs("laziel cann't delete protectedIssue").isFalse();

        // group member is allowed
        assertThat(AccessControl.isAllowed(doortts, publicIssue.asResource(), Operation.UPDATE)).describedAs("doortts can update publicIssue").isTrue();
        assertThat(AccessControl.isAllowed(doortts, publicIssue.asResource(), Operation.DELETE)).describedAs("doortts can delete publicIssue").isTrue();
        // author is allowed
        assertThat(AccessControl.isAllowed(nori, publicIssue.asResource(), Operation.UPDATE)).describedAs("nori can update publicIssue").isTrue();
        assertThat(AccessControl.isAllowed(nori, publicIssue.asResource(), Operation.DELETE)).describedAs("nori can delete publicIssue").isTrue();
        // guest is not allowed
        assertThat(AccessControl.isAllowed(laziel, publicIssue.asResource(), Operation.UPDATE)).describedAs("laziel cann't update publicIssue").isFalse();
        assertThat(AccessControl.isAllowed(laziel, publicIssue.asResource(), Operation.DELETE)).describedAs("laziel cann't delete publicIssue").isFalse();
    }

    private Organization createOrganization(String name) {
        Organization organization = new Organization();
        organization.name = name;
        organization.save();

        return organization;
    }

    private Project createProject(String name, ProjectScope projectScope, Organization organization) {
        Project project = new Project();
        project.name = name;
        project.organization = organization;
        project.projectScope = projectScope;
        project.vcs = "GIT";
        Project.create(project);
        return project;
    }

    private Issue createIssue(Project project, User author) {
        Issue issue = new Issue();
        issue.project = project;
        issue.author = author;
        issue.authorId = author.id;
        issue.title = "hello";
        issue.body = "world";
        issue.state = State.OPEN;
        issue.save();
        return issue;
    }
}
