package models;

import static org.fest.assertions.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.Before;

import com.avaje.ebean.Page;
import play.data.validation.Validation;

public class IssueTest extends ModelTest<Issue> {
    private User admin;
    private User manager;
    private User member;
    private User author;
    private User nonmember;
    private User anonymous;

    private Issue issue;

    @Before
    public void before() {
        Project project = Project.findByOwnerAndProjectName("hobi", "nForge4java");
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
}
