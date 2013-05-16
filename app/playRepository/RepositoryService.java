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

/**
 * 저장소 관련 서비스를 제공하는 클래스
 */
public class RepositoryService {
    public static final String VCS_SUBVERSION = "Subversion";
    public static final String VCS_GIT = "GIT";

    /**
     * 지원하는 VCS(버전 관리 시스템) 타입을 반환한다.
     * <p/>
     * when: 프로젝트 생성 화면에서 VCS 타입 보여줄 때 사용한다.
     * <p/>
     * "project.new.vcsType.git"과 "project.new.vcsType.subversion"이라는 키값으로
     * message.en과 message.kr에 적혀있는 값을 화면에 보여줄 때 사용한다.
     *
     * @return
     */
    public static Map<String, String> vcsTypes() {
        HashMap<String, String> map = new HashMap<String, String>();
        map.put(VCS_GIT, "project.new.vcsType.git");
        map.put(VCS_SUBVERSION, "project.new.vcsType.subversion");
        return map;
    }

    /**
     * 저장소를 삭제한다.
     * <p/>
     * when: {@link ProjectApp#deleteProject(String, String)}에서 프로젝트를 삭제할 때
     * 해당 프로젝트와 관련있는 저장소를 삭제할 때 사용한다.
     * <p/>
     * {@code userName}과 {@code projectName}에 해당하는 프로젝트의 {@link PlayRepository}를 읽어오고
     * {@link playRepository.PlayRepository#delete()}를 호출한다.
     *
     * @param userName
     * @param projectName
     * @param type
     * @throws IOException
     * @throws ServletException
     * @see {@link ProjectApp#deleteProject(String, String)}
     * @see {@link playRepository.PlayRepository#delete()}
     */
    public static void deleteRepository(String userName, String projectName, String type)
            throws IOException, ServletException {
        Project project = ProjectApp.getProject(userName, projectName);
        RepositoryService.getRepository(project).delete();
    }

    /**
     * {@code project}와 관련있는 저장소를 생성한다.
     * <p/>
     * when: {@link controllers.ProjectApp#newProject()}로 프로젝트를 생성할 때 사용한다.
     * <p/>
     * {@code project}에 해당하는 저장소를 삭제하고 {@link PlayRepository}를 읽어온 뒤
     * {@link PlayRepository#create()}를 호출한다.
     *
     * @param project
     * @throws IOException
     * @throws ServletException
     * @throws ClientException
     * @throws UnsupportedOperationException
     * @see {@link #deleteRepository(String, String, String)}
     * @see {@link PlayRepository#create()}
     */
    public static void createRepository(Project project) throws IOException, ServletException,
            ClientException, UnsupportedOperationException {
        RepositoryService.deleteRepository(project.owner, project.name, project.vcs);
        RepositoryService.getRepository(project).create();
    }

    /**
     * {@code userName}의 {@code projectName}에 해당하는 프로젝트의 저장소에서
     * {@code path}에 해당하는 정보를 JSON으로 읽어온다.
     * <p/>
     * when: {@link controllers.CodeApp#ajaxRequest(String, String, String)}에서
     * 프로젝트의 코드를 조회할 때 사용한다.
     * <p/>
     * {@code userName}의 {@code projectName}에 해당하는 {@link Project}의 {@link PlayRepository}를 찾아서
     * {@link PlayRepository#findFileInfo(String)}를 호출한다.
     *
     * @param userName
     * @param projectName
     * @param path
     * @return
     * @throws NoHeadException
     * @throws UnsupportedOperationException
     * @throws IOException
     * @throws GitAPIException
     * @throws ServletException
     * @throws SVNException
     * @see {@link PlayRepository#findFileInfo(String)}
     */
    public static ObjectNode getMetaDataFrom(String userName, String projectName, String path)
            throws NoHeadException, UnsupportedOperationException, IOException, GitAPIException,
            ServletException, SVNException {
        Project project = ProjectApp.getProject(userName, projectName);
        return RepositoryService.getRepository(project).findFileInfo(path);
    }

    /**
     * {@code userName}의 {@code projectName}에 해당하는 프로젝트의 저장소에서
     * {@code branch}의 {@code path}에 해당하는 정보를 JSON으로 읽어온다.
     * <p/>
     * when: {@link controllers.CodeApp#ajaxRequestWithBranch(String, String, String, String)}에서
     * 특정 프로젝트의 특정 브랜치에 있는 코드를 조회할 때 사용한다.
     * <p/>
     * {@code userName}의 {@code projectName}에 해당하는 {@link Project}의 {@link PlayRepository}를 찾아서
     * {@link PlayRepository#findFileInfo(String, String)}를 호출한다.
     *
     * @param userName
     * @param projectName
     * @param path
     * @param branch
     * @return
     * @throws AmbiguousObjectException
     * @throws NoHeadException
     * @throws UnsupportedOperationException
     * @throws IOException
     * @throws SVNException
     * @throws GitAPIException
     * @throws ServletException
     * @see {@link PlayRepository#findFileInfo(String, String)}
     */
    public static ObjectNode getMetaDataFrom(String userName, String projectName, String path, String branch) throws AmbiguousObjectException, NoHeadException, UnsupportedOperationException, IOException, SVNException, GitAPIException, ServletException {
        // TODO Auto-generated method stub
        Project project = ProjectApp.getProject(userName, projectName);
        return RepositoryService.getRepository(project).findFileInfo(branch, path);
    }


