package controllers;

import models.Project;
import models.ProjectUser;
import models.PullRequest;
import models.User;
import models.enumeration.ResourceType;
import models.enumeration.RoleType;
import models.enumeration.State;
import org.codehaus.jackson.node.ObjectNode;
import play.data.Form;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import playRepository.GitCommit;
import playRepository.GitRepository;
import playRepository.RepositoryService;
import utils.AccessControl;
import utils.Constants;
import utils.JodaDateUtil;
import views.html.git.*;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.List;

/**
 * 프로젝트 복사(포크)와 코드 주고받기(풀리퀘) 기능을 다루는 컨트롤러
 *
 * @author Keesun Baik
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
    public static Result newFork(String userName, String projectName) {
        Project project = ProjectApp.getProject(userName, projectName);
        if(project == null ) {
            return badRequestForNullProject(userName, projectName);
        }

        User currentUser = UserApp.currentUser();
        if(!AccessControl.isProjectResourceCreatable(currentUser, project, ResourceType.FORK)) {
            return forbidden();
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
            return forbidden();
        }

        // 이미 포크한 프로젝트가 있다면 그 프로젝트로 이동.
        Project forkedProject = Project.findByOwnerAndOriginalProject(currentUser.loginId, originalProject);
        if(forkedProject != null) {
            flash(Constants.WARNING, "already.existing.fork.alert");
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
            GitRepository.cloneRepository(originalProject, forkProject);
            Long projectId = Project.create(forkProject);
            ProjectUser.assignRole(currentUser.id, projectId, RoleType.MANAGER);
            result.put(status, "success");
            result.put(url, routes.ProjectApp.project(forkProject.owner, forkProject.name).url());
            return ok(result);
        } catch (Exception e) {
            result.put(status, failed);
            result.put(url, routes.PullRequestApp.pullRequests(originalProject.owner, originalProject.name).url());
            return ok(result);
        }
    }

    /**
     * {@code userName}과 {@code projectName}에 해당하는 프로젝트의 원본 프로젝트로 코드를 보낼 수 있는 코드 보내기 폼을 보여준다.
     *
     * 코드 보내기 폼에서 보내려는 브랜치과 코드를 받을 브랜치를 선택할 수 있도록 브랜치 목록을 보여준다.
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

        return ok(create.render("title.newPullRequest", new Form<>(PullRequest.class), project, fromBranches, toBranches));
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
    public static Result newPullRequest(String userName, String projectName) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);

        Result result = validateBeforePullRequest(project, userName, projectName);
        if(result != null) {
            return result;
        }

        Form<PullRequest> form = new Form<>(PullRequest.class).bindFromRequest();
        if(form.hasErrors()) {
            return badRequest(form.errors().toString());
        }

        Project originalProject = project.originalProject;

        PullRequest pullRequest = form.get();
        pullRequest.created = JodaDateUtil.now();
        pullRequest.contributor = UserApp.currentUser();
        pullRequest.fromProject = project;
        pullRequest.toProject = originalProject;

        // 동일한 브랜치로 주고 받으며, Open 상태의 PullRequest가 이미 존재한다면 해당 PullRequest로 이동한다.
        PullRequest sentRequest = PullRequest.findDuplicatedPullRequest(pullRequest);
        if(sentRequest != null) {
            return redirect(routes.PullRequestApp.pullRequest(originalProject.owner, originalProject.name, sentRequest.id));
        }

        pullRequest.save();

        return redirect(routes.PullRequestApp.pullRequest(originalProject.owner, originalProject.name, pullRequest.id));
    }

    /**
     * 열려 있는 상태의 확인할 코드 요청을 조회한다.
     *
     * @param userName
     * @param projectName
     * @return
     */
    public static Result pullRequests(String userName, String projectName) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        if(project == null) {
            return badRequestForNullProject(userName, projectName);
        }
        if(!project.vcs.equals("GIT")) {
            return badRequest("Now, only git project is allowed this request.");
        }
        List<PullRequest> pullRequests = PullRequest.findOpendPullRequests(project);
        return ok(list.render(project, pullRequests, "opened"));
    }

    /**
     * 받은 상태의 코드 요청을 조회한다.
     *
     * @param userName
     * @param projectName
     * @return
     */
    public static Result closedPullRequests(String userName, String projectName) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        if(project == null) {
            return badRequestForNullProject(userName, projectName);
        }
        List<PullRequest> pullRequests = PullRequest.findClosedPullRequests(project);
        return ok(list.render(project, pullRequests, "closed"));
    }

    /**
     * 보류 상태의 코드 요청을 조회한다.
     *
     * @param userName
     * @param projectName
     * @return
     */
    public static Result rejectedPullRequests(String userName, String projectName) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        if(project == null) {
            return badRequestForNullProject(userName, projectName);
        }
        List<PullRequest> pullRequests = PullRequest.findRejectedPullRequests(project);
        return ok(list.render(project, pullRequests, "rejected"));
    }

    /**
     * 보낸 코드 요청을 조회한다.
     *
     * @param userName
     * @param projectName
     * @return
     */
    public static Result sentPullRequests(String userName, String projectName) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        if(project == null) {
            return badRequestForNullProject(userName, projectName);
        }
        List<PullRequest> pullRequests = PullRequest.findSentPullRequests(project);
        return ok(list.render(project, pullRequests, "sent"));
    }

    /**
     * {@code userName}과 {@code projectName}에 해당하는 프로젝트로 들어온 {@code pullRequestId}에 해당하는 코드 요청을 조회한다.
     *
     * 해당 코드 요청이 열려 있는 상태일 경우에는 자동으로 merge해도 안전한지 확인한다.
     *
     * @param userName
     * @param projectName
     * @param pullRequestId
     * @return
     */
    public static Result pullRequest(String userName, String projectName, long pullRequestId) {
        PullRequest pullRequest = PullRequest.findById(pullRequestId);
        Project project = Project.findByOwnerAndProjectName(userName, projectName);

        Result result = validatePullRequest(project, pullRequest, userName, projectName, pullRequestId);
        if(result != null) {
            return result;
        }

        boolean isSafe = false;
        if(pullRequest.isOpen()) {
            isSafe = GitRepository.isSafeToMerge(pullRequest);
        }

        return ok(view.render(project, pullRequest, isSafe));
    }

    /**
     * {@code userName}과 {@code projectName}에 해당하는 프로젝트로 들어온
     * {@code pullRequestId}에 해당하는 코드 요청의 커밋 목록을 조회한다.
     *
     * @param userName
     * @param projectName
     * @param pullRequestId
     * @return
     */
    public static Result pullRequestCommits(String userName, String projectName, long pullRequestId) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        PullRequest pullRequest = PullRequest.findById(pullRequestId);

        Result result = validatePullRequest(project, pullRequest, userName, projectName, pullRequestId);
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
     * @param pullRequestId
     * @return
     */
    public static Result accept(String userName, String projectName, long pullRequestId) {
        PullRequest pullRequest = PullRequest.findById(pullRequestId);
        Project project = Project.findByOwnerAndProjectName(userName, projectName);

        Result result = validatePullRequestOperation(project, pullRequest, userName, projectName, pullRequestId);
        if(result != null) {
            return result;
        }

        GitRepository.merge(pullRequest);
        if(pullRequest.state == State.CLOSED) {
            pullRequest.received = JodaDateUtil.now();
            pullRequest.receiver = UserApp.currentUser();
            pullRequest.update();
        }
        return redirect(routes.PullRequestApp.pullRequest(userName, projectName, pullRequestId));
    }



    /**
     * {@code pullRequestId}에 해당하는 코드 요청을 보류한다.
     *
     * 해당 코드 요청을 보류 상태로 변경한다.
     *
     * @param userName
     * @param projectName
     * @param pullRequestId
     * @return
     */
    public static Result reject(String userName, String projectName, Long pullRequestId) {
        PullRequest pullRequest = PullRequest.findById(pullRequestId);
        Project project = Project.findByOwnerAndProjectName(userName, projectName);

        Result result = validatePullRequestOperation(project, pullRequest, userName, projectName, pullRequestId);
        if(result != null) {
            return result;
        }

        pullRequest.state = State.REJECTED;
        pullRequest.received = JodaDateUtil.now();
        pullRequest.receiver = UserApp.currentUser();
        pullRequest.update();
        return redirect(routes.PullRequestApp.pullRequest(userName, projectName, pullRequestId));
    }

    /**
     * {@code pullRequestId}에 해당하는 코드 요청을 다시 열어준다.
     *
     * when: 보류 상태로 변경했던 코드 요청을 다시 Open 상태로 변경할 때 사용한다.
     *
     * @param userName
     * @param projectName
     * @param pullRequestId
     * @return
     */
    public static Result open(String userName, String projectName, Long pullRequestId) {
        PullRequest pullRequest = PullRequest.findById(pullRequestId);
        Project project = Project.findByOwnerAndProjectName(userName, projectName);

        Result result = validatePullRequestOperation(project, pullRequest, userName, projectName, pullRequestId);
        if(result != null) {
            return result;
        }

        pullRequest.state = State.OPEN;
        pullRequest.received = JodaDateUtil.now();
        pullRequest.receiver = UserApp.currentUser();
        pullRequest.update();
        return redirect(routes.PullRequestApp.pullRequest(userName, projectName, pullRequestId));
    }

    /**
     * {@code pullRequestId}에 해당하는 코드 요청을 삭제한다.
     *
     * @param userName
     * @param projectName
     * @param pullRequestId
     * @return
     */
    public static Result cancel(String userName, String projectName, Long pullRequestId) {
        PullRequest pullRequest = PullRequest.findById(pullRequestId);
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        User user = UserApp.currentUser();

        Result result = validatePullRequest(project, pullRequest, userName, projectName, pullRequestId);
        if(result != null) {
            return result;
        }

        // 게스트 중에서 코드 요청을 보낸 사용자는 취소 할 수 있다.
        if(isGuest(project, user)) {
            if(!user.equals(pullRequest.contributor)) {
                forbidden("Only this project's member and manager and the pull_request's author are allowed.");
            }
        }

        pullRequest.delete();
        return redirect(routes.PullRequestApp.pullRequests(userName, projectName));
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

        if(project == null ) {
            return badRequestForNullProject(userName, projectName);
        }
        if(!project.isFork()) {
            return badRequest("Only fork project is allowed this request");
        }

        // anonymous는 위에서 걸렀고, 남은건 manager, member, site-manager, guest인데 이중에서 guest만 다시 걸러낸다.
        if(isGuest(project, currentUser)) {
            return badRequest("Guest is not allowed this request");
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
        return badRequest("No project matches given parameters'" + userName + "' and project_name '" + projectName + "'");
    }

    /**
     * {@code project}과 {@code pullRequest}가 null인지 확인한다.
     *
     * @param project
     * @param pullRequest
     * @param userName
     * @param projectName
     * @param pullRequestId
     * @return
     */
    private static Result validatePullRequest(Project project, PullRequest pullRequest,
                                              String userName, String projectName, long pullRequestId) {
        if(project == null) {
            return badRequestForNullProject(userName, projectName);
        }

        if(pullRequest == null) {
            return badRequest("No pull_request matches given pull_request_id '" + pullRequestId + "'");
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
     * @param pullRequestId
     * @return
     */
    private static Result validatePullRequestOperation(Project project, PullRequest pullRequest,
                                                       String userName, String projectName, long pullRequestId) {
        User user = UserApp.currentUser();
        if(user.isAnonymous()) {
            flash(Constants.WARNING, "user.login.alert");
            return redirect(routes.UserApp.loginForm());
        }

        Result result = validatePullRequest(project, pullRequest, userName, projectName, pullRequestId);

        if(isGuest(project, user)) {
            result = forbidden("Guest is not allowed this request");
        }

        return result;
    }

}