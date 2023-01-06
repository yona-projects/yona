/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/
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
import models.enumeration.ProjectScope;
import models.enumeration.ResourceType;
import models.enumeration.State;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.Tika;
import com.fasterxml.jackson.databind.node.ObjectNode;
import play.Configuration;
import play.twirl.api.Html;
import play.data.Form;
import play.data.validation.ValidationError;
import play.db.ebean.Transactional;
import play.i18n.Messages;
import play.libs.Json;
import play.mvc.*;
import utils.*;
import views.html.issue.*;
import views.html.organization.group_issue_list;

import javax.annotation.Nonnull;
import javax.persistence.PersistenceException;
import java.io.IOException;
import java.util.*;

import static utils.JodaDateUtil.getOptionalShortDate;

@AnonymousCheck
public class IssueApp extends AbstractPostingApp {
    private static final String EXCEL_EXT = "xls";
    private static final Integer ITEMS_PER_PAGE_MAX = 45;

    @AnonymousCheck(requiresLogin = false, displaysFlashMessage = true)
    public static Result organizationIssues(@Nonnull String organizationName, @Nonnull String state, @Nonnull String format, int pageNum) throws WriteException, IOException {

        // SearchCondition from param
        Form<models.support.SearchCondition> issueParamForm = new Form<>(models.support.SearchCondition.class);
        models.support.SearchCondition searchCondition = issueParamForm.bindFromRequest().get();
        searchCondition.pageNum = pageNum - 1;

        Integer itemsPerPage = getItemsPerPage();
        Organization organization = Organization.findByName(organizationName);
        if (organization == null) {
            return notFound(ErrorViews.NotFound.render("error.notfound.organization"));
        }
        ExpressionList<Issue> el = searchCondition.asExpressionList(organization);
        Page<Issue> issues = el.findPagingList(itemsPerPage).getPage(searchCondition.pageNum);

        return ok(group_issue_list.render("title.issueList", issues, searchCondition, organization));
    }

    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    public static Result userIssuesPage() throws WriteException, IOException {
        String pageNum = StringUtils.defaultIfBlank(request().getQueryString("pageNum"), "1");
        return controllers.IssueApp.userIssues("", "html", Integer.parseInt(pageNum));
    }

    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    public static Result userIssues(String state, String format, int pageNum) throws WriteException, IOException {
        Project project = null;
        // SearchCondition from param
        Form<models.support.SearchCondition> issueParamForm = new Form<>(models.support.SearchCondition.class);
        models.support.SearchCondition searchCondition = issueParamForm.bindFromRequest().get();
        if (!searchCondition.hasCondition()) {
            searchCondition.assigneeId = UserApp.currentUser().id;
        }
        searchCondition.pageNum = pageNum - 1;

        // default for my issues
        String orderBy = request().getQueryString("orderBy");
        if (StringUtils.isBlank(orderBy)) {
            searchCondition.orderBy = "updatedDate";
        }

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

    @Transactional
    @IsAllowed(Operation.READ)
    public static Result issues(String ownerName, String projectName) throws WriteException, IOException {
       return issues(ownerName, projectName, State.OPEN.state(), "html", 1);
    }

    @IsAllowed(Operation.READ)
    public static List<Issue> findDraftIssues(String ownerName, String projectName) {
        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);

        return   Issue.finder.where()
                .eq("authorLoginId", UserApp.currentUser().loginId)
                .eq("project.id", project.id)
                .eq("isDraft", true)
                .orderBy("number desc").findList();
    }

