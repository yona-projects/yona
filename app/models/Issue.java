/**
 * @author Taehyun Park
 */
package models;

import java.util.Date;
import java.util.List;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

import play.data.format.Formats;
import play.data.validation.Constraints;
import play.db.ebean.Model;
import utils.JodaDateUtil;

import com.avaje.ebean.Page;

@Entity
public class Issue extends Model {
    private static final long serialVersionUID = 1L;

    public final static String ORDERBY_ASCENDING = "asc";
    public final static String ORDERBY_DESCENDING = "desc";

    public final static String SORTBY_ID = "id";
    public final static String SORTBY_TITLE = "title";
    public final static String SORTBY_AGE = "date";

    public final static int FIRST_PAGE_NUMBER = 0;

    public static final int STATUS_ENROLLED = 1; // 등록
    public static final int STATUS_ASSINGED = 2; // 진행중
    public static final int STATUS_SOLVED = 3; // 해결
    public static final int STATUS_FINISHED = 4; // 닫힘
    public static final int STATUS_OPEN = 5; // 미해결
    public static final int STATUS_CLOSED = 6; // 해결
    public static final int STATUS_NONE = 0; // 전체

    // TODO 추후 세부정보의 해당 이슈 유형이 결정나면 추후 설정
    public static final int DEFECTTYPE_WORST = 1; // 치명결함
    public static final int DEFECTTYPE_WORSE = 2; // 중결함
    public static final int DEFECTTYPE_BAD = 3; // 경결함
    public static final int DEFECTTYPE_SIMPLEIMPROVEMENT = 4; // 단순개선
    public static final int DEFECTTYPE_RECOMMENDATION = 5; // 권고사항

    public static final int ISSUE_COUNT_PER_PAGE = 25;

    /**
     * @ userId : 글쓴이 title 제목 body 이슈 내용 status statusType date issueType
     * reponsibleMemberId comp milestone importance diagnosisType commentCount
     * filePath
     */
    @Id
    public Long id;
    public Long userId; // 글쓴이
    @Constraints.Required
    public String title; // 제목
    @Constraints.Required
    public String body; // 글 내용
    public Long projectId;
    public int status; // 이슈 상태
    public int statusType;
    @Formats.DateTime(pattern = "YYYY/MM/DD/hh/mm/ss")
    public Date date; // 이슈 작성일
    // 세부정보
    public int issueType; // 이슈유형
    @ManyToOne
    public User responsibleMember; // 담당자
    public String componentName; // 컴포넌트
    @ManyToOne
    public Milestone milestone; // 적용된 마일스톤
    public int importance;// 중요도
    public int diagnosisResult;// 진단유형
    public int commentCount;
    // TODO 첨부 파일이 여러개인경우는?
    public String filePath;
    // TODO 이슈 유형이나 진단유형처럼 int 값을 할지?
    public String osType;
    public String browserType;
    public String dbmsType;

    public Issue() {
        this.date = JodaDateUtil.today();
        this.commentCount = 0;
    }

    public String status() {
        if (this.status == STATUS_ENROLLED) {
            return "등록";
        } else if (this.status == STATUS_ASSINGED) {
            return "진행중";
        } else if (this.status == STATUS_SOLVED) {
            return "해결";
        } else if (this.status == STATUS_FINISHED) {
            return "닫힘";
        } else
            return "등록";
    }

    public void setStatusType(int status) {
        if (this.status == STATUS_ASSINGED || this.status == STATUS_ENROLLED) {
            this.statusType = STATUS_OPEN;
        } else if (this.status == STATUS_CLOSED
                || this.status == STATUS_FINISHED) {
            this.statusType = STATUS_SOLVED;
        }
    }

    private static Finder<Long, Issue> find = new Finder<Long, Issue>(
            Long.class, Issue.class);

    public static Long create(Issue issue) {
        issue.save();
        return issue.id;
    }

    public static void delete(Long id) {
        find.ref(id).delete();
        IssueComment.deleteByIssueId(id);
    }

    /**
     * Return a page of Issues
     * 
     * @param pageNum
     *            Page to display
     * @param pageSize
     *            Number of issues per page
     * @param sortBy
     *            Computer property used for sorting
     * @param order
     *            Sort order (either or asc or desc)
     * @param filter
     *            Filter applied on the title column
     * @param statusType
     *            status type of issue(OPEN or CLOSED), '0' means ALL
     * 
     */
    //TODO 첨부파일 있는 것들 검색도 여기에 붙일것인가? 이게 과연 잘하는 짓일까?
    public static Page<Issue> page(Long projectId, int pageNum, int pageSize,
            String sortBy, String order, String filter, int statusType, int commentCount) {
        Page<Issue> pageIssues = null;
        if (statusType == 0) {
            pageIssues = find.where().ilike("title", "%" + filter + "%")
                    .eq("projectId", projectId).orderBy(sortBy + " " + order)
                    .findPagingList(pageSize).getPage(pageNum);
        } 
        else if(commentCount==1){
            pageIssues = find.where().ilike("title", "%" + filter + "%").ge("commentCount", 1)
                    .eq("projectId", projectId).orderBy(sortBy + " " + order)
                    .findPagingList(pageSize).getPage(pageNum);
        }else {
            pageIssues = find.where().eq("statusType", statusType)
                    .eq("projectId", projectId).orderBy(sortBy + " " + order)
                    .findPagingList(pageSize).getPage(pageNum);
        }
        return pageIssues;
    }

    public static Issue findById(Long id) {
        return find.byId(id);
    }

    public static List<Issue> findByTitle(String keyword) {
        return find.where().contains("title", keyword).findList();
    }

    // public static Page<Issue> findOnlyClosedIssues(int pageNum) {
    // ExpressionFactory exprFactory = find.getExpressionFactory();
    //
    // return find
    // .where()
    // .or(exprFactory.eq("status", STATUS_FINISHED),
    // exprFactory.eq("status", STATUS_SOLVED))
    // .findPagingList(numIssueOnePage).getPage(pageNum - 1);
    // }

    public static void countUpCommentCounter(Long issueId) {
        Issue issue = findById(issueId);
        issue.commentCount++;
        issue.update();
    }

}
