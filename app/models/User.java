/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @Author Ahn Hyeok Jun
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
package models;

import java.text.SimpleDateFormat;
import java.util.*;

import javax.persistence.*;

import controllers.UserApp;
import models.enumeration.*;
import models.resource.GlobalResource;
import models.resource.Resource;
import models.resource.ResourceConvertible;

import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.crypto.hash.Sha256Hash;
import org.apache.shiro.util.ByteSource;
import play.data.format.Formats;
import play.data.validation.Constraints;
import play.data.validation.Constraints.*;
import play.db.ebean.Model;
import play.db.ebean.Transactional;
import utils.JodaDateUtil;
import utils.ReservedWordsValidator;

import com.avaje.ebean.Ebean;
import com.avaje.ebean.ExpressionList;
import com.avaje.ebean.Page;
import com.avaje.ebean.RawSql;
import com.avaje.ebean.RawSqlBuilder;

/**
 * User 클래스
 */
@Table(name = "n4user")
@Entity
public class User extends Model implements ResourceConvertible {
    private static final long serialVersionUID = 1L;

    public static final Model.Finder<Long, User> find = new Finder<>(Long.class, User.class);

    /**
     * 한 페이지에 보여줄 사용자 개수.
     */
    public static final int USER_COUNT_PER_PAGE = 30;

    /**
     * 사이트 관리자의 id값.
     */
    public static final Long SITE_MANAGER_ID = 1l;

    /**
     * 로그인ID 패턴
     */
    public static final String LOGIN_ID_PATTERN = "[a-zA-Z0-9-]+([_.][a-zA-Z0-9-]+)*";

    // TODO anonymous를 사용하는 것이아니라 향후 NullUser 패턴으로 usages들을 교체해야 함
    public static final User anonymous = new NullUser();

    /**
     * PK
     */
    @Id
    public Long id;

    /**
     * 화면에 보여줄 사용자 이름
     */
    public String name;

    /**
     * 로그인할 때 사용할 아이디
     */
    @Pattern(value = "^" + LOGIN_ID_PATTERN + "$", message = "user.wrongloginId.alert")
    @Required
    @ValidateWith(ReservedWordsValidator.class)
    public String loginId;

    /**
     * 비밀번호 수정할 때 기존 비밀번호 확인할 때 사용하는 값
     */
    @Transient
    public String oldPassword;

    /**
     * 비밀번호
     */
    public String password;

    /**
     * 비밀번호 암화할 할 때 사용하는 값
     */
    public String passwordSalt;

    /**
     * 이메일
     */
    @Constraints.Email(message = "user.wrongEmail.alert")
    public String email;

    /**
     * 로그인 정보를 기억할지 나타내는 값
     */
    public boolean rememberMe;

    @Enumerated(EnumType.STRING)
    public UserState state;

    @Formats.DateTime(pattern = "yyyy-MM-dd")
    public Date lastStateModifiedDate;

    /**
     * 계정 생성일
     */
    @Formats.DateTime(pattern = "yyyy-MM-dd")
    public Date createdDate;

    /**
     * 프로젝트에서 사용자의 역할을 나타내는 값
     *
     * 해당 프로젝트의 관리자 혹은 멤버일 수 있다.
     */
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    public List<ProjectUser> projectUser;

