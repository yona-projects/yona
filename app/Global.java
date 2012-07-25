import java.util.List;
import java.util.Map;

import models.User;

import com.avaje.ebean.Ebean;

import play.Application;
import play.GlobalSettings;
import play.libs.Yaml;

public class Global extends GlobalSettings {
    public void onStart(Application app) {
        InitialData.insert(app);
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
                for(Object role: all.get("roles")) {
                    // Insert the role/permission relation
                    Ebean.saveManyToManyAssociations(role, "permissions");
                }
                Ebean.save(all.get("projectUsers"));
            }
        }

    }

    public void onStop(Application app) {
    }
}
