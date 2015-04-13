/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @author Sangcheol Hwang
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

import actions.DefaultProjectCheckAction;
import com.avaje.ebean.*;

import controllers.annotation.AnonymousCheck;
import controllers.annotation.IsAllowed;
import info.schleichardt.play2.mailplugin.Mailer;
import models.*;
import models.enumeration.*;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.mail.HtmlEmail;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.tmatesoft.svn.core.SVNException;
import play.Logger;
import play.data.Form;
import play.data.validation.Constraints.PatternValidator;
import play.data.validation.ValidationError;
import play.db.ebean.Transactional;
import play.i18n.Messages;
import play.libs.Json;
import play.mvc.*;
import play.mvc.Http.MultipartFormData.FilePart;
import playRepository.Commit;
import playRepository.PlayRepository;
import playRepository.RepositoryService;
import scala.reflect.io.FileOperationException;
import utils.*;
import validation.ExConstraints.RestrictedValidator;
import views.html.project.create;
import views.html.project.delete;
import views.html.project.home;
import views.html.project.setting;
import views.html.project.transfer;
import views.html.project.change_vcs;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import static play.data.Form.form;
import static play.libs.Json.toJson;
import static utils.LogoUtil.*;
import static utils.TemplateHelper.*;

@AnonymousCheck
public class ProjectApp extends Controller {

    private static final int ISSUE_MENTION_SHOW_LIMIT = 2000;

    private static final int MAX_FETCH_PROJECTS = 1000;

    private static final int COMMIT_HISTORY_PAGE = 0;

    private static final int COMMIT_HISTORY_SHOW_LIMIT = 10;

    private static final int RECENLTY_ISSUE_SHOW_LIMIT = 10;

    private static final int RECENLTY_POSTING_SHOW_LIMIT = 10;

    private static final int RECENT_PULL_REQUEST_SHOW_LIMIT = 10;

    private static final int PROJECT_COUNT_PER_PAGE = 10;

    private static final String HTML = "text/html";

    private static final String JSON = "application/json";

    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @IsAllowed(Operation.UPDATE)
    public static Result projectOverviewUpdate(String ownerId, String projectName){
        Project targetProject = Project.findByOwnerAndProjectName(ownerId, projectName);
        if (targetProject == null) {
            return notFound(ErrorViews.NotFound.render("error.notfound"));
        }

        targetProject.overview = request().body().asJson().findPath("overview").textValue();
        targetProject.save();

        ObjectNode result = Json.newObject();
        result.put("overview", targetProject.overview);
        return ok(result);
    }

    @IsAllowed(Operation.READ)
    public static Result project(String ownerId, String projectName)
            throws IOException, ServletException, SVNException, GitAPIException {
        Project project = Project.findByOwnerAndProjectName(ownerId, projectName);
        List<History> histories = getProjectHistory(ownerId, project);

        UserApp.currentUser().visits(project);

        String tabId = StringUtils.defaultIfBlank(request().getQueryString("tabId"), "readme");

        return ok(home.render(getTitleMessage(tabId), project, histories, tabId));
    }

    private static String getTitleMessage(String tabId) {
        switch (tabId) {
            case "history":
                return "project.history.recent";
            case "dashboard":
                return "title.projectDashboard";
            default:
            case "readme":
                return "title.projectHome";
        }
    }

    private static List<History> getProjectHistory(String ownerId, Project project)
            throws IOException, ServletException, SVNException, GitAPIException {
        project.fixInvalidForkData();

        PlayRepository repository = RepositoryService.getRepository(project);

        List<Commit> commits = null;

        try {
            commits = repository.getHistory(COMMIT_HISTORY_PAGE, COMMIT_HISTORY_SHOW_LIMIT, null, null);
        } catch (NoHeadException e) {
            // NOOP
        }

        List<Issue> issues = Issue.findRecentlyCreated(project, RECENLTY_ISSUE_SHOW_LIMIT);
        List<Posting> postings = Posting.findRecentlyCreated(project, RECENLTY_POSTING_SHOW_LIMIT);
        List<PullRequest> pullRequests = PullRequest.findRecentlyReceived(project, RECENT_PULL_REQUEST_SHOW_LIMIT);

        return History.makeHistory(ownerId, project, commits, issues, postings, pullRequests);
    }

    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    public static Result newProjectForm() {
        Form<Project> projectForm = form(Project.class).bindFromRequest("owner");
        projectForm.discardErrors();
        List<OrganizationUser> orgUserList = OrganizationUser.findByAdmin(UserApp.currentUser().id);
        return ok(create.render("title.newProject", projectForm, orgUserList));
    }

    @IsAllowed(Operation.UPDATE)
    public static Result settingForm(String ownerId, String projectName) throws Exception {
        Project project = Project.findByOwnerAndProjectName(ownerId, projectName);
        Form<Project> projectForm = form(Project.class).fill(project);
        PlayRepository repository = RepositoryService.getRepository(project);
        return ok(setting.render("title.projectSetting", projectForm, project, repository.getRefNames()));
    }

