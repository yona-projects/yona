/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @author Hwi Ahn
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

import com.avaje.ebean.Page;
import controllers.annotation.AnonymousCheck;
import info.schleichardt.play2.mailplugin.Mailer;
import models.*;
import models.enumeration.State;
import models.enumeration.UserState;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;
import org.eclipse.jgit.api.errors.GitAPIException;
import play.Configuration;
import play.Logger;
import play.db.ebean.Transactional;
import views.html.site.*;
import play.mvc.*;
import utils.*;

import java.util.*;

import static play.libs.Json.toJson;

/**
 * The Class SiteApp.
 */
 @With(SiteManagerAuthAction.class)
@AnonymousCheck
public class SiteApp extends Controller {

    private static final int PROJECT_COUNT_PER_PAGE = 25;
    private static final int POSTING_COUNT_PER_PAGE = 30;
    private static final int ISSUE_COUNT_PER_PAGE = 30;

    /**
     * @return the result
     * @throws EmailException the email exception
     * @see {@link SiteApp#writeMail(String, boolean)}
     */
    public static Result sendMail() throws EmailException{
        SimpleEmail email = new SimpleEmail();

        Map<String, String[]> formData = request().body().asFormUrlEncoded();
        email.setFrom(utils.HttpUtil.getFirstValueFromQuery(formData, "from"));
        email.setSubject(utils.HttpUtil.getFirstValueFromQuery(formData, "subject"));
        email.addTo(utils.HttpUtil.getFirstValueFromQuery(formData, "to"));
        email.setMsg(utils.HttpUtil.getFirstValueFromQuery(formData, "body"));
        email.setCharset("utf-8");

        String errorMessage = null;
        boolean sended;
        String result = Mailer.send(email);
        Logger.info(">>>" + result);
        sended = true;
        return writeMail(errorMessage, sended);
    }

    public static Result writeMail(String errorMessage, boolean sended) {

        Configuration config = play.Play.application().configuration();
        List<String> notConfiguredItems = new ArrayList<>();
        String[] requiredItems = {"smtp.host", "smtp.user", "smtp.password"};
        for(String key : requiredItems) {
            if (config.getString(key) == null) {
                notConfiguredItems.add(key);
            }
        }

        String sender = utils.Config.getEmailFromSmtp();

        return ok(mail.render("title.sendMail", notConfiguredItems, sender, errorMessage, sended));
    }

    public static Result massMail() {
        return ok(massMail.render("title.massMail"));
    }

    /**
     * @param pageNum pager number
     * @param loginId loginId
     * @return the result
     * @see {@link User#findUsers(int, String)}
     */
    public static Result userList(int pageNum, String query) {
        String state = StringUtils.defaultIfBlank(request().getQueryString("state"), UserState.ACTIVE.name());
        UserState userState = UserState.valueOf(state);
        Page<User> users = User.findUsers(pageNum -1, query, userState);
        return ok(userList.render("title.siteSetting", users, userState, query));
    }

    /**
     * @param pageNum page number
     * @return the result
     */
    public static Result postList(int pageNum) {
        Page<Posting> page = Posting.finder.order("createdDate DESC").findPagingList(POSTING_COUNT_PER_PAGE).getPage(pageNum - 1);
        return ok(postList.render("title.siteSetting", page));
    }

    /**
     * @param pageNum page number
     * @return the result
     */
    public static Result issueList(int pageNum) {
        String state = StringUtils.defaultIfBlank(request().getQueryString("state"), State.OPEN.name());
        State currentState = State.valueOf(state.toUpperCase());
        Page<Issue> page = Issue.findIssuesByState(ISSUE_COUNT_PER_PAGE, pageNum - 1, currentState);
        return ok(issueList.render("title.siteSetting", page, currentState));
    }

    /**
     * @param userId the user id
     * @return the result
     * @see {@link Project#isOnlyManager(Long)}
     */
    @Transactional
    public static Result deleteUser(Long userId) {
        if (User.findByLoginId(session().get("loginId")).isSiteManager()){
            if (Project.isOnlyManager(userId)) {
                flash(Constants.WARNING, "site.userList.deleteAlert");
                return forbidden();
            } else {
                User user = User.find.byId(userId);
                for (ProjectUser projectUser : user.projectUser) {
                    projectUser.delete();
                }
                user.changeState(UserState.DELETED);

                return redirect(routes.SiteApp.userList(1, null));
            }
        } else {
            flash(Constants.WARNING, "error.auth.unauthorized.waringMessage");
            return forbidden();
        }
    }

