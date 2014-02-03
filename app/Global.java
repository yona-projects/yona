/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Wansoon Park, Yi EungJun, Suwon Chae
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
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Date;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import models.*;

import com.avaje.ebean.Ebean;

import controllers.UserApp;
import controllers.routes;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.impl.cookie.DateUtils;
import play.Application;
import play.GlobalSettings;
import play.Configuration;
import play.Play;
import play.api.mvc.Handler;
import play.data.Form;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Http.RequestHeader;
import play.mvc.Result;
import play.mvc.Results;

import utils.AccessLogger;
import utils.ErrorViews;
import utils.YamlUtil;

import views.html.welcome.secret;
import views.html.welcome.restart;
import static play.data.Form.form;
import static play.mvc.Results.badRequest;

public class Global extends GlobalSettings {
    public static final String APPLICATION_CONF_DEFAULT = "application.conf.default";
    public static final String APPLICATION_CONF = "application.conf";
    public static final String CONFIG_DIRNAME = "conf";
    private final String DEFAULT_SECRET = "VA2v:_I=h9>?FYOH:@ZhW]01P<mWZAKlQ>kk>Bo`mdCiA>pDw64FcBuZdDh<47Ew";

    private boolean isRestartRequired = false;
    private boolean isValidationRequired = false;

    @Override
    public Configuration onLoadConfig(play.Configuration config, File path, ClassLoader classloader) {
        String basePath = path.getAbsolutePath();
        Path pathToDefaultConfig = Paths.get(basePath, CONFIG_DIRNAME, APPLICATION_CONF_DEFAULT);
        Path pathToConfig = Paths.get(basePath, CONFIG_DIRNAME, APPLICATION_CONF);
        File configFile = pathToConfig.toFile();

        if (!configFile.exists()) {
            try {
                Files.copy(pathToDefaultConfig, pathToConfig);
            } catch (IOException e) {
                play.Logger.error("Failed to initialize configuration", e);
                return null;
            }
            Config parsedConfig = ConfigFactory.parseFileAnySyntax(configFile);
            return new Configuration(ConfigFactory.load(classloader, parsedConfig));
        } else {
            if (!configFile.isFile()) {
                play.Logger.error(
                        "Failed to initialize configuration: " + pathToConfig + " is a directory.");
                return null;
            }
        }

        return null;
    }

    public void onStart(Application app) {
        isValidationRequired = !validateSecret();

        insertInitialData();

        PullRequest.regulateNumbers();

        PullRequest.changeStateToClosed();

        if (notificationEnabled()) {
            NotificationMail.startSchedule();
        }
    }

    private boolean notificationEnabled() {
        play.Configuration config = play.Configuration.root();
        Boolean notificationEnabled = config.getBoolean("notification.bymail.enabled");
        return notificationEnabled == null || notificationEnabled;
    }

    private boolean validateSecret() {
        play.Configuration config = play.Configuration.root();
        String secret = config.getString("application.secret");
        return secret == null || !secret.equals(DEFAULT_SECRET);
    }

