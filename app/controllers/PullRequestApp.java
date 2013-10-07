package controllers;

import com.avaje.ebean.Page;
import models.*;
import models.PullRequestCommit;
import models.enumeration.*;

import org.apache.commons.collections.IteratorUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.ObjectNode;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.tmatesoft.svn.core.SVNException;

import actors.PullRequestEventActor;
import akka.actor.Props;
import play.api.mvc.Call;
import play.data.Form;
import play.db.ebean.Transactional;
import play.i18n.Messages;
import play.libs.Akka;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import playRepository.*;
import utils.*;
import views.html.git.*;

import javax.servlet.ServletException;
import java.io.IOException;
import java.lang.reflect.Array;
import java.text.MessageFormat;
import java.util.*;

/**
 * 프로젝트 복사(포크)와 코드 주고받기(풀리퀘) 기능을 다루는 컨트롤러
 */
public class PullRequestApp extends Controller {

    private static final String NEW_LINE_DELIMETER = "\n";

    /**
     * {@code userName}과 {@code projectName}에 해당하는 프로젝트를
     * 복사하여 현재 접속한 사용자의 새 프로젝트로 생성할 수 있다는 안내 메시지와 안내 정보를 보여준다.
     *
     * @param userName
     * @param projectName
     * @return
     */
    public static Result newFork(String userName, String projectName) {
        Project project = ProjectApp.getProject(userName, projectName);
        if(project == null ) {
            return badRequestForNullProject(userName, projectName);
        }

        User currentUser = UserApp.currentUser();
        if(!AccessControl.isProjectResourceCreatable(currentUser, project, ResourceType.FORK)) {
            return forbidden(ErrorViews.Forbidden.render("error.forbidden", project));
        }

        Project forkedProject = Project.findByOwnerAndOriginalProject(currentUser.loginId, project);
        if(forkedProject != null) {
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
    public static Result fork(String userName, String projectName) {
        Project originalProject = Project.findByOwnerAndProjectName(userName, projectName);
        if(originalProject == null) {
            return badRequestForNullProject(userName, projectName);
        }

        User currentUser = UserApp.currentUser();
        if(!AccessControl.isProjectResourceCreatable(currentUser, originalProject, ResourceType.FORK)) {
            return forbidden(ErrorViews.Forbidden.render("error.forbidden", originalProject));
        }

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
    public static Result newPullRequestForm(String userName, String projectName) throws IOException, ServletException {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);

        Result result = validateBeforePullRequest(project, userName, projectName);
        if(result != null) {
            return result;
        }

        List<String> fromBranches = RepositoryService.getRepository(project).getBranches();
        List<String> toBranches = RepositoryService.getRepository(project.originalProject).getBranches();
    
        PullRequest pullRequest = new PullRequest();
        pullRequest.toProject = project.originalProject;
        pullRequest.fromProject = project;
        pullRequest.fromBranch = request().getQueryString("fromBranch");
        pullRequest.toBranch = request().getQueryString("toBranch");
        //TODO cloneAndFetch시 브랜치명에 id를 사용하고 있어 임의로 추가   
        pullRequest.id = Long.MAX_VALUE;    
        
        PullRequestMergeResult mergeResult = null;
        
        if (!StringUtils.isEmpty(pullRequest.fromBranch) && !StringUtils.isEmpty(pullRequest.toBranch)) {
            mergeResult = pullRequest.attemptMerge();
            Map<String, String> suggestText = suggestTitleAndBodyFromDiffCommit(mergeResult.getGitCommits());
            pullRequest.title = suggestText.get("title");
            pullRequest.body = suggestText.get("body");
        } 
    
        String xRequested = request().getHeader("X-Requested-With");
        
        if (!StringUtils.isEmpty(xRequested)) {
            response().setHeader("Cache-Control", "no-cache, no-store");
            return ok(partial_diff.render(new Form<>(PullRequest.class).fill(pullRequest), project, mergeResult, pullRequest));
        } 
        
        return ok(create.render("title.newPullRequest", new Form<>(PullRequest.class).fill(pullRequest), project, fromBranches, toBranches, mergeResult, pullRequest));
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
    public static Result newPullRequest(String userName, String projectName) throws IOException, ServletException {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);

        Result result = validateBeforePullRequest(project, userName, projectName);
        if(result != null) {
            return result;
        }

        Form<PullRequest> form = new Form<>(PullRequest.class).bindFromRequest();
        validateForm(form);
        if(form.hasErrors()) {
            List<String> fromBranches = RepositoryService.getRepository(project).getBranches();
            List<String> toBranches = RepositoryService.getRepository(project.originalProject).getBranches();
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

        Attachment.moveAll(UserApp.currentUser().asResource(), pullRequest.asResource());

        Call pullRequestCall = routes.PullRequestApp.pullRequest(originalProject.owner, originalProject.name, pullRequest.number);
        
        NotificationEvent notiEvent = NotificationEvent.addNewPullRequest(pullRequestCall, request(), pullRequest);
        PullRequestEvent.addEvent(notiEvent, pullRequest);
 
        PullRequestEventMessage message = new PullRequestEventMessage(
                UserApp.currentUser(), request(), pullRequest.toProject, pullRequest.toBranch);
        Akka.system().actorOf(new Props(PullRequestEventActor.class)).tell(message, null);
        
        return redirect(pullRequestCall);
    }
    
    /**
     * diff commit message로 pull request의 title과 body를 채운다.<br>
     * <br>
     * case 1 : commit이 한개이고 message가 한줄일 경우 title에 추가한다.<br>
     * case 2 : commit이 한개이고 message가 여러줄일 경우 첫번째줄 mesage는 title 나머지 message는 body에 추가<br>
     * case 3 : commit이 여러개일 경우 각 commit의 첫번째줄 message들을 모아서 body에 추가 <br>
     * 
     * @param commits
     * @return
     */
    private static Map<String, String> suggestTitleAndBodyFromDiffCommit(List<GitCommit> commits) {
        Map<String, String> messageMap = new HashMap<>();

        String message = StringUtils.EMPTY;

        if (commits.isEmpty()) {
            return messageMap;
            
        } else if (commits.size() == 1) {
            message = commits.get(0).getMessage();            
            String[] messages = message.split(NEW_LINE_DELIMETER);

            if (messages.length > 1) {
                String[] msgs = Arrays.copyOfRange(messages, 1, messages.length);
                messageMap.put("title", messages[0]);
                messageMap.put("body", StringUtils.join(msgs, NEW_LINE_DELIMETER));
                         
            } else {
                messageMap.put("title", messages[0]);
                messageMap.put("body", StringUtils.EMPTY);
            }
            
        } else {
            String[] firstMessages = new String[commits.size()];
            for (int i = 0; i < commits.size(); i++) {
                String[] messages = commits.get(i).getMessage().split(NEW_LINE_DELIMETER);
                firstMessages[i] = messages[0];
            }
            messageMap.put("body", StringUtils.join(firstMessages, NEW_LINE_DELIMETER));
            
        }

        return messageMap;
    }
    
    
    private static void validateForm(Form<PullRequest> form) {
        Map<String, String> data = form.data();
        ValidationUtils.rejectIfEmpty(flash(), data.get("fromBranch"), "pullRequest.fromBranch.required");
        ValidationUtils.rejectIfEmpty(flash(), data.get("toBranch"), "pullRequest.toBranch.required");
        ValidationUtils.rejectIfEmpty(flash(), data.get("title"), "pullRequest.title.required");
        ValidationUtils.rejectIfEmpty(flash(), data.get("body"), "pullRequest.body.required");
    }

    /**
     * 열려 있는 상태의 확인할 코드 요청을 조회한다.
     *
     * @param userName
     * @param projectName
     * @return
     */
    public static Result pullRequests(String userName, String projectName, int pageNum) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        if(project == null) {
            return badRequestForNullProject(userName, projectName);
        }
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
    public static Result closedPullRequests(String userName, String projectName, int pageNum) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        if(project == null) {
            return badRequestForNullProject(userName, projectName);
        }
        Page<PullRequest> page = PullRequest.findPagingList(State.CLOSED, project, pageNum - 1);
        return ok(list.render(project, page, "closed"));
    }

    /**
     * 보류 상태의 코드 요청을 조회한다.
     *
     * @param userName
     * @param projectName
     * @return
     */
    public static Result rejectedPullRequests(String userName, String projectName, int pageNum) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        if(project == null) {
            return badRequestForNullProject(userName, projectName);
        }
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
    public static Result sentPullRequests(String userName, String projectName, int pageNum) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        if(project == null) {
            return badRequestForNullProject(userName, projectName);
        }
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
    public static Result pullRequest(String userName, String projectName, long pullRequestNumber) throws IOException {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        PullRequest pullRequest = PullRequest.findOne(project, pullRequestNumber);

        Result result = validatePullRequest(project, pullRequest, userName, projectName, pullRequestNumber);
        if (result != null) {
            return result;
        }

        String activeTab = request().getQueryString("activeTab");
        if(activeTab == null && !isValid(activeTab)) {
            activeTab = "info";
        }
        
        boolean canDeleteBranch = false;
        boolean canRestoreBranch = false;
        
        if (pullRequest.isClosed()) {
            canDeleteBranch = GitRepository.canDeleteFromBranch(pullRequest);
            canRestoreBranch = GitRepository.canRestoreBranch(pullRequest);
        }

        List<PullRequestComment> comments = PullRequestComment.findByResourceKey(pullRequest.getResourceKey());

        return ok(view.render(project, pullRequest, comments, canDeleteBranch, canRestoreBranch, activeTab));
    }

    private static boolean isValid(String activeTab) {
        List<String> validTabs = Arrays.asList("info", "commits", "changes");
        return validTabs.contains(activeTab);
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
    public static Result pullRequestCommits(String userName, String projectName, long pullRequestNumber) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        PullRequest pullRequest = PullRequest.findOne(project, pullRequestNumber);

        Result result = validatePullRequest(project, pullRequest, userName, projectName, pullRequestNumber);
        if(result != null) {
            return result;
        }

        List<GitCommit> commits = GitRepository.getPullingCommits(pullRequest);
        return ok(viewCommits.render(project, pullRequest, commits));
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
    public static Result accept(String userName, String projectName, long pullRequestNumber) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        PullRequest pullRequest = PullRequest.findOne(project, pullRequestNumber);

        Result result = validatePullRequestOperation(project, pullRequest, userName, projectName, pullRequestNumber, Operation.ACCEPT);
        if(result != null) {
            return result;
        }

        pullRequest.merge();
        
        Call call = routes.PullRequestApp.pullRequest(userName, projectName, pullRequestNumber);
        
        NotificationEvent notiEvent = NotificationEvent.addPullRequestUpdate(call, request(), pullRequest, State.OPEN, State.CLOSED);

        // merge이후 관련 pullRequest의 상태를 체크한다. 
        PullRequestEventMessage message = new PullRequestEventMessage(
                UserApp.currentUser(), request(), project, pullRequest.toBranch);
        PullRequest.changeStateToMergingRelatedPullRequests(message.getProject(), message.getBranch());
        Akka.system().actorOf(new Props(PullRequestEventActor.class)).tell(message, null);

        PullRequestEvent.addEvent(notiEvent, pullRequest);
                
        return redirect(call);
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
    public static Result reject(String userName, String projectName, Long pullRequestNumber) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        PullRequest pullRequest = PullRequest.findOne(project, pullRequestNumber);

        Result result = validatePullRequestOperation(project, pullRequest, userName, projectName, pullRequestNumber, Operation.REJECT);
        if(result != null) {
            return result;
        }

        pullRequest.reject();

        Call call = routes.PullRequestApp.pullRequest(userName, projectName, pullRequestNumber);
        NotificationEvent notiEvent = NotificationEvent.addPullRequestUpdate(call, request(), pullRequest, State.OPEN, State.REJECTED);

        PullRequestEvent.addEvent(notiEvent, pullRequest);
        
        return redirect(call);
    }

    /**
     * {@code pullRequestId}에 해당하는 코드 요청을 다시 열어준다.
     *
     * when: 보류 상태로 변경했던 코드 요청을 다시 Open 상태로 변경할 때 사용한다.
     *
     * @param userName
     * @param projectName
     * @param pullRequestNumber
     * @return
     */
    public static Result open(String userName, String projectName, Long pullRequestNumber) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        PullRequest pullRequest = PullRequest.findOne(project, pullRequestNumber);

        Result result = validatePullRequestOperation(project, pullRequest, userName, projectName, pullRequestNumber, Operation.REOPEN);
        if(result != null) {
            return result;
        }

        pullRequest.reopen();
        Call call = routes.PullRequestApp.pullRequest(userName, projectName, pullRequestNumber);
        NotificationEvent notiEvent = NotificationEvent.addPullRequestUpdate(call, request(), pullRequest, State.REJECTED, State.OPEN);
        
        PullRequestEvent.addEvent(notiEvent, pullRequest);
        return redirect(call);
    }

    /**
     * {@code pullRequestId}에 해당하는 코드 요청을 삭제한다.
     *
     * @param userName
     * @param projectName
     * @param pullRequestNumber
     * @return
     */
    public static Result cancel(String userName, String projectName, Long pullRequestNumber) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        PullRequest pullRequest = PullRequest.findOne(project, pullRequestNumber);

        User user = UserApp.currentUser();

        Result result = validatePullRequestOperation(project, pullRequest, userName, projectName, pullRequestNumber, Operation.DELETE);
        if(result != null) {
            return result;
        }

        // 게스트 중에서 코드 요청을 보낸 사용자는 취소 할 수 있다.
        if(isGuest(project, user)) {
            if(!user.equals(pullRequest.contributor)) {
                forbidden(ErrorViews.Forbidden.render("Only this project's member and manager and the pull_request's author are allowed.", project));
            }
        }

        pullRequest.delete();
        return redirect(routes.PullRequestApp.pullRequests(userName, projectName, 1));
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
    public static Result editPullRequestForm(String userName, String projectName, Long pullRequestNumber) throws IOException, ServletException {
        Project toProject = Project.findByOwnerAndProjectName(userName, projectName);
        PullRequest pullRequest = PullRequest.findOne(toProject, pullRequestNumber);
        Project fromProject = pullRequest.fromProject;

        User user = UserApp.currentUser();

        if(!AccessControl.isAllowed(user, pullRequest.asResource(), Operation.UPDATE)) {
            return forbidden(ErrorViews.Forbidden.render(Messages.get("error.forbidden"), fromProject));
        }

        Form<PullRequest> editForm = new Form<>(PullRequest.class).fill(pullRequest);
        List<String> fromBranches = RepositoryService.getRepository(pullRequest.fromProject).getBranches();
        List<String> toBranches = RepositoryService.getRepository(pullRequest.toProject).getBranches();

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
    public static Result editPullRequest(String userName, String projectName, Long pullRequestNumber) {
        Project toProject = Project.findByOwnerAndProjectName(userName, projectName);
        PullRequest pullRequest = PullRequest.findOne(toProject, pullRequestNumber);
        Project fromProject = pullRequest.fromProject;

        if(!AccessControl.isAllowed(UserApp.currentUser(), pullRequest.asResource(), Operation.UPDATE)) {
            return forbidden(ErrorViews.Forbidden.render(Messages.get("error.forbidden"), toProject));
        }

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
    public static Result deleteFromBranch(String userName, String projectName, Long pullRequestNumber) {
        Project toProject = Project.findByOwnerAndProjectName(userName, projectName);
        PullRequest pullRequest = PullRequest.findOne(toProject, pullRequestNumber);

        if(!AccessControl.isAllowed(UserApp.currentUser(), pullRequest.asResource(), Operation.UPDATE)) {
            return forbidden(ErrorViews.Forbidden.render(Messages.get("error.forbidden"), toProject));
        }

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
    public static Result restoreFromBranch(String userName, String projectName, Long pullRequestNumber) {
        Project toProject = Project.findByOwnerAndProjectName(userName, projectName);
        PullRequest pullRequest = PullRequest.findOne(toProject, pullRequestNumber);


        if(!AccessControl.isAllowed(UserApp.currentUser(), pullRequest.asResource(), Operation.UPDATE)) {
            return forbidden(ErrorViews.Forbidden.render(Messages.get("error.forbidden"), toProject));
        }

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
    public static Result commitView(String userName, String projectName, Long pullRequestNumber, String commitId) throws IOException, ServletException, GitAPIException, SVNException {
        Project toProject = Project.findByOwnerAndProjectName(userName, projectName);
        PullRequest pullRequest = PullRequest.findOne(toProject, pullRequestNumber);

        Project project = pullRequest.fromProject;

        if(project == null) {
            return notFound(("No project matches given parameters '" + userName + "' and project_name '" + projectName + "'"));
        }

        if (!AccessControl.isAllowed(UserApp.currentUser(), project.asResource(), Operation.READ)) {
            return forbidden(ErrorViews.Forbidden.render("error.forbidden", project));
        }

        String patch = RepositoryService.getRepository(project).getPatch(commitId);
        Commit commit = RepositoryService.getRepository(project).getCommit(commitId);
        Commit parentCommit = RepositoryService.getRepository(project).getParentCommitOf(commitId);

        if (patch == null) {
            return notFound(ErrorViews.NotFound.render("error.notfound", project, null));
        }

        List<CommitComment> comments = CommitComment.find.where().eq("commitId",
                commitId).eq("project.id", project.id).findList();

        return ok(diff.render(pullRequest, commit, parentCommit, patch, comments));
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
    private static Result validateBeforePullRequest(Project project, String userName, String projectName) {
        User currentUser = UserApp.currentUser();
        if(currentUser.isAnonymous()) {
            flash(Constants.WARNING, "user.login.alert");
            return redirect(routes.UserApp.loginForm());
        }

        if(project == null) {
            return notFound(("No project matches given parameters '" + userName + "' and project_name '" + projectName + "'"));
        }

        if(!project.isFork()) {
            return badRequest(ErrorViews.BadRequest.render("Only fork project is allowed this request", project));
        }

        // anonymous는 위에서 걸렀고, 남은건 manager, member, site-manager, guest인데 이중에서 guest만 다시 걸러낸다.
        if(isGuest(project, currentUser)) {
            return forbidden(ErrorViews.BadRequest.render("Guest is not allowed this request", project));
        }

        return null;
    }

    private static boolean isGuest(Project project, User currentUser) {
        return ProjectUser.roleOf(currentUser.loginId, project).equals("guest");
    }

    /**
     * {@code userName}과 {@code projectName}에 해당하는 프로젝트가 없을 경우에 보내는 응답
     *
     * @param userName
     * @param projectName
     * @return
     */
    private static Status badRequestForNullProject(String userName, String projectName) {
        return badRequest(ErrorViews.BadRequest.render("No project matches given parameters'" + userName + "' and project_name '" + projectName + "'"));
    }

    /**
     * {@code project}과 {@code pullRequest}가 null인지 확인한다.
     *
     * @param project
     * @param pullRequest
     * @param userName
     * @param projectName
     * @param pullRequestNumber
     * @return
     */
    private static Result validatePullRequest(Project project, PullRequest pullRequest,
                                              String userName, String projectName, long pullRequestNumber) {
        if(project == null) {
            return notFound(("No project matches given parameters '" + userName + "' and project_name '" + projectName + "'"));
        }

        if(pullRequest == null) {
            return notFound(ErrorViews.NotFound.render("No pullrequest matches given parameter '" + pullRequestNumber + "'", project));
        }

        Project toProject = pullRequest.toProject;
        if(!toProject.equals(project)) {
            return redirect(routes.PullRequestApp.pullRequest(toProject.owner, toProject.name, pullRequestNumber));
        }
        return null;
    }

    /**
     * {@code project}와 {@code pullRequest}가 null인지 확인하고
     * 현재 사용자가 {@code project}의 코드 요청을 처리 할 수 있는지 확인한다.
     *
     * @param project
     * @param pullRequest
     * @param userName
     * @param projectName
     * @param pullRequestNumber
     * @return
     */
    private static Result validatePullRequestOperation(Project project, PullRequest pullRequest,
                                                       String userName, String projectName, long pullRequestNumber, Operation operation) {
        User user = UserApp.currentUser();
        if(user.isAnonymous()) {
            flash(Constants.WARNING, "user.login.alert");
            return redirect(routes.UserApp.loginForm());
        }

        Result result = validatePullRequest(project, pullRequest, userName, projectName, pullRequestNumber);
        if(result != null) {
            return result;
        }

        if(!AccessControl.isAllowed(user, pullRequest.asResource(), operation)) {
            result = forbidden(ErrorViews.Forbidden.render(Messages.get("error.forbidden"), project));
        }

        return result;
    }


}
