package models;

import com.avaje.ebean.*;
import controllers.*;
import jxl.*;
import jxl.format.*;
import jxl.write.*;
import models.enumeration.*;
import models.support.*;
import org.joda.time.*;
import play.data.format.*;
import play.data.validation.*;
import play.db.ebean.*;
import utils.*;

import javax.persistence.*;
import java.io.*;
import java.util.*;

import static com.avaje.ebean.Expr.*;

/**
 * @param id              이슈 ID
 * @param title           이슈 제목
 * @param body            이슈 내용
 * @param state           이슈 상태(등록, 진행중, 해결, 닫힘)
 * @param statusType      이슈 상태, 등록 및 진행중 => 미해결, 해결 및 닫힘 => 해결
 * @param date            이슈 등록 날짜
 * @param authorId        이슈 작성자 ID
 * @param project         이슈가 등록된 프로젝트
 * @param issueType       이슈 상세정보의 유형
 * @param assigneeId      이슈에 배정된 담당자 Id
 * @param componentName   컴포넌트
 * @param milestone       이슈가 등록된 마일스톤
 * @param importance      이슈 상세정보의 중요도
 * @param diagnosisResult 이슈 상세정보의 진단유형
 * @param filePath        이슈에 첨부된 파일 주소
 * @param osType          이슈 상세정보의 OS 유형
 * @param browserType     이슈 상세정보의 브라우저 유형
 * @param dbmsType        이슈 상세정보의 DBMS 유형
 * @author Taehyun Park
 *         <p/>
 *         Issue entity mangaed by Ebean
 */
@Entity
public class Issue extends Model {
    private static final long serialVersionUID = -2409072006294045262L;

    private static Finder<Long, Issue> finder = new Finder<Long, Issue>(Long.class, Issue.class);

    public static final int FIRST_PAGE_NUMBER = 0;
    public static final int ISSUE_COUNT_PER_PAGE = 25;
    public static final int NUMBER_OF_ONE_MORE_COMMENTS = 1;
    public static final String DEFAULT_SORTER = "date";
    public static final String TO_BE_ASSIGNED = "TBA";

    @Id
    public Long id;

    @Constraints.Required
    public String title;

    @Constraints.Required
    public String body;

    @Formats.DateTime(pattern = "yyyy-MM-dd")
    public Date date;

    public int numOfComments;
    public Long milestoneId;
    public Long assigneeId;
    public Long authorId;
    public String authorName;
    public IssueState state;
    public StateType stateType;
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

    public Issue() {
        this.date = JodaDateUtil.now();
    }

    public String issueTypeLabel() {
        return issueTypes().get(issueType);
    }

    public Duration ago() {
        return JodaDateUtil.ago(this.date);
    }

    public String reporterName() {
        return User.findNameById(this.authorId);
    }

    /**
     * issueList, issue view에서 assignee의 이름을 출력해준다. 아래의 getAssigneeName과 합쳐질 수
     * 있을듯.
     */
    public String assigneeName() {

        return (this.assigneeId != null ? User.findNameById(this.assigneeId) : "issue.noAssignee");
    }

    /**
     * 이슈의 오픈 상태를 확인한다.
     *
     * @return boolean
     */
    public boolean isOpen() {
        return StateType.OPEN.equals(this.stateType);
    }

    /**
     * View에서 스트링값으로 변환하도록 한다.
     *
     * @return
     */

    /**
     * 해당 이슈의 상태(state) 따라서 탭 기능에서 구분 짖는(stateType) 것이 해결인지 미해결인지 값을 결정해준다.
     * diagnosisResult가 2인 경우는 현 진단결과 선택 란에서 "수정완료"를 의미하므로, 이슈 정상 해결을 의미한다.
     *
     * @param state
     */
    public void updateStateType(Issue issue) {

        if (this.state == null || this.state.equals(IssueState.ASSIGNED)
                || this.state.equals(IssueState.ENROLLED)) {
            this.stateType = StateType.OPEN;
        } else {
            this.stateType = StateType.CLOSED;
        }
    }

