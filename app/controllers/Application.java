/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp.
 * https://yona.io
 **/
package controllers;

import com.feth.play.module.pa.PlayAuthenticate;
import controllers.annotation.AnonymousCheck;
import jsmessages.JsMessages;
import models.Project;
import models.User;
import models.UserCredential;
import models.UserSetting;
import org.apache.commons.lang3.StringUtils;
import play.Logger;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import playRepository.RepositoryService;
import views.html.error.notfound_default;
import views.html.index.index;
import views.html.index.notifications;
import views.html.index.sidebar;

import static com.feth.play.module.pa.controllers.Authenticate.*;

public class Application extends Controller {
    public static final String FLASH_MESSAGE_KEY = "message";
    public static final String FLASH_ERROR_KEY = "error";
    public static boolean useSocialNameSync = play.Configuration.root().getBoolean("application.use.social.login.name.sync", false);
    public static String GITHUB_NAME = play.Configuration.root().getString("application.social.login.github.name", "github");
    public static String ALLOWED_SENDING_MAIL_DOMAINS = play.Configuration.root().getString("application.allowed.sending.mail.domains", "");
    public static String PRIVATE_IS_DEFAULT = play.Configuration.root().getString("project.default.scope.when.create", "public");
    public static boolean HIDE_PROJECT_LISTING = play.Configuration.root().getBoolean("application.hide.project.listing", false);
    public static boolean SEND_YONA_USAGE =play.Configuration.root().getBoolean("application.send.yona.usage", true);
    public static String GUEST_USER_LOGIN_ID_PREFIX  = play.Configuration.root().getString("application.guest.user.login.id.prefix ", "");
    public static String LOGIN_PAGE_LOGINID_PLACEHOLDER  = play.Configuration.root().getString("application.login.page.loginId.placeholder", "");
    public static String LOGIN_PAGE_PASSWORD_PLACEHOLDER  = play.Configuration.root().getString("application.login.page.password.placeholder", "");
    public static boolean SHOW_USER_EMAIL = play.Configuration.root().getBoolean("application.show.user.email", true);
    public static String NAVBAR_CUSTOM_LINK_NAME  = play.Configuration.root().getString("application.navbar.custom.link.name", "");
    public static String NAVBAR_CUSTOM_LINK_URL  = play.Configuration.root().getString("application.navbar.custom.link.url", "");

    @AnonymousCheck
    public static Result index() {
        User user = UserApp.currentUser();
        UserSetting userSetting = UserSetting.findByUser(user.id);
        if(!user.isAnonymous() && StringUtils.isNotBlank(userSetting.loginDefaultPage)) {
            return redirect(userSetting.loginDefaultPage);
        }
        return ok(index.render(UserApp.currentUser()));
    }

    @AnonymousCheck
    public static Result sidebar() {
        return ok(sidebar.render(UserApp.currentUser()));
    }

    @AnonymousCheck
    public static Result notifications() {
        return ok(notifications.render(UserApp.currentUser()));
    }

    public static Result oAuth(final String provider) {
        return authenticate(provider);
    }

    public static Result oAuthLogout() {
        UserApp.logout();
        logout();
        return returnToReferer();
    }

    public static Result oAuthDenied(final String providerKey) {
        noCache(response());
        flash(FLASH_ERROR_KEY,
                "You need to accept the OAuth connection in order to use this website!");
        return redirect(routes.Application.index());
    }

    public static UserCredential getLocalUser(final Http.Session session) {
        final UserCredential localUser = UserCredential.findByAuthUserIdentity(PlayAuthenticate
                .getUser(session));
        return localUser;
    }

    public static UserCredential getLocalUser() {
        final UserCredential localUser = UserCredential.findByAuthUserIdentity(PlayAuthenticate
                .getUser(session()));
        return localUser;
    }

    public static Result removeTrailer(String paths){
        String path = request().path();
        if( path.charAt(path.length()-1) == '/' ) {
            path = path.substring(0, path.length() - 1);
        } else {
            Logger.error("Unexpected url call : " + request().path());
            return notFound(notfound_default.render("error.notfound"));
        }
        Logger.debug("Trailing slash removed and redirected: " + request().path() + " to " + path );
        return redirect(path);
    }

    public static Result init() {
        makeTestRepository();
        return redirect(routes.Application.index());
    }

    static final JsMessages messages = JsMessages.create(play.Play.application());

    public static Result jsMessages() {
        return ok(messages.generate("Messages")).as("application/javascript");
    }

    private static void makeTestRepository() {
        for (Project project : Project.find.all()) {
            Logger.debug("makeTestRepository: " + project.name);
            try {
                RepositoryService.createRepository(project);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static Result navi() {
        return ok(index.render(UserApp.currentUser()));
    }

    public static Result UIKit(){
        return ok(views.html.help.UIKit.render());
    }

    public static Result fake() {
        // Do not call this.
        return badRequest();
    }

    public static Result returnToReferer() {
        return redirect(StringUtils.defaultString(request().getHeader("referer"), index().toString()));
    }
}
