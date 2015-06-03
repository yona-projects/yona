/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Wansoon Park, Yi EungJun, Suwon Chae
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

import com.avaje.ebean.Ebean;
import com.typesafe.config.ConfigFactory;
import controllers.SvnApp;
import controllers.UserApp;
import controllers.routes;
import mailbox.MailboxService;
import models.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.impl.cookie.DateUtils;
import play.Application;
import play.Configuration;
import play.GlobalSettings;
import play.Play;
import play.api.mvc.Handler;
import play.data.Form;
import play.libs.F.Promise;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Http.RequestHeader;
import play.mvc.Result;
import play.mvc.Results;
import utils.*;
import views.html.welcome.restart;
import views.html.welcome.secret;

import javax.annotation.Nonnull;
import javax.persistence.PersistenceException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Date;

import static play.data.Form.form;
import static play.mvc.Results.badRequest;


public class Global extends GlobalSettings {
    private static final String[] INITIAL_ENTITY_NAME = {"users", "roles", "siteAdmins"};
    private final String DEFAULT_SECRET = "VA2v:_I=h9>?FYOH:@ZhW]01P<mWZAKlQ>kk>Bo`mdCiA>pDw64FcBuZdDh<47Ew";

    private boolean isSecretInvalid = false;
    private boolean isRestartRequired = false;
    private MailboxService mailboxService = new MailboxService();
    private boolean hasFailedToUpdateSecretKey = false;

    private ConfigFile configFile = new ConfigFile("config", "application.conf");
    private ConfigFile loggerConfigFile = new ConfigFile("logger", "application-logger.xml");

    @Override
    public Configuration onLoadConfig(play.Configuration config, File path, ClassLoader classloader) {
        initLoggerConfig();
        return initConfig(classloader);
    }

    /**
     * Creates application.conf by default if necessary
     *
     * @param   classloader
     * @return  the configuration read from the created file,
     *          or null if this method didn't create the file.
     */
    private Configuration initConfig(ClassLoader classloader) {
        if (configFile.isLocationSpecified()) {
            return null;
        }

        try {
            if (configFile.getPath().toFile().exists()) {
                return null;
            }
        } catch (URISyntaxException e) {
            play.Logger.error("Failed to check whether the config file exists", e);
            return null;
        }

        try {
            configFile.createByDefault();
            return new Configuration(ConfigFactory.load(classloader,
                    ConfigFactory.parseFileAnySyntax(configFile.getPath().toFile())));
        } catch (Exception e) {
            play.Logger.error("Failed to initialize configuration", e);
            return null;
        }
    }

    /**
     * Creates application-logger.xml by default if necessary
     *
     * Note: This method creates application-logger.xml even if logger.xml exists.
     */
    private void initLoggerConfig() {
        try {
            if (!loggerConfigFile.isLocationSpecified() && !loggerConfigFile.getPath().toFile().exists()) {
                try {
                    loggerConfigFile.createByDefault();
                } catch (Exception e) {
                    play.Logger.error("Failed to initialize logger configuration", e);
                }
            }
        } catch (URISyntaxException e) {
            play.Logger.error("Failed to check whether the logger config file exists", e);
        }
    }

