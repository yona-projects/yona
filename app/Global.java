import java.util.List;
import java.util.Map;

import models.User;

import com.avaje.ebean.Ebean;

import controllers.routes;
import play.Application;
import play.GlobalSettings;
import play.api.mvc.Handler;
import play.libs.Yaml;
import play.mvc.Http.RequestHeader;

public class Global extends GlobalSettings {
    public void onStart(Application app) {
    	insertDefaults();
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
    
    @Override
    public Handler onRouteRequest(RequestHeader request) {
        // Route here these webdav methods to be used for serving Subversion
        // repositories, because Play2 cannot route them.
        String[] webdavMethods = { "PROPFIND", "REPORT", "PROPPATCH", "COPY", "MOVE", "LOCK",
                "UNLOCK", "MKCOL", "VERSION-CONTROL", "MKWORKSPACE", "MKACTIVITY", "CHECKIN",
                "CHECKOUT", "MERGE", "TRACE" };
        for (String method : webdavMethods) {
            if (request.method().equalsIgnoreCase(method)) {
                return routes.ref.SvnApp.service().handler();
            }
        }
        return super.onRouteRequest(request);
    }

    public void onStop(Application app) {
    }
}
