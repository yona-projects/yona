package controllers;

import com.avaje.ebean.Page;
import com.avaje.ebean.ExpressionList;
import models.*;
import models.enumeration.*;
import models.support.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.tmatesoft.svn.core.SVNException;
import play.data.Form;
import play.db.ebean.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Http.MultipartFormData;
import play.mvc.Http.MultipartFormData.FilePart;
import play.mvc.Result;
import playRepository.Commit;
import playRepository.PlayRepository;
import playRepository.RepositoryService;
import utils.AccessControl;
import utils.Constants;
import utils.HttpUtil;
import views.html.project.*;
import play.i18n.Messages;

import javax.servlet.ServletException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import static play.data.Form.form;
import static play.libs.Json.toJson;
import static com.avaje.ebean.Expr.contains;

/**
 * ProjectApp
 *
 */
public class ProjectApp extends Controller {

	private static final int LOGO_FILE_LIMIT_SIZE = 1048576;

	/** 프로젝트 로고로 사용할 수 있는 이미지 확장자 */
    public static final String[] LOGO_TYPE = {"jpg", "jpeg", "png", "gif", "bmp"};

    /** 자동완성에서 보여줄 최대 프로젝트 개수 */
    private static final int MAX_FETCH_PROJECTS = 1000;

	private static final int COMMIT_HISTORY_PAGE = 1;

	private static final int COMMIT_HISTORY_SHOW_LIMIT = 5;

	private static final int RECENLTY_ISSUE_SHOW_LIMIT = 5;

	private static final int RECENLTY_POSTING_SHOW_LIMIT = 5;

    private static final int PROJECT_COUNT_PER_PAGE = 10;    
    
    private static final String HTML = "text/html";
    
    private static final String JSON = "application/json";
    

    
    /**
     * getProject
     * @param userName
     * @param projectName
     * @return
     */
    public static Project getProject(String userName, String projectName) {
        return Project.findByOwnerAndProjectName(userName, projectName);
    }

    /**
     * 프로젝트 Overview 페이지를 처리한다.<p />
     *
     * {@code loginId}와 {@code projectName}으로 프로젝트 정보를 가져온다.<br />
     * 읽기 권한이 없을 경우는 unauthorized로 응답한다.<br />
     * 해당 프로젝트의 최근 커밋, 이슈, 포스팅 목록을 가져와서 히스토리를 만든다.<br />
     *
     * @param loginId
     * @param projectName
     * @return 프로젝트, 히스토리 정보
     * @throws IOException Signals that an I/O exception has occurred.
     * @throws ServletException the servlet exception
     * @throws SVNException the svn exception
     * @throws GitAPIException the git api exception
     */
    public static Result project(String loginId, String projectName) throws IOException, ServletException, SVNException, GitAPIException {
        Project project = Project.findByOwnerAndProjectName(loginId, projectName);

        if (!AccessControl.isAllowed(UserApp.currentUser(), project.asResource(), Operation.READ)) {
            return unauthorized(views.html.error.unauthorized.render(project));
        }

        PlayRepository repository = RepositoryService.getRepository(project);

        List<Commit> commits = null;
        try {
			commits = repository.getHistory(COMMIT_HISTORY_PAGE, COMMIT_HISTORY_SHOW_LIMIT, null);
        } catch (NoHeadException e) {
		// NOOP
        }

        List<Issue> issues = Issue.findRecentlyCreated(project, RECENLTY_ISSUE_SHOW_LIMIT);
        List<Posting> postings = Posting.findRecentlyUpdated(project, RECENLTY_POSTING_SHOW_LIMIT);

        List<History> histories = History.makeHistory(loginId, project, commits, issues, postings);

        return ok(overview.render("title.projectHome", project, histories));
    }

