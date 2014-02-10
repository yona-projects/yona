/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
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

import actions.AnonymousCheckAction;

import com.avaje.ebean.ExpressionList;
import com.avaje.ebean.annotation.Transactional;

import models.*;
import models.enumeration.Operation;
import models.enumeration.UserState;

import org.apache.commons.lang.StringUtils;
import org.apache.shiro.crypto.RandomNumberGenerator;
import org.apache.shiro.crypto.SecureRandomNumberGenerator;
import org.apache.shiro.crypto.hash.Sha256Hash;
import org.apache.shiro.util.ByteSource;

import play.Configuration;
import play.Logger;
import play.data.Form;
import play.i18n.Messages;
import play.mvc.*;
import play.mvc.Http.Cookie;
import utils.AccessControl;
import utils.Constants;
import utils.HttpUtil;
import utils.ReservedWordsValidator;
import utils.ErrorViews;
import views.html.user.*;

import org.codehaus.jackson.node.ObjectNode;

import play.libs.Json;

import java.util.*;

import static play.data.Form.form;
import static play.libs.Json.toJson;

public class UserApp extends Controller {
    public static final String SESSION_USERID = "userId";
    public static final String SESSION_LOGINID = "loginId";
    public static final String SESSION_USERNAME = "userName";
    public static final String TOKEN = "yobi.token";
    public static final int MAX_AGE = 30*24*60*60;
    public static final String DEFAULT_AVATAR_URL
            = routes.Assets.at("images/default-avatar-128.png").url();
    public static final int MAX_FETCH_USERS = 1000;
    private static final int HASH_ITERATIONS = 1024;
    public static final int DAYS_AGO = 7;
    public static final int UNDEFINED = 0;
    public static final String DAYS_AGO_COOKIE = "daysAgo";
    public static final String DEFAULT_GROUP = "own";
    public static final String DEFAULT_SELECTED_TAB = "projects";

    /**
     * ajax 를 이용한 사용자 검색
     * 요청 헤더의 accept 파라미터에 application/json 값이 없으면 406 응답
     * 응답에 포함되는 데이터 수는 MAX_FETCH_USERS 로 제한된다
     * 입력 파라미터 query 가 부분매칭 되는 loginId 목록을 json 형태로 응답
     *
     * @param query 검색어
     * @return
     */
    public static Result users(String query) {
        if (!request().accepts("application/json")) {
            return status(Http.Status.NOT_ACCEPTABLE);
        }

        ExpressionList<User> el = User.find.select("loginId, name").where().disjunction();
        el.icontains("loginId", query);
        el.icontains("name", query);
        el.endJunction();

        int total = el.findRowCount();
        if (total > MAX_FETCH_USERS) {
            el.setMaxRows(MAX_FETCH_USERS);
            response().setHeader("Content-Range", "items " + MAX_FETCH_USERS + "/" + total);
        }

        List<Map<String, String>> users = new ArrayList<>();
        for (User user : el.findList()) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("<img class='mention_image' src='%s'>", user.avatarUrl()));
            sb.append(String.format("<b class='mention_name'>%s</b>", user.name));
            sb.append(String.format("<span class='mention_username'> @%s</span>", user.loginId));

