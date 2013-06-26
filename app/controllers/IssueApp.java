package controllers;

import models.*;
import models.enumeration.*;

import play.Logger;
import play.data.DynamicForm;
import play.mvc.Http;
import views.html.issue.edit;
import views.html.issue.view;
import views.html.issue.list;
import views.html.issue.create;
import views.html.error.notfound;
import views.html.error.forbidden;

import utils.AccessControl;
import utils.Callback;
import utils.JodaDateUtil;
import utils.HttpUtil;

import play.data.Form;
import play.mvc.Call;
import play.mvc.Result;

import jxl.write.WriteException;
import org.apache.tika.Tika;
import com.avaje.ebean.Page;
import com.avaje.ebean.ExpressionList;

import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.Transient;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.avaje.ebean.Expr.icontains;
import static play.data.Form.form;

public class IssueApp extends AbstractPostingApp {
    private static final String EXCEL_EXT = "xls";

    public static class SearchCondition extends AbstractPostingApp.SearchCondition {
        public String state;
        public Boolean commentedCheck;
        public Long milestoneId;
        public Set<Long> labelIds;
        public String authorLoginId;
        public Long assigneeId;

        @Transient
        public static SearchCondition emptySearchCondition = new SearchCondition();

        public SearchCondition() {
            super();
            milestoneId = null;
            state = State.OPEN.name();
            commentedCheck = false;
        }

        private ExpressionList<Issue> asExpressionList(Project project) {
            ExpressionList<Issue> el = Issue.finder.where().eq("project.id", project.id);

            if (filter != null) {
                el.or(icontains("title", filter), icontains("body", filter));
            }

            if (authorLoginId != null && !authorLoginId.isEmpty()) {
                User user = User.findByLoginId(authorLoginId);
                if (!user.isAnonymous()) {
                    el.eq("authorId", user.id);
                } else {
                    List<Long> ids = new ArrayList<Long>();
                    for (User u : User.find.where().icontains("loginId", authorLoginId).findList()) {
                        ids.add(u.id);
                    }
                    el.in("authorId", ids);
                }
            }

            if (assigneeId != null) {
                el.eq("assignee.user.id", assigneeId);
                el.eq("assignee.project.id", project.id);
            }

            if (milestoneId != null) {
                el.eq("milestone.id", milestoneId);
            }

            if (labelIds != null) {
                for (Long labelId : labelIds) {
                    el.eq("labels.id", labelId);
                }
            }

            if (commentedCheck) {
                el.ge("numOfComments", AbstractPosting.NUMBER_OF_ONE_MORE_COMMENTS);
            }

            State st = State.getValue(state);
            if (st.equals(State.OPEN) || st.equals(State.CLOSED)) {
                el.eq("state", st);
            }

            if (orderBy != null) {
                el.orderBy(orderBy + " " + orderDir);
            }

            return el;
        }
    }

