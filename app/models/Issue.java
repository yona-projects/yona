package models;

import static models.enumeration.IssueState.ASSIGNED;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;

import models.enumeration.Direction;
import models.enumeration.IssueState;
import models.enumeration.IssueStateType;
import models.enumeration.Matching;
import models.support.FinderTemplate;
import models.support.OrderParams;
import models.support.SearchParams;
import play.data.format.Formats;
import play.data.validation.Constraints;
import play.db.ebean.Model;
import utils.JodaDateUtil;

import com.avaje.ebean.Page;

/**
 * @author Taehyun Park
 * 
 *         Issue entity mangaed by Ebean
 * @param id
 *            이슈 ID
 * @param title
 *            이슈 제목
 * @param body
 *            이슈 내용
 * @param status
 *            이슈 상태(등록, 진행중, 해결, 닫힘)
 * @param statusType
 *            이슈 상태, 등록 및 진행중 => 미해결, 해결 및 닫힘 => 해결
 * @param date
 *            등록된 날짜
 * @param reporter
 *            이슈 작성자
 * @param project
 *            이슈가 등록된 프로젝트
 * @param issueType
 *            이슈 상세정보의 유형
 * @param assignee
 *            담당자
 * @param componentName
 *            컴포넌트
 * @param milestone
 *            이슈가 등록된 마일스톤
 * @param importance
 *            이슈 상세정보의 중요도
 * @param diagnosisResult
 *            이슈 상세정보의 진단유형
 * @param filePath
 *            이슈에 첨부된 파일 주소
 * @param osType
 *            이슈 상세정보의 OS 유형
 * @param browserType
 *            이슈 상세정보의 브라우저 유형
 * @param dbmsType
 *            이슈 상세정보의 DBMS 유형
 * @param comments
 *            이슈에 등록된 댓글의 갯수 !코드 리팩토링 예정
 */
@Entity
public class Issue extends Model {
    private static final long serialVersionUID = 1L;
    private static Finder<Long, Issue> find = new Finder<Long, Issue>(
            Long.class, Issue.class);

    public static final int FIRST_PAGE_NUMBER = 0;
    public static final int ISSUE_COUNT_PER_PAGE = 25;
    public static final int NUMBER_OF_ONE_MORE_COMMENTS = 1;
    public static final int NUMBER_OF_NO_COMMENT = 0;
    public static final String DEFAULT_SORTER = "date";

    @Id
    public Long id;

    @Constraints.Required
    public String title;

    @Constraints.Required
    public String body;

    @Formats.DateTime(pattern = "yyyy-MM-dd")
    public Date date;

    public String milestoneId;
    public Long assigneeId;
    public Long reporterId;
    public IssueState state;
    public IssueStateType stateType;
    public String issueType;
    public String componentName;
    // TODO 첨부 파일이 여러개인경우는?
    public String filePath;
    public String osType;
    public String browserType;
    public String dbmsType;
    public String importance;
    public String diagnosisResult;

    @ManyToOne
    public Project project;

    @OneToMany(mappedBy = "issue", cascade = CascadeType.ALL)
    public List<IssueComment> comments = new ArrayList<IssueComment>();
    
    public int numOfIssueComments;

    public Issue() {
        this.date = JodaDateUtil.today();
        this.numOfIssueComments = comments.size();
    }

    /**
     * View에서 스트링값으로 변환하도록 한다. !!! 코드 리팩토링 대상
     * 
     * @return
     */
    public String state() {
        if (this.state == ASSIGNED) {
            return "진행중";
        } else if (this.state == IssueState.SOLVED) {
            return "해결";
        } else if (this.state == IssueState.FINISHED) {
            return "닫힘";
        } else
            return "등록";
    }

    /**
     * 해당 이슈에 따라서 해결인지 미해결인지 값을 결정해준다.
     * 
     * @param status
     */

    public void updateStatusType(IssueState state) {
        if (this.state == ASSIGNED || this.state == IssueState.ENROLLED) {
            this.stateType = IssueStateType.OPEN;
        } else if (this.state == IssueState.SOLVED
                || this.state == IssueState.FINISHED) {
            this.stateType = IssueStateType.CLOSED;
        }
    }

    /**
     * 이슈를 생성한다.
     * 
     * @param issue
     * @return
     */
    public static Long create(Issue issue) {
        issue.save();
        if (!issue.milestoneId.equals("none")) {
            Milestone milestone = Milestone.findById(Long
                    .valueOf(issue.milestoneId));
            milestone.add(issue);
        }
        return issue.id;
    }

    /**
     * 이슈를 삭제한다.
     * 
     * @param id
     */
    public static void delete(Long id) {
        Issue issue = find.byId(id);
        if (!issue.milestoneId.equals("none")) {
            Milestone milestone = Milestone.findById(Long
                    .valueOf(issue.milestoneId));
            milestone.delete(issue);
        }
        issue.delete();
        IssueComment.deleteByIssueId(id);
    }

    /**
     * 미해결 탭을 눌렀을 때, open 상태의 이슈들을 찾아준다..
     * 
     * @param projectName
     * @return
     */
    public static Page<Issue> findOpenIssues(String projectName) {
        return Issue.findIssues(projectName, IssueStateType.OPEN);
    }

    /**
     * 해결 탭을 눌렀을 때, closed 상태의 이슈들을 찾아준다.
     * 
     * @param projectName
     * @return
     */
    public static Page<Issue> findClosedIssues(String projectName) {
        return Issue.findIssues(projectName, IssueStateType.CLOSED);
    }