    @Transactional
    public static Result newProject() throws Exception {
        Form<Project> filledNewProjectForm = form(Project.class).bindFromRequest();
        String owner = filledNewProjectForm.field("owner").value();

        User user = UserApp.currentUser();
        Organization organization = Organization.findByName(owner);

        if ((!AccessControl.isGlobalResourceCreatable(user))
                || (Organization.isNameExist(owner) && !OrganizationUser.isAdmin(organization.id, user.id))) {
            return forbidden(ErrorViews.Forbidden.render("'" + user.name + "' has no permission"));
        }

        if (validateWhenNew(filledNewProjectForm)) {
            return badRequest(create.render("title.newProject",
                    filledNewProjectForm, OrganizationUser.findByAdmin(user.id)));
        }

        Project project = filledNewProjectForm.get();
        if (Organization.isNameExist(owner)) {
            project.organization = organization;
        }
        ProjectUser.assignRole(user.id, Project.create(project), RoleType.MANAGER);
        RepositoryService.createRepository(project);

        saveProjectMenuSetting(project);

        return redirect(routes.ProjectApp.project(project.owner, project.name));
    }

    private static boolean validateWhenNew(Form<Project> newProjectForm) {
        String owner = newProjectForm.field("owner").value();
        String name = newProjectForm.field("name").value();

        User user = User.findByLoginId(owner);
        boolean ownerIsUser = User.isLoginIdExist(owner);
        boolean ownerIsOrganization = Organization.isNameExist(owner);

        if (!ownerIsUser && !ownerIsOrganization) {
            newProjectForm.reject("owner", "project.owner.invalidate");
        }

        if (ownerIsUser && !UserApp.currentUser().id.equals(user.id)) {
            newProjectForm.reject("owner", "project.owner.invalidate");
        }

        if (Project.exists(owner, name)) {
            newProjectForm.reject("name", "project.name.duplicate");
        }

        ValidationError error = newProjectForm.error("name");
        if (error != null) {
            if (PatternValidator.message.equals(error.message())) {
                newProjectForm.errors().remove("name");
                newProjectForm.reject("name", "project.name.alert");
            } else if (RestrictedValidator.message.equals(error.message())) {
                newProjectForm.errors().remove("name");
                newProjectForm.reject("name", "project.name.reserved.alert");
            }
        }

        return newProjectForm.hasErrors();
    }

    @Transactional
    @IsAllowed(Operation.UPDATE)
    public static Result settingProject(String ownerId, String projectName)
            throws IOException, NoSuchAlgorithmException, UnsupportedOperationException, ServletException {
        Form<Project> filledUpdatedProjectForm = form(Project.class).bindFromRequest();
        Project project = Project.findByOwnerAndProjectName(ownerId, projectName);
        PlayRepository repository = RepositoryService.getRepository(project);

        if (validateWhenUpdate(ownerId, filledUpdatedProjectForm)) {
            return badRequest(setting.render("title.projectSetting",
                    filledUpdatedProjectForm, project, repository.getRefNames()));
        }

        Project updatedProject = filledUpdatedProjectForm.get();

        FilePart filePart = request().body().asMultipartFormData().getFile("logoPath");

        if (!isEmptyFilePart(filePart)) {
            Attachment.deleteAll(updatedProject.asResource());
            new Attachment().store(filePart.getFile(), filePart.getFilename(), updatedProject.asResource());
        }

        Map<String, String[]> data = request().body().asMultipartFormData().asFormUrlEncoded();
        String defaultBranch = HttpUtil.getFirstValueFromQuery(data, "defaultBranch");
        if (StringUtils.isNotEmpty(defaultBranch)) {
            repository.setDefaultBranch(defaultBranch);
        }

        if (!project.name.equals(updatedProject.name)) {
            if (!repository.renameTo(updatedProject.name)) {
                throw new FileOperationException("fail repository rename to " + project.owner + "/" + updatedProject.name);
            }
        }

        updatedProject.update();

        saveProjectMenuSetting(updatedProject);

        return redirect(routes.ProjectApp.settingForm(ownerId, updatedProject.name));
    }

    private static void saveProjectMenuSetting(Project project) {
        Form<ProjectMenuSetting> filledUpdatedProjectMenuSettingForm = form(ProjectMenuSetting.class).bindFromRequest();
        ProjectMenuSetting updatedProjectMenuSetting = filledUpdatedProjectMenuSettingForm.get();

        project.refresh();
        updatedProjectMenuSetting.project = project;

        if (project.menuSetting == null) {
            updatedProjectMenuSetting.save();
        } else {
            updatedProjectMenuSetting.id = project.menuSetting.id;
            updatedProjectMenuSetting.update();
        }
    }

    private static boolean validateWhenUpdate(String loginId, Form<Project> updateProjectForm) {
        Long id = Long.parseLong(updateProjectForm.field("id").value());
        String name = updateProjectForm.field("name").value();

        if (!Project.projectNameChangeable(id, loginId, name)) {
            flash(Constants.WARNING, "project.name.duplicate");
            updateProjectForm.reject("name", "project.name.duplicate");
        }

        FilePart filePart = request().body().asMultipartFormData().getFile("logoPath");

        if (!isEmptyFilePart(filePart)) {
            if (!isImageFile(filePart.getFilename())) {
                flash(Constants.WARNING, "project.logo.alert");
                updateProjectForm.reject("logoPath", "project.logo.alert");
            } else if (filePart.getFile().length() > LOGO_FILE_LIMIT_SIZE) {
                flash(Constants.WARNING, "project.logo.fileSizeAlert");
                updateProjectForm.reject("logoPath", "project.logo.fileSizeAlert");
            }
        }

        ValidationError error = updateProjectForm.error("name");
        if (error != null) {
            if (PatternValidator.message.equals(error.message())) {
                flash(Constants.WARNING, "project.name.alert");
                updateProjectForm.errors().remove("name");
                updateProjectForm.reject("name", "project.name.alert");
            } else if (RestrictedValidator.message.equals(error.message())) {
                flash(Constants.WARNING, "project.name.reserved.alert");
                updateProjectForm.errors().remove("name");
                updateProjectForm.reject("name", "project.name.reserved.alert");
            }
        }

        return updateProjectForm.hasErrors();
    }