	/**
     * 신규 프로젝트 생성 페이지로 이동한다.<p />
     *
     * 비로그인 상태({@link controllers.UserApp#anonymous})이면 로그인 경고메세지와 함께 로그인페이지로 redirect 된다.<br />
     * 로그인 상태이면 프로젝트 생성 페이지로 이동한다.<br />
     *
     * @return 익명사용자이면 로그인페이지, 로그인 상태이면 프로젝트 생성페이지
     */
    public static Result newProjectForm() {
        if (UserApp.currentUser().isAnonymous()) {
            flash(Constants.WARNING, "user.login.alert");
            return redirect(routes.UserApp.loginForm());
        } else {
            return ok(create.render("title.newProject", form(Project.class)));
        }
    }

    /**
     * 프로젝트 설정(업데이트) 페이지로 이동한다.<p />
     *
     * {@code loginId}와 {@code projectName}으로 프로젝트 정보를 가져온다.<br />
     * 업데이트 권한이 없을 경우는 unauthorized로 응답한다.<br />
     *
     * @param loginId
     * @param projectName
     * @return 프로젝트 정보
     */
    public static Result settingForm(String loginId, String projectName) {
        Project project = Project.findByOwnerAndProjectName(loginId, projectName);

        if (!AccessControl.isAllowed(UserApp.currentUser(), project.asResource(), Operation.UPDATE)) {
            return unauthorized(views.html.error.unauthorized.render(project));
        }

        Form<Project> projectForm = form(Project.class).fill(project);
        return ok(setting.render("title.projectSetting", projectForm, project));
    }

    /**
     * 신규 프로젝트 생성(관리자Role 설정/코드 저장소를 생성)하고 Overview 페이지로 redirect 된다.<p />
     *
     * {@code loginId}와 {@code projectName}으로 프로젝트 정보를 조회하여 <br />
     * 프로젝트가 이미 존재할 경우 경고메세지와 함께 badRequest로 응답한다.<br />
     * 프로젝트폼 입력데이터에 오류가 있을 경우 경고메시지와 함께 badRequest로 응답한다.<br />
     *
     * @return 프로젝트 존재시 경고메세지, 입력폼 오류시 경고메세지, 프로젝트 생성시 프로젝트 정보
     * @throws Exception
     */
    @Transactional
    public static Result newProject() throws Exception {
        if( !AccessControl.isCreatable(UserApp.currentUser(), ResourceType.PROJECT) ){
           return forbidden("'" + UserApp.currentUser().name + "' has no permission");
        }
        Form<Project> filledNewProjectForm = form(Project.class).bindFromRequest();

        if (Project.exists(UserApp.currentUser().loginId, filledNewProjectForm.field("name").value())) {
            flash(Constants.WARNING, "project.name.duplicate");
            filledNewProjectForm.reject("name");
            return badRequest(create.render("title.newProject", filledNewProjectForm));
        } else if (filledNewProjectForm.hasErrors()) {
            filledNewProjectForm.reject("name");
            flash(Constants.WARNING, "project.name.alert");
            return badRequest(create.render("title.newProject", filledNewProjectForm));
        } else {
            Project project = filledNewProjectForm.get();
            project.owner = UserApp.currentUser().loginId;
            ProjectUser.assignRole(UserApp.currentUser().id, Project.create(project), RoleType.MANAGER);

            RepositoryService.createRepository(project);

            return redirect(routes.ProjectApp.project(project.owner, project.name));
        }
    }

