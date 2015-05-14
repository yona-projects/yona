/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @author Tae
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
import com.avaje.ebean.ExpressionList;
import com.avaje.ebean.Page;
import controllers.annotation.AnonymousCheck;
import controllers.annotation.IsAllowed;
import controllers.annotation.IsCreatable;
import jxl.write.WriteException;
import models.*;
import models.enumeration.Operation;
import models.enumeration.ResourceType;
import models.enumeration.State;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.Tika;
import com.fasterxml.jackson.databind.node.ObjectNode;
import play.twirl.api.Html;
import play.data.Form;
import play.data.validation.ValidationError;
import play.db.ebean.Transactional;
import play.i18n.Messages;
import play.libs.Json;
import play.mvc.*;
import utils.*;
import views.html.issue.*;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@AnonymousCheck
public class IssueApp extends AbstractPostingApp {
    private static final String EXCEL_EXT = "xls";
    private static final Integer ITEMS_PER_PAGE_MAX = 45;

    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
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

    @IsAllowed(Operation.READ)
    public static Result issues(String ownerName, String projectName, String state, String format, int pageNum) throws WriteException, IOException {
        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);

        // SearchCondition from param
        Form<models.support.SearchCondition> issueParamForm = new Form<>(models.support.SearchCondition.class);
        models.support.SearchCondition searchCondition = issueParamForm.bindFromRequest().get();
        searchCondition.pageNum = pageNum - 1;
        searchCondition.labelIds.addAll(LabelApp.getLabelIds(request()));
        searchCondition.labelIds.remove(null);

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

    private static Result issuesAsHTML(Project project, Page<Issue> issues, models.support.SearchCondition searchCondition){
        if(project == null){
            return ok(my_list.render("title.issueList", issues, searchCondition, project));
        } else {
            return ok(list.render("title.issueList", issues, searchCondition, project));
        }

    }

    private static Result issuesAsExcel(Project project, ExpressionList<Issue> el) throws WriteException, IOException {
        byte[] excelData = Issue.excelFrom(el.findList());
        String filename = HttpUtil.encodeContentDisposition(
                project.name + "_issues_" + JodaDateUtil.today().getTime() + "." + EXCEL_EXT);

        response().setHeader("Content-Type", new Tika().detect(filename));
        response().setHeader("Content-Disposition", "attachment; " + filename);

        return ok(excelData);
    }

    private static Result issuesAsPjax(Project project, Page<Issue> issues, models.support.SearchCondition searchCondition) {
        response().setHeader("Cache-Control", "no-cache, no-store");
        if (project == null) {
            return ok(my_partial_search.render("title.issueList", issues, searchCondition, project));
        } else {
            return ok(partial_list_wrap.render("title.issueList", issues, searchCondition, project));
        }

    }

    private static Result issuesAsJson(Project project, Page<Issue> issues) {
        ObjectNode listData = Json.newObject();

        String exceptIdStr = request().getQueryString("exceptId");
        Long exceptId = -1L;

        if(!StringUtils.isEmpty(exceptIdStr)){
            try {
                exceptId = Long.parseLong(exceptIdStr);
            } catch(Exception e){
                return badRequest(listData);
            }
        }

        List<Issue> issueList = issues.getList();

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

    @With(NullProjectCheckAction.class)
    public static Result issue(String ownerName, String projectName, Long number) {
        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);

        Issue issueInfo = Issue.findByNumber(project, number);

        response().setHeader("Vary", "Accept");

        if (issueInfo == null) {
            if (HttpUtil.isJSONPreferred(request())){
                ObjectNode result = Json.newObject();
                result.put("title", number);
                result.put("body", Messages.get("error.notfound.issue_post"));
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

    @IsAllowed(resourceType = ResourceType.ISSUE_POST, value = Operation.READ)
    public static Result timeline(String ownerName, String projectName, Long number) {
        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);
        Issue issueInfo = Issue.findByNumber(project, number);

        for (IssueLabel label: issueInfo.labels) {
            label.refresh();
        }

        return ok(partial_comments.render(project, issueInfo));
    }

    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @IsCreatable(ResourceType.ISSUE_POST)
    public static Result newIssueForm(String ownerName, String projectName) {
        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);
        return ok(create.render("title.newIssue", new Form<>(Issue.class), project));
    }

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
                Assignee newAssignee;
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

            if (issueMassUpdate.attachingLabelIds != null) {
                for (Long labelId : issueMassUpdate.attachingLabelIds) {
                    issue.labels.add(IssueLabel.finder.byId(labelId));
                }
            }

            if (issueMassUpdate.detachingLabelIds != null) {
                for (Long labelId : issueMassUpdate.detachingLabelIds) {
                    issue.labels.remove(IssueLabel.finder.byId(labelId));
                }
            }

            if (issueMassUpdate.isDueDateChanged) {
                issue.dueDate = JodaDateUtil.lastSecondOfDay(issueMassUpdate.dueDate);
            }

            issue.updatedDate = JodaDateUtil.now();
            issue.update();
            updatedItems++;

            if(assigneeChanged) {
                NotificationEvent notiEvent = NotificationEvent.afterAssigneeChanged(oldAssignee, issue);
                IssueEvent.addFromNotificationEvent(notiEvent, issue, UserApp.currentUser().loginId);
            }
            if(stateChanged) {
                NotificationEvent notiEvent = NotificationEvent.afterStateChanged(oldState, issue);
                IssueEvent.addFromNotificationEvent(notiEvent, issue, UserApp.currentUser().loginId);
            }
        }