    @IsAllowed(Operation.DELETE)
    public static Result deleteForm(String ownerId, String projectName) {
        Project project = Project.findByOwnerAndProjectName(ownerId, projectName);
        Form<Project> projectForm = form(Project.class).fill(project);
        return ok(delete.render("title.projectDelete", projectForm, project));
    }

    @Transactional
    @IsAllowed(Operation.DELETE)
    public static Result deleteProject(String ownerId, String projectName) throws Exception {
        Project project = Project.findByOwnerAndProjectName(ownerId, projectName);
        project.delete();
        RepositoryService.deleteRepository(project);

        if (HttpUtil.isRequestedWithXHR(request())){
            response().setHeader("Location", routes.Application.index().toString());
            return status(204);
        }

        return redirect(routes.Application.index());
    }

    @Transactional
    @IsAllowed(Operation.UPDATE)
    public static Result members(String loginId, String projectName) {
        Project project = Project.findByOwnerAndProjectName(loginId, projectName);
        project.cleanEnrolledUsers();
        return ok(views.html.project.members.render("title.projectMembers",
                ProjectUser.findMemberListByProject(project.id), project,
                Role.findProjectRoles()));
    }

    @IsAllowed(Operation.READ)
    public static Result mentionList(String loginId, String projectName, Long number, String resourceType) {
        String prefer = HttpUtil.getPreferType(request(), HTML, JSON);
        if (prefer == null) {
            return status(Http.Status.NOT_ACCEPTABLE);
        } else {
            response().setHeader("Vary", "Accept");
        }

        Project project = Project.findByOwnerAndProjectName(loginId, projectName);

        List<User> userList = new ArrayList<>();
        collectAuthorAndCommenter(project, number, userList, resourceType);
        addProjectMemberList(project, userList);
        addGroupMemberList(project, userList);
        addProjectAuthorsAndWatchersList(project, userList);

        userList.remove(UserApp.currentUser());
        userList.add(UserApp.currentUser()); //send me last at list

        Map<String, List<Map<String, String>>> result = new HashMap<>();
        result.put("result", getUserList(project, userList));
        result.put("issues", getIssueList(project));

        return ok(toJson(result));
    }

    private static List<Map<String, String>> getIssueList(Project project) {
        List<Map<String, String>> mentionListOfIssues = new ArrayList<>();
        collectedIssuesToMap(mentionListOfIssues, getMentionIssueList(project));
        return mentionListOfIssues;
    }

    private static List<Map<String, String>> getUserList(Project project, List<User> userList) {
        List<Map<String, String>> mentionListOfUser = new ArrayList<>();
        collectedUsersToMentionList(mentionListOfUser, userList);
        addProjectNameToMentionList(mentionListOfUser, project);
        addOrganizationNameToMentionList(mentionListOfUser, project);
        return mentionListOfUser;
    }

    private static void addProjectNameToMentionList(List<Map<String, String>> users, Project project) {
        Map<String, String> projectUserMap = new HashMap<>();
        if(project != null){
            projectUserMap.put("loginid", project.owner+"/" + project.name);
            projectUserMap.put("username", project.name );
            projectUserMap.put("name", project.name);
            projectUserMap.put("image", urlToProjectLogo(project).toString());
            users.add(projectUserMap);
        }
    }

    private static void addOrganizationNameToMentionList(List<Map<String, String>> users, Project project) {
        Map<String, String> projectUserMap = new HashMap<>();
        if(project != null && project.organization != null){
            projectUserMap.put("loginid", project.organization.name);
            projectUserMap.put("username", project.organization.name);
            projectUserMap.put("name", project.organization.name);
            projectUserMap.put("image", urlToOrganizationLogo(project.organization).toString());
            users.add(projectUserMap);
        }
    }

    private static void collectedIssuesToMap(List<Map<String, String>> mentionList,
            List<Issue> issueList) {
        for (Issue issue : issueList) {
            Map<String, String> projectIssueMap = new HashMap<>();
            projectIssueMap.put("name", issue.getNumber().toString() + issue.title);
            projectIssueMap.put("issueNo", issue.getNumber().toString());
            projectIssueMap.put("title", issue.title);
            mentionList.add(projectIssueMap);
        }
    }

    private static List<Issue> getMentionIssueList(Project project) {
        return Issue.finder.where()
                .eq("project.id", project.isForkedFromOrigin() ? project.originalProject.id : project.id)
                .orderBy("createdDate desc")
                .setMaxRows(ISSUE_MENTION_SHOW_LIMIT)
                .findList();
    }

