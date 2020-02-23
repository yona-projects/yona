/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp.
 * https://yona.io
 **/
package controllers;

import com.avaje.ebean.ExpressionList;
import com.avaje.ebean.Page;
import com.avaje.ebean.annotation.Transactional;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.feth.play.module.mail.Mailer;
import com.feth.play.module.mail.Mailer.Mail;
import com.feth.play.module.mail.Mailer.Mail.Body;
import com.feth.play.module.pa.PlayAuthenticate;
import controllers.annotation.AnonymousCheck;
import jxl.write.WriteException;
import models.*;
import models.enumeration.Operation;
import models.enumeration.UserState;
import models.support.LdapUser;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.crypto.RandomNumberGenerator;
import org.apache.shiro.crypto.SecureRandomNumberGenerator;
import org.apache.shiro.crypto.hash.Sha256Hash;
import org.apache.shiro.util.ByteSource;
import org.joda.time.LocalDateTime;
import play.Configuration;
import play.Logger;
import play.Play;
import play.data.Form;
import play.i18n.Messages;
import play.libs.Json;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Http.Cookie;
import play.mvc.Result;
import utils.*;
import views.html.user.*;

import javax.naming.AuthenticationException;
import javax.naming.CommunicationException;
import javax.naming.NamingException;
import java.io.IOException;
import java.util.*;

import static com.feth.play.module.mail.Mailer.getEmailName;
import static models.NotificationMail.isAllowedEmailDomains;
import static play.data.Form.form;
import static play.libs.Json.toJson;
import static utils.HtmlUtil.defaultSanitize;
import static utils.LdapService.FALLBACK_TO_LOCAL_LOGIN;

public class UserApp extends Controller {
    public static final String SESSION_USERID = "userId";
    public static final String SESSION_LOGINID = "loginId";
    public static final String SESSION_USERNAME = "userName";
    public static final String SESSION_KEY = "key";
    public static final String TOKEN = "yobi.token";
    public static final String TOKEN_SEPARATOR = ":";
    public static final int TOKEN_LENGTH = 2;
    public static final int MAX_AGE = 30*24*60*60;
    public static final String DEFAULT_AVATAR_URL
            = routes.Assets.at("images/default-avatar-128.png").url();
    private static final int AVATAR_FILE_LIMIT_SIZE = 1024*1000*1; //1M
    public static final int MAX_FETCH_USERS = 10;  //Match value to Typeahead deafult value at yobi.ui.Typeaheds.js
    private static final int HASH_ITERATIONS = 1024;
    public static final int DAYS_AGO = 14;
    public static final int UNDEFINED = 0;
    public static final String DAYS_AGO_COOKIE = "daysAgo";
    public static final String DEFAULT_GROUP = "own";
    public static final String DEFAULT_SELECTED_TAB = "projects";
    public static final String TOKEN_USER = "TOKEN_USER";
    public static final String USER_TOKEN_HEADER = "Yona-Token";

    public static final boolean useSocialLoginOnly = play.Configuration.root()
            .getBoolean("application.use.social.login.only", false);
    public static final String FLASH_MESSAGE_KEY = "message";
    public static final String FLASH_ERROR_KEY = "error";
    private static boolean usingEmailVerification = play.Configuration.root()
            .getBoolean("application.use.email.verification", false);

