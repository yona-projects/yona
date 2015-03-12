/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Wansoon Park
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

import actors.PullRequestMergingActor;
import akka.actor.Props;
import com.avaje.ebean.Page;
import com.fasterxml.jackson.databind.node.ObjectNode;
import controllers.annotation.AnonymousCheck;
import controllers.annotation.IsAllowed;
import controllers.annotation.IsCreatable;
import controllers.annotation.IsOnlyGitAvailable;
import errors.PullRequestException;
import models.*;
import models.enumeration.Operation;
import models.enumeration.ResourceType;
import models.enumeration.RoleType;
import models.enumeration.State;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.tmatesoft.svn.core.SVNException;
import play.api.mvc.Call;
import play.data.Form;
import play.db.ebean.Transactional;
import play.libs.Akka;
import play.libs.F;
import play.libs.F.Function;
import play.libs.F.Promise;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Result;
import playRepository.GitBranch;
import playRepository.GitRepository;
import playRepository.RepositoryService;
import utils.*;
import views.html.error.notfound;
import views.html.git.*;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@IsOnlyGitAvailable
@AnonymousCheck
public class PullRequestApp extends Controller {

    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @IsCreatable(ResourceType.FORK)
    public static Result newFork(String userName, String projectName, String forkOwner) {
        String destination = findDestination(forkOwner);
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        List<OrganizationUser> orgUserList = OrganizationUser.findByAdmin(UserApp.currentUser().id);
        List<Project> forkedProjects = Project.findByOwnerAndOriginalProject(destination, project);
        Project forkProject = Project.copy(project, destination);
        return ok(fork.render("fork", project, forkProject, forkedProjects, new Form<>(Project.class), orgUserList));
    }

    private static String findDestination(String forkOwner) {
        Organization organization = Organization.findByName(forkOwner);
        if (OrganizationUser.isAdmin(organization, UserApp.currentUser())) {
            return forkOwner;
        }
        return UserApp.currentUser().loginId;
    }

    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @IsCreatable(ResourceType.FORK)
    public static Result fork(String userName, String projectName) {
        Form<Project> forkProjectForm = new Form<>(Project.class).bindFromRequest();
        Project projectForm = forkProjectForm.get();
        String destination = findDestination(projectForm.owner);
        Project originalProject = Project.findByOwnerAndProjectName(userName, projectName);

        if (Project.exists(destination, projectForm.name)) {
            flash(Constants.WARNING, "project.name.duplicate");
            forkProjectForm.reject("name");
            return redirect(routes.PullRequestApp.newFork(originalProject.owner, originalProject.name, destination));
        }

        Project forkProject = Project.copy(originalProject, destination);
        forkProject.name = projectForm.name;
        forkProject.projectScope = projectForm.projectScope;
        originalProject.addFork(forkProject);

        return ok(clone.render("fork", forkProject));
    }

