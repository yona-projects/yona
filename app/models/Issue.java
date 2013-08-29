package models;

import static com.avaje.ebean.Expr.icontains;

import com.avaje.ebean.Ebean;
import com.avaje.ebean.ExpressionList;
import controllers.IssueApp;
import jxl.Workbook;
import jxl.format.Alignment;
import jxl.format.Border;
import jxl.format.BorderLineStyle;
import jxl.format.Colour;
import jxl.format.*;
import jxl.write.*;
import models.enumeration.ResourceType;
import models.enumeration.State;
import models.resource.Resource;
import utils.JodaDateUtil;

import javax.persistence.*;

import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * 이슈
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "number"}))
public class Issue extends AbstractPosting implements LabelOwner {
    /**
     * @param id              이슈 ID
     * @param title           이슈 제목
     * @param body            이슈 내용
     * @param state           이슈 상태(열림, 닫힘)
     * @param createdDate            이슈 등록 날짜
     * @param authorId        이슈 작성자 ID
     * @param project         이슈가 등록된 프로젝트
     * @param assigneeId      이슈에 배정된 담당자 Id
     * @param milestone       이슈가 등록된 마일스톤
     * @param importance      이슈 상세정보의 중요도
     * @author Yobi TEAM
     */
    private static final long serialVersionUID = -2409072006294045262L;

    public static final Finder<Long, Issue> finder = new Finder<>(Long.class, Issue.class);

    public static final String DEFAULT_SORTER = "createdDate";
    public static final String TO_BE_ASSIGNED = "TBA";

    public State state;

    public static List<State> availableStates = new ArrayList<>();
    static {
        availableStates.add(State.OPEN);
        availableStates.add(State.CLOSED);
    }

    @ManyToOne
    public Milestone milestone;

    @ManyToMany(fetch = FetchType.EAGER, cascade = CascadeType.REMOVE)
    public Set<IssueLabel> labels;

    @ManyToOne
    public Assignee assignee;

    @OneToMany(cascade = CascadeType.ALL, mappedBy="issue")
    public List<IssueComment> comments;

    @OneToMany(cascade = CascadeType.ALL, mappedBy="issue")
    public List<IssueEvent> events;

    /**
     * @return
     * @see models.AbstractPosting#computeNumOfComments()
     */
    public int computeNumOfComments() {
        return comments.size();
    }

    /**
     * @return
     * @see models.AbstractPosting#getFinder()
     */
    @Override
    public Finder<Long, ? extends AbstractPosting> getFinder() {
        return finder;
    }

    /**
     * {@link Project}의 {@link Issue} 마다 유일하게 증가하는 번호를 갖도록
     * 최근 이슈 번호를 반환한다.
     *
     * @return
     * @see models.Project#increaseLastIssueNumber()
     */
    @Override
    protected Long increaseNumber() {
        return project.increaseLastIssueNumber();
    }

    protected void fixLastNumber() {
        project.fixLastIssueNumber();
    }

    /**
     * issueList, issue view에서 assignee의 이름을 출력해준다.
     * 아래의 getAssigneeName과 합쳐질 수 있을듯.
     */
    public String assigneeName() {
        return ((assignee != null && assignee.user != null) ? assignee.user.name : null);
    }

    /**
     * 담당자가 지정되어 있다면 {@link Assignee} 정보를 저장한다.
     * @see Assignee#add(Long, Long)
     */
    private void updateAssignee() {
        if (assignee != null && assignee.id == null && assignee.user.id != null) {
            assignee = Assignee.add(assignee.user.id, project.id);
        }
    }

    /**
     * 수정할 때 담당자 정보를 저장할 수도 있다.
     * @see #updateAssignee()
     */
    @Transient
    public void update() {
        updateAssignee();
        super.update();
    }

    @Override
    public void updateProperties() {
        // update null milestone explicitly
        if(this.milestone == null) {
            HashSet<String> updateProps = new HashSet<>();
            updateProps.add("milestone");
            Ebean.update(this, updateProps);
        }
    }

    /**
     * 저장할 때 담당자 정보를 저정할 수도 있다.
     * @see #updateAssignee()
     */
    @Transient
    public void save() {
        updateAssignee();
        super.save();
    }

    /**
     * {@code projectId}에 해당한느 프로젝트에
     * {@link State}에 해당하는 이슈 개수를 반환한다.
     *
     * @param projectId
     * @param state
     * @return
     */
    public static int countIssues(Long projectId, State state) {
        if (state == State.ALL) {
            return finder.where().eq("project.id", projectId).findRowCount();
        } else {
            return finder.where().eq("project.id", projectId).eq("state", state).findRowCount();
        }
    }