    /**
     * 이슈의 담당자(assignee) 배정과 이슈의 진단결과(diagnosisResult)에 따라 이슈의 상태를 정해진 로직에 따라
     * 변경한다.
     *
     * @param issue
     */
    public void updateState(Issue issue) {

        if (isEnrolled(issue)) {
            this.state = IssueState.ENROLLED;
        } else if (isFinished(issue)) {
            this.state = IssueState.FINISHED;
        } else if (isAssigned(issue)) {
            this.state = IssueState.ASSIGNED;
        } else if (isSolved(issue)) {
            this.state = IssueState.SOLVED;
        } else
            this.state = IssueState.ENROLLED;

        updateStateType(issue);
    }

    /**
     * 이슈가 담당자와 진단결과가 없는 경우에는 "등록" 상태임을 확인해준다.
     *
     * @param issue
     * @return boolean
     */
    public boolean isEnrolled(Issue issue) {

        if (issue.assigneeId == null && issue.diagnosisResult.equals("")) {
            return true;
        } else
            return false;
    }

    /**
     * 이슈가 담당자는 배정받고, 진단결과가 없는 경우에는 "진행중" 상태임을 확인해준다.
     *
     * @param issue
     * @return
     */
    public boolean isAssigned(Issue issue) {

        if (issue.assigneeId != null && issue.diagnosisResult.equals("")) {
            return true;
        } else
            return false;
    }

    /**
     * 이슈가 담당자를 배정받고 진단결과가 2번째 수정완료(추후 변경 가능) 인 경우에는 "해결" 상태임을 확인해준다.
     *
     * @param issue
     * @return
     */
    public boolean isSolved(Issue issue) {

        if (issue.assigneeId != null && issue.diagnosisResult.equals("2")) {
            return true;
        } else
            return false;
    }

    /**
     * 이슈가 담당자의 유무와 상관 없이, 진단결과가 2번째 수정완료가 아닌 다른 사유가 존재한 경우에는 "닫힘" 상태임을 확인해준다.
     *
     * @param issue
     * @return
     */

    public boolean isFinished(Issue issue) {

        if (!issue.diagnosisResult.equals("2") && !issue.diagnosisResult.equals("")) {
            return true;
        } else
            return false;
    }

    /**
     * View에서 사용할 이슈 유형에 대한 옵션을 제공한다. Purpose : View에서 Select 부분에서 i18n를 사용하면서
     * 최대한 간단하게 하기 위함.
     *
     * @return
     */
    public static Map<String, String> issueTypes() {
        return new Options("issue.new.detailInfo.issueType.worst",
                "issue.new.detailInfo.issueType.worse", "issue.new.detailInfo.issueType.bad",
                "issue.new.detailInfo.issueType.enhancement",
                "issue.new.detailInfo.issueType.recommendation");
    }

    /**
     * View에서 사용할 OS유형에 대한 옵션을 제공한다. Purpose : View에서 Select 부분에서 i18n를 사용하면서
     * 최대한 간단하게 하기 위함.
     *
     * @return
     */
    public static Map<String, String> osTypes() {
        return new Options("issue.new.environment.osType.windows",
                "issue.new.environment.osType.Mac", "issue.new.environment.osType.Linux");
    }

    /**
     * View에서 사용할 브라우져 유형에 대한 옵션을 제공한다. Purpose : View에서 Select 부분에서 i18n를 사용하면서
     * 최대한 간단하게 하기 위함.
     *
     * @return
     */
    public static Map<String, String> browserTypes() {
        return new Options("issue.new.environment.browserType.ie",
                "issue.new.environment.browserType.chrome",
                "issue.new.environment.browserType.firefox",
                "issue.new.environment.browserType.safari",
                "issue.new.environment.browserType.opera");
    }

    /**
     * View에서 사용할 DBMS 유형에 대한 옵션을 제공한다. Purpose : View에서 Select 부분에서 i18n를 사용하면서
     * 최대한 간단하게 하기 위함.
     *
     * @return
     */
    public static Map<String, String> dbmsTypes() {
        return new Options("issue.new.environment.dbmsType.postgreSQL",
                "issue.new.environment.dbmsType.CUBRID", "issue.new.environment.dbmsType.MySQL");
    }