    /**
     * 이슈 목록 조회
     *
     * <p>when: 프로젝트의 이슈 목록 진입, 이슈 검색</p>
     *
     * 현재 사용자가 프로젝트에 권한이 없다면 Forbidden 으로 응답한다.
     * 입력된 검색 조건이 있다면 적용하고 페이징 처리된 목록을 보여준다.
     * 요청 형식({@code format})이 엑셀(xls)일 경우 목록을 엑셀로 다운로드한다.
     *
     * @param ownerName 프로젝트 소유자 이름
     * @param projectName 프로젝트 이름
     * @param state 이슈 상태 (해결 / 미해결)
     * @param format 요청 형식
     * @param pageNum 페이지 번호
     * @return
     * @throws WriteException
     * @throws IOException
     */
    public static Result issues(String ownerName, String projectName, String state, String format, int pageNum) throws WriteException, IOException {
        Project project = ProjectApp.getProject(ownerName, projectName);
        if (project == null) {
            return notFound();
        }

        if (!AccessControl.isAllowed(UserApp.currentUser(), project.asResource(), Operation.READ)) {
            return forbidden(views.html.error.forbidden.render(project));
        }

        Form<SearchCondition> issueParamForm = new Form<SearchCondition>(SearchCondition.class);
        SearchCondition searchCondition = issueParamForm.bindFromRequest().get();
        searchCondition.pageNum = pageNum - 1;
        searchCondition.state = state;

        String[] labelIds = request().queryString().get("labelIds");
        if (labelIds != null) {
            for (String labelId : labelIds) {
                searchCondition.labelIds.add(Long.valueOf(labelId));
            }
        }

        ExpressionList<Issue> el = searchCondition.asExpressionList(project);

        if (EXCEL_EXT.equals(format)) {
            byte[] excelData = Issue.excelFrom(el.findList());
            String filename = HttpUtil.encodeContentDisposition(
                    project.name + "_issues_" + JodaDateUtil.today().getTime() + "." + EXCEL_EXT);

            response().setHeader("Content-Type", new Tika().detect(filename));
            response().setHeader("Content-Disposition", "attachment; " + filename);

            return ok(excelData);
        } else {
            Page<Issue> issues = el
                .findPagingList(ITEMS_PER_PAGE).getPage(searchCondition.pageNum);

            return ok(list.render("title.issueList", issues, searchCondition, project));
        }
    }

    /**
     * 이슈 조회
     *
     * <p>when: 단일 이슈의 상세내용 조회</p>
     *
     * 접근 권한이 없을 경우, Forbidden 으로 응답한다.
     * 조회하려는 이슈가 존재하지 않을 경우엔 NotFound 로 응답한다.
     *
     * @param ownerName 프로젝트 소유자 이름
     * @param projectName 프로젝트 이름
     * @param number 이슈 번호
     * @return
     */
    public static Result issue(String ownerName, String projectName, Long number) {
        Project project = ProjectApp.getProject(ownerName, projectName);
        if (project == null) {
            return notFound();
        }

        Issue issueInfo = Issue.findByNumber(project, number);
        if (issueInfo == null) {
            return notFound(views.html.error.notfound.render("error.notfound", project, "issue"));
        }

        if (!AccessControl.isAllowed(UserApp.currentUser(), issueInfo.asResource(), Operation.READ)) {
            return forbidden(views.html.error.forbidden.render(project));
        }

        for (IssueLabel label: issueInfo.labels) {
            label.refresh();
        }

        Form<Comment> commentForm = new Form<Comment>(Comment.class);
        Form<Issue> editForm = new Form<Issue>(Issue.class).fill(Issue.findByNumber(project, number));

        return ok(view.render("title.issueDetail", issueInfo, editForm, commentForm, project));
    }

    /**
     * 새 이슈 등록 폼
     *
     * <p>when: 새로운 이슈 작성</p>
     *
     * @param ownerName 프로젝트 소유자 이름
     * @param projectName 프로젝트 이름
     * @return
     * @see {@link AbstractPostingApp#newPostingForm(Project, ResourceType, play.mvc.Content)}
     */
    public static Result newIssueForm(String ownerName, String projectName) {
        Project project = ProjectApp.getProject(ownerName, projectName);
        if (project == null) {
            return notFound();
        }

        return newPostingForm(project, ResourceType.ISSUE_POST,
                create.render("title.newIssue", new Form<Issue>(Issue.class), project));
    }