    @AnonymousCheck
    public static Result users(String query) {
        String referer = StringUtils.defaultString(request().getHeader("referer"), "");
        if (!referer.endsWith("members") || !request().accepts("application/json")) {
            return status(Http.Status.NOT_ACCEPTABLE);
        }

        if(StringUtils.isEmpty(query)){
            return ok(toJson(new ArrayList<>()));
        }

        List<Map<String, String>> users = new ArrayList<>();
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

    public static void noCache(final Http.Response response) {
        // http://stackoverflow.com/questions/49547/making-sure-a-web-page-is-not-cached-across-all-browsers
        response.setHeader(Http.Response.CACHE_CONTROL, "no-cache, no-store, must-revalidate");  // HTTP 1.1
        response.setHeader(Http.Response.PRAGMA, "no-cache");  // HTTP 1.0.
        response.setHeader(Http.Response.EXPIRES, "0");  // Proxies.
    }

    public static Result loginForm() {
        noCache(response());
        if(!UserApp.currentUser().isAnonymous()) {
            return redirect(routes.Application.index());
        }

        String redirectUrl = request().getQueryString("redirectUrl");
        String loginFormUrl = routes.UserApp.loginForm().url();
        String referer = request().getHeader("Referer");
        if(StringUtils.isEmpty(redirectUrl) && !StringUtils.equals(loginFormUrl, referer)) {
            redirectUrl = request().getHeader("Referer");
        }

        //Assume oAtuh is passed but not linked with existed account
        if(PlayAuthenticate.isLoggedIn(session())){
            UserApp.linkWithExistedOrCreateLocalUser();
            return redirect(redirectUrl);
        } else {
            return ok(views.html.user.login.render("title.login", form(AuthInfo.class), redirectUrl));
        }
    }

    public static Result logout() {
        processLogout();
        flash(Constants.SUCCESS, "user.logout.success");
        String redirectUrl = request().getHeader("Referer");
        return redirect(redirectUrl);
    }

    public static Result login() {
        noCache(response());
        if(useSocialLoginOnly){
            flash(FLASH_ERROR_KEY,
                    Messages.get("app.warn.support.social.login.only"));
            return Application.index();
        }
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

        if (isUsingSignUpConfirm()) {
            if (User.findByLoginId(sourceUser.loginId).state == UserState.LOCKED) {
                flash(Constants.WARNING, "user.locked");
                return redirect(getLoginFormURLWithRedirectURL());
            }
        }

        if (User.findByLoginId(sourceUser.loginId).state == UserState.DELETED) {
            flash(Constants.WARNING, "user.deleted");
            return redirect(getLoginFormURLWithRedirectURL());
        }

        User authenticate = User.anonymous;
        if (LdapService.useLdap) {
            authenticate =  authenticateWithLdap(authInfoForm.get().loginIdOrEmail, authInfoForm.get().password);
        } else {
            authenticate = authenticateWithPlainPassword(sourceUser.loginId, authInfoForm.get().password);
        }

        if (!authenticate.isAnonymous()) {
            authenticate.refresh();
        }

        if(authenticate.isLocked()){
            flash(Constants.WARNING, "user.locked");
            return logout();
        }
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
                return redirect(encodedPath(redirectUrl));
            }
        }

