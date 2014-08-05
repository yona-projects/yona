/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @Author yoon
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

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import models.enumeration.State;

import org.apache.commons.lang3.time.DateUtils;
import org.junit.After;
import org.junit.Test;
import org.junit.Before;

public class IssueTest extends ModelTest<Issue> {
    private User admin;
    private User manager;
    private User member;
    private User author;
    private User nonmember;
    private User anonymous;
    private Project project;

    private Issue issue;

    @Before
    public void before() {
        project = Project.findByOwnerAndProjectName("yobi", "projectYobi");
        admin = User.findByLoginId("admin");
        manager = User.findByLoginId("yobi");
        member = User.findByLoginId("laziel");
        author = User.findByLoginId("nori");
        nonmember = User.findByLoginId("doortts");
        anonymous = new NullUser();

        issue = new Issue();
        issue.setProject(project);
        issue.setTitle("hello");
        issue.setBody("world");
        issue.setAuthor(author);
        issue.state = State.OPEN;
        issue.save();
    }

    @After
    public void after() {
        issue.setProject(project);
        issue.setTitle("hello");
        issue.setBody("world");
        issue.setAuthor(author);
        issue.state = State.OPEN;
        issue.update();
    }

    @Test
    public void vote() {
        // when
        issue.addVoter(admin);
        issue.addVoter(manager);

        // then
        assertThat(issue.voters.size()).isEqualTo(2);
    }

    @Test
    public void unvote() {
        // given
        issue.addVoter(admin);
        issue.addVoter(manager);

        // when
        issue.removeVoter(admin);

        // then
        assertThat(issue.voters.size()).isEqualTo(1);
    }

    @Test
    public void watchersAfterVoting() {
        // when
        issue.addVoter(member);
        issue.addVoter(manager);

        // then
        assertThat(issue.getWatchers().size()).isEqualTo(3);
    }

    @Test
    public void unwatchByDefault() {
        // given
        assertThat(issue.getWatchers().contains(admin)).isFalse();
        assertThat(issue.getWatchers().contains(manager)).isFalse();
        assertThat(issue.getWatchers().contains(member)).isFalse();
        assertThat(issue.getWatchers().contains(nonmember)).isFalse();
        assertThat(issue.getWatchers().contains(anonymous)).isFalse();
    }

    @Test
    public void commenterWatches() {
        // given
        IssueComment comment = new IssueComment();
        comment.setAuthor(nonmember);
        comment.setContents("blah");
        issue.comments.add(comment);
        issue.update();

        assertThat(issue.getComments().size()).isEqualTo(1);
        assertThat(issue.getWatchers().contains(nonmember)).isTrue();
    }

    @Test
    public void assigneeWatches() {
        // given
        issue.assignee = Assignee.add(member.id, issue.project.id);
        assertThat(issue.getWatchers().contains(member)).isTrue();
    }


    @Test
    public void authorWatches() {
        // given
        assertThat(issue.getWatchers().contains(author)).isTrue();
    }


    @Test
    public void watchExplicitly() {
        Watch.watch(nonmember, issue.asResource());
        assertThat(issue.getWatchers().contains(nonmember)).isTrue();
    }

    @Test
    public void unwatchExplicitly() {
        Watch.unwatch(author, issue.asResource());
        assertThat(issue.getWatchers().contains(author)).isFalse();
    }

    @Test
    public void watchAndUnwatchProject() {
        assertThat(issue.getWatchers().contains(nonmember)).describedAs("before watch").isFalse();
        nonmember.addWatching(project);
        issue.refresh();

        assertThat(issue.getWatchers().contains(nonmember)).describedAs("after watch").isTrue();
        nonmember.removeWatching(project);

        issue = Issue.finder.byId(issue.id); // 데이터가 refresh가 안되서 다시 읽어옴.
        assertThat(issue.getWatchers().contains(nonmember)).describedAs("after unwatch").isFalse();
    }

    @Test
    public void getMentionedUsers() {
        String body = "hello @admin hihi @keesun";
        Matcher matcher = Pattern.compile("@" + User.LOGIN_ID_PATTERN).matcher(body);

        matcher.find();
        assertThat(matcher.group()).isEqualTo("@admin");
        matcher.find();
        assertThat(matcher.group()).isEqualTo("@keesun");
    }

    @Test
    public void nextState(){
        //Given
        issue.state = State.OPEN;

        //When //Then
        assertThat(issue.nextState()).isEqualTo(State.CLOSED);
    }

    @Test
    public void previousState(){
        //Given
        issue.state = State.CLOSED;

        //When //Then
        assertThat(issue.previousState()).isEqualTo(State.OPEN);
    }

    @Test
    public void toNextState(){
        //Given
        State exptected = issue.nextState();

        //When
        issue.toNextState();

        //Then
        assertThat(issue.state).isEqualTo(exptected);
    }

    @Test
    public void getTimeline() throws Exception {
        // Given
        IssueComment comment1 = createIssueComment("2013-12-01");
        IssueComment comment2 = createIssueComment("2013-12-03");
        List<IssueComment> comments = new ArrayList<>();
        comments.add(comment1);
        comments.add(comment2);
        issue.comments = comments;

        IssueEvent event1 = createIssueEvent("2013-12-02");
        IssueEvent event2 = createIssueEvent("2013-12-04");
        List<IssueEvent> events = new ArrayList<>();
        events.add(event1);
        events.add(event2);
        issue.events = events;

        // When
        List<TimelineItem> timeline = issue.getTimeline();

        // Then
        assertThat(timeline).containsExactly(comment1, event1, comment2, event2);
    }

    private IssueComment createIssueComment(String str) throws ParseException {
        IssueComment comment = new IssueComment();
        comment.createdDate = DateUtils.parseDate(str, "yyyy-MM-dd");
        return comment;
    }

    private IssueEvent createIssueEvent(String str) throws ParseException {
        IssueEvent event = new IssueEvent();
        event.created = DateUtils.parseDate(str, "yyyy-MM-dd");
        return event;
    }

    @Test
    public void optimisticLockException() {
        Project project1 = Project.findByOwnerAndProjectName("yobi", "projectYobi");
        Project project2 = Project.findByOwnerAndProjectName("yobi", "projectYobi");

        issue = new Issue();
        issue.setProject(project1);
        issue.setTitle("a");
        issue.save();

        issue = new Issue();
        issue.setProject(project2);
        issue.setTitle("b");
        issue.save();
    }
}
