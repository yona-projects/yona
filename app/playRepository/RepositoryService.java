/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @author Yi EungJun
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package playRepository;

import com.fasterxml.jackson.databind.node.ObjectNode;
import controllers.ProjectApp;
import controllers.UserApp;
import models.Project;
import models.User;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.transport.RefAdvertiser.PacketLineOutRefAdvertiser;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.server.dav.DAVServlet;
import play.Logger;
import play.mvc.Http.RawBuffer;
import play.mvc.Http.Request;
import play.mvc.Http.Response;
import playRepository.hooks.*;
import utils.Config;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import java.io.*;
import java.util.*;

public class RepositoryService {
    public static final String VCS_SUBVERSION = "Subversion";
    public static final String VCS_GIT = "GIT";

    public static Map<String, String> vcsTypes() {
        HashMap<String, String> map = new HashMap<>();
        map.put(VCS_GIT, "project.new.vcsType.git");
        map.put(VCS_SUBVERSION, "project.new.vcsType.subversion");
        return map;
    }

    /**
     * @see {@link ProjectApp#deleteProject(String, String)}
     * @see {@link playRepository.PlayRepository#delete()}
     */
    public static void deleteRepository(Project project)
            throws Exception {
        RepositoryService.getRepository(project).delete();
    }

    /**
     * @see {@link #deleteRepository(Project)}
     * @see {@link PlayRepository#create()}
     */
    public static void createRepository(Project project) throws Exception {
        RepositoryService.deleteRepository(project);
        RepositoryService.getRepository(project).create();
    }

    public static List<ObjectNode> getMetaDataFromAncestorDirectories(PlayRepository repository,
                                                                      String branch,
                                                                      String path) throws
            SVNException, GitAPIException, IOException {

        List<ObjectNode> recursiveData = new ArrayList<>();

        String partialPath = "";
        String[] pathArray = path.split("/");
        int pathLength = path.equals("") ? 0 : pathArray.length;
        ObjectNode metaData;

        metaData = repository.getMetaDataFromPath(branch, "");
        if (metaData == null) {
            return null;
        }
        metaData.put("path", "");
        recursiveData.add(metaData);
        for(int i = 0; i < pathLength; i++){
            partialPath = (partialPath.equals("")) ? pathArray[i] : partialPath + "/" + pathArray[i];
            if (!repository.isIntermediateFolder(partialPath)) {
                metaData = repository.getMetaDataFromPath(branch, partialPath);
                if (metaData == null) {
                    return null;
                }
                metaData.put("path", partialPath);
                recursiveData.add(metaData);
            }
        }

        return recursiveData;
    }

    /**
     * @see {@link PlayRepository#getRawFile(String, String)}
     */
    public static byte[] getFileAsRaw(String userName, String projectName, String revision, String path)
            throws UnsupportedOperationException, IOException, ServletException, SVNException {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        return RepositoryService.getRepository(project, true).getRawFile(revision, path);
    }

    public static PlayRepository getRepository(Project project, boolean alternatesMergeRepo) throws IOException,
            ServletException, UnsupportedOperationException {
        if (project == null) {
            return null;
        }

        switch (project.vcs) {
            case VCS_GIT:
                return new GitRepository(project.owner, project.name, alternatesMergeRepo);
            case VCS_SUBVERSION:
                return new SVNRepository(project.owner, project.name);
            default:
                throw new UnsupportedOperationException();
        }
    }

    public static PlayRepository getRepository(Project project) throws IOException,
            ServletException, UnsupportedOperationException {
        return getRepository(project, true);
    }

    public static PlayRepository getRepository(String userName, String projectName) throws IOException,
    ServletException, UnsupportedOperationException {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        return RepositoryService.getRepository(project);
    }

    public static DAVServlet createDavServlet(final String userName) throws ServletException {
        DAVServlet servlet = new DAVServlet();
        servlet.init(new ServletConfig() {

            @Override
            public String getInitParameter(String name) {
                if (name.equals("SVNParentPath")) {
                    return new File(SVNRepository.getRootDirectory(), userName + "/").getAbsolutePath();
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

        if (service.equals("git-upload-pack")) {
            Repository repository = GitRepository.buildGitRepository(project);
            UploadPack uploadPack = new UploadPack(repository);
            uploadPack.setBiDirectionalPipe(false);
            uploadPack.sendAdvertisedRefs(packetLineOutRefAdvertiser);
        } else if (service.equals("git-receive-pack")) {
            Repository repository = GitRepository.buildGitRepository(project, false);
            ReceivePack receivePack = new ReceivePack(repository);
            receivePack.sendAdvertisedRefs(packetLineOutRefAdvertiser);
        }

        byteArrayOutputStream.close();

        return byteArrayOutputStream.toByteArray();
    }

    /**
     * @see <a href="https://www.kernel.org/pub/software/scm/git/docs/git-upload-pack.html">git-upload-pack</a>
     * @see <a href="https://www.kernel.org/pub/software/scm/git/docs/git-receive-pack.html">git-receive-pack</a>
     */
    public static PipedInputStream gitRpc(final Project project, String service, Request request, Response response) {
        response.setContentType("application/x-" + service + "-result");

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

            Repository repository;
            PipedInputStream responseStream = new PipedInputStream();

            switch (service) {
                case "git-upload-pack":
                    repository = GitRepository.buildGitRepository(project);
                    uploadPack(requestStream, repository, new PipedOutputStream(responseStream));
                    break;
                case "git-receive-pack":
                    repository = GitRepository.buildGitRepository(project, false);
                    PreReceiveHook preReceiveHook = createPreReceiveHook();
                    PostReceiveHook postReceiveHook = createPostReceiveHook(UserApp.currentUser(), project, request);
                    receivePack(requestStream, repository, new PipedOutputStream(responseStream),
                            preReceiveHook, postReceiveHook);
                    // receivePack.setEchoCommandFailures(true);
                    break;
                default:
                    requestStream.close();
                    break;
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

    private static PreReceiveHook createPreReceiveHook() {
        List<PreReceiveHook> hooks = new ArrayList<>();
        hooks.add(new RejectPushToReservedRefs());
        return PreReceiveHookChain.newChain(hooks);
    }

    private static PostReceiveHook createPostReceiveHook(
            final User currentUser, final Project project, final Request request) {
        List<PostReceiveHook> hooks = new ArrayList<>();
        hooks.add(new UpdateLastPushedDate(project));
        hooks.add(new UpdateRecentlyPushedBranch(project));
        hooks.add(new IssueReferredFromCommitEvent(project, currentUser));
        hooks.add(new PullRequestCheck(currentUser, request, project));
        hooks.add(new NotifyPushedCommits(project, currentUser));
        return PostReceiveHookChain.newChain(hooks);
    }

    private static void receivePack(final InputStream input, Repository repository,
                                    final OutputStream output,
                                    final PreReceiveHook preReceiveHook,
                                    final PostReceiveHook postReceiveHook) {
        final ReceivePack receivePack = new ReceivePack(repository);
        receivePack.setBiDirectionalPipe(false);
        new Thread() {
            @Override
            public void run() {
                try {
                    receivePack.setPreReceiveHook(preReceiveHook);
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
