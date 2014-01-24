package models;

import static org.fest.assertions.Assertions.assertThat;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import models.enumeration.State;

import org.apache.commons.lang3.time.DateUtils;
import org.junit.Test;
import org.junit.Before;

import com.avaje.ebean.Page;
import play.Logger;
import play.data.validation.Validation;

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
}