            Map<String, String> userMap = new HashMap<>();
            userMap.put("info", sb.toString());
            userMap.put("loginId", user.loginId);
            users.add(userMap);
        }

        return ok(toJson(users));
    }

    /**
     * 로그인 폼으로 이동
     *
     * @return
     */
    public static Result loginForm() {
        String redirectUrl = request().getQueryString("redirectUrl");
        String loginFormUrl = routes.UserApp.loginForm().absoluteURL(request());
        String referer = request().getHeader("Referer");
        if(StringUtils.isEmpty(redirectUrl) && !StringUtils.equals(loginFormUrl, referer)) {
            redirectUrl = request().getHeader("Referer");
        }
        return ok(login.render("title.login", form(User.class), redirectUrl));
    }

    /**
     * 로그아웃
     * 로그인 유지 기능 해제
     * 메인으로 이동
     *
     * @return
     */
    public static Result logout() {
        processLogout();
        flash(Constants.SUCCESS, "user.logout.success");
        String redirectUrl = request().getHeader("Referer");
        return redirect(redirectUrl);
    }

    /**
     * 로그인 처리
     * 시스템 설정에서 가입승인 기능이 활성화 되어 있고 사용자 상태가 잠금상태(미승인?)라면 계정이 잠겼다는 메시지를 노출하고 로그인 폼으로 돌아감
     * 시스템 설정에서 가입승인 기능이 활성화 되어 있지 않다면, 사용자 상태가 잠금상태라도 로그인이 가능하다 (스펙확인 필요)
     * 요청의 정보로 사용자 인증에 성공하면 로그인쿠키를 생성하고 로그인유지하기가 선택되었다면, 로그인유지를 위한 쿠키를 별도로 생성한다
     * 인증에 실패하면 관련된 메시지를 노출하고 로그인 폼으로 돌아간다
     *
     * @return
     */
    public static Result login() {
        Form<User> userForm = form(User.class).bindFromRequest();
        if(userForm.hasErrors()) {
            return badRequest(login.render("title.login", userForm, null));
        }
        User sourceUser = form(User.class).bindFromRequest().get();

        Map<String, String[]> params = request().body().asFormUrlEncoded();
        String redirectUrl = HttpUtil.getFirstValueFromQuery(params, "redirectUrl");

        String loginFormUrl = routes.UserApp.loginForm().absoluteURL(request());
        loginFormUrl += "?redirectUrl=" + redirectUrl;

        if (isUseSignUpConfirm()) {
            if (User.findByLoginId(sourceUser.loginId).state == UserState.LOCKED) {
                flash(Constants.WARNING, "user.locked");
                return redirect(loginFormUrl);
            }
        }

        if (User.findByLoginId(sourceUser.loginId).state == UserState.DELETED) {
            flash(Constants.WARNING, "user.deleted");
            return redirect(loginFormUrl);
        }

        User authenticate = authenticateWithPlainPassword(sourceUser.loginId, sourceUser.password);

        if (authenticate != null) {
            addUserInfoToSession(authenticate);
            if (sourceUser.rememberMe) {
                setupRememberMe(authenticate);
            }

            if (StringUtils.isEmpty(redirectUrl)) {
                return redirect(routes.Application.index());
            } else {
                return redirect(redirectUrl);
            }
        }

        flash(Constants.WARNING, "user.login.failed");
        return redirect(routes.UserApp.loginForm());
    }

    /**
     * loginId 와 hash 값을 이용해서 사용자 인증.
     * 인증에 성공하면 DB 에서 조회된 사용자 정보를 리턴
     * 인증에 실패하면 null 리턴
     *
     * @param loginId 로그인ID
     * @param password hash된 비밀번호
     * @return
     */
    public static User authenticateWithHashedPassword(String loginId, String password) {
        User user = User.findByLoginId(loginId);
        return authenticate(user, password);
    }

    /**
     * loginId 와 plain password 를 이용해서 사용자 인증
     * 인증에 성공하면 DB 에서 조회된 사용자 정보를 리턴
     * 인증에 실패하면 null 리턴
     *
     * @param loginId 로그인ID
     * @param password 입력받은 비밀번호
     * @return
     */
    public static User authenticateWithPlainPassword(String loginId, String password) {
        User user = User.findByLoginId(loginId);
        if(user == User.anonymous) {
            return null;
        }
        return authenticate(user, hashedPassword(password, user.passwordSalt));
    }

    /**
     * 로그인 유지기능 사용 여부
     * 로그인 유지 기능이 사용중이면 로그인쿠키를 생성하고 true 를 리턴
     * 사용중이지 않으면 false 리턴
     *
     * @return 로그인 유지기능 사용 여부
     */
    public static boolean isRememberMe() {
        // Remember Me
        Cookie cookie = request().cookies().get(TOKEN);
        if (cookie != null) {
            String[] subject = cookie.value().split(":");
            Logger.debug(cookie.value());
            if(subject.length < 2) return false;
            User user = authenticateWithHashedPassword(subject[0], subject[1]);
            if (user != null) {
                addUserInfoToSession(user);
            }
            return true;
        }
        return false;
    }

    /**
     * 사용자 가입 화면 이동
     *
     * @return
     */
    public static Result signupForm() {
        return ok(signup.render("title.signup", form(User.class)));
    }

    /**
     * 사용자 가입 처리
     * 입력된 데이터 유효성 검증에 실패하면 bad request 응답
     * 사용자 정보를 저장, 로그인 쿠기 생성 후 메인 페이지로 이동
     * 시스템 설정에서 가입승인 기능이 활성화되어 있다면 사용자의 계정 상태를 잠금으로 설정하여 저장, 로그인 쿠키 생성 안됨
     *
     * @return
     */
    @Transactional
    public static Result newUser() {
        Form<User> newUserForm = form(User.class).bindFromRequest();
        validate(newUserForm);
        if (newUserForm.hasErrors()) {
            return badRequest(signup.render("title.signup", newUserForm));
        } else {
            User user = createNewUser(newUserForm.get());
            if (user.state == UserState.LOCKED) {
                flash(Constants.INFO, "user.signup.requested");
            } else {
                addUserInfoToSession(user);
            }
            return redirect(routes.Application.index());
        }
    }

    /**
     * 사용자 비밀번호 변경
     * 비밀번호 변경에 성공하면 로그인 화면으로 이동
     * 비밀번호 변경에 실패하면 수정화면으로 돌아간다
     *
     * @return
     */
    @Transactional
    public static Result resetUserPassword() {
        Form<User> userForm = form(User.class).bindFromRequest();

        if(userForm.hasErrors()) {
            return badRequest(ErrorViews.BadRequest.render("error.badrequest"));
        }

        User currentUser = currentUser();
        User user = userForm.get();

        if(!isValidPassword(currentUser, user.oldPassword)) {
            Form<User> currentUserForm = new Form<>(User.class);
            currentUserForm = currentUserForm.fill(currentUser);

            flash(Constants.WARNING, "user.wrongPassword.alert");
            return badRequest(edit.render(currentUserForm, currentUser));
        }

        resetPassword(currentUser, user.password);

        //go to login page
        processLogout();
        flash(Constants.WARNING, "user.loginWithNewPassword");
        return redirect(routes.UserApp.loginForm());

    }

    /**
     * 비밀번호 검증
     * 사용자 객체의 hash 된 비밀번호 값과 입력 받은 비밀번호의 hash 값이 같은지 검사한다
     *
     * @param currentUser 사용자 객체
     * @param password 입력받은 비밀번호
     * @return
     */
    public static boolean isValidPassword(User currentUser, String password) {
        String hashedOldPassword = hashedPassword(password, currentUser.passwordSalt);
        return currentUser.password.equals(hashedOldPassword);
    }

    /**
     * 신규 비밀번호의 hash 값을 구하여 설정 후 저장한다
     *
     * @param user 사용자객체
     * @param newPassword 신규비밀번호
     */
    @Transactional
    public static void resetPassword(User user, String newPassword) {
        user.password = hashedPassword(newPassword, user.passwordSalt);
        user.save();
    }

    /**
     * 세션에 저장된 정보를 이용해서 사용자 객체를 생성한다
     * 세션에 저장된 정보가 없다면 anonymous 객체가 리턴된다
     *
     * @return 세션 정보 기준 조회된 사용자 객체
     */
    public static User currentUser() {
        String userId = session().get(SESSION_USERID);
        if (StringUtils.isEmpty(userId) || !StringUtils.isNumeric(userId)) {
            return User.anonymous;
        }
        User foundUser = User.find.byId(Long.valueOf(userId));
        if (foundUser == null) {
            processLogout();
            return User.anonymous;
        }
        return foundUser;
    }

    /**
     * 사용자 정보 조회
     *
     * when: 사용자 로그인 아이디나 아바타를 클릭할 때 사용한다.
     *
     * {@code groups}에는 여러 그룹 이름이 콤마(,)를 기준으로 들어올 수 있으며,
     * 각그룹에 해당하는 프로젝트 목록을 간추리고,
     * 그 프로젝트 목록에 포함되는 이슈, 게시물, 풀리퀘, 마일스톤 데이터를 종합하고 최근 등록일 순으로 정렬하여 보여준다.
     *
     * @param loginId 로그인ID
     * @return
     */
    public static Result userInfo(String loginId, String groups, int daysAgo, String selected) {
        if (daysAgo == UNDEFINED) {
            Cookie cookie = request().cookie(DAYS_AGO_COOKIE);
            if (cookie != null) {
                daysAgo = Integer.parseInt(cookie.value());
            } else {
                daysAgo = DAYS_AGO;
                response().setCookie(DAYS_AGO_COOKIE, daysAgo + "");
            }
        } else {
            if (daysAgo < 0) {
                daysAgo = 1;
            }
            response().setCookie(DAYS_AGO_COOKIE, daysAgo + "");
        }

        User user = User.findByLoginId(loginId);
        String[] groupNames = groups.trim().split(",");

        List<Posting> postings = new ArrayList<>();
        List<Issue> issues = new ArrayList<>();
        List<PullRequest> pullRequests = new ArrayList<>();
        List<Milestone> milestones = new ArrayList<>();

        List<Project> projects = collectProjects(loginId, user, groupNames);
        collectDatum(projects, postings, issues, pullRequests, milestones, daysAgo);
        sortDatum(postings, issues, pullRequests, milestones);

        sortByLastPushedDateAndName(projects);
        return ok(view.render(user, groupNames, projects, postings, issues, pullRequests, milestones, daysAgo, selected));
    }

    private static void sortByLastPushedDateAndName(List<Project> projects) {
        Collections.sort(projects, new Comparator<Project>() {
            @Override
            public int compare(Project p1, Project p2) {
                int compareLastPushedDate;
                if (p1.lastPushedDate == null && p2.lastPushedDate == null) {
                    return p1.name.compareTo(p2.name);
                }

                if (p1.lastPushedDate == null) {
                    return 1;
                } else if (p2.lastPushedDate == null) {
                    return -1;
                }

                compareLastPushedDate = p2.lastPushedDate.compareTo(p1.lastPushedDate);
                if (compareLastPushedDate == 0) {
                    return p1.name.compareTo(p2.name);
                }
                return compareLastPushedDate;
            }
        });
    }

    private static void sortDatum(List<Posting> postings, List<Issue> issues, List<PullRequest> pullRequests, List<Milestone> milestones) {

        Collections.sort(issues, new Comparator<Issue>() {
            @Override
            public int compare(Issue i1, Issue i2) {
                return i2.createdDate.compareTo(i1.createdDate);
            }
        });

        Collections.sort(postings, new Comparator<Posting>() {
            @Override
            public int compare(Posting p1, Posting p2) {
                return p2.createdDate.compareTo(p1.createdDate);
            }
        });

        Collections.sort(pullRequests, new Comparator<PullRequest>() {
            @Override
            public int compare(PullRequest p1, PullRequest p2) {
                return p2.created.compareTo(p1.created);
            }
        });

        Collections.sort(milestones, new Comparator<Milestone>() {
            @Override
            public int compare(Milestone m1, Milestone m2) {
                return m2.title.compareTo(m1.title);
            }
        });
    }

    private static void collectDatum(List<Project> projects, List<Posting> postings, List<Issue> issues, List<PullRequest> pullRequests, List<Milestone> milestones, int daysAgo) {
        // collect all postings, issues, pullrequests and milesotnes that are contained in the projects.
        for (Project project : projects) {
            if (AccessControl.isAllowed(UserApp.currentUser(), project.asResource(), Operation.READ)) {
                postings.addAll(Posting.findRecentlyCreatedByDaysAgo(project, daysAgo));
                issues.addAll(Issue.findRecentlyOpendIssuesByDaysAgo(project, daysAgo));
                pullRequests.addAll(PullRequest.findOpendPullRequestsByDaysAgo(project, daysAgo));
                milestones.addAll(Milestone.findOpenMilestones(project.id));
            }
        }
    }

    private static List<Project> collectProjects(String loginId, User user, String[] groupNames) {
        List<Project> projectCollection = new ArrayList<>();
        // collect all projects that are included in the project groups.
        for (String group : groupNames) {
            switch (group) {
                case "own":
                    addProjectNotDupped(projectCollection, Project.findProjectsCreatedByUser(loginId, null));
                    break;
                case "member":
                    addProjectNotDupped(projectCollection, Project.findProjectsJustMemberAndNotOwner(user));
                    break;
                case "watching":
                    addProjectNotDupped(projectCollection, user.getWatchingProjects());
                    break;
            }
        }
        return projectCollection;
    }

    private static void addProjectNotDupped(List<Project> target, List<Project> foundProjects) {
        for (Project project : foundProjects) {
            if( !target.contains(project) ) {
                target.add(project);
            }
        }
    }

    /**
     * 사용자 정보 수정 폼으로 이동
     * 현재 로그인된 사용자 기준
     *
     * @return
     */
    @With(AnonymousCheckAction.class)
    public static Result editUserInfoForm() {
        User user = UserApp.currentUser();
        Form<User> userForm = new Form<>(User.class);
        userForm = userForm.fill(user);
        return ok(edit.render(userForm, user));
    }

    /**
     * 사용자 정보 수정
     *
     * @return
     */
    @With(AnonymousCheckAction.class)
    @Transactional
    public static Result editUserInfo() {
        Form<User> userForm = new Form<>(User.class).bindFromRequest("name", "email");
        String newEmail = userForm.data().get("email");
        String newName = userForm.data().get("name");
        User user = UserApp.currentUser();

        if (StringUtils.isEmpty(newEmail)) {
            userForm.reject("email", "user.wrongEmail.alert");
        } else {
            if (!StringUtils.equals(user.email, newEmail) && User.isEmailExist(newEmail)) {
                userForm.reject("email", "user.email.duplicate");
            }
        }

        if (userForm.error("email") != null) {
            flash(Constants.WARNING, userForm.error("email").message());
            return badRequest(edit.render(userForm, user));
        }
        user.email = newEmail;
        user.name = newName;

        try {
            Long avatarId = Long.valueOf(userForm.data().get("avatarId"));
            if (avatarId != null) {
                Attachment attachment = Attachment.find.byId(avatarId);
                String primary = attachment.mimeType.split("/")[0].toLowerCase();
                if (primary.equals("image")) {
                    Attachment.deleteAll(currentUser().avatarAsResource());
                    attachment.moveTo(currentUser().avatarAsResource());
                }
            }
        } catch (NumberFormatException ignored) {
        }

        Email.deleteOtherInvalidEmails(user.email);
        user.update();
        return redirect(routes.UserApp.userInfo(user.loginId, DEFAULT_GROUP, DAYS_AGO, DEFAULT_SELECTED_TAB));
    }

    /**
     * 현재 사용자가 특정 프로젝트에서 탈퇴
     *
     * @param userName 프로젝트 매니저의 로그인ID
     * @param projectName 프로젝트 이름
     * @return
     */
    @Transactional
    public static Result leave(String userName, String projectName) {
        ProjectApp.deleteMember(userName, projectName, UserApp.currentUser().id);
        return redirect(routes.UserApp.userInfo(UserApp.currentUser().loginId, DEFAULT_GROUP, DAYS_AGO, DEFAULT_SELECTED_TAB));
    }

    /**
     * 로그인ID 존재 여부, 로그인ID 예약어 여부
     *
     * @param loginId 로그인ID
     * @return
     */
    public static Result isUserExist(String loginId) {
        ObjectNode result = Json.newObject();
        result.put("isExist", User.isLoginIdExist(loginId));
        result.put("isReserved", ReservedWordsValidator.isReserved(loginId));
        return ok(result);
    }

    /**
     * 이메일 존재 여부
     *
     * @param email 이메일
     * @return
     */
    @BodyParser.Of(BodyParser.Json.class)
    public static Result isEmailExist(String email) {
        ObjectNode result = Json.newObject();
        result.put("isExist", User.isEmailExist(email));
        return ok(result);
    }

    /**
     * 비밀번호 hash 값 생성
     *
     * @param plainTextPassword plain text
     * @param passwordSalt hash salt
     * @return hashed password
     */
    public static String hashedPassword(String plainTextPassword, String passwordSalt) {
        if (plainTextPassword == null  || passwordSalt == null) {
            throw new IllegalArgumentException("Bad password or passwordSalt!");
        }
        return new Sha256Hash(plainTextPassword, ByteSource.Util.bytes(passwordSalt), HASH_ITERATIONS).toBase64();
    }

    /**
     * 이메일 추가
     *
     * @return
     */
    @Transactional
    public static Result addEmail() {
        Form<Email> emailForm = form(Email.class).bindFromRequest();
        String newEmail = emailForm.data().get("email");

        if(emailForm.hasErrors()) {
            flash(Constants.WARNING, emailForm.error("email").message());
            return redirect(routes.UserApp.editUserInfoForm());
        }

        User currentUser = currentUser();
        if(currentUser == null || currentUser.isAnonymous()) {
            return forbidden(ErrorViews.NotFound.render());
        }

        if(User.isEmailExist(newEmail) || Email.exists(newEmail, true) || currentUser.has(newEmail)) {
            flash(Constants.WARNING, Messages.get("user.email.duplicate"));
            return redirect(routes.UserApp.editUserInfoForm());
        }

        Email email = new Email();
        User user = currentUser();
        email.user = user;
        email.email = newEmail;
        email.valid = false;

        user.addEmail(email);

        return redirect(routes.UserApp.editUserInfoForm());
    }

    /**
     * 이메일 삭제
     *
     * @param id
     * @return
     */
    @Transactional
    public static Result deleteEmail(Long id) {
        User currentUser = currentUser();
        Email email = Email.find.byId(id);

        if(currentUser == null || currentUser.isAnonymous() || email == null) {
            return forbidden(ErrorViews.NotFound.render());
        }

        if(!AccessControl.isAllowed(currentUser, email.user.asResource(), Operation.DELETE)) {
            return forbidden(ErrorViews.Forbidden.render(Messages.get("error.forbidden")));
        }

        email.delete();
        return redirect(routes.UserApp.editUserInfoForm());
    }

    /**
     * 보조 이메일 확인 메일 보내기
     *
     * @param id
     * @return
     */
    @Transactional
    public static Result sendValidationEmail(Long id) {
        User currentUser = currentUser();
        Email email = Email.find.byId(id);

        if(currentUser == null || currentUser.isAnonymous() || email == null) {
            return forbidden(ErrorViews.NotFound.render());
        }

        if(!AccessControl.isAllowed(currentUser, email.user.asResource(), Operation.UPDATE)) {
            return forbidden(ErrorViews.Forbidden.render(Messages.get("error.forbidden")));
        }

        email.sendValidationEmail();

        flash(Constants.WARNING, "확인 메일을 전송했습니다.");
        return redirect(routes.UserApp.editUserInfoForm());
    }

    /**
     * 이메일 확인
     *
     * @param id
     * @param token
     * @return
     */
    @Transactional
    public static Result confirmEmail(Long id, String token) {
        Email email = Email.find.byId(id);

        if(email == null) {
            return forbidden(ErrorViews.NotFound.render());
        }

        if(email.validate(token)) {
            addUserInfoToSession(email.user);
            return redirect(routes.UserApp.editUserInfoForm());
        } else {
            return forbidden(ErrorViews.NotFound.render());
        }
    }

    /**
     * 대표 메일로 설정하기
     *
     * @param id
     * @return
     */
    @Transactional
    public static Result setAsMainEmail(Long id) {
        User currentUser = currentUser();
        Email email = Email.find.byId(id);

        if(currentUser == null || currentUser.isAnonymous() || email == null) {
            return forbidden(ErrorViews.NotFound.render());
        }

        if(!AccessControl.isAllowed(currentUser, email.user.asResource(), Operation.UPDATE)) {
            return forbidden(ErrorViews.Forbidden.render(Messages.get("error.forbidden")));
        }

        String oldMainEmail = currentUser.email;
        currentUser.email = email.email;
        currentUser.removeEmail(email);
        currentUser.update();

        Email newSubEmail = new Email();
        newSubEmail.valid = true;
        newSubEmail.email = oldMainEmail;
        newSubEmail.user = currentUser;
        currentUser.addEmail(newSubEmail);

        return redirect(routes.UserApp.editUserInfoForm());
    }

    /*
     * 사용자 인증
     *
     * 사용자 객체와 hash 값을 이용
     */
    private static User authenticate(User user, String password) {
        if (!user.isAnonymous()) {
            if (user.password.equals(password)) {
                return user;
            }
        }
        return null;
    }

    /*
     * 시스템 설정에서 가입 승인 여부 조회
     */
    private static boolean isUseSignUpConfirm(){
        Configuration config = play.Play.application().configuration();
        String useSignUpConfirm = config.getString("signup.require.confirm");
        return useSignUpConfirm != null && useSignUpConfirm.equals("true");
    }

    /*
     * 로그인 유지기능 활성화
     */
    private static void setupRememberMe(User user) {
        response().setCookie(TOKEN, user.loginId + ":" + user.password, MAX_AGE);
        Logger.debug("remember me enabled");
    }

    /*
     * 로그아웃 처리
     *
     * 세션 데이터 클리어
     * 로그인 유지 쿠키 폐기
     */
    private static void processLogout() {
        session().clear();
        response().discardCookie(TOKEN);
    }

    /*
     * 사용자 가입 입력 폼 유효성 체크
     */
    private static void validate(Form<User> newUserForm) {
        // loginId가 빈 값이 들어오면 안된다.
        if (newUserForm.field("loginId").value().trim().isEmpty()) {
            newUserForm.reject("loginId", "user.wrongloginId.alert");
        }

        if (newUserForm.field("loginId").value().contains(" ")) {
            newUserForm.reject("loginId", "user.wrongloginId.alert");
        }

        // password가 빈 값이 들어오면 안된다.
        if (newUserForm.field("password").value().trim().isEmpty()) {
            newUserForm.reject("password", "user.wrongPassword.alert");
        }

        // 중복된 loginId로 가입할 수 없다.
        if (User.isLoginIdExist(newUserForm.field("loginId").value())) {
            newUserForm.reject("loginId", "user.loginId.duplicate");
        }

        // 중복된 email로 가입할 수 없다.
        if (User.isEmailExist(newUserForm.field("email").value())) {
            newUserForm.reject("email", "user.email.duplicate");
        }
    }

    /*
     * 신규 가입 사용자 생성
     */
    private static User createNewUser(User user) {
        RandomNumberGenerator rng = new SecureRandomNumberGenerator();
        user.passwordSalt = Arrays.toString(rng.nextBytes().getBytes());
        user.password = hashedPassword(user.password, user.passwordSalt);
        User.create(user);
        if (isUseSignUpConfirm()) {
            user.changeState(UserState.LOCKED);
        } else {
            user.changeState(UserState.ACTIVE);
        }
        Email.deleteOtherInvalidEmails(user.email);
        return user;
    }

    /*
     * 사용자 정보를 세션에 저장한다 (로그인 처리됨)
     */
    public static void addUserInfoToSession(User user) {
        session(SESSION_USERID, String.valueOf(user.id));
        session(SESSION_LOGINID, user.loginId);
        session(SESSION_USERNAME, user.name);
    }
}