    /**
     * 멤버 등록 요청한 프로젝트
     */
    @ManyToMany(cascade = CascadeType.ALL)
    @JoinTable(name = "user_enrolled_project", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "project_id"))
    public List<Project> enrolledProjects;

    @ManyToMany(cascade = CascadeType.ALL)
    @JoinTable(name = "user_enrolled_organization", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "organization_id"))
    public List<Organization> enrolledOrganizations;

    @ManyToMany(mappedBy = "receivers")
    @OrderBy("created DESC")
    public List<NotificationEvent> notificationEvents;

    /**
     * 사용자로 인식할 수 있는 추가 이메일
     *
     * 한 사용자가 여러 이메일을 사용할 경우, 해당 이메일로도 사용자를 인식할 때 사용한다. {@link #email}은 대표 이메일로 사용한다.
     *
     * 추가 이메일 목록 중에 하나를 {@link #email}로 설정할 수 있으며 그때는 {@link #emails}에서 빠지고 {@link #email}로 바뀐다.
     */
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    public List<Email> emails;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    public RecentlyVisitedProjects recentlyVisitedProjects;

    @OneToMany(mappedBy = "user")
    public List<Mention> mentions;

    /**
     * The user's preferred language code which can be recognized by {@link play.api.i18n.Lang#get},
     * such as "ko", "en-US" or "ja". This field is used as a language for notification mail.
     */
    public String lang;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    public List<OrganizationUser> organizationUsers;

    public User() {
    }

    public User(Long id) {
        this.id = id;
    }

    /**
     * 완료일을 yyyy-MM-dd 형식의 문자열로 변환한다.
     *
     * view에서 노출하기 위한 용도로 사용한다.
     *
     * @return
     */
    public String getDateString() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
        return sdf.format(this.createdDate);
    }

    /**
     * 자신이 속한 프로젝트 목록을 반환한다.
     *
     * @return
     */
    public List<Project> myProjects(String orderString) {
        return Project.findProjectsByMemberWithFilter(id, orderString);
    }

    /**
     * 사용자를 추가한다.
     *
     * 사용자 추가시 생성일을 설정하고 PK를 반환한다.
     *
     * @param user
     * @return
     */
    public static Long create(User user) {
        user.createdDate = JodaDateUtil.now();
        user.save();
        return user.id;
    }

    /**
     * 로그인 아이디로 사용자를 조회한다.
     *
     * 사용자가 없으면 {@link #anonymous} 객체를 반환한다.
     *
     * @param loginId
     * @return
     */
    public static User findByLoginId(String loginId) {
        User user = find.where().ieq("loginId", loginId).findUnique();
        if (user == null) {
            return anonymous;
        }
        else {
            return user;
        }
    }

    /**
     *
     * Find a user by email account.
     * - find a user with a given email account or who has the email account  as one of sub email accounts.
     * - If no user matched up with the given email account, then return new {@link models.NullUser}
     * after setting the email account to the object.
     *
     * @param email
     * @return
     */
    public static User findByEmail(String email) {
        User user = find.where().eq("email", email).findUnique();
        if (user != null) {
            return user;
        }

        Email subEmail = Email.findByEmail(email, true);
        if (subEmail != null) {
            return subEmail.user;
        }

        User anonymous = new NullUser();
        anonymous.email = email;
        return anonymous;
    }

    /**
     * 로그인아이디로 존재하는 사용자인지를 확인한다.
     *
     * @param loginId
     * @return 사용자 존재여부
     */
    public static boolean isLoginIdExist(String loginId) {
        int findRowCount = find.where().ieq("loginId", loginId).findRowCount();
        return (findRowCount != 0);
    }

    /**
     * 전체 사용자 PK와 이름을 반환한다.
     *
     * @return
     */
    public static Map<String, String> options() {
        LinkedHashMap<String, String> options = new LinkedHashMap<>();
        for (User user : User.find.orderBy("name").findList()) {
            options.put(user.id.toString(), user.name);
        }
        return options;
    }

    /**
     * 익명사용자와 사이트 관리자를 제외한 사이트에 가입된 사용자 목록을 로그인 아이디로 정렬하여 Page객체로 반환한다.
     *
     * @param pageNum 해당 페이지
     * @param query {@code query}가 null이 아니면 {@code query}를 포함하고 있는 사용자 목록을 검색한다.
     * @return
     */
    public static Page<User> findUsers(int pageNum, String query, UserState state) {
        ExpressionList<User> el = User.find.where();
        el.ne("id",SITE_MANAGER_ID);
        el.ne("loginId",anonymous.loginId);
        el.eq("state", state);

        if(StringUtils.isNotBlank(query)) {
            el = el.disjunction();
            el = el.icontains("loginId", query).icontains("name", query);
            el.endJunction();
        }

        return el.findPagingList(USER_COUNT_PER_PAGE).getPage(pageNum);
    }

    /**
     * 사이트 관리자를 제외한 특정 프로젝트에 속한 사용자 목록을 반환한다.
     *
     * @param projectId
     * @return
     */
    public static List<User> findUsersByProject(Long projectId) {
        return find.where().eq("projectUser.project.id", projectId)
                .ne("projectUser.role.id", RoleType.SITEMANAGER.roleType()).orderBy().asc("name")
                .findList();
    }

    /**
     * 사용자의 아바타 아이디를 반환한다.
     * @return
     */
    @Transient
    public Long avatarId() {
        List<Attachment> attachments = Attachment.findByContainer(avatarAsResource());
        if (attachments.size() > 0) {
            return attachments.get(attachments.size() - 1).id;
        }
        else {
            return null;
        }
    }

    /**
     * 기존에 존재하는 email인지 확인한다.
     *
     * {@link Email}에 검증된 보조 이메일로 존재하는지 확인한다.
     *
     * @param emailAddress
     * @return email이 있으면 true / 없으면 false
     */
    public static boolean isEmailExist(String emailAddress) {
        User user = find.where().ieq("email", emailAddress).findUnique();
        return user != null || Email.exists(emailAddress, true);
    }

    /**
     * 사용자가 익명사용자인지 확인한다.
     * @return
     */
    public boolean isAnonymous() {
        return id == null || id.equals(anonymous.id);
    }

    /**
     * 로그인 아이디로 사용자를 조회하고 새 비밀번호를 암호화하여 설정한다.
     *
     * @param loginId
     * @param newPassword {@link User.passwordSalt}로 암호화하여 설정할 새 비밀번호
     */
    public static void resetPassword(String loginId, String newPassword) {
        User user = findByLoginId(loginId);
        user.password = new Sha256Hash(newPassword, ByteSource.Util.bytes(user.passwordSalt), 1024)
                .toBase64();
        user.save();
    }

    /**
     * 모델을 리소스 객체로 반환한다.
     *
     * 권한검사와 첨부파일 정보를 포함한다.
     *
     * @return
     */
    @Override
    public Resource asResource() {
        return new GlobalResource() {
            @Override
            public String getId() {
                return id.toString();
            }

            @Override
            public ResourceType getType() {
                return ResourceType.USER;
            }
        };
    }

    public Resource avatarAsResource() {
        return new GlobalResource() {
            @Override
            public String getId() {
                return id.toString();
            }

            @Override
            public ResourceType getType() {
                return ResourceType.USER_AVATAR;
            }
        };
    }

    /**
     * 사이트 관리자 여부를 확인한다.
     * @return
     */
    public boolean isSiteManager() {
        return SiteAdmin.exists(this);
    }

    public List<Project> getEnrolledProjects() {
        if (this.enrolledProjects == null) {
            this.enrolledProjects = new ArrayList<>();
        }
        return this.enrolledProjects;
    }

    public List<Organization> getEnrolledOrganizations() {
        if (this.enrolledOrganizations == null) {
            this.enrolledOrganizations = new ArrayList<>();
        }
        return this.enrolledOrganizations;
    }

    @Transactional
    public void addWatching(Project project) {
        Watch.watch(this, project.asResource());
    }

    @Transactional
    public void removeWatching(Project project) {
        Watch.unwatch(this, project.asResource());
    }

    public static boolean isWatching(Project project) {
        return Watch.isWatching(project.asResource());
    }

    public List<Project> getWatchingProjects() {
        return getWatchingProjects(null);
    }

    public List<Project> getWatchingProjects(String orderString) {
        List<String> projectIds = Watch.findWatchedResourceIds(this, ResourceType.PROJECT);
        List<Project> projects = new ArrayList<>();
        for (String id : projectIds) {
            projects.add(Project.find.byId(Long.valueOf(id)));
        }
        if (StringUtils.isBlank(orderString)) {
            return projects;
        }
        return Ebean.filter(Project.class).sort(orderString).filter(projects);
    }

    /**
     * {@code project}에 멤버 등록 요청을 추가한다.
     *
     * @param project
     */
    public void enroll(Project project) {
        getEnrolledProjects().add(project);
        this.update();
    }

    public void enroll(Organization organization) {
        getEnrolledOrganizations().add(organization);
        this.update();
    }

    /**
     * {@code project}에 보낸 멤버 등록 요청을 삭제한다.
     *
     * @param project
     */
    public void cancelEnroll(Project project) {
        getEnrolledProjects().remove(project);
        this.update();
    }

    public void cancelEnroll(Organization organization) {
        getEnrolledOrganizations().remove(organization);
        this.update();
    }

    /**
     * 현재 사용자가 {@code project}에 멤버 등록 요청을 보냈는지 확인한다.
     *
     * @param project
     * @return
     */
    public static boolean enrolled(Project project) {
        User user = UserApp.currentUser();
        if (user.isAnonymous()) {
            return false;
        }
        return user.getEnrolledProjects().contains(project);
    }

    public static boolean enrolled(Organization organization) {
        User user = UserApp.currentUser();
        if (user.isAnonymous()) {
            return false;
        }
        return user.getEnrolledOrganizations().contains(organization);
    }

    @Override
    public void delete() {
        for (Assignee assignee : Assignee.finder.where().eq("user.id", id).findList()) {
            assignee.delete();
        }
        super.delete();
    }

    public void changeState(UserState state) {
        this.state = state;
        lastStateModifiedDate = new Date();

        if (this.state == UserState.DELETED) {
            name = "DELETED";
            oldPassword = "";
            password = "";
            passwordSalt = "";
            email = "deleted-" + loginId + "@noreply.yobi.io";
            rememberMe = false;
            projectUser.clear();
            enrolledProjects.clear();
            notificationEvents.clear();
            for (Assignee assignee : Assignee.finder.where().eq("user.id", id).findList()) {
                for (Issue issue : assignee.issues) {
                    issue.assignee = null;
                    issue.update();
                }
                assignee.delete();
            }
        }

        update();
    }

    public String avatarUrl() {
        Long avatarId = avatarId();
        if (avatarId == null) {
            return UserApp.DEFAULT_AVATAR_URL;
        }
        else {
            return controllers.routes.AttachmentApp.getFile(avatarId).url();
        }
    }

    /**
     * {@code projectId} 에 해당하는 project 에 이슈를 작성한 모든 사용자 조회
     *
     * @param projectId
     * @return
     */
    public static List<User> findIssueAuthorsByProjectId(long projectId) {
        String sql = "select user.id, user.name, user.login_id from issue issue, n4user user where issue.author_id = user.id group by issue.author_id";
        return createUserSearchQueryWithRawSql(sql).where()
                .eq("issue.project_id", projectId)
                .findList();
    }

    /**
     * {@code projectId} 에 해당하는 project 에 코드-주고받기를 보낸 모든 사용자 조회
     *
     * @param projectId
     * @return
     */
    public static List<User> findPullRequestContributorsByProjectId(long projectId) {
        String sql = "SELECT user.id, user.name, user.login_id FROM pull_request pullrequest, n4user user WHERE pullrequest.contributor_id = user.id GROUP BY pullrequest.contributor_id";
        return createUserSearchQueryWithRawSql(sql).where()
                .eq("pullrequest.to_project_id", projectId)
                .findList();
    }

    private static com.avaje.ebean.Query<User> createUserSearchQueryWithRawSql(String sql) {
        RawSql rawSql = RawSqlBuilder.parse(sql).columnMapping("user.login_id", "loginId").create();
        return User.find.setRawSql(rawSql);
    }

    /**
     * {@code projectId} 에 해당하는 프로젝트에서 {@code roleType} 역할을 가지고 있는 사용자 목록을 조회한다.
     *
     * @param projectId
     * @param roleType
     * @return
     */
    public static List<User> findUsersByProject(Long projectId, RoleType roleType) {
        return find.where().eq("projectUser.project.id", projectId)
                .eq("projectUser.role.id", roleType.roleType()).orderBy().asc("name").findList();
    }

    public static List<User> findUsersByOrganization(Long organizationId, RoleType roleType) {
        return find.where().eq("organizationUsers.organization.id", organizationId)
                .eq("organizationUsers.role.id", roleType.roleType()).orderBy().asc("name").findList();
    }

    /**
     * 사용자가 가진 보조 이메일에 새로운 이메일 추가한다.
     *
     * @param email
     */
    public void addEmail(Email email) {
        email.save();
        emails.add(email);
    }

    /**
     * 사용자가 가진 보조 이메일 중에 {@code newEmail}값에 해당하는 이메일이 있는지 확인한다.
     *
     * @param newEmail
     * @return
     */
    public boolean has(String newEmail) {
        for (Email email : emails) {
            if (email.email.equals(newEmail)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 사용자가 가진 보조 이메일에서 {@code email}을 삭제한다.
     *
     * @param email
     */
    public void removeEmail(Email email) {
        emails.remove(email);
        email.delete();
    }

    public void visits(Project project) {
        this.recentlyVisitedProjects = RecentlyVisitedProjects.addNewVisitation(this, project);
        this.update();
    }


    public List<ProjectVisitation> getVisitedProjects(int size) {
        if(size < 1 || this.recentlyVisitedProjects == null) {
            return new ArrayList<>();
        }

        return this.recentlyVisitedProjects.findRecentlyVisitedProjects(size);
    }

    public List<Organization> getOrganizations(int size) {
        if(size < 1) {
            throw new IllegalArgumentException("the size should be bigger then 0");
        }
        List<Organization> orgs = new ArrayList<>();
        for(OrganizationUser ou : OrganizationUser.findByUser(this, size)) {
            orgs.add(ou.organization);
        }
        return orgs;
    }

    public void createOrganization(Organization organization) {
        OrganizationUser ou = new OrganizationUser();
        ou.user = this;
        ou.organization = organization;
        ou.role = Role.findByRoleType(RoleType.ORG_ADMIN);
        ou.save();

        this.add(ou);
        organization.add(ou);
        this.update();
    }

    private void add(OrganizationUser ou) {
        this.organizationUsers.add(ou);
    }
}