    /**
     * 프로젝트 설정을 업데이트한다.<p />
     *
     * 업데이트 권한이 없을 경우 경고메세지와 함께 프로젝트 설정페이지로 redirect된다.<br />
     * {@code loginId}의 프로젝트중 변경하고자 하는 이름과 동일한 프로젝트명이 있으면 경고메세지와 함께 badRequest를 응답한다.<br />
     * 프로젝트 로고({@code filePart})가 이미지파일이 아니거나 제한사이즈(1MB) 보다 크다면 경고메세지와 함께 badRequest를 응답한다.<br />
     * 프로젝트 로고({@code filePart})가 이미지파일이고 제한사이즈(1MB) 보다 크지 않다면 첨부파일과 프로젝트 정보를 저장하고 프로젝트 설정(업데이트) 페이지로 이동한다.<br />
     *
     * @param loginId user login id
     * @param projectName the project name
     * @return
     * @throws IOException Signals that an I/O exception has occurred.
     * @throws NoSuchAlgorithmException the no such algorithm exception
     */
    @Transactional
    public static Result settingProject(String loginId, String projectName) throws IOException, NoSuchAlgorithmException {
        Form<Project> filledUpdatedProjectForm = form(Project.class).bindFromRequest();
        Project project = filledUpdatedProjectForm.get();

        if (!AccessControl.isAllowed(UserApp.currentUser(), project.asResource(), Operation.UPDATE)) {
            flash(Constants.WARNING, "project.member.isManager");
            return redirect(routes.ProjectApp.settingForm(loginId, project.name));
        }

        if (!Project.projectNameChangeable(project.id, loginId, project.name)) {
            flash(Constants.WARNING, "project.name.duplicate");
            filledUpdatedProjectForm.reject("name");
        }

        MultipartFormData body = request().body().asMultipartFormData();
        FilePart filePart = body.getFile("logoPath");

        if (!isEmptyFilePart(filePart)) {
            if(!isImageFile(filePart.getFilename())) {
                flash(Constants.WARNING, "project.logo.alert");
                filledUpdatedProjectForm.reject("logoPath");
            } else if (filePart.getFile().length() > LOGO_FILE_LIMIT_SIZE) {
                flash(Constants.WARNING, "project.logo.fileSizeAlert");
                filledUpdatedProjectForm.reject("logoPath");
            } else {
                Attachment.deleteAll(project.asResource());
                new Attachment().store(filePart.getFile(), filePart.getFilename(), project.asResource());
            }
        }

        if (filledUpdatedProjectForm.hasErrors()) {
            return badRequest(setting.render("title.projectSetting",
                    filledUpdatedProjectForm, Project.find.byId(project.id)));
        }

        project.update();
        return redirect(routes.ProjectApp.settingForm(loginId, project.name));
    }

    /**
     * {@code filePart} 정보가 비어있는지 확인한다.<p />
     * @param filePart
     * @return {@code filePart}가 null이면 true, {@code filename}이 null이면 true, {@code fileLength}가 0 이하이면 true
     */
	private static boolean isEmptyFilePart(FilePart filePart) {
		return filePart == null || filePart.getFilename() == null || filePart.getFilename().length() <= 0;
	}

    /**
     * {@code filename}의 확장자를 체크하여 이미지인지 확인한다.<p />
     *
     * 이미지 확장자는 {@link controllers.ProjectApp#LOGO_TYPE} 에 정의한다.
     * @param filename the filename
     * @return true, if is image file
     */
    public static boolean isImageFile(String filename) {
        boolean isImageFile = false;
        for(String suffix : LOGO_TYPE) {
            if(filename.toLowerCase().endsWith(suffix))
                isImageFile = true;
        }
        return isImageFile;
    }

    /**
     * 프로젝트 삭제 페이지로 이동한다.<p />
     *
     * {@code loginId}와 {@code projectName}으로 프로젝트 정보를 가져온다.<br />
     * 업데이트 권한이 없을 경우는 unauthorized로 응답한다.<br />
     *
     * @param loginId user login id
     * @param projectName the project name
     * @return 프로젝트폼, 프로젝트 정보
     */
    public static Result deleteForm(String loginId, String projectName) {
        Project project = Project.findByOwnerAndProjectName(loginId, projectName);
        if (!AccessControl.isAllowed(UserApp.currentUser(), project.asResource(), Operation.UPDATE)) {
            return unauthorized(views.html.error.unauthorized.render(project));
        }

        Form<Project> projectForm = form(Project.class).fill(project);
        return ok(delete.render("title.projectSetting", projectForm, project));
    }

