package playRepository;

import java.io.*;
import java.util.*;

import javax.servlet.*;

import controllers.UserApp;
import models.Project;
import models.User;
import models.enumeration.ResourceType;
import models.resource.Resource;

import org.apache.tika.Tika;
import org.codehaus.jackson.node.ObjectNode;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.errors.AmbiguousObjectException;

import org.tigris.subversion.javahl.*;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNRevision;

import controllers.ProjectApp;

import play.libs.Json;

import utils.FileUtil;
import utils.GravatarUtil;

import org.joda.time.*;
import org.joda.time.format.*;

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

    public SVNRepository(final String userName, String projectName) throws ServletException {
        this.ownerName = userName;
        this.projectName = projectName;
    }

    @Override
    public byte[] getRawFile(String revision, String path) throws SVNException {
        Long revId = (revision.equals("HEAD") ? -1l : Long.parseLong(revision));
        org.tmatesoft.svn.core.io.SVNRepository repository = getSVNRepository();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        repository.getFile(path, revId, null, baos); // revId=-1l
        return baos.toByteArray();
    }

    @Override
    public ObjectNode getMetaDataFromPath(String path) throws SVNException {
        org.tmatesoft.svn.core.io.SVNRepository repository = getSVNRepository();

        SVNNodeKind nodeKind = repository.checkPath(path , -1 );

        if(nodeKind == SVNNodeKind.DIR){
            //폴더 내용 출력
            ObjectNode result = Json.newObject();
            ObjectNode listData = Json.newObject();
            SVNProperties prop = new SVNProperties();
            Collection<SVNDirEntry> entries = repository.getDir(path, -1, prop, SVNDirEntry.DIRENT_ALL, (Collection)null);

            result.put("type", "folder");

            Iterator<SVNDirEntry> iterator = entries.iterator( );

            while(iterator.hasNext()){
                SVNDirEntry entry = iterator.next( );

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
                data.put("size", entry.getSize());

                listData.put(entry.getName(), data);
            }
            result.put("data", listData);

            return result;

        } else if(nodeKind == SVNNodeKind.FILE) {
            //파일 내용 출력
            return fileAsJson(path, repository);
        } else {
            return null;
        }
    }

    private static String getAvatar(User user) {
        if(user.isAnonymous() || user.avatarUrl.equals(UserApp.DEFAULT_AVATAR_URL)) {
            String defaultImageUrl = "http://ko.gravatar.com/userimage/53495145/0eaeeb47c620542ad089f17377298af6.png";
            return GravatarUtil.getAvatar(user.email, 34, defaultImageUrl);
        } else {
            return user.avatarUrl;
        }
    }

    @Override
    public ObjectNode getMetaDataFromPath(String branch, String path) throws AmbiguousObjectException,
            IOException, SVNException {
        org.tmatesoft.svn.core.io.SVNRepository repository = getSVNRepository();

        SVNNodeKind nodeKind = repository.checkPath(path , -1 );

        if(nodeKind == SVNNodeKind.DIR){
            //폴더 내용 출력
            ObjectNode result = Json.newObject();
            ObjectNode listData = Json.newObject();
            SVNProperties prop = new SVNProperties();
            Collection<SVNDirEntry> entries = repository.getDir(path, -1, prop, SVNDirEntry.DIRENT_ALL, (Collection)null);

            result.put("type", "folder");

            Iterator<SVNDirEntry> iterator = entries.iterator();

            while(iterator.hasNext()){
                SVNDirEntry entry = iterator.next();

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

    private ObjectNode fileAsJson(String path, org.tmatesoft.svn.core.io.SVNRepository repository) throws SVNException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SVNProperties prop = new SVNProperties();
        repository.getFile(path, -1l, prop, baos);
        long size = repository.info(path, -1l).getSize();
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
                data = new String(bytes);
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
        //result.put("commitMessage", ""); //TODO: 커밋메시지
        result.put("commiter", author);
        result.put("size", size);
        result.put("isBinary", isBinary);
        result.put("mimeType", mimeType);
        result.put("data", data);

        return result;
    }

    @Override
    public void create() throws ClientException {
        String svnPath = new File(SVNRepository.getRepoPrefix() + ownerName + "/" + projectName)
                .getAbsolutePath();
        new org.tigris.subversion.javahl.SVNAdmin().create(svnPath, false, false, null, "fsfs");
    }

    @Override
    public void delete() {
        FileUtil.rm_rf(new File(getRepoPrefix() + ownerName + "/" + projectName));
    }

    @Override
    public String getPatch(String commitId) throws SVNException {
        // Prepare required arguments.
        SVNURL svnURL = SVNURL.fromFile(new File(getRepoPrefix() + ownerName + "/" + projectName));
        long rev = Integer.parseInt(commitId);

        // Get diffClient.
        SVNClientManager clientManager = SVNClientManager.newInstance();
        SVNDiffClient diffClient = clientManager.getDiffClient();

        // Using diffClient, write the changes by commitId into
        // byteArrayOutputStream, as unified format.
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        diffClient.doDiff(svnURL, null, SVNRevision.create(rev - 1), SVNRevision.create(rev),
                SVNDepth.INFINITY, true, byteArrayOutputStream);


        return byteArrayOutputStream.toString();
    }

    @Override
    public List<FileDiff> getDiff(String commitId) throws GitAPIException, IOException, SVNException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Commit> getHistory(int page, int limit, String until, String path) throws AmbiguousObjectException,
            IOException, NoHeadException, GitAPIException, SVNException {
        // Get the repository
        SVNURL svnURL = SVNURL.fromFile(new File(repoPrefix + ownerName + "/" + projectName));
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
        SVNURL svnURL = SVNURL.fromFile(new File(getRepoPrefix() + ownerName + "/" + projectName));
        org.tmatesoft.svn.core.io.SVNRepository repository = SVNRepositoryFactory.create(svnURL);

        for(Object entry : repository.log(paths, null, rev, rev, false, false)) {
            return new SvnCommit((SVNLogEntry) entry);
        }

        return null;
    }

    @Override
    public List<String> getBranches() {
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
                return ProjectApp.getProject(ownerName, projectName);
            }

            @Override
            public ResourceType getType() {
                return ResourceType.CODE;
            }

        };
    }

    private org.tmatesoft.svn.core.io.SVNRepository getSVNRepository() throws SVNException {
        SVNURL svnURL = SVNURL.fromFile(new File(getRepoPrefix() + ownerName + "/" +
                projectName));

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
        return isFile(path, Long.valueOf(revStr));
    }


    /**
     * 코드저장소 프로젝트명을 변경하고 결과를 반환한다.
     * @param projectName
     * @return 코드저장소 이름 변경성공시 true / 실패시 false
     */
    @Override
    public boolean renameTo(String projectName) {

        File src = new File(getRepoPrefix() + this.ownerName + "/" + this.projectName);
        File dest = new File(getRepoPrefix() + this.ownerName + "/" + projectName);
        src.setWritable(true);

        return src.renameTo(dest);

    }

    @Override
    public VCSRef getSymbolicRef(String ref) throws IOException, UnsupportedOperationException {
        throw new UnsupportedOperationException("symbolic-ref is unsupported method");
    }

    @Override
    public void updateSymbolicRef(String ref, String target) throws IOException, UnsupportedOperationException {
        throw new UnsupportedOperationException("symbolic-ref is unsupported method");
    }

    /**
     * {@code #revNumber}의 이전 리비전에 해당하는 커밋 정보를 번환한다.
     *
     * @param revNumber
     * @return
     */
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
        SVNURL svnURL = null;
        org.tmatesoft.svn.core.io.SVNRepository repository = null;
        try {
            svnURL = SVNURL.fromFile(new File(repoPrefix + ownerName + "/" + projectName));
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
}
