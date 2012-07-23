/**
 * @author Taehyun Park
 * 
 */
package models;

import com.avaje.ebean.Page;
import org.junit.Test;
import utils.JodaDateUtil;

import static org.fest.assertions.Assertions.assertThat;

public class IssueTest extends ModelTest {

    @Test
    public void create() {
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

    @Test
    public void page() {
        // Given
        // When
        Page<Issue> issues = Issue.page(1l, Issue.FIRST_PAGE_NUMBER, Issue.ISSUE_COUNT_PER_PAGE,
            Issue.SORTBY_ID, Issue.ORDERBY_DESCENDING, "",
            Issue.STATUS_NONE);
        // Then
        assertThat(issues.getTotalRowCount()).isEqualTo(2);
        assertThat(issues.getList().size()).isEqualTo(2);

    }

    @Test
    public void pageSearch() {
        // Given
        // When
        Page<Issue> issues = Issue.page(1l, Issue.FIRST_PAGE_NUMBER, Issue.ISSUE_COUNT_PER_PAGE,
            Issue.SORTBY_ID, Issue.ORDERBY_DESCENDING, "메모리",
            Issue.STATUS_NONE);
        // Then
        assertThat(issues.getTotalRowCount()).isEqualTo(1);
        assertThat(issues.getList().size()).isEqualTo(1);
    }

}
