import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.tmatesoft.svn.util.SVNLogType;

import models.User;


import com.avaje.ebean.Ebean;

import controllers.SvnApp;
import controllers.routes;

import play.Application;
import play.GlobalSettings;
import play.Logger;
import play.api.mvc.Handler;
import play.core.Router;
import play.libs.Yaml;
import play.mvc.Action;
import play.mvc.Http.Context;
import play.mvc.Http.Request;
import play.mvc.Http.RequestHeader;
import play.mvc.Result;

public class Global extends GlobalSettings {
    public void onStart(Application app) {
        InitialData.insert(app);
    }
    
        
   @Override
    public Handler onRouteRequest(RequestHeader request) {
       String [] arr = {"PROPFIND","REPORT","PROPPATCH","COPY","MOVE","LOCK","UNLOCK","MKCOL","VERSION-CONTROL","MKWORKSPACE","MKACTIVITY","CHECKIN","CHECKOUT","MERGE","TRACE"};
       for(String key : arr){
           if(request.method().equalsIgnoreCase(key)) {
               Logger.debug("onRouteRequest " + request.method());
               return routes.ref.SvnApp.service().handler();
           }
       }
       Logger.debug("onRouteRequest 2 " + request.method());
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
                    // Insert the role/permission relation
                    Ebean.saveManyToManyAssociations(role, "permissions");
                }
                Ebean.save(all.get("projectUsers"));
                
//                java.util.logging.ConsoleHandler loghandler = new java.util.logging.ConsoleHandler();
//                loghandler.setLevel(Level.ALL);
//                java.util.logging.Logger.getLogger(SVNLogType.NETWORK.getName()).addHandler(loghandler);
//                java.util.logging.Logger.getLogger(SVNLogType.DEFAULT.getName()).addHandler(loghandler);
//                java.util.logging.Logger.getLogger(SVNLogType.CLIENT.getName()).addHandler(loghandler);
//                java.util.logging.Logger.getLogger(SVNLogType.WC.getName()).addHandler(loghandler);
//                java.util.logging.Logger.getLogger(SVNLogType.FSFS.getName()).addHandler(loghandler);
            }
        }
    }

    public void onStop(Application app) {
    }
}