    @IsAllowed(Operation.READ)
    public static Result mentionListAtCommitDiff(String ownerId, String projectName, String commitId, Long pullRequestId)
            throws IOException, UnsupportedOperationException, ServletException, SVNException {
        Project project = Project.findByOwnerAndProjectName(ownerId, projectName);

        PullRequest pullRequest;
        Project fromProject = project;
        if (pullRequestId != -1) {
            pullRequest = PullRequest.findById(pullRequestId);
            if (pullRequest != null) {
                fromProject = pullRequest.fromProject;
            }
        }

        Commit commit = RepositoryService.getRepository(fromProject).getCommit(commitId);

        List<User> userList = new ArrayList<>();
        addCommitAuthor(commit, userList);
        addCodeCommenters(commitId, fromProject.id, userList);
        addProjectMemberList(project, userList);
        addGroupMemberList(project, userList);
        userList.remove(UserApp.currentUser());
        userList.add(UserApp.currentUser()); //send me last at list

        Map<String, List<Map<String, String>>> result = new HashMap<>();
        result.put("result", getUserList(project, userList));
        result.put("issues", getIssueList(project));

        return ok(toJson(result));
    }

    @IsAllowed(Operation.READ)
    public static Result mentionListAtPullRequest(String ownerId, String projectName, String commitId, Long pullRequestId)
            throws IOException, UnsupportedOperationException, ServletException, SVNException {
        Project project = Project.findByOwnerAndProjectName(ownerId, projectName);

        PullRequest pullRequest = PullRequest.findById(pullRequestId);
        List<User> userList = new ArrayList<>();

        addCommentAuthors(pullRequestId, userList);
        addProjectMemberList(project, userList);
        addGroupMemberList(project, userList);
        if(!commitId.isEmpty()) {
            addCommitAuthor(RepositoryService.getRepository(pullRequest.fromProject).getCommit(commitId), userList);
        }

        User contributor = pullRequest.contributor;
        if(!userList.contains(contributor)) {
            userList.add(contributor);
        }

        userList.remove(UserApp.currentUser());
        userList.add(UserApp.currentUser()); //send me last at list

        Map<String, List<Map<String, String>>> result = new HashMap<>();
        result.put("result", getUserList(project, userList));
        result.put("issues", getIssueList(project));

        return ok(toJson(result));
    }

    private static void addCommentAuthors(Long pullRequestId, List<User> userList) {
        List<CommentThread> threads = PullRequest.findById(pullRequestId).commentThreads;
        for (CommentThread thread : threads) {
            for (ReviewComment comment : thread.reviewComments) {
                final User commenter = User.findByLoginId(comment.author.loginId);
                if(userList.contains(commenter)) {
                    userList.remove(commenter);
                }
                userList.add(commenter);
            }
        }
        Collections.reverse(userList);
    }

    @IsAllowed(Operation.DELETE)
    public static Result transferForm(String ownerId, String projectName) {
        Project project = Project.findByOwnerAndProjectName(ownerId, projectName);
        Form<Project> projectForm = form(Project.class).fill(project);

        return ok(transfer.render("title.projectTransfer", projectForm, project));
    }

    @Transactional
    @IsAllowed(Operation.DELETE)
    public static Result transferProject(String ownerId, String projectName) {
        Project project = Project.findByOwnerAndProjectName(ownerId, projectName);
        String destination = request().getQueryString("owner");

        User destOwner = User.findByLoginId(destination);
        Organization destOrg = Organization.findByName(destination);
        if (destOwner.isAnonymous() && destOrg == null) {
            return badRequest(ErrorViews.BadRequest.render());
        }

        User projectOwner = User.findByLoginId(project.owner);
        Organization projectOrg = Organization.findByName(project.owner);
        if((!destOwner.isAnonymous() && destOwner.equals(projectOwner)) || (projectOrg != null && projectOrg.equals(destOrg))) {
            flash(Constants.INFO, "project.transfer.has.same.owner");
            Form<Project> projectForm = form(Project.class).fill(project);
            return ok(transfer.render("title.projectTransfer", projectForm, project));
        }

        ProjectTransfer pt = null;
        // make a request to move to an user
        if (!destOwner.isAnonymous()) {
            pt = ProjectTransfer.requestNewTransfer(project, UserApp.currentUser(), destOwner.loginId);
        }
        // make a request to move to an group
        if (destOrg != null) {
            pt = ProjectTransfer.requestNewTransfer(project, UserApp.currentUser(), destOrg.name);
        }
        sendTransferRequestMail(pt);
        flash(Constants.INFO, "project.transfer.is.requested");

        // if the request is sent by XHR, response with 204 204 No Content and Location header.
        String url = routes.ProjectApp.project(ownerId, projectName).url();
        if (HttpUtil.isRequestedWithXHR(request())) {
            response().setHeader("Location", url);
            return status(204);
        }

        return redirect(url);
    }