        flash(Constants.WARNING, "user.login.invalid");
        return redirect(getLoginFormURLWithRedirectURL());
    }

    private static String encodedPath(String path){
        String[] paths = path.split("/");
        if(paths.length == 0){
            return "/";
        }
        String[] encodedPaths = new String[paths.length];
        for (int i=0; i< paths.length; i++) {
            encodedPaths[i] = HttpUtil.encodeUrlString(paths[i]);
        }
        return String.join("/", encodedPaths);
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

        if (isUsingSignUpConfirm()) {
            if (User.findByLoginId(sourceUser.loginId).state == UserState.LOCKED) {
                return forbidden(getObjectNodeWithMessage("user.locked"));
            }
        }

        if (User.findByLoginId(sourceUser.loginId).state == UserState.DELETED) {
            return notFound(getObjectNodeWithMessage("user.deleted"));
        }

        User user = User.anonymous;
        if (LdapService.useLdap) {
            user =  authenticateWithLdap(authInfoForm.get().loginIdOrEmail, authInfoForm.get().password);
        } else {
            user = authenticateWithPlainPassword(sourceUser.loginId, authInfoForm.get().password);
        }

        if(user.isLocked()){
            return forbidden(getObjectNodeWithMessage("user.locked"));
        }

        if (!user.isAnonymous()) {
            if (authInfoForm.get().rememberMe) {
                setupRememberMe(user);
            }

            user.refresh();
            user.lang = play.mvc.Http.Context.current().lang().code();
            user.update();
            addUserInfoToSession(user);

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
        }

        if (!isAllowedEmailDomains(newUserForm.get().email)) {
            flash(Constants.INFO, "user.unacceptable.email.domain");
            play.Logger.warn("Signup rejected: " + newUserForm.get().name + " with " + newUserForm.get().email);
            return badRequest(signup.render("title.signup", newUserForm));
        }

        User user = createNewUser(newUserForm.get());
        if (isUsingEmailVerification()) {
            if (isAllowedEmailDomains(user.email)) {
                flash(Constants.INFO, "user.verification.mail.sent");
            } else {
                flash(Constants.INFO, "user.unacceptable.email.domain");
            }
        }
        if (user.state == UserState.LOCKED && isUsingSignUpConfirm()) {
            flash(Constants.INFO, "user.signup.requested");
        } else {
            addUserInfoToSession(user);
        }
        return redirect(routes.Application.index());
    }

    private static String newLoginIdWithoutDup(final String candidate, int num) {
        String newLoginIdSuggestion = candidate + "" + num;
        if(User.findByLoginId(newLoginIdSuggestion).isAnonymous()){
            return newLoginIdSuggestion;
        } else {
            num = num + 1;
            return newLoginIdWithoutDup(newLoginIdSuggestion, num);
        }
    }

    public static User createLocalUserWithOAuth(UserCredential userCredential){
        if(userCredential.email == null || "null".equalsIgnoreCase(userCredential.email)) {
            flash(FLASH_ERROR_KEY,
                    Messages.get("app.warn.cannot.access.email.information"));
            play.Logger.error("Cannot confirm email address of " + userCredential.id + ": " + userCredential.name);
            userCredential.delete();
            forceOAuthLogout();
            return User.anonymous;
        }

        if (!isAllowedEmailDomains(userCredential.email)) {
            flash(Constants.INFO, "user.unacceptable.email.domain");
            play.Logger.warn("Signup rejected: " + userCredential.name + " with " + userCredential.email);
            userCredential.delete();
            forceOAuthLogout();
            return User.anonymous;
        }
        CandidateUser candidateUser = new CandidateUser(userCredential.name, userCredential.email);
        User created = createUserDelegate(candidateUser);

        // checking is delegated to oAuth
        created.refresh();
        created.state = UserState.ACTIVE;
        created.update();

        //Also, update userCredential
        userCredential.loginId = created.loginId;
        userCredential.user = created;
        userCredential.update();

        return created;
    }

    private static void forceOAuthLogout() {
        session().put("pa.url.orig", routes.Application.oAuthLogout().url());
    }

    private static User createUserDelegate(CandidateUser candidateUser) {
        String loginIdCandidate = candidateUser.getLoginId();

        User user = new User();

        if (StringUtils.isBlank(loginIdCandidate) || LdapService.USE_EMAIL_BASE_LOGIN) {
            loginIdCandidate = candidateUser.getEmail().substring(0, candidateUser.getEmail().indexOf("@"));
            loginIdCandidate = generateLoginId(user, loginIdCandidate);
        }

        user.loginId = loginIdCandidate;
        user.name = candidateUser.getName();
        user.email = candidateUser.getEmail();

        if(StringUtils.isEmpty(candidateUser.getPassword())){
            user.password = (new SecureRandomNumberGenerator()).nextBytes().toBase64();  // random password because created with OAuth
        } else {
            user.password = candidateUser.getPassword();
        }

        user.isGuest = candidateUser.isGuest();
        return createNewUser(user);
    }

    public static Result verifyUser(String loginId, String verificationCode){
        if(!UserApp.currentUser().isAnonymous()) {
            return redirect(routes.Application.index());
        }
        UserVerification uv = UserVerification.findbyLoginIdAndVerificationCode(loginId, verificationCode);
        if(uv == null){
            return notFound("Invalid verification");
        }
        if (uv.isValidDate()) {
            User user = User.findByLoginId(loginId);
            user.state = UserState.ACTIVE;
            user.update();
            uv.invalidate();
            return ok(verified.render("", loginId));
        }
        return notFound("Invalid verification");
    }

    private static void sendMailAfterUserCreation(User created) {
        if (!isAllowedEmailDomains(created.email)) {
            flash(Constants.INFO, "user.unacceptable.email.domain");
            return;
        }
        Mail mail = new Mail(Messages.get("user.verification.signup.confirm")
                + ": " + getServeIndexPageUrl(),
                getNewAccountMailBody(created),
                new String[] { getEmailName(created.email, created.name) });
        Mailer mailer = Mailer.getCustomMailer(Configuration.root().getConfig("play-easymail"));
        mailer.sendMail(mail);
    }

    private static Body getNewAccountMailBody(User user){
        String passwordResetUrl = getServeIndexPageUrl() + routes.PasswordResetApp.lostPassword();
        StringBuilder html = new StringBuilder();
        StringBuilder plainText = new StringBuilder();

        if(isUsingEmailVerification()){
            setVerificationMessage(user, html, plainText);
        }
        setSignupInfomation(user, passwordResetUrl, html, plainText);
        return new Body(plainText.toString(), html.toString());
    }

    private static void setSignupInfomation(User user, String passwordResetUrl, StringBuilder html, StringBuilder plainText) {
        html.append("URL: <a href='").append(getServeIndexPageUrl()).append("'>")
                    .append(getServeIndexPageUrl()).append("</a><br/>\n")
                .append("ID: ").append(user.loginId).append("<br/>\n")
                .append("Email: ").append(user.email).append("<br/>\n<br/>\n")
                .append("Password reset: <a href='").append(passwordResetUrl).append("' target='_blank'>")
                .append(passwordResetUrl).append("</a><br/>\n");
        plainText.append("URL: ").append(getServeIndexPageUrl()).append("\n")
                .append("ID: ").append(user.loginId).append("\n")
                .append("Email: ").append(user.email).append("\n\n")
                .append("Password reset: ").append(passwordResetUrl).append("\n");
    }

    private static void setVerificationMessage(User user, StringBuilder html, StringBuilder plainText) {
        UserVerification verification = UserVerification.findbyUser(user);
        if(verification == null){
            verification = UserVerification.newVerification(user);
        }
        String verificationUrl = getServeIndexPageUrl()
                + routes.UserApp.verifyUser(user.loginId, verification.verificationCode).toString();
        html.append("<h1>").append(Messages.get("user.verification")).append("</h1>\n");
        html.append("<hr />\n");
        html.append("<p><a href='").append(verificationUrl).append("'>")
                .append(Messages.get("user.verification.link.click")).append("</a></p>\n");
        html.append("<br />\n");
        html.append("<br />\n");

        plainText.append(Messages.get("user.verification")).append("\n");
        plainText.append("--------------------------\n");
        plainText.append(verificationUrl).append("\n");
        plainText.append("\n");
        plainText.append("\n");
    }

    private static String getServeIndexPageUrl(){
        StringBuilder url = new StringBuilder();
        if(request().secure()){
            url.append("https://");
        } else {
            url.append("http://");
        }
        url.append(utils.Config.getHostport("localhost:9000"));

        return url.toString();
    }

    private static String generateLoginId(User user, String loginIdCandidate) {
        User sameLoginIdUser = User.findByLoginId(loginIdCandidate);
        if (sameLoginIdUser.isAnonymous()) {
            return loginIdCandidate;
        } else {
            sameLoginIdUser = User.findByLoginId(loginIdCandidate + "-yona");
            if (sameLoginIdUser.isAnonymous()) {
                return loginIdCandidate + "-yona";   // first dup, then use suffix "-yona"
            } else {
                return newLoginIdWithoutDup(loginIdCandidate, 2);
            }
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

    public static Result resetUserVisitedList() {
        RecentProject.deleteAll(currentUser());
        flash(Constants.INFO, "userinfo.reset.visited.project.list.done");
        return redirect(routes.UserApp.editUserInfoForm());
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

    @Transactional
    public static User currentUser() {
        User user = getUserFromSession();
        if (!user.isAnonymous()) {
            return user;
        } else {
            user = User.findUserIfTokenExist(user);
        }
        if(!user.isAnonymous()) {
            return user;
        }
        return getUserFromContext();
    }

    private static User getUserFromSession() {
        String userId = session().get(SESSION_USERID);
        String userKey = session().get(SESSION_KEY);
        if (userId == null) {
            return User.anonymous;
        }
        if (!StringUtils.isNumeric(userId)) {
            return invalidSession();
        }
        User user = null;
        if ((userKey != null && Long.valueOf(userId) != null)){
            user = CacheStore.yonaUsers.getIfPresent(Long.valueOf(userId));
        }
        if (user == null || user.isLocked()) {
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
        User foundUser = (User) Http.Context.current().args.get(TOKEN_USER);

        if(foundUser.isLocked()) {
            processLogout();
            return User.anonymous;
        }

        return foundUser;
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
    public static Result userFiles(){
        final int USER_FILES_COUNT_PER_PAGE = 50;
        String pageNumString = request().getQueryString("pageNum");
        String filter = request().getQueryString("filter");
        int pageNum = 1;

        if (StringUtils.isNotEmpty(pageNumString)){
            pageNum = Integer.parseInt(pageNumString);
        }

        Page<Attachment> page = Attachment.findByUser(currentUser(), USER_FILES_COUNT_PER_PAGE, pageNum, filter);
        return ok(userFiles.render("User Files", page));
    }

    @AnonymousCheck
    public static Result userInfo(String loginId, int daysAgo, String selected) {
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

        List<Issue> issues = new ArrayList<>();
        List<PullRequest> pullRequests = new ArrayList<>();
        List<Project> projects = new ArrayList<>();
        Map<Long, Boolean> projectAclMap = new HashMap<>();

        if (!Application.HIDE_PROJECT_LISTING || !UserApp.currentUser().isAnonymous()) {
            projects = collectProjects(user, projectAclMap);
            issues = getAclValidatedIssues(
                    Issue.findRecentlyIssuesByDaysAgo(user, daysAgo),
                    projectAclMap);
            pullRequests = getAclValidatedPullRequests(
                    PullRequest.findOpendPullRequestsByDaysAgo(user, daysAgo),
                    projectAclMap);

            sortByLastPushedDateAndName(projects);
        }

        if (user.isAnonymous()) {
            return notFound(ErrorViews.NotFound.render("user.notExists.name"));
        }
        return ok(view.render(user, projects, issues, pullRequests, daysAgo, selected));
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

    private static List<PullRequest> getAclValidatedPullRequests(List<PullRequest> pullRequests, Map<Long, Boolean> projectAcl) {
        List<PullRequest> aclValidatedPullRequests = new ArrayList<>();
        for (PullRequest pullRequest : pullRequests) {
            if(projectAcl.getOrDefault(pullRequest.toProject.id, false)) {
                aclValidatedPullRequests.add(pullRequest);
            } else {
                if (AccessControl.isAllowed(UserApp.currentUser(), pullRequest.toProject.asResource(), Operation.READ)) {
                    aclValidatedPullRequests.add(pullRequest);
                    projectAcl.putIfAbsent(pullRequest.toProject.id, true);
                } else {
                    projectAcl.putIfAbsent(pullRequest.toProject.id, false);
                }
            }
        }
        return aclValidatedPullRequests;
    }

    private static List<Issue> getAclValidatedIssues(List<Issue> issues, Map<Long, Boolean> projectAcl) {
        List<Issue> aclValidatedIssues = new ArrayList<>();

        for (Issue issue : issues) {
            if(projectAcl.getOrDefault(issue.project.id, false)) {
                aclValidatedIssues.add(issue);
            } else {
                if (AccessControl.isAllowed(UserApp.currentUser(), issue.project.asResource(), Operation.READ)) {
                    aclValidatedIssues.add(issue);
                    projectAcl.putIfAbsent(issue.project.id, true);
                } else {
                    projectAcl.putIfAbsent(issue.project.id, false);
                }
            }
        }
        return aclValidatedIssues;
    }

    private static void sortIssues(List<Issue> issues) {
        Collections.sort(issues, new Comparator<Issue>() {
            @Override
            public int compare(Issue i1, Issue i2) {
                return i2.updatedDate.compareTo(i1.updatedDate);
            }
        });
    }

    private static void sortPullRequests(List<PullRequest> pullRequests) {
        Collections.sort(pullRequests, new Comparator<PullRequest>() {
            @Override
            public int compare(PullRequest p1, PullRequest p2) {
                return p2.updated.compareTo(p1.updated);
            }
        });
    }

    private static List<Project> collectProjects(User user, Map<Long, Boolean> projectAcl) {
        List<Project> projectCollection = new ArrayList<>();
        addProjectNotDupped(projectCollection, Project.findProjectsByMember(user.id), projectAcl);
        return projectCollection;
    }

    private static void addProjectNotDupped(List<Project> target, List<Project> foundProjects,
                                            Map<Long, Boolean> projectAcl) {
        for (Project project : foundProjects) {
            if (target.contains(project)) {
                continue;
            }

            if (projectAcl.containsKey(project.id)) {
                if (projectAcl.get(project.id)) {
                    target.add(project);
                }
            } else {
                if(AccessControl.isAllowed(UserApp.currentUser(), project.asResource(), Operation.READ)) {
                    target.add(project);
                    projectAcl.putIfAbsent(project.id, true);
                } else {
                    projectAcl.putIfAbsent(project.id, false);
                }
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
            case TOKEN_RESET:
                user.token = null;
            case TOKEN:
                if( StringUtils.isEmpty(user.token)){
                    user.token = new Sha256Hash(LocalDateTime.now().toString()).toBase64();
                    user.save();
                }
                return ok(edit_token.render(userForm, user));
            case PROFILE:
                return ok(edit.render(userForm, user));
            default:
                return ok(edit.render(userForm, user));
        }
    }

    public static boolean isUsingEmailVerification() {
        return usingEmailVerification;
    }

    private enum UserInfoFormTabType {
        PROFILE("profile"),
        PASSWORD("password"),
        NOTIFICATIONS("notifications"),
        EMAILS("emails"),
        TOKEN("token"),
        TOKEN_RESET("token_reset");

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
        String newName = defaultSanitize(userForm.data().get("name"));
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
        user.name = HtmlUtil.defaultSanitize(newName);

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
        CacheStore.yonaUsers.put(user.id, user);
        return redirect(routes.UserApp.userInfo(user.loginId, DAYS_AGO, DEFAULT_SELECTED_TAB));
    }

    @Transactional
    public static Result leave(String userName, String projectName) {
        ProjectApp.deleteMember(userName, projectName, UserApp.currentUser().id);
        return redirect(routes.UserApp.userInfo(UserApp.currentUser().loginId, DAYS_AGO, DEFAULT_SELECTED_TAB));
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

    public static User authenticateWithLdap(String loginIdOrEmail, String password) {
        LdapService ldapService = new LdapService();
        try {
            LdapUser ldapUser = ldapService.authenticate(loginIdOrEmail, password);
            play.Logger.debug("l: " + ldapUser);

            User localUserFoundByLdapLogin = User.findByEmail(ldapUser.getEmail());
            if (localUserFoundByLdapLogin.isAnonymous()) {
                return createNewUser(password, ldapUser);
            } else {
                if(!localUserFoundByLdapLogin.isSamePassword(password)) {
                    User.resetPassword(localUserFoundByLdapLogin.loginId, password);
                }

                localUserFoundByLdapLogin.refresh();
                localUserFoundByLdapLogin.name = ldapUser.getDisplayName();
                if(StringUtils.isNotBlank(ldapUser.getEnglishName())){
                    localUserFoundByLdapLogin.englishName = ldapUser.getEnglishName();
                }
                localUserFoundByLdapLogin.isGuest = ldapUser.isGuestUser();
                localUserFoundByLdapLogin.update();
                return localUserFoundByLdapLogin;
            }
        } catch (CommunicationException e) {
            play.Logger.error("Cannot connect to ldap server \n" + e.getMessage());
            e.printStackTrace();
            if(FALLBACK_TO_LOCAL_LOGIN){
                play.Logger.warn("fallback to local login: " + loginIdOrEmail);
                return authenticateWithPlainPassword(loginIdOrEmail, password);
            }
            return User.anonymous;
        } catch (AuthenticationException e) {
            flash(Constants.WARNING, Messages.get("user.login.invalid"));
            play.Logger.warn("login failed \n" + e.getMessage());
            if(FALLBACK_TO_LOCAL_LOGIN){
                play.Logger.warn("fallback to local login: " + loginIdOrEmail);
                return authenticateWithPlainPassword(loginIdOrEmail, password);
            }
            return User.anonymous;
        } catch (NamingException e) {
            play.Logger.error("Cannot connect to ldap server \n" + e.getMessage());
            e.printStackTrace();
            return User.anonymous;
        }
    }

    private static User createNewUser(String password, LdapUser ldapUser) {
        CandidateUser candidateUser = new CandidateUser(
                ldapUser.getDisplayName(),
                ldapUser.getEmail(),
                ldapUser.getUserLoginId(),
                password,
                ldapUser.isGuestUser()
        );
        User created = createUserDelegate(candidateUser);
        if (created.state == UserState.LOCKED) {
            flash(Constants.INFO, "user.signup.requested");
            return User.anonymous;
        }
        return created;
    }

    public static boolean isUsingSignUpConfirm(){
        Configuration config = play.Play.application().configuration();
        Boolean useSignUpConfirm = config.getBoolean("signup.require.admin.confirm");
        if(useSignUpConfirm == null) {
            useSignUpConfirm = config.getBoolean("signup.require.confirm", false); // for compatibility under v1.1
        }
        return useSignUpConfirm;
    }

    public static void setupRememberMe(User user) {
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

    public static User createNewUser(User user) {
        RandomNumberGenerator rng = new SecureRandomNumberGenerator();
        user.passwordSalt = rng.nextBytes().toBase64();
        user.password = hashedPassword(user.password, user.passwordSalt);
        if (isUsingSignUpConfirm() || isUsingEmailVerification()) {
            user.state = UserState.LOCKED;
        } else {
            user.state = UserState.ACTIVE;
        }
        User.create(user);
        Email.deleteOtherInvalidEmails(user.email);
        if (isUsingEmailVerification()) {
            UserVerification.newVerification(user);
            sendMailAfterUserCreation(user);
        }
        return user;
    }

    public static void addUserInfoToSession(User user) {
        if(user.isLocked()){
            return;
        }
        String key = new Sha256Hash(new Date().toString(), ByteSource.Util.bytes(user.passwordSalt), 1024)
                .toBase64();
        CacheStore.yonaUsers.put(user.id, user);
        session(SESSION_USERID, String.valueOf(user.id));
        session(SESSION_LOGINID, user.loginId);
        session(SESSION_USERNAME, user.name);
        session(SESSION_KEY, key);
    }

    public static boolean linkWithExistedOrCreateLocalUser() {
        final UserCredential oAuthUser = UserCredential.findByAuthUserIdentity(PlayAuthenticate
                .getUser(Http.Context.current().session()));
        User user = null;
        if (oAuthUser.loginId == null) {
            user = User.findByEmail(oAuthUser.email);
        } else {
            user = User.findByLoginId(oAuthUser.loginId);
        }

        if(PlayAuthenticate.isLoggedIn(session()) && user.isAnonymous()){
            return !createLocalUserWithOAuth(oAuthUser).isAnonymous();
        } else {
            if (oAuthUser.loginId == null) {
                oAuthUser.loginId = user.loginId;
                oAuthUser.user = user;
                oAuthUser.update();
            }
            UserApp.addUserInfoToSession(user);
        }
        return true;
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

    @AnonymousCheck
    public static Result setDefaultLoginPage() throws IOException, WriteException {
        UserSetting userSetting = UserSetting.findByUser(UserApp.currentUser().id);
        userSetting.loginDefaultPage = request().getQueryString("path");
        userSetting.save();

        ObjectNode json = Json.newObject();
        json.put("defaultLoginPage", userSetting.loginDefaultPage);
        return ok(json);
    }

    public static Result usermenuTabContentList(){
        return ok(views.html.common.usermenu_tab_content_list.render());
    }
}