    /**
     * 여러 이슈를 한번에 갱신하려는 요청에 응답한다.
     *
     * <p>when: 이슈 목록 페이지에서 이슈를 체크하고 상단의 갱신 드롭박스를 이용해 체크한 이슈들을 갱신할 때</p>
     *
     * 갱신을 시도한 이슈들 중 하나 이상 갱신에 성공했다면 이슈 목록 페이지로 리다이렉트한다. (303 See Other)
     * 어떤 이슈에 대한 갱신 요청이든 모두 실패했으며, 그 중 권한 문제로 실패한 것이 한 개 이상 있다면 403
     * Forbidden 으로 응답한다.
     * 갱신 요청이 잘못된 경우엔 400 Bad Request 로 응답한다.
     *
     * @param ownerName 프로젝트 소유자 이름
     * @param projectName 프로젝트 이름
     * @return
     * @throws IOException
     */
    public static Result massUpdate(String ownerName, String projectName) throws IOException {
        Form<IssueMassUpdate> issueMassUpdateForm
                = new Form<IssueMassUpdate>(IssueMassUpdate.class).bindFromRequest();
        if (issueMassUpdateForm.hasErrors()) {
            return badRequest(issueMassUpdateForm.errorsAsJson());
        }
        IssueMassUpdate issueMassUpdate = issueMassUpdateForm.get();

        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);
        if (project == null) {
            return notFound();
        }

        int updatedItems = 0;
        int rejectedByPermission = 0;

        for (Issue issue : issueMassUpdate.issues) {
            issue.refresh();

            if (issueMassUpdate.delete == true) {
                if (AccessControl.isAllowed(UserApp.currentUser(), issue.asResource(),
                        Operation.DELETE)) {
                    issue.delete();
                    continue;
                } else {
                    rejectedByPermission++;
                    continue;
                }
            }

            if (!AccessControl.isAllowed(UserApp.currentUser(), issue.asResource(),
                    Operation.UPDATE)) {
                rejectedByPermission++;
                continue;
            }

            if (issueMassUpdate.assignee != null) {
                if (issueMassUpdate.assignee.isAnonymous()) {
                    issue.assignee = null;
                } else {
                    issue.assignee = Assignee.add(issueMassUpdate.assignee.id, project.id);
                }
            }

            if (issueMassUpdate.state != null) {
                issue.state = issueMassUpdate.state;
            }

            if (issueMassUpdate.milestone != null) {
                if(issueMassUpdate.milestone.isNullMilestone()) {
                    issue.milestone = null;
                } else {
                    issue.milestone = issueMassUpdate.milestone;
                }
            }

            if (issueMassUpdate.attachingLabel != null) {
                issue.labels.add(issueMassUpdate.attachingLabel);
            }

            if (issueMassUpdate.detachingLabel != null) {
                issue.labels.remove(issueMassUpdate.detachingLabel);
            }

            issue.update();
            updatedItems++;
        }

        if (updatedItems == 0 && rejectedByPermission > 0) {
            return forbidden(views.html.error.forbidden.render(project));
        }

