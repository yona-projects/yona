package playRepository;

import actors.CommitCheckActor;

import actors.PullRequestEventActor;
import akka.actor.Props;
import controllers.ProjectApp;
import controllers.UserApp;

import models.CommitCheckMessage;

import models.PullRequest;
import models.PullRequestEventMessage;

import models.Project;
import models.User;
import models.enumeration.State;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.node.ObjectNode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.CommitTimeRevFilter;
import org.eclipse.jgit.transport.PacketLineOut;
import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.RefAdvertiser.PacketLineOutRefAdvertiser;
import org.eclipse.jgit.transport.UploadPack;
import org.tigris.subversion.javahl.ClientException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.server.dav.DAVServlet;

import play.Logger;
import play.libs.Akka;
import play.mvc.Http.RawBuffer;
import play.mvc.Http.Request;
import play.mvc.Http.Response;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import java.io.*;

import java.util.*;

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
        HashMap<String, String> map = new HashMap<>();
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
     * {@code branch}의 {@code path}의 모든 상위 경로의 정보를 JSON으로 읽어온다.
     * <p/>
     * when: {@link controllers.CodeApp#ajaxRequestWithBranch(String, String, String, String)}에서
     * 특정 프로젝트의 특정 브랜치, 특정 경로에 있는 코드 정보를 조회할 때 사용한다.
     * <p/>
     *
     * @param userName
     * @param projectName
     * @param path
     * @param branch
     * @return
     * @throws Exception
     */
    public static List<ObjectNode> getMetaDataFromAncestorDirectories(PlayRepository repository, String branch, String path)
        throws Exception {
            
        List<ObjectNode> recursiveData = new ArrayList<ObjectNode>();
        
        String partialPath = "";            
        String[] pathArray = path.split("/");
        Integer pathLength = pathArray.length;
        ObjectNode metaData;
        
        for(int i = 0; i < pathLength; i++){
            metaData = repository.getMetaDataFromPath(branch, partialPath);
            metaData.put("path", partialPath);
            partialPath = (partialPath.equals("")) ? pathArray[i] : partialPath + "/" + pathArray[i];
            recursiveData.add(metaData);
        }
        
        return recursiveData;
    }

    /**
     * {@code userName}의 {@code projectName}에 해당하는 프로젝트의 저장소에서
     * {@code revision}의 {@code path}에 해당하는 파일을 읽어온다.
     * <p/>
     * when: {@link controllers.CodeApp#showRawFile(String, String, String)}과
     * {@link controllers.CodeApp#showImageFile(String, String, String)}에서 파일 내용을 화면에 보여줄 때 사용한다.
     * <p/>
     * {@code userName}의 {@code projectName}에 해당하는 {@link Project}의 {@link PlayRepository}를 찾아서
     * {@link PlayRepository#getRawFile(String)}을 호출한다.
     *
     * @param userName
     * @param projectName
     * @param revision
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
    public static byte[] getFileAsRaw(String userName, String projectName, String revision, String path)
            throws MissingObjectException, IncorrectObjectTypeException, AmbiguousObjectException,
            UnsupportedOperationException, IOException, ServletException, SVNException {
        Project project = ProjectApp.getProject(userName, projectName);
        return RepositoryService.getRepository(project).getRawFile(revision, path);
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
     * {@code project}의 저장소를 나타내는 {@link PlayRepository}를 반환한다.
     * <p/>
     * when:  {@link PlayRepository}를 사용하는 여러 곳에서 이 메서드를 사용하고 있다.
     * <p/>
     * Git 프로젝트일 경우에는 {@link GitRepository}를 반환하고 SVN 프로젝트일 경우에는 {@link SVNRepository}를 반환한다.
     *
     * @param userName
     * @param projectName
     * @return
     * @throws IOException
     * @throws ServletException
     * @throws UnsupportedOperationException
     */
    public static PlayRepository getRepository(String userName, String projectName) throws IOException,
    ServletException, UnsupportedOperationException {
        Project project = ProjectApp.getProject(userName, projectName);
        return RepositoryService.getRepository(project);
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
    public static PipedInputStream gitRpc(final Project project, String service, Request request, Response response) {
        response.setContentType("application/x-" + service + "-result");

        // FIXME 스트림으로..
        RawBuffer raw = request.body().asRaw();
        byte[] buf = raw.asBytes();
        InputStream requestStream = null;

        try {
            // If the content size is bigger than memoryThreshold,
            // which is defined as 100 * 1024 in play.api.mvc.BodyParsers trait,
            // the content is stored as a file.
            if (buf != null) {
                requestStream = new ByteArrayInputStream(buf);
            } else {
                requestStream = new FileInputStream(raw.asFile());
            }

            Repository repository = GitRepository.createGitRepository(project);
            PipedInputStream responseStream = new PipedInputStream();

            if (service.equals("git-upload-pack")) {
                uploadPack(requestStream, repository, new PipedOutputStream(responseStream));
            } else if (service.equals("git-receive-pack")) {
                PostReceiveHook postReceiveHook = createPostReceiveHook(UserApp.currentUser(), project, request);
                receivePack(requestStream, repository, new PipedOutputStream(responseStream),
                        postReceiveHook);
                // receivePack.setEchoCommandFailures(true);//git버전에 따라서 불린값 설정필요.
            } else {
                requestStream.close();
            }

            return responseStream;
        } catch (IOException e) {
            if(requestStream != null) {
                try { requestStream.close(); } catch (IOException e1) {
                    Logger.error("failed to close request stream", e1);
                }
            }
            throw new RuntimeException(e);
        }
    }

    /*
     * receive-pack 후처리 객체 생성
     * project 의 lastPushedDate 업데이트
     * 변경된 branch 와 관련된 pull-request 들 충돌 검사
     */
    private static PostReceiveHook createPostReceiveHook(
            final User currentUser, final Project project, final Request request) {
        return new PostReceiveHook() {
            @Override
            public void onPostReceive(ReceivePack receivePack, Collection<ReceiveCommand> commands) {
                updateLastPushedDate();
                
                conflictCheck(commands);
                commitCheck(commands);
            }

            private void commitCheck(Collection<ReceiveCommand> commands) {
                CommitCheckMessage message = new CommitCheckMessage(commands, project);
                Akka.system().actorOf(new Props(CommitCheckActor.class)).tell(message, null);
            }

            /*
             * project 가 마지막 업데이트된 시점 저장
             */
            private void updateLastPushedDate() {
                project.lastPushedDate = new Date();
                project.save();
            }

            /*
             * 성공한 ReceiveCommand 로 영향받은 branch 에 대해서
             * 관련 있는 코드-보내기 요청을 찾아 충돌이 발생했는지 검사한다.
             */
            private void conflictCheck(Collection<ReceiveCommand> commands) {
                Set<String> branches = getBranches(commands);
                for (String branch : branches) {
                    PullRequestEventMessage message = new PullRequestEventMessage(currentUser, request, project, branch);
                    changePullRequestState(message);
                    Akka.system().actorOf(new Props(PullRequestEventActor.class)).tell(message, null);
                }
            }
            
            /**
             * 백엔드 작업이 남아있는 pull-request의 상태값을 변경한다. 
             * @param message
             */
            private void changePullRequestState(PullRequestEventMessage message) {
                List<PullRequest> pullRequests = PullRequest.findRelatedPullRequests(
                    message.getProject(), message.getBranch());
                    
                for (PullRequest pullRequest : pullRequests) {
                    if (!pullRequest.isClosed()) {
                        pullRequest.isMerging = true;
                    }
                    pullRequest.update();
                }
            }

            /*
             * ReceiveCommand 중, branch update 에 해당하는 것들의 참조 branch set 을 구한다.
             */
            private Set<String> getBranches(
                    Collection<ReceiveCommand> commands) {
                Set<String> branches = new HashSet<>();
                for (ReceiveCommand command : commands) {
                    if (isUpdateCommand(command)) {
                        branches.add(command.getRefName());
                    }
                }
                return branches;
            }

            /*
             * command 가 update type 인지 판별한다.
             */
            private boolean isUpdateCommand(ReceiveCommand command) {
                return command.getType() == ReceiveCommand.Type.UPDATE
                        || command.getType() == ReceiveCommand.Type.UPDATE_NONFASTFORWARD;
            }
        };
    }

    private static void receivePack(final InputStream input, Repository repository,
                                    final OutputStream output,
                                    final PostReceiveHook postReceiveHook) {
        final ReceivePack receivePack = new ReceivePack(repository);
        receivePack.setBiDirectionalPipe(false);
        new Thread() {
            @Override
            public void run() {
                try {
                    receivePack.setPostReceiveHook(postReceiveHook);
                    receivePack.receive(input, output, null);
                } catch (IOException e) {
                    Logger.error("receivePack failed", e);
                }

                closeStreams("receivePack", input, output);
            }
        }.start();
    }

    private static void uploadPack(final InputStream input, Repository repository,
                                   final OutputStream output) {
        final UploadPack uploadPack = new UploadPack(repository);
        uploadPack.setBiDirectionalPipe(false);
        new Thread() {
            @Override
            public void run() {
                try {
                    uploadPack.upload(input, output, null);
                } catch (IOException e) {
                    Logger.error("uploadPack failed", e);
                }

                closeStreams("uploadPack", input, output);
            }
        }.start();
    }

    private static void closeStreams(String serviceName, InputStream input, OutputStream output) {
        try {
            input.close();
        } catch (IOException e) {
            Logger.error(serviceName + ": Failed to close input stream", e);
        }

        try {
            output.close();
        } catch (IOException e) {
            Logger.error(serviceName + ": Failed to close output stream", e);
        }
    }

}