    /**
     * 프로젝트를 삭제한다.<p />
     *
	 * {@code loginId}와 {@code projectName}으로 프로젝트 정보를 가져온다.<br />
     * 삭제 권한이 없을 경우는 경고 메시지와 함께 설정페이지로 redirect된다. <br />
     *
     * @param loginId the user login id
     * @param projectName the project name
     * @return the result
     * @throws Exception the exception
     */
    public static Result deleteProject(String loginId, String projectName) throws Exception {
	Project project = Project.findByOwnerAndProjectName(loginId, projectName);

        if (AccessControl.isAllowed(UserApp.currentUser(), project.asResource(), Operation.DELETE)) {
            RepositoryService.deleteRepository(loginId, projectName, project.vcs);
            project.deleteFork();
            project.delete();
            return redirect(routes.Application.index());
        } else {
            flash(Constants.WARNING, "project.member.isManager");
            return redirect(routes.ProjectApp.settingForm(loginId, projectName));
        }
    }

    /**
     * 프로젝트 멤버설정 페이지로 이동한다.<p />
     *
     * {@code loginId}와 {@code projectName}으로 프로젝트 정보를 가져온다.<br />
     * 프로젝트 아이디로 해당 프로젝트의 멤버목록을 가져온다.<br />
     * 프로젝트 관련 Role 목록을 가져온다.<br />
     *
     * @param loginId the user login id
     * @param projectName the project name
     * @return 프로젝트, 멤버목록, Role 목록
     */
    public static Result members(String loginId, String projectName) {
	Project project = Project.findByOwnerAndProjectName(loginId, projectName);
        return ok(views.html.project.members.render("title.memberList",
                ProjectUser.findMemberListByProject(project.id), project,
                Role.getActiveRoles()));
    }

    /**
     * 프로젝트의 신규 멤버를 추가한다.<p />
     *
     * 입력폼 오류시 경고메세지와 함께 프로젝트 설정페이지로 redirect 된다.<br />
     * 입력폼으로부터 멤버로 추가한 사용자 정보를 가져온다.<br />
     * {@code loginId}와 {@code projectName}으로 프로젝트 정보를 가져온다.<br />
     * <br />
     * 현재 로그인 사용자가 UPDATE 권한이 없을경우 경고메세지와 함께 멤버설정 페이지로 redirect 된다.<br />
     * 추가한 사용자 정보가 null일 경우 경고메세지와 함께 멤버설정 페이지로 redirect 된다.<br />
     * 추가한 사용자가 이미 프로젝트 멤버일 경우 경고메시지와 함께 멤버설정 페이지로 redirect 된다.<br />
     *
     * @param loginId the user login id
     * @param projectName the project name
     * @return 프로젝트, 멤버목록, Role 목록
     */
    public static Result newMember(String loginId, String projectName) {
        // TODO change into view validation
        Form<User> addMemberForm = form(User.class).bindFromRequest();
        if (addMemberForm.hasErrors()){
            flash(Constants.WARNING, "project.member.notExist");
            return redirect(routes.ProjectApp.members(loginId, projectName));
        }

        User user = User.findByLoginId(form(User.class).bindFromRequest().get().loginId);
        Project project = Project.findByOwnerAndProjectName(loginId, projectName);

        if (!AccessControl.isAllowed(UserApp.currentUser(), project.asResource(), Operation.UPDATE)) {
            flash(Constants.WARNING, "project.member.isManager");
            return redirect(routes.ProjectApp.members(loginId, projectName));
        } else if (user == null) {
            flash(Constants.WARNING, "project.member.notExist");
            return redirect(routes.ProjectApp.members(loginId, projectName));
        } else if (!ProjectUser.isMember(user.id, project.id)){
            ProjectUser.assignRole(user.id, project.id, RoleType.MEMBER);
        } else{
            flash(Constants.WARNING, "project.member.alreadyMember");
        }
        return redirect(routes.ProjectApp.members(loginId, projectName));
    }