    /**
     * 해당 프로젝트의 State 외의 것들은 기본값들로 이뤄진 이슈들을 찾아준다.
     * 
     * @param projectName
     * @param state
     * @return
     */
    public static Page<Issue> findIssues(String projectName,
            IssueStateType state) {
        return findIssues(projectName, FIRST_PAGE_NUMBER, state,
                DEFAULT_SORTER, Direction.DESC, "", "none", false, false);
    }

    /**
     * 검색창에서 제공된 query(filter)와 댓글과 파일첨부된 이슈만 찾아주는 체크박스의 값에 따라 필터링된 이슈들을 찾아준다.
     * 
     * @param projectName
     * @param filter
     * @param state
     * @param commentedCheck
     * @param fileAttachedCheck
     * @return
     */
    public static Page<Issue> findFilteredIssues(String projectName,
            String filter, IssueStateType state, boolean commentedCheck,
            boolean fileAttachedCheck) {
        return findIssues(projectName, FIRST_PAGE_NUMBER, state,
                DEFAULT_SORTER, Direction.DESC, filter, "none", commentedCheck,
                fileAttachedCheck);
    }

    /**
     * 댓글이 달린 이슈들만 찾아준다.
     * 
     * @param projectName
     * @param filter
     * @return
     */
    public static Page<Issue> findCommentedIssues(String projectName,
            String filter) {
        return findIssues(projectName, FIRST_PAGE_NUMBER, IssueStateType.ALL,
                DEFAULT_SORTER, Direction.DESC, filter, "none", true, false);
    }

    /**
     * 파일이 첨부된 이슈들만 찾아준다.
     * 
     * @param projectName
     * @param filter
     * @return
     */

    public static Page<Issue> findFileAttachedIssues(String projectName,
            String filter) {
        return findIssues(projectName, FIRST_PAGE_NUMBER, IssueStateType.ALL,
                DEFAULT_SORTER, Direction.DESC, filter, "none", false, true);
    }

    public static Page<Issue> findIssuesByMilestoneId(String projectName,
            String milestoneId) {
        return findIssues(projectName, FIRST_PAGE_NUMBER, IssueStateType.ALL,
                DEFAULT_SORTER, Direction.DESC, "", milestoneId, false, false);
    }

    /**
     * Return a page of Issues
     * 
     * @param projectName
     *            project ID to find issues
     * @param pageNumber
     *            Page to display
     * @param state
     *            state type of issue(OPEN or CLOSED
     * @param sortBy
     *            Issue property used for sorting, but, it might be fixed to
     *            enum type
     * @param order
     *            Sort order(either asc or desc)
     * @param filter
     *            filter applied on the title column
     * @param commentedCheck
     *            filter applied on the commetedCheck column, 댓글이 존재하는 이슈만 필터링
     * @param fileAttachedCheck
     *            filter applied on the fileAttachedCheck column, 파일이 업로드된 이슈만
     *            필터링
     * @return 위의 조건에 따라 필터링된 이슈들을 Page로 반환.
     */
    public static Page<Issue> findIssues(String projectName, int pageNumber,
            IssueStateType state, String sortBy, Direction order,
            String filter, String milestone, boolean commentedCheck,
            boolean fileAttachedCheck) {

        OrderParams orderParams = new OrderParams().add(sortBy, order);
        SearchParams searchParams = new SearchParams().add("project.name",
                projectName, Matching.EQUALS);
        searchParams.add("title", filter, Matching.CONTAINS);
        if (!milestone.equals("none")) {
            searchParams.add("milestoneId", Long.valueOf(milestone),
                    Matching.EQUALS); 
        }
        if (commentedCheck) {
            searchParams.add("numOfIssueComments", 1, Matching.GE);
        }
        if (fileAttachedCheck) {
            searchParams.add("filePath", "", Matching.NOT_EQUALS);
        }
        if (state == null) {
            state = IssueStateType.ALL;
        }
        switch (state) {
        case OPEN:
            searchParams.add("stateType", IssueStateType.OPEN, Matching.EQUALS);
            break;
        case CLOSED:
            searchParams.add("stateType", IssueStateType.CLOSED,
                    Matching.EQUALS);
            break;
        }
        return FinderTemplate.getPage(orderParams, searchParams, find,
                ISSUE_COUNT_PER_PAGE, pageNumber);
    }

    /**
     * 이슈 id로 이슈를 찾아준다.
     * 
     * @param id
     * @return
     */
    public static Issue findById(Long id) {
        return find.byId(id);
    }

    /**
     * 이슈 상세 조회시에, 이슈에 달린 코멘트를 제공한다.
     * 
     * @param issueComment
     */
    public void addIssueComment(IssueComment issueComment) {
        issueComment.issue = this;
        issueComment.save();

    }

    public static Long findAssigneeIdByIssueId(Long projectId, Long issueId) {
        return find.where().eq("id", issueId).findUnique().assigneeId;
    }

    /**
     * 이슈의 오픈 상태를 확인한다.
     * @return boolean
     */
    public boolean isOpen() {
        return IssueStateType.OPEN.equals(this.stateType);
    }

    /**
     * 해당 마일스톤아이디로 관련 이슈를 검색한다.
     * @param milestoneId
     * @return
     */
    public static List<Issue> findByMilestoneId(Long milestoneId) {
        SearchParams searchParams = new SearchParams()
            .add("milestoneId", String.valueOf(milestoneId), Matching.EQUALS);

        return FinderTemplate.findBy(null, searchParams ,find);
    }

}
