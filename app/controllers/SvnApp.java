package controllers;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Enumeration;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.tmatesoft.svn.core.internal.server.dav.DAVServlet;

import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Result;
import utils.PlayServletRequest;
import utils.PlayServletResponse;
import utils.PlayServletSession;
import utils.PlayServletContext;

public class SvnApp extends Controller{
    static DAVServlet davServlet;
    public static final String REPO_PREFIX = "repo/svn/";

    @BodyParser.Of(BodyParser.Raw.class)
    public static Result serviceWithPath(String path) throws ServletException, IOException {
        return service();
    }

    public static DAVServlet getDavServlet() throws ServletException {
        if (davServlet == null) {
            davServlet = new DAVServlet();
            davServlet.init(new ServletConfig() {

                @Override
                public String getInitParameter(String name) {
                    if (name.equals("SVNParentPath")) {
                        return new File(REPO_PREFIX).getAbsolutePath();
                    } else {
                        return play.Configuration.root().getString("application." + name);
                    }
                }

                @Override
                public Enumeration<String> getInitParameterNames() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public ServletContext getServletContext() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public String getServletName() {
                    throw new UnsupportedOperationException();
                }

            });
        }

        return davServlet;
    }

    @BodyParser.Of(BodyParser.Raw.class)
    public static Result service() throws ServletException, IOException {
        String path;
        try {
            path = new java.net.URI(request().uri()).getPath();
        } catch (URISyntaxException e) {
            return badRequest();
        }
        String pathInfo = path.substring(path.indexOf('/', 1));
        PlayServletRequest request = new PlayServletRequest(request(), new PlayServletSession(new PlayServletContext()), pathInfo);
        PlayServletResponse response = new PlayServletResponse(response());

        getDavServlet().service(request, response);

        response.flushBuffer();

        return status(response.getStatus(), response.getBuffer().toByteArray());
    }
}