    private static void insertInitialData() {
        if (Ebean.find(User.class).findRowCount() == 0) {
            String[] entityNames = {
                "users", "roles", "siteAdmins"
            };
            YamlUtil.insertDataFromYaml("initial-data.yml", entityNames);
        }
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Action onRequest(final Http.Request request, Method actionMethod) {
        if (isValidationRequired) {
            if (!validateSecret()) {
                if (isRestartRequired) {
                    return getRestartAction();
                } else {
                    return getConfigSecretAction();
                }
            } else {
                isValidationRequired = false;
            }
        }

        final long start = System.currentTimeMillis();
        return new Action.Simple() {
            public Result call(Http.Context ctx) throws Throwable {
                if (ctx.session().get(UserApp.SESSION_USERID) == null) {
                    UserApp.isRememberMe();
                }
                ctx.response().setHeader("Date", DateUtils.formatDate(new Date()));
                ctx.response().setHeader("Cache-Control", "no-cache");
                Result result = delegate.call(ctx);
                AccessLogger.log(request, result, start);
                return result;
            }
        };
    }

    /*
     * 사이트 관리자 입력 폼 유효성 체크
     */
    private static void validate(Form<User> newUserForm) {
        // loginId가 빈 값이 들어오면 안된다.
        if (StringUtils.isBlank(newUserForm.field("loginId").value())) {
            newUserForm.reject("loginId", "user.wrongloginId.alert");
        }

        if (!newUserForm.field("loginId").value().equals("admin")) {
            newUserForm.reject("loginId", "user.wrongloginId.alert");
        }

        // password가 빈 값이 들어오면 안된다.
        if (StringUtils.isBlank(newUserForm.field("password").value())) {
            newUserForm.reject("password", "user.wrongPassword.alert");
        }

        // password와 retypedPassword가 일치해야 한다.
        if (!newUserForm.field("password").value().equals(newUserForm.field("retypedPassword").value())) {
            newUserForm.reject("retypedPassword", "user.confirmPassword.alert");
        }

        // email이 빈 값이 들어오면 안된다.
        if (StringUtils.isBlank(newUserForm.field("email").value())) {
            newUserForm.reject("email", "validation.invalidEmail");
        }

        // 중복된 email로 가입할 수 없다.
        if (User.isEmailExist(newUserForm.field("email").value())) {
            newUserForm.reject("email", "user.email.duplicate");
        }
    }

    private Action<Void> getConfigSecretAction() {
        return new Action.Simple() {
            @Override
            public Result call(Http.Context ctx) throws Throwable {
                if( ctx.request().method().toLowerCase().equals("post") ) {
                    Form<User> newSiteAdminUserForm = form(User.class).bindFromRequest();
                    validate(newSiteAdminUserForm);

                    if (newSiteAdminUserForm.hasErrors()) {
                        return badRequest(secret.render(SiteAdmin.SITEADMIN_DEFAULT_LOGINID, newSiteAdminUserForm));
                    }

                    User siteAdmin = SiteAdmin.updateDefaultSiteAdmin(newSiteAdminUserForm.get());
                    replaceSiteSecretKey(createSeed(siteAdmin.password));
                    isRestartRequired = true;
                    return ok(restart.render());
                } else {
                    return ok(secret.render(SiteAdmin.SITEADMIN_DEFAULT_LOGINID, new Form<>(User.class)));
                }
            }
        };
    }

    private String createSeed(String basicSeed) {
        String seed = basicSeed;
        try {
            seed += InetAddress.getLocalHost();
        } catch (Exception e) {
            play.Logger.warn("Failed to get localhost address", e);
        }
        return seed;
    }

    private void replaceSiteSecretKey(String seed) throws IOException {
        SecureRandom random = new SecureRandom(seed.getBytes());
        String secret = new BigInteger(130, random).toString(32);

        Path path = Paths.get("conf/application.conf");
        byte[] bytes = Files.readAllBytes(path);
        String config = new String(bytes);
        config = config.replace(DEFAULT_SECRET, secret);
        Files.write(path, config.getBytes());
    }

    private Action<Void> getRestartAction() {
        return new Action.Simple() {

            @Override
            public Result call(Http.Context ctx) throws Throwable {
                return ok(restart.render());
            }
        };
    }

    @Override
    public Handler onRouteRequest(RequestHeader request) {
        // Route here these webdav methods to be used for serving Subversion
        // repositories, because Play2 cannot route them.
        String[] webdavMethods = { "PROPFIND", "REPORT", "PROPPATCH", "COPY", "MOVE", "LOCK",
                "UNLOCK", "MKCOL", "VERSION-CONTROL", "MKWORKSPACE", "MKACTIVITY", "CHECKIN",
                "CHECKOUT", "MERGE", "TRACE" };
        for (String method : webdavMethods) {
            if (request.method().equalsIgnoreCase(method)) {
                return routes.ref.SvnApp.service().handler();
            }
        }
        return super.onRouteRequest(request);
    }

    public void onStop(Application app) {
    }

    @Override
    public Result onHandlerNotFound(RequestHeader request) {
        AccessLogger.log(request, null, Http.Status.NOT_FOUND);
        return Results.notFound(ErrorViews.NotFound.render());
    }

    @Override
    public Result onError(RequestHeader request, Throwable t) {
        AccessLogger.log(request, null, Http.Status.INTERNAL_SERVER_ERROR);

        if (Play.isProd()) {
            return Results.internalServerError(views.html.error.internalServerError_default.render("error.internalServerError"));
        } else {
            return super.onError(request,  t);
        }
    }

    @Override
    public Result onBadRequest(RequestHeader request, String error) {
        AccessLogger.log(request, null, Http.Status.BAD_REQUEST);
        return badRequest(ErrorViews.BadRequest.render());
    }

}
