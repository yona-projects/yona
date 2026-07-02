package modules;

import controllers.UserApp;
import models.SiteAdmin;
import models.User;
import org.apache.commons.lang3.StringUtils;
import play.data.Form;
import play.http.DefaultActionCreator;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Result;
import utils.AccessLogger;
import utils.Config;
import utils.LegacyRequestContext;
import views.html.welcome.restart;
import views.html.welcome.secret;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.InetAddress;
import java.security.SecureRandom;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static utils.FormUtil.form;
import static play.mvc.Results.badRequest;
import static play.mvc.Results.ok;

@Singleton
public class YonaActionCreator extends DefaultActionCreator {
    private final Provider<YonaRuntime> runtimeProvider;

    @Inject
    public YonaActionCreator(Provider<YonaRuntime> runtimeProvider) {
        this.runtimeProvider = runtimeProvider;
    }

    @Override
    public Action createAction(final Http.Request request, Method actionMethod) {
        YonaRuntime runtime = runtimeProvider.get();
        if (runtime.isSecretInvalid()) {
            if (runtime.isRestartRequired()) {
                return getRestartAction(runtime);
            }
            return getConfigSecretAction(runtime);
        }
        return getDefaultAction(request);
    }

    private Action<Void> getDefaultAction(final Http.Request request) {
        final long start = System.currentTimeMillis();
        return new Action.Simple() {
            @Override
            public CompletionStage<Result> call(Http.Request request) {
                return LegacyRequestContext.withRequest(request, () -> {
                    UserApp.initTokenUser();
                    try {
                        UserApp.updatePreferredLanguage();
                    } catch (Exception e) {
                        play.Logger.warn("Failed to update the preferred language", e);
                    }
                    LegacyRequestContext.response().setHeader("Date", DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneId.of("GMT"))));
                    LegacyRequestContext.response().setHeader("Cache-Control", "no-cache");
                    CompletionStage<Result> promise = delegate.call(request);
                    AccessLogger.log(request, promise, start);
                    return promise;
                });
            }
        };
    }

    private Action<Void> getRestartAction(YonaRuntime runtime) {
        return new Action.Simple() {
            @Override
            public CompletionStage<Result> call(Http.Request request) {
                return LegacyRequestContext.withRequest(request, () -> CompletableFuture.completedFuture(
                        (Result) ok(restart.render(runtime.hasFailedToUpdateSecretKey()))));
            }
        };
    }

    private Action<Void> getConfigSecretAction(YonaRuntime runtime) {
        return new Action.Simple() {
            @Override
            public CompletionStage<Result> call(Http.Request request) {
                return LegacyRequestContext.withRequest(request, () -> {
                    if (request.method().toLowerCase().equals("post")) {
                        Form<User> newSiteAdminUserForm = form(User.class).bindFromRequest(request);

                        if (hasError(newSiteAdminUserForm)) {
                            return CompletableFuture.completedFuture(
                                    (Result) badRequest(secret.render(SiteAdmin.SITEADMIN_DEFAULT_LOGINID, newSiteAdminUserForm)));
                        }

                        User siteAdmin = SiteAdmin.updateDefaultSiteAdmin(newSiteAdminUserForm.get());
                        try {
                            runtime.updateSiteSecretKey(createSeed(siteAdmin.loginId + ":" + siteAdmin.password));
                        } catch (Exception e) {
                            play.Logger.warn("Failed to update secret key", e);
                            runtime.markFailedToUpdateSecretKey();
                        }
                        runtime.requireRestart();
                        return CompletableFuture.completedFuture(
                                (Result) ok(restart.render(runtime.hasFailedToUpdateSecretKey())));
                    }
                    return CompletableFuture.completedFuture(
                            (Result) ok(secret.render(SiteAdmin.SITEADMIN_DEFAULT_LOGINID, utils.FormUtil.form(User.class))));
                });
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

            private boolean hasError(Form<User> newUserForm) {
                String loginId = newUserForm.field("loginId").value().orElse("");
                String password = newUserForm.field("password").value().orElse("");
                String retypedPassword = newUserForm.field("retypedPassword").value().orElse("");
                String email = newUserForm.field("email").value().orElse("");

                if (StringUtils.isBlank(loginId)) {
                    utils.FormUtil.reject(newUserForm, "loginId", "user.wrongloginId.alert");
                }

                if (!loginId.equals("admin")) {
                    utils.FormUtil.reject(newUserForm, "loginId", "user.wrongloginId.alert");
                }

                if (StringUtils.isBlank(password)) {
                    utils.FormUtil.reject(newUserForm, "password", "user.wrongPassword.alert");
                }

                if (!password.equals(retypedPassword)) {
                    utils.FormUtil.reject(newUserForm, "retypedPassword", "user.confirmPassword.alert");
                }

                if (StringUtils.isBlank(email)) {
                    utils.FormUtil.reject(newUserForm, "email", "validation.invalidEmail");
                }

                if (User.isEmailExist(email)) {
                    utils.FormUtil.reject(newUserForm, "email", "user.email.duplicate");
                }

                return newUserForm.hasErrors();
            }
        };
    }

}
