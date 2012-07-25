package models;

import static org.fest.assertions.Assertions.assertThat;

import models.enumeration.Direction;
import models.enumeration.IssueState;

import org.junit.Test;

import utils.JodaDateUtil;

import com.avaje.ebean.Page;

public class IssueTest extends ModelTest {

    @Test
    public void create() throws Exception {
        // Given
        Issue issue = new Issue();
        issue.title = "불필요한 로그 출력 코드 제거";
        issue.date = JodaDateUtil.today();
        issue.status = Issue.STATUS_ENROLLED;
        issue.reporter = getTestUser();
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
        issue.reporter = getTestUser();
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

//    @Test
//    public void page() {
//        // Given
//        // When
//        Page<Issue> issues = Issue.page(1l, Issue.FIRST_PAGE_NUMBER,
//                Issue.ISSUE_COUNT_PER_PAGE, Issue.SORTBY_ID,
//                Issue.ORDERBY_DESCENDING, "", Issue.STATUS_NONE);
//        // Then
//        assertThat(issues.getTotalRowCount()).isEqualTo(2);
//        assertThat(issues.getList().size()).isEqualTo(2);
//
//    }
//
//    @Test
//    public void pageSearch() {
//        // Given
//        // When
//        Page<Issue> issues = Issue.page(1l, Issue.FIRST_PAGE_NUMBER,
//                Issue.ISSUE_COUNT_PER_PAGE, Issue.SORTBY_ID,
//                Issue.ORDERBY_DESCENDING, "메모리", Issue.STATUS_NONE);
//        // Then
//        assertThat(issues.getTotalRowCount()).isEqualTo(1);
//        assertThat(issues.getList().size()).isEqualTo(1);
//    }

    @Test
    public void findOpenIssues() throws Exception {
        // Given
        // When
        Page<Issue> issues = Issue.findOpenIssues(1l);
        // Then
        assertThat(issues.getTotalRowCount()).isEqualTo(1);
    }

    @Test
    public void findClosedIssues() throws Exception {
        // Given
        // When
        Page<Issue> issues = Issue.findClosedIssues(1l);
        // Then
        assertThat(issues.getTotalRowCount()).isEqualTo(1);
    }

    @Test
    public void findFilterIssues() throws Exception {

        // Given
        // When
        Page<Issue> issues = Issue.findFilteredIssues(1l, "git");
        // Then
        assertThat(issues.getTotalRowCount()).isEqualTo(1);

    }

    @Test
    public void findCommentedIssue() throws Exception {
        // Given
        // When
        Page<Issue> issues = Issue.findCommentedIssues(1l, "");
        // Then
        assertThat(issues.getTotalRowCount()).isEqualTo(1);
    }
    
    @Test
    public void findFileAttachedIssue() throws Exception {
        // Given
        // When
        Page<Issue> issues = Issue.findFileAttachedIssues(1l, "");
        // Then
        assertThat(issues.getTotalRowCount()).isEqualTo(1);
    }

}