    /**
     * View에서 사용할 중요도에 대한 옵션을 제공한다. Purpose : View에서 Select 부분에서 i18n를 사용하면서 최대한
     * 간단하게 하기 위함.
     *
     * @return
     */
    public static Map<String, String> importances() {
        return new Options("issue.new.result.importance.highest",
                "issue.new.result.importance.high", "issue.new.result.importance.average",
                "issue.new.result.importance.low", "issue.new.result.importance.lowest");
    }

    /**
     * View에서 사용할 진단 결과에 대한 옵션을 제공한다. Purpose : View에서 Select 부분에서 i18n를 사용하면서
     * 최대한 간단하게 하기 위함.
     *
     * @return
     */
    public static Map<String, String> diagnosisResults() {
        return new Options("issue.new.result.diagnosisResult.bug",
                "issue.new.result.diagnosisResult.fixed",
                "issue.new.result.diagnosisResult.willNotFixed",
                "issue.new.result.diagnosisResult.notaBug",
                "issue.new.result.diagnosisResult.awaitingResponse",
                "issue.new.result.diagnosisResult.unreproducible",
                "issue.new.result.diagnosisResult.duplicated",
                "issue.new.result.diagnosisResult.works4me");
    }

    /**
     * 이슈 id로 이슈를 찾아준다.
     *
     * @param id
     * @return
     */
    public static Issue findById(Long id) {
        return finder.byId(id);
    }

    /**
     * 이슈를 생성한다.
     *
     * @param issue
     * @return
     */
    public static Long create(Issue issue) {
        issue.save();
        if (issue.milestoneId != null) {
            Milestone milestone = Milestone.findById(issue.milestoneId);
            milestone.add(issue);
        }
        issue.updateStateType(issue);
        return issue.id;
    }

    /**
     * 이슈를 삭제한다.
     *
     * @param id
     */
    public static void delete(Long id) {
        Issue issue = finder.byId(id);
        if (!issue.milestoneId.equals(0l) || issue.milestoneId != null) {
            Milestone milestone = Milestone.findById(issue.milestoneId);
            milestone.delete(issue);
        }
        issue.delete();
    }

    /**
     * 이슈를 수정 & 업데이트 한다.
     *
     * @param issue
     */
    public static void edit(Issue issue) {
        Issue previousIssue = findById(issue.id);
        if (issue.filePath == null) {
            issue.filePath = previousIssue.filePath;
        }
        issue.updateStateType(issue);
        issue.update();
    }

    /**
     * 미해결 탭을 눌렀을 때, open 상태의 이슈들을 찾아준다..
     *
     * @param projectName
     * @return
     */
    public static Page<Issue> findOpenIssues(String projectName) {
        return Issue.findIssues(projectName, StateType.OPEN);
    }

    /**
     * 해결 탭을 눌렀을 때, closed 상태의 이슈들을 찾아준다.
     *
     * @param projectName
     * @return
     */
    public static Page<Issue> findClosedIssues(String projectName) {
        return Issue.findIssues(projectName, StateType.CLOSED);
    }

