package models;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

import javax.persistence.*;
import javax.servlet.ServletException;
import javax.validation.constraints.NotNull;

import com.avaje.ebean.Ebean;
import com.avaje.ebean.ExpressionList;
import models.enumeration.ResourceType;
import models.enumeration.RoleType;
import models.resource.Resource;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.ObjectNode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.joda.time.Duration;

import org.tmatesoft.svn.core.SVNException;
import play.data.validation.Constraints;
import play.db.ebean.Model;
import play.db.ebean.Transactional;
import playRepository.Commit;
import playRepository.GitRepository;
import playRepository.RepositoryService;
import utils.JodaDateUtil;

import com.avaje.ebean.Page;

/**
 * Project.
 */
@Entity
public class Project extends Model {
    private static final long serialVersionUID = 1L;
    public static Finder<Long, Project> find = new Finder<Long, Project>(Long.class, Project.class);

    /** 한 페이지에 보여줄 프로젝트 개수 */
    public static final int PROJECT_COUNT_PER_PAGE = 10;

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
    public Set<Tag> tags;

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
        return find.where().eq("owner", loginId).eq("name", projectName)
                .findUnique();
    }

    /**
     * 해당 프로젝트 존재 여부를 반환한다.
     *
     * {@code loginId} 와 {@code projectName} 으로 프로젝트 카운트를 가져오고 존재 여부를 반환한다.
     *
     * @param loginId 로그인 아이디
     * @param projectName 프로젝트 이름
     * @return 프로젝트가 존재하면 true, 존재하지 않으면 false
     */
    public static boolean exists(String loginId, String projectName) {
        int findRowCount = find.where().eq("owner", loginId)
                .eq("name", projectName).findRowCount();
        return (findRowCount != 0) ? true : false;
    }

    /**
     * 프로젝트 이름을 {@code projectName} 값으로 변경이 가능한지 여부를 반환한다.
     *
     * 프로젝트 정보(이름) 변경시 중복방지를 위해 사용한다.
     *
     * @param id 현재 프로젝트 id
     * @param owner 프로젝트 관리자 loginId
     * @param projectName 프로젝트 이름
     * @return 자신을 제외한 프로젝트 중 동일한 프로젝트 이름이 있으면 false, 없으면 true를 반환
     */
    public static boolean projectNameChangeable(Long id, String userName,
            String projectName) {
        int findRowCount = find.where().eq("name", projectName)
                .eq("owner", userName).ne("id", id).findRowCount();
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

    /**
     * {@code state} 별 프로젝트 카운트를 반환한다.
     *
     * all(모든) / public(공개) / private(비공개) 외의 조건이 들어여몬 0을 반환한다.
     *
     * @param state 프로젝트 상태(all/public/private)
     * @return 프로젝트 카운트
     */
    public static int countByState(String state) {
        if (state == "all") {
            return find.findRowCount();
        } else if (state == "public") {
            return find.where().eq("isPublic", true).findRowCount();
        } else if (state == "private") {
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
            GitRepository gitRepo = new GitRepository(owner, name);
            List<String> branches = RepositoryService.getRepository(this)
                    .getBranches();
            if (!branches.isEmpty()) {
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

    /**
     * 프로젝트의 저장소로부터 Readme 파일을 읽어 String으로 반환한다.
     * Exception 발생시 null을 반환한다.
     *
     * @return Readme
     */
    public String readme() {
        try {
            String realFileName = getReadmeFileName();
            return new String(RepositoryService.getRepository(this).getRawFile(realFileName));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 프로젝트 저장소로부터 파일(디렉토리) 정보를 가져오고 {@code baseFileName} 와 동일한 파일명을 찾아 반환한다.
     *
     * 같은 파일명이 존재하면 해당 파일명을 반환하고 없으면 {@code baseFileName} 을 반환한다.
     *
     * @return the readme file name
     * @throws IOException Signals that an I/O exception has occurred.
     * @throws GitAPIException the git api exception
     * @throws SVNException the sVN exception
     * @throws ServletException the servlet exception
     */
    private String getReadmeFileName() throws IOException, GitAPIException, SVNException, ServletException {
        String baseFileName = "README.md";
        ObjectNode objectNode = RepositoryService.getRepository(this).findFileInfo("/");
        List<JsonNode> nodes = objectNode.findValues("data");
        for(JsonNode node : nodes) {
            Iterator<String> fieldNames = node.getFieldNames();
            while(fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                if(fieldName.toLowerCase().equals(baseFileName.toLowerCase())) {
                    return fieldName;
                }
            }
        }
        return baseFileName;
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

    /**
     * Tags as resource.
     *
     * @return the resource
     */
    public Resource tagsAsResource() {
        return new Resource() {

            @Override
            public Long getId() {
                return id;
            }

            @Override
            public Project getProject() {
                return Project.this;
            }

            @Override
            public ResourceType getType() {
                return ResourceType.PROJECT_TAGS;
            }

        };
    }

    /**
     * As resource.
     *
     * @return the resource
     */
    public Resource asResource() {
        return new Resource() {

            @Override
            public Long getId() {
                return id;
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
     * 프로젝트 태그를 추가하고 성공여부를 반환한다.
     *
     * 태그가 이미 있을경우 false를 반환한다.
     * 태그가 없으면 추가하고 true를 반환한다.
     *
     * @param tag 신규 태그
     * @return 이미 태그가 있을 경우 false / 없으면 추가하고 true 반환
     */
    public Boolean tag(Tag tag) {
       if (tags.contains(tag)) {
            // Return false if the tag has been already attached.
            return false;
        }

        // Attach new tag.
        tags.add(tag);
        update();

        return true;
    }

    /**
     * 태그를 제거한다.
     *
     * 태그를 참조하고 있는 프로젝트가 없으면 해당 태그를 삭제하고
     * 참조하는 프로젝트가 있으면 태그 매핑정보를 업데이트한다.
     *
     * @param tag 삭제할 태그
     */
    public void untag(Tag tag) {
        tag.projects.remove(this);
        if (tag.projects.size() == 0) {
            tag.delete();
        } else {
            tag.update();
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
}