        if (updatedItems == 0 && rejectedByPermission > 0) {
            return forbidden(ErrorViews.Forbidden.render("error.forbidden", project));
        }

        // Determine type of response with Accept header
        if (HttpUtil.isJSONPreferred(request())){
            if (issueMassUpdate.isDueDateChanged) {
                Issue issue = issueMassUpdate.issues.get(0);
                ObjectNode result = Json.newObject();
                result.put("isOverDue", issue.isOverDueDate());
                result.put("dueDateMsg", issue.isOverDueDate() ? Messages.get("issue.dueDate.overdue") : issue.until());
                return ok(result);
            } else {
                // jQuery treats as error if response text empty
                // on dataType is json
                return ok("{}");
            }
        } else {
            return redirect(request().getHeader("Referer"));
        }
    }

    @Transactional
    @IsCreatable(ResourceType.ISSUE_POST)
    public static Result newIssue(String ownerName, String projectName) {
        Form<Issue> issueForm = new Form<>(Issue.class).bindFromRequest();
        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);

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

        newIssue.dueDate = JodaDateUtil.lastSecondOfDay(newIssue.dueDate);
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

    @With(NullProjectCheckAction.class)
    public static Result editIssueForm(String ownerName, String projectName, Long number) {
        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);
        Issue issue = Issue.findByNumber(project, number);

        if (!AccessControl.isAllowed(UserApp.currentUser(), issue.asResource(), Operation.UPDATE)) {
            return forbidden(ErrorViews.Forbidden.render("error.forbidden", project));
        }

        Form<Issue> editForm = new Form<>(Issue.class).fill(issue);

        return ok(edit.render("title.editIssue", editForm, issue, project));
    }

    @Transactional
    @IsAllowed(value = Operation.UPDATE, resourceType = ResourceType.ISSUE_POST)
    public static Result nextState(String ownerName, String projectName, Long number) {
        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);

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

    @With(NullProjectCheckAction.class)
    public static Result editIssue(String ownerName, String projectName, Long number) {
        Form<Issue> issueForm = new Form<>(Issue.class).bindFromRequest();

        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);

        if (issueForm.hasErrors()) {
            return badRequest(edit.render("error.validation", issueForm, Issue.findByNumber(project, number), project));
        }

        final Issue issue = issueForm.get();
        setAssignee(issueForm, issue, project);
        removeAnonymousAssignee(issue);
        setMilestone(issueForm, issue);
        issue.dueDate = JodaDateUtil.lastSecondOfDay(issue.dueDate);

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

    private static void setAssignee(Form<Issue> issueForm, Issue issue, Project project) {
        String value = issueForm.field("assignee.user.id").value();
        if (value != null) {
            long userId = Long.parseLong(value);
            if (userId != User.anonymous.id) {
                issue.assignee = new Assignee(userId, project.id);
            }
        }
    }

    private static void setMilestone(Form<Issue> issueForm, Issue issue) {
        String milestoneId = issueForm.data().get("milestoneId");
        if(milestoneId != null && !milestoneId.isEmpty()) {
            issue.milestone = Milestone.findById(Long.parseLong(milestoneId));
        } else {
            issue.milestone = null;
        }
    }

    /**
     * @ see {@link AbstractPostingApp#delete(play.db.ebean.Model, models.resource.Resource, Call)}
     */
    @Transactional
    @With(NullProjectCheckAction.class)
    public static Result deleteIssue(String ownerName, String projectName, Long number) {
        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);
        Issue issue = Issue.findByNumber(project, number);
        if(!issue.canBeDeleted()) {
            return badRequest(ErrorViews.BadRequest.render());
        }
        Call redirectTo =
            routes.IssueApp.issues(project.owner, project.name, State.OPEN.state(), "html", 1);

        return delete(issue, issue.asResource(), redirectTo);
    }

    /**
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

        final IssueComment comment = commentForm.get();

        IssueComment existingComment = IssueComment.find.where().eq("id", comment.id).findUnique();

        if (commentForm.hasErrors()) {
            flash(Constants.WARNING, "common.comment.empty");
            return redirect(routes.IssueApp.issue(project.owner, project.name, number));
        }

        Comment savedComment;
        if (existingComment != null) {
            existingComment.contents = comment.contents;
            savedComment = saveComment(existingComment, getContainerUpdater(issue, comment));
            NotificationEvent.afterCommentUpdated(savedComment);
        } else {
            savedComment = saveComment(comment, getContainerUpdater(issue, comment));
            NotificationEvent.afterNewComment(savedComment);
        }

        if( containsStateTransitionRequest() ){
            toNextState(number, project);
            IssueEvent.addFromNotificationEvent(
                    NotificationEvent.afterStateChanged(issue.previousState(), issue),
                    issue, UserApp.currentUser().loginId);
        }

        return redirect(RouteUtil.getUrl(savedComment));
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
                if(!StringUtils.isEmpty(labelId)) {
                    issue.labels.add(IssueLabel.finder.byId(Long.parseLong(labelId)));
                }
            }
        }
    }
}
