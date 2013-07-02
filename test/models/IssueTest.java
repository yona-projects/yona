package models;

import static org.fest.assertions.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        project = Project.findByOwnerAndProjectName("hobi", "nForge4java");
        admin = User.findByLoginId("admin");
        manager = User.findByLoginId("hobi");
        member = User.findByLoginId("k16wire");
        author = User.findByLoginId("nori");
        nonmember = User.findByLoginId("doortts");
        anonymous = new NullUser();

        issue = new Issue();
        issue.setProject(project);
        issue.setTitle("hello");
        issue.setBody("world");
        issue.setAuthor(author);
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
        issue.watch(nonmember);
        assertThat(issue.getWatchers().contains(nonmember)).isTrue();
    }

    @Test
    public void unwatchExplicitly() {
        issue.unwatch(author);
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
}
