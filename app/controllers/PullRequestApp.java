/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Wansoon Park
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

import com.avaje.ebean.Page;

import controllers.annotation.IsCreatable;
import controllers.annotation.ProjectAccess;
import controllers.annotation.PullRequestAccess;
import models.*;
import models.enumeration.*;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.node.ObjectNode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.tmatesoft.svn.core.SVNException;

import actions.AnonymousCheckAction;
import actors.PullRequestEventActor;
import akka.actor.Props;
import play.api.mvc.Call;
import play.data.Form;
import play.db.ebean.Transactional;
import play.libs.Akka;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.With;
import playRepository.*;
import utils.*;
import views.html.git.*;

import javax.servlet.ServletException;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;

/**
 * 프로젝트 복사(포크)와 코드 주고받기(풀리퀘) 기능을 다루는 컨트롤러
 */
public class PullRequestApp extends Controller {

    /**
     * {@code userName}과 {@code projectName}에 해당하는 프로젝트를
     * 복사하여 현재 접속한 사용자의 새 프로젝트로 생성할 수 있다는 안내 메시지와 안내 정보를 보여준다.
     *
     * @param userName
     * @param projectName
     * @return
     */
    @With(AnonymousCheckAction.class)
    @IsCreatable(ResourceType.FORK)
    public static Result newFork(String userName, String projectName) {
        Project project = ProjectApp.getProject(userName, projectName);
        User currentUser = UserApp.currentUser();
        Project forkedProject = Project.findByOwnerAndOriginalProject(currentUser.loginId, project);

        if (forkedProject != null) {
            return ok(fork.render("fork", project, forkedProject, false, new Form<>(Project.class)));
        } else {
            Project forkProject = Project.copy(project, currentUser);
            return ok(fork.render("fork", project, forkProject, true, new Form<>(Project.class)));
        }
    }

    /**
     * {@code userName}과 {@code projectName}에 해당하는 프로젝트를 복사하여
     * 새 프로젝트를 생성할 수 있는지 확인한다.
     *
     * 해당 프로젝트를 복사한 프로젝트가 이미 존재한다면, 해당 프로젝트로 이동한다.
     * Git 프로젝트가 아닐 경우에는 이 기능을 지원하지 않는다.
     * 복사하려는 프로젝트의 이름에 해당하는 프로젝트가 이미 존재한다면, 원본 프로젝트 이름 뒤에 '-1'을 붙여서 생성한다.
     *
     * @param userName
     * @param projectName
     * @return
     */
    @With(AnonymousCheckAction.class)
    @IsCreatable(ResourceType.FORK)
    public static Result fork(String userName, String projectName) {
        Project originalProject = Project.findByOwnerAndProjectName(userName, projectName);
        User currentUser = UserApp.currentUser();

        // 이미 포크한 프로젝트가 있다면 그 프로젝트로 이동.
        Project forkedProject = Project.findByOwnerAndOriginalProject(currentUser.loginId, originalProject);
        if(forkedProject != null) {
            flash(Constants.WARNING, "fork.redirect.exist");
            return redirect(routes.ProjectApp.project(forkedProject.owner, forkedProject.name));
        }

        // 포크 프로젝트 이름 중복 검사
        Form<Project> forkProjectForm = new Form<>(Project.class).bindFromRequest();
        if (Project.exists(UserApp.currentUser().loginId, forkProjectForm.field("name").value())) {
            flash(Constants.WARNING, "project.name.duplicate");
            forkProjectForm.reject("name");
            return redirect(routes.PullRequestApp.newFork(originalProject.owner, originalProject.name));
        }

        // 새 프로젝트 생성
        Project forkProject = Project.copy(originalProject, currentUser);
        Project projectFromForm = forkProjectForm.get();
        forkProject.name = projectFromForm.name;
        forkProject.isPublic = projectFromForm.isPublic;
        originalProject.addFork(forkProject);

        return ok(clone.render("fork", forkProject));
    }

