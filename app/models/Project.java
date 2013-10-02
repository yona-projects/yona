package models;

import com.avaje.ebean.Ebean;
import com.avaje.ebean.ExpressionList;
import com.avaje.ebean.Page;
import com.avaje.ebean.RawSql;
import com.avaje.ebean.RawSqlBuilder;

import controllers.EnrollProjectApp;
import controllers.ProjectApp;
import controllers.routes;
import models.enumeration.RequestState;
import models.enumeration.ResourceType;
import models.enumeration.RoleType;
import models.resource.Resource;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.joda.time.Duration;
import org.tmatesoft.svn.core.SVNException;
import play.Logger;
import play.data.validation.Constraints;
import play.db.ebean.Model;
import play.db.ebean.Transactional;
import playRepository.*;
import utils.JodaDateUtil;

import javax.persistence.*;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.*;

/**
 * Project.
 */
@Entity
public class Project extends Model implements LabelOwner {
    private static final long serialVersionUID = 1L;
    public static final play.db.ebean.Model.Finder <Long, Project> find = new Finder<>(Long.class, Project.class);

    @Id
    public Long id;

    @Constraints.Required
    @Constraints.Pattern("^[-a-zA-Z0-9_]*$")
    @Constraints.MinLength(2)
    public String name;

    public String overview;
    /** 프로젝트에서 사용하는 vcs */
    public String vcs;
    public String siteurl;
    /** 프로젝트 관리자 loginId */
    public String owner;
    /** 프로젝트 공개여부(공개면 true) */
    public boolean isPublic;

    public Date createdDate;

    /** 프로젝트 이슈 **/
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
    public Set<Issue> issues;

    /** 프로젝트 멤버 */
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
    public List<ProjectUser> projectUser;

    /** 프로젝트 게시물 */
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
    public List<Posting> posts;

    /** 프로젝트 마일스톤 */
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
    public List<Milestone> milestones;

    /**
     * 마지막 등록된 이슈 번호
     *
     * TODO : 사용하는곳을 찾을 수 없습니다. 삭제시 데이터 싱크가 되고 있지 않습니다.
     */
    private long lastIssueNumber;
    /**
     * 마지막 등록된 게시물 번호
     *
     * TODO : 사용하는곳을 찾을 수 없습니다. 삭제시 데이터 싱크가 되고 있지 않습니다.
     */
    private long lastPostingNumber;

    @ManyToMany
    public Set<Label> labels;

    @ManyToOne
    public Project originalProject;

    @OneToMany(mappedBy = "originalProject")
    public List<Project> forkingProjects;

    @OneToMany(mappedBy = "project")
    public Set<Assignee> assignees;

    /**
     * 사용자에게 관심 프로젝트로 등록된 개수
     */
    public long watchingCount;
    public Date lastPushedDate;

    @ManyToMany(mappedBy = "enrolledProjects")
    public List<User> enrolledUsers;

    @OneToMany(cascade = CascadeType.REMOVE)
    public List<CodeComment> codeComments;

    /**
     * 신규 프로젝트를 생성한다.
     *
     * 프로젝트 생성시 사용한다.
     *
     * {@code siteurl}과 {@code createdDate}을 초기화하고 저장한다.
     * 프로젝트에 사이트 관리자의 Role을 추가한다.
     *
     * @param newProject 신규프로젝트
     * @return 생성된 프로젝트 {@code id}
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

    /**
     * 프로젝트 이름을 포함하는 프로젝트 목록을 반환한다.
     *
     * {@link Page} 형태의 프로젝트 목록 조회시 사용한다.
     *
     * 프로젝트명에 {@code name} 값이 포함된 프로젝트 정보를 {@link Page} 형태로 가져온다.
     *
     * @param name 프로젝트 이름
     * @param pageSize {@code pageSize}
     * @param pageNum {@code pageNum}
     * @return {@link Page} 형태의 프로젝트
     */
    public static Page<Project> findByName(String name, int pageSize,
                                           int pageNum) {
        return find.where().ilike("name", "%" + name + "%")
                .findPagingList(pageSize).getPage(pageNum);
    }

