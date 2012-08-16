import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.tmatesoft.svn.util.SVNLogType;

import models.User;


import anorm.SimpleSql;
import anorm.Sql;
import anorm.SqlQuery;

import com.avaje.ebean.CallableSql;
import com.avaje.ebean.Ebean;
import com.avaje.ebean.Query;
import com.avaje.ebean.RawSql;
import com.avaje.ebean.RawSqlBuilder;

import controllers.SvnApp;
import controllers.routes;

import play.Application;
import play.GlobalSettings;
import play.Logger;
import play.api.mvc.Handler;
import play.core.Router;
import play.libs.Yaml;

import play.mvc.Http.RequestHeader;

public class Global extends GlobalSettings {
    public void onStart(Application app) {
        InitialData.insert(app);
    }
    
        
   @Override
    public Handler onRouteRequest(RequestHeader request) {
       String [] arr = {"PROPFIND","REPORT","PROPPATCH","COPY","MOVE","LOCK","UNLOCK","MKCOL","VERSION-CONTROL","MKWORKSPACE","MKACTIVITY","CHECKIN","CHECKOUT","MERGE","TRACE"};
       for(String key : arr){
           if(request.method().equalsIgnoreCase(key)) {
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
                for(Object role: all.get("roles")) {
                    Ebean.saveManyToManyAssociations(role, "permissions");
                }
                Ebean.save(all.get("projectUsers"));
            }
        }
    }

    public void onStop(Application app) {
    }
}
