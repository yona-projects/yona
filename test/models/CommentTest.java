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

package models;

import models.enumeration.State;
import org.apache.commons.lang3.time.DateUtils;
import org.junit.Before;
import org.junit.Test;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.fest.assertions.Assertions.assertThat;

public class CommentTest extends ModelTest<Issue> {
    private User admin;
    private User manager;
    private User member;
    private User author;
    private User nonmember;
    private User anonymous;
    private Project project;

    @Before
    public void before() {
        project = Project.findByOwnerAndProjectName("yobi", "projectYobi");
        admin = User.findByLoginId("admin");
        manager = User.findByLoginId("yobi");
        author = User.findByLoginId("doortts");
        member = User.findByLoginId("laziel");
        nonmember = User.findByLoginId("nori");
        anonymous = User.anonymous;
    }

    private Issue createIssueByNonMember() {
        Issue issue = new Issue();
        issue.setProject(project);
        issue.setTitle("issue title");
        issue.setBody("issue body");
        issue.setAuthor(nonmember);
        issue.state = State.OPEN;
        issue.save();
        return issue;
    }

    @Test
    public void createIssueComment_ByNonMember_atPublicProject(){

        //Given
        Issue issue = createIssueByNonMember();

        //When
        IssueComment comment = new IssueComment();
        comment.createdDate = new Date();
        comment.contents = "hello world";
        comment.issue = issue;
        comment.setAuthor(nonmember);

        issue.comments.add(comment);
        issue.save();

        //Then
        assertThat(issue.comments.size()).isEqualTo(1);
        assertThat(IssueComment.find.where().eq("issue.id", issue.id).findUnique()).isEqualTo(comment);
    }

}