    /**
     * 해당 프로젝트의 State 외의 것들은 기본값들로 이뤄진 이슈들을 찾아준다.
     *
     * @param projectName
     * @param state
     * @return
     */
    public static Page<Issue> findIssues(String projectName, StateType state) {
        return find(projectName, FIRST_PAGE_NUMBER, state, DEFAULT_SORTER, Direction.DESC,
                "", null, false, false);
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
    public static Page<Issue> findFilteredIssues(String projectName, String filter,
                                                 StateType state, boolean commentedCheck, boolean fileAttachedCheck) {
        return find(projectName, FIRST_PAGE_NUMBER, state, DEFAULT_SORTER, Direction.DESC,
                filter, null, commentedCheck, fileAttachedCheck);
    }

    /**
     * 댓글이 달린 이슈들만 찾아준다.
     *
     * @param projectName
     * @param filter
     * @return
     */
    public static Page<Issue> findCommentedIssues(String projectName, String filter) {
        return find(projectName, FIRST_PAGE_NUMBER, StateType.ALL, DEFAULT_SORTER,
                Direction.DESC, filter, null, true, false);
    }

    /**
     * 파일이 첨부된 이슈들만 찾아준다.
     *
     * @param projectName
     * @param filter
     * @return
     */

    public static Page<Issue> findFileAttachedIssues(String projectName, String filter) {
        return find(projectName, FIRST_PAGE_NUMBER, StateType.ALL, DEFAULT_SORTER,
                Direction.DESC, filter, null, false, true);
    }

    /**
     * 마일스톤 Id에 의거해서 해당 마일스톤에 속한 이슈들을 찾아준다.
     *
     * @param projectName
     * @param milestoneId
     * @return
     */
    public static Page<Issue> findIssuesByMilestoneId(String projectName, Long milestoneId) {
        return find(projectName, FIRST_PAGE_NUMBER, StateType.ALL, DEFAULT_SORTER,
                Direction.DESC, "", milestoneId, false, false);
    }

    /**
     * 이슈들을 아래의 parameter들의 조건에 의거하여 Page형태로 반환한다.
     *
     * @param projectName       project ID to finder issues
     * @param pageNumber        Page to display
     * @param state             state type of issue(OPEN or CLOSED
     * @param sortBy            Issue property used for sorting, but, it might be fixed to
     *                          enum type
     * @param order             Sort order(either asc or desc)
     * @param filter            filter applied on the title column
     * @param commentedCheck    filter applied on the commetedCheck column, 댓글이 존재하는 이슈만 필터링
     * @param fileAttachedCheck filter applied on the fileAttachedCheck column, 파일이 업로드된 이슈만
     *                          필터링
     * @return 위의 조건에 따라 필터링된 이슈들을 Page로 반환.
     */
    public static Page<Issue> find(String projectName, int pageNumber, StateType state,
                                   String sortBy, Direction order, String filter, Long milestoneId,
                                   boolean commentedCheck, boolean fileAttachedCheck) {
        OrderParams orderParams = new OrderParams().add(sortBy, order);
        SearchParams searchParams = new SearchParams().add("project.name", projectName,
                Matching.EQUALS);

        if (filter != null && !filter.isEmpty()) {
            searchParams.add("title", filter, Matching.CONTAINS);
        }
        if (milestoneId != null) {
            searchParams.add("milestoneId", milestoneId, Matching.EQUALS);
        }
        if (commentedCheck) {
            searchParams.add("numOfComments", NUMBER_OF_ONE_MORE_COMMENTS, Matching.GE);
        }
        if (fileAttachedCheck) {
            searchParams.add("filePath", "", Matching.NOT_EQUALS);
        }
        if (state == null) {
            state = StateType.ALL;
        }
        switch (state) {
            case OPEN:
                searchParams.add("stateType", StateType.OPEN, Matching.EQUALS);
                break;
            case CLOSED:
                searchParams.add("stateType", StateType.CLOSED, Matching.EQUALS);
                break;
            default:
        }
        return FinderTemplate.getPage(orderParams, searchParams, finder, ISSUE_COUNT_PER_PAGE,
                pageNumber);
    }

    /**
     * 전체 컨텐츠 검색할 때 제목과 내용에 condition.filter를 포함하고 있는 이슈를 검색한다.
     *
     * @param project
     * @param condition
     * @return
     */
    public static Page<Issue> find(Project project, SearchApp.ContentSearchCondition condition) {
        String filter = condition.filter;
        return finder.where().eq("project.id", project.id)
                .or(contains("title", filter), contains("body", filter))
                .findPagingList(condition.pageSize).getPage(condition.page - 1);
    }

    public static Long findAssigneeIdByIssueId(String projectName, Long issueId) {
        return finder.byId(issueId).assigneeId;
    }

    /**
     * 해당 마일스톤아이디로 관련 이슈를 검색한다.
     *
     * @param milestoneId
     * @return
     */
    public static List<Issue> findByMilestoneId(Long milestoneId) {
        SearchParams searchParams = new SearchParams().add("milestoneId", milestoneId,
                Matching.EQUALS);
        return FinderTemplate.findBy(null, searchParams, finder);
    }

    /**
     * JXL 라이브러리를 이용하여 엑셀 파일로 저장하며, 해당 파일이 저장된 주소를 반환한다.
     *
     * @param resultList 엑셀로 저장하고자 하는 리스트
     * @param pageName   엑셀로 저장하고자 하는 목록의 페이지(내용, ex 이슈, 게시물 등) 이름
     * @return
     * @throws Exception
     */
    public static String excelSave(List<Issue> resultList, String pageName) throws Exception {
        String excelFile = pageName + "_" + JodaDateUtil.today().getTime() + ".xls";
        String fullPath = "public/uploadFiles/" + excelFile;
        WritableWorkbook workbook = null;
        WritableSheet sheet = null;

        try {
            WritableFont wf1 = new WritableFont(WritableFont.TIMES, 13, WritableFont.BOLD, false,
                    UnderlineStyle.SINGLE, Colour.BLUE_GREY, ScriptStyle.NORMAL_SCRIPT);
            WritableCellFormat cf1 = new WritableCellFormat(wf1);
            cf1.setBorder(Border.ALL, BorderLineStyle.DOUBLE);
            cf1.setAlignment(Alignment.CENTRE);

            WritableFont wf2 = new WritableFont(WritableFont.TAHOMA, 11, WritableFont.NO_BOLD,
                    false, UnderlineStyle.NO_UNDERLINE, Colour.BLACK, ScriptStyle.NORMAL_SCRIPT);
            WritableCellFormat cf2 = new WritableCellFormat(wf2);
            cf2.setShrinkToFit(true);
            cf2.setBorder(Border.ALL, BorderLineStyle.THIN);
            cf2.setAlignment(Alignment.CENTRE);

            workbook = Workbook.createWorkbook(new File(fullPath));
            sheet = workbook.createSheet(String.valueOf(JodaDateUtil.today().getTime()), 0);

            String[] labalArr = {"ID", "STATE", "TITLE", "ASSIGNEE", "DATE"};

            for (int i = 0; i < labalArr.length; i++) {
                sheet.addCell(new Label(i, 0, labalArr[i], cf1));
                sheet.setColumnView(i, 20);
            }
            for (int i = 1; i < resultList.size() + 1; i++) {
                Issue issue = (Issue) resultList.get(i - 1);
                int colcnt = 0;
                sheet.addCell(new Label(colcnt++, i, issue.id.toString(), cf2));
                sheet.addCell(new Label(colcnt++, i, issue.state.toString(), cf2));
                sheet.addCell(new Label(colcnt++, i, issue.title, cf2));
                sheet.addCell(new Label(colcnt++, i, getAssigneeName(issue.assigneeId), cf2));
                sheet.addCell(new Label(colcnt++, i, issue.date.toString(), cf2));
            }
            workbook.write();
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        } finally {
            try {
                if (workbook != null)
                    workbook.close();
            } catch (WriteException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return excelFile;
    }

    /**
     * excelSave에서 assignee를 리턴해준다.
     *
     * @param uId
     * @return
     */
    private static String getAssigneeName(Long uId) {
        return (uId != null ? User.findNameById(uId) : TO_BE_ASSIGNED);
    }

    // FIXME 이것이 없이 테스트는 잘 작동하나, view에서 댓글이 달린 이슈들을 필터링하는 라디오버튼을 작동시에 이 메쏘드에서
    // 시행하는 동기화 작업 없이는 작동을 하지 않는다.

    /**
     * comment가 delete되거나 create될 때, numOfComment와 comment.size()를 동기화 시켜준다.
     *
     * @param id
     */
    public static void updateNumOfComments(Long id) {

        Issue issue = Issue.findById(id);
        issue.numOfComments = issue.comments.size();
        issue.update();
    }
}
