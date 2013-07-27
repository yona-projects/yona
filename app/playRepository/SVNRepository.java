package playRepository;

import java.io.*;
import java.util.*;

import javax.servlet.*;

import models.Project;
import models.User;
import models.enumeration.ResourceType;
import models.resource.Resource;

import org.apache.tika.Tika;
import org.codehaus.jackson.node.ObjectNode;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
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
    public byte[] getRawFile(String path) throws SVNException {
        org.tmatesoft.svn.core.io.SVNRepository repository = getSVNRepository();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        repository.getFile(path, -1l, null, baos);
        return baos.toByteArray();
    }

    @Override
    public ObjectNode findFileInfo(String path) throws SVNException {
        org.tmatesoft.svn.core.io.SVNRepository repository = getSVNRepository();

        SVNNodeKind nodeKind = repository.checkPath(path , -1 );

        if(nodeKind == SVNNodeKind.DIR){
            //폴더 내용 출력
            ObjectNode result = Json.newObject();

            result.put("type", "folder");
            ObjectNode listData = Json.newObject();

            SVNProperties prop = new SVNProperties();

            Collection<SVNDirEntry> entries = repository.getDir(path, -1, prop, SVNDirEntry.DIRENT_ALL, (Collection)null);

            Iterator<SVNDirEntry> iterator = entries.iterator( );
            while ( iterator.hasNext( ) ) {
                SVNDirEntry entry = iterator.next( );

                ObjectNode data = Json.newObject();
                data.put("type", entry.getKind() == SVNNodeKind.DIR ? "folder" : "file");
                data.put("msg", entry.getCommitMessage());
                String author = entry.getAuthor();
                data.put("author", author);
                data.put("avatar", getAvatar(author));
                data.put("createdDate", entry.getDate().getTime());

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

    private static String getAvatar(String author) {
        User user = User.findByLoginId(author);
        if(user.isAnonymous()) {
            return "/assets/images/default-avatar-34.png";
        } else {
            return user.avatarUrl;
        }
    }

    @Override
    public ObjectNode findFileInfo(String branch, String path) throws AmbiguousObjectException,
            IOException, SVNException {
        org.tmatesoft.svn.core.io.SVNRepository repository = getSVNRepository();

        SVNNodeKind nodeKind = repository.checkPath(path , -1 );

        if(nodeKind == SVNNodeKind.DIR){
            //폴더 내용 출력
            ObjectNode result = Json.newObject();

            result.put("type", "folder");
            ObjectNode listData = Json.newObject();

            SVNProperties prop = new SVNProperties();

            Collection<SVNDirEntry> entries = repository.getDir(path, -1, prop, SVNDirEntry.DIRENT_ALL, (Collection)null);

            Iterator<SVNDirEntry> iterator = entries.iterator( );
            while ( iterator.hasNext( ) ) {
                SVNDirEntry entry = iterator.next( );

                ObjectNode data = Json.newObject();
                data.put("type", entry.getKind() == SVNNodeKind.DIR ? "folder" : "file");
                data.put("msg", entry.getCommitMessage());
                String author = entry.getAuthor();
                data.put("author", author);
                data.put("avatar", getAvatar(author));
                data.put("createdDate", entry.getDate().getTime());

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
            isBinary = FileUtil.isBinary(bytes);
            if (!isBinary) {
                data = new String(bytes);
            }
            mimeType = new Tika().detect(bytes, path);
        }
        String author = prop.getStringValue(SVNProperty.LAST_AUTHOR);

        ObjectNode result = Json.newObject();
        result.put("type", "file");
        result.put("revisionNo", prop.getStringValue(SVNProperty.COMMITTED_REVISION));
        result.put("author", author);
        result.put("avatar", getAvatar(author));
        result.put("createdDate", prop.getStringValue(SVNProperty.COMMITTED_DATE));
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
        diffClient.doDiff(svnURL, null, SVNRevision.create(rev), SVNRevision.create(rev - 1),
                SVNDepth.INFINITY, true, byteArrayOutputStream);


        return byteArrayOutputStream.toString();
    }

    @Override
    public List<Commit> getHistory(int page, int limit, String until) throws AmbiguousObjectException,
            IOException, NoHeadException, GitAPIException, SVNException {
        // Get the repository
        SVNURL svnURL = SVNURL.fromFile(new File(repoPrefix + ownerName + "/" + projectName));
        org.tmatesoft.svn.core.io.SVNRepository repository = SVNRepositoryFactory.create(svnURL);

        // path to get log
        String[] paths = {"/"};

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
            public Long getId() {
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
}