    /**
     * @param projectName the project name
     * @param pageNum page number
     * @return the result
     * @see {@link Project#findByName(String, int, int)}
     */
    public static Result projectList(String projectName, int pageNum) {
        Page<Project> projects = Project.findByName(projectName, PROJECT_COUNT_PER_PAGE, pageNum);
        return ok(projectList.render("title.projectList", projects, projectName));
    }

    /**
     * @param projectId the project id
     * @return the result
     */
    @Transactional
    public static Result deleteProject(Long projectId){
        if( User.findByLoginId(session().get("loginId")).isSiteManager() ){
            Project.find.byId(projectId).delete();
        } else {
            flash(Constants.WARNING, "error.auth.unauthorized.waringMessage");
        }
        return redirect(routes.SiteApp.projectList(StringUtils.EMPTY, 0));
    }

    /**
     * @param loginId the login id
     * @return the result
     */

    public static Result toggleAccountLock(String loginId, String state, String query){
        String stateParam = StringUtils.defaultIfBlank(state, UserState.ACTIVE.name());
        UserState userState = UserState.valueOf(stateParam);

        if(User.findByLoginId(session().get("loginId")).isSiteManager()){
            User targetUser = User.findByLoginId(loginId);
            if (targetUser.isAnonymous()){
                flash(Constants.WARNING, "user.notExists.name");
                return redirect(routes.SiteApp.userList(0, null));
            }
            if (targetUser.state == UserState.ACTIVE) {
                targetUser.changeState(UserState.LOCKED);
            } else {
                targetUser.changeState(UserState.ACTIVE);
            }
            return ok(userList.render("title.siteSetting", User.findUsers(0, query, userState), userState, query));
        }
        flash(Constants.WARNING, "error.auth.unauthorized.waringMessage");
        return redirect(routes.Application.index());
    }

    public static Result mailList() {
        Set<String> emails = new HashSet<>();
        Map<String, String[]> projects = request().body().asFormUrlEncoded();
        if(!UserApp.currentUser().isSiteManager()) {
            return forbidden(ErrorViews.Forbidden.render("error.auth.unauthorized.waringMessage"));
        }

        if (!request().accepts("application/json")) {
            return status(Http.Status.NOT_ACCEPTABLE);
        }

        if (projects == null) {
            return ok(toJson(new HashSet<String>()));
        }

        if (projects.containsKey("all")) {
            if (projects.get("all")[0].equals("true")) {
                for(User user : User.find.findList()) {
                    emails.add(user.email);
                }
            }
        } else {
            for(String[] projectNames : projects.values()) {
                String projectName = projectNames[0];
                String[] parts = projectName.split("/");
                String owner = parts[0];
                String name = parts[1];
                Project project = Project.findByOwnerAndProjectName(owner, name);
                for (ProjectUser projectUser : ProjectUser.findMemberListByProject(project.id)) {
                    Logger.debug(projectUser.user.email);
                    emails.add(projectUser.user.email);
                }
            }
        }

        return ok(toJson(emails));
    }

    /**
     * Hide the notification for Yobi updates.
     */
    public static Result unwatchUpdate() {
        YobiUpdate.isWatched = false;
        return ok();
    }

    /**
     * Show the page to update Yobi.
     */
    public static Result update() throws GitAPIException {
        String currentVersion = null;
        Exception exception = null;

        try {
            currentVersion = Config.getCurrentVersion();
            YobiUpdate.refreshVersionToUpdate();
        } catch (Exception e) {
            exception = e;
        }

        return ok(update.render("title.siteSetting", currentVersion,
                    YobiUpdate.versionToUpdate, exception));
    }

    /**
     * Diagnose Yobi
     * @return
     */
    public static Result diagnose() {
        return ok(diagnostic.render("title.siteSetting", Diagnostic.checkAll()));
    }
}
