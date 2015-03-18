/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @author Ahn Hyeok Jun
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

import controllers.UserApp;
import controllers.routes;
import models.Project;
import models.User;
import models.enumeration.ResourceType;
import models.resource.Resource;
import org.apache.commons.io.FileUtils;
import org.apache.tika.Tika;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.RawText;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import play.libs.Json;
import utils.Config;
import utils.FileUtil;
import utils.GravatarUtil;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SVNRepository implements PlayRepository {

    private static String repoPrefix = "repo/svn/";

    public static String getRepoPrefix() {
        return repoPrefix;
    }

    public static void setRepoPrefix(String repoPrefix) {
        SVNRepository.repoPrefix = repoPrefix;
    }

    private final String projectName;

    private final String ownerName;

    public SVNRepository(final String userName, String projectName) {
        this.ownerName = userName;
        this.projectName = projectName;
    }

    @Override
    public byte[] getRawFile(String revision, String path) throws SVNException, FileNotFoundException {
        Long revId = (revision.equals("HEAD") ? -1l : Long.parseLong(revision));
        org.tmatesoft.svn.core.io.SVNRepository repository = getSVNRepository();

        if (!repository.checkPath(path, revId).equals(SVNNodeKind.FILE)) {
            throw new FileNotFoundException();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        repository.getFile(path, revId, null, baos); // revId=-1l
        return baos.toByteArray();
    }

    public boolean isIntermediateFolder(String path) {
        return false;
    }

    @Override
    public ObjectNode getMetaDataFromPath(String path) throws SVNException, IOException {
        return getMetaDataFromPath(-1, path);
    }

    private ObjectNode getMetaDataFromPath(int revision, String path) throws SVNException, IOException {
        org.tmatesoft.svn.core.io.SVNRepository repository = getSVNRepository();

        SVNNodeKind nodeKind = repository.checkPath(path , revision);

        if(nodeKind == SVNNodeKind.DIR){
            ObjectNode result = Json.newObject();
            ObjectNode listData = Json.newObject();
            SVNProperties prop = new SVNProperties();
            Collection<SVNDirEntry> entries = repository.getDir(path, -1, prop, SVNDirEntry.DIRENT_ALL, (Collection)null);

            result.put("type", "folder");

            for (SVNDirEntry entry : entries) {
                ObjectNode data = Json.newObject();
                String author = entry.getAuthor();
                User user = User.findByLoginId(author);
                Long commitTime = entry.getDate().getTime();

                data.put("type", entry.getKind() == SVNNodeKind.DIR ? "folder" : "file");
                data.put("msg", entry.getCommitMessage());
                data.put("author", author);
                data.put("avatar", getAvatar(user));
                data.put("userName", user.name);
                data.put("userLoginId", user.loginId);
                data.put("createdDate", commitTime);
                data.put("commitMessage", entry.getCommitMessage());
                data.put("commiter", author);
                data.put("commitDate", commitTime);
                data.put("commitId", entry.getRevision());
                data.put("commitUrl", routes.CodeHistoryApp.show(ownerName, projectName, String.valueOf(entry.getRevision())).url());
                data.put("size", entry.getSize());

                listData.put(entry.getName(), data);
            }
            result.put("data", listData);

            return result;
        } else if(nodeKind == SVNNodeKind.FILE) {
            return fileAsJson(path, repository);
        } else {
            return null;
        }
    }

    private static String getAvatar(User user) {
        if(user.isAnonymous() || user.avatarUrl().equals(UserApp.DEFAULT_AVATAR_URL)) {
            String defaultImageUrl = "http://ko.gravatar.com/userimage/53495145/0eaeeb47c620542ad089f17377298af6.png";
            return GravatarUtil.getAvatar(user.email, 34, defaultImageUrl);
        } else {
            return user.avatarUrl();
        }
    }

    @Override
    public ObjectNode getMetaDataFromPath(String revision, String path) throws
            IOException, SVNException {
        int revisionNumber = -1;
        try {
            revisionNumber = Integer.parseInt(revision);
        } catch (NumberFormatException e) {
            play.Logger.info("Illegal SVN revision: " + revision);
        }

        return getMetaDataFromPath(revisionNumber, path);
    }

    private ObjectNode fileAsJson(String path, org.tmatesoft.svn.core.io.SVNRepository repository) throws SVNException, IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SVNProperties prop = new SVNProperties();
        repository.getFile(path, -1l, prop, baos);
        SVNDirEntry entry = repository.info(path, -1l);
        long size = entry.getSize();
        boolean isBinary;
        String mimeType;
        String data = null;

        if (size > MAX_FILE_SIZE_CAN_BE_VIEWED) {
            isBinary = true;
            mimeType = "application/octet-stream";
        } else {
            byte[] bytes = baos.toByteArray();
            isBinary = RawText.isBinary(bytes);
            if (!isBinary) {
                data = new String(bytes, FileUtil.detectCharset(bytes));
            }
            mimeType = new Tika().detect(bytes, path);
        }

        String author = prop.getStringValue(SVNProperty.LAST_AUTHOR);
        User user = User.findByLoginId(author);

        String commitDate = prop.getStringValue(SVNProperty.COMMITTED_DATE);
        DateTimeFormatter dateFormatter = ISODateTimeFormat.dateTime();
        Long commitTime = dateFormatter.parseMillis(commitDate);

        ObjectNode result = Json.newObject();
        result.put("type", "file");
        result.put("revisionNo", prop.getStringValue(SVNProperty.COMMITTED_REVISION));
        result.put("author", author);
        result.put("avatar", getAvatar(user));
        result.put("userName", user.name);
        result.put("userLoginId", user.loginId);
        result.put("createdDate", commitTime);
        result.put("commitMessage", entry.getCommitMessage());
        result.put("commiter", author);
        result.put("size", size);
        result.put("isBinary", isBinary);
        result.put("mimeType", mimeType);
        result.put("data", data);

        return result;
    }

    @Override
    public void create() throws SVNException {
        SVNRepositoryFactory.createLocalRepository(getDirectory(), true, false);
    }

    @Override
    public void delete() throws Exception {
        FileUtil.rm_rf(getDirectory());
    }

    @Override
    public String getPatch(String commitId) throws SVNException, UnsupportedEncodingException {
        long rev = Integer.parseInt(commitId);
        return getPatch(rev - 1, rev);
    }

    @Override
    public String getPatch(String revA, String revB) throws SVNException, UnsupportedEncodingException {
        return getPatch(Long.parseLong(revA), Long.parseLong(revB));
    }

    private String getPatch(long revA, long revB) throws SVNException, UnsupportedEncodingException {
        // Prepare required arguments.
        SVNURL svnURL = SVNURL.fromFile(getDirectory());

        // Get diffClient.
        SVNClientManager clientManager = SVNClientManager.newInstance();
        SVNDiffClient diffClient = clientManager.getDiffClient();

        // Using diffClient, write the changes by commitId into
        // byteArrayOutputStream, as unified format.
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        diffClient.doDiff(svnURL, null, SVNRevision.create(revA), SVNRevision.create(revB),
                SVNDepth.INFINITY, true, byteArrayOutputStream);

        return byteArrayOutputStream.toString(Config.getCharset().name());
    }

    @Override
    public List<FileDiff> getDiff(String commitId) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<FileDiff> getDiff(String revA, String revB) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Commit> getHistory(int page, int limit, String until, String path) throws
            IOException, GitAPIException, SVNException {
        // Get the repository
        SVNURL svnURL = SVNURL.fromFile(getDirectory());
        org.tmatesoft.svn.core.io.SVNRepository repository = SVNRepositoryFactory.create(svnURL);

        // path to get log
        String[] paths = {"/"};
        if(path != null){
            paths[0] = "/" + path;
        }

        // Determine revisions
        long startRevision = repository.getLatestRevision() - page * limit;
        long endRevision = startRevision - limit;
        if (endRevision < 1) {
            endRevision = 1;
        }

        // No log to return.
        if (startRevision < endRevision) {
            return new ArrayList<>();
        }

        // Get the logs
        List<Commit> result = new ArrayList<>();
        for(Object entry : repository.log(paths, null, startRevision, endRevision, false, false)) {
            result.add(new SvnCommit((SVNLogEntry) entry));
        }

        return result;
    }

    @Override
    public Commit getCommit(String revNumber) throws IOException, SVNException {
        long rev = Integer.parseInt(revNumber);
        String[] paths = {"/"};
        SVNURL svnURL = SVNURL.fromFile(getDirectory());
        org.tmatesoft.svn.core.io.SVNRepository repository = SVNRepositoryFactory.create(svnURL);

        for(Object entry : repository.log(paths, null, rev, rev, false, false)) {
            return new SvnCommit((SVNLogEntry) entry);
        }

        return null;
    }

    @Override
    public List<String> getRefNames() {
        ArrayList<String> branches = new ArrayList<>();
        branches.add(SVNRevision.HEAD.getName());
        return branches;
    }

    @Override
    public Resource asResource() {
        return new Resource() {
            @Override
            public String getId() {
                return null;
            }

            @Override
            public Project getProject() {
                return Project.findByOwnerAndProjectName(ownerName, projectName);
            }

            @Override
            public ResourceType getType() {
                return ResourceType.CODE;
            }

        };
    }

    private org.tmatesoft.svn.core.io.SVNRepository getSVNRepository() throws SVNException {
        SVNURL svnURL = SVNURL.fromFile(getDirectory());

        return SVNRepositoryFactory.create(svnURL);
    }

    public boolean isFile(String path, long rev) throws SVNException {
        return getSVNRepository().checkPath(path, rev) == SVNNodeKind.FILE;
    }

    @Override
    public boolean isFile(String path) throws SVNException, IOException {
        return isFile(path, getSVNRepository().getLatestRevision());
    }

    @Override
    public boolean isFile(String path, String revStr) throws SVNException {
        return isFile(path, Long.parseLong(revStr));
    }


    @Override
    public boolean renameTo(String projectName) {
        return move(this.ownerName, this.projectName, this.ownerName, projectName);
    }

    @Override
    public String getDefaultBranch() throws IOException {
        return "HEAD";
    }

    @Override
    public void setDefaultBranch(String target) throws IOException {
    }

    @Override
    public Commit getParentCommitOf(String revNumber) {
        Long rev = Long.parseLong(revNumber) - 1;
        try {
            return getCommit(rev.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isEmpty() {
        SVNURL svnURL;
        org.tmatesoft.svn.core.io.SVNRepository repository = null;
        try {
            svnURL = SVNURL.fromFile(getDirectory());
            repository = SVNRepositoryFactory.create(svnURL);
            return repository.getLatestRevision() == 0;
        } catch (SVNException e) {
            throw new RuntimeException(e);
        } finally {
            if(repository != null) {
                repository.closeSession();
            }
        }
    }

    public boolean move(String srcProjectOwner, String srcProjectName, String desrProjectOwner, String destProjectName) {
        File src = new File(getRootDirectory(), srcProjectOwner + "/" + srcProjectName);
        File dest = new File(getRootDirectory(), desrProjectOwner + "/" + destProjectName);
        src.setWritable(true);

        try {
            if(src.exists()) {
                FileUtils.moveDirectory(src, dest);
            }
            return true;
        } catch (IOException e) {
            play.Logger.error("Move Failed", e);
            return false;
        }
    }

    @Override
    public File getDirectory() {
        return new File(getRootDirectory(), ownerName + "/" + projectName);
    }

    public static File getRootDirectory() {
        return new File(Config.getYobiHome(), getRepoPrefix());
    }
}
