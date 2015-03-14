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

import com.avaje.ebean.ExpressionList;
import com.avaje.ebean.annotation.Transactional;
import controllers.annotation.AnonymousCheck;
import models.*;
import models.enumeration.Operation;
import models.enumeration.UserState;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.crypto.RandomNumberGenerator;
import org.apache.shiro.crypto.SecureRandomNumberGenerator;
import org.apache.shiro.crypto.hash.Sha256Hash;
import org.apache.shiro.util.ByteSource;
import com.fasterxml.jackson.databind.node.ObjectNode;
import play.Configuration;
import play.Logger;
import play.Play;
import play.data.Form;
import play.i18n.Messages;
import play.libs.Json;
import play.mvc.*;
import play.mvc.Http.Cookie;
import utils.*;
import views.html.user.*;

import java.util.*;

import static play.data.Form.form;
import static play.libs.Json.toJson;

public class UserApp extends Controller {
    public static final String SESSION_USERID = "userId";
    public static final String SESSION_LOGINID = "loginId";
    public static final String SESSION_USERNAME = "userName";
    public static final String TOKEN = "yobi.token";
    public static final String TOKEN_SEPARATOR = ":";
    public static final int TOKEN_LENGTH = 2;
    public static final int MAX_AGE = 30*24*60*60;
    public static final String DEFAULT_AVATAR_URL
            = routes.Assets.at("images/default-avatar-128.png").url();
    private static final int AVATAR_FILE_LIMIT_SIZE = 1024*1000*1; //1M
    public static final int MAX_FETCH_USERS = 1000;
    private static final int HASH_ITERATIONS = 1024;
    public static final int DAYS_AGO = 7;
    public static final int UNDEFINED = 0;
    public static final String DAYS_AGO_COOKIE = "daysAgo";
    public static final String DEFAULT_GROUP = "own";
    public static final String DEFAULT_SELECTED_TAB = "projects";
    public static final String TOKEN_USER = "TOKEN_USER";

