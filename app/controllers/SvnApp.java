package controllers;

import java.io.IOException;
import java.net.URISyntaxException;

import javax.servlet.ServletException;

import models.Project;
import models.enumeration.Operation;
import models.enumeration.Resource;

import org.apache.commons.lang.StringUtils;
import org.tigris.subversion.javahl.ClientException;
import org.tmatesoft.svn.core.internal.server.dav.DAVServlet;
import org.tmatesoft.svn.core.internal.server.dav.SVNPathBasedAccess;
import org.tmatesoft.svn.core.internal.server.dav.handlers.DAVHandlerFactory;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;

import play.mvc.*;
import play.mvc.Http.Session;
import playRepository.SVNRepository;
import utils.*;

public class SvnApp extends Controller{
    static DAVServlet davServlet;
    public static final String REPO_PREFIX = "repo/svn/";

    @BodyParser.Of(BodyParser.Raw.class)
    public static Result serviceWithPath(String path) throws ServletException, IOException {
        return service();
    }

    @With(BasicAuthAction.class)
    @BodyParser.Of(BodyParser.Raw.class)
    public static Result service() throws ServletException, IOException {
        //FIXME DAVServlet 들어내고 싶다.
        String path;
        try {
            path = new java.net.URI(request().uri()).getPath();
        } catch (URISyntaxException e) {
            return badRequest();
        }
        String pathInfo = SVNEncodingUtil.uriEncode(path.substring(path.indexOf('/',1)));
        
        String[] pathSegments = pathInfo.substring(1).split("/");

        if (pathSegments.length < 2) {
            return badRequest();
        }

        String userName = pathSegments[0];
        String projectName = pathSegments[1];

        Project project = Project.findByName(projectName);

        if (!AccessControl.isAllowed(session().get(UserApp.SESSION_USERID), project.id,
                Resource.CODE, getRequestedOperation(request().method()), null)) {
            return forbidden("You have no permission to read this repository.");
        }

        pathInfo = pathInfo.substring(pathInfo.indexOf('/', 1));
        PlayServletRequest request = new PlayServletRequest(request(), new PlayServletSession(new PlayServletContext()), pathInfo);
        PlayServletResponse response = new PlayServletResponse(response());

        new SVNRepository(userName, "").getCore().service(request, response);

        response.flushBuffer();

        return status(response.getStatus(), response.getBuffer().toByteArray());
    }

    public static void createRepository(String userName, String projectName) throws ClientException, ServletException {
        new SVNRepository(userName, projectName).create();
    }

    public static String getURL(String ownerName, String projectName) {
        String[] pathSegments = { "svn", ownerName, projectName };
        return Config.getScheme("http") + "://" + Config.getHostport(request().host()) + "/"
                + StringUtils.join(pathSegments, "/");
    }

    public static Result showRawCode(String userName, String projectName, String path) {
        // TODO Auto-generated method stub
        
        return null;
    }

    public static void deleteRepository(String userName, String projectName) throws Exception {
        new SVNRepository(userName, projectName).delete();
        
    }

    private static Operation getRequestedOperation(String method) {
        if (DAVHandlerFactory.METHOD_OPTIONS.equals(method)
                || DAVHandlerFactory.METHOD_PROPFIND.equals(method)
                || DAVHandlerFactory.METHOD_GET.equals(method)
                || DAVHandlerFactory.METHOD_REPORT.equals(method)) {
            return Operation.READ;
        } else {
            return Operation.WRITE;
        }
    }

}
