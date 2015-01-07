/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
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
import models.resource.Resource;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import play.mvc.Result;
import play.test.FakeApplication;
import play.test.Helpers;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.fest.assertions.Assertions.assertThat;
import static play.test.Helpers.*;

public class CommentAppTest {
    protected static FakeApplication app;

    private User admin;
    private User manager;
    private User member;
    private User author;
    private User nonmember;
    private User anonymous;

    private String projectOwner = "yobi";
    private String projectName = "projectYobi";

    @BeforeClass
    public static void beforeClass() {
        app = support.Helpers.makeTestApplication();
        Helpers.start(app);
    }

    @Before
    public void before() {
        admin = User.findByLoginId("admin");
        manager = User.findByLoginId("yobi");
        member = User.findByLoginId("laziel");
        author = User.findByLoginId("nori");
        nonmember = User.findByLoginId("doortts");
        anonymous = User.anonymous;

    }

    private Issue newIssueByMember() {
        Project project = Project.findByOwnerAndProjectName(projectOwner, projectName);

        Issue issueByMember = new Issue();
        issueByMember.setProject(project);
        issueByMember.setTitle("hello");
        issueByMember.setBody("world by member");
        issueByMember.setAuthor(member);
        issueByMember.save();
        return issueByMember;
    }

    private Issue newIssueByNonMember() {
        Project project = Project.findByOwnerAndProjectName(projectOwner, projectName);

        Issue issueByNonMember = new Issue();
        issueByNonMember.setProject(project);
        issueByNonMember.setTitle("hello");
        issueByNonMember.setBody("world by nonmember");
        issueByNonMember.setAuthor(nonmember);
        issueByNonMember.save();
        return issueByNonMember;
    }

    @After
    public void after() {
        Helpers.stop(app);
    }

    @Test
    public void commentByMember() throws IOException {
        //Given
        Issue issue = newIssueByMember();

        // When
        Map<String,String> data = new HashMap<>();
        data.put("contents", "world");
        Result result = callAction(
                controllers.routes.ref.IssueApp.newComment(projectOwner, projectName,
                        issue.getNumber()),
                fakeRequest(
                        POST,
                        routes.IssueApp.newComment(projectOwner, projectName, issue.getNumber())
                                .url()).withFormUrlEncodedBody(data).withSession(
                        UserApp.SESSION_USERID, member.getId().toString()));

        // Then
        assertThat(status(result)).describedAs("Member can create new comment").isEqualTo(SEE_OTHER);
    }

}
