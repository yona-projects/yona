import controllers.routes;
import play.Application;
import play.GlobalSettings;
import play.api.mvc.Handler;
import play.mvc.Http.RequestHeader;

public class Global extends GlobalSettings {
    public void onStart(Application app) {
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