    @Transactional
    @IsCreatable(ResourceType.FORK)
    public static Result doClone(String userName, String projectName) {
        Form<Project> form = new Form<>(Project.class).bindFromRequest();
        Project projectForm = form.get();
        String destination = findDestination(projectForm.owner);

        String status = "status";
        String failed = "failed";
        String url = "url";
        ObjectNode result = Json.newObject();

        Project originalProject = Project.findByOwnerAndProjectName(userName, projectName);

        if(originalProject == null) {
            result.put(status, failed);
            result.put(url, routes.Application.index().url());
            return ok(result);
        }

        User currentUser = UserApp.currentUser();
        if(!AccessControl.isProjectResourceCreatable(currentUser, originalProject, ResourceType.FORK)) {
            result.put(status, failed);
            result.put(url, routes.UserApp.loginForm().url());
            return ok(result);
        }

        Project forkProject = Project.copy(originalProject, destination);
        forkProject.name = projectForm.name;
        forkProject.projectScope = projectForm.projectScope;
        if (Organization.isNameExist(destination)) {
            forkProject.organization = Organization.findByName(destination);
        }
        originalProject.addFork(forkProject);

        try {
            GitRepository.cloneLocalRepository(originalProject, forkProject);
            Long projectId = Project.create(forkProject);
            ProjectUser.assignRole(currentUser.id, projectId, RoleType.MANAGER);
            result.put(status, "success");
            result.put(url, routes.ProjectApp.project(forkProject.owner, forkProject.name).url());
            return ok(result);
        } catch (Exception e) {
            play.Logger.error(MessageFormat.format("Failed to fork \"{0}\"", originalProject), e);
            result.put(status, failed);
            result.put(url, routes.PullRequestApp.pullRequests(originalProject.owner, originalProject.name).url());
            return ok(result);
        }
    }

    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @IsCreatable(ResourceType.FORK)
    public static Result newPullRequestForm(String userName, String projectName) throws IOException, GitAPIException {
        final Project project = Project.findByOwnerAndProjectName(userName, projectName);

        ValidationResult validation = validateBeforePullRequest(project);
        if(validation.hasError()) {
            return validation.getResult();
        }

        final List<Project> projects = project.getAssociationProjects();

        final Project fromProject = getSelectedProject(project, request().getQueryString("fromProjectId"), false);
        final Project toProject = getSelectedProject(project, request().getQueryString("toProjectId"), true);

        final List<GitBranch> fromBranches = new GitRepository(fromProject).getBranches();
        final List<GitBranch> toBranches = new GitRepository(toProject).getBranches();

        if(fromBranches.isEmpty()) {
            return badRequest(ErrorViews.BadRequest.render("error.pullRequest.empty.from.repository", fromProject, MenuType.PULL_REQUEST));
        }
        if(toBranches.isEmpty()) {
            return badRequest(ErrorViews.BadRequest.render("error.pullRequest.empty.to.repository", toProject));
        }

        final PullRequest pullRequest = PullRequest.createNewPullRequest(fromProject, toProject
                , StringUtils.defaultIfBlank(request().getQueryString("fromBranch"), fromBranches.get(0).getName())
                , StringUtils.defaultIfBlank(request().getQueryString("toBranch"), project.defaultBranch()));

        return ok(create.render("title.newPullRequest", new Form<>(PullRequest.class).fill(pullRequest), project, projects, fromProject, toProject, fromBranches, toBranches, pullRequest));
    }

    private static Project getSelectedProject(Project project, String projectId, boolean isToProject) {
        Project selectedProject = project;
        if(isToProject && project.isForkedFromOrigin() && project.originalProject.menuSetting.code
                && project.originalProject.menuSetting.pullRequest) {
                selectedProject = project.originalProject;
        }

        if(StringUtils.isNumeric(projectId)) {
            selectedProject = Project.find.byId(Long.parseLong(projectId));
        }

        return selectedProject;
    }

    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @IsCreatable(ResourceType.FORK)
    public static Result mergeResult(String userName, String projectName) throws IOException, GitAPIException {
        final Project project = Project.findByOwnerAndProjectName(userName, projectName);

        ValidationResult validation = validateBeforePullRequest(project);
        if(validation.hasError()) {
            return validation.getResult();
        }

        final Project fromProject = getSelectedProject(project, request().getQueryString("fromProjectId"), false);
        final Project toProject = getSelectedProject(project, request().getQueryString("toProjectId"), true);

        final List<GitBranch> fromBranches = new GitRepository(fromProject).getBranches();
        final List<GitBranch> toBranches = new GitRepository(toProject).getBranches();

        final PullRequest pullRequest = PullRequest.createNewPullRequest(fromProject, toProject
                , StringUtils.defaultIfBlank(request().getQueryString("fromBranch"), fromBranches.get(0).getName())
                , StringUtils.defaultIfBlank(request().getQueryString("toBranch"), toBranches.get(0).getName()));

        PullRequestMergeResult mergeResult = pullRequest.getPullRequestMergeResult();

        response().setHeader("Cache-Control", "no-cache, no-store");
        return ok(partial_merge_result.render(project, pullRequest, mergeResult.getGitCommits(),
                mergeResult.conflicts()));
    }

