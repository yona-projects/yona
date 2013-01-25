package controllers;

import java.io.File;
import java.util.List;
import java.util.Map;

import com.avaje.ebean.Ebean;
import models.Project;
import models.User;

import org.h2.util.StringUtils;

import play.Logger;
import play.cache.Cached;
import play.libs.Yaml;
import play.mvc.Controller;
import play.mvc.Result;
import playRepository.RepositoryService;
import views.html.index;

public class Application extends Controller {

    public static Result index() {
    	UserApp.isRememberMe();

        String orderString = request().getQueryString("order");

        if (session().containsKey(UserApp.SESSION_LOGINID)) {
        	String userId = session().get("userId");
        	if(StringUtils.isNumber(userId)) {
        		List<Project> projects = Project.findProjectsByMemberWithFilter(Long.parseLong(userId), orderString);
        		return ok(index.render(projects, orderString));
        	}
        }

        return ok(index.render(null, null));
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
}