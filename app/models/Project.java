/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp.
 * https://yona.io
 **/
package models;

import com.avaje.ebean.Ebean;
import com.avaje.ebean.ExpressionList;
import com.avaje.ebean.Page;
import com.avaje.ebean.RawSqlBuilder;
import models.enumeration.ProjectScope;
import models.enumeration.RequestState;
import models.enumeration.ResourceType;
import models.enumeration.RoleType;
import models.resource.GlobalResource;
import models.resource.Resource;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.tmatesoft.svn.core.SVNException;
import play.data.validation.Constraints;
import play.db.ebean.Model;
import play.db.ebean.Transactional;
import playRepository.*;
import utils.CacheStore;
import utils.FileUtil;
import utils.JodaDateUtil;
import validation.ExConstraints;

import javax.annotation.Nonnull;
import javax.persistence.*;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.*;

import static utils.CacheStore.getProjectCacheKey;
import static utils.CacheStore.projectMap;
import static utils.HttpUtil.decodeUrlString;

@Entity
public class Project extends Model implements LabelOwner {
    private static final long serialVersionUID = 1L;
    public static final play.db.ebean.Model.Finder <Long, Project> find = new Finder<>(Long.class, Project.class);

    private static final int DRAFT_TIME_IN_MILLIS = 1000 * 60 * 60;

    @Id
    public Long id;

    @Constraints.Required
    @Constraints.Pattern("^[a-zA-Z0-9-_\\.가-힣]+$")
    @ExConstraints.Restricted({".", "..", ".git"})
    public String name;

    public String overview;
    public String vcs;
    public String siteurl;
    public String owner;

    public Date createdDate;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
    public Set<Issue> issues;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
    public List<ProjectUser> projectUser;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
    public List<Posting> posts;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
    public List<Milestone> milestones;

    /** Project Notification */
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
    public List<UserProjectNotification> notifications;

    private long lastIssueNumber;

    private long lastPostingNumber;

    public boolean isCodeAccessibleMemberOnly;

    @ManyToMany
    public Set<Label> labels;

    @ManyToOne
    public Project originalProject;

    @OneToMany(mappedBy = "originalProject")
    public List<Project> forkingProjects;

    @OneToMany(mappedBy = "project")
    public Set<Webhook> webhooks;

    @OneToMany(mappedBy = "project")
    public Set<Assignee> assignees;

    public Date lastPushedDate;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
    public List<PushedBranch> pushedBranches;

    @ManyToMany(mappedBy = "enrolledProjects")
    public List<User> enrolledUsers;

    @OneToMany(cascade = CascadeType.REMOVE)
    public List<CommitComment> codeComments;

    @OneToMany(cascade = CascadeType.REMOVE)
    public List<CommentThread> commentThreads;

    public Integer defaultReviewerCount = 1;

    public boolean isUsingReviewerCount;

    @ManyToOne
    public Organization organization;

    @Enumerated(EnumType.STRING)
    public ProjectScope projectScope;

    @OneToOne(mappedBy = "project", cascade = CascadeType.ALL)
    public ProjectMenuSetting menuSetting;

    private String previousOwnerLoginId;
    private String previousName;
    private Long previousNameChangedTime;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
    public List<IssueLabel> issueLabels;

    /**
     * @see {@link User#SITE_MANAGER_ID}
     * @see {@link RoleType#SITEMANAGER}
     */
    public static Long create(Project newProject) {
        newProject.siteurl = "http://localhost:9000/" + newProject.name;
        newProject.createdDate = new Date();
        newProject.save();
        ProjectUser.assignRole(User.SITE_MANAGER_ID, newProject.id,
                RoleType.SITEMANAGER);
        return newProject.id;
    }

    public static Page<Project> findByName(String name, int pageSize,
                                           int pageNum) {
        if(StringUtils.isBlank(name)){
            return find.where().order().desc("createdDate").findPagingList(pageSize).getPage(pageNum);
        }

        return find.where().ilike("name", "%" + decodeUrlString(name) + "%")
                .findPagingList(pageSize).getPage(pageNum);
    }

