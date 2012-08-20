package svn;

import java.io.File;
import java.util.Enumeration;

import javax.servlet.*;

import org.codehaus.jackson.node.ObjectNode;
import org.tigris.subversion.javahl.ClientException;
import org.tmatesoft.svn.core.internal.server.dav.DAVServlet;

import controllers.SvnApp;

public class SVNRepository {
    public static final String REPO_PREFIX = "repo/svn/";

    private static DAVServlet davServlet = new DAVServlet();

    public static void create(String userName, String projectName) throws ClientException {
        String svnPath = new File(SvnApp.REPO_PREFIX + userName + "/" + projectName)
                .getAbsolutePath();
        new org.tigris.subversion.javahl.SVNAdmin().create(svnPath, false, false, null, "fsfs");
    }

   
    
    public static DAVServlet getDavServlet(final String userName) throws ServletException {
        davServlet.init(new ServletConfig() {

            @Override
            public String getInitParameter(String name) {
                if (name.equals("SVNParentPath")) {
                    return new File(REPO_PREFIX + userName + "/").getAbsolutePath();
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
        return davServlet;
    }

    public byte[] getRawFile(String path) {
        return null;

    }

    public ObjectNode findFileInfo(String path) {
        return null;
    }

}
