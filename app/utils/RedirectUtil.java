package utils;

import actions.support.PathParser;
import models.Project;
import play.libs.F.Promise;
import play.mvc.Http;
import play.mvc.Result;

import javax.annotation.Nonnull;

import static play.mvc.Results.movedPermanently;
import static play.mvc.Results.notFound;

public class RedirectUtil {
    public static Promise<Result> redirect(@Nonnull Project project) {
        PathParser pathParser = new PathParser(Http.Context.current());
        if(project.owner == null || project.name == null){
            play.Logger.error("project.owner or project.name is null! " + project.owner + "/" + project.name);
            Promise.pure((Result)notFound(ErrorViews.NotFound.render("error.notfound", project)));
        }
        String redirectPath = "/" + project.owner + "/" + project.name + "/" + pathParser.restOfPathExceptOwnerAndProjectName();
        play.Logger.info(Http.Context.current().request().path() + " is redirected to " + redirectPath);
        return Promise.pure(movedPermanently(redirectPath));
    }
}
