import java.util.List;
import java.util.Map;

import models.Issue;
import models.Posting;
import models.Project;
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
            String initFileName = "initial-data.yml";

            @SuppressWarnings("unchecked")
            Map<String, List<Object>> all = (Map<String, List<Object>>) Yaml
                    .load(initFileName);

            String[] entityNames = {
                "users", "projects", "milestones",
                "issues", "issueComments",
                "postings", "postingComments",
                "roles", "projectUsers",
                "siteAdmins"
            };

            // Check whether every entities exist.
            for (String entityName : entityNames) {
                if (all.get(entityName) == null) {
                    throw new RuntimeException("Failed to find the '" + entityName
                            + "' entity in '" + initFileName + "'");
                }
            }

            for (String entityName : entityNames) {
                Ebean.save(all.get(entityName));
            }

            // Do numbering for issues and postings.
            for (Project project : Project.find.findList()) {
                List<Issue> issues = Issue.finder.where()
                        .eq("project.id", project.id).orderBy("id desc")
                        .findList();

                for (Issue issue: issues) {
                    issue.save();
                }

                List<Posting> postings = Posting.finder.where()
                        .eq("project.id", project.id).orderBy("id desc")
                        .findList();

                for (Posting posting: postings) {
                    posting.save();
                }
            }

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