    @Transactional
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    public static synchronized Result acceptTransfer(Long id, String confirmKey) throws IOException, ServletException {
        ProjectTransfer pt = ProjectTransfer.findValidOne(id);
        if (pt == null) {
            return notFound(ErrorViews.NotFound.render());
        }
        if (confirmKey == null || !pt.confirmKey.equals(confirmKey)) {
            return badRequest(ErrorViews.BadRequest.render());
        }

        if (!AccessControl.isAllowed(UserApp.currentUser(), pt.asResource(), Operation.ACCEPT)) {
            return forbidden(ErrorViews.Forbidden.render());
        }

        Project project = pt.project;

        // Change the project's name and move the repository.
        String newProjectName = Project.newProjectName(pt.destination, project.name);
        PlayRepository repository = RepositoryService.getRepository(project);
        repository.move(project.owner, project.name, pt.destination, newProjectName);

        User newOwnerUser = User.findByLoginId(pt.destination);
        Organization newOwnerOrg = Organization.findByName(pt.destination);

        // Change the project's information.
        project.owner = pt.destination;
        project.name = newProjectName;
        if (newOwnerOrg != null) {
            project.organization = newOwnerOrg;
        } else {
            project.organization = null;
        }
        project.update();

        // Change roles.
        if (ProjectUser.isManager(pt.sender.id, project.id)) {
            ProjectUser.assignRole(pt.sender.id, project.id, RoleType.MEMBER);
        }
        if (!newOwnerUser.isAnonymous()) {
            ProjectUser.assignRole(newOwnerUser.id, project.id, RoleType.MANAGER);
        }

        // Change the tranfer's status to be accepted.
        pt.newProjectName = newProjectName;
        pt.accepted = true;
        pt.update();

        // If the opposite request is exists, delete it.
        ProjectTransfer.deleteExisting(project, pt.sender, pt.destination);

        return redirect(routes.ProjectApp.project(project.owner, project.name));
    }

    @IsAllowed(Operation.UPDATE)
    public static Result changeVCSForm(String ownerId, String projectName) {
        Project project = Project.findByOwnerAndProjectName(ownerId, projectName);
        Form<Project> projectForm = form(Project.class).fill(project);
        return ok(change_vcs.render("title.projectChangeVCS", projectForm, project));
    }

    @IsAllowed(Operation.UPDATE)
    public static Result changeVCS(String ownerId, String projectName) throws Exception {
        Project project = Project.findByOwnerAndProjectName(ownerId, projectName);
        try {
            if (project.readme() != null){
                Posting posting = Posting.findREADMEPosting(project);
                if (posting != null){
                    posting.readme = false;
                    posting.save();
                }
            }
            project.changeVCS();
            String url = routes.ProjectApp.project(ownerId, projectName).url();
            response().setHeader("Location", url);
            return noContent();
        } catch (Exception e) {
            Logger.error(e.getMessage());
        }
        return internalServerError();
    }

    private static void sendTransferRequestMail(ProjectTransfer pt) {
        HtmlEmail email = new HtmlEmail();
        try {
            String acceptUrl = pt.getAcceptUrl();
            String message = Messages.get("transfer.message.hello", pt.destination) + "\n\n"
                    + Messages.get("transfer.message.detail", pt.project.name, pt.newProjectName, pt.project.owner, pt.destination) + "\n"
                    + Messages.get("transfer.message.link") + "\n\n"
                    + acceptUrl + "\n\n"
                    + Messages.get("transfer.message.deadline") + "\n\n"
                    + Messages.get("transfer.message.thank");

            email.setFrom(Config.getEmailFromSmtp(), pt.sender.name);
            email.addTo(Config.getEmailFromSmtp(), "Yobi");

            User to = User.findByLoginId(pt.destination);
            if (!to.isAnonymous()) {
                email.addBcc(to.email, to.name);
            }

            Organization org = Organization.findByName(pt.destination);
            if (org != null) {
                List<OrganizationUser> admins = OrganizationUser.findAdminsOf(org);
                for(OrganizationUser admin : admins) {
                    email.addBcc(admin.user.email, admin.user.name);
                }
            }

            email.setSubject(String.format("[%s] @%s wants to transfer project", pt.project.name, pt.sender.loginId));
            email.setHtmlMsg(Markdown.render(message));
            email.setTextMsg(message);
            email.setCharset("utf-8");
            email.addHeader("References", "<" + acceptUrl + "@" + Config.getHostname() + ">");
            email.setSentDate(pt.requested);
            Mailer.send(email);
            String escapedTitle = email.getSubject().replace("\"", "\\\"");
            String logEntry = String.format("\"%s\" %s", escapedTitle, email.getBccAddresses());
            play.Logger.of("mail").info(logEntry);
        } catch (Exception e) {
            Logger.warn("Failed to send a notification: " + email + "\n" + ExceptionUtils.getStackTrace(e));
        }
    }

    private static void addCodeCommenters(String commitId, Long projectId, List<User> userList) {
        Project project = Project.find.byId(projectId);

        if (RepositoryService.VCS_GIT.equals(project.vcs)) {
            List<ReviewComment> comments = ReviewComment.find
                    .fetch("thread")
                    .where()
                    .eq("thread.commitId",commitId)
                    .eq("thread.project", project)
                    .eq("thread.pullRequest", null).findList();

            for (ReviewComment comment : comments) {
                User commentAuthor = User.findByLoginId(comment.author.loginId);
                if (userList.contains(commentAuthor)) {
                    userList.remove(commentAuthor);
                }
                userList.add(commentAuthor);
            }
        } else {
            List<CommitComment> comments = CommitComment.find.where().eq("commitId",
                    commitId).eq("project.id", projectId).findList();

            for (CommitComment codeComment : comments) {
                User commentAuthor = User.findByLoginId(codeComment.authorLoginId);
                if (userList.contains(commentAuthor)) {
                    userList.remove(commentAuthor);
                }
                userList.add(commentAuthor);
            }
        }

        Collections.reverse(userList);
    }

