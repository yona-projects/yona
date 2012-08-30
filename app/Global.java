import java.util.*;

import models.User;
import play.*;
import play.Application;
import play.api.mvc.Handler;
import play.libs.Yaml;
import play.mvc.Http.RequestHeader;
import playRepository.RepositoryService;

import com.avaje.ebean.Ebean;

import controllers.*;

public class Global extends GlobalSettings {
    public void onStart(Application app) {
        InitialData.insert(app);
    }

    @Override
    public Handler onRouteRequest(RequestHeader request) {
        String[] arr = { "PROPFIND", "REPORT", "PROPPATCH", "COPY", "MOVE", "LOCK", "UNLOCK",
                "MKCOL", "VERSION-CONTROL", "MKWORKSPACE", "MKACTIVITY", "CHECKIN", "CHECKOUT",
                "MERGE", "TRACE" };
        for (String key : arr) {
            if (request.method().equalsIgnoreCase(key)) {
                return routes.ref.SvnApp.service().handler();
            }
        }
        return super.onRouteRequest(request);
    }

    static class InitialData {

        public static void insert(Application app) {
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
            }
        }

        public static void makeTestRepository() {
            try {
                
                // RepositoryService.createRepository("hobi", "nForge4java", RepositoryService.VCS_GIT);
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void onStop(Application app) {
        
    }
}
