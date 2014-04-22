/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @Author Tae
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package controllers;

import actions.NullProjectCheckAction;
import actions.AnonymousCheckAction;
import com.avaje.ebean.ExpressionList;
import com.avaje.ebean.Page;
import controllers.annotation.IsAllowed;
import controllers.annotation.IsCreatable;
import jxl.write.WriteException;
import models.*;
import models.enumeration.Operation;
import models.enumeration.ResourceType;
import models.enumeration.State;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.Tika;
import org.codehaus.jackson.node.ObjectNode;
import play.api.templates.Html;
import play.data.Form;
import play.data.validation.ValidationError;
import play.db.ebean.Transactional;
import play.i18n.Messages;
import play.libs.Json;
import play.mvc.Call;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.With;
import utils.*;
import views.html.issue.*;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import utils.HttpUtil;

public class IssueApp extends AbstractPostingApp {
    private static final String EXCEL_EXT = "xls";
    private static final Integer ITEMS_PER_PAGE_MAX = 45;

    /**
     * 내 이슈 목록 조회
     *
     * <p>when: 자신의 이슈만 검색하고 싶을때</p>
     *
     * 입력된 검색 조건이 있다면 적용하고 페이징 처리된 목록을 보여준다.
     *
     * @param state 이슈 상태 (해결 / 미해결)
     * @param format 요청 형식
     * @param pageNum 페이지 번호
     * @return
     * @throws WriteException
     * @throws IOException
     */
    @With(AnonymousCheckAction.class)
    public static Result userIssues(String state, String format, int pageNum) throws WriteException, IOException {
        Project project = null;
        // SearchCondition from param
        Form<models.support.SearchCondition> issueParamForm = new Form<>(models.support.SearchCondition.class);
        models.support.SearchCondition searchCondition = issueParamForm.bindFromRequest().get();
        if (hasNotConditions(searchCondition)) {
            searchCondition.assigneeId = UserApp.currentUser().id;
        }
        searchCondition.pageNum = pageNum - 1;

        // determine pjax or json when requested with XHR
        if (HttpUtil.isRequestedWithXHR(request())) {
            format = HttpUtil.isPJAXRequest(request()) ? "pjax" : "json";
        }

        Integer itemsPerPage = getItemsPerPage();
        ExpressionList<Issue> el = searchCondition.asExpressionList();
        Page<Issue> issues = el.findPagingList(itemsPerPage).getPage(searchCondition.pageNum);

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

    private static boolean hasNotConditions(models.support.SearchCondition searchCondition) {
        return searchCondition.assigneeId == null && searchCondition.authorId == null && searchCondition.mentionId == null;
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
    @IsAllowed(Operation.READ)
    public static Result issues(String ownerName, String projectName, String state, String format, int pageNum) throws WriteException, IOException {
        Project project = ProjectApp.getProject(ownerName, projectName);

        // SearchCondition from param
        Form<models.support.SearchCondition> issueParamForm = new Form<>(models.support.SearchCondition.class);
        models.support.SearchCondition searchCondition = issueParamForm.bindFromRequest().get();
        searchCondition.pageNum = pageNum - 1;
        searchCondition.labelIds.addAll(LabelSearchUtil.getLabelIds(request()));

        // determine pjax or json when requested with XHR
        if (HttpUtil.isRequestedWithXHR(request())) {
            format = HttpUtil.isPJAXRequest(request()) ? "pjax" : "json";
        }

        Integer itemsPerPage = getItemsPerPage();
        ExpressionList<Issue> el = searchCondition.asExpressionList(project);
        Page<Issue> issues = el.findPagingList(itemsPerPage).getPage(searchCondition.pageNum);

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
     * 한 페이지에 표시할 항목의 갯수를 반환한다
     * GET 쿼리로 itemsPerPage 가 있으면 그것을 활용하고,
     * 기본값은 ITEMS_PER_PAGE 상수를 사용한다
     * 최대값인 ITEMS_PER_PAGE_MAX 를 넘을수는 없다
     * @return
     */
    private static Integer getItemsPerPage(){
        Integer itemsPerPage = ITEMS_PER_PAGE;
        String amountStr = request().getQueryString("itemsPerPage");

        if(amountStr != null){ // or amount from query string
            try {
                itemsPerPage = Integer.parseInt(amountStr);
            } catch (NumberFormatException ignored){}
        }

        return Math.min(itemsPerPage, ITEMS_PER_PAGE_MAX);
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
        if(project == null){
            return ok(my_list.render("title.issueList", issues, searchCondition, project));
        } else {
            return ok(list.render("title.issueList", issues, searchCondition, project));
        }

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
        if (project == null) {
            return ok(my_partial_search.render("title.issueList", issues, searchCondition, project));
        } else {
            return ok(partial_search.render("title.issueList", issues, searchCondition, project));
        }

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

            if(issueId.equals(exceptId)){
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
    @With(NullProjectCheckAction.class)
    public static Result issue(String ownerName, String projectName, Long number) {
        Project project = ProjectApp.getProject(ownerName, projectName);

        Issue issueInfo = Issue.findByNumber(project, number);

        response().setHeader("Vary", "Accept");

        if (issueInfo == null) {
            if (HttpUtil.isJSONPreferred(request())){
                ObjectNode result = Json.newObject();
                result.put("title", number);
                result.put("body", Messages.get("error.notfound.issue"));
                return ok(result);
            } else {
                return notFound(ErrorViews.NotFound.render("error.notfound", project, ResourceType.ISSUE_POST.resource()));
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
        UserApp.currentUser().visits(project);
        // Determine response type with Accept header
        if (HttpUtil.isJSONPreferred(request())){
            ObjectNode result = Json.newObject();
            result.put("id", issueInfo.getNumber());
            result.put("title", issueInfo.title);
            result.put("state", issueInfo.state.toString());
            result.put("body", StringUtils.abbreviate(issueInfo.body, 1000));
            result.put("createdDate", issueInfo.createdDate.toString());
            result.put("link", routes.IssueApp.issue(project.owner, project.name, issueInfo.getNumber()).toString());
            return ok(result);
        } else {
            return ok(view.render("title.issueDetail", issueInfo, editForm, commentForm, project));
        }
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
    @IsAllowed(resourceType = ResourceType.ISSUE_POST, value = Operation.READ)
    public static Result timeline(String ownerName, String projectName, Long number) {
        Project project = ProjectApp.getProject(ownerName, projectName);
        Issue issueInfo = Issue.findByNumber(project, number);

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
     */
    @With(AnonymousCheckAction.class)
    @IsCreatable(ResourceType.ISSUE_POST)
    public static Result newIssueForm(String ownerName, String projectName) {
        Project project = ProjectApp.getProject(ownerName, projectName);
        return ok(create.render("title.newIssue", new Form<>(Issue.class), project));
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
    @With(NullProjectCheckAction.class)
    public static Result massUpdate(String ownerName, String projectName) {
        Form<IssueMassUpdate> issueMassUpdateForm
                = new Form<>(IssueMassUpdate.class).bindFromRequest();
        if (issueMassUpdateForm.hasErrors()) {
            return badRequest(issueMassUpdateForm.errorsAsJson());
        }
        IssueMassUpdate issueMassUpdate = issueMassUpdateForm.get();

        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);

        int updatedItems = 0;
        int rejectedByPermission = 0;

        for (Issue issue : issueMassUpdate.issues) {
            issue.refresh();
            if (issueMassUpdate.delete) {
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
                if(hasAssignee(issue)) {
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
                for (IssueLabel label : issueMassUpdate.attachingLabel) {
                    issue.labels.add(label);
                }
            }

            if (issueMassUpdate.detachingLabel != null) {
                for (IssueLabel label : issueMassUpdate.detachingLabel) {
                    issue.labels.remove(label);
                }
            }

            issue.updatedDate = JodaDateUtil.now();
            issue.update();
            updatedItems++;

            Issue updatedIssue = Issue.finder.byId(issue.id);
            if(assigneeChanged) {
                NotificationEvent notiEvent = NotificationEvent.afterAssigneeChanged(oldAssignee, updatedIssue);
                IssueEvent.addFromNotificationEvent(notiEvent, issue, UserApp.currentUser().loginId);
            }
            if(stateChanged) {
                NotificationEvent notiEvent = NotificationEvent.afterStateChanged(oldState, updatedIssue);
                IssueEvent.addFromNotificationEvent(notiEvent, issue, UserApp.currentUser().loginId);
            }
        }

        if (updatedItems == 0 && rejectedByPermission > 0) {
            return forbidden(ErrorViews.Forbidden.render("error.forbidden", project));
        }

        // Determine type of response with Accept header
        if (HttpUtil.isJSONPreferred(request())){
            // jQuery treats as error if response text empty
            // on dataType is json
            return ok("{}");
        } else {
            return redirect(request().getHeader("Referer"));
        }
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
    @IsCreatable(ResourceType.ISSUE_POST)
    public static Result newIssue(String ownerName, String projectName) {
        Form<Issue> issueForm = new Form<>(Issue.class).bindFromRequest();
        Project project = ProjectApp.getProject(ownerName, projectName);

        if (issueForm.hasErrors()) {
            return badRequest(create.render("error.validation", issueForm, project));
        }

        final Issue newIssue = issueForm.get();
        removeAnonymousAssignee(newIssue);

        if (newIssue.body == null) {
            return status(REQUEST_ENTITY_TOO_LARGE,
                    ErrorViews.RequestTextEntityTooLarge.render());
        }

        newIssue.createdDate = JodaDateUtil.now();
        newIssue.updatedDate = JodaDateUtil.now();
        newIssue.setAuthor(UserApp.currentUser());
        newIssue.project = project;

        newIssue.state = State.OPEN;

        addLabels(newIssue, request());
        setMilestone(issueForm, newIssue);

        newIssue.save();

        attachUploadFilesToPost(newIssue.asResource());

        NotificationEvent.afterNewIssue(newIssue);

        return redirect(routes.IssueApp.issue(project.owner, project.name, newIssue.getNumber()));
    }

    private static void removeAnonymousAssignee(Issue issue) {
        if(hasAssignee(issue) && isAnonymousAssignee(issue)) {
            issue.assignee = null;
        }
    }

    private static boolean isAnonymousAssignee(Issue issue) {
        return issue.assignee.user != null && issue.assignee.user.isAnonymous();
    }

    private static boolean hasAssignee(Issue issue) {
        return issue.assignee != null;
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
    @With(NullProjectCheckAction.class)
    public static Result editIssueForm(String ownerName, String projectName, Long number) {
        Project project = ProjectApp.getProject(ownerName, projectName);
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
    @IsAllowed(value = Operation.UPDATE, resourceType = ResourceType.ISSUE_POST)
    public static Result nextState(String ownerName, String projectName, Long number) {
        Project project = ProjectApp.getProject(ownerName, projectName);

        final Issue issue = Issue.findByNumber(project, number);

        Call redirectTo = routes.IssueApp.issue(project.owner, project.name, number);
        issue.toNextState();
        NotificationEvent notiEvent = NotificationEvent.afterStateChanged(issue.previousState(), issue);
        IssueEvent.addFromNotificationEvent(notiEvent, issue, UserApp.currentUser().loginId);
        return redirect(redirectTo);
    }

    private static void addAssigneeChangedNotification(Issue modifiedIssue, Issue originalIssue) {
        if(!originalIssue.assignedUserEquals(modifiedIssue.assignee)) {
            User oldAssignee = null;
            if(hasAssignee(originalIssue)) {
                oldAssignee = originalIssue.assignee.user;
            }
            NotificationEvent notiEvent = NotificationEvent.afterAssigneeChanged(oldAssignee, modifiedIssue);
            IssueEvent.addFromNotificationEvent(notiEvent, modifiedIssue, UserApp.currentUser().loginId);
        }
    }

    private static void addStateChangedNotification(Issue modifiedIssue, Issue originalIssue) {
        if(modifiedIssue.state != originalIssue.state) {
            NotificationEvent notiEvent = NotificationEvent.afterStateChanged(originalIssue.state, modifiedIssue);
            IssueEvent.addFromNotificationEvent(notiEvent, modifiedIssue, UserApp.currentUser().loginId);
        }
    }

    private static void addBodyChangedNotification(Issue modifiedIssue, Issue originalIssue) {
        if (!modifiedIssue.body.equals(originalIssue.body)) {
            NotificationEvent notiEvent = NotificationEvent.afterIssueBodyChanged(originalIssue.body, modifiedIssue);
            IssueEvent.addFromNotificationEvent(notiEvent, modifiedIssue, UserApp.currentUser().loginId);
        }
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
     * @see {@link AbstractPostingApp#editPosting}
     */
    @With(NullProjectCheckAction.class)
    public static Result editIssue(String ownerName, String projectName, Long number) {
        Form<Issue> issueForm = new Form<>(Issue.class).bindFromRequest();

        Project project = ProjectApp.getProject(ownerName, projectName);

        if (issueForm.hasErrors()) {
            return badRequest(edit.render("error.validation", issueForm, Issue.findByNumber(project, number), project));
        }

        final Issue issue = issueForm.get();
        removeAnonymousAssignee(issue);
        setMilestone(issueForm, issue);

        final Issue originalIssue = Issue.findByNumber(project, number);

        Call redirectTo = routes.IssueApp.issue(project.owner, project.name, number);

        // preUpdateHook.run would be called just before this issue is updated.
        // It updates some properties only for issues, such as assignee or labels, but not for non-issues.
        Runnable preUpdateHook = new Runnable() {
            @Override
            public void run() {
                // Below addAll() method is needed to avoid the exception, 'Timeout trying to lock table ISSUE'.
                // This is just workaround and the cause of the exception is not figured out yet.
                // Do not replace it to 'issue.comments = originalIssue.comments;'
                issue.voters.addAll(originalIssue.voters);
                issue.comments = originalIssue.comments;
                addLabels(issue, request());

                addAssigneeChangedNotification(issue, originalIssue);
                addStateChangedNotification(issue, originalIssue);
                addBodyChangedNotification(issue, originalIssue);
            }
        };

        return editPosting(originalIssue, issue, issueForm, redirectTo, preUpdateHook);
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
    @With(NullProjectCheckAction.class)
    public static Result deleteIssue(String ownerName, String projectName, Long number) {
        Project project = ProjectApp.getProject(ownerName, projectName);
        Issue issue = Issue.findByNumber(project, number);
        if(!issue.canBeDeleted()) {
            return badRequest(ErrorViews.BadRequest.render());
        }
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
    @With(NullProjectCheckAction.class)
    public static Result newComment(String ownerName, String projectName, Long number) throws IOException {
        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);
        final Issue issue = Issue.findByNumber(project, number);
        Call redirectTo = routes.IssueApp.issue(project.owner, project.name, number);
        Form<IssueComment> commentForm = new Form<>(IssueComment.class).bindFromRequest();

        if (!AccessControl.isResourceCreatable(
                    UserApp.currentUser(), issue.asResource(), ResourceType.ISSUE_COMMENT)) {
            return forbidden(ErrorViews.Forbidden.render("error.forbidden", project));
        }

        if (commentForm.hasErrors()) {
            return badRequest(commentFormValidationResult(project, commentForm));
        }

        if( containsStateTransitionRequest() ){
            toNextState(number, project);
            IssueEvent.addFromNotificationEvent(
                    NotificationEvent.afterStateChanged(issue.previousState(), issue),
                    issue, UserApp.currentUser().loginId);
        }

        final IssueComment comment = commentForm.get();

        IssueComment existingComment = IssueComment.find.where().eq("id", comment.id).findUnique();
        if( existingComment != null){
            existingComment.contents = comment.contents;
            return saveComment(existingComment, commentForm, redirectTo, getContainerUpdater(issue, comment));
        } else {
            return saveComment(comment, commentForm, redirectTo, getContainerUpdater(issue, comment));
        }
    }

    private static Runnable getContainerUpdater(final Issue issue, final IssueComment comment) {
        return new Runnable() {
            @Override
            public void run() {
                comment.issue = issue;
            }
        };
    }

    private static void toNextState(Long number, Project project) {
        final Issue issue = Issue.findByNumber(project, number);
        issue.toNextState();
    }

    private static boolean containsStateTransitionRequest() {

        if (!isMultipartForm() || getStateTransitionFormValue() == null){
            return false;
        }

        return StringUtils.isNotBlank(getStateTransitionFormValue()[0]);
    }

    private static String[] getStateTransitionFormValue() {
        return request().body().asMultipartFormData().asFormUrlEncoded().get("withStateTransition");
    }

    private static boolean isMultipartForm() {
        return request().body().asMultipartFormData() != null;
    }

    private static Html commentFormValidationResult(Project project, Form<IssueComment> commentForm) {
        Map<String,List<ValidationError>> errors = commentForm.errors();
        if( errors.get("contents") != null ){
            return ErrorViews.BadRequest.render("post.comment.empty", project);
        } else {
            return ErrorViews.BadRequest.render("error.validation", project);
        }
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
    @With(NullProjectCheckAction.class)
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
            issue.labels = new HashSet<>();
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
