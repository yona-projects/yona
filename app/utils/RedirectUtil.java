package utils;

import actions.support.PathParser;
import models.Project;
import java.util.concurrent.*;
import play.mvc.Result;

import javax.annotation.Nonnull;

import static play.mvc.Results.movedPermanently;
import static play.mvc.Results.notFound;

public class RedirectUtil {
    public static CompletionStage<Result> redirect(@Nonnull Project project) {
        PathParser pathParser = new PathParser(LegacyRequestContext.request());
        if(project.owner == null || project.name == null){
            play.Logger.error("project.owner or project.name is null! " + project.owner + "/" + project.name);
            CompletableFuture.completedFuture((Result)notFound(ErrorViews.NotFound.render("error.notfound", project)));
        }
        String redirectPath = "/" + project.owner + "/" + project.name + "/" + pathParser.restOfPathExceptOwnerAndProjectName();
        play.Logger.info(LegacyRequestContext.request().path() + " is redirected to " + redirectPath);
        return CompletableFuture.completedFuture(movedPermanently(redirectPath));
    }
}
