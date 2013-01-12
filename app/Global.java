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

    public void onStop(Application app) {
    }
}
