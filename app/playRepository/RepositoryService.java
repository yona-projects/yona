package playRepository;

import controllers.ProjectApp;
import models.Project;
import org.codehaus.jackson.node.ObjectNode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PacketLineOut;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.RefAdvertiser.PacketLineOutRefAdvertiser;
import org.eclipse.jgit.transport.UploadPack;
import org.tigris.subversion.javahl.ClientException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.server.dav.DAVServlet;
import play.mvc.Http.RawBuffer;
import play.mvc.Http.Request;
import play.mvc.Http.Response;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import java.io.*;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

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
            ServletException, SVNException {
        Project project = ProjectApp.getProject(userName, projectName);
        return RepositoryService.getRepository(project).findFileInfo(path);
    }

    public static ObjectNode getMetaDataFrom(String userName, String projectName, String path, String branch) throws AmbiguousObjectException, NoHeadException, UnsupportedOperationException, IOException, SVNException, GitAPIException, ServletException {
        // TODO Auto-generated method stub
        Project project = ProjectApp.getProject(userName, projectName);
        return RepositoryService.getRepository(project).findFileInfo(branch, path);
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
     * @throws SVNException
     */
    public static byte[] getFileAsRaw(String userName, String projectName, String path)
            throws MissingObjectException, IncorrectObjectTypeException, AmbiguousObjectException,
            UnsupportedOperationException, IOException, ServletException, SVNException {
        Project project = ProjectApp.getProject(userName, projectName);
        return RepositoryService.getRepository(project).getRawFile(path);
    }

    public static PlayRepository getRepository(Project project) throws IOException,
            ServletException, UnsupportedOperationException {
        if (project == null) {
            return null;
        }

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
                    return new File(SVNRepository.getRepoPrefix() + userName + "/").getAbsolutePath();
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

    public static Repository createGitRepository(Project project) throws IOException {
        return GitRepository.buildGitRepository(project);
    }

    public static byte[] gitAdvertise(Project project, String service, Response response) throws IOException {
        response.setContentType("application/x-" + service + "-advertisement");

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        PacketLineOut packetLineOut = new PacketLineOut(byteArrayOutputStream);
        packetLineOut.writeString("# service=" + service + "\n");
        packetLineOut.end();
        PacketLineOutRefAdvertiser packetLineOutRefAdvertiser = new PacketLineOutRefAdvertiser(packetLineOut);

        Repository repository = createGitRepository(project);

        if (service.equals("git-upload-pack")) {
            UploadPack uploadPack = new UploadPack(repository);
            uploadPack.setBiDirectionalPipe(false);
            uploadPack.sendAdvertisedRefs(packetLineOutRefAdvertiser);
        } else if (service.equals("git-receive-pack")) {
            ReceivePack receivePack = new ReceivePack(repository);
            receivePack.sendAdvertisedRefs(packetLineOutRefAdvertiser);
        }

        byteArrayOutputStream.close();

        return byteArrayOutputStream.toByteArray();
    }

    public static byte[] gitRpc(Project project, String service, Request request, Response response) throws IOException {
        response.setContentType("application/x-" + service + "-result");
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        // FIXME 스트림으로..
        RawBuffer raw = request.body().asRaw();
        byte[] buf = raw.asBytes();
        InputStream in;

        // If the content size is bigger than memoryThreshold,
        // which is defined as 100 * 1024 in play.api.mvc.BodyParsers trait,
        // the content is stored as a file.
        if (buf != null) {
            in = new ByteArrayInputStream(buf);
        } else {
            in = new FileInputStream(raw.asFile());
        }

        Repository repository = createGitRepository(project);

        if (service.equals("git-upload-pack")) {
            UploadPack uploadPack = new UploadPack(repository);
            uploadPack.setBiDirectionalPipe(false);
            uploadPack.upload(in, byteArrayOutputStream, null);
        } else if (service.equals("git-receive-pack")) {
            ReceivePack receivePack = new ReceivePack(repository);
            receivePack.setBiDirectionalPipe(false);
            receivePack.receive(in, byteArrayOutputStream, null);
        }

        // receivePack.setEchoCommandFailures(true);//git버전에 따라서 불린값 설정필요.
        byteArrayOutputStream.close();

        return byteArrayOutputStream.toByteArray();
    }

}