    @AnonymousCheck
    public static Result users(String query) {
        if (!request().accepts("application/json")) {
            return status(Http.Status.NOT_ACCEPTABLE);
        }

        ExpressionList<User> el = User.find.select("loginId, name").where()
            .ne("state", UserState.DELETED).disjunction();
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

    public static Result loginForm() {
        if(!UserApp.currentUser().isAnonymous()) {
            return redirect(routes.Application.index());
        }

        String redirectUrl = request().getQueryString("redirectUrl");
        String loginFormUrl = routes.UserApp.loginForm().url();
        String referer = request().getHeader("Referer");
        if(StringUtils.isEmpty(redirectUrl) && !StringUtils.equals(loginFormUrl, referer)) {
            redirectUrl = request().getHeader("Referer");
        }
        return ok(login.render("title.login", form(AuthInfo.class), redirectUrl));
    }

    public static Result logout() {
        processLogout();
        flash(Constants.SUCCESS, "user.logout.success");
        String redirectUrl = request().getHeader("Referer");
        return redirect(redirectUrl);
    }

    public static Result login() {
        if (HttpUtil.isJSONPreferred(request())) {
            return loginByAjaxRequest();
        } else {
            return loginByFormRequest();
        }
    }

    /**
     * Process login in general case of request.
     *
     * Returns:
     * - If "signup.require.confirm = true" has enabled in application.conf,
     *   the user in state of locked(or unconfirmed) cannot be logged in.
     *   and page will be redirected to login form with message "user.locked".
     *
     * - If "signup.require.confirm" is disabled(as default),
     *   the user in state of locked can be logged in. (TODO: check this in feature specification).
     *
     * - If failed to authentication, redirect to login form with error message.
     *
     * Cookie for login will be created
     * if success to authenticate with request.
     *
     * If "rememberMe" included in request,
     * Cookie for "rememberMe" (which means "Stay logged in") will be create
     * separate from login cookie.
     *
     * @return
     */
    private static Result loginByFormRequest() {
        Form<AuthInfo> authInfoForm = form(AuthInfo.class).bindFromRequest();

        if(authInfoForm.hasErrors()) {
            flash(Constants.WARNING, "user.login.required");
            return badRequest(login.render("title.login", authInfoForm, null));
        }

        User sourceUser = User.findByLoginKey(authInfoForm.get().loginIdOrEmail);

        if (isUseSignUpConfirm()) {
            if (User.findByLoginId(sourceUser.loginId).state == UserState.LOCKED) {
                flash(Constants.WARNING, "user.locked");
                return redirect(getLoginFormURLWithRedirectURL());
            }
        }

        if (User.findByLoginId(sourceUser.loginId).state == UserState.DELETED) {
            flash(Constants.WARNING, "user.deleted");
            return redirect(getLoginFormURLWithRedirectURL());
        }

        User authenticate = authenticateWithPlainPassword(sourceUser.loginId, authInfoForm.get().password);

        if (!authenticate.isAnonymous()) {
            addUserInfoToSession(authenticate);

            if (authInfoForm.get().rememberMe) {
                setupRememberMe(authenticate);
            }

            authenticate.lang = play.mvc.Http.Context.current().lang().code();
            authenticate.update();

            String redirectUrl = getRedirectURLFromParams();

            if(StringUtils.isEmpty(redirectUrl)){
                return redirect(routes.Application.index());
            } else {
                return redirect(redirectUrl);
            }
        }

        flash(Constants.WARNING, "user.login.invalid");
        return redirect(routes.UserApp.loginForm());
    }

    /**
     * Process login request by AJAX
     *
     * Almost same with loginByFormRequest
     * except part of handle with "redirectUrl" has excluded.
     *
     * Returns:
     * - In case of success: empty JSON string {}
     * - In case of failed: error message as JSON string in form of {"message":"cause"}.
     *
     * @return
     */
    private static Result loginByAjaxRequest() {
        Form<AuthInfo> authInfoForm = form(AuthInfo.class).bindFromRequest();

        if(authInfoForm.hasErrors()) {
            return badRequest(getObjectNodeWithMessage("user.login.required"));
        }

        User sourceUser = User.findByLoginKey(authInfoForm.get().loginIdOrEmail);

        if (isUseSignUpConfirm()) {
            if (User.findByLoginId(sourceUser.loginId).state == UserState.LOCKED) {
                return forbidden(getObjectNodeWithMessage("user.locked"));
            }
        }

        if (User.findByLoginId(sourceUser.loginId).state == UserState.DELETED) {
            return notFound(getObjectNodeWithMessage("user.deleted"));
        }

        User authenticate = authenticateWithPlainPassword(sourceUser.loginId, authInfoForm.get().password);

        if (!authenticate.isAnonymous()) {
            addUserInfoToSession(authenticate);

            if (authInfoForm.get().rememberMe) {
                setupRememberMe(authenticate);
            }

            authenticate.lang = play.mvc.Http.Context.current().lang().code();
            authenticate.update();

            return ok("{}");
        }

        return forbidden(getObjectNodeWithMessage("user.login.invalid"));
    }

    /**
     * Get value of "redirectUrl" from query
     * @return
     */
    private static String getRedirectURLFromParams(){
        Map<String, String[]> params = request().body().asFormUrlEncoded();
        return HttpUtil.getFirstValueFromQuery(params, "redirectUrl");
    }

    /**
     * Get login form URL string with "redirectUrl" parameter in query
     * @return
     */
    private static String getLoginFormURLWithRedirectURL(){
        String redirectUrl = getRedirectURLFromParams();
        String loginFormUrl = routes.UserApp.loginForm().url();
        loginFormUrl = loginFormUrl + "?redirectUrl=" + redirectUrl;

        return loginFormUrl;
    }

    /**
     * Returns ObjectNode which has "message" node filled with {@code message}
     * loginByAjaxRequest() uses this to return result as JSON string
     *
     * @param message
     * @return
     */
    private static ObjectNode getObjectNodeWithMessage(String message){
        ObjectNode result = Json.newObject();
        result.put("message", message);
        return result;
    }

    public static User authenticateWithHashedPassword(String loginId, String password) {
        return authenticate(loginId, password, true);
    }

    public static User authenticateWithPlainPassword(String loginId, String password) {
        return authenticate(loginId, password, false);
    }

    public static Result signupForm() {
        if(!UserApp.currentUser().isAnonymous()) {
            return redirect(routes.Application.index());
        }

        return ok(signup.render("title.signup", form(User.class)));
    }

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

    public static boolean isValidPassword(User currentUser, String password) {
        String hashedOldPassword = hashedPassword(password, currentUser.passwordSalt);
        return currentUser.password.equals(hashedOldPassword);
    }

    @Transactional
    public static void resetPassword(User user, String newPassword) {
        user.password = hashedPassword(newPassword, user.passwordSalt);
        user.save();
    }

    public static User currentUser() {
        User user = getUserFromSession();
        if (!user.isAnonymous()) {
            return user;
        }
        return getUserFromContext();
    }

    private static User getUserFromSession() {
        String userId = session().get(SESSION_USERID);
        if (userId == null) {
            return User.anonymous;
        }
        if (!StringUtils.isNumeric(userId)) {
            return invalidSession();
        }
        User user = User.find.byId(Long.valueOf(userId));
        if (user == null) {
            return invalidSession();
        }
        return user;
    }

    private static User getUserFromContext() {
        Object cached = Http.Context.current().args.get(TOKEN_USER);
        if (cached instanceof User) {
            return (User) cached;
        }
        initTokenUser();
        return (User) Http.Context.current().args.get(TOKEN_USER);
    }

    public static void initTokenUser() {
        User user = getUserFromToken();
        Http.Context.current().args.put(TOKEN_USER, user);
        if (!user.isAnonymous() && getUserFromSession().isAnonymous()) {
            addUserInfoToSession(user);
        }
    }

    private static User getUserFromToken() {
        Cookie cookie = request().cookies().get(TOKEN);
        if (cookie == null) {
            return User.anonymous;
        }
        String[] subject =  StringUtils.split(cookie.value(), TOKEN_SEPARATOR);
        if (ArrayUtils.getLength(subject) != TOKEN_LENGTH) {
            return invalidToken();
        }
        User user = authenticateWithHashedPassword(subject[0], subject[1]);
        if (user.isAnonymous()) {
            return invalidToken();
        }
        return user;
    }

    private static User invalidSession() {
        session().clear();
        return User.anonymous;
    }

    private static User invalidToken() {
        response().discardCookie(TOKEN);
        return User.anonymous;
    }

    @AnonymousCheck
    public static Result userInfo(String loginId, String groups, int daysAgo, String selected) {
        Organization org = Organization.findByName(loginId);
        if(org != null) {
            return redirect(routes.OrganizationApp.organization(org.name));
        }

        if (daysAgo == UNDEFINED) {
            Cookie cookie = request().cookie(DAYS_AGO_COOKIE);
            if (cookie != null && StringUtils.isNotEmpty(cookie.value())) {
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

    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    public static Result editUserInfoForm() {
        User user = UserApp.currentUser();
        Form<User> userForm = new Form<>(User.class);
        userForm = userForm.fill(user);
        return ok(edit.render(userForm, user));
    }

    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    public static Result editUserInfoByTabForm(String tabId) {
        User user = UserApp.currentUser();
        Form<User> userForm = new Form<>(User.class);
        userForm = userForm.fill(user);

        switch(UserInfoFormTabType.fromString(tabId)){
            case PASSWORD:
                return ok(edit_password.render(userForm, user));
            case NOTIFICATIONS:
                return ok(edit_notifications.render(userForm, user));
            case EMAILS:
                return ok(edit_emails.render(userForm, user));
            default:
            case PROFILE:
                return ok(edit.render(userForm, user));
        }
    }

    private enum UserInfoFormTabType {
        PROFILE("profile"),
        PASSWORD("password"),
        NOTIFICATIONS("notifications"),
        EMAILS("emails");

        private String tabId;

        UserInfoFormTabType(String tabId) {
            this.tabId = tabId;
        }

        public String value(){
            return tabId;
        }

        public static UserInfoFormTabType fromString(String text)
            throws IllegalArgumentException {
            for(UserInfoFormTabType tab : UserInfoFormTabType.values()){
                if (tab.value().equalsIgnoreCase(text)) {
                    return tab;
                }
            }
            throw new IllegalArgumentException("Invalid tabId");
        }
    }

    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
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

                if (attachment.size > AVATAR_FILE_LIMIT_SIZE){
                    userForm.reject("avatarId", "user.avatar.fileSizeAlert");
                }

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

    @Transactional
    public static Result leave(String userName, String projectName) {
        ProjectApp.deleteMember(userName, projectName, UserApp.currentUser().id);
        return redirect(routes.UserApp.userInfo(UserApp.currentUser().loginId, DEFAULT_GROUP, DAYS_AGO, DEFAULT_SELECTED_TAB));
    }

    /**
     * check the given {@code loginId} is being used by someone else's logindId or group name,
     * and whether {@code loginId} is a reserved word or not.
     *
     * @param name
     * @return
     * @see User#isLoginIdExist(String)
     * @see Organization#isNameExist(String)
     * @see ReservedWordsValidator#isReserved(String)
     */
    public static Result isUsed(String name) {
        ObjectNode result = Json.newObject();
        result.put("isExist", User.isLoginIdExist(name) || Organization.isNameExist(name));
        result.put("isReserved", ReservedWordsValidator.isReserved(name));
        return ok(result);
    }

    @BodyParser.Of(BodyParser.Json.class)
    public static Result isEmailExist(String email) {
        ObjectNode result = Json.newObject();
        result.put("isExist", User.isEmailExist(email));
        return ok(result);
    }

    /**
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

    private static User authenticate(String loginId, String password, boolean hashed) {
        User user = User.findByLoginId(loginId);
        if (user.isAnonymous()) {
            return user;
        }
        String hashedPassword = hashed ? password : hashedPassword(password, user.passwordSalt);
        if (StringUtils.equals(user.password, hashedPassword)) {
            return user;
        }
        return User.anonymous;
    }

    private static boolean isUseSignUpConfirm(){
        Configuration config = play.Play.application().configuration();
        String useSignUpConfirm = config.getString("signup.require.confirm");
        return useSignUpConfirm != null && useSignUpConfirm.equals("true");
    }

    private static void setupRememberMe(User user) {
        response().setCookie(TOKEN, user.loginId + ":" + user.password, MAX_AGE);
        Logger.debug("remember me enabled");
    }

    private static void processLogout() {
        session().clear();
        response().discardCookie(TOKEN);
    }

    private static void validate(Form<User> newUserForm) {
        if (newUserForm.field("loginId").value().trim().isEmpty()) {
            newUserForm.reject("loginId", "user.wrongloginId.alert");
        }

        if (newUserForm.field("loginId").value().contains(" ")) {
            newUserForm.reject("loginId", "user.wrongloginId.alert");
        }

        if (newUserForm.field("password").value().trim().isEmpty()) {
            newUserForm.reject("password", "user.wrongPassword.alert");
        }

        if (User.isLoginIdExist(newUserForm.field("loginId").value())
            || Organization.isNameExist(newUserForm.field("loginId").value())) {
            newUserForm.reject("loginId", "user.loginId.duplicate");
        }

        if (User.isEmailExist(newUserForm.field("email").value())) {
            newUserForm.reject("email", "user.email.duplicate");
        }
    }

    private static User createNewUser(User user) {
        RandomNumberGenerator rng = new SecureRandomNumberGenerator();
        user.passwordSalt = rng.nextBytes().toBase64();
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

    public static void addUserInfoToSession(User user) {
        session(SESSION_USERID, String.valueOf(user.id));
        session(SESSION_LOGINID, user.loginId);
        session(SESSION_USERNAME, user.name);
    }

    public static void updatePreferredLanguage() {
        Http.Request request = Http.Context.current().request();
        User user = UserApp.currentUser();

        if (user.isAnonymous()) {
            return;
        }

        if (request.acceptLanguages().isEmpty() &&
                request.cookie(Play.langCookieName()) == null) {
            return;
        }

        String code = StringUtils.left(Http.Context.current().lang().code(), 255);

        if (!code.equals(user.lang)) {
            synchronized (user) {
                user.refresh();
                user.lang = code;
                user.update();
            }
        }
    }

    public static Result resetUserPasswordBySiteManager(String loginId){
        if (!request().getQueryString("action").equals("resetPassword")) {
            ObjectNode json = Json.newObject();
            json.put("isSuccess", false);
            json.put("reason", "BAD_REQUEST");
            return badRequest(json);
        }
        String newPassword = PasswordReset.generateResetHash(loginId).substring(0,6);
        User targetUser = User.findByLoginId(loginId);
        if(!targetUser.isAnonymous() && UserApp.currentUser().isSiteManager()){
            User.resetPassword(loginId, newPassword);

            ObjectNode json = Json.newObject();
            json.put("loginId", targetUser.loginId);
            json.put("name", targetUser.name);
            json.put("newPassword", newPassword);
            json.put("isSuccess", true);
            return ok(json);
        } else {
            ObjectNode json = Json.newObject();
            json.put("isSuccess", false);
            json.put("reason", "FORBIDDEN");
            return forbidden(json);
        }
    }

    public static boolean isSiteAdminLoggedInSession(){
        if(SiteAdmin.SITEADMIN_DEFAULT_LOGINID.equals(session().get(SESSION_LOGINID))){
            return true;
        } else {
            return false;
        }
    }
}
