package models;

import static org.fest.assertions.Assertions.assertThat;

import java.util.List;

import models.enumeration.IssueState;
import models.enumeration.StateType;

import org.junit.Ignore;
import org.junit.Test;

import utils.JodaDateUtil;

import com.avaje.ebean.Page;

import controllers.SearchApp;

public class IssueTest extends ModelTest<Issue> {

    @Test
    public void create() throws Exception {
        // Given
        Issue issue = new Issue();
        issue.title = "불필요한 로그 출력 코드 제거test";
        issue.date = JodaDateUtil.today();
        issue.state = IssueState.ASSIGNED;
        issue.authorId = User.findById(1l).id;
        issue.milestoneId = 4l;
        issue.issueType = "issue.new.detailInfo.issueType.worst";
        // When
        // Then
        assertThat(Issue.create(issue)).isNotNull();
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
        Issue issue = Issue.findById(3l);
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
        Issue.delete(3l);
        flush();
        // Then
        assertThat(Issue.findById(3l)).isNull();
        assertThat(IssueComment.findById(1l)).isNull();
    }

    @Test
    public void findOpenIssues() throws Exception {
        // Given
        // When
        Page<Issue> issues = Issue.findOpenIssues("nForge4java");
        // Then
        assertThat(issues.getTotalRowCount()).isEqualTo(3);
    }

    @Test
    public void findClosedIssues() throws Exception {
        // Given
        // When
        Page<Issue> issues = Issue.findClosedIssues("nForge4java");
        // Then
        assertThat(issues.getTotalRowCount()).isEqualTo(4);
    }

    @Test
    public void findFilteredIssues() throws Exception {

        // Given
        // When
        Page<Issue> issues = Issue.findFilteredIssues("nForge4java", "로그", StateType.OPEN, false,
                true);
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
        Long assignee = Issue.findAssigneeIdByIssueId("nForge4java", 2l);
        // Then
        assertThat(assignee).isEqualTo(2l);
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
        Page<Issue> issues = Issue.findIssuesByMilestoneId("CUBRID", 5l);
        // Then
        assertThat(issues.getTotalRowCount()).isEqualTo(2);

    }

    @Test
    public void findByMilestoneId() throws Exception {
        // Given
        // When
        List<Issue> issues = Issue.findByMilestoneId(6l);
        // Then
        assertThat(issues.size()).isEqualTo(1);
    }

    @Test
    @Ignore
    public void excelSave() throws Exception {
        // Given
        // When
       //  String excelFilePath = Issue.excelSave(Issue.findIssues("nForge4java", StateType.ALL)
        //                .getList(), "testExcelSave");
        // Then
        // assertThat(excelFilePath).isEqualTo("testExcelSave.xls");
    }
    
    @Test
    public void updateNumOfComments() throws Exception {
        // Given
        IssueComment.delete(1l);
        flush();
        // When
        Issue.updateNumOfComments(3l);
        // Then
        assertThat(Issue.findById(3l).numOfComments).isEqualTo(0);
    }
    
	@Test
	public void findIssues() {
		// Given
		SearchApp.ContentSearchCondition condition = new SearchApp.ContentSearchCondition();
		condition.filter = "git";
		condition.page = 1;
		condition.pageSize = 10;
		Project project = Project.findById(1l);

		// When
		Page<Issue> issuePage = Issue.findIssues(project, condition);

		// Then
		List<Issue> list = issuePage.getList();
		assertThat(list.size()).isEqualTo(2);
	}
}
