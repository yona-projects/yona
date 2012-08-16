package models;

import static org.fest.assertions.Assertions.assertThat;

import org.junit.Test;

public class IssueCommentTest extends ModelTest<IssueComment> {

    @Test
    public void findById() throws Exception {
        // Given
        // When
        // Then
        assertThat(IssueComment.findById(1l).authorId).isEqualTo(2l);
    }

    @Test
    public void create() throws Exception {
        // Given
        IssueComment issueComment = new IssueComment();
        issueComment.contents = "create() test";
        issueComment.authorId = getTestUser().id;
        issueComment.issue = Issue.findById(1l);
        // When
        // Then
        assertThat(IssueComment.create(issueComment)).isNotNull();
        assertThat(Issue.findById(1l).comments.size()).isEqualTo(1);

    }

    @Test
    public void delete() throws Exception {
        // Given
        assertThat(IssueComment.findById(1l)).isNotNull();
        // When
        IssueComment.delete(1l);
        flush();
        // Then
        assertThat(IssueComment.findById(1l)).isNull();
        assertThat(Issue.findById(3l).comments.size()).isEqualTo(0);
    }
    
    @Test
    public void isAuthor() throws Exception {
        // Given
        Long currentUserId_hobi = 2l;
        Long issueCommentId = 1l;
        // When
        boolean result = IssueComment.isAuthor(currentUserId_hobi, issueCommentId);
        // Then
        assertThat(result).isEqualTo(true);
    }
}
