package playRepository;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.codehaus.jackson.node.ObjectNode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Repository;
import org.tigris.subversion.javahl.ClientException;
import org.tmatesoft.svn.core.internal.server.dav.DAVServlet;

import play.Logger;

import models.Project;

import controllers.ProjectApp;

public class RepositoryService {
    public static final String VCS_SUBVERSION = "Subversion";
    public static final String VCS_GIT = "GIT";

    public static Map<String, String> vcsTypes() {
        HashMap<String, String> map = new HashMap<String, String>();
        map.put(VCS_GIT, "project.new.vcsType.git");
        map.put(VCS_SUBVERSION, "project.new.vcsType.subversion");
        return map;
    }

    public static void deleteRepository(String userName, String projectName, String type)
            throws IOException, ServletException {
        Project project = ProjectApp.getProject(userName, projectName);
        RepositoryService.getRepository(project).delete();
    }

    public static void createRepository(Project project) throws IOException, ServletException,
            ClientException, UnsupportedOperationException {
        RepositoryService.deleteRepository(project.owner, project.name, project.vcs);
        RepositoryService.getRepository(project).create();
    }

    public static ObjectNode getMetaDataFrom(String userName, String projectName, String path)
            throws NoHeadException, UnsupportedOperationException, IOException, GitAPIException,
            ServletException {
        Project project = ProjectApp.getProject(userName, projectName);
    	Logger.info(project.vcs);
        return RepositoryService.getRepository(project).findFileInfo(path);
    }

    /**
     * Raw 소스를 보여주는 코드
     * @param userName
     * @param projectName
     * @param path
     * @return
     * @throws ServletException 
     * @throws IOException 
     * @throws UnsupportedOperationException 
     * @throws AmbiguousObjectException 
     * @throws IncorrectObjectTypeException 
     * @throws MissingObjectException 
     */
    public static byte[] getFileAsRaw(String userName, String projectName, String path)
            throws MissingObjectException, IncorrectObjectTypeException, AmbiguousObjectException,
            UnsupportedOperationException, IOException, ServletException {
        Project project = ProjectApp.getProject(userName, projectName);
        return RepositoryService.getRepository(project).getRawFile(path);
    }

    public static PlayRepository getRepository(Project project) throws IOException,
            ServletException, UnsupportedOperationException {
        if (project.vcs.equals(VCS_GIT)) {
            return new GitRepository(project.owner, project.name);
        } else if (project.vcs.equals(VCS_SUBVERSION)) {
            return new SVNRepository(project.owner, project.name);
        } else {
            throw new UnsupportedOperationException();
        }
    }
    
    public static DAVServlet createDavServlet(final String userName) throws ServletException {
        DAVServlet servlet = new DAVServlet();
        servlet.init(new ServletConfig() {

            @Override
            public String getInitParameter(String name) {
                if (name.equals("SVNParentPath")) {
                    return new File(SVNRepository.REPO_PREFIX + userName + "/").getAbsolutePath();
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
        
        return servlet;
    }

    public static Repository createGitRepository(String userName, String projectName) throws IOException {
        return GitRepository.createGitRepository(userName, projectName);
    }

}