package models;

import com.avaje.ebean.Page;
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

import javax.persistence.*;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

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
 *            이슈 상태(등록, 진행중, 해결, 닫힘), !코드 리팩토링 대상
 * @param statusType
 *            이슈 상태, !코드 리팩토링 대상
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
 * @param issueComments
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
    public IssueState state;
    public IssueStateType stateType;
    @Formats.DateTime(pattern = "yyyy-MM-dd")
    public Date date;
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
    @ManyToOne
    public User reporter;
    @ManyToOne
    public User assignee;
    @ManyToOne
    public Milestone milestone;
    @OneToMany(mappedBy = "issue", cascade = CascadeType.ALL)
    public Set<IssueComment> issueComments;
    public int commentCount;

    public Issue() {
        this.date = JodaDateUtil.today();
    }

    /**
     * View에서 스트링값으로 변환하도록 한다. !!! 코드 리팩토링 대상
     * 
     * @return
     */
    public String state() {
        if (this.state == IssueState.ASSIGNED) {
            return "진행중";
        } else if (this.state == IssueState.SOLVED) {
            return "해결";
        } else if (this.state == IssueState.FINISHED) {
            return "닫힘";
        } else
            return "등록";
    }

    /**
     * 해당 이슈에 따라서 해결인지 미해결인지 값을 결정해준다. !!! 코드 리팩토링 대상
     * 
     * @param status
     */

    public void updateStatusType(IssueState state) {
        if (this.state == IssueState.ASSIGNED
                || this.state == IssueState.ENROLLED) {
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
        return issue.id;
    }

    /**
     * 이슈를 삭제한다.
     * 
     * @param id
     */
    public static void delete(Long id) {
        find.ref(id).delete();
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
     * 해결 탭을 눌렀을 때, closed 상태의 이슈들을 찾아준다..
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
                DEFAULT_SORTER, Direction.DESC, "", false, false);
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
                DEFAULT_SORTER, Direction.DESC, filter, commentedCheck,
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
                DEFAULT_SORTER, Direction.DESC, filter, true, false);
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
                DEFAULT_SORTER, Direction.DESC, filter, false, true);
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
            String filter, boolean commentedCheck, boolean fileAttachedCheck) {

        OrderParams orderParams = new OrderParams().add(sortBy, order);
        SearchParams searchParams = new SearchParams().add("project.name",
                projectName, Matching.EQUALS);
        searchParams.add("title", filter, Matching.CONTAINS);

        if (commentedCheck) {
            searchParams.add("commentCount", 1, Matching.GE);
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
     * 이슈에 대한 댓글이 달렸을 경우, 코멘트의 갯수를 올려준다. 하지만 코드 리팩토리 대상
     * 
     * @param issue
     */

    public static void countUpCommentCounter(Issue issue) {
        issue.commentCount++;
        issue.update();
    }

    /**
     * 이슈 상세 조회시에, 이슈에 달린 코멘트를 제공한다.
     * 
     * @param issueComment
     */
    public void addIssueComment(IssueComment issueComment) {
        if (this.issueComments == null) {
            this.issueComments = new HashSet<IssueComment>();
        }
        this.issueComments.add(issueComment);
        issueComment.issue = this;
    }
}