    @Override
    public void onStart(Application app) {
        isSecretInvalid = equalsDefaultSecret();
        insertInitialData();

        Config.onStart();
        Property.onStart();
        PullRequest.onStart();
        NotificationMail.onStart();
        NotificationEvent.onStart();
        Attachment.onStart();
        AccessControl.onStart();

        if (!isSecretInvalid) {
            YobiUpdate.onStart();
            mailboxService.start();
        }
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
            public Promise<Result> call(Http.Context ctx) throws Throwable {
                UserApp.initTokenUser();
                try {
                    UserApp.updatePreferredLanguage();
                } catch (Exception e) {
                    play.Logger.warn("Failed to update the preferred language", e);
                }
                ctx.response().setHeader("Date", DateUtils.formatDate(new Date()));
                ctx.response().setHeader("Cache-Control", "no-cache");
                Promise<Result> promise = delegate.call(ctx);
                AccessLogger.log(request, promise, start);
                return promise;
            }
        };
    }

    private Action<Void> getRestartAction() {
        return new Action.Simple() {
            @Override
            public Promise<Result> call(Http.Context ctx) throws Throwable {
                return Promise.pure((Result) ok(restart.render(hasFailedToUpdateSecretKey)));
            }
        };
    }

    private Action<Void> getConfigSecretAction() {
        return new Action.Simple() {
            @Override
            public Promise<Result> call(Http.Context ctx) throws Throwable {
                if( ctx.request().method().toLowerCase().equals("post") ) {
                    Form<User> newSiteAdminUserForm = form(User.class).bindFromRequest();

                    if (hasError(newSiteAdminUserForm)) {
                        return Promise.pure((Result) badRequest(secret.render(SiteAdmin.SITEADMIN_DEFAULT_LOGINID, newSiteAdminUserForm)));
                    }

                    User siteAdmin = SiteAdmin.updateDefaultSiteAdmin(newSiteAdminUserForm.get());
                    try {
                        updateSiteSecretKey(createSeed(siteAdmin.loginId + ":" + siteAdmin.password));
                    } catch (Exception e) {
                        play.Logger.warn("Failed to update secret key", e);
                        hasFailedToUpdateSecretKey = true;
                    }
                    isRestartRequired = true;
                    return Promise.pure((Result) ok(restart.render(hasFailedToUpdateSecretKey)));
                } else {
                    return Promise.pure((Result) ok(secret.render(SiteAdmin.SITEADMIN_DEFAULT_LOGINID, new Form<>(User.class))));
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

            private void updateSiteSecretKey(String seed) throws Exception {
                SecureRandom random = new SecureRandom(seed.getBytes(Config.getCharset()));
                String secret = new BigInteger(130, random).toString(32);

                if (configFile.isExternal()) {
                    throw new Exception("Cowardly refusing to update an external file: " + configFile.getPath());
                }

                byte[] bytes = Files.readAllBytes(configFile.getPath());
                String config = new String(bytes, Config.getCharset());
                config = config.replace(DEFAULT_SECRET, secret);
                Files.write(configFile.getPath(), config.getBytes(Config.getCharset()));
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
        mailboxService.stop();
    }

    @Override
    public Promise<Result> onHandlerNotFound(RequestHeader request) {
        AccessLogger.log(request, null, Http.Status.NOT_FOUND);
        return Promise.pure((Result) Results.notFound(ErrorViews.NotFound.render()));
    }

    @Override
    public Promise<Result> onError(RequestHeader request, Throwable t) {
        AccessLogger.log(request, null, Http.Status.INTERNAL_SERVER_ERROR);

        if (Play.isProd()) {
            String messageKey;
            if (t.getCause() instanceof PersistenceException && StringUtils.contains(t.getMessage(), "timed out")){
                messageKey = "error.timeout";
            } else {
                messageKey = "error.internalServerError";
            }
            return Promise.pure((Result) Results.internalServerError(views.html.error.internalServerError_default.render(messageKey)));
        } else {
            return super.onError(request, t);
        }
    }

    @Override
    public Promise<Result> onBadRequest(RequestHeader request, String error) {
        AccessLogger.log(request, null, Http.Status.BAD_REQUEST);
        return Promise.pure((Result) badRequest(ErrorViews.BadRequest.render()));
    }

    private static class ConfigFile {
        private static final String CONFIG_DIRNAME = "conf";
        private final String fileName;
        private final String defaultFileName;
        private final String propertyGroup;

        ConfigFile(String propertyGroup, String fileName) {
            this.propertyGroup = propertyGroup;
            this.fileName = fileName;
            this.defaultFileName = fileName + ".default";
        }

        String getProperty(@Nonnull String key) {
            return System.getProperty(propertyGroup + "." + key);
        }

        String getProperty(@Nonnull String key, String defaultValue) {
            return System.getProperty(propertyGroup + "." + key, defaultValue);
        }

        /**
         * The location of the config file is specified by user
         */
        boolean isLocationSpecified() {
            return (getProperty("resource") != null)
                || (getProperty("file") != null)
                || (getProperty("url") != null);
        }

        void createByDefault() throws IOException, URISyntaxException {
            InputStream stream = Config.class.getClassLoader().getResourceAsStream(defaultFileName);

            getPath().toFile().getParentFile().mkdirs();

            if (stream != null) {
                Files.copy(stream, getPath());
            } else {
                Files.copy(getDirectoryPath().resolve(defaultFileName), getPath());
            }
        }

        /**
         * @return the path to the configuration file
         * @throws java.lang.IllegalStateException
         */
        Path getPath() throws URISyntaxException {
            if (getProperty("url") != null) {
                return Paths.get(new URI(getProperty("url")));
            }

            if (getProperty("file") != null) {
                return Paths.get(getProperty("file"));
            }

            String filename = getProperty("resource", fileName);

            return getDirectoryPath().resolve(filename);
        }

        /**
         * @return the path to the directory to store configuration files
         */
        static Path getDirectoryPath() {
            return Paths.get(Config.getYobiHome(""), CONFIG_DIRNAME);
        }

        boolean isExternal() throws IOException, URISyntaxException {
            return !FileUtil.isSubpathOf(getPath(), getDirectoryPath()) &&
                   !FileUtil.isSubpathOf(getPath(), Paths.get(Config.getYobiHome()));
        }
    }
}