    /**
     * {@code projectId} 프로젝트에서 인자 조건에 따른 이슈 개수를 반환한다.
     *
     * @param projectId
     * @param cond
     * @return
     */

    public static int countIssuesBy(Long projectId, IssueApp.SearchCondition cond) {
        return cond.asExpressionList(Project.find.byId(projectId)).findRowCount();
    }

    /**
     * Generate a Microsoft Excel file in byte array from the given issue list,
     * using JXL.
     *
     * @param issueList 엑셀로 저장하고자 하는 리스트
     * @return
     * @throws WriteException
     * @throws IOException
     * @throws Exception
     */
    public static byte[] excelFrom(List<Issue> issueList) throws WriteException, IOException {
        WritableWorkbook workbook;
        WritableSheet sheet;

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

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        workbook = Workbook.createWorkbook(bos);
        sheet = workbook.createSheet(String.valueOf(JodaDateUtil.today().getTime()), 0);

        String[] labalArr = {"ID", "STATE", "TITLE", "ASSIGNEE", "DATE"};

        for (int i = 0; i < labalArr.length; i++) {
            sheet.addCell(new jxl.write.Label(i, 0, labalArr[i], cf1));
            sheet.setColumnView(i, 20);
        }
        for (int i = 1; i < issueList.size() + 1; i++) {
            Issue issue = issueList.get(i - 1);
            int colcnt = 0;
            sheet.addCell(new jxl.write.Label(colcnt++, i, issue.id.toString(), cf2));
            sheet.addCell(new jxl.write.Label(colcnt++, i, issue.state.toString(), cf2));
            sheet.addCell(new jxl.write.Label(colcnt++, i, issue.title, cf2));
            sheet.addCell(new jxl.write.Label(colcnt++, i, getAssigneeName(issue.assignee), cf2));
            sheet.addCell(new jxl.write.Label(colcnt++, i, issue.createdDate.toString(), cf2));
        }
        workbook.write();

        try {
            workbook.close();
        } catch (WriteException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return bos.toByteArray();
    }

    /**
     * excelSave에서 assignee를 리턴해준다.
     *
     * @param assignee
     * @return
     */
    private static String getAssigneeName(Assignee assignee) {
        return (assignee != null ? assignee.user.name : TO_BE_ASSIGNED);
    }

    // FIXME 이것이 없이 테스트는 잘 작동하나, view에서 댓글이 달린 이슈들을 필터링하는 라디오버튼을 작동시에 이 메쏘드에서
    // 시행하는 동기화 작업 없이는 작동을 하지 않는다.

	public boolean isOpen() {
	    return this.state == State.OPEN;
	}

	public boolean isClosed() {
	    return this.state == State.CLOSED;
	}

    @Override
    public Resource asResource() {
        return asResource(ResourceType.ISSUE_POST);
    }

    public Resource fieldAsResource(final ResourceType resourceType) {
        return new Resource() {
            @Override
            public String getId() {
                return id.toString();
            }

            @Override
            public Project getProject() {
                return project;
            }

            @Override
            public ResourceType getType() {
                return resourceType;
            }
        };
    }

    public Resource stateAsResource() {
        return fieldAsResource(ResourceType.ISSUE_STATE);
    }

    public Resource milestoneAsResource() {
        return fieldAsResource(ResourceType.ISSUE_MILESTONE);
    }

    public Resource assigneeAsResource() {
        return fieldAsResource(ResourceType.ISSUE_ASSIGNEE);
    }

    /**
     * {@code project}에 최근에 생성된 이슈를 {@code size} 개수 만큼 반환한다.
     *
     * when: 프로젝트 overview 화면에서 최근 활동 내역 만들 때 사용한다.
     *
     * @param project
     * @param size
     * @return
     */
    public static List<Issue> findRecentlyCreated(Project project, int size) {
        return finder.where().eq("project.id", project.id)
                .order().desc("createdDate")
                .findPagingList(size).getPage(0)
                .getList();
    }

    /**
     * @return
     * @see models.AbstractPosting#getComments()
     */
    @Transient
    public List<? extends Comment> getComments() {
        Collections.sort(comments, Comment.comparator());
        return comments;
    }

    public static Issue findByNumber(Project project, Long number) {
        return AbstractPosting.findByNumber(finder, project, number);
    }

    /**
     * 이 이슈를 지켜보고 있는 모든 사용자들을 얻는다.
     *
     * @return 이 이슈를 지켜보고 있는 모든 사용자들의 집합
     */
    @Transient
    public Set<User> getWatchers() {
        Set<User> baseWatchers = new HashSet<>();
        if (assignee != null) {
            baseWatchers.add(assignee.user);
        }
        return super.getWatchers(baseWatchers);
    }


    public boolean assigneeEquals(Assignee otherAssignee) {
        return (assignee == otherAssignee) ||
               (assignee != null && assignee.equals(otherAssignee));
    }

    /**
     * {@code project}에서 최근 열려있는 이슈 중에 {@code size} 만큼을 가져온다.
     *
     * when: 프로필 화면에서 여러 프로젝트의 이슈 목록을 종합하여 보여줄 때 사용한다.
     *
     * @param project
     * @param days days ago
     * @return
     */
    public static List<Issue> findRecentlyOpendIssuesByDaysAgo(Project project, int days) {
        return finder.where()
                .eq("project.id", project.id)
                .eq("state", State.OPEN)
                .ge("createdDate", JodaDateUtil.before(days)).order().desc("createdDate").findList();
    }

    /**
     * 이전 이슈 상태가 무엇이었는지 알려준다.
     * (현재는 OPEN/CLOSE 만 존재하나 이후 DEVELOPING이나 QA등을 추가할 수 있도록 할 예정임)
     *
     * when: 어떤 이슈의 이전 상태가 무엇이었는지 알고 싶을 때
     *
     * 주의!
     * 현재는 상태가 circular 형태로 동작한다. 그래서 처음 상태의 이전 상태는 마지막 상태로 알려준다.
     *
     * @return 현재 이슈의 이전 상태
     */
    public State previousState() {
        int currentState = Issue.availableStates.indexOf(this.state);
        if(isLastState(currentState)) {
            return Issue.availableStates.get(0);
        } else {
            return Issue.availableStates.get(currentState + 1);
        }
    }

    private boolean isLastState(int currentState) {
        return currentState + 1 == Issue.availableStates.size();
    }

    /**
     * 다음 이슈 상태가 무엇인지 알려준다.
     * (현재는 OPEN/CLOSE 만 존재하나 이후 DEVELOPING이나 QA등을 추가할 수 있도록 할 예정임)
     *
     * when: 어떤 이슈의 다음 상태가 무엇이 될지 알고 싶을 때
     *
     * 주의!
     * 현재는 상태가 circular 형태로 동작한다. 그래서 최종 상태의 다음 상태는 처음 상태로 알려준다.
     *
     * @return 현재 이슈의 다음 상태
     */
    public State nextState() {
        int currentState = Issue.availableStates.indexOf(this.state);
        if(isFirstState(currentState)) {
            return Issue.availableStates.get(Issue.availableStates.size()-1);
        } else {
            return Issue.availableStates.get(currentState - 1);
        }
    }

    private boolean isFirstState(int currentState) {
        return currentState  == 0;
    }

    /**
     * 이슈를 다음 이슈 상태로 전이한다.
     * (현재는 OPEN/CLOSE 만 존재)
     *
     * when: 이슈의 상태를 간단히 전이시킬 때
     *
     * 주의!
     * 현재는 상태가 circular 형태로 동작한다. 그래서 최종 상태의 다음 상태는 처음 상태가 된다.
     *
     * @return 현재 이슈의 다음 상태
     */
    public State toNextState(){
        this.state = nextState();
        super.update();
        return this.state;
    }

    @Override
    public Set<IssueLabel> getLabels() {
        return labels;
    }

    /**
     * 이 이슈의 타임라인을 얻는다.
     *
     * 타임라인이란 어떤 이슈에 대한 댓글들과 변경 내역(상태 변경, 담당자 변경)들의 시간순으로 정렬된 목록이다.
     *
     * when: 이슈 하나의 내용을 보여줄 때
     *
     * @return 타임라인
     */
    public List<TimelineItem> getTimeline() {
        List<TimelineItem> timelineItems = new ArrayList<>();
        timelineItems.addAll(comments);
        timelineItems.addAll(events);
        Collections.sort(timelineItems, new Comparator<TimelineItem>() {
            @Override
            public int compare(TimelineItem o1, TimelineItem o2) {
                return o1.getDate().compareTo(o2.getDate());
            }
        });
        return timelineItems;
    }
}