    /**
     * {@code userName}의 {@code projectName}에 해당하는 프로젝트의 저장소에서
     * {@code branch}의 {@code path}에 해당하는 파일을 읽어온다.
     * <p/>
     * when: {@link controllers.CodeApp#showRawFile(String, String, String)}과
     * {@link controllers.CodeApp#showImageFile(String, String, String)}에서 파일 내용을 화면에 보여줄 때 사용한다.
     * <p/>
     * {@code userName}의 {@code projectName}에 해당하는 {@link Project}의 {@link PlayRepository}를 찾아서
     * {@link PlayRepository#getRawFile(String)}을 호출한다.
     *
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
     * @see {@link PlayRepository#getRawFile(String)}
     */
    public static byte[] getFileAsRaw(String userName, String projectName, String path)
            throws MissingObjectException, IncorrectObjectTypeException, AmbiguousObjectException,
            UnsupportedOperationException, IOException, ServletException, SVNException {
        Project project = ProjectApp.getProject(userName, projectName);
        return RepositoryService.getRepository(project).getRawFile(path);
    }

    /**
     * {@code project}의 저장소를 나타내는 {@link PlayRepository}를 반환한다.
     * <p/>
     * when:  {@link PlayRepository}를 사용하는 여러 곳에서 이 메서드를 사용하고 있다.
     * <p/>
     * Git 프로젝트일 경우에는 {@link GitRepository}를 반환하고 SVN 프로젝트일 경우에는 {@link SVNRepository}를 반환한다.
     *
     * @param project
     * @return
     * @throws IOException
     * @throws ServletException
     * @throws UnsupportedOperationException
     */
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

    /**
     * {@link DAVServlet}을 생성하고 초기화(init)해서 객체를 반환한다.
     * <p/>
     * when: {@link controllers.SvnApp#service()}에서 SVN 요청을 처리할 때 사용한다.
     * <p/>
     * 초기화 할 때 사용하는 {@link ServletConfig#getInitParameter(String)}를 오버라이딩해서
     * 넘어온 매개변수의 값이 "SVNParentPath"일 때는{@link SVNRepository#getRepoPrefix()} +
     * {@code userName} + "/"의 절대 경로를 반환하고
     * 다른 값일 경우에는 플레이 설정 파일에서 "application." + name에 해당하는 값을 반환하도록 설정한다.
     *
     * @param userName
     * @return
     * @throws ServletException
     */
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



    /**
     * Git advertise 요청을 처리한다.
     * <p/>
     * when: {@link controllers.GitApp#service(String, String, String, boolean)}에서 사용한다.
     *
     * @param project
     * @param service
     * @param response
     * @return
     * @throws IOException
     * @see <a href="https://www.kernel.org/pub/software/scm/git/docs/git-upload-pack.html">git-upload-pack</a>
     * @see <a href="https://www.kernel.org/pub/software/scm/git/docs/git-receive-pack.html">git-receive-pack</a>
     */
    public static byte[] gitAdvertise(Project project, String service, Response response) throws IOException {
        response.setContentType("application/x-" + service + "-advertisement");

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        PacketLineOut packetLineOut = new PacketLineOut(byteArrayOutputStream);
        packetLineOut.writeString("# service=" + service + "\n");
        packetLineOut.end();
        PacketLineOutRefAdvertiser packetLineOutRefAdvertiser = new PacketLineOutRefAdvertiser(packetLineOut);

        Repository repository = GitRepository.createGitRepository(project);

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

    /**
     * GIT RPC 요청을 처리합니다.
     * <p/>
     * when: {@link controllers.GitApp#service(String, String, String, boolean)}에서 사용한다.
     *
     * @param project
     * @param service
     * @param request
     * @param response
     * @return
     * @throws IOException
     * @see <a href="https://www.kernel.org/pub/software/scm/git/docs/git-upload-pack.html">git-upload-pack</a>
     * @see <a href="https://www.kernel.org/pub/software/scm/git/docs/git-receive-pack.html">git-receive-pack</a>
     */
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

        Repository repository = GitRepository.createGitRepository(project);

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