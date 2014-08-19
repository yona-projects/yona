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
import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Date;

import com.avaje.ebean.Ebean;
import controllers.SvnApp;
import models.*;

import controllers.UserApp;
import controllers.routes;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.impl.cookie.DateUtils;
import play.Application;
import play.GlobalSettings;
import play.Play;
import play.api.mvc.Handler;
import play.data.Form;
import play.mvc.*;
import play.mvc.Http.RequestHeader;

import utils.AccessControl;
import utils.AccessLogger;
import utils.ErrorViews;
import utils.YamlUtil;

import views.html.welcome.secret;
import views.html.welcome.restart;
import static play.data.Form.form;
import static play.mvc.Results.badRequest;

public class Global extends GlobalSettings {
    private static final String[] INITIAL_ENTITY_NAME = {"users", "roles", "siteAdmins"};
    private final String DEFAULT_SECRET = "VA2v:_I=h9>?FYOH:@ZhW]01P<mWZAKlQ>kk>Bo`mdCiA>pDw64FcBuZdDh<47Ew";

    private boolean isSecretInvalid = false;
    private boolean isRestartRequired = false;

    @Override
    public void onStart(Application app) {
        isSecretInvalid = equalsDefaultSecret();
        insertInitialData();

        PullRequest.onStart();
        NotificationMail.onStart();
        NotificationEvent.onStart();
        Attachment.onStart();
        YobiUpdate.onStart();
        AccessControl.onStart();
    }

    private boolean equalsDefaultSecret() {
        return DEFAULT_SECRET.equals(play.Configuration.root().getString("application.secret"));
    }

    private static void insertInitialData() {
        if (Ebean.find(User.class).findRowCount() == 0) {
            YamlUtil.insertDataFromYaml("initial-data.yml", INITIAL_ENTITY_NAME);
        }
    }

    @Override
    public Action<Void> onRequest(final Http.Request request, Method actionMethod) {
        if (isSecretInvalid) {
            if (isRestartRequired) {
                return getRestartAction();
            } else {
                return getConfigSecretAction();
            }
        } else {
            return getDefaultAction(request);
        }
    }

    @SuppressWarnings("rawtypes")
    private Action<Void> getDefaultAction(final Http.Request request) {
        final long start = System.currentTimeMillis();
        return new Action.Simple() {
            public Result call(Http.Context ctx) throws Throwable {
                UserApp.initTokenUser();
                UserApp.updatePreferredLanguage();
                ctx.response().setHeader("Date", DateUtils.formatDate(new Date()));
                ctx.response().setHeader("Cache-Control", "no-cache");
                Result result = delegate.call(ctx);
                AccessLogger.log(request, result, start);
                return result;
            }
        };
    }

    private Action<Void> getRestartAction() {
        return new Action.Simple() {
            @Override
            public Result call(Http.Context ctx) throws Throwable {
                return ok(restart.render());
            }
        };
    }

    private Action<Void> getConfigSecretAction() {
        return new Action.Simple() {
            @Override
            public Result call(Http.Context ctx) throws Throwable {
                if( ctx.request().method().toLowerCase().equals("post") ) {
                    Form<User> newSiteAdminUserForm = form(User.class).bindFromRequest();

                    if (hasError(newSiteAdminUserForm)) {
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

            private boolean hasError(Form<User> newUserForm) {
                if (StringUtils.isBlank(newUserForm.field("loginId").value())) {
                    newUserForm.reject("loginId", "user.wrongloginId.alert");
                }

                if (!newUserForm.field("loginId").value().equals("admin")) {
                    newUserForm.reject("loginId", "user.wrongloginId.alert");
                }

                if (StringUtils.isBlank(newUserForm.field("password").value())) {
                    newUserForm.reject("password", "user.wrongPassword.alert");
                }

                if (!newUserForm.field("password").value().equals(newUserForm.field("retypedPassword").value())) {
                    newUserForm.reject("retypedPassword", "user.confirmPassword.alert");
                }

                if (StringUtils.isBlank(newUserForm.field("email").value())) {
                    newUserForm.reject("email", "validation.invalidEmail");
                }

                if (User.isEmailExist(newUserForm.field("email").value())) {
                    newUserForm.reject("email", "user.email.duplicate");
                }

                return newUserForm.hasErrors();
            }
        };
    }

    @Override
    public Handler onRouteRequest(RequestHeader request) {
        // If request method is webdav method, SvnApp serves this request
        // because Play2 cannot route them.
        if (SvnApp.isWebDavMethod(request.method())) {
            return routes.ref.SvnApp.service().handler();
        } else {
            return super.onRouteRequest(request);
        }
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
            return super.onError(request, t);
        }
    }

    @Override
    public Result onBadRequest(RequestHeader request, String error) {
        AccessLogger.log(request, null, Http.Status.BAD_REQUEST);
        return badRequest(ErrorViews.BadRequest.render());
    }

}
