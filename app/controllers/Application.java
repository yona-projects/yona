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

	@Cached(key = "index")
    public static Result index() {  
    	UserApp.isRememberMe();
    	
        if (session().containsKey("userId")) {        	
        	String userId = session().get("userId");
        	if(StringUtils.isNumber(userId)) {
        		List<Project> projects = Project.findProjectsByMember(Long.parseLong(userId));
        		return ok(index.render(projects));
        	}
        }

        return ok(index.render(null));
    }

    public static Result init() {
        insertDefaults();
        makeUploadFolder();
        makeTestRepository();
        return redirect(routes.Application.index());
    }

    public static Result jsMessages() {
        return ok(jsmessages.JsMessages.generate("Messages")).as("application/javascript");
    }

    private static void insertDefaults() {
        if (Ebean.find(User.class).findRowCount() == 0) {
            @SuppressWarnings("unchecked")
            Map<String, List<Object>> all = (Map<String, List<Object>>) Yaml
                    .load("initial-data.yml");

            Ebean.save(all.get("users"));
            Ebean.save(all.get("projects"));
            Ebean.save(all.get("milestones"));
            Ebean.save(all.get("issues"));
            Ebean.save(all.get("issueComments"));
            Ebean.save(all.get("posts"));
            Ebean.save(all.get("comments"));
            Ebean.save(all.get("permissions"));

            Ebean.save(all.get("roles"));
            for (Object role : all.get("roles")) {
                Ebean.saveManyToManyAssociations(role, "permissions");
            }
            Ebean.save(all.get("projectUsers"));

            Ebean.save(all.get("taskBoards"));
            Ebean.save(all.get("lines"));
            Ebean.save(all.get("cards"));
            Ebean.save(all.get("labels"));
            Ebean.save(all.get("checkLists"));
        }
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