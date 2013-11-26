package controllers;

import models.*;
import models.enumeration.*;

import play.i18n.Messages;
import play.mvc.Http;
import views.html.issue.edit;
import views.html.issue.partial_search;
import views.html.issue.view;
import views.html.issue.list;
import views.html.issue.create;
import views.html.issue.partial_comments;

import utils.AccessControl;
import utils.JodaDateUtil;
import utils.HttpUtil;
import utils.ErrorViews;
import utils.LabelSearchUtil;

import play.data.Form;
import play.db.ebean.Transactional;
import play.mvc.Call;
import play.mvc.Result;

import jxl.write.WriteException;

import org.apache.tika.Tika;
import org.apache.commons.lang.StringUtils;

import com.avaje.ebean.Page;
import com.avaje.ebean.ExpressionList;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import org.codehaus.jackson.node.ObjectNode;
import play.libs.Json;

public class IssueApp extends AbstractPostingApp {
    private static final String EXCEL_EXT = "xls";

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
            return notFound(ErrorViews.NotFound.render("error.notfound"));
        }

        if (!AccessControl.isAllowed(UserApp.currentUser(), project.asResource(), Operation.READ)) {
            return forbidden(ErrorViews.Forbidden.render("error.forbidden", project));
        }

        // SearchCondition from param
        Form<models.support.SearchCondition> issueParamForm = new Form<>(models.support.SearchCondition.class);
        models.support.SearchCondition searchCondition = issueParamForm.bindFromRequest().get();
        searchCondition.pageNum = pageNum - 1;
        searchCondition.state = state;
        if (searchCondition.orderBy.equals("id")) {
            searchCondition.orderBy = "createdDate";
        }
        searchCondition.labelIds.addAll(LabelSearchUtil.getLabelIds(request()));

        // determine pjax or json when requested with XHR
        if (HttpUtil.isRequestedWithXHR(request())) {
            format = HttpUtil.isPJAXRequest(request()) ? "pjax" : "json";
        }

        ExpressionList<Issue> el = searchCondition.asExpressionList(project);
        Page<Issue> issues = el.findPagingList(ITEMS_PER_PAGE).getPage(searchCondition.pageNum);

        switch(format){
            case EXCEL_EXT:
                return issuesAsExcel(project, el);

            case "pjax":
                return issuesAsPjax(project, issues, searchCondition);

            case "json":
                return issuesAsJson(project, issues);

            case "html":
            default:
                return issuesAsHTML(project, issues, searchCondition); 
        }
    }

    /**
     * 이슈 목록을 HTML 페이지로 반환
     * issues() 에서 기본값으로 호출된다
     * 
     * @param project 프로젝트
     * @param issues 이슈 목록 페이지
     * @param searchCondition 검색 조건
     * @return
     */
    private static Result issuesAsHTML(Project project, Page<Issue> issues, models.support.SearchCondition searchCondition){
        return ok(list.render("title.issueList", issues, searchCondition, project));
    }

    /**
     * 이슈 목록을 Microsoft Excel 형식으로 반환
     * issues() 에서 요청 형식({@code format})이 엑셀(xls)일 경우 호출된다
     * 
     * @param project 프로젝트
     * @param el
     * @throws WriteException
     * @throws IOException
     * @return
     */
    private static Result issuesAsExcel(Project project, ExpressionList<Issue> el) throws WriteException, IOException {
        byte[] excelData = Issue.excelFrom(el.findList());
        String filename = HttpUtil.encodeContentDisposition(
                project.name + "_issues_" + JodaDateUtil.today().getTime() + "." + EXCEL_EXT);

        response().setHeader("Content-Type", new Tika().detect(filename));
        response().setHeader("Content-Disposition", "attachment; " + filename);

        return ok(excelData);
    }

    /**
     * 이슈 목록을 PJAX 용으로 응답한다
     * issuesAsHTML()과 거의 같지만 캐시하지 않고 partial_search 로 렌더링한다는 점이 다르다
     * 
     * @param project
     * @param issues
     * @param searchCondition
     * @return
     */
    private static Result issuesAsPjax(Project project, Page<Issue> issues, models.support.SearchCondition searchCondition) {
        response().setHeader("Cache-Control", "no-cache, no-store");
        return ok(partial_search.render("title.issueList", issues, searchCondition, project));
    }

    /**
     * 이슈 목록을 정해진 갯수만큼 JSON으로 반환한다
     * QueryString 으로 목록에서 제외할 이슈ID (exceptId) 를 지정할 수 있고 
     * 반환하는 갯수는 ITEMS_PER_PAGE
     * 
     * 이슈 작성/수정시 비슷할 수 있는 이슈 표현을 위해 XHR 이슈 검색시 사용된다 
     * 
     * @param project
     * @param issues
     * @return
     */
    private static Result issuesAsJson(Project project, Page<Issue> issues) {
        ObjectNode listData = Json.newObject();

        // 반환할 목록에서 제외할 이슈ID 를 exceptId 로 지정할 수 있다(QueryString)
        // 이슈 수정시 '비슷할 수 있는 이슈' 목록에서 현재 수정중인 이슈를 제외하기 위해 사용한다
        String exceptIdStr = request().getQueryString("exceptId");
        Long exceptId = -1L;

        if(!StringUtils.isEmpty(exceptIdStr)){
            try {
                exceptId = Long.parseLong(exceptIdStr);
            } catch(Exception e){
                return badRequest(listData);
            }
        }

        List<Issue> issueList = issues.getList(); // the list of entities for this page

        for (Issue issue : issueList){
            Long issueId = issue.getNumber();

            if(issueId == exceptId){
                continue;
            }

            ObjectNode result = Json.newObject();
            result.put("id", issueId);
            result.put("title", issue.title);
            result.put("state", issue.state.toString());
            result.put("createdDate", issue.createdDate.toString());
            result.put("link", routes.IssueApp.issue(project.owner, project.name, issueId).toString());
            listData.put(issue.id.toString(), result);
        }

        return ok(listData);
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
            return notFound(ErrorViews.NotFound.render("error.notfound"));
        }

        Issue issueInfo = Issue.findByNumber(project, number);

        if (issueInfo == null) {
            if (isXHR()){
                ObjectNode result = Json.newObject();
                result.put("title", number);
                result.put("body", Messages.get("error.notfound.issue"));
                return ok(result);
            } else {
                return notFound(ErrorViews.NotFound.render("error.notfound", project, "issue"));
            }
        }

        if (!AccessControl.isAllowed(UserApp.currentUser(), issueInfo.asResource(), Operation.READ)) {
            return forbidden(ErrorViews.Forbidden.render("error.forbidden", project));
        }

        for (IssueLabel label: issueInfo.labels) {
            label.refresh();
        }

        Form<Comment> commentForm = new Form<>(Comment.class);
        Form<Issue> editForm = new Form<>(Issue.class).fill(Issue.findByNumber(project, number));

        if( isXHR() ) {
            ObjectNode result = Json.newObject();
            result.put("id", issueInfo.getNumber());
            result.put("title", issueInfo.title);
            result.put("state", issueInfo.state.toString());
            result.put("body", StringUtils.abbreviate(issueInfo.body, 1000));
            result.put("createdDate", issueInfo.createdDate.toString());
            result.put("link", routes.IssueApp.issue(project.owner, project.name, issueInfo.getNumber()).toString());
            return ok(result);
        }

        return ok(view.render("title.issueDetail", issueInfo, editForm, commentForm, project));
    }

    private static Boolean isXHR() {
        // Response as JSON on XHR
        String contentType = HttpUtil.getPreferType(request(), "application/json", "text/html");
        return contentType.equals("application/json");
    }

    /**
     * 이슈 타임라인 조회
     * 
     * <p>when: 단일 이슈의 타임라인 조회</p>
     * 
     * 접근 권한이 없을 경우, Forbidden 으로 응답한다.
     * 조회하려는 이슈가 존재하지 않을 경우엔 NotFound 로 응답한다.
     *
     * @param ownerName 프로젝트 소유자 이름
     * @param projectName 프로젝트 이름
     * @param number 이슈 번호
     * @return
     */
    public static Result timeline(String ownerName, String projectName, Long number) {
        Project project = ProjectApp.getProject(ownerName, projectName);
        if (project == null) {
            return notFound(ErrorViews.NotFound.render("error.notfound"));
        }

        Issue issueInfo = Issue.findByNumber(project, number);
        if (issueInfo == null) {
            return notFound(ErrorViews.NotFound.render("error.notfound", project, "issue"));
        }

        if (!AccessControl.isAllowed(UserApp.currentUser(), issueInfo.asResource(), Operation.READ)) {
            return forbidden(ErrorViews.Forbidden.render("error.forbidden", project));
        }
        
        for (IssueLabel label: issueInfo.labels) {
            label.refresh();
        }
        
        return ok(partial_comments.render(project, issueInfo));
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
            return notFound(ErrorViews.NotFound.render("error.notfound"));
        }

        return newPostingForm(project, ResourceType.ISSUE_POST,
                create.render("title.newIssue", new Form<>(Issue.class), project));
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
    @Transactional
    public static Result massUpdate(String ownerName, String projectName) throws IOException {
        Form<IssueMassUpdate> issueMassUpdateForm
                = new Form<>(IssueMassUpdate.class).bindFromRequest();
        if (issueMassUpdateForm.hasErrors()) {
            return badRequest(issueMassUpdateForm.errorsAsJson());
        }
        IssueMassUpdate issueMassUpdate = issueMassUpdateForm.get();

        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);
        if (project == null) {
            return notFound(ErrorViews.NotFound.render("error.notfound", project));
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

            boolean assigneeChanged = false;
            User oldAssignee = null;
            if (issueMassUpdate.assignee != null) {
                if(issue.assignee != null) {
                    oldAssignee = issue.assignee.user;
                }
                Assignee newAssignee = null;
                if (issueMassUpdate.assignee.isAnonymous()) {
                    newAssignee = null;
                } else {
                    newAssignee = Assignee.add(issueMassUpdate.assignee.id, project.id);
                }
                assigneeChanged = !issue.assignedUserEquals(newAssignee);
                issue.assignee = newAssignee;
            }

            boolean stateChanged = false;
            State oldState = null;
            if ((issueMassUpdate.state != null) && (issue.state != issueMassUpdate.state)) {
                stateChanged = true;
                oldState = issue.state;
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

            issue.updatedDate = JodaDateUtil.now();
            issue.update();
            updatedItems++;

            Issue updatedIssue = Issue.finder.byId(issue.id);
            String urlToView = routes.IssueApp.issue(issue.project.owner, issue.project.name,
                    issue.getNumber()).url();
            if(assigneeChanged) {
                addAssigneeChangedNotification(oldAssignee, updatedIssue, urlToView);
            }
            if(stateChanged) {
                addStateChangedNotification(oldState, updatedIssue, urlToView);
            }
        }

        if (updatedItems == 0 && rejectedByPermission > 0) {
            return forbidden(ErrorViews.Forbidden.render("error.forbidden", project));
        }

        // Response as JSON on XHR
        return isXHR() ? ok() : redirect(request().getHeader("Referer"));
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
    @Transactional
    public static Result newIssue(String ownerName, String projectName) throws IOException {
        Form<Issue> issueForm = new Form<>(Issue.class).bindFromRequest();
        Project project = ProjectApp.getProject(ownerName, projectName);
        if (project == null) {
            return notFound(ErrorViews.NotFound.render("error.notfound"));
        }

        if (!AccessControl.isProjectResourceCreatable(UserApp.currentUser(), project, ResourceType.ISSUE_POST)) {
            return forbidden(ErrorViews.Forbidden.render("error.forbidden", project));
        }

        if (issueForm.hasErrors()) {
            return badRequest(create.render("error.validation", issueForm, project));
        }

        final Issue newIssue = issueForm.get();
        newIssue.createdDate = JodaDateUtil.now();
        newIssue.updatedDate = JodaDateUtil.now();
        newIssue.setAuthor(UserApp.currentUser());
        newIssue.project = project;

        newIssue.state = State.OPEN;

        addLabels(newIssue, request());
        setMilestone(issueForm, newIssue);

        newIssue.save();

        // Attach all of the files in the current user's temporary storage.
        Attachment.moveAll(UserApp.currentUser().asResource(), newIssue.asResource());

        final Call issueCall = routes.IssueApp.issue(project.owner, project.name, newIssue.getNumber());

        String title = NotificationEvent.formatNewTitle(newIssue);
        Set<User> watchers = newIssue.getWatchers();
        watchers.addAll(NotificationEvent.getMentionedUsers(newIssue.body));
        watchers.remove(newIssue.getAuthor());

        NotificationEvent notiEvent = new NotificationEvent();
        notiEvent.created = new Date();
        notiEvent.title = title;
        notiEvent.senderId = UserApp.currentUser().id;
        notiEvent.receivers = watchers;
        notiEvent.urlToView = issueCall.url();
        notiEvent.resourceId = newIssue.id.toString();
        notiEvent.resourceType = newIssue.asResource().getType();
        notiEvent.eventType = EventType.NEW_ISSUE;
        notiEvent.oldValue = null;
        notiEvent.newValue = newIssue.body;

        NotificationEvent.add(notiEvent);

        return redirect(issueCall);
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
            return notFound(ErrorViews.NotFound.render("error.notfound"));
        }
        Issue issue = Issue.findByNumber(project, number);

        if (!AccessControl.isAllowed(UserApp.currentUser(), issue.asResource(), Operation.UPDATE)) {
            return forbidden(ErrorViews.Forbidden.render("error.forbidden", project));
        }

        Form<Issue> editForm = new Form<>(Issue.class).fill(issue);

        return ok(edit.render("title.editIssue", editForm, issue, project));
    }

    /**
     * 이슈 상태 전이
     *
     * <p>when: 특정 이슈를 다음 상태로 전이시킬 때</p>
     *
     * OPEN 된 이슈의 다음 상태는 CLOSED가 된다.
     * 단, CLOSED 된 상태의 이슈를 다음 상태로 전이시키면 OPEN 상태가 된다.
     *
     * @param ownerName 프로젝트 소유자 이름
     * @param projectName 프로젝트 이름
     * @param number 이슈 번호
     * @return
     * @throws IOException
     */
    @Transactional
    public static Result nextState(String ownerName, String projectName, Long number) {
        Project project = ProjectApp.getProject(ownerName, projectName);
        if (project == null) {
            return notFound(ErrorViews.NotFound.render("error.notfound"));
        }
        final Issue issue = Issue.findByNumber(project, number);

        Call redirectTo = routes.IssueApp.issue(project.owner, project.name, number);
        if (!AccessControl.isAllowed(UserApp.currentUser(), issue.asResource(), Operation.UPDATE)) {
            return forbidden(ErrorViews.Forbidden.render("error.forbidden", issue.project));
        }
        issue.toNextState();
        addStateChangedNotification(issue.previousState(), issue, redirectTo.absoluteURL(request() ));
        return redirect(redirectTo);
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
     * @see {@link AbstractPostingApp#editPosting(models.AbstractPosting, models.AbstractPosting, play.data.Form}
     */
    @Transactional
    public static Result editIssue(String ownerName, String projectName, Long number) throws IOException {
        Form<Issue> issueForm = new Form<>(Issue.class).bindFromRequest();

        Project project = ProjectApp.getProject(ownerName, projectName);
        if (project == null) {
            return notFound(ErrorViews.NotFound.render("error.notfound"));
        }

        if (issueForm.hasErrors()) {
            return badRequest(edit.render("error.validation", issueForm, Issue.findByNumber(project, number), project));
        }

        final Issue issue = issueForm.get();
        setMilestone(issueForm, issue);

        final Issue originalIssue = Issue.findByNumber(project, number);

        Call redirectTo = routes.IssueApp.issue(project.owner, project.name, number);

        // updateIssueBeforeSave.run would be called just before this issue is saved.
        // It updates some properties only for issues, such as assignee or labels, but not for non-issues.
        Runnable updateIssueBeforeSave = new Runnable() {
            @Override
            public void run() {
                issue.comments = originalIssue.comments;
                addLabels(issue, request());
            }
        };

        Result result = editPosting(originalIssue, issue, issueForm, redirectTo, updateIssueBeforeSave);

        if(!originalIssue.assignedUserEquals(issue.assignee)) {
            Issue updatedIssue = Issue.finder.byId(originalIssue.id);
            User oldAssignee = null;
            if(originalIssue.assignee != null) {
                oldAssignee = originalIssue.assignee.user;
            }
            addAssigneeChangedNotification(oldAssignee, updatedIssue, redirectTo.absoluteURL(request()));
        }

        if(issue.state != originalIssue.state) {
            Issue updatedIssue = Issue.finder.byId(originalIssue.id);
            addStateChangedNotification(issue.state, updatedIssue,
                    redirectTo.absoluteURL(request()
                    ));
        }

        return result;
    }

    /**
     * 상태 변경에 대한 notification을 등록한다.
     *
     * 등록된 notification은 사이트 메인 페이지를 통해 사용자에게 보여지며 또한
     * {@link models.NotificationMail#startSchedule()} 에 의해 메일로 발송된다.
     *
     * @param oldState
     * @param updatedIssue
     * @param urlToView
     */
    @Transactional
    private static void addStateChangedNotification(State oldState, Issue updatedIssue, String urlToView) {
        NotificationEvent notiEvent = new NotificationEvent();

        notiEvent.oldValue = oldState.state();
        notiEvent.newValue = updatedIssue.state.state();

        notiEvent.receivers = updatedIssue.getWatchers();
        notiEvent.receivers.remove(UserApp.currentUser());

        notiEvent.senderId = UserApp.currentUser().id;

        notiEvent.title = NotificationEvent.formatReplyTitle(updatedIssue);

        notiEvent.created = new Date();
        notiEvent.urlToView = urlToView;
        notiEvent.resourceId = updatedIssue.id.toString();
        notiEvent.resourceType = updatedIssue.asResource().getType();
        notiEvent.eventType = EventType.ISSUE_STATE_CHANGED;

        NotificationEvent.add(notiEvent);

        IssueEvent.addFromNotificationEvent(notiEvent, updatedIssue, UserApp.currentUser().loginId);
    }

    /**
     * 담당자 변경에 대한 notification을 등록한다.
     *
     * 등록된 notification은 사이트 메인 페이지를 통해 사용자에게 보여지며 또한
     * {@link models.NotificationMail#startSchedule()} 에 의해 메일로 발송된다.
     *
     * @param oldAssignee
     * @param updatedIssue
     * @param urlToView
     */
    @Transactional
    private static void addAssigneeChangedNotification(User oldAssignee, Issue updatedIssue, String urlToView) {
        NotificationEvent notiEvent = new NotificationEvent();

        Set<User> receivers = updatedIssue.getWatchers();
        if(oldAssignee != null) {
            notiEvent.oldValue = oldAssignee.loginId;
            receivers.add(oldAssignee);
        }
        receivers.remove(UserApp.currentUser());

        notiEvent.title = NotificationEvent.formatReplyTitle(updatedIssue);

        if (updatedIssue.assignee != null) {
            notiEvent.newValue = User.find.byId(updatedIssue.assignee.user.id).loginId;
        }

        notiEvent.created = new Date();
        notiEvent.senderId = UserApp.currentUser().id;
        notiEvent.receivers = receivers;
        notiEvent.urlToView = urlToView;
        notiEvent.resourceId = updatedIssue.id.toString();
        notiEvent.resourceType = updatedIssue.asResource().getType();
        notiEvent.eventType = EventType.ISSUE_ASSIGNEE_CHANGED;

        NotificationEvent.add(notiEvent);

        IssueEvent.addFromNotificationEvent(notiEvent, updatedIssue, UserApp.currentUser().loginId);
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
    @Transactional
    public static Result deleteIssue(String ownerName, String projectName, Long number) {
        Project project = ProjectApp.getProject(ownerName, projectName);
        if (project == null) {
            return notFound(ErrorViews.NotFound.render("error.notfound"));
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
     * @see {@link AbstractPostingApp#newComment(models.Comment, play.data.Form}
     */
    @Transactional
    public static Result newComment(String ownerName, String projectName, Long number) throws IOException {
        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);
        if (project == null) {
            return notFound(ErrorViews.NotFound.render("error.notfound"));
        }
        final Issue issue = Issue.findByNumber(project, number);
        if (issue == null) {
            return notFound(ErrorViews.NotFound.render("error.notfound"));
        }
        Call redirectTo = routes.IssueApp.issue(project.owner, project.name, number);
        Form<IssueComment> commentForm = new Form<>(IssueComment.class)
                .bindFromRequest();

        if (commentForm.hasErrors()) {
            return badRequest(ErrorViews.BadRequest.render("error.validation", project));
        }

        if (!AccessControl.isProjectResourceCreatable(
                    UserApp.currentUser(), project, ResourceType.ISSUE_COMMENT)) {
            return forbidden(ErrorViews.Forbidden.render("error.forbidden", project));
        }

        final IssueComment comment = commentForm.get();

        return newComment(comment, commentForm, redirectTo, new Runnable() {
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
    @Transactional
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
    private static void addLabels(Issue issue, Http.Request request) {
        if (issue.labels == null) {
            issue.labels = new HashSet<IssueLabel>();
        }

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
                issue.labels.add(IssueLabel.finder.byId(Long.parseLong(labelId)));
            }
        }
    }
}