    /**
     * 프로젝트 멤버를 삭제한다.<p />
     *
     * {@code loginId}와 {@code projectName}으로 프로젝트 정보를 가져온다.<br />
     * 삭제할 멤버가 로그인 사용자 이거나 프로젝트 업데이트 권한이 있을 경우 삭제하고 멤버 설정페이지로 redirect 된다.<br />
     * 삭제할 멤버가 프로젝트 관리자일 경우 경고메세지와 함께 forbidden을 응답한다.<br />
     *
     * @param loginId the user login id
     * @param projectName the project name
     * @param userId 삭제할 멤버 아이디
     * @return the result
     */
    public static Result deleteMember(String loginId, String projectName, Long userId) {
        Project project = Project.findByOwnerAndProjectName(loginId, projectName);

        if (UserApp.currentUser().id == userId
                || AccessControl.isAllowed(UserApp.currentUser(), project.asResource(), Operation.UPDATE)) {
            if (project.isOwner(User.find.byId(userId))) {
                return forbidden(Messages.get("project.member.ownerCannotLeave"));
            }
            ProjectUser.delete(userId, project.id);
            return redirect(routes.ProjectApp.members(loginId, projectName));
        } else {
            return forbidden(views.html.error.forbidden.render(project));
        }
    }

    /**
     * 멤버의 Role을 설정하고 NO_CONTENT를 응답한다.<p />
     *
     * {@code loginId}와 {@code projectName}으로 프로젝트 정보를 가져온다.<br />
     * 로그인 사용자가 업데이트 권한이 있을경우 멤버에게 새로 설정한 Role을 할당한다.<br />
     * <br />
     * 변경하고자 하는 멤버가 프로젝트 관리자일 경우 경고메세지와 함께 forbidden을 응답한다.<br />
     * 업데이트 권한이 없을 경우 경고메세지와 함께 forbidden을 응답한다.<br />
     *
     * @param loginId the user login id
     * @param projectName the project name
     * @param userId the user id
     * @return
     */
    public static Result editMember(String loginId, String projectName, Long userId) {
        Project project = Project.findByOwnerAndProjectName(loginId, projectName);

        if (AccessControl.isAllowed(UserApp.currentUser(), project.asResource(), Operation.UPDATE)) {
            if (project.isOwner(User.find.byId(userId))) {
                return forbidden(Messages.get("project.member.ownerMustBeAManager"));
            }
            ProjectUser.assignRole(userId, project.id, form(Role.class)
                    .bindFromRequest().get().id);
            return status(Http.Status.NO_CONTENT);
        } else {
            return forbidden(Messages.get("project.member.isManager"));
        }
    }

    /**
     * accepted가능한 Content-type에 따라 JSON 목록을 반환하거나 프로젝트 목록 페이지로 이동한다.<p />
     * HTML 또는 JSON 요청이 아닌경우 NOT_ACCEPTABLE 을 응답한다.<br />
     * <br />
     * 1. JSON 목록 반환 ( 자동완성용 )<br />
     * 프로젝트 관리자 또는 이름에 {@code query}값를 포함하고 있는 프로젝트 목록을 가져온다.<br />
     * 반환되는 최대 목록개수는 {@link ProjectApp#MAX_FETCH_PROJECTS} 로 설정한다.<br />
     * 프로젝트 목록의 {@code owner}와 {@code name}을 조합하여 새로운 목록을 만들고 JSON 형태로 반환한다.<br />
     * <br />
     * 2. 프로젝트 목록 페이지 이동<br />
     * 프로젝트 목록을 최근 생성일 기준으로 정렬하여 페이지(사이즈 : {@link Project#PROJECT_COUNT_PER_PAGE}) 단위로 가져오고<br />
     * 조회 조건은 프로젝트명 또는 프로젝트관리자({@code query}), 공개 여부({@code state}) 이다.<br />
     *
     * @param query the query
     * @param state the state
     * @param pageNum the page num
     * @return json일 경우 json형태의 프로젝트명 목록, html일 경우 java객체 형태의 프로젝트 목록
     */
    public static Result projects(String query, String state, int pageNum) {

        String prefer = HttpUtil.getPreferType(request(), JSON, HTML);

        if (prefer == null) {
            return status(Http.Status.NOT_ACCEPTABLE);
        }

        if (prefer.equals(JSON)) {
            return getProjectsToJSON(query);        
        } else {
            return getPagingProjects(query, state, pageNum);
        }
    }

