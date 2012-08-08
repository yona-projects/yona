package models;

import com.avaje.ebean.Page;
import models.enumeration.IssueState;
import models.enumeration.StateType;
import org.junit.Test;

import play.Logger;
import utils.JodaDateUtil;

import java.io.File;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;

public class IssueTest extends ModelTest<Issue> {

    @Test
    public void create() throws Exception {
        // Given
        Issue issue = new Issue();
        issue.title = "불필요한 로그 출력 코드 제거test";
        issue.date = JodaDateUtil.today();
        issue.state = IssueState.ASSIGNED;
        issue.reporterId = User.findById(1l).id;
        issue.milestoneId = 4l;
        // When
        // Then
        assertThat(Issue.create(issue)).isEqualTo(9l);
    }

    @Test
    public void findById() throws Exception {
        // Given
        // When
        Issue issue = Issue.findById(1l);
        // Then
        assertThat(issue.id).isEqualTo(1l);
        assertThat(issue.title).isEqualTo("불필요한 로그 출력 코드 제거");
    }

    @Test
    public void findCommentsById() throws Exception {
        // Given
        // When
        Issue issue = Issue.findById(1l);
        // Then
        assertThat(issue.comments.size()).isEqualTo(1);

        IssueComment issueComment = issue.comments.get(0);
        assertThat(issueComment.id).isEqualTo(1l);
        assertThat(issueComment.contents).isEqualTo("코드를 수정했습니다");
    }

    @Test
    public void delete() {
        // Given
        // When
        Issue.delete(1l);
        flush();
        // Then
        assertThat(Issue.findById(1l)).isNull();
        assertThat(IssueComment.findById(1l)).isNull();
    }

    @Test
    public void findOpenIssues() throws Exception {
        // Given
        // When
        Page<Issue> issues = Issue.findOpenIssues("nForge4java");
        // Then
        assertThat(issues.getTotalRowCount()).isEqualTo(1);
    }

    @Test
    public void findClosedIssues() throws Exception {
        // Given
        // When
        Page<Issue> issues = Issue.findClosedIssues("nForge4java");
        // Then
        assertThat(issues.getTotalRowCount()).isEqualTo(1);
    }

    @Test
    public void findFilteredIssues() throws Exception {

        // Given
        // When
        Page<Issue> issues = Issue.findFilteredIssues("nForge4java", "로그", StateType.OPEN,
                true, true);
        // Then
        assertThat(issues.getTotalRowCount()).isEqualTo(1);

    }

    @Test
    public void findCommentedIssue() throws Exception {
        // Given
        // When
        Page<Issue> issues = Issue.findCommentedIssues("nForge4java", "");
        // Then
        assertThat(issues.getTotalRowCount()).isEqualTo(1);
    }

    @Test
    public void findFileAttachedIssue() throws Exception {
        // Given
        // When
        Page<Issue> issues = Issue.findFileAttachedIssues("nForge4java", "");
        // Then
        assertThat(issues.getTotalRowCount()).isEqualTo(1);
    }

    @Test
    public void findAssigneeByIssueId() {
        // Given
        // When
        Long assignee = Issue.findAssigneeIdByIssueId(1l, 1l);
        // Then
        assertThat(assignee).isEqualTo(1l);
    }

    @Test
    public void isOpen() {
        // Given
        Issue issue = Issue.findById(1l);
        // When
        // Then
        assertThat(issue.isOpen()).isTrue();
    }

    @Test
    public void findIssuesByMilestoneId() throws Exception {
        // Given
        // When
        Page<Issue> issues = Issue.findIssuesByMilestoneId("CUBRID", 9l);
        // Then
        assertThat(issues.getTotalRowCount()).isEqualTo(1);

    }

    @Test
    public void findByMilestoneId() throws Exception {
        // Given
        // When
        List<Issue> issues = Issue.findByMilestoneId(9l);
        // Then
        assertThat(issues.size()).isEqualTo(1);
    }

    @Test
    public void excelSave() throws Exception {
        // Given
        // When
        String excelFilePath = Issue.excelSave(Issue.findIssues("nForge4java", StateType.ALL)
                .getList(), "testExcelSave");
        // Then
        // assertThat(excelFilePath).isEqualTo("testExcelSave.xls");
    }

}
