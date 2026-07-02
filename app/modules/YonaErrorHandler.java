package modules;

import org.apache.commons.lang3.StringUtils;
import com.typesafe.config.Config;
import play.Environment;
import play.api.OptionalSourceMapper;
import play.api.routing.Router;
import play.http.DefaultHttpErrorHandler;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Results;
import utils.AccessLogger;
import utils.ErrorViews;

import javax.inject.Inject;
import javax.inject.Provider;
import jakarta.persistence.PersistenceException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static play.mvc.Results.badRequest;

public class YonaErrorHandler extends DefaultHttpErrorHandler {
    private final Environment environment;

    @Inject
    public YonaErrorHandler(Config configuration, Environment environment,
                            OptionalSourceMapper sourceMapper, Provider<Router> routes) {
        super(configuration, environment, sourceMapper, routes);
        this.environment = environment;
    }

    @Override
    protected CompletionStage<Result> onNotFound(Http.RequestHeader request, String message) {
        AccessLogger.log(request, null, Http.Status.NOT_FOUND);
        return CompletableFuture.completedFuture((Result) Results.notFound(ErrorViews.NotFound.render()));
    }

    @Override
    protected CompletionStage<Result> onBadRequest(Http.RequestHeader request, String message) {
        AccessLogger.log(request, null, Http.Status.BAD_REQUEST);
        return CompletableFuture.completedFuture((Result) badRequest(ErrorViews.BadRequest.render()));
    }

    @Override
    public CompletionStage<Result> onServerError(Http.RequestHeader request, Throwable throwable) {
        AccessLogger.log(request, null, Http.Status.INTERNAL_SERVER_ERROR);

        if (environment.isProd()) {
            String messageKey;
            if (throwable.getCause() instanceof PersistenceException && StringUtils.contains(throwable.getMessage(), "timed out")) {
                messageKey = "error.timeout";
            } else {
                messageKey = "error.internalServerError";
            }
            return CompletableFuture.completedFuture((Result) Results.internalServerError(views.html.error.internalServerError_default.render(messageKey)));
        }

        return super.onServerError(request, throwable);
    }
}