    /**
     * 프로젝트 목록을 가져온다.
     * 
     * when : 프로젝트명, 프로젝트 관리자, 공개여부로 프로젝트 목록 조회시 
     * 
     * 프로젝트명 또는 관리자 로그인 아이디가 {@code query}를 포함하고 
     * 공개여부가 @{code state} 인 프로젝트 목록을 최근생성일로 정렬하여 페이징 형태로 가져온다.
     * 
     * @param query 검색질의(프로젝트명 또는 관리자)
     * @param state 프로젝트 상태(공개/비공개)
     * @param pageNum 페이지번호
     * @return 프로젝트명 또는 관리자 로그인 아이디가 {@code query}를 포함하고 공개여부가 @{code state} 인 프로젝트 목록
     */
    private static Result getPagingProjects(String query, String state, int pageNum) {
        ExpressionList<Project> el = Project.find.where().or(contains("name", query), contains("owner", query));
        
        Project.State stateType = Project.State.valueOf(state.toUpperCase()); 
        if (stateType == Project.State.PUBLIC) {
            el.eq("isPublic", true);
        } else if (stateType == Project.State.PRIVATE) {
            el.eq("isPublic", false);
        }
        el.orderBy("createdDate desc");
        Page<Project> projects = el.findPagingList(PROJECT_COUNT_PER_PAGE).getPage(pageNum - 1);

        return ok(views.html.project.list.render("title.projectList", projects, query, state));
    }

    /**
     * 프로젝트 정보를 JSON으로 가져온다.
     * 
     * 프로젝트명 또는 관리자 아이디에 {@code query} 가 포함되는 프로젝트 목록을 {@link MAX_FETCH_PROJECTS} 만큼 가져오고
     * JSON으로 변환하여 반환한다.
     * 
     * @param query 검색질의(프로젝트명 또는 관리자)
     * @return JSON 형태의 프로젝트 목록
     */
    private static Result getProjectsToJSON(String query) {
        List<String> projectNames = new ArrayList<String>();
        ExpressionList<Project> el = Project.find.where().or(contains("name", query), contains("owner", query));
        int total = el.findRowCount();
        if (total > MAX_FETCH_PROJECTS) {
            el.setMaxRows(MAX_FETCH_PROJECTS);
            response().setHeader("Content-Range", "items " + MAX_FETCH_PROJECTS + "/" + total);
        }
        for (Project project: el.findList()) {
            projectNames.add(project.owner + "/" + project.name);
        }

        return ok(toJson(projectNames));
    }

    /**
     * 프로젝트 설정페이지에서 사용하며 태그목록을 JSON 형태로 반환한다.<p />
     *
     * {@code loginId}와 {@code projectName}으로 프로젝트 정보를 가져온다.<br />
     * 읽기 권한이 없을경우 forbidden을 반환한다.<br />
     * application/json 요청이 아닐경우 not_acceptable을 반환한다.<br />
     *
     *
     * @param owner the owner login id
     * @param projectName the project name
     * @return 프로젝트 태그 JSON 데이터
     */
    public static Result tags(String owner, String projectName) {
        Project project = Project.findByOwnerAndProjectName(owner, projectName);
        if (!AccessControl.isAllowed(UserApp.currentUser(), project.asResource(), Operation.READ)) {
            return forbidden();
        }

        if (!request().accepts("application/json")) {
            return status(Http.Status.NOT_ACCEPTABLE);
        }

        Map<Long, String> tags = new HashMap<Long, String>();
        for (Tag tag: project.tags) {
            tags.put(tag.id, tag.toString());
        }

        return ok(toJson(tags));
    }

