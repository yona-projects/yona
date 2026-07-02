package modules;

import controllers.SvnApp;
import play.api.http.JavaCompatibleHttpRequestHandler;
import play.http.DefaultHttpRequestHandler;
import play.http.HandlerForRequest;
import play.mvc.Http;
import play.routing.Router;

import javax.inject.Inject;

public class YonaRequestHandler extends DefaultHttpRequestHandler {
    private final Router router;

    @Inject
    public YonaRequestHandler(JavaCompatibleHttpRequestHandler delegate, Router router) {
        super(delegate);
        this.router = router;
    }

    @Override
    public HandlerForRequest handlerForRequest(Http.RequestHeader request) {
        if (SvnApp.isWebDavMethod(request.method())) {
            Http.Request fakeRequest = new Http.RequestBuilder()
                    .method("POST")
                    .uri("/!svn-fake/sevice/")
                    .build();
            return new HandlerForRequest(request, router.route(fakeRequest).orElse(null));
        }
        return super.handlerForRequest(request);
    }
}
