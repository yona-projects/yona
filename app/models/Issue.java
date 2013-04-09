package models;

import jxl.*;
import jxl.format.*;
import jxl.format.Colour;
import jxl.format.BorderLineStyle;
import jxl.format.Border;
import jxl.format.Alignment;
import jxl.write.*;
import models.enumeration.*;
import models.resource.Resource;
import utils.*;

import javax.persistence.*;
import java.io.*;
import java.util.*;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "number"}))
public class Issue extends AbstractPosting {
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
     * @author HIVE TEAM
     */
    private static final long serialVersionUID = -2409072006294045262L;

    public static Finder<Long, Issue> finder = new Finder<Long, Issue>(Long.class, Issue.class);

    public static final String DEFAULT_SORTER = "createdDate";
    public static final String TO_BE_ASSIGNED = "TBA";

    public State state;

    @ManyToOne
    public Milestone milestone;

    @ManyToMany(fetch = FetchType.EAGER)
    public Set<IssueLabel> labels;

    @ManyToOne
    public Assignee assignee;

    @OneToMany(cascade = CascadeType.ALL)
    public List<IssueComment> comments;

    public int computeNumOfComments() {
        return comments.size();
    }

    @Override
    public Finder<Long, ? extends AbstractPosting> getFinder() {
        return finder;
    }

    /**
     * issueList, issue view에서 assignee의 이름을 출력해준다. 아래의 getAssigneeName과 합쳐질 수
     * 있을듯.
     */
    public String assigneeName() {
        return (this.assignee != null ? assignee.user.name : null);
    }

    @Transient
    public void save() {
        if (assignee != null && assignee.user.id != null) {
            assignee = Assignee.add(assignee.user.id, project.id);
        } else {
            assignee = null;
        }

        super.save();
    }

    public static int countIssues(Long projectId, State state) {
        if (state == State.ALL) {
            return finder.where().eq("project.id", projectId).findRowCount();
        } else {
            return finder.where().eq("project.id", projectId).eq("state", state).findRowCount();
        }
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
            sheet.addCell(new Label(colcnt++, i, issue.createdDate.toString(), cf2));
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

    public Resource asResource() {
        return asResource(ResourceType.ISSUE_POST);
    }

    public Resource fieldAsResource(final ResourceType resourceType) {
        return new Resource() {
            @Override
            public Long getId() {
                return id;
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
}