    /**
     * {@code userName}과 {@code projectName}에 해당하는 프로젝트를 복사하여
     * 현재 접속한 사용자의 새 프로젝트를 생성한다.
     *
     * @param userName
     * @param projectName
     * @param name
     * @param isPublic
     * @return
     */
    @Transactional
    public static Result doClone(String userName, String projectName, String name, Boolean isPublic) {
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

        // 새 프로젝트 생성
        Project forkProject = Project.copy(originalProject, currentUser);
        if(name != null && !name.isEmpty()) {
            forkProject.name = name;
        }
        if(isPublic != null) {
            forkProject.isPublic = isPublic;
        }

        originalProject.addFork(forkProject);

        // git clone으로 Git 저장소 생성하고 새 프로젝트를 만들고 권한 추가.
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
            result.put(url, routes.PullRequestApp.pullRequests(originalProject.owner, originalProject.name, 1).url());
            return ok(result);
        }
    }

    /**
     * {@code userName}과 {@code projectName}에 해당하는 프로젝트의 원본 프로젝트로 코드를 보낼 수 있는 코드 보내기 폼을 보여준다.
     *
     * 코드 보내기 폼에서 보내려는 브랜치과 코드를 받을 브랜치를 선택할 수 있도록 브랜치 목록을 보여준다.
     * 보내는 브랜치(fromBranch)와 받는 브랜치(toBranch) 파라미터가 있을 경우 두개의 브랜치를 merge해보고 결과를 반환한다.
     * ajax 요청일 경우 partial로 렌더링한다.
     *
     * @param userName
     * @param projectName
     * @return
     * @throws IOException
     * @throws ServletException
     */
    @With(AnonymousCheckAction.class)
    @IsCreatable(ResourceType.FORK)
    public static Result newPullRequestForm(String userName, String projectName) throws ServletException, IOException, GitAPIException {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);

        ValidationResult validation = validateBeforePullRequest(project);
        if(validation.hasError()) {
            return validation.getResult();
        }

        List<GitBranch> fromBranches = new GitRepository(project).getAllBranches();
        List<GitBranch> toBranches = new GitRepository(project.originalProject).getAllBranches();

        PullRequest pullRequest = PullRequest.createNewPullRequest(project
                            , StringUtils.defaultIfBlank(request().getQueryString("fromBranch"), fromBranches.get(0).getName())
                            , StringUtils.defaultIfBlank(request().getQueryString("toBranch"), project.defaultBranch())); 
        PullRequestMergeResult mergeResult = pullRequest.getPullRequestMergeResult();

        if (HttpUtil.isRequestedWithXHR(request())) {
            response().setHeader("Cache-Control", "no-cache, no-store");
            return ok(partial_diff.render(new Form<>(PullRequest.class).fill(pullRequest), project, pullRequest, mergeResult));
        }

        return ok(create.render("title.newPullRequest", new Form<>(PullRequest.class).fill(pullRequest), project, fromBranches, toBranches, pullRequest, mergeResult));
    }

    /**
     * {@link PullRequest}를 생성한다.
     *
     * Form에서 입력받은 '코드를 보낼 브랜치'와 '코드를 받을 브랜치' 정보를 사용하여
     * 새로운 {@link PullRequest}를 생성한다.
     * 만약, 동일한 브랜치로 주고 받으며 열려있는 상태의 {@link PullRequest}가 있을 때는
     * 해당 {@link PullRequest}로 이동한다.
     *
     * @param userName
     * @param projectName
     * @return
     */
    @Transactional
    @With(AnonymousCheckAction.class)
    @IsCreatable(ResourceType.FORK)
    public static Result newPullRequest(String userName, String projectName) throws ServletException, IOException, GitAPIException {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);

        ValidationResult validation = validateBeforePullRequest(project);
        if(validation.hasError()) {
            return validation.getResult();
        }

        Form<PullRequest> form = new Form<>(PullRequest.class).bindFromRequest();
        validateForm(form);
        if(form.hasErrors()) {
            List<GitBranch> fromBranches = new GitRepository(project).getAllBranches();
            List<GitBranch> toBranches = new GitRepository(project.originalProject).getAllBranches();
            return ok(create.render("title.newPullRequest", new Form<>(PullRequest.class), project, fromBranches, toBranches, null, null));
        }

        Project originalProject = project.originalProject;

        PullRequest pullRequest = form.get();
        pullRequest.created = JodaDateUtil.now();
        pullRequest.contributor = UserApp.currentUser();
        pullRequest.fromProject = project;
        pullRequest.toProject = originalProject;
        pullRequest.isMerging = true;
        pullRequest.isConflict = false;

        // 동일한 브랜치로 주고 받으며, Open 상태의 PullRequest가 이미 존재한다면 해당 PullRequest로 이동한다.
        PullRequest sentRequest = PullRequest.findDuplicatedPullRequest(pullRequest);
        if(sentRequest != null) {
            return redirect(routes.PullRequestApp.pullRequest(originalProject.owner, originalProject.name, sentRequest.number));
        }

        pullRequest.save();

        PushedBranch.removeByPullRequestFrom(pullRequest);
        Attachment.moveAll(UserApp.currentUser().asResource(), pullRequest.asResource());

        Call pullRequestCall = routes.PullRequestApp.pullRequest(originalProject.owner, originalProject.name, pullRequest.number);

        NotificationEvent notiEvent = NotificationEvent.addNewPullRequest(pullRequestCall, request(), pullRequest);
        PullRequestEvent.addEvent(notiEvent, pullRequest);

        PullRequestEventMessage message = new PullRequestEventMessage(
                UserApp.currentUser(), request(), pullRequest.toProject, pullRequest.toBranch);
        Akka.system().actorOf(new Props(PullRequestEventActor.class)).tell(message, null);

        return redirect(pullRequestCall);
    }

    private static void validateForm(Form<PullRequest> form) {
        Map<String, String> data = form.data();
        ValidationUtils.rejectIfEmpty(flash(), data.get("fromBranch"), "pullRequest.fromBranch.required");
        ValidationUtils.rejectIfEmpty(flash(), data.get("toBranch"), "pullRequest.toBranch.required");
        ValidationUtils.rejectIfEmpty(flash(), data.get("title"), "pullRequest.title.required");
    }

    /**
     * 열려 있는 상태의 확인할 코드 요청을 조회한다.
     *
     * @param userName
     * @param projectName
     * @return
     */
    @ProjectAccess(Operation.READ)
    public static Result pullRequests(String userName, String projectName, int pageNum) {

        Project project = Project.findByOwnerAndProjectName(userName, projectName);

        if(!project.vcs.equals("GIT")) {
            return badRequest(ErrorViews.BadRequest.render("Now, only git project is allowed this request.", project));
        }

        Page<PullRequest> page = PullRequest.findPagingList(State.OPEN, project, pageNum - 1);
        return ok(list.render(project, page, "opened"));
    }

    /**
     * 받은 상태의 코드 요청을 조회한다.
     *
     * @param userName
     * @param projectName
     * @return
     */
    @ProjectAccess(Operation.READ)
    public static Result closedPullRequests(String userName, String projectName, int pageNum) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        Page<PullRequest> page = PullRequest.findClosedPagingList(project, pageNum - 1);
        return ok(list.render(project, page, "closed"));
    }

    /**
     * 보류 상태의 코드 요청을 조회한다.
     *
     * @param userName
     * @param projectName
     * @return
     */
    @ProjectAccess(Operation.READ)
    public static Result rejectedPullRequests(String userName, String projectName, int pageNum) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        Page<PullRequest> page = PullRequest.findPagingList(State.REJECTED, project, pageNum - 1);
        return ok(list.render(project, page, "rejected"));
    }

    /**
     * 보낸 코드 요청을 조회한다.
     *
     * @param userName
     * @param projectName
     * @return
     */
    @ProjectAccess(Operation.READ)
    public static Result sentPullRequests(String userName, String projectName, int pageNum) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        Page<PullRequest> page = PullRequest.findSentPullRequests(project, pageNum - 1);
        return ok(list.render(project, page, "sent"));
    }

    /**
     * {@code userName}과 {@code projectName}에 해당하는 프로젝트로 들어온 {@code pullRequestId}에 해당하는 코드 요청을 조회한다.
     *
     * @param userName
     * @param projectName
     * @param pullRequestNumber
     * @return
     */
    @ProjectAccess(Operation.READ)
    @PullRequestAccess(Operation.READ)
    public static Result pullRequest(String userName, String projectName, long pullRequestNumber) throws IOException {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        PullRequest pullRequest = PullRequest.findOne(project, pullRequestNumber);

        boolean canDeleteBranch = false;
        boolean canRestoreBranch = false;

        if (pullRequest.isMerged()) {
            canDeleteBranch = GitRepository.canDeleteFromBranch(pullRequest);
            canRestoreBranch = GitRepository.canRestoreBranch(pullRequest);
        }

        List<PullRequestComment> comments = new ArrayList<>();

        for (PullRequestComment comment : pullRequest.comments) {
            if (comment.line != null && comment.hasValidCommitId()) {
                comments.add(comment);
            }
        }

        return ok(view.render(project, pullRequest, comments, canDeleteBranch, canRestoreBranch));
    }

    /**
     * {@code userName}과 {@code projectName}에 해당하는 프로젝트로 들어온
     * {@code pullRequestId}에 해당하는 코드 요청의 상태를 반환한다
     *
     * @param userName
     * @param projectName
     * @param pullRequestNumber
     * @return
     */
    @ProjectAccess(Operation.READ)
    @PullRequestAccess(Operation.READ)
    public static Result pullRequestState(String userName, String projectName, long pullRequestNumber) throws IOException {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        PullRequest pullRequest = PullRequest.findOne(project, pullRequestNumber);

        boolean canDeleteBranch = false;
        boolean canRestoreBranch = false;

        if (pullRequest.isClosed()) {
            canDeleteBranch = GitRepository.canDeleteFromBranch(pullRequest);
            canRestoreBranch = GitRepository.canRestoreBranch(pullRequest);
        }

        if(HttpUtil.isRequestedWithXHR(request()) && !HttpUtil.isPJAXRequest(request())){
            ObjectNode requestState = Json.newObject();
            requestState.put("id", pullRequestNumber);
            requestState.put("isOpen",     pullRequest.isOpen());
            requestState.put("isClosed",   pullRequest.isClosed());
            requestState.put("isRejected", pullRequest.isRejected());
            requestState.put("isMerging",  pullRequest.isMerging);
            requestState.put("isConflict", pullRequest.isConflict);
            requestState.put("canDeleteBranch", canDeleteBranch);
            requestState.put("canRestoreBranch", canRestoreBranch);
            requestState.put("html", partial_state.render(project, pullRequest, canDeleteBranch, canRestoreBranch).toString());
            return ok(requestState);
        }
        return ok(partial_state.render(project, pullRequest, canDeleteBranch, canRestoreBranch));
    }

    /**
     * {@code userName}과 {@code projectName}에 해당하는 프로젝트로 들어온
     * {@code pullRequestId}에 해당하는 코드 요청의 커밋 목록을 조회한다.
     *
     * @param userName
     * @param projectName
     * @param pullRequestNumber
     * @return
     */
    @ProjectAccess(Operation.READ)
    @PullRequestAccess(Operation.READ)
    public static Result pullRequestCommits(String userName, String projectName, long pullRequestNumber) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        PullRequest pullRequest = PullRequest.findOne(project, pullRequestNumber);
        return ok(viewCommits.render(project, pullRequest));
    }

    /**
     * {@code userName}과 {@code projectName}에 해당하는 프로젝트로 들어온
     * {@code pullRequestId}에 해당하는 코드 요청의 변경내역을 조회한다.
     *
     * @param userName
     * @param projectName
     * @param pullRequestNumber
     * @return
     */
    @ProjectAccess(Operation.READ)
    @PullRequestAccess(Operation.READ)
    public static Result pullRequestChanges(String userName, String projectName, long pullRequestNumber) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        PullRequest pullRequest = PullRequest.findOne(project, pullRequestNumber);

        List<PullRequestComment> comments = new ArrayList<>();

        for (PullRequestComment comment : pullRequest.comments) {
            if (comment.hasValidCommitId()) {
                comments.add(comment);
            }
        }

        return ok(viewChanges.render(project, pullRequest, comments));
    }

    /**
     * {@code pullRequestId}에 해당하는 코드 요청을 수락한다.
     *
     * 보내는 브랜치의 코드를 받을 브랜치의 코드에 병합한다. 문제없이 병합되면 코드 요청을 닫힌 상태로 변경한다.
     *
     * @param userName
     * @param projectName
     * @param pullRequestNumber
     * @return
     */
    @Transactional
    @With(AnonymousCheckAction.class)
    @ProjectAccess(Operation.READ)
    @PullRequestAccess(Operation.ACCEPT)
    public static Result accept(String userName, String projectName, long pullRequestNumber) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        PullRequest pullRequest = PullRequest.findOne(project, pullRequestNumber);

        PullRequestEventMessage message = new PullRequestEventMessage(
                UserApp.currentUser(), request(), project, pullRequest.toBranch);

        Call call = routes.PullRequestApp.pullRequest(userName, projectName, pullRequestNumber);
        pullRequest.merge(message, call);

        return redirect(call);
    }

    private static void addNotification(PullRequest pullRequest, Call call, State from, State to) {
        NotificationEvent notiEvent = NotificationEvent.addPullRequestUpdate(call, request(), pullRequest, from, to);
        PullRequestEvent.addEvent(notiEvent, pullRequest);
    }

    /**
     * {@code pullRequestId}에 해당하는 코드 요청을 보류한다.
     *
     * 해당 코드 요청을 보류 상태로 변경한다.
     *
     * @param userName
     * @param projectName
     * @param pullRequestNumber
     * @return
     */
    @Transactional
    @With(AnonymousCheckAction.class)
    @ProjectAccess(Operation.READ)
    @PullRequestAccess(Operation.REJECT)
    public static Result reject(String userName, String projectName, Long pullRequestNumber) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        PullRequest pullRequest = PullRequest.findOne(project, pullRequestNumber);

        State beforeState = pullRequest.state;
        pullRequest.reject();

        Call call = routes.PullRequestApp.pullRequest(userName, projectName, pullRequestNumber);
        addNotification(pullRequest, call, beforeState, State.REJECTED);

        return redirect(call);
    }

    /**
     * 코드 보내기 상태를 닫힘으로 처리한다.
     *
     * @param userName
     * @param projectName
     * @param pullRequestNumber
     * @return
     */
    @Transactional
    @With(AnonymousCheckAction.class)
    @ProjectAccess(Operation.READ)
    @PullRequestAccess(Operation.CLOSE)
    public static Result close(String userName, String projectName, Long pullRequestNumber) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        PullRequest pullRequest = PullRequest.findOne(project, pullRequestNumber);

        State beforeState = pullRequest.state;
        pullRequest.close();

        Call call = routes.PullRequestApp.pullRequest(userName, projectName, pullRequestNumber);
        addNotification(pullRequest, call, beforeState, State.CLOSED);

        return redirect(call);
    }

    /**
     * {@code pullRequestId}에 해당하는 코드 요청을 다시 열어준다.
     *
     * when: 보류 또는 닫기 상태로 변경했던 코드 요청을 다시 Open 상태로 변경할 때 사용한다.
     *
     * @param userName
     * @param projectName
     * @param pullRequestNumber
     * @return
     */
    @Transactional
    @With(AnonymousCheckAction.class)
    @ProjectAccess(Operation.READ)
    @PullRequestAccess(Operation.CLOSE)
    public static Result open(String userName, String projectName, Long pullRequestNumber) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        PullRequest pullRequest = PullRequest.findOne(project, pullRequestNumber);

        if (pullRequest.isMerged() || pullRequest.isOpen()) {
            return badRequest(ErrorViews.BadRequest.render());
        }

        State beforeState = pullRequest.state;
        pullRequest.reopen();

        Call call = routes.PullRequestApp.pullRequest(userName, projectName, pullRequestNumber);
        addNotification(pullRequest, call, beforeState, State.OPEN);

        PullRequestEventMessage message = new PullRequestEventMessage(
                UserApp.currentUser(), request(), pullRequest.fromProject, pullRequest.fromBranch);
        Akka.system().actorOf(new Props(PullRequestEventActor.class)).tell(message, null);

        return redirect(call);
    }

    /**
     * 코드 요청 수정 폼으로 이동한다.
     *
     * @param userName
     * @param projectName
     * @param pullRequestNumber
     * @return
     * @throws IOException
     * @throws ServletException
     */
    @With(AnonymousCheckAction.class)
    @ProjectAccess(Operation.READ)
    @PullRequestAccess(Operation.UPDATE)
    public static Result editPullRequestForm(String userName, String projectName, Long pullRequestNumber) throws ServletException, IOException, GitAPIException {
        Project toProject = Project.findByOwnerAndProjectName(userName, projectName);
        PullRequest pullRequest = PullRequest.findOne(toProject, pullRequestNumber);
        Project fromProject = pullRequest.fromProject;

        Form<PullRequest> editForm = new Form<>(PullRequest.class).fill(pullRequest);
        List<GitBranch> fromBranches = new GitRepository(pullRequest.fromProject).getAllBranches();
        List<GitBranch> toBranches = new GitRepository(pullRequest.toProject).getAllBranches();

        Attachment.moveAll(UserApp.currentUser().asResource(), pullRequest.asResource());

        return ok(edit.render("title.editPullRequest", editForm, fromProject, fromBranches, toBranches, pullRequest));
    }

    /**
     * 코드 요청을 수정한다.
     *
     * fromBranch와 toBranch를 변경했다면 기존에 동일한 브랜치로 코드를 주고 받는 열려있는 코드 요청이 있을 때 중복 에러를 던진다.
     * 중복 에러가 발생하면 flash로 에러 메시지를 보여주고 수정 폼으로 이동한다.
     *
     * @param userName
     * @param projectName
     * @param pullRequestNumber
     * @return
     */
    @Transactional
    @With(AnonymousCheckAction.class)
    @ProjectAccess(Operation.READ)
    @PullRequestAccess(Operation.UPDATE)
    public static Result editPullRequest(String userName, String projectName, Long pullRequestNumber) {
        Project toProject = Project.findByOwnerAndProjectName(userName, projectName);
        PullRequest pullRequest = PullRequest.findOne(toProject, pullRequestNumber);
        Project fromProject = pullRequest.fromProject;

        Form<PullRequest> pullRequestForm = new Form<>(PullRequest.class).bindFromRequest();
        PullRequest updatedPullRequest = pullRequestForm.get();
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

        return redirect(routes.PullRequestApp.pullRequest(toProject.owner, toProject.name, pullRequest.number));
    }

    /**
     * {@code pullRequestNumber}에 해당하는 코드 보내기 요청의 {@link PullRequest#fromBranch} 브랜치를 삭제한다.
     *
     * @param userName
     * @param projectName
     * @param pullRequestNumber
     * @return
     */
    @Transactional
    @With(AnonymousCheckAction.class)
    @ProjectAccess(Operation.READ)
    @PullRequestAccess(Operation.UPDATE)
    public static Result deleteFromBranch(String userName, String projectName, Long pullRequestNumber) {
        Project toProject = Project.findByOwnerAndProjectName(userName, projectName);
        PullRequest pullRequest = PullRequest.findOne(toProject, pullRequestNumber);

        pullRequest.deleteFromBranch();

        return redirect(routes.PullRequestApp.pullRequest(toProject.owner, toProject.name, pullRequestNumber));
    }

    /**
     * {@code pullRequestNumber}에 해당한느 코드 보내기 요청의 {@link PullRequest#fromBranch} 브랜치를 복구한다.
     *
     * @param userName
     * @param projectName
     * @param pullRequestNumber
     * @return
     */
    @Transactional
    @With(AnonymousCheckAction.class)
    @ProjectAccess(Operation.READ)
    @PullRequestAccess(Operation.UPDATE)
    public static Result restoreFromBranch(String userName, String projectName, Long pullRequestNumber) {
        Project toProject = Project.findByOwnerAndProjectName(userName, projectName);
        PullRequest pullRequest = PullRequest.findOne(toProject, pullRequestNumber);

        pullRequest.restoreFromBranch();

        return redirect(routes.PullRequestApp.pullRequest(toProject.owner, toProject.name, pullRequestNumber));
    }

    /**
     * {@code pullRequestId}에 해당하는 코드 보내기 요청의 {@code commitId}의 Diff를 보여준다.
     *
     * @param userName
     * @param projectName
     * @param pullRequestNumber
     * @param commitId
     * @return
     * @throws IOException
     * @throws ServletException
     * @throws GitAPIException
     * @throws SVNException
     */
    @Transactional
    @ProjectAccess(Operation.READ)
    public static Result commitView(String userName, String projectName, Long pullRequestNumber, String commitId) throws GitAPIException, SVNException, IOException, ServletException {
        Project toProject = Project.findByOwnerAndProjectName(userName, projectName);
        PullRequest pullRequest = PullRequest.findOne(toProject, pullRequestNumber);

        Project project = pullRequest.fromProject;

        List<FileDiff> fileDiffs = RepositoryService.getRepository(project).getDiff(commitId);
        Commit commit = RepositoryService.getRepository(project).getCommit(commitId);
        Commit parentCommit = RepositoryService.getRepository(project).getParentCommitOf(commitId);

        if (fileDiffs == null) {
            return notFound(ErrorViews.NotFound.render("error.notfound", project));
        }

        List<CommitComment> comments = CommitComment.find.where().eq("commitId",
                commitId).eq("project.id", project.id).order("createdDate").findList();

        return ok(diff.render(pullRequest, commit, parentCommit, fileDiffs, comments));
    }


    /**
     * {@link PullRequest}를 만들어 보내기 전에 프로젝트와 현재 사용자의 권한을 검증한다.
     *
     * when: {@link PullRequest}를 생성하는 폼이나 폼 서브밋을 처리하기 전에 사용한다.
     *
     * 현재 사용자가 익명 사용자일 경우 로그인 페이지로 이동하는 303 응답을 반환한다.
     * 프로젝트가 null이면 400(bad request) 응답을 반환한다.
     * 프로젝트가 Fork 프로젝트가 아닐 경우에도 400(bad request) 응답을 반환한다.
     * 사용자의 프로젝트 권한이 guest일 경우에도 400(bad request) 응답을 반환한다.
     * 이외의 경우에는 null을 반환한다.
     *
     * @param userName
     * @param projectName
     * @return
     */
    private static ValidationResult validateBeforePullRequest(Project project) {
        Result result = null;
        boolean hasError = false;
        if(!project.isForkedFromOrigin()) {
            result = badRequest(ErrorViews.BadRequest.render("Only fork project is allowed this request", project));
            hasError = true;
        }

        // anonymous는 위에서 걸렀고, 남은건 manager, member, site-manager, guest인데 이중에서 guest만 다시 걸러낸다.
        if(isGuest(project, UserApp.currentUser())) {
            result = forbidden(ErrorViews.BadRequest.render("Guest is not allowed this request", project));
            hasError = true;
        }

        return new ValidationResult(result, hasError);
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

    private static boolean isGuest(Project project, User currentUser) {
        return ProjectUser.roleOf(currentUser.loginId, project).equals("guest");
    }
}