    public static Project findByOwnerAndProjectName(String loginId, String projectName) {
        String key = getProjectCacheKey(loginId, projectName);
        Long projectId = CacheStore.projectMap.get(key);
        if(projectId == null || projectId == 0){
            Project project= find.where().ieq("owner", decodeUrlString(loginId)).ieq("name", decodeUrlString(projectName))
                    .findUnique();
            if( project == null) {
                project = findByPreviousPlaceOf(decodeUrlString(loginId), decodeUrlString(projectName));
            }
            if(project != null){
                CacheStore.projectMap.put(key, project.id);
            }
            return project;
        } else {
            return find.byId(projectId);
        }
    }

    public static List<Project> findByOwner(String loginId) {
        return find.where().ieq("owner", decodeUrlString(loginId)).orderBy("name asc").findList();
    }

    public Set<User> findAuthors() {
        Set<User> allAuthors = new LinkedHashSet<>();
        allAuthors.addAll(getIssueUsers());
        allAuthors.addAll(getPostingUsers());
        allAuthors.addAll(getPullRequestUsers());

        return allAuthors;
    }

    public Set<User> findAuthorsAndWatchers() {
        Set<User> allAuthors = new LinkedHashSet<>();
        allAuthors.addAll(findAuthors());
        allAuthors.addAll(getWatchedUsers());

        return allAuthors;
    }

    private Set<User> getIssueUsers() {
        String issueSql = "select distinct author_id id from issue where project_id=" + this.id;
        return User.find.setRawSql(RawSqlBuilder.parse(issueSql).create()).findSet();
    }

    private Set<User> getPostingUsers() {
        String postSql = "SELECT distinct author_id id FROM posting where project_id=" + this.id;
        return User.find.setRawSql(RawSqlBuilder.parse(postSql).create()).findSet();
    }

    private Set<User> getPullRequestUsers() {
        String postSql = "SELECT distinct contributor_id id FROM pull_request where to_project_id=" + this.id;
        return User.find.setRawSql(RawSqlBuilder.parse(postSql).create()).findSet();
    }

    public Set<User> getWatchedUsers() {
        String postSql = "SELECT distinct user_id id FROM watch where resource_type='PROJECT' and resource_id=" + this.id;
        return User.find.setRawSql(RawSqlBuilder.parse(postSql).create()).findSet();
    }