    @Transactional
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @IsCreatable(ResourceType.FORK)
    public static Result newPullRequest(String userName, String projectName) throws IOException, GitAPIException {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);

        ValidationResult validation = validateBeforePullRequest(project);
        if(validation.hasError()) {
            return validation.getResult();
        }

        Form<PullRequest> form = new Form<>(PullRequest.class).bindFromRequest();
        validateForm(form);
        if(form.hasErrors()) {
            List<GitBranch> fromBranches = new GitRepository(project).getBranches();
            List<GitBranch> toBranches = new GitRepository(project.originalProject).getBranches();
            return ok(create.render("title.newPullRequest", new Form<>(PullRequest.class), project, null, null, null, fromBranches, toBranches, null));
        }

        PullRequest pullRequest = form.get();

        if (pullRequest.body == null) {
            return status(REQUEST_ENTITY_TOO_LARGE,
                    ErrorViews.RequestTextEntityTooLarge.render());
        }

        pullRequest.created = JodaDateUtil.now();
        pullRequest.contributor = UserApp.currentUser();
        pullRequest.toProject = Project.find.byId(pullRequest.toProjectId);
        pullRequest.fromProject = Project.find.byId(pullRequest.fromProjectId);
        pullRequest.isMerging = false;
        pullRequest.isConflict = false;

        PullRequest sentRequest = PullRequest.findDuplicatedPullRequest(pullRequest);
        if(sentRequest != null) {
            return redirect(routes.PullRequestApp.pullRequest(pullRequest.toProject.owner, pullRequest.toProject.name, sentRequest.number));
        }

        pullRequest.save();

        PushedBranch.removeByPullRequestFrom(pullRequest);

        AbstractPostingApp.attachUploadFilesToPost(pullRequest.asResource());

        Call pullRequestCall = routes.PullRequestApp.pullRequest(pullRequest.toProject.owner, pullRequest.toProject.name, pullRequest.number);

        NotificationEvent notiEvent = NotificationEvent.afterNewPullRequest(pullRequest);
        PullRequestEvent.addFromNotificationEvent(notiEvent, pullRequest);

        PullRequestEventMessage message = new PullRequestEventMessage(UserApp.currentUser(), request(), pullRequest, notiEvent.eventType);
        Akka.system().actorOf(Props.create(PullRequestMergingActor.class)).tell(message, null);