    /**
     * 프로젝트 설정 페이지에서 사용하며 새로운 태그를 추가하고 추가된 태그를 JSON으로 반환한다.<p />
     *
     * {@code loginId}와 {@code projectName}으로 프로젝트 정보를 가져온다.<br />
     * 업데이트 권한이 없을경우 forbidden을 반환한다.<br />
     * 태그명 파라미터가 null일 경우 empty 데이터를 JSON으로 반환한다.<br />
     * 프로젝트내 동일한 태그가 존재할 경우 empty 데이터를 JSON으로 반환한다.<br />
     *
     * @param ownerName the owner name
     * @param projectName the project name
     * @return the result
     */
    public static Result tag(String ownerName, String projectName) {
        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);
        if (!AccessControl.isAllowed(UserApp.currentUser(), project.tagsAsResource(), Operation.UPDATE)) {
            return forbidden();
        }

        // Get category and name from the request. Return 400 Bad Request if name is not given.
        Map<String, String[]> data = request().body().asFormUrlEncoded();
        String category = HttpUtil.getFirstValueFromQuery(data, "category");
        String name = HttpUtil.getFirstValueFromQuery(data, "name");
        if (name == null || name.length() == 0) {
            // A tag must have its name.
            return badRequest("Tag name is missing.");
        }

        Tag tag = Tag.find
            .where().eq("category", category).eq("name", name).findUnique();

        boolean isCreated = false;
        if (tag == null) {
            // Create new tag if there is no tag which has the given name.
            tag = new Tag(category, name);
            tag.save();
            isCreated = true;
        }

        Boolean isAttached = project.tag(tag);

        if (!isCreated && !isAttached) {
            // Something is wrong. This case is not possible.
            play.Logger.warn(
                    "A tag '" + tag + "' is created but failed to attach to project '"
                    + project + "'.");
        }

        if (isAttached) {
            // Return the attached tag. The return type is Map<Long, String>
            // even if there is only one tag, to unify the return type with
            // ProjectApp.tags().
            Map<Long, String> tags = new HashMap<Long, String>();
            tags.put(tag.id, tag.toString());
            if (isCreated) {
                return created(toJson(tags));
            } else {
                return ok(toJson(tags));
            }
        } else {
            // Return 204 No Content if the tag has been attached already.
            return status(Http.Status.NO_CONTENT);
        }
    }

    /**
     * 프로젝트 설정 페이지에서 사용하며 태그를 삭제한다.<p />
     *
     * _method 파라미터가 delete가 아니면 badRequest를 반환한다.<br />
     * 삭제할 태그가 존재하지 않으면 notfound를 반환한다.
     *
     * @param ownerName the owner name
     * @param projectName the project name
     * @param id the id
     * @return the result
     */
    public static Result untag(String ownerName, String projectName, Long id) {
        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);
        if (!AccessControl.isAllowed(UserApp.currentUser(), project.tagsAsResource(), Operation.UPDATE)) {
            return forbidden();
        }

        // _method must be 'delete'
        Map<String, String[]> data = request().body().asFormUrlEncoded();
        if (!HttpUtil.getFirstValueFromQuery(data, "_method").toLowerCase()
                .equals("delete")) {
            return badRequest("_method must be 'delete'.");
        }

        Tag tag = Tag.find.byId(id);

        if (tag == null) {
            return notFound();
        }

        project.untag(tag);

        return status(Http.Status.NO_CONTENT);
    }
}