    public boolean hasMember(User user) {
        if (user.isMemberOf(this) ||
                user.isManagerOf(this) ||
                user.isSiteManager()) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean exists(String loginId, String projectName) {
        int findRowCount = find.where().ieq("owner", loginId)
                .ieq("name", projectName).findRowCount();
        return (findRowCount != 0);
    }

    public static boolean projectNameChangeable(Long id, String userName,
                                                String projectName) {
        int findRowCount = find.where().ieq("name", decodeUrlString(projectName))
                .ieq("owner", userName).ne("id", id).findRowCount();
        return (findRowCount == 0);
    }

    /**
     * @see {@link RoleType#MANAGER}
     */
    public static boolean isOnlyManager(Long userId) {
        List<Project> projects = find.select("id").select("name").where()
                .eq("projectUser.user.id", userId)
                .eq("projectUser.role.id", RoleType.MANAGER.roleType())
                .findList();

        for (Project project : projects) {
            if (ProjectUser.checkOneMangerPerOneProject(userId, project.id)) {
                return true;
            }
        }
        return false;
    }

    public static List<Project> findProjectsByMember(Long userId) {
        return find.where().eq("projectUser.user.id", userId).findList();
    }

    public static List<Project> findProjectsJustMemberAndNotOwner(User user) {
        return findProjectsJustMemberAndNotOwner(user, null);
    }

    public static List<Project> findProjectsJustMemberAndNotOwner(User user, String orderString) {
        ExpressionList<Project> el = find.where()
                .eq("projectUser.user.id", user.id)
                .ne("projectUser.role.id", RoleType.SITEMANAGER.roleType())
                .ne("owner", user.loginId);
        if (StringUtils.isNotBlank(orderString)) {
            el.orderBy(orderString);
        }
        return el.findList();
    }


    public static List<Project> findProjectsByMemberWithFilter(Long userId, String orderString) {
        List<Project> userProjectList = find.where().eq("projectUser.user.id", userId).findList();
        if( orderString == null ){
            return userProjectList;
        }

        return Ebean.filter(Project.class).sort(orderString).filter(userProjectList);
    }

    public static List<Project> findProjectsCreatedByUser(String loginId, String orderString) {
        if( orderString == null ){
            return find.where().eq("owner", loginId).orderBy("createdDate desc").findList();
        } else {
            return find.where().eq("owner", loginId).orderBy(orderString).findList();
        }

    }

    public static List<Project> findProjectsCreatedByUserAndScope(String loginId, ProjectScope projectScope, String orderString) {
        return find.where().eq("owner", loginId)
                .eq("projectScope", projectScope)
                .orderBy(orderString).findList();
    }

    public Date lastUpdateDate() {
        try {
            PlayRepository repository = RepositoryService.getRepository(this);
            List<String> branches = repository.getRefNames();
            if (!branches.isEmpty() && repository instanceof GitRepository) {
                GitRepository gitRepo = new GitRepository(owner, name);
                List<Commit> history = gitRepo.getHistory(0, 2, "HEAD", null);
                if(history == null) {
                    return this.createdDate;
                }
                return history.get(0).getAuthorDate();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NoHeadException e) {
            e.printStackTrace();
        } catch (GitAPIException e) {
            e.printStackTrace();
        } catch (UnsupportedOperationException e) {
            e.printStackTrace();
        } catch (ServletException e) {
            e.printStackTrace();
        }
        return this.createdDate;
    }

    public String defaultBranch() {
        try {
            return RepositoryService.getRepository(this).getDefaultBranch();
        } catch (Exception ignored) {
        }
        return "HEAD";
    }

    public Duration ago() {
        return JodaDateUtil.ago(lastUpdateDate());
    }

    public Duration lastPushedDateAgo(){
        if( this.lastPushedDate == null){
            return null;
        }
        return JodaDateUtil.ago(this.lastPushedDate);
    }

    public String readme() {
        try {
            byte[] bytes = RepositoryService.getRepository(this)
                    .getRawFile("HEAD", getReadmeFileName());
            return new String(bytes, FileUtil.detectCharset(bytes));
        } catch (Exception e) {
            return null;
        }
    }

    public String getIssueTemplate() {
        try {
            byte[] bytes = RepositoryService.getRepository(this)
                    .getRawFile("HEAD", "ISSUE_TEMPLATE.md");
            return new String(bytes, FileUtil.detectCharset(bytes));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * @return the readme file name or {@code null} if the file does not exist
     * @throws IOException Signals that an I/O exception has occurred.
     * @throws GitAPIException the git api exception
     * @throws SVNException the sVN exception
     * @throws ServletException the servlet exception
     */
    public String getReadmeFileName() throws IOException, SVNException, ServletException {
        String baseFileName = "README.md";

        PlayRepository repo = RepositoryService.getRepository(this);

        if (repo.isFile(baseFileName)) {
            return baseFileName;
        }

        if (repo.isFile(baseFileName.toLowerCase())) {
            return baseFileName.toLowerCase();
        }

        if(repo instanceof SVNRepository) {
            baseFileName = "/trunk/" + baseFileName;
            if(repo.isFile(baseFileName)) {
                return baseFileName;
            }

            baseFileName = baseFileName.toLowerCase();
            if(repo.isFile(baseFileName)) {
                return baseFileName;
            }
        }

        return null;
    }

    private boolean isLastIssueNumberCorrect() {
        return issues == null || lastIssueNumber >= issues.size();
    }

    private Long getLastIssueNumber() {
        if (isLastIssueNumberCorrect()) {
            return lastIssueNumber;
        }

        Issue issue = Issue.finder.where().eq("project.id", id).order().desc("number").findList().get(0);
        issue.refresh();

        return issue.number == null ? 0L : issue.number;
    }

    private void setLastIssueNumber(Long number) {
        lastIssueNumber = number;
    }

    private boolean isLastPostingNumberCorrect() {
        return posts == null || lastPostingNumber >= posts.size();
    }

    private Long getLastPostingNumber() {
        if (isLastPostingNumberCorrect()) {
            return lastPostingNumber;
        }

        Posting posting = Posting.finder.where().eq("project.id", id).order().desc("number").findList().get(0);
        posting.refresh();

        return posting.number == null ? 0L : posting.number;
    }

    private void setLastPostingNumber(Long number) {
        lastPostingNumber = number;
    }

    /**
     * 마지막 이슈번호를 증가시킨다.
     *
     * 이슈 추가시 사용한다.
     *
     * @return {@code lastIssueNumber}
     */
    public static Long increaseLastIssueNumber(Long projectId) {
        Project project = find.byId(projectId);
        project.setLastIssueNumber(project.getLastIssueNumber() + 1);
        project.update();

        return project.lastIssueNumber;
    }

    public static void fixLastIssueNumber(Long projectId) {
        Project project = find.byId(projectId);
        project.refresh();
        project.setLastIssueNumber(project.getLastIssueNumber());
        project.update();
    }

    public static Long increaseLastPostingNumber(Long projectId) {
        Project project = find.byId(projectId);
        project.setLastPostingNumber(project.getLastPostingNumber() + 1);
        project.update();

        return project.lastPostingNumber;
    }

    public static void fixLastPostingNumber(Long projectId) {
        Project project = find.byId(projectId);
        project.refresh();
        project.setLastPostingNumber(project.getLastPostingNumber());
        project.update();
    }

    public Resource labelsAsResource() {
        return new Resource() {

            @Override
            public String getId() {
                return id.toString();
            }

            @Override
            public Project getProject() {
                return Project.this;
            }

            @Override
            public ResourceType getType() {
                return ResourceType.PROJECT_LABELS;
            }

        };
    }

    @Override
    public Resource asResource() {
        return new GlobalResource() {

            @Override
            public String getId() {
                return id.toString();
            }

            @Override
            public ResourceType getType() {
                return ResourceType.PROJECT;
            }

            @Override
            public Project getProject() {
                return Project.this;
            }

        };
    }

    public User getOwnerByLoginId(String loginId){
        return User.findByLoginId(loginId);
    }

    public Boolean attachLabel(Label label) {
        if (label.projects.contains(this)) {
            // Return false if the label has been already attached.
            return false;
        }

        // Attach new label. I don't know why labels.add(label) does not work.
        label.projects.add(this);
        label.update();

        return true;
    }

    public void detachLabel(Label label) {
        label.projects.remove(this);
        if (label.projects.size() == 0) {
            label.delete();
        } else {
            label.update();
        }
    }

    public boolean isOwner(User user) {
        return owner.toLowerCase().equals(user.loginId.toLowerCase());
    }

    public String toString() {
        return owner + "/" + name;
    }

    public List<ProjectUser> members() {
        return ProjectUser.findMemberListByProject(this.id);
    }

    /**
     * Return assignable users to this project and group of the project.
     *
     * If the project has no groups, it returns all project members.
     * If the project has a group and is private, it returns all project members and group admins.
     * If the project has a group and is protected or public, it returns all project and group members.
     */
    public List<User> getAssignableUsers() {
        return User.findUsersByProjectAndOrganization(this);
    }

    public List<User> getAssignableUsersAndAssignee(Issue issue) {
        List<User> users = getAssignableUsers();

        if (issue != null && issue.assignee != null && !users.contains(issue.assignee.user)) {
            users.add(issue.assignee.user);
        }

        return users;
    }

    public boolean isProjectOrOrganizationUser(User user) {
        return User.findUsersByProjectAndOrganization(this).contains(user);
    }

    public boolean isAssignableUser(User user) {
        return getAssignableUsers().contains(user);
    }

    public boolean isForkedFromOrigin() {
        return this.originalProject != null;
    }

    public boolean hasForks() {
        return this.forkingProjects.size() > 0;
    }

    public List<Project> getForkingProjects() {
        if(this.forkingProjects == null) {
            this.forkingProjects = new ArrayList<>();
        }
        return forkingProjects;
    }

    public void addFork(Project forkProject) {
        getForkingProjects().add(forkProject);
        forkProject.originalProject = this;
    }

    public static List<Project> findByOwnerAndOriginalProject(String loginId, Project originalProject) {
        return find.where()
                .eq("originalProject", originalProject)
                .eq("owner", loginId)
                .findList();
    }

    public void deleteFork() {
        if(this.originalProject != null) {
            this.originalProject.deleteFork(this);
        }
    }

    private void deleteFork(Project project) {
        getForkingProjects().remove(project);
        project.originalProject = null;
    }

    public void fixInvalidForkData() {
        if(originalProject != null) {
            try {
                String owner = originalProject.owner;
            } catch (EntityNotFoundException e) {
                originalProject = null;
                super.update();
            }
        }
    }

    /**
     * @see controllers.ProjectApp#members(String, String)
     */
    @Transactional
    public void cleanEnrolledUsers() {
        List<User> enrolledUsers = this.enrolledUsers;
        List<User> acceptedUsers = new ArrayList<>();
        List<ProjectUser> members = this.members();
        for(ProjectUser projectUser : members) {
            User user = projectUser.user;
            if(enrolledUsers.contains(user)) {
                acceptedUsers.add(user);
            }
        }
        for(User user : acceptedUsers) {
            user.cancelEnroll(this);
            NotificationEvent.afterMemberRequest(this, user, RequestState.ACCEPT);
        }
    }

    public void changeVCS() throws Exception {
        if(this.forkingProjects != null) {
            for(Project fork : forkingProjects) {
                fork.originalProject = null;
                fork.update();
            }
        }

        RepositoryService.deleteRepository(this);
        this.vcs = nextVCS();
        RepositoryService.getRepository(this).create();
        this.update();
    }

    public boolean isCodeAvailable() {
        return menuSetting == null || menuSetting.code;
    }

    /**
     * When a project is renamed or transferred, record it's previous location and change time
     *
     * @param project
     */
    public void recordRenameOrTransferHistoryIfLastChangePassed24HoursFrom(@Nonnull Project project) {
        if(isRenamedOrTransferredIn24Hours(project)) {
            this.previousNameChangedTime = DateTime.now().getMillis();
            this.previousName = project.name;
            this.previousOwnerLoginId = project.owner;
        }
    }

    private static boolean isRenamedOrTransferredIn24Hours(@Nonnull Project project) {
        return project.previousNameChangedTime == null || hasPassed24hoursFrom(project.previousNameChangedTime);
    }

    private static boolean hasPassed24hoursFrom(Long time) {
        return new Duration(DateTime.now().getMillis() - time).getStandardHours() > 24;
    }

    public enum State {
        PUBLIC, PRIVATE, ALL
    }

    /**
     * <pre>Parameter "#1" is not set; SQL statement: delete from issue_comment where (issue_id in (?) [90012-168]]</pre>
     *
     * @see <a href="http://www.avaje.org/bugdetail-420.html">
     *     BUG 420 : SQLException with CascadeType.REMOVE</a>
     */
    @Override
    public void delete() {
        CacheStore.refreshProjectMap();
        projectMap.remove(getProjectCacheKey(this.owner, this.name));
        deleteProjectTransfer();
        deleteFork();
        deleteCommentThreads();
        deletePullRequests();

        if(this.hasForks()) {
            for(Project fork : forkingProjects) {
                fork.deletePullRequests();
                fork.deleteOriginal();
                fork.update();
            }
        }


        // Issues must be deleted before issue labels because issues may refer
        // issue labels.
        for(Issue issue : issues) {
            issue.delete();
        }

        for(IssueLabelCategory category : IssueLabelCategory.findByProject(this)) {
            category.delete();
        }

        for (Assignee assignee : assignees) {
            assignee.delete();
        }

        for (Webhook webhook : webhooks) {
            webhook.delete();
        }

        for(Posting posting : posts) {
            posting.delete();
        }

        for (Label label : labels) {
            label.delete(this);
            label.update();
        }
        
        super.delete();
    }

    private void deleteProjectTransfer() {
        List<ProjectTransfer> pts = ProjectTransfer.findByProject(this);
        for(ProjectTransfer pt : pts) {
            pt.delete();
        }
    }

    private void deleteOriginal() {
        this.originalProject = null;
    }

    private void deletePullRequests() {
        List<PullRequest> sentPullRequests = PullRequest.findSentPullRequests(this);
        for(PullRequest pullRequest : sentPullRequests) {
            CommentThread.deleteByPullRequest(pullRequest);
            pullRequest.delete();
        }

        List<PullRequest> allReceivedRequests = PullRequest.allReceivedRequests(this);
        for(PullRequest pullRequest : allReceivedRequests) {
            CommentThread.deleteByPullRequest(pullRequest);
            pullRequest.delete();
        }
    }

    private void deleteCommentThreads() {
        for(CommentThread commentThread : this.commentThreads) {
            commentThread.delete();
        }
    }

    public static String newProjectName(String loginId, String projectName) {
        Project project = Project.findByOwnerAndProjectName(loginId, projectName);
        if(project == null) {
            return projectName;
        }

        for(int i = 1 ; ; i++) {
            String newProjectName = projectName + "-" + i;
            project = Project.findByOwnerAndProjectName(loginId, newProjectName);
            if(project == null) {
                return newProjectName;
            }
        }
    }

    /**
     * @see #newProjectName(String, String)
     */
    public static Project copy(Project project, String owner) {
        Project copyProject = new Project();
        copyProject.name = newProjectName(owner, project.name);
        copyProject.overview = project.overview;
        copyProject.vcs = project.vcs;
        copyProject.owner = owner;
        copyProject.projectScope = project.projectScope;
        copyProject.menuSetting = new ProjectMenuSetting(project.menuSetting);
        copyProject.menuSetting.project = copyProject;
        copyProject.menuSetting.save();

        return copyProject;
    }

    @Override
    public Set<Label> getLabels() {
        return labels;
    }

    public static int countProjectsJustMemberAndNotOwner(String loginId) {
        return find.where().eq("projectUser.user.loginId", loginId)
                .ne("owner", loginId).findRowCount();
    }

    public static int countProjectsCreatedByUser(String loginId) {
        return find.where().eq("owner", loginId).findRowCount();
    }

    public List<PushedBranch> getRecentlyPushedBranches() {
        return PushedBranch.find.where()
                            .eq("project", this)
                            .gt("pushedDate", DateTime.now().minusMillis(DRAFT_TIME_IN_MILLIS).toDate())
                            .findList();
    }

    public List<PushedBranch> getOldPushedBranches() {
        return PushedBranch.find.where()
                            .eq("project", this)
                            .lt("pushedDate", DateTime.now().minusMillis(DRAFT_TIME_IN_MILLIS).toDate())
                            .findList();
    }

    public boolean isGit() {
        return vcs.equals("GIT");
    }

    public List<Project> getAssociationProjects() {
        List<Project> projects = new ArrayList<>();
        projects.add(this);
        projects.addAll(forkingProjects);
        if(isForkedFromOrigin() && originalProject.menuSetting.code
                && originalProject.menuSetting.pullRequest) {
                projects.add(originalProject);
        }
        return projects;
    }

    public int getMaxNumberOfRequiredReviewerCount() {
        List<ProjectUser> members = ProjectUser.findMemberListByProject(this.id);
        if(members.size() > 1) {
            return members.size();
        } else {
            return 1;
        }
    }

    public int getWatchingCount() {
        Resource resource = this.asResource();
        return Watch.countBy(resource.getType(), resource.getId());
    }

    public boolean hasGroup() {
        return this.organization != null;
    }

    public boolean isPublic() {
        return projectScope == ProjectScope.PUBLIC;
    }

    public boolean isProtected() {
        return projectScope == ProjectScope.PROTECTED;
    }

    public boolean isPrivate() {
        return projectScope == ProjectScope.PRIVATE;
    }

    public String nextVCS() {
        if(this.vcs.equals(RepositoryService.VCS_GIT)) {
            return RepositoryService.VCS_SUBVERSION;
        } else {
            return RepositoryService.VCS_GIT;
        }
    }

    /**
     * Find project with previous owner and previous project name
     *
     * when to use:
     *  When specific project can't be found.
     *
     *  In some cases, it is reasonable to assume that project was moved or transferred.
     *  In that case, try this method.
     *
     *  This method is intended to be used at controllers.
     *
     * @param previousOwnerLoginid
     * @param previousName
     * @return
     */

    public static Project findByPreviousPlaceOf(String previousOwnerLoginid, String previousName) {
        List<Project> projects = find.where().ieq("previousOwnerLoginId", previousOwnerLoginid).ieq("previousName", previousName)
            .setOrderBy("previousNameChangedTime desc").findList();
        if(CollectionUtils.isEmpty(projects)){
            return null;
        }
        return projects.get(0);  // Choose latest
    }

    public boolean hasOldPlace(){
        if(StringUtils.isBlank(this.previousName)){
            return false;
        } else {
            return true;
        }
    }

    public String getOldPlace(){
        if(this.previousOwnerLoginId == null){
            return "";
        }

        if(hasOldPlace()){
            return this.previousOwnerLoginId + "/" + this.previousName;
        } else {
            return "";
        }
    }
}
