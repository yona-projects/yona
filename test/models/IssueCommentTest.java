package models;

import static org.fest.assertions.Assertions.assertThat;

import java.util.List;

import org.junit.Test;

public class IssueCommentTest extends ModelTest {

    @Test
    public void findCommentsByIssueId() {
        // Given
        // When
        List<IssueComment> issueCommentTest = IssueComment
                .findCommentsByIssueId(1l);
        // Then
        assertThat(issueCommentTest.size()).isEqualTo(1);

    }
    @Test
    public void create(){
        // Given
        IssueComment issueComment = new IssueComment();
        issueComment.contents   =   "create() test";
        issueComment.authorId     =   getTestUser().id;
        // When
        long id =   IssueComment.create(issueComment);
        // Then
        assertThat(IssueComment.findCommentsByIssueId(id)).isNotNull();
    }
    
    @Test
    public void deleteByIssueId() throws Exception {
        // Given
        // When
        IssueComment.deleteByIssueId(1l);
        // Then
        assertThat(IssueComment.findCommentsByIssueId(1l)).isEmpty();
    }
    
}
