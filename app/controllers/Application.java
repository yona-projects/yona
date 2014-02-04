package controllers;

import models.Project;
import play.Logger;
import play.mvc.Controller;
import play.mvc.Result;
import playRepository.RepositoryService;
import views.html.error.notfound_default;
import views.html.index.index;

import java.io.File;

public class Application extends Controller {

    public static Result index() {
        return ok(index.render(UserApp.currentUser()));
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
        makeUploadFolder();
        makeTestRepository();
        return redirect(routes.Application.index());
    }

    public static Result jsMessages() {
        return ok(jsmessages.JsMessages.generate("Messages")).as("application/javascript");
    }

    private static void makeUploadFolder() {
        new File("public/uploadFiles/").mkdir();
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
}
