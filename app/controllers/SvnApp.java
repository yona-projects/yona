package controllers;

import java.io.IOException;
import java.net.URISyntaxException;

import javax.servlet.ServletException;

import models.Project;
import models.User;
import models.enumeration.Operation;

import org.tmatesoft.svn.core.internal.server.dav.DAVServlet;
import org.tmatesoft.svn.core.internal.server.dav.handlers.DAVHandlerFactory;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;

import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.With;
import playRepository.PlayRepository;
import playRepository.RepositoryService;
import utils.AccessControl;
import utils.BasicAuthAction;
import utils.PlayServletContext;
import utils.PlayServletRequest;
import utils.PlayServletResponse;
import utils.PlayServletSession;

public class SvnApp extends Controller {
    static DAVServlet davServlet;

    @With(BasicAuthAction.class)
    @BodyParser.Of(value = BodyParser.Raw.class, maxLength = Integer.MAX_VALUE)
    public static Result serviceWithPath(String path) throws ServletException, IOException {
        return service();
    }

    @With(BasicAuthAction.class)
    @BodyParser.Of(value = BodyParser.Raw.class, maxLength = Integer.MAX_VALUE)
    public static Result service() throws ServletException, IOException {
        // FIXME DAVServlet 들어내고 싶다.
        String path;
        try {
            path = new java.net.URI(request().uri()).getPath();
        } catch (URISyntaxException e) {
            return badRequest();
        }

        // If the url starts with slash, remove the slash.
        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        // Split the url into three segments: "svn", userName, pathInfo
        String[] segments = path.split("/", 3);
        if (segments.length < 3) {
            return forbidden();
        }

        // Get userName and pathInfo from path segments.
        String loginId = segments[1];
        String pathInfo = segments[2];

        // Get projectName from the pathInfo.
        String projectName = pathInfo.split("/", 2)[0];

        // if user is anon, currentUser is null
        User currentUser = UserApp.currentUser();
        // Check the user has a permission to access this repository.
        Project project = Project.findByOwnerAndProjectName(loginId, projectName);

        PlayRepository repository = RepositoryService.getRepository(project);
        if (!AccessControl.isAllowed(currentUser, repository.asResource(),
                getRequestedOperation(request().method()))) {
            if (currentUser.id == UserApp.anonymous.id) {
                return BasicAuthAction.unauthorized(response());
            } else {
                return forbidden("You have no permission to access this repository.");
            }
        }

        // Transform request and response in this context to ServletRequest and
        // ServletResponse for DAVServlet.
        PlayServletRequest request = new PlayServletRequest(request(), new PlayServletSession(
                new PlayServletContext()), SVNEncodingUtil.uriEncode(pathInfo));
        PlayServletResponse response = new PlayServletResponse(response());

        // Get DAVServlet from SVNRepository and serve the request using it.
        RepositoryService.createDavServlet(loginId).service(request, response);

        response.flushBuffer();

        return status(response.getStatus(), response.getBuffer().toByteArray());
    }

    private static Operation getRequestedOperation(String method) {
        if (DAVHandlerFactory.METHOD_OPTIONS.equals(method)
                || DAVHandlerFactory.METHOD_PROPFIND.equals(method)
                || DAVHandlerFactory.METHOD_GET.equals(method)
                || DAVHandlerFactory.METHOD_REPORT.equals(method)) {
            return Operation.READ;
        } else {
            return Operation.UPDATE;
        }
    }

}
