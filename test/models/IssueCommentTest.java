package models;

import static org.fest.assertions.Assertions.assertThat;

import org.junit.Test;

public class IssueCommentTest extends ModelTest<IssueComment> {

    @Test
    public void findById() {
        // Given
        // When
        // Then
        assertThat(IssueComment.findById(1l).authorId).isEqualTo(1l);
    }

    @Test
    public void create() {
        // Given
        IssueComment issueComment = new IssueComment();
        issueComment.contents = "create() test";
        issueComment.authorId = getTestUser().id;
        issueComment.issue = Issue.findById(1l);
        // When
        long id = IssueComment.create(issueComment);
        // Then
        assertThat(IssueComment.findById(id)).isNotNull();
    }
}