    /**
     * 프로젝트 관리자 {@code loginId} 와 {@code projectName} 으로 프로젝트 정보를 가져온다.
     *
     * 동일한 관리자 loginId와 {@code projectName} 으로 생성된 프로젝트는 Unique 하다.
     *
     * @param loginId 프로젝트 관리자 loginId
     * @param projectName 프로젝트 이름
     * @return 프로젝트
     */
    public static Project findByOwnerAndProjectName(String loginId, String projectName) {
        return find.where().ieq("owner", loginId).ieq("name", projectName)
                .findUnique();
    }

    /**
     * 해당 프로젝트 존재 여부를 반환한다.
     *
     * {@code loginId} 와 {@code projectName} 으로 프로젝트 카운트를 가져오고 존재 여부를 반환한다.
     *
     * @param loginId 로그인 아이디
     * @param projectName 프로젝트 이름 (대소문자를 구분하지 않음)
     * @return 프로젝트가 존재하면 true, 존재하지 않으면 false
     */
    public static boolean exists(String loginId, String projectName) {
        int findRowCount = find.where().ieq("owner", loginId)
                .ieq("name", projectName).findRowCount();
        return (findRowCount != 0) ? true : false;
    }

    /**
     * 프로젝트 이름을 {@code projectName} 값으로 변경이 가능한지 여부를 반환한다.
     *
     * 프로젝트 정보(이름) 변경시 중복방지를 위해 사용한다.
     *
     * @param id 현재 프로젝트 id
     * @param userName 프로젝트 관리자 loginId
     * @param projectName 프로젝트 이름
     * @return 자신을 제외한 프로젝트 중 동일한 프로젝트 이름이 있으면 false, 없으면 true를 반환
     */
    public static boolean projectNameChangeable(Long id, String userName,
                                                String projectName) {
        int findRowCount = find.where().ieq("name", projectName)
                .ieq("owner", userName).ne("id", id).findRowCount();
        return (findRowCount == 0) ? true : false;
    }