    private static void addCommitAuthor(Commit commit, List<User> userList) {
        if (!commit.getAuthor().isAnonymous() && !userList.contains(commit.getAuthor())) {
            userList.add(commit.getAuthor());
        }

        //fallback: additional search by email id
        if (commit.getAuthorEmail() != null) {
            User authorByEmail = User.findByLoginId(commit.getAuthorEmail().substring(0, commit.getAuthorEmail().lastIndexOf("@")));
            if (!authorByEmail.isAnonymous() && !userList.contains(authorByEmail)) {
                userList.add(authorByEmail);
            }
        }
    }

    private static void collectAuthorAndCommenter(Project project, Long number, List<User> userList, String resourceType) {
        AbstractPosting posting;
        switch (ResourceType.getValue(resourceType)) {
            case ISSUE_POST:
                posting = AbstractPosting.findByNumber(Issue.finder, project, number);
                break;
            case BOARD_POST:
                posting = AbstractPosting.findByNumber(Posting.finder, project, number);
                break;
            default:
                return;
        }

        if (posting != null) {
            for (Comment comment: posting.getComments()) {
                User commentUser = User.findByLoginId(comment.authorLoginId);
                if (userList.contains(commentUser)) {
                    userList.remove(commentUser);
                }
                userList.add(commentUser);
            }
            Collections.reverse(userList); // recent commenter first!
            User postAuthor = User.findByLoginId(posting.authorLoginId);
            if (!userList.contains(postAuthor)) {
                userList.add(postAuthor);
            }
        }
    }

    private static void collectedUsersToMentionList(List<Map<String, String>> users, List<User> userList) {
        for (User user: userList) {
            Map<String, String> projectUserMap = new HashMap<>();
            if (user != null && !user.loginId.equals(Constants.ADMIN_LOGIN_ID)) {
                projectUserMap.put("loginid", user.loginId);
                projectUserMap.put("username", user.name);
                projectUserMap.put("name", user.name + user.loginId);
                projectUserMap.put("image", user.avatarUrl());
                users.add(projectUserMap);
            }
        }
    }

    private static void addProjectMemberList(Project project, List<User> userList) {
        for (ProjectUser projectUser: project.projectUser) {
            if (!userList.contains(projectUser.user)) {
                userList.add(projectUser.user);
            }
        }
    }

    private static void addGroupMemberList(Project project, List<User> userList) {
        if (!project.hasGroup()) {
            return;
        }

        for (OrganizationUser organizationUser : project.organization.users) {
            if (!userList.contains(organizationUser.user)) {
                userList.add(organizationUser.user);
            }
        }
    }

    private static void addProjectAuthorsAndWatchersList(Project project, List<User> userList) {
        if(project == null){
            return;
        }

        for (User user : findAuthorsAndWatchers(project)) {
            if (!userList.contains(user)) {
                userList.add(user);
            }
        }
    }

    @Transactional
    @With(DefaultProjectCheckAction.class)
    @IsAllowed(Operation.UPDATE)
    public static Result newMember(String ownerId, String projectName) {
        Form<User> addMemberForm = form(User.class).bindFromRequest();

        User newMember = User.findByLoginId(addMemberForm.field("loginId").value());
        Project project = Project.findByOwnerAndProjectName(ownerId, projectName);

        if (isErrorOnAddMemberForm(newMember, project, addMemberForm)) {
            if(HttpUtil.isJSONPreferred(request())){
                return badRequest(addMemberForm.errorsAsJson());
            }

            List<ValidationError> errors = addMemberForm.errors().get("loginId");
            flash(Constants.WARNING, errors.get(errors.size() - 1).message());
            return redirect(routes.ProjectApp.members(ownerId, projectName));
        }

        ProjectUser.assignRole(newMember.id, project.id, RoleType.MEMBER);
        project.cleanEnrolledUsers();
        NotificationEvent.afterMemberRequest(project, newMember, RequestState.ACCEPT);

        if(HttpUtil.isJSONPreferred(request())){
            return ok("{}");
        }
        return redirect(routes.ProjectApp.members(ownerId, projectName));
    }

    private static boolean isErrorOnAddMemberForm(User user, Project project, Form<User> addMemberForm) {
        if (addMemberForm.hasErrors()) {
            addMemberForm.reject("loginId", "project.members.addMember");
        } else if (!AccessControl.isAllowed(UserApp.currentUser(), project.asResource(), Operation.UPDATE)) {
            addMemberForm.reject("loginId", "project.member.isManager");
        } else if (user.isAnonymous()) {
            addMemberForm.reject("loginId", "project.member.notExist");
        } else if (user.isMemberOf(project)) {
            addMemberForm.reject("loginId", "project.member.alreadyMember");
        }

        return addMemberForm.hasErrors();
    }


    /**
     * Returns OK(200) with {@code location} which is represented as JSON .
     *
     * Since returning redirect response(3xx) to Ajax request causes unexpected result, this function returns OK(200) containing redirect location.
     * The client will check {@code location} and have a page move to the location.
     *
     * @param location
     * @return
     */
    private static Result okWithLocation(String location) {
        ObjectNode result = Json.newObject();
        result.put("location", location);

        return ok(result);
    }