    @Transactional
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
            return ok(my_list.render("menu.issue", issues, searchCondition, project));
        } else {
            UserApp.currentUser().visits(project);
            return ok(list.render("menu.issue", issues, searchCondition, project));
        }

    }

    private static Result issuesAsExcel(Project project, ExpressionList<Issue> el) throws WriteException, IOException {
        byte[] excelData = Issue.excelFrom(el.findList());
        String filename = HttpUtil.encodeContentDisposition(
                project.name + "_issues_" +  JodaDateUtil.getDateStringWithoutSpace(new Date()) + "." + EXCEL_EXT);

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
            if (project != null) {
                result.put("link", routes.IssueApp.issue(project.owner, project.name, issueId).toString());
            }
            listData.put(issue.id.toString(), result);
        }

        return ok(listData);
    }

    @Transactional
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

        if (issueInfo.isDraft && !issueInfo.authorLoginId.equals(UserApp.currentUser().loginId)) {
            return forbidden(ErrorViews.NotFound.render("error.notfound", project));
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
        UserApp.currentUser().visits(issueInfo);
        // Determine response type with Accept header
        if (HttpUtil.isJSONPreferred(request())){
            ObjectNode result = Json.newObject();
            result.put("id", issueInfo.getNumber());
            result.put("title", issueInfo.title);
            result.put("state", issueInfo.state.toString());
            result.put("body", StringUtils.abbreviate(issueInfo.body, 200));
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

    public static Result newDirectIssueForm(Long commentId) {
        User current = UserApp.currentUser();

        Project project = null;

        // Fallback #1: Last visited project
        List<Project> visitedProjects = current.getVisitedProjects();
        if (project == null && !CollectionUtils.isEmpty(visitedProjects)) {
            project = visitedProjects.get(0);
        }

        if(project != null){
            if (commentId != -1) {
                return newIssueFormByComment(project.owner, project.name, commentId);
            }
            return newIssueForm(project.owner, project.name);
        } else {
            flash(Constants.WARNING, Messages.get("project.is.empty"));
            return Application.index();
        }
    }

    public static Result newDirectMyIssueForm() {
        User current = UserApp.currentUser();

        // Prefixed project. inbox or _private
        Project project = Project.findByOwnerAndProjectName(current.loginId, "inbox");
        if( project == null ) {
            project = Project.findByOwnerAndProjectName(current.loginId, "_private");
        }

        // Fallback to my project which is private and recently created
        if (project == null) {
            List<Project> projects = Project.findProjectsCreatedByUserAndScope(current.loginId, ProjectScope.PRIVATE, "createdDate desc");
            if (!CollectionUtils.isEmpty(projects)) {
                project = projects.get(0);
            }
        }

        // Fallback to my public project
        if( project == null ) {
            List<Project> projects = Project.findProjectsCreatedByUserAndScope(current.loginId, ProjectScope.PUBLIC, "createdDate desc");
            if (!CollectionUtils.isEmpty(projects)) {
                project = projects.get(0);
            }
        }

        if(project != null){
            return newIssueForm(project.owner, project.name);
        } else {
            flash(Constants.WARNING, Messages.get("project.is.empty"));
            return Application.index();
        }
    }

    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @IsCreatable(ResourceType.ISSUE_POST)
    public static Result newIssueForm(String ownerName, String projectName) {
        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);
        String issueTemplate = StringUtils.defaultIfBlank(project.getIssueTemplate(), "");
        return ok(create.render("title.newIssue", new Form<>(Issue.class), project, issueTemplate));
    }

    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @IsCreatable(ResourceType.ISSUE_POST)
    public static Result newIssueFormByComment(String ownerName, String projectName, Long commentId) {
        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);
        Comment comment = IssueComment.find.byId(commentId);
        String context = Configuration.root().getString("application.context");
        String contextPath = context == null ? "" : context;

        String reference = comment.contents + "\n\n_Originally posted by @" + comment.authorLoginId + " in "
                + Config.getScheme() + "://" + request().host() + contextPath + RouteUtil.getUrl(comment);

        String conetent = StringUtils.defaultIfBlank(reference, "");
        return ok(create.render("title.newIssue", new Form<>(Issue.class), project, conetent));
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
            if (issue.isDraft) continue;

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

            updateAssigneeIfChanged(issueMassUpdate.assignee, project, issue);
            updateStateIfChanged(issueMassUpdate.state, issue);
            updateMilestoneIfChanged(issueMassUpdate.milestone, issue);
            updateLabelIfChanged(issueMassUpdate.attachingLabelIds,
                    issueMassUpdate.detachingLabelIds, issue);

            if (issueMassUpdate.isDueDateChanged) {
                issue.dueDate = JodaDateUtil.lastSecondOfDay(issueMassUpdate.dueDate);
            }

            issue.updatedDate = JodaDateUtil.now();
            issue.update();
            updatedItems++;
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

    private static void updateLabelIfChanged(List<Long> attachingLabelIds, List<Long> detachingLabelIds,
                                             Issue issue) {
        boolean isLabelChanged = false;
        StringBuilder addedLabels = new StringBuilder();
        StringBuilder deletedLabels = new StringBuilder();

        if (attachingLabelIds != null) {
            for (Long labelId : attachingLabelIds) {
                IssueLabel label = IssueLabel.finder.byId(labelId);
                issue.labels.add(label);
                isLabelChanged = true;
                addedLabels.append(label.category.name).append(" - ").append(label.name).append(" ").append(label.color);
            }
        }

        if (detachingLabelIds != null) {
            for (Long labelId : detachingLabelIds) {
                IssueLabel label = IssueLabel.finder.byId(labelId);
                issue.labels.remove(label);
                isLabelChanged = true;
                deletedLabels.append(label.category.name).append(" - ").append(label.name).append(" ").append(label.color);
            }
        }

        if(isLabelChanged) {
            NotificationEvent notiEvent = NotificationEvent.afterIssueLabelChanged(
                    addedLabels.toString(),
                    deletedLabels.toString(),
                    issue);
            IssueEvent.addFromNotificationEventWithoutSkipEvent(notiEvent, issue, UserApp.currentUser().loginId);
        }
    }

    private static void updateMilestoneIfChanged(Milestone newMilestone, Issue issue) {

        Long oldMilestoneId = issue.milestoneId();

        if (!isMilestoneChanged(newMilestone, issue.milestone)) {
            return;
        }

        if(newMilestone.isNullMilestone()) {
            issue.milestone = null;
        } else {
            issue.milestone = newMilestone;
        }
        NotificationEvent notiEvent = NotificationEvent.afterMilestoneChanged(oldMilestoneId, issue);
        IssueEvent.addFromNotificationEvent(notiEvent, issue, UserApp.currentUser().loginId);
    }

    private static boolean isMilestoneChanged(Milestone newMilestone, Milestone oldMilestone) {
        if (newMilestone == null) {
            return false;
        }

        if (oldMilestone != null && oldMilestone.id.equals(newMilestone.id)) {
            return false;
        }

        return true;
    }

    private static void updateStateIfChanged(State newState, Issue issue) {
        boolean stateChanged = false;
        State oldState = null;
        if ((newState != null) && (issue.state != newState)) {
            stateChanged = true;
            oldState = issue.state;
            issue.state = newState;
        }
        if(!issue.isDraft && stateChanged) {
            NotificationEvent notiEvent = NotificationEvent.afterStateChanged(oldState, issue);
            IssueEvent.addFromNotificationEvent(notiEvent, issue, UserApp.currentUser().loginId);
        }
    }

    private static void updateAssigneeIfChanged(User assignee, Project project, Issue issue) {
        boolean assigneeChanged = false;
        User oldAssignee = null;

        if (assignee != null) {
            if(hasAssignee(issue)) {
                oldAssignee = issue.assignee.user;
            }
            Assignee newAssignee;
            if (assignee.isAnonymous()) {
                newAssignee = null;
            } else {
                newAssignee = Assignee.add(assignee.id, project.id);
            }
            assigneeChanged = !issue.assignedUserEquals(newAssignee);
            issue.assignee = newAssignee;
        }

        if(assigneeChanged) {
            NotificationEvent notiEvent = NotificationEvent.afterAssigneeChanged(oldAssignee, issue);
            IssueEvent.addFromNotificationEvent(notiEvent, issue, UserApp.currentUser().loginId);
        }
    }

    @Transactional
    @IsCreatable(ResourceType.ISSUE_POST)
    public static Result newIssue(String ownerName, String projectName) {
        Form<Issue> issueForm = new Form<>(Issue.class).bindFromRequest();
        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);

        if (issueForm.hasErrors()) {
            String issueTemplate = StringUtils.defaultIfBlank(project.getIssueTemplate(), "");
            return badRequest(create.render("error.validation", issueForm, project, issueTemplate));
        }

        final Issue newIssue = issueForm.get();
        if(hasTargetProject(newIssue)){
            Project toAnotherProject = Project.find.byId(Long.valueOf(newIssue.targetProjectId));
            if(toAnotherProject == null){
                flash(Constants.WARNING, Messages.get("error.notfound.project"));
                return badRequest(create.render("title.newIssue", new Form<>(Issue.class), project, null));
            } else {
                if (!AccessControl.isProjectResourceCreatable(
                        UserApp.currentUser(), toAnotherProject, ResourceType.ISSUE_POST)) {
                    return forbidden(ErrorViews.Forbidden.render("error.forbidden", toAnotherProject));
                }
                project = toAnotherProject;
            }
        }

        if (newIssue.body == null) {
            return status(REQUEST_ENTITY_TOO_LARGE,
                    ErrorViews.RequestTextEntityTooLarge.render());
        }

        if(StringUtils.isNotEmpty(newIssue.parentIssueId)){
            newIssue.parent = Issue.finder.byId(Long.valueOf(newIssue.parentIssueId));
        }
        newIssue.createdDate = JodaDateUtil.now();
        newIssue.updatedDate = JodaDateUtil.now();
        newIssue.setAuthor(UserApp.currentUser());
        newIssue.project = project;

        String assineeLoginId = null;
        String[] assigneeLoginIds = request().body().asMultipartFormData().asFormUrlEncoded().get("assigneeLoginId");
        if(assigneeLoginIds != null && assigneeLoginIds.length > 0) {
            assineeLoginId = assigneeLoginIds[0];
        }

        User assigneeUser = User.findByLoginId(assineeLoginId);

        if(!assigneeUser.isAnonymous()){
            newIssue.assignee = new Assignee(assigneeUser.id, project.id);
        } else {
            newIssue.assignee = null;
        }
        if (newIssue.isDraft) {
            newIssue.state = State.DRAFT;
        } else {
            newIssue.state = State.OPEN;
        }

        if (newIssue.project.id.equals(Project.findByOwnerAndProjectName(ownerName, projectName).id)) {
            addLabels(newIssue, request());
        }
        setMilestone(issueForm, newIssue);

        newIssue.dueDate = JodaDateUtil.lastSecondOfDay(newIssue.dueDate);
        newIssue.save();

        attachUploadFilesToPost(newIssue.asResource());

        if (!newIssue.isDraft) {
            NotificationEvent.afterNewIssue(newIssue);
        }

        if (StringUtils.isNotEmpty(newIssue.referCommentId) && !newIssue.isDraft) {
            String context = Configuration.root().getString("application.context");
            String contextPath = context == null ? "" : context;
            String content = Messages.get("issue.derived") + ": " + Config.getScheme() + "://" + request().host() + contextPath + RouteUtil.getUrl(newIssue);

            IssueComment parent = IssueComment.find.byId(Long.parseLong(newIssue.referCommentId));
            IssueComment referComment = new IssueComment(parent.issue, UserApp.currentUser(), content);
            referComment.parentCommentId = newIssue.referCommentId;
            newReferComment(referComment);
        }

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

        if (issue == null) {
            return notFound(ErrorViews.Forbidden.render("error.notfound", project));
        }

        if (!AccessControl.isAllowed(UserApp.currentUser(), issue.asResource(), Operation.READ)) {
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
        State state = issue.toNextState();

        if(state == State.OPEN && issue.hasParentIssue() && issue.parent.state == State.CLOSED) {
            issue.parent.toNextState();
        }
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
        if (modifiedIssue.state != originalIssue.state) {
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

    private static void addIssueMovedNotification(Project previous, Issue originalIssue, Issue issue, Set<User> fromWatchers) {
        if (isRequestedToOtherProject(previous, originalIssue.project)) {
            NotificationEvent notiEvent = NotificationEvent.afterIssueMoved(previous, originalIssue, () -> fromWatchers);
            IssueEvent.addFromNotificationEvent(notiEvent, originalIssue, UserApp.currentUser().loginId);

            play.Logger.debug("addIssueMovedNotification - afterIssueMoved receivers: " + notiEvent.receivers);
        }
        NotificationEvent notiEvent = NotificationEvent.afterNewIssue(issue);

        play.Logger.debug("addIssueMovedNotification - afterNewIssue receivers: " + notiEvent.receivers);
    }

    @With(NullProjectCheckAction.class)
    public static Result editIssue(String ownerName, String projectName, Long number) {
        Form<Issue> issueForm = new Form<>(Issue.class).bindFromRequest();

        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);

        if (issueForm.hasErrors()) {
            flash(Constants.WARNING, issueForm.error("name").message());
            return badRequest(edit.render("error.validation", issueForm, Issue.findByNumber(project, number), project));
        }

        final Issue issue = issueForm.get();

        String assineeLoginId = request().body().asMultipartFormData()
                .asFormUrlEncoded().get("assigneeLoginId")[0];

        User assigneeUser = User.findByLoginId(assineeLoginId);
        if(!assigneeUser.isAnonymous()){
            issue.assignee = new Assignee(assigneeUser.id, project.id);
        } else {
            issue.assignee = null;
        }

        setMilestone(issueForm, issue);
        issue.dueDate = JodaDateUtil.lastSecondOfDay(issue.dueDate);

        Issue originalIssue = Issue.findByNumber(project, number);
        Set<User> fromWatchers = originalIssue.getWatchers();

        if(hasTargetProject(issue)) {
            Project toOtherProject = Project.find.byId(Long.valueOf(issue.targetProjectId));
            if (toOtherProject == null) {
                flash(Constants.WARNING, Messages.get("error.notfound.project"));
                return badRequest(edit.render("error.validation", issueForm, Issue.findByNumber(project, number), project));
            }

            if (!AccessControl.isProjectResourceCreatable(
                    UserApp.currentUser(), toOtherProject, ResourceType.ISSUE_POST)) {
                return forbidden(ErrorViews.Forbidden.render("error.forbidden", toOtherProject));
            }

            if (isRequestedToOtherProject(project, toOtherProject)) {
                moveIssueToOtherProject(originalIssue, toOtherProject);
                issue.milestone = null;
            } else {
                updateSubtaskRelation(issue, originalIssue);
            }
        }

        if (issue.isPublish) {
            originalIssue.createdDate = JodaDateUtil.now();
            if (originalIssue.state == State.DRAFT) {
                originalIssue.state = State.OPEN;
            }
            originalIssue.setNumber(Project.increaseLastIssueNumber(originalIssue.project.id));
        }
        Call redirectTo = routes.IssueApp.issue(originalIssue.project.owner, originalIssue.project.name, originalIssue.getNumber());

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
                issue.sharers.addAll(originalIssue.sharers);
                issue.weight = originalIssue.weight;

                final Project previous = Project.findByOwnerAndProjectName(ownerName, projectName);
                if(isRequestedToOtherProject(originalIssue.project, previous)){
                    issue.labels = originalIssue.labels;
                    if(isFromMyOwnPrivateProject(previous)){
                        issue.history = "";
                    } else {
                        if (!issue.isDraft) {
                            addIssueMovedNotification(previous, originalIssue, issue, fromWatchers);
                        }
                    }
                } else {
                    addLabels(issue, request());
                }

                if (issue.isPublish) {
                    NotificationEvent.afterNewIssue(issue);
                    return;
                }

                if (issue.isDraft) {
                    // Do not notify
                    return;
                }

                if(isSelectedToSendNotificationMail() || !originalIssue.isAuthoredBy(UserApp.currentUser())){
                    addAssigneeChangedNotification(issue, originalIssue);
                    addStateChangedNotification(issue, originalIssue);
                    addBodyChangedNotification(issue, originalIssue);
                }
            }
        };

        return editPosting(originalIssue, issue, issueForm, redirectTo, preUpdateHook);
    }

    private static boolean hasTargetProject(Issue issue) {
        return StringUtils.isNotEmpty(issue.targetProjectId);
    }

    private static boolean isFromMyOwnPrivateProject(Project previous) {
        return previous.isPrivate() && previous.owner.equalsIgnoreCase(UserApp.currentUser().loginId);
    }

    private static void moveIssueToOtherProject(Issue originalIssue, Project toOtherProject) {
        updateIssueToOtherProject(originalIssue, toOtherProject);
        moveSubtaskToOtherProject(originalIssue, toOtherProject);

    }

    private static void moveSubtaskToOtherProject(Issue originalIssue, Project toOtherProject) {
        List<Issue> subtasks = Issue.findByParentIssueId(originalIssue.id);
        for(Issue issue: subtasks) {
            updateIssueToOtherProject(issue, toOtherProject);
        }
    }

    private static void updateIssueToOtherProject(Issue issue, Project toOtherProject) {
        issue.project = toOtherProject;
        issue.setNumber(Project.increaseLastIssueNumber(toOtherProject.id));
        issue.createdDate = JodaDateUtil.now();
        issue.updatedDate = JodaDateUtil.now();
        issue.milestone = null;
        for(IssueComment comment: issue.comments){
            comment.projectId = issue.project.id;
            comment.update();
        }
        if (UserApp.currentUser().isMemberOf(toOtherProject) && issue.labels.size() > 0) {
            transferLabels(issue, toOtherProject);
        } else {
            issue.labels = new HashSet<>();
        }
        issue.update();
    }

    private static void transferLabels(Issue originalIssue, Project toProject) {
        Set<IssueLabel> newLabels = new HashSet<>();

        for (IssueLabel label : originalIssue.getLabels()) {
            IssueLabel copiedLabel = IssueLabel.copyIssueLabel(toProject, label);
            IssueLabel existedLabel = copiedLabel.findExistLabel();
            if(existedLabel == null){
                toProject.issueLabels.add(copiedLabel);
                copiedLabel.issues.add(originalIssue);
                copiedLabel.save();
                toProject.update();
            } else {
                copiedLabel = existedLabel;
                copiedLabel.issues.add(originalIssue);
                copiedLabel.update();
            }
            newLabels.add(copiedLabel);
        }

        originalIssue.labels = new HashSet<>(newLabels);
    }

    private static boolean isRequestedToOtherProject(Project project, Project toOtherProject) {
        return !project.id.equals(toOtherProject.id);
    }

    private static void updateSubtaskRelation(Issue issue, Issue originalIssue) {
        if(StringUtils.isEmpty(issue.parentIssueId)){
            issue.parent = null;
        } else {
            issue.parent = Issue.finder.byId(Long.valueOf(issue.parentIssueId));
        }
        originalIssue.parent = issue.parent;
        originalIssue.update();
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

        if (!issue.isDraft) {
            NotificationEvent.afterResourceDeleted(issue, UserApp.currentUser());
        }
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
        Form<IssueComment> commentForm = new Form<>(IssueComment.class).bindFromRequest();

        if (!AccessControl.isResourceCreatable(
                    UserApp.currentUser(), issue.asResource(), ResourceType.ISSUE_COMMENT)) {
            return forbidden(ErrorViews.Forbidden.render("error.forbidden", project));
        }

        if (commentForm.hasErrors()) {
            return badRequest(commentFormValidationResult(project, commentForm));
        }

        final IssueComment comment = commentForm.get();


        if (commentForm.hasErrors()) {
            flash(Constants.WARNING, "common.comment.empty");
            return redirect(routes.IssueApp.issue(project.owner, project.name, number));
        }

        if(StringUtils.isNotEmpty(comment.parentCommentId)){
            comment.setParentComment(IssueComment.find.byId(Long.valueOf(comment.parentCommentId)));
        }

        AddPreviousContent(issue, comment);

        Comment savedComment = saveComment(project, issue, comment);

        if( containsStateTransitionRequest() ){
            toNextState(number, project);
            IssueEvent.addFromNotificationEvent(
                    NotificationEvent.afterStateChanged(issue.previousState(), issue),
                    issue, UserApp.currentUser().loginId);
        } else {
            issue.updatedDate = JodaDateUtil.now();
            issue.update();
        }

        return redirect(RouteUtil.getUrl(savedComment));
    }

    @Transactional
    @With(NullProjectCheckAction.class)
    public static void newReferComment(IssueComment comment) {
        if (!AccessControl.isResourceCreatable(
                UserApp.currentUser(), comment.issue.asResource(), ResourceType.ISSUE_COMMENT)) {
            play.Logger.warn("Http.Status.FORBIDDEN: cannot add issue comment: " + comment.issue);
            return;
        }

        if(StringUtils.isNotEmpty(comment.parentCommentId)){
            comment.setParentComment(IssueComment.find.byId(Long.valueOf(comment.parentCommentId)));
        }

        AddPreviousContent(comment.issue, comment);
        saveComment(comment.issue.project, comment.issue, comment);
    }


    private static void AddPreviousContent(Issue issue, IssueComment comment) {
        if(issue.numOfComments == 0) {
            comment.previousContents = getPrevious("Original issue", issue.body, issue.updatedDate, issue.authorLoginId);
        } else {
            Comment previousComment;
            if (comment.parentCommentId != null) {
                List<IssueComment> siblingComments = comment.getSiblingComments();
                if (siblingComments.size() > 0) {
                    previousComment = siblingComments.get(siblingComments.size() - 1);
                } else {
                    previousComment = comment.getParentComment();
                }
                comment.previousContents = getPrevious("Previous comment", previousComment.contents, previousComment.createdDate, previousComment.authorLoginId);
            } else {
                int commentsSize = issue.comments.size();

                if (issue.numOfComments != commentsSize) {
                    play.Logger.warn("Recalculate comments number of issue: "
                            + issue.project.owner + "/" + issue.project.name + "/" + issue.getNumber()
                    + " " + issue.numOfComments + " -> " + commentsSize);
                    issue.numOfComments = commentsSize;
                    issue.update();
                }

                if (commentsSize > 0) {
                    previousComment = issue.comments.get(commentsSize - 1);
                    comment.previousContents = getPrevious("Previous comment", previousComment.contents, previousComment.createdDate, previousComment.authorLoginId);
                } else {
                    comment.previousContents = getPrevious("Issue", issue.body, issue.updatedDate, issue.authorLoginId);
                    List<IssueComment> list = IssueComment.find.where().eq("issue.id", issue.id).findList();
                    for (IssueComment garbageComment: list) {
                        play.Logger.warn("Garbage comment deleted: " + garbageComment);
                        garbageComment.delete();
                    }
                }
            }
        }
    }

    private static String getPrevious(String templateTitle, String contents, Date updatedDate, String authorLoginId) {
        return "\n\n<br />\n\n--- " + templateTitle + " from @" + authorLoginId + "  " + getOptionalShortDate(updatedDate) + " ---\n\n<br />\n\n" + contents;
    }

    // Just made for compatibility. No meanings.
    public static Result updateComment(String ownerName, String projectName, Long number, Long commentId) throws IOException {
        return newComment(ownerName, projectName, number);
    }

    private static Comment saveComment(Project project, Issue issue, IssueComment comment) {
        Comment savedComment;
        IssueComment existingComment = IssueComment.find.where().eq("id", comment.id).findUnique();
        if (existingComment == null) {
            comment.projectId = project.id;
            savedComment = saveComment(comment, getContainerUpdater(issue, comment));
            NotificationEvent.afterNewComment(savedComment);
        } else {
            existingComment.contents = comment.contents;
            savedComment = saveComment(existingComment, getContainerUpdater(issue, comment));
            if(isSelectedToSendNotificationMail() || !existingComment.isAuthoredBy(UserApp.currentUser())){
                NotificationEvent.afterCommentUpdated(savedComment);
            }
        }
        return savedComment;
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
