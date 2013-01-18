package models;

import com.avaje.ebean.*;
import controllers.*;
import jxl.*;
import jxl.format.*;
import jxl.format.Colour;
import jxl.format.BorderLineStyle;
import jxl.format.Border;
import jxl.format.Alignment;
import jxl.write.*;
import models.enumeration.*;
import models.resource.ProjectResource;
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
 * @param state           이슈 상태(열림, 닫힘)
 * @param date            이슈 등록 날짜
 * @param authorId        이슈 작성자 ID
 * @param project         이슈가 등록된 프로젝트
 * @param issueType       이슈 상세정보의 유형
 * @param assigneeId      이슈에 배정된 담당자 Id
 * @param milestone       이슈가 등록된 마일스톤
 * @param importance      이슈 상세정보의 중요도
 * @author Taehyun Park
 *         <p/>
 *         Issue entity mangaed by Ebean
 */
@Entity
public class Issue extends Model {
    private static final long serialVersionUID = -2409072006294045262L;

    public static Finder<Long, Issue> finder = new Finder<Long, Issue>(Long.class, Issue.class);

    public static final int FIRST_PAGE_NUMBER = 0;
    public static final int ISSUE_COUNT_PER_PAGE = 25;
    public static final int NUMBER_OF_ONE_MORE_COMMENTS = 1;
    public static final String DEFAULT_SORTER = "date";
    public static final String TO_BE_ASSIGNED = "TBA";

    @Id
    public Long id;

    @Constraints.Required
    public String title;

    @Column(length=4000)
    public String body;

    @Formats.DateTime(pattern = "yyyy-MM-dd")
    public Date date;

    public int numOfComments;
    public Long milestoneId;
    public Long authorId;
    public String authorLoginId;
    public String authorName;
    public State state;
    @OneToMany
    public List<IssueDetail> issueDetails;

    @ManyToOne
    public Project project;

    @OneToMany(mappedBy = "issue", cascade = CascadeType.ALL)
    public List<IssueComment> comments;

    @ManyToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    public Set<IssueLabel> labels;

    @ManyToOne(cascade = CascadeType.ALL)
    public Assignee assignee;

    public Issue(String title) {
        this.title = title;
        this.date = JodaDateUtil.now();
    }

    public Duration ago() {
        return JodaDateUtil.ago(this.date);
    }

    /**
     * issueList, issue view에서 assignee의 이름을 출력해준다. 아래의 getAssigneeName과 합쳐질 수
     * 있을듯.
     */
    public String assigneeName() {
        return (this.assignee != null ? assignee.user.name : null);
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
        return issue.id;
    }