    /**
     * @param ownerId the user login id
     * @param projectName the project name
     * @param userId
     * @return the result
     */
    @Transactional
    @With(DefaultProjectCheckAction.class)
    public static Result deleteMember(String ownerId, String projectName, Long userId) {
        Project project = Project.findByOwnerAndProjectName(ownerId, projectName);
        User deleteMember = User.find.byId(userId);

        if (!UserApp.currentUser().id.equals(userId)
                && !AccessControl.isAllowed(UserApp.currentUser(), project.asResource(), Operation.UPDATE)) {
            return forbidden(ErrorViews.Forbidden.render("error.forbidden", project));
        }

        if (project.isOwner(deleteMember)) {
            return forbidden(ErrorViews.Forbidden.render("project.member.ownerCannotLeave", project));
        }

        ProjectUser.delete(userId, project.id);

        if (UserApp.currentUser().id.equals(userId)) {
            if (AccessControl.isAllowed(UserApp.currentUser(), project.asResource(), Operation.READ)) {
                return okWithLocation(routes.ProjectApp.project(project.owner, project.name).url());
            } else {
                return okWithLocation(routes.Application.index().url());
            }
        } else {
            return okWithLocation(routes.ProjectApp.members(ownerId, projectName).url());
        }
    }

    /**
     * @param ownerId the user login id
     * @param projectName the project name
     * @param userId the user id
     * @return
     */
    @Transactional
    @IsAllowed(Operation.UPDATE)
    public static Result editMember(String ownerId, String projectName, Long userId) {
        Project project = Project.findByOwnerAndProjectName(ownerId, projectName);
        User editMember = User.find.byId(userId);

        if (project.isOwner(editMember)) {
            return badRequest(ErrorViews.Forbidden.render("project.member.ownerMustBeAManager", project));
        }

        ProjectUser.assignRole(userId, project.id, form(Role.class).bindFromRequest().get().id);
        return status(Http.Status.NO_CONTENT);
    }

    /**
     * @param query the query
     * @param pageNum the page num
     * @return
     */
    public static Result projects(String query, int pageNum) {
        String prefer = HttpUtil.getPreferType(request(), HTML, JSON);
        if (prefer == null) {
            return status(Http.Status.NOT_ACCEPTABLE);
        }

        response().setHeader("Vary", "Accept");

        if (prefer.equals(JSON)) {
            return getProjectsToJSON(query);
        } else {
            return getPagingProjects(query, pageNum);
        }
    }

    private static Result getPagingProjects(String query, int pageNum) {
        ExpressionList<Project> el = createProjectSearchExpressionList(query);

        Set<Long> labelIds = LabelApp.getLabelIds(request());
        if (CollectionUtils.isNotEmpty(labelIds)) {
            el.in("labels.id", labelIds);
        }

        el.orderBy("createdDate desc");
        Page<Project> projects = el.findPagingList(PROJECT_COUNT_PER_PAGE).getPage(pageNum - 1);

        return ok(views.html.project.list.render("title.projectList", projects, query));
    }

    private static Result getProjectsToJSON(String query) {
        ExpressionList<Project> el = createProjectSearchExpressionList(query);

        int total = el.findRowCount();
        if (total > MAX_FETCH_PROJECTS) {
            el.setMaxRows(MAX_FETCH_PROJECTS);
            response().setHeader("Content-Range", "items " + MAX_FETCH_PROJECTS + "/" + total);
        }

        List<String> projectNames = new ArrayList<>();
        for (Project project: el.findList()) {
            projectNames.add(project.owner + "/" + project.name);
        }

        return ok(toJson(projectNames));
    }

    private static ExpressionList<Project> createProjectSearchExpressionList(String query) {
        ExpressionList<Project> el = Project.find.where();

        if (StringUtils.isNotBlank(query)) {
            Junction<Project> junction = el.disjunction();
            junction.icontains("owner", query)
                    .icontains("name", query)
                    .icontains("overview", query);
            List<Object> ids = Project.find.where().icontains("labels.name", query).findIds();
            if (!ids.isEmpty()) {
                junction.idIn(ids);
            }
            junction.endJunction();
        }

        User user = UserApp.currentUser();
        if (!user.isSiteManager()) {
            if(user.isAnonymous()) {
                el.eq("projectScope", ProjectScope.PUBLIC);
            } else {
                Junction<Project> junction = el.conjunction();
                Junction<Project> pj = junction.disjunction();
                pj.add(Expr.eq("projectScope", ProjectScope.PUBLIC)); // public
                List<Organization> orgs = Organization.findOrganizationsByUserLoginId(user.loginId); // protected
                if(!orgs.isEmpty()) {
                    pj.and(Expr.in("organization", orgs), Expr.eq("projectScope", ProjectScope.PROTECTED));
                }
                pj.add(Expr.eq("projectUser.user.id", user.id)); // private
                pj.endJunction();
            }
        }

        return el;
    }

    /**
     * @param ownerId the owner login id
     * @param projectName the project name
     * @return
     */
    @IsAllowed(Operation.READ)
    public static Result labels(String ownerId, String projectName) {
        if (!request().accepts("application/json")) {
            return status(Http.Status.NOT_ACCEPTABLE);
        }

        Project project = Project.findByOwnerAndProjectName(ownerId, projectName);

        Map<Long, Map<String, String>> labels = new HashMap<>();
        for (Label label: project.labels) {
            labels.put(label.id, convertToMap(label));
        }

        return ok(toJson(labels));
    }