    /**
     *
     * {@code userId}로 사용자가 속한 프로젝트들 중에서 해당 사용자가 유일한 관리자인 프로젝트가 있는지 검사하고 그 프로젝트들의 목록을 반환한다.
     *
     * 사이트관리자가 사용자 삭제시 사용한다.
     *
     * @param userId the user id
     * @return {@code userId} 가 유일한 관리자인 프로젝트가 있으면 true, 없으면 false
     * @see {@link RoleType#MANAGER}
     */
    public static boolean isOnlyManager(Long userId) {
        List<Project> projects = find.select("id").select("name").where()
                .eq("projectUser.user.id", userId)
                .eq("projectUser.role.id", RoleType.MANAGER.roleType())
                .findList();

        Iterator<Project> iterator = projects.iterator();

        while (iterator.hasNext()) {
            Project project = iterator.next();
            if (ProjectUser.checkOneMangerPerOneProject(userId, project.id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code userId} 가 멤버로 있는 프로젝트 목록을 반환한다.
     *
     * @param userId the user id
     * @return {@code userId}의 프로젝트 목록
     */
    public static List<Project> findProjectsByMember(Long userId) {
        return find.where().eq("projectUser.user.id", userId).findList();
    }

    /**
     * {@code user} 가 owner가 아니고 멤버로 있는 프로젝트 목록을 반환한다.
     *
     * @param user 사용자
     * @return {@code user}의 프로젝트 목록
     */
    public static List<Project> findProjectsJustMemberAndNotOwner(User user) {
        return findProjectsJustMemberAndNotOwner(user, null);
    }

    /**
     * {@code user} 가 owner가 아니고 멤버로 있는 프로젝트 목록을 반환한다.
     * 반환되는 목록을 {@code orderString} 을 이용해서 정렬한다.
     *
     * @param user 사용자
     * @param orderString 정렬 정보
     * @return {@code user}의 프로젝트 목록
     */
    public static List<Project> findProjectsJustMemberAndNotOwner(User user, String orderString) {
        ExpressionList<Project> el = find.where()
                .eq("projectUser.user.id", user.id).ne("owner", user.loginId);
        if (StringUtils.isNotBlank(orderString)) {
            el.orderBy(orderString);
        }
        return el.findList();
    }


    /**
     * {@code userId} 가 멤버로 있는 프로젝트 목록을 {@code orderString} 에 따라 정렬하여 반환한다.
     *
     * {@code orderString} 이 null 일 경우 정렬하지 않고 반환한다.
     *
     * @param userId 유저 아이디
     * @param orderString 정렬방식
     * @return 정렬된 프로젝트 목록
     */
    public static List<Project> findProjectsByMemberWithFilter(Long userId, String orderString) {
        List<Project> userProjectList = find.where().eq("projectUser.user.id", userId).findList();
        if( orderString == null ){
            return userProjectList;
        }

        List<Project> filteredList = Ebean.filter(Project.class).sort(orderString).filter(userProjectList);

        return filteredList;
    }

    public static List<Project> findProjectsCreatedByUser(String loginId, String orderString) {
        List<Project> userProjectList = find.where().eq("owner", loginId).findList();
        if( orderString == null ){
            return userProjectList;
        }

        List<Project> filteredList = Ebean.filter(Project.class).sort(orderString).filter(userProjectList);

        return filteredList;
    }
    /**
     * {@code state} 별 프로젝트 카운트를 반환한다.
     *
     * all(모든) / public(공개) / private(비공개) 외의 조건이 들어여몬 0을 반환한다.
     *
     * @param state 프로젝트 상태(all/public/private)
     * @return 프로젝트 카운트
     */
    public static int countByState(String state) {
        if (state.equals("all")) {
            return find.findRowCount();
        } else if (state.equals("public")) {
            return find.where().eq("isPublic", true).findRowCount();
        } else if (state.equals("private")) {
            return find.where().eq("isPublic", false).findRowCount();
        } else {
            return 0;
        }
    }

    /**
     * 프로젝트의 마지막 업데이트일을 반환한다.
     *
     * 프로젝트의 vcs 타입(git, svn)에 따라 branch를 조회하여 branch가 하나 이상 존재하면 commit history(head revision)을 가져오고 commit history의 updateDate를 반환한다.
     * branch가 존재하지 않거나 Exception 발생시 {@code createDate} 를 반환한다.
     *
     * TODO : 현재는 GitRepository의 히스토리만 가져오게 구현되어 있음. svn은?
     *
     * @return 마지막 업데이트일
     */
    public Date lastUpdateDate() {
        try {
            PlayRepository repository = RepositoryService.getRepository(this);
            List<String> branches = repository.getBranches();
            if (!branches.isEmpty() && repository instanceof GitRepository) {
                GitRepository gitRepo = new GitRepository(owner, name);
                List<Commit> history = gitRepo.getHistory(0, 2, "HEAD");
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

    /**
     *
     * 프로젝트 마지막 업데이트일을 {@link Duration} 객체로 반환한다. ( 지속시간 )
     *
     * @return 마지막 업데이트일 지속시간
     */
    public Duration ago() {
        return JodaDateUtil.ago(lastUpdateDate());
    }

    public Duration lastPushedDateAgo(){
        if( this.lastPushedDate == null){
            return null;
        }
        return JodaDateUtil.ago(this.lastPushedDate);
    }

    /**
     * 프로젝트의 저장소로부터 Readme 파일을 읽어 String으로 반환한다.
     * Exception 발생시 null을 반환한다.
     *
     * @return Readme
     */
    public String readme() {
        try {
            return new String(RepositoryService.getRepository(this).getRawFile
                    (getReadmeFileName()), "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 프로젝트의 README 파일 이름을 얻는다. 없다면 {@code null}을 반환한다.
     *
     * 코드저장소 루트 디렉토리에서 다음의 순서로 파일을 찾아서 발견되는대로 그 이름을 반환한다.
     *
     * - README.md
     * - readme.md
     *
     * @return the readme file name or {@code null} if the file does not exist
     * @throws IOException Signals that an I/O exception has occurred.
     * @throws GitAPIException the git api exception
     * @throws SVNException the sVN exception
     * @throws ServletException the servlet exception
     */
    public String getReadmeFileName() throws IOException, GitAPIException, SVNException, ServletException {
        String baseFileName = "README.md";

        PlayRepository repo = RepositoryService.getRepository(this);

        if (repo.isFile(baseFileName)) {
            return baseFileName;
        }

        if (repo.isFile(baseFileName.toLowerCase())) {
            return baseFileName.toLowerCase();
        }

        // SVN은 /trunk/readme.md 까지 찾아본다.
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

    /**
     * 마지막 이슈번호를 증가시킨다.
     *
     * 이슈 추가시 사용한다.
     *
     * @return {@code lastIssueNumber}
     */
    @Transactional
    public Long increaseLastIssueNumber() {
        lastIssueNumber++;
        update();
        return lastIssueNumber;
    }

    public void fixLastIssueNumber() {
        lastIssueNumber = Issue.finder.where().eq("project.id", id).order().desc("number").findList().get(0).number;
    }

    /**
     * 마지막 게시글번호를 증가시킨다.
     *
     * 게시글 추가시 사용한다.
     *
     * @return {@code lastPostingNumber}
     */
    @Transactional
    public Long increaseLastPostingNumber() {
        lastPostingNumber++;
        update();
        return lastPostingNumber;
    }

    public void fixLastPostingNumber() {
        lastPostingNumber = Posting.finder.where().eq("project.id", id).order().desc("number").findList().get(0).number;
    }

    /**
     * Labels as resource.
     *
     * @return the resource
     */
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

    /**
     * As resource.
     *
     * @return the resource
     */
    @Override
    public Resource asResource() {
        return new Resource() {

            @Override
            public String getId() {
                return id.toString();
            }

            @Override
            public Project getProject() {
                return null;
            }

            @Override
            public ResourceType getType() {
                return ResourceType.PROJECT;
            }

        };
    }

    /**
     * loginId로 관리자(Project owner) 정보를 가져온다.
     *
     * @param loginId the user id
     * @return the owner by name
     */
    public User getOwnerByLoginId(String loginId){
        return User.findByLoginId(loginId);
    }

    /**
     * 프로젝트 라벨를 추가하고 성공여부를 반환한다.
     *
     * 라벨가 이미 있을경우 false를 반환한다.
     * 라벨가 없으면 추가하고 true를 반환한다.
     *
     * @param label 신규 라벨
     * @return 이미 라벨가 있을 경우 false / 없으면 추가하고 true 반환
     */
    public Boolean attachLabel(Label label) {
        if (labels.contains(label)) {
            // Return false if the label has been already attached.
            return false;
        }

        // Attach new label.
        labels.add(label);
        update();

        return true;
    }

    /**
     * 라벨를 제거한다.
     *
     * 라벨를 참조하고 있는 프로젝트가 없으면 해당 라벨를 삭제하고
     * 참조하는 프로젝트가 있으면 라벨 매핑정보를 업데이트한다.
     *
     * @param label 삭제할 라벨
     */
    public void detachLabel(Label label) {
        label.projects.remove(this);
        if (label.projects.size() == 0) {
            label.delete();
        } else {
            label.update();
        }
    }

    /**
     * {@code user} 가 owner 인지 확인한다.
     *
     * @param user 사용자
     * @return owner 이면 true, 아니면 false
     */
    public boolean isOwner(User user) {
        return owner.toLowerCase().equals(user.loginId.toLowerCase());
    }

    /**
     * {@code owner} 와 '/', {@code name} 의 조합으로 String 을 반환한다.
     * @return 프로젝트명
     */
    public String toString() {
        return owner + "/" + name;
    }

    public List<ProjectUser> members() {
        return ProjectUser.findMemberListByProject(this.id);
    }

    /**
     * 이 프로젝트가 포크 프로젝트인지 확인한다.
     *
     * @return
     */
    public boolean isFork() {
        return this.originalProject != null;
    }

    /**
     * 이 프로젝트를 포크 받은 프로젝트가 있는지 확인한다.
     *
     * @return
     */
    public boolean hasForks() {
        return this.forkingProjects.size() > 0;
    }

    /**
     * 포크 프로젝트 목록을 반환한다.
     *
     * @return
     */
    public List<Project> getForkingProjects() {
        if(this.forkingProjects == null) {
            this.forkingProjects = new ArrayList<>();
        }
        return forkingProjects;
    }

    /**
     * 포크를 추가한다.
     *
     * @param forkProject
     */
    public void addFork(Project forkProject) {
        getForkingProjects().add(forkProject);
        forkProject.originalProject = this;
    }

    /**
     * {@code loginId}에 해당하는 유저가 {@code originalProject}를 포크 받은 프로젝트를 반환한다.
     *
     * when: fork 할 때 기존에 포크 받은 프로젝트가 있는지 확인할 때 사용한다.
     *
     * @param loginId
     * @param originalProject
     * @return
     */
    public static Project findByOwnerAndOriginalProject(String loginId, Project originalProject) {
        return find.where()
                .eq("originalProject", originalProject)
                .eq("owner", loginId)
                .findUnique();
    }

    /**
     * 포크 프로젝트를 삭제한다.
     *
     * when: 프로젝트를 삭제할 때 해당 프로젝트가 포크 프로젝트라면 원본 프로젝트의 포크 프로젝트 목록에서
     * 해당 프로젝트를 삭제한다.
     */
    public void deleteFork() {
        if(this.originalProject != null) {
            this.originalProject.deleteFork(this);
        }
    }

    /**
     * {@code project}를 포크 목록에서 삭제한다.
     *
     * @param project
     */
    private void deleteFork(Project project) {
        getForkingProjects().remove(project);
        project.originalProject = null;
    }

    public void upWatcingCount() {
        this.watchingCount++;
    }

    public void downWathcingCount() {
        this.watchingCount--;
    }

    /**
     * 데이터 교정용 메서드로, 원본이 삭제된 포크 프로젝트일 경우에 포크 프로젝트를 원본 프로젝트로 만든다.
     *
     * when: 프로젝트 조회할 때 사용.
     *
     */
    public void fixInvalidForkData() {
        if(originalProject != null) {
            try {
                // originalProject의 속성에 접근해봐야 알 수 있다.
                String owner = originalProject.owner;
            } catch (EntityNotFoundException e) {
                originalProject = null;
                super.update();
            }
        }
    }

    /**
     * 프로젝트 멤버 등록 요청중에서 이미 프로젝트 멤버로 등록된 유저의 요청은 삭제한다.
     *
     * when: 프로젝트 멤버 설정 화면을 보여줄 때 실행합니다.
     *
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
            EnrollProjectApp
                    .addNotificationEvent(this, user, RequestState.ACCEPT,
                            routes.ProjectApp.project(owner, name));
        }
    }

    /**
     * 프로젝트 상태(공개/비공개)
     */
    public enum State {
        PUBLIC, PRIVATE, ALL
    }

    /**
     * <p>프로젝트를 삭제한다.</p>
     *
     * <p>{@link play.db.ebean.Model#delete()}를 override해서 이 메소드를 구현한 이유는,
     * {@link #assignees}를 삭제하기 위해서이며, {@code Cascading.REMOVE}로 삭제 가능함에도 굳이 직접
     * 삭제하는 것은 cascading을 설정한 상태에서 프로젝트 삭제시 발생하는 다음의 예외를 피하기 위함이다.</p>
     *
     * <pre>Parameter "#1" is not set; SQL statement: delete from issue_comment where (issue_id in (?) [90012-168]]</pre>
     *
     * <p>이것은 Ebean의 버그로 알려져있다.</p>
     *
     * @see <a href="http://www.avaje.org/bugdetail-420.html">
     *     BUG 420 : SQLException with CascadeType.REMOVE</a>
     */
    @Override
    public void delete() {
        deleteFork();
        deletePullRequests();

        if(this.hasForks()) {
            for(Project fork : forkingProjects) {
                fork.deletePullRequests();
                fork.deleteOriginal();
                fork.update();
            }
        }

        for (Assignee assignee : assignees) {
            assignee.delete();
        }

        for (Label label : labels) {
            label.delete(this);
            label.update();
        }

        for(IssueLabel label : IssueLabel.findByProject(this)) {
            label.delete();
        }

        super.delete();
    }

    private void deleteOriginal() {
        this.originalProject = null;
    }

    /**
     * 이 프로젝트에서 보낸 코드 요청 이 프로젝트가 받은 코드 요청을 삭제한다.
     *
     * when: 프로젝트를 삭제할 떄 관련 코드 요청을 삭제할 때 사용한다.
     */
    private void deletePullRequests() {
        List<PullRequest> sentPullRequests = PullRequest.findSentPullRequests(this);
        for(PullRequest pullRequest : sentPullRequests) {
            pullRequest.delete();
        }

        List<PullRequest> allReceivedRequests = PullRequest.allReceivedRequests(this);
        for(PullRequest pullRequest : allReceivedRequests) {
            pullRequest.delete();
        }
    }

    /**
     * {@code loginId}와 {@code projectName}으로 새 프로젝트 이름을 생성한다.
     *
     * 기존에 해당 프로젝트와 동일한 이름을 가진 프로젝트가 있다면 프로젝트 이름 뒤에 -1을 추가한다.
     * '프로젝트이름-1'과 동일한 프로젝트가 있다면 뒤에 숫자를 계속 증가시킨다.
     *
     * @param loginId
     * @param projectName
     * @return
     */
    private static String newProjectName(String loginId, String projectName) {
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
     * {@code project}의 {@code user}의 복사본 프로젝트를 만든다.
     * 이때 프로젝트의 이름은 {@link #newProjectName(String, String)}을 사용한다.
     *
     * when: 프로젝트 복사 폼과 복사 폼 처리에서 사용한다.
     *
     * @param project
     * @param user
     * @return
     * @see #newProjectName(String, String)
     */
    public static Project copy(Project project, User user) {
        Project copyProject = new Project();
        copyProject.name = newProjectName(user.loginId, project.name);
        copyProject.overview = project.overview;
        copyProject.vcs = project.vcs;
        copyProject.owner = user.loginId;
        copyProject.isPublic = project.isPublic;
        return copyProject;
    }

    @Override
    public Set<Label> getLabels() {
        return labels;
    }

    /**
     * {@code loginId} 가 owner 가 아니고 멤버로 있는 프로젝트 갯수를 반환한다.
     *
     * @param loginId loginId
     * @return {@code loginId} 가 owner 가 아니고 멤버로 있는 프로젝트 갯수
     */
    public static int countProjectsJustMemberAndNotOwner(String loginId) {
        return find.where().eq("projectUser.user.loginId", loginId)
                .ne("owner", loginId).findRowCount();
    }

    /**
     * {@code loginId} 가 생성한 프로젝트 갯수를 반환한다.
     *
     * @param loginId loginId
     * @return {@code loginId} 가 생성한 프로젝트 갯수
     */
    public static int countProjectsCreatedByUser(String loginId) {
        return find.where().eq("owner", loginId).findRowCount();
    }
}
