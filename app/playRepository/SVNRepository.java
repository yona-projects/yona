package playRepository;

import java.io.File;
import java.util.Enumeration;

import javax.servlet.*;

import org.codehaus.jackson.node.ObjectNode;
import org.tigris.subversion.javahl.ClientException;
import org.tmatesoft.svn.core.internal.server.dav.DAVServlet;

import utils.FileUtil;

import controllers.SvnApp;

public class SVNRepository implements PlayRepository {
    public static final String REPO_PREFIX = "repo/svn/";

    private DAVServlet servlet;

    private String projectName;

    private String userName;

    public SVNRepository(final String userName, String projectName) throws ServletException {
        this.servlet = new DAVServlet();
        servlet.init(new ServletConfig() {

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
        this.userName = userName;
        this.projectName = projectName;
    }

    public static void create1(String userName, String projectName) throws ClientException {
        String svnPath = new File(SvnApp.REPO_PREFIX + userName + "/" + projectName)
                .getAbsolutePath();
        new org.tigris.subversion.javahl.SVNAdmin().create(svnPath, false, false, null, "fsfs");
    }

    public void create(String userName, String projectName) throws ClientException {
        String svnPath = new File(SvnApp.REPO_PREFIX + userName + "/" + projectName)
                .getAbsolutePath();
        new org.tigris.subversion.javahl.SVNAdmin().create(svnPath, false, false, null, "fsfs");
    }


    public byte[] getRawFile(String path) {
        return null;

    }

    public ObjectNode findFileInfo(String path) {
        return null;
    }

    @Override
    public void create() throws ClientException {
        String svnPath = new File(SvnApp.REPO_PREFIX + userName + "/" + projectName)
                .getAbsolutePath();
        new org.tigris.subversion.javahl.SVNAdmin().create(svnPath, false, false, null, "fsfs");
    }

    @Override
    public DAVServlet getCore() {
        return servlet;
    }

    @Override
    public void delete() {
        FileUtil.rm_rf(new File(REPO_PREFIX + userName + "/" + projectName));
    }

}