    /**
     * 이슈를 삭제한다.
     *
     * @param id
     */
    public static void delete(Long id) {
        Issue issue = finder.byId(id);
        if (issue.milestoneId != null && !issue.milestoneId.equals(0l)) {
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
        issue.update();
    }

    public static int countIssues(Long projectId, State state) {
        if (state == State.ALL) {
            return finder.where().eq("project.id", projectId).findRowCount();
        } else {
            return finder.where().eq("project.id", projectId).eq("state", state).findRowCount();
        }
    }

    /**
     * 미해결 탭을 눌렀을 때, open 상태의 이슈들을 찾아준다..
     *
     * @param projectId
     * @return
     */
    public static Page<Issue> findOpenIssues(Long projectId) {
        return Issue.findIssues(projectId, State.OPEN);
    }

    /**
     * 해결 탭을 눌렀을 때, closed 상태의 이슈들을 찾아준다.
     *
     * @param projectId
     * @return
     */
    public static Page<Issue> findClosedIssues(Long projectId) {
        return Issue.findIssues(projectId, State.CLOSED);
    }

    /**
     * 해당 프로젝트의 State 외의 것들은 기본값들로 이뤄진 이슈들을 찾아준다.
     *
     * @param projectName
     * @param state
     * @return
     */
    public static Page<Issue> findIssues(Long projectId, State state) {
        return find(projectId, FIRST_PAGE_NUMBER, state, DEFAULT_SORTER, Direction.DESC, "", null,
                null, false);
    }

    /**
     * 검색창에서 제공된 query(filter)와 댓글과 파일첨부된 이슈만 찾아주는 체크박스의 값에 따라 필터링된 이슈들을 찾아준다.
     *
     * @param projectId
     * @param filter
     * @param state
     * @param commentedCheck
     * @return
     */
    public static Page<Issue> findFilteredIssues(Long projectId, String filter, State state,
            boolean commentedCheck) {
        return find(projectId, FIRST_PAGE_NUMBER, state, DEFAULT_SORTER, Direction.DESC, filter,
                null, null, commentedCheck);
    }

    /**
     * 댓글이 달린 이슈들만 찾아준다.
     *
     * @param projectId
     * @param filter
     * @return
     */
    public static Page<Issue> findCommentedIssues(Long projectId, String filter) {
        return find(projectId, FIRST_PAGE_NUMBER, State.ALL, DEFAULT_SORTER, Direction.DESC,
                filter, null, null, true);
    }

    /**
     * 마일스톤 Id에 의거해서 해당 마일스톤에 속한 이슈들을 찾아준다.
     *
     * @param projectId
     * @param milestoneId
     * @return
     */
    public static Page<Issue> findIssuesByMilestoneId(Long projectId, Long milestoneId) {
        return find(projectId, FIRST_PAGE_NUMBER, State.ALL, DEFAULT_SORTER, Direction.DESC, "",
                milestoneId, null, false);
    }

    /**
     * 이슈들을 아래의 parameter들의 조건에 의거하여 Page형태로 반환한다.
     *
     * @param projectId
     *            project ID to finder issues
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
     * @return 위의 조건에 따라 필터링된 이슈들을 Page로 반환.
     */
    public static Page<Issue> find(Long projectId, int pageNumber, State state, String sortBy,
            Direction order, String filter, Long milestoneId, Set<Long> labelIds,
            boolean commentedCheck) {
        OrderParams orderParams = new OrderParams().add(sortBy, order);
        SearchParams searchParams = new SearchParams()
                .add("project.id", projectId, Matching.EQUALS);

        if (filter != null && !filter.isEmpty()) {
            searchParams.add("title", filter, Matching.CONTAINS);
        }
        if (milestoneId != null) {
            searchParams.add("milestoneId", milestoneId, Matching.EQUALS);
        }
        if (labelIds != null) {
            // searchParams.add("labels.id", labelIds, Matching.IN);
            for (Long labelId : labelIds) {
                searchParams.add("labels.id", labelId, Matching.EQUALS);
            }
        }
        if (commentedCheck) {
            searchParams.add("numOfComments", NUMBER_OF_ONE_MORE_COMMENTS, Matching.GE);
        }

        if (state == null) {
            state = State.ALL;
        }
        switch (state) {
            case OPEN:
                searchParams.add("state", State.OPEN, Matching.EQUALS);
                break;
            case CLOSED:
                searchParams.add("state", State.CLOSED, Matching.EQUALS);
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

    public static Long findAssigneeIdByIssueId(Long issueId) {
        return finder.byId(issueId).assignee.user.id;
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
     * @throws WriteException
     * @throws IOException
     * @throws Exception
     */
    public static File excelSave(List<Issue> resultList, String pageName) throws WriteException, IOException {
        String excelFile = pageName + "_" + JodaDateUtil.today().getTime() + ".xls";
        String fullPath = "public/uploadFiles/" + excelFile;
        WritableWorkbook workbook = null;
        WritableSheet sheet = null;

        WritableFont wf1 = new WritableFont(WritableFont.TIMES, 13, WritableFont.BOLD, false,
                UnderlineStyle.SINGLE, Colour.BLUE_GREY, ScriptStyle.NORMAL_SCRIPT);
        WritableCellFormat cf1 = new WritableCellFormat(wf1);
        cf1.setBorder(Border.ALL, BorderLineStyle.DOUBLE);
        cf1.setAlignment(Alignment.CENTRE);

        WritableFont wf2 = new WritableFont(WritableFont.TAHOMA, 11, WritableFont.NO_BOLD, false, UnderlineStyle.NO_UNDERLINE, Colour.BLACK, ScriptStyle.NORMAL_SCRIPT);
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
            Issue issue = resultList.get(i - 1);
            int colcnt = 0;
            sheet.addCell(new Label(colcnt++, i, issue.id.toString(), cf2));
            sheet.addCell(new Label(colcnt++, i, issue.state.toString(), cf2));
            sheet.addCell(new Label(colcnt++, i, issue.title, cf2));
            sheet.addCell(new Label(colcnt++, i, getAssigneeName(issue.assignee), cf2));
            sheet.addCell(new Label(colcnt++, i, issue.date.toString(), cf2));
        }
        workbook.write();

        try {
            if (workbook != null)
                workbook.close();
        } catch (WriteException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new File(fullPath);
    }

    /**
     * excelSave에서 assignee를 리턴해준다.
     *
     * @param uId
     * @return
     */
    private static String getAssigneeName(Assignee assignee) {
        return (assignee != null ? assignee.user.name : TO_BE_ASSIGNED);
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

	public void addIssueDetails(IssueDetail issueDetail) {
		if(this.issueDetails == null) {
			this.issueDetails = new ArrayList<IssueDetail>();
		}
		this.issueDetails.add(issueDetail);
	}

	public boolean isOpen() {
	    return this.state == State.OPEN;
	}

	public boolean isClosed() {
	    return this.state == State.CLOSED;
	}

	public ProjectResource asResource() {
	    return new ProjectResource() {
	        @Override
	        public Long getId() {
	            return null;
	        }

	        @Override
	        public Project getProject() {
	            return project;
	        }

	        @Override
	        public Resource getType() {
	            return Resource.ISSUE_POST;
	        }
	    };
	}

    public ProjectResource fieldAsResource(final Resource resourceType) {
        return new ProjectResource() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public Project getProject() {
                return project;
            }

            @Override
            public Resource getType() {
                return resourceType;
            }
        };
    }

    public ProjectResource stateAsResource() {
        return fieldAsResource(Resource.ISSUE_STATE);
    }

    public ProjectResource milestoneAsResource() {
        return fieldAsResource(Resource.ISSUE_MILESTONE);
    }

    public ProjectResource assigneeAsResource() {
        return fieldAsResource(Resource.ISSUE_ASSIGNEE);
    }
}