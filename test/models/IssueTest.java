package models;

import static org.fest.assertions.Assertions.assertThat;

import java.util.List;

import org.junit.Test;

import com.avaje.ebean.Page;

import utils.JodaDateUtil;

public class IssueTest extends ModelTest {

    @Test
    public void create() {
        // Given
        Issue issue = new Issue();
        issue.title = "불필요한 로그 출력 코드 제거";
        issue.date = JodaDateUtil.today();
        issue.status = Issue.STATUS_ENROLLED;
        issue.userId = 1l;
        // When
        // Then
        assertThat(Issue.create(issue)).isEqualTo(5l);
    }

    @Test
    public void findById() throws Exception {
        // Given
        Issue issue = new Issue();
        issue.title = "불필요한 로그 출력 코드 제거";
        issue.date = JodaDateUtil.today();
        issue.status = Issue.STATUS_ENROLLED;
        issue.userId = 1l;
        Long id = Issue.create(issue);
        // When
        Issue issueTest = Issue.findById(id);
        // Then
        assertThat(issueTest).isNotNull();
    }

    @Test
    public void delete() {
        // Given
        // When
        Issue.delete(4l);
        // Then
        assertThat(Issue.findById(4l)).isNull();
    }

    @Test
    public void findByTitle() {
        // Given
        // When
        List<Issue> issueTest = Issue.findByTitle("git");
        // Then
        assertThat(issueTest).hasSize(2);
    }
    
    @Test
    public void findOnePage() {
        // Given
        // When
        Page<Issue> issueTest = Issue.findOnePage(1);
        // Then 
        assertThat(issueTest.getList().size()).isEqualTo(4);
    }

    @Test
    public void findOnlyOpenIssues() {
        // Given
        // When
        Page<Issue> issueTest = Issue.findOnlyOpenIssues(1);
        // Then
        assertThat(issueTest.getList().size()).isEqualTo(2);
    }

    @Test
    public void findOnlyClosedIssues() {
        // Given
        // When
        Page<Issue> issueTest = Issue.findOnlyClosedIssues(1);
        // Then
        assertThat(issueTest.getList().size()).isEqualTo(2);
    }

}
