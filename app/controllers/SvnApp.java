package controllers;

import java.io.IOException;
import java.net.URISyntaxException;

import javax.servlet.ServletException;

import org.tigris.subversion.javahl.ClientException;
import org.tmatesoft.svn.core.internal.server.dav.DAVServlet;

import play.Logger;
import play.mvc.*;
import repository.SVNRepository;
import utils.*;

public class SvnApp extends Controller{
    static DAVServlet davServlet;
    public static final String REPO_PREFIX = "repo/svn/";

    @BodyParser.Of(BodyParser.Raw.class)
    public static Result serviceWithPath(String path) throws ServletException, IOException {
        return service();
    }

    @BodyParser.Of(BodyParser.Raw.class)
    public static Result service() throws ServletException, IOException {
        //FIXME DAVServlet 들어내고 싶다.
        String path;
        try {
            path = new java.net.URI(request().uri()).getPath();
        } catch (URISyntaxException e) {
            return badRequest();
        }
        String pathInfo = path.substring(path.indexOf('/',1));
        
        String userName = pathInfo.substring(1, pathInfo.indexOf('/', 1));
        Logger.info(userName);
        pathInfo = pathInfo.substring(pathInfo.indexOf('/', 1));
        Logger.info(pathInfo);
        PlayServletRequest request = new PlayServletRequest(request(), new PlayServletSession(new PlayServletContext()), pathInfo);
        PlayServletResponse response = new PlayServletResponse(response());

        new SVNRepository(userName, "").getCore().service(request, response);

        response.flushBuffer();

        return status(response.getStatus(), response.getBuffer().toByteArray());
    }

    public static void createRepository(String userName, String projectName) throws ClientException, ServletException {
        new SVNRepository(userName, projectName).create();
    }

    public static Result showRawCode(String userName, String projectName, String path) {
        // TODO Auto-generated method stub
        return null;
    }
}