        return redirect(request().getHeader("Referer"));
    }

    /**
     * 새 이슈 등록
     *
     * <p>when: 새 이슈 등록 폼에서 저장</p>
     *
     * 이슈 생성 권한이 없다면 Forbidden 으로 응답한다.
     * 입력 폼에 문제가 있다면 BadRequest 로 응답한다.
     * 이슈 저장전에 임시적으로 사용자에게 첨부되었던 첨부파일들을 이슈 하위로 옮긴다,
     * 저장 이후 목록 화면으로 돌아간다.
     *
     * @param ownerName 프로젝트 소유자 이름
     * @param projectName 프로젝트 이름
     * @return
     * @throws IOException
     */
    public static Result newIssue(String ownerName, String projectName) throws IOException {
        Form<Issue> issueForm = new Form<Issue>(Issue.class).bindFromRequest();
        Project project = ProjectApp.getProject(ownerName, projectName);
        if (project == null) {
            return notFound();
        }

        if (!AccessControl.isProjectResourceCreatable(UserApp.currentUser(), project, ResourceType.ISSUE_POST)) {
            return forbidden(views.html.error.forbidden.render(project));
        }

        if (issueForm.hasErrors()) {
            return badRequest(create.render(issueForm.errors().toString(), issueForm, project));
        }

        Issue newIssue = issueForm.get();
        newIssue.createdDate = JodaDateUtil.now();
        newIssue.setAuthor(UserApp.currentUser());
        newIssue.project = project;

        newIssue.state = State.OPEN;
        addLabels(newIssue.labels, request());

        setMilestone(issueForm, newIssue);

        newIssue.save();

        // Attach all of the files in the current user's temporary storage.
        Attachment.moveAll(UserApp.currentUser().asResource(), newIssue.asResource());

        return redirect(routes.IssueApp.issue(project.owner, project.name, newIssue.getNumber()));
    }

    /**
     * 이슈 수정 폼
     *
     *  <p>when: 기존 이슈 수정</p>
     *
     *  이슈 수정 권한이 없을 경우 Forbidden 으로 응답한다.
     *
     * @param ownerName 프로젝트 소유자 이름
     * @param projectName 프로젝트 이름
     * @param number 이슈 번호
     * @return
     */
    public static Result editIssueForm(String ownerName, String projectName, Long number) {
        Project project = ProjectApp.getProject(ownerName, projectName);
        if (project == null) {
            return notFound();
        }
        Issue issue = Issue.findByNumber(project, number);

        if (!AccessControl.isAllowed(UserApp.currentUser(), issue.asResource(), Operation.UPDATE)) {
            return forbidden(views.html.error.forbidden.render(project));
        }

        Form<Issue> editForm = new Form<Issue>(Issue.class).fill(issue);

        return ok(edit.render("title.editIssue", editForm, issue, project));
    }

    /**
     * 이슈 수정
     *
     * <p>when: 이슈 수정 폼에서 저장</p>
     *
     * 폼에서 전달 받은 내용, 마일스톤, 라벨 정보와
     * 기존 이슈에 작성되어 있던 댓글 정보를 정리하여 저장한다.
     * 저장후 목록 화면으로 돌아간다.
     *
     * @param ownerName 프로젝트 소유자 이름
     * @param projectName 프로젝트 이름
     * @param number 이슈 번호
     * @return
     * @throws IOException
     * @see {@link AbstractPostingApp#editPosting(AbstractPosting, AbstractPosting, Form, Call, Callback)}
     */
    public static Result editIssue(String ownerName, String projectName, Long number) throws IOException {
        Form<Issue> issueForm = new Form<Issue>(Issue.class).bindFromRequest();
        final Issue issue = issueForm.get();
        setMilestone(issueForm, issue);

        final Project project = ProjectApp.getProject(ownerName, projectName);
        if (project == null) {
            return notFound();
        }
        final Issue originalIssue = Issue.findByNumber(project, number);

        Call redirectTo = routes.IssueApp.issue(project.owner, project.name, number);

        // updateIssueBeforeSave.run would be called just before this issue is saved.
        // It updates some properties only for issues, such as assignee or labels, but not for non-issues.
        Callback updateIssueBeforeSave = new Callback() {
            @Override
            public void run() {
                issue.comments = originalIssue.comments;
                addLabels(issue.labels, request());
            }
        };

        return editPosting(originalIssue, issue, issueForm, redirectTo, updateIssueBeforeSave);
    }

    /*
     * form 에서 전달받은 마일스톤ID를 이용해서 이슈객체에 마일스톤 객체를 set한다
     */
    private static void setMilestone(Form<Issue> issueForm, Issue issue) {
        String milestoneId = issueForm.data().get("milestoneId");
        if(milestoneId != null && !milestoneId.isEmpty()) {
            issue.milestone = Milestone.findById(Long.parseLong(milestoneId));
        } else {
            issue.milestone = null;
        }
    }

    /**
     * 이슈 삭제
     *
     * <p>when: 이슈 조회화면에서 삭제</p>
     *
     * 이슈 번호에 해당하는 이슈 삭제 후 이슈 목록 화면으로 돌아간다.
     *
     * @param ownerName 프로젝트 소유자 이름
     * @param projectName 프로젝트 이름
     * @param number 이슈 번호
     * @return
     * @ see {@link AbstractPostingApp#delete(play.db.ebean.Model, models.resource.Resource, Call)}
     */
    public static Result deleteIssue(String ownerName, String projectName, Long number) {
        Project project = ProjectApp.getProject(ownerName, projectName);
        if (project == null) {
            return notFound();
        }
        Issue issue = Issue.findByNumber(project, number);
        Call redirectTo =
            routes.IssueApp.issues(project.owner, project.name, State.OPEN.state(), "html", 1);

        return delete(issue, issue.asResource(), redirectTo);
    }

    /**
     * 댓글 작성
     *
     * <p>when: 이슈 조회화면에서 댓글 작성하고 저장</p>
     *
     * 현재 사용자를 댓글 작성자로 하여 저장하고 이슈 조회화면으로 돌아간다.
     *
     * @param ownerName 프로젝트 소유자 이름
     * @param projectName 프로젝트 이름
     * @param number 이슈 번호
     * @return
     * @throws IOException
     * @see {@link AbstractPostingApp#newComment(Comment, Form, Call, Callback)}
     */
    public static Result newComment(String ownerName, String projectName, Long number) throws IOException {
        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);
        if (project == null) {
            return notFound();
        }
        final Issue issue = Issue.findByNumber(project, number);
        Call redirectTo = routes.IssueApp.issue(project.owner, project.name, number);
        Form<IssueComment> commentForm = new Form<IssueComment>(IssueComment.class)
                .bindFromRequest();

        if (commentForm.hasErrors()) {
            return badRequest(commentForm.errors().toString());
        }

        final IssueComment comment = commentForm.get();

        return newComment(comment, commentForm, redirectTo, new Callback() {
            @Override
            public void run() {
                comment.issue = issue;
            }
        });
    }

    /**
     * 댓글 삭제
     *
     * <p>when: 댓글 삭제 버튼</p>
     *
     * 댓글을 삭제하고 이슈 조회 화면으로 돌아간다.
     * 삭제 권한이 없을 경우 Forbidden 으로 응답한다.
     *
     * @param ownerName 프로젝트 소유자 이름
     * @param projectName 프로젝트 이름
     * @param issueNumber 이슈 번호
     * @param commentId 댓글ID
     * @return
     * @see {@link AbstractPostingApp#delete(play.db.ebean.Model, models.resource.Resource, Call)}
     */
    public static Result deleteComment(String ownerName, String projectName, Long issueNumber,
            Long commentId) {
        Comment comment = IssueComment.find.byId(commentId);
        Project project = comment.asResource().getProject();
        Call redirectTo =
            routes.IssueApp.issue(project.owner, project.name, issueNumber);

        return delete(comment, comment.asResource(), redirectTo);
    }

    /**
     * 이슈 라벨 구성
     *
     * <p>when: 새로 이슈를 작성하거나, 기존 이슈를 수정할때</p>
     *
     * {@code request} 에서 이슈 라벨 ID들을 추출하여 이에 대응하는 이슈라벨 정보들을
     * {@code labels} 에 저장한다.
     *
     * @param labels 이슈 라벨을 저장할 대상
     * @param request 요청 정보 (이슈라벨 ID를 추출하여 사용한다)
     */
    public static void addLabels(Set<IssueLabel> labels, Http.Request request) {
        Http.MultipartFormData multipart = request.body().asMultipartFormData();
        Map<String, String[]> form;
        if (multipart != null) {
            form = multipart.asFormUrlEncoded();
        } else {
            form = request.body().asFormUrlEncoded();
        }
        String[] labelIds = form.get("labelIds");
        if (labelIds != null) {
            for (String labelId : labelIds) {
                labels.add(IssueLabel.finder.byId(Long.parseLong(labelId)));
            }
        }
    }

    public static Result watch(String ownerName, String projectName, Long issueNumber) {
        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);
        Issue issue = Issue.findByNumber(project, issueNumber);

        return AbstractPostingApp.watch(issue);
    }

    public static Result unwatch(String ownerName, String projectName, Long issueNumber) {
        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);
        Issue issue = Issue.findByNumber(project, issueNumber);

        return AbstractPostingApp.unwatch(issue);
    }


}