    /**
     * convert from some part of {@link models.Label} to {@link java.util.Map}
     * @param label {@link models.Label} object
     * @return label's map data
     */
    private static Map<String, String> convertToMap(Label label) {
        Map<String, String> tagMap = new HashMap<>();
        tagMap.put("category", label.category);
        tagMap.put("name", label.name);
        return tagMap;
    }

    /**
     * @param ownerId the owner name
     * @param projectName the project name
     * @return the result
     */
    @Transactional
    @With(DefaultProjectCheckAction.class)
    public static Result attachLabel(String ownerId, String projectName) {
        Project project = Project.findByOwnerAndProjectName(ownerId, projectName);

        if (!AccessControl.isAllowed(UserApp.currentUser(), project.labelsAsResource(), Operation.UPDATE)) {
            return forbidden(ErrorViews.Forbidden.render("error.forbidden", project));
        }

        // Get category and name from the request. Return 400 Bad Request if name is not given.
        Map<String, String[]> data = request().body().asFormUrlEncoded();
        String category = HttpUtil.getFirstValueFromQuery(data, "category");
        String name = HttpUtil.getFirstValueFromQuery(data, "name");
        if (StringUtils.isEmpty(name)) {
            // A label must have its name.
            return badRequest(ErrorViews.BadRequest.render("Label name is missing.", project));
        }

        Label label = Label.find
            .where().eq("category", category).eq("name", name).findUnique();

        boolean isCreated = false;
        if (label == null) {
            // Create new label if there is no label which has the given name.
            label = new Label(category, name);
            label.save();
            isCreated = true;
        }

        Boolean isAttached = project.attachLabel(label);

        if (isCreated && !isAttached) {
            // Something is wrong. This case is not possible.
            play.Logger.warn(
                    "A label '" + label + "' is created but failed to attach to project '" + project + "'.");
        }

        if (isAttached) {
            // Return the attached label. The return type is Map<Long, Map<String, String>>
            // even if there is only one label, to unify the return type with
            // ProjectApp.labels().
            Map<Long, Map<String, String>> labels = new HashMap<>();
            labels.put(label.id, convertToMap(label));

            if (isCreated) {
                return created(toJson(labels));
            } else {
                return ok(toJson(labels));
            }
        } else {
            // Return 204 No Content if the label is already attached.
            return status(Http.Status.NO_CONTENT);
        }
    }

    /**
     * @param ownerId the owner name
     * @param projectName the project name
     * @param id the id
     * @return the result
     */
    @Transactional
    @With(DefaultProjectCheckAction.class)
    public static Result detachLabel(String ownerId, String projectName, Long id) {
        Project project = Project.findByOwnerAndProjectName(ownerId, projectName);

        if (!AccessControl.isAllowed(UserApp.currentUser(), project.labelsAsResource(), Operation.UPDATE)) {
            return forbidden(ErrorViews.Forbidden.render("error.forbidden", project));
        }

        // _method must be 'delete'
        Map<String, String[]> data = request().body().asFormUrlEncoded();
        if (!HttpUtil.getFirstValueFromQuery(data, "_method").toLowerCase()
                .equals("delete")) {
            return badRequest(ErrorViews.BadRequest.render("_method must be 'delete'.", project));
        }

        Label tag = Label.find.byId(id);

        if (tag == null) {
            return notFound(ErrorViews.NotFound.render("error.notfound"));
        }

        project.detachLabel(tag);

        return status(Http.Status.NO_CONTENT);
    }

    @Transactional
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @IsAllowed(Operation.DELETE)
    public static Result deletePushedBranch(String ownerId, String projectName, Long id) {
        PushedBranch pushedBranch = PushedBranch.find.byId(id);
        if (pushedBranch != null) {
            pushedBranch.delete();
        }
        return ok();
    }

    public static Set<User> findAuthorsAndWatchers(@Nonnull Project project) {
        Set<User> allAuthors = new LinkedHashSet<>();

        allAuthors.addAll(getIssueUsers(project));
        allAuthors.addAll(getPostingUsers(project));
        allAuthors.addAll(getPullRequestUsers(project));
        allAuthors.addAll(getWatchedUsers(project));

        return allAuthors;
    }

    private static Set<User> getPostingUsers(Project project) {
        String postSql = "SELECT distinct author_id id FROM posting where project_id=" + project.id;
        return User.find.setRawSql(RawSqlBuilder.parse(postSql).create()).findSet();
    }

    private static Set<User> getIssueUsers(Project project) {
        String issueSql = "SELECT distinct author_id id FROM ISSUE where project_id=" + project.id;
        return User.find.setRawSql(RawSqlBuilder.parse(issueSql).create()).findSet();
    }

    private static Set<User> getPullRequestUsers(Project project) {
        String postSql = "SELECT distinct contributor_id id FROM pull_request where to_project_id=" + project.id;
        return User.find.setRawSql(RawSqlBuilder.parse(postSql).create()).findSet();
    }

    private static Set<User> getWatchedUsers(Project project) {
        String postSql = "SELECT distinct user_id id FROM watch where resource_type='PROJECT' and resource_id=" + project.id;
        return User.find.setRawSql(RawSqlBuilder.parse(postSql).create()).findSet();
    }

}