        return redirect(pullRequestCall);
    }

    private static void validateForm(Form<PullRequest> form) {
        Map<String, String> data = form.data();
        ValidationUtils.rejectIfEmpty(flash(), data.get("fromBranch"), "pullRequest.fromBranch.required");
        ValidationUtils.rejectIfEmpty(flash(), data.get("toBranch"), "pullRequest.toBranch.required");
        ValidationUtils.rejectIfEmpty(flash(), data.get("title"), "pullRequest.title.required");
    }

    @IsAllowed(Operation.READ)
    public static Result pullRequests(String userName, String projectName) {
        return pullRequests(userName, projectName, Category.OPEN);
    }

    @IsAllowed(Operation.READ)
    public static Result closedPullRequests(String userName, String projectName) {
        return pullRequests(userName, projectName, Category.CLOSED);
    }

    @IsAllowed(Operation.READ)
    public static Result sentPullRequests(String userName, String projectName) {
        return pullRequests(userName, projectName, Category.SENT);
    }

    private static Result pullRequests(String userName, String projectName, Category category) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        SearchCondition condition = Form.form(SearchCondition.class).bindFromRequest().get();
        condition.setProject(project).setCategory(category);
        Page<PullRequest> page = PullRequest.findPagingList(condition);
        if (HttpUtil.isPJAXRequest(request())) {
            response().setHeader("Cache-Control", "no-cache, no-store");
            return ok(partial_search.render(project, page, condition, category.code));
        } else {
            return ok(list.render(project, page, condition, category.code));
        }
    }

    @IsAllowed(value = Operation.READ, resourceType = ResourceType.PULL_REQUEST)
    public static Result pullRequest(String userName, String projectName, long pullRequestNumber) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        PullRequest pullRequest = PullRequest.findOne(project, pullRequestNumber);

        boolean canDeleteBranch = false;
        boolean canRestoreBranch = false;

        if (pullRequest.isMerged()) {
            canDeleteBranch = GitRepository.canDeleteFromBranch(pullRequest);
            canRestoreBranch = GitRepository.canRestoreBranch(pullRequest);
        }

        UserApp.currentUser().visits(project);
        return ok(view.render(project, pullRequest, canDeleteBranch, canRestoreBranch));
    }

    @IsAllowed(value = Operation.READ, resourceType = ResourceType.PULL_REQUEST)
    public static Result pullRequestState(String userName, String projectName, long pullRequestNumber) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        PullRequest pullRequest = PullRequest.findOne(project, pullRequestNumber);

        boolean canDeleteBranch = false;
        boolean canRestoreBranch = false;

        if (pullRequest.isMerged()) {
            canDeleteBranch = GitRepository.canDeleteFromBranch(pullRequest);
            canRestoreBranch = GitRepository.canRestoreBranch(pullRequest);
        }

        if(HttpUtil.isRequestedWithXHR(request()) && !HttpUtil.isPJAXRequest(request())){
            ObjectNode requestState = Json.newObject();
            requestState.put("id", pullRequestNumber);
            requestState.put("isOpen",     pullRequest.isOpen());
            requestState.put("isClosed",   pullRequest.isClosed());
            requestState.put("isMerged",   pullRequest.isMerged());
            requestState.put("isMerging",  pullRequest.isMerging);
            requestState.put("isConflict", pullRequest.isConflict);
            requestState.put("canDeleteBranch", canDeleteBranch);
            requestState.put("canRestoreBranch", canRestoreBranch);
            requestState.put("html", partial_state.render(project, pullRequest, canDeleteBranch, canRestoreBranch).toString());
            return ok(requestState);
        }
        return ok(partial_state.render(project, pullRequest, canDeleteBranch, canRestoreBranch));
    }

    @IsAllowed(value = Operation.READ, resourceType = ResourceType.PULL_REQUEST)
    public static Result pullRequestChanges(String userName, String projectName,
                                            long pullRequestNumber) {
        return specificChange(userName, projectName, pullRequestNumber, null);
    }

    @IsAllowed(value = Operation.READ, resourceType = ResourceType.PULL_REQUEST)
    public static Result specificChange(String userName, String projectName,
                                            long pullRequestNumber, String commitId) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        PullRequest pullRequest = PullRequest.findOne(project, pullRequestNumber);
        return ok(views.html.git.viewChanges.render(project, pullRequest, commitId));
    }

    @Transactional
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @IsAllowed(value = Operation.ACCEPT, resourceType = ResourceType.PULL_REQUEST)
    public static Promise<Result> accept(final String userName, final String projectName,
                                final long pullRequestNumber) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        final PullRequest pullRequest = PullRequest.findOne(project, pullRequestNumber);

        // change it's state to block other accept request.
        pullRequest.isMerging = true;
        pullRequest.update();

        final PullRequestEventMessage message = new PullRequestEventMessage(
                UserApp.currentUser(), request(), project, pullRequest.toBranch);

        if(project.isUsingReviewerCount && !pullRequest.isReviewed()) {
            return Promise.pure((Result) badRequest(
                    ErrorViews.BadRequest.render("pullRequest.not.enough.review.point")));
        }

        Promise<Void> promise = Promise.promise(
                new F.Function0<Void>() {
                    public Void apply() throws Exception {
                        pullRequest.merge(message);
                        // mark the end of the merge
                        pullRequest.endMerge();
                        pullRequest.update();
                        return null;
                    }
                }
        );

        return promise.map(new Function<Void, Result>() {
            @Override
            public Result apply(Void v) throws Throwable {
                return redirect(routes.PullRequestApp.pullRequest(userName, projectName,
                        pullRequestNumber));
            }
        });
    }

    private static void addNotification(PullRequest pullRequest, State from, State to) {
        NotificationEvent notiEvent = NotificationEvent.afterPullRequestUpdated(pullRequest, from, to);
        PullRequestEvent.addFromNotificationEvent(notiEvent, pullRequest);
    }

    @Transactional
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @IsAllowed(value = Operation.CLOSE, resourceType = ResourceType.PULL_REQUEST)
    public static Result close(String userName, String projectName, Long pullRequestNumber) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        PullRequest pullRequest = PullRequest.findOne(project, pullRequestNumber);

        State beforeState = pullRequest.state;
        pullRequest.close();

        Call call = routes.PullRequestApp.pullRequest(userName, projectName, pullRequestNumber);
        addNotification(pullRequest, beforeState, State.CLOSED);

        return redirect(call);
    }

    @Transactional
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @IsAllowed(value = Operation.REOPEN, resourceType = ResourceType.PULL_REQUEST)
    public static Result open(String userName, String projectName, Long pullRequestNumber) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        PullRequest pullRequest = PullRequest.findOne(project, pullRequestNumber);

        if (pullRequest.isMerged() || pullRequest.isOpen()) {
            return badRequest(ErrorViews.BadRequest.render());
        }

        State beforeState = pullRequest.state;
        pullRequest.reopen();

        Call call = routes.PullRequestApp.pullRequest(userName, projectName, pullRequestNumber);
        addNotification(pullRequest, beforeState, State.OPEN);

        PullRequestEventMessage message = new PullRequestEventMessage(
                UserApp.currentUser(), request(), pullRequest);
        Akka.system().actorOf(Props.create(PullRequestMergingActor.class)).tell(message, null);

        return redirect(call);
    }

    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @IsAllowed(value = Operation.UPDATE, resourceType = ResourceType.PULL_REQUEST)
    public static Result editPullRequestForm(String userName, String projectName, Long pullRequestNumber) throws IOException, GitAPIException {
        Project toProject = Project.findByOwnerAndProjectName(userName, projectName);
        PullRequest pullRequest = PullRequest.findOne(toProject, pullRequestNumber);
        Project fromProject = pullRequest.fromProject;

        Form<PullRequest> editForm = new Form<>(PullRequest.class).fill(pullRequest);
        List<GitBranch> fromBranches = new GitRepository(pullRequest.fromProject).getBranches();
        List<GitBranch> toBranches = new GitRepository(pullRequest.toProject).getBranches();

        return ok(edit.render("title.editPullRequest", editForm, fromProject, fromBranches, toBranches, pullRequest));
    }

    @Transactional
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @IsAllowed(value = Operation.UPDATE, resourceType = ResourceType.PULL_REQUEST)
    public static Result editPullRequest(String userName, String projectName, Long pullRequestNumber) {
        Project toProject = Project.findByOwnerAndProjectName(userName, projectName);
        PullRequest pullRequest = PullRequest.findOne(toProject, pullRequestNumber);
        Project fromProject = pullRequest.fromProject;

        Form<PullRequest> pullRequestForm = new Form<>(PullRequest.class).bindFromRequest();
        PullRequest updatedPullRequest = pullRequestForm.get();

        if (pullRequest.body == null) {
            return status(REQUEST_ENTITY_TOO_LARGE,
                    ErrorViews.RequestTextEntityTooLarge.render());
        }

        updatedPullRequest.toProject = toProject;
        updatedPullRequest.fromProject = fromProject;

        if(!updatedPullRequest.hasSameBranchesWith(pullRequest)) {
            PullRequest sentRequest = PullRequest.findDuplicatedPullRequest(updatedPullRequest);
            if(sentRequest != null) {
                flash(Constants.WARNING, "pullRequest.duplicated");
                return redirect(routes.PullRequestApp.editPullRequestForm(fromProject.owner, fromProject.name, pullRequestNumber));
            }
        }

        pullRequest.updateWith(updatedPullRequest);

        AbstractPostingApp.attachUploadFilesToPost(pullRequest.asResource());

        return redirect(routes.PullRequestApp.pullRequest(toProject.owner, toProject.name, pullRequest.number));
    }

    @Transactional
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @IsAllowed(value = Operation.UPDATE, resourceType = ResourceType.PULL_REQUEST)
    public static Result deleteFromBranch(String userName, String projectName, Long pullRequestNumber) {
        Project toProject = Project.findByOwnerAndProjectName(userName, projectName);
        PullRequest pullRequest = PullRequest.findOne(toProject, pullRequestNumber);

        pullRequest.deleteFromBranch();

        return redirect(routes.PullRequestApp.pullRequest(toProject.owner, toProject.name, pullRequestNumber));
    }

    @Transactional
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @IsAllowed(value = Operation.UPDATE, resourceType = ResourceType.PULL_REQUEST)
    public static Result restoreFromBranch(String userName, String projectName, Long pullRequestNumber) {
        Project toProject = Project.findByOwnerAndProjectName(userName, projectName);
        PullRequest pullRequest = PullRequest.findOne(toProject, pullRequestNumber);

        pullRequest.restoreFromBranch();

        return redirect(routes.PullRequestApp.pullRequest(toProject.owner, toProject.name, pullRequestNumber));
    }

    private static ValidationResult validateBeforePullRequest(Project project) {
        Result result = null;
        boolean hasError = false;

        if(ProjectUser.isGuest(project, UserApp.currentUser())) {
            result = forbidden(ErrorViews.BadRequest.render("Guest is not allowed this request", project));
            hasError = true;
        }

        return new ValidationResult(result, hasError);
    }

    @IsCreatable(ResourceType.REVIEW_COMMENT)
    public static Result newComment(String ownerName, String projectName, Long pullRequestId,
                                    String commitId) throws IOException, ServletException,
            SVNException {
        Form<CodeRange> codeRangeForm = new Form<>(CodeRange.class).bindFromRequest();

        Form<ReviewComment> reviewCommentForm = new Form<>(ReviewComment.class)
                .bindFromRequest();

        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);

        if (reviewCommentForm.hasErrors()) {
            return badRequest(ErrorViews.BadRequest.render("error.validation", project));
        }

        PullRequest pullRequest = PullRequest.findById(pullRequestId);

        if (pullRequest == null) {
            return notFound(notfound.render("error.notfound", project, request().path()));
        }

        ReviewComment comment = reviewCommentForm.get();
        comment.author = new UserIdent(UserApp.currentUser());
        if (comment.thread == null) {
            if (codeRangeForm.errors().isEmpty()) {
                CodeCommentThread thread = new CodeCommentThread();

                if (commitId != null) {
                    thread.commitId = commitId;
                } else {
                    thread.commitId = pullRequest.mergedCommitIdTo;
                    thread.prevCommitId = pullRequest.mergedCommitIdFrom;
                }

                User codeAuthor = RepositoryService
                        .getRepository(project)
                        .getCommit(thread.commitId)
                        .getAuthor();
                if (!codeAuthor.isAnonymous()) {
                    thread.codeAuthors.add(codeAuthor);
                }

                thread.codeRange = codeRangeForm.get();

                comment.thread = thread;
            } else {
                // non-range
                NonRangedCodeCommentThread thread = new NonRangedCodeCommentThread();
                if (commitId != null) {
                    thread.commitId = commitId;
                } else {
                    thread.commitId = pullRequest.mergedCommitIdTo;
                    thread.prevCommitId = pullRequest.mergedCommitIdFrom;
                }
                comment.thread = thread;
            }
            comment.thread.project = project;
            comment.thread.state = CommentThread.ThreadState.OPEN;
            comment.thread.createdDate = comment.createdDate;
            comment.thread.author = comment.author;
            pullRequest.addCommentThread(comment.thread);
        } else {
            comment.thread = CommentThread.find.byId(comment.thread.id);
        }
        comment.save();
        pullRequest.update();

        AbstractPostingApp.attachUploadFilesToPost(comment.asResource());

        play.mvc.Call toView;
        if (commitId != null) {
            toView = routes.PullRequestApp.specificChange(ownerName, projectName,
                    pullRequest.number, commitId);
        } else {
            toView = routes.PullRequestApp.pullRequestChanges(ownerName, projectName,
                    pullRequest.number);
        }
        String urlToView = toView + "#comment-" + comment.id;

        NotificationEvent.afterNewComment(UserApp.currentUser(), pullRequest, comment, urlToView);

        return redirect(urlToView);
    }

    static class ValidationResult {
        private Result result;
        private boolean hasError;

        ValidationResult(Result result, boolean hasError) {
            this.result = result;
            this.hasError = hasError;
        }

        public boolean hasError(){
            return hasError;
        }
        public Result getResult() {
            return result;
        }

    }

    public static class SearchCondition implements Cloneable {
        public Project project;
        public String filter;
        public Long contributorId;
        public int pageNum = Constants.DEFAULT_PAGE;
        public Category category;

        public SearchCondition setProject(Project project) {
            this.project = project;
            return this;
        }

        public SearchCondition setFilter(String filter) {
            this.filter = filter;
            return this;
        }

        public SearchCondition setContributorId(Long contributorId) {
            this.contributorId = contributorId;
            return this;
        }

        public SearchCondition setPageNum(int pageNum) {
            this.pageNum = pageNum;
            return this;
        }

        public SearchCondition setCategory(Category category) {
            this.category = category;
            return this;
        }

        @Override
        public SearchCondition clone() throws CloneNotSupportedException {
            SearchCondition clone = new SearchCondition();
            clone.project = this.project;
            clone.filter = this.filter;
            clone.contributorId = this.contributorId;
            clone.pageNum = this.pageNum;
            clone.category = this.category;
            return clone;
        }

        public String queryString() throws UnsupportedEncodingException {
            List<String> queryStrings = new ArrayList<>();
            if (StringUtils.isNotBlank(filter)) {
                queryStrings.add("filter=" + URLEncoder.encode(filter, "UTF-8"));
            }
            if (category != Category.SENT && contributorId != null) {
                queryStrings.add("contributorId=" + contributorId);
            }
            if (pageNum != Constants.DEFAULT_PAGE) {
                queryStrings.add("pageNum=" + pageNum);
            }
            if (queryStrings.isEmpty()) {
                return StringUtils.EMPTY;
            }
            return "?" + StringUtils.join(queryStrings, "&");
        }
    }

    public enum Category {
        OPEN("open", "toProject", "number", State.OPEN),
        CLOSED("closed", "toProject", "received", State.CLOSED, State.MERGED),
        SENT("sent", "fromProject", "created"),
        ACCEPTED("accepted", "fromProject", "created", State.MERGED);

        private Category(String code, String project, String order, State... states) {
            this.code = code;
            this.project = project;
            this.order = order;
            this.states = states;
        }

        private String code;
        private String project;
        private String order;
        private State[] states;

        public String code() {
            return code;
        }

        public String project() {
            return project;
        }

        public String order() {
            return order;
        }

        public State[] states() {
            return states;
        }
    }
}
