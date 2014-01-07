package playRepository;

import controllers.ProjectApp;
import controllers.UserApp;
import models.Project;
import models.PullRequest;
import models.User;
import models.enumeration.ResourceType;
import models.resource.Resource;

import org.apache.commons.collections.IteratorUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.ObjectNode;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.diff.*;
import org.eclipse.jgit.diff.Edit.Type;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.WindowCacheConfig;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.io.NullOutputStream;
import org.tmatesoft.svn.core.SVNException;

import play.Logger;
import play.libs.Json;
import utils.FileUtil;
import utils.GravatarUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

import static org.eclipse.jgit.diff.DiffEntry.ChangeType.*;

/**
 * Git 저장소
 */
public class GitRepository implements PlayRepository {

    /**
     * Git 저장소 베이스 디렉토리
     */
    private static String repoPrefix = "repo/git/";

    /**
     * Git 저장소 베이스 디렉토리
     */
    private static String repoForMergingPrefix = "repo/git-merging/";

    public static String getRepoPrefix() {
        return repoPrefix;
    }

    public static void setRepoPrefix(String repoPrefix) {
        GitRepository.repoPrefix = repoPrefix;
    }

    public static String getRepoForMergingPrefix() {
        return repoForMergingPrefix;
    }

    public static void setRepoForMergingPrefix(String repoForMergingPrefix) {
        GitRepository.repoForMergingPrefix = repoForMergingPrefix;
    }

    private final Repository repository;
    private final String ownerName;
    private final String projectName;

    /**
     * 매개변수로 전달받은 {@code ownerName}과 {@code projectName}을 사용하여 Git 저장소를 참조할 {@link GitRepository}를 생성한다.
     *
     * @param ownerName
     * @param projectName
     * @throws IOException
     * @see #buildGitRepository(String, String)
     */
    public GitRepository(String ownerName, String projectName) {
        this.ownerName = ownerName;
        this.projectName = projectName;
        this.repository = buildGitRepository(ownerName, projectName);
    }

    /**
     * {@code project} 정보를 사용하여 Git 저장소를 참조할 {@link GitRepository}를 생성한다.
     *
     * @param project
     * @throws IOException
     * @see #GitRepository(String, String)
     */
    public GitRepository(Project project) throws IOException {
        this(project.owner, project.name);
    }

    /**
     * {@code ownerName}과 {@code projectName}을 받아서 {@link Repository} 객체를 생성한다.
     *
     * 실제 저장소를 생성하는건 아니고, Git 저장소를 참조할 수 있는 {@link Repository} 객체를 생성한다.
     * 생성된 {@link Repository} 객체를 사용해서 기존에 만들어져있는 Git 저장소를 참조할 수도 있고, 새 저장소를 생성할 수도 있다.
     *
     * @param ownerName
     * @param projectName
     * @return
     * @throws IOException
     */
    public static Repository buildGitRepository(String ownerName, String projectName) {
        try {
            return new RepositoryBuilder()
                .setGitDir(new File(getGitDirectory(ownerName, projectName)))
                .addAlternateObjectDirectory(new File(getDirectoryForMergingObjects(ownerName, projectName)))
                .build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * {@code project}의 {@link Project#owner}와 {@link Project#name}을 사용하여 {@link Repository} 객체를 생성한다.
     *
     * @param project
     * @return
     * @throws IOException
     * @see #buildGitRepository(String, String)
     */
    public static Repository buildGitRepository(Project project) {
        return buildGitRepository(project.owner, project.name);
    }

    /**
     * 로컬에 있는 Git 저장소를 복제(clone)한다.
     *
     * 디스크 공간을 절약하기 위해 우선 object들을 하드링크하는 클론을 시도하고,
     * 실패한 경우에는 기본 JGit 클론을 한다.
     *
     * @param originalProject
     * @param forkProject
     * @throws IOException
     * @throws GitAPIException
     */
    public static void cloneLocalRepository(Project originalProject, Project forkProject)
            throws IOException, GitAPIException {
        try {
            cloneHardLinkedRepository(originalProject, forkProject);
        } catch (Exception e) {
            new GitRepository(forkProject).delete();
            play.Logger.warn(
                    "Failed to clone a repository using hardlink. Fall back to straight copy", e);
            cloneRepository(getGitDirectoryURL(originalProject), forkProject);
        }
    }

    /**
     * bare 모드로 Git 저장소를 생성한다.
     *
     * @throws IOException
     * @see <a href="https://www.kernel.org/pub/software/scm/git/docs/gitglossary.html#def_bare_repository">bare repository</a>
     * @see Repository#create()
     */
    @Override
    public void create() throws IOException {
        this.repository.create(true);
    }

    /**
     * {@link Constants#HEAD}에서 {@code path}가 디렉토리일 경우에는 해당 디렉토리에 들어있는 파일과 디렉토리 목록을 JSON으로 반환하고,
     * 파일일 경우에는 해당 파일 정보를 JSON으로 반환한다.
     *
     * @param path
     * @return {@code path}가 디렉토리이면 그 안에 들어있는 파일과 디렉토리 목록을 담고있는 JSON, 파일이면 해당 파일 정보를 담고 있는 JSON
     * @throws IOException
     * @throws NoHeadException
     * @throws GitAPIException
     * @throws SVNException
     * @see #getMetaDataFromPath(String, String)
     */
    @Override
    public ObjectNode getMetaDataFromPath(String path) throws IOException, GitAPIException, SVNException {
        return getMetaDataFromPath(null, path);
    }

    public boolean isFile(String path, String revStr) throws IOException {
        ObjectId objectId = getObjectId(revStr);

        if (objectId == null) {
            return false;
        }

        RevWalk revWalk = new RevWalk(repository);
        RevTree revTree = revWalk.parseTree(objectId);
        TreeWalk treeWalk = new TreeWalk(repository);
        treeWalk.addTree(revTree);

        while (treeWalk.next()) {
            if (treeWalk.getPathString().equals(path) && !treeWalk.isSubtree()) {
                return true;
            }
        }

        return false;
    }

    /**
     * {@code branch}에서 {@code path}가 디렉토리일 경우에는 해당 디렉토리에 들어있는 파일과 디렉토리 목록을 JSON으로 반환하고,
     * 파일일 경우에는 해당 파일 정보를 JSON으로 반환한다.
     *
     *
     * {@code branch}가 null이면 {@link Constants#HEAD}의 Commit을 가져오고, null이 아닐때는 해당 브랜치의 head Commit을 가져온다.
     * Commit의 루트 Tree를 가져오고, {@link TreeWalk}로 해당 Tree를 순회하며 {@code path}에 해당하는 디렉토리나 파일을 찾는다.
     * {@code path}가 디렉토리이면 해당 디렉토리로 들어가서 그 안에 있는 파일과 디렉토리 목록을 JSON으로 만들어서 변환한다.
     * {@code path}가 파일이면 해당 파일 정보를 JSON으로 만들어 반환한다.
     *
     * @param branch
     * @param path
     * @return {@code path}가 디렉토리이면 그 안에 들어있는 파일과 디렉토리 목록을 담고있는 JSON, 파일이면 해당 파일 정보를 담고 있는 JSON
     * @throws IOException
     * @throws GitAPIException
     */
    @Override
    public ObjectNode getMetaDataFromPath(String branch, String path) throws IOException, GitAPIException {
        RevCommit headCommit = getRevCommit(branch);
        if (headCommit == null) {
            Logger.debug("GitRepository : init Project - No Head commit");
            return null;
        }

        RevWalk revWalk = new RevWalk(repository);
        RevTree revTree = revWalk.parseTree(headCommit);
        TreeWalk treeWalk = new TreeWalk(repository);
        treeWalk.addTree(revTree);

        if (path.isEmpty()) {
            return treeAsJson(path, treeWalk, headCommit);
        }

        PathFilter pathFilter = PathFilter.create(path);
        treeWalk.setFilter(pathFilter);
        while (treeWalk.next()) {
            if (pathFilter.isDone(treeWalk)) {
                break;
            } else if (treeWalk.isSubtree()) {
                treeWalk.enterSubtree();
            }
        }

        if (treeWalk.isSubtree()) {
            treeWalk.enterSubtree();
            return treeAsJson(path, treeWalk, headCommit);
        } else {
            return fileAsJson(treeWalk, headCommit);
        }
    }

    /**
     * {@code treeWalk}가 현재 위치한 파일 메타데이터를 JSON 데이터로 변환하여 반환한다.
     * 그 파일에 대한 {@code untilCommitId} 혹은 그 이전 커밋 중에서 가장 최근 커밋 정보를 사용하여 Commit
     * 메시지와 author 정보등을 같이 반환한다.
     *
     * @param treeWalk
     * @param untilCommitId
     * @return
     * @throws IOException
     * @see <a href="https://www.kernel.org/pub/software/scm/git/docs/git-log.html">git log until</a>
     */
    private ObjectNode fileAsJson(TreeWalk treeWalk, AnyObjectId untilCommitId) throws IOException, GitAPIException {
        Git git = new Git(repository);

        GitCommit commit = new GitCommit(git.log()
            .add(untilCommitId)
            .addPath(treeWalk.getPathString())
            .call()
            .iterator()
            .next());

        ObjectNode result = Json.newObject();
        long commitTime = commit.getCommitTime() * 1000L;
        User author = commit.getAuthor();

        result.put("type", "file");
        result.put("msg", commit.getShortMessage());
        result.put("author", commit.getAuthorName());
        result.put("avatar", getAvatar(author));
        result.put("userName", author.name);
        result.put("userLoginId", author.loginId);
        result.put("createdDate", commitTime);
        result.put("commitMessage", commit.getShortMessage());
        result.put("commiter", commit.getCommitterName());
        result.put("commitDate", commitTime);
        result.put("commitId", untilCommitId.getName());
        ObjectLoader file = repository.open(treeWalk.getObjectId(0));
        result.put("size", file.getSize());

        boolean isBinary = RawText.isBinary(file.openStream());
        result.put("isBinary", isBinary);
        if (!isBinary && file.getSize() < MAX_FILE_SIZE_CAN_BE_VIEWED) {
            byte[] bytes = file.getBytes();
            String str = new String(bytes, FileUtil.detectCharset(bytes));
            result.put("data", str);
        }
        Metadata meta = new Metadata();
        meta.add(Metadata.RESOURCE_NAME_KEY, treeWalk.getNameString());
        result.put("mimeType", new Tika().detect(file.openStream(), meta));

        return result;
    }

    private String getAvatar(User user) {
        if(user.isAnonymous() || user.avatarUrl().equals(UserApp.DEFAULT_AVATAR_URL)) {
            return GravatarUtil.getAvatar(user.email, 34);
        } else {
            return user.avatarUrl();
        }
    }

    /**
     * {@code treeWalk}가 현재 위치한 디렉토리에 들어있는 파일과 디렉토리 메타데이터를 JSON 데이터로 변환하여 반환한다.
     * 각 파일과 디렉토리에 대한 {@code untilCommitId} 혹은 그 이전 커밋 중에서 가장 최근 커밋 정보를 사용하여 Commit 메시지와 author 정보등을 같이 반홚다.
     *
     * @param treeWalk
     * @param untilCommitId
     * @return
     * @throws IOException
     * @throws GitAPIException
     * @see <a href="https://www.kernel.org/pub/software/scm/git/docs/git-log.html">git log until</a>
     */
    private ObjectNode treeAsJson(String basePath, TreeWalk treeWalk, AnyObjectId untilCommitId) throws IOException, GitAPIException {
        ObjectNode result = Json.newObject();
        ObjectNode listData = Json.newObject();
        listData.putAll(new ObjectFinder(basePath, treeWalk, untilCommitId).find());
        result.put("type", "folder");
        result.put("data", listData);
        return result;
    }

    public class ObjectFinder {
        private SortedMap<String, JsonNode> found = new TreeMap<>();
        private Map<String, JsonNode> targets = new HashMap<>();
        private String basePath;
        private AnyObjectId untilCommitId;
        private Iterator<RevCommit> commitIterator;

        public ObjectFinder(String basePath, TreeWalk treeWalk, AnyObjectId untilCommitId) throws IOException, GitAPIException {
            while (treeWalk.next()) {
                String path = treeWalk.getNameString();
                ObjectNode object = Json.newObject();
                object.put("type", treeWalk.isSubtree() ? "folder" : "file");
                targets.put(path, object);
            }
            this.basePath = basePath;
            this.untilCommitId = untilCommitId;
            this.commitIterator = getCommitIterator();
        }

        public SortedMap<String, JsonNode> find() throws IOException {
            while (shouldFindMore()) {
                RevCommit commit = commitIterator.next();
                Map<String, ObjectId> objects = findObjects(commit);
                found(commit, objects);
            }
            return found;
        }

        /*
         * get commit logs with untilCommitId and basePath
         */
        private Iterator<RevCommit> getCommitIterator() throws IOException, GitAPIException {
            Git git = new Git(repository);
            LogCommand logCommand = git.log().add(untilCommitId);
            if (StringUtils.isNotEmpty(basePath)) {
                logCommand.addPath(basePath);
             }
            return logCommand.call().iterator();
        }

        private boolean shouldFindMore() {
            // If targets is empty, it means we have found every interested objects and no need to continue.
            if (targets.isEmpty()) {
                return false;
            }
            return commitIterator.hasNext();
        }

        private Map<String, ObjectId> findObjects(RevCommit commit) throws IOException {
            final Map<String, ObjectId> objects = new HashMap<>();

            // We want to find the latest commit for each of `targets`. We already know they have
            // same `basePath`. So get every blobs and trees match one of `targets`, under the
            // `basePath`, and put them into `objects`.
            TreeWalkHandler objectCollector = new TreeWalkHandler() {
                @Override
                public void handle(TreeWalk treeWalk) {
                    if (targets.containsKey(treeWalk.getNameString())) {
                        objects.put(treeWalk.getNameString(), treeWalk.getObjectId(0));
                    }
                }
            };

            // Remove every blob and tree from `objects` if any of parent commits have a
            // object whose path and id is identical with the blob or the tree. It means the
            // blob or tree is not changed so we are not interested in it.
            TreeWalkHandler objectRemover = new TreeWalkHandler() {
                @Override
                public void handle(TreeWalk treeWalk) {
                    if (treeWalk.getObjectId(0).equals(objects.get(treeWalk.getNameString()))) {
                        objects.remove(treeWalk.getNameString());
                    }
                }
            };

            // Choose only "interest" objects from the blobs and trees. We are interested in
            // blobs and trees which has change between the last commit and the current commit.
            traverseTree(commit, objectCollector);
            for(RevCommit parent : commit.getParents()) {
                traverseTree(parent, objectRemover);
            }
            return objects;
        }

        private void traverseTree(RevCommit commit, TreeWalkHandler handler) throws IOException {
            TreeWalk treeWalk;
            if (StringUtils.isEmpty(basePath)) {
                treeWalk = new TreeWalk(repository);
                treeWalk.addTree(commit.getTree());
            } else {
                treeWalk = TreeWalk.forPath(repository, basePath, commit.getTree());
                if (treeWalk == null) {
                    return;
                }
                treeWalk.enterSubtree();
            }
            while (treeWalk.next()) {
                handler.handle(treeWalk);
            }
        }

        /*
         * Now, every objects in `objects` are interested. Get metadata from the objects, put
         * them into `found` and remove from `targets`.
         */
        private void found(RevCommit revCommit, Map<String, ObjectId> objects) {
            for (String path : objects.keySet()) {
                GitCommit commit = new GitCommit(revCommit);
                ObjectNode data = (ObjectNode) targets.get(path);
                data.put("msg", commit.getShortMessage());
                String emailAddress = commit.getAuthorEmail();
                User user = User.findByEmail(emailAddress);
                data.put("avatar", getAvatar(user));
                data.put("userName", user.name);
                data.put("userLoginId", user.loginId);
                data.put("createdDate", revCommit.getCommitTime() * 1000l);
                data.put("author", commit.getAuthorName());
                found.put(path, data);
                targets.remove(path);
            }
        }
    }

    public static interface TreeWalkHandler {
        void handle(TreeWalk treeWalk);
    }

    /**
     * {@link Constants#HEAD}에서 {@code path}에 해당하는 파일을 반환한다.
     *
     * @param revision
     * @param path
     * @return {@code path}가 디렉토리일 경우에는 null, 아닐때는 해당 파일
     * @throws IOException
     */
    @Override
    public byte[] getRawFile(String revision, String path) throws IOException {
        RevTree tree = new RevWalk(repository).parseTree(repository.resolve(revision));
        TreeWalk treeWalk = TreeWalk.forPath(repository, path, tree);
        if (treeWalk.isSubtree()) {
            return null;
        } else {
            return repository.open(treeWalk.getObjectId(0)).getBytes();
        }
    }

    /**
     * Git 저장소 디렉토리를 삭제한다.
     * 변경전 {@code repository.close()}를 통해 open된 repository의 리소스를 반환하고
     * repository 내부에서 사용하는 {@code Cache}를 초기화하여 packFile의 참조를 제거한다.
     */
    @Override
    public void delete() {
        repository.close();
        WindowCacheConfig config = new WindowCacheConfig();
        config.install();
        FileUtil.rm_rf(repository.getDirectory());
    }

    /**
     * {@code rev}에 해당하는 리비전의 변경 내역을 반환한다.
     *
     * {@code rev}에 해당하는 커밋의 root tree와 그 이전 커밋의 root tree를 비교한다.
     * 이전 커밋이 없을 때는 비어있는 트리와 비교한다.
     *
     * @param rev
     * @return
     * @throws GitAPIException
     * @throws IOException
     */
    @Override
    public String getPatch(String rev) throws IOException {
        // Get the trees, from current commit and its parent, as treeWalk.
        ObjectId commitId = repository.resolve(rev);

        if (commitId == null) {
            return null;
        }

        TreeWalk treeWalk = new TreeWalk(repository);
        RevWalk revWalk = new RevWalk(repository);
        RevCommit commit = revWalk.parseCommit(commitId);
        if (commit.getParentCount() > 0) {
            RevTree tree = revWalk.parseCommit(commit.getParent(0).getId()).getTree();
            treeWalk.addTree(tree);
        } else {
            treeWalk.addTree(new EmptyTreeIterator());
        }
        treeWalk.addTree(commit.getTree());

        // Render the difference from treeWalk which has two trees.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DiffFormatter diffFormatter = new DiffFormatter(out);
        diffFormatter.setRepository(repository);
        treeWalk.setRecursive(true);
        diffFormatter.format(DiffEntry.scan(treeWalk));

        return out.toString("UTF-8");
    }

    /**
     * {@code rev}에 해당하는 리비전의 변경 내역을 반환한다.
     *
     * {@code rev}에 해당하는 커밋의 root tree와 그 이전 커밋의 root tree를 비교한다.
     * 이전 커밋이 없을 때는 비어있는 트리와 비교한다.
     *
     * @param rev
     * @return
     * @throws GitAPIException
     * @throws IOException
     */
    public List<FileDiff> getDiff(String rev) throws IOException {
        return getDiff(repository.resolve(rev));
    }

    public List<FileDiff> getDiff(ObjectId commitId) throws IOException {
        if (commitId == null) {
            return null;
        }

        // Get the trees, from current commit and its parent, as treeWalk.
        RevWalk revWalk = new RevWalk(repository);
        RevCommit commit = revWalk.parseCommit(commitId);
        ObjectId commitIdA = null;
        if (commit.getParentCount() > 0) {
            commitIdA = commit.getParent(0).getId();
        }

        return getFileDiffs(repository, repository, commitIdA, commitId);
    }

    public List<FileDiff> getDiff(RevCommit commit) throws IOException {
        ObjectId commitIdA = null;
        if (commit.getParentCount() > 0) {
            commitIdA = commit.getParent(0).getId();
        }

        return getFileDiffs(repository, repository, commitIdA, commit.getId());
    }

    /**
     * {@code untilRevName}에 해당하는 리비전까지의 커밋 목록을 반환한다.
     * {@code untilRevName}이 null이면 HEAD 까지의 커밋 목록을 반환한다.
     *
     * @param pageNumber 0부터 시작
     * @param pageSize
     * @param untilRevName
     * @return
     * @throws IOException
     * @throws GitAPIException
     */
    @Override
    public List<Commit> getHistory(int pageNumber, int pageSize, String untilRevName, String path)
            throws IOException, GitAPIException {
        // Get the list of commits from HEAD to the given pageNumber.
        LogCommand logCommand = new Git(repository).log();
        if (path != null) {
            logCommand.addPath(path);
        }

        RevCommit start = getRevCommit(untilRevName);
        if (start == null) {
            return null;
        }
        logCommand.add(start);

        Iterable<RevCommit> iter = logCommand.setMaxCount(pageNumber * pageSize + pageSize).call();
        List<RevCommit> list = new LinkedList<>();
        for (RevCommit commit : iter) {
            if (list.size() >= pageSize) {
                list.remove(0);
            }
            list.add(commit);
        }

        List<Commit> result = new ArrayList<>();
        for (RevCommit commit : list) {
            result.add(new GitCommit(commit));
        }

        return result;
    }

    @Override
    public Commit getCommit(String rev) throws IOException {
        ObjectId commitId = repository.resolve(rev);

        if (commitId == null) {
            return null;
        }

        return new GitCommit(new RevWalk(repository).parseCommit(commitId));
    }

    /**
     * Git 저장소의 모든 브랜치 이름을 반환한다.
     * @return
     */
    @Override
    public List<String> getBranches() {
        return new ArrayList<>(repository.getAllRefs().keySet());
    }

    public List<GitBranch> getAllBranches() throws IOException, GitAPIException {
        List<GitBranch> branches = new ArrayList<>();

        for(Ref branchRef : new Git(repository).branchList().call()) {
            RevWalk walk = new RevWalk(repository);
            RevCommit head = walk.parseCommit(branchRef.getObjectId());
            walk.dispose();

            GitBranch newBranch = new GitBranch(branchRef.getName(), new GitCommit(head));
            setTheLatestPullRequest(newBranch);

            branches.add(newBranch);
        }

        Collections.sort(branches, new Comparator<GitBranch>() {
            @Override
            public int compare(GitBranch b1, GitBranch b2) {
                return b2.getHeadCommit().getCommitterDate().compareTo(b1.getHeadCommit().getCommitterDate());
            }
        });

        return branches;
    }

    private void setTheLatestPullRequest(GitBranch gitBranch) {
        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);
        gitBranch.setPullRequest(PullRequest.findTheLatestOneFrom(project, gitBranch.getName()));
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

    @Override
    public boolean isFile(String path) throws IOException {
        return isFile(path, Constants.HEAD);
    }

    /**
     * Git 디렉토리 경로를 반환한다.
     *
     * when: Git 저장소를 참조하는 {@link Repository} 객체를 생성할 때 주로 사용한다.
     *
     * @param project
     * @return
     * @see #getGitDirectory(String, String)
     */
    public static String getGitDirectory(Project project) {
        return getGitDirectory(project.owner, project.name);
    }

    /**
     * Git 디렉토리 URL을 반환한다.
     *
     * when: 로컬 저장소에서 clone, fetch, push 커맨드를 사용할 때 저장소를 참조할 URL이 필요할 때 사용한다.
     *
     * @param project
     * @return
     * @throws IOException
     */
    public static String getGitDirectoryURL(Project project) throws IOException {
        String currentDirectory = new java.io.File( "." ).getCanonicalPath();
        return currentDirectory + "/" + getGitDirectory(project);
    }

    /**
     * Git 디렉토리 경로를 반환한다.
     *
     * when: Git 저장소를 참조하는 {@link Repository} 객체를 생성할 때 주로 사용한다.
     *
     * @param ownerName
     * @param projectName
     * @return
     */
    public static String getGitDirectory(String ownerName, String projectName) {
        return getRepoPrefix() + ownerName + "/" + projectName + ".git";
    }

    /**
     * {@code project}의 Git 저장소를 반환한다.
     * <p/>
     * when: {@link RepositoryService#gitAdvertise(models.Project, String, play.mvc.Http.Response)}와
     * {@link RepositoryService#gitRpc(models.Project, String, play.mvc.Http.Request, play.mvc.Http.Response)}에서 사용한다.
     * <p/>
     * {@link GitRepository#buildGitRepository(models.Project)}를 사용하여 Git 저장소를 참조할 객체를 생성한다.
     *
     * @param project
     * @return
     * @throws IOException
     */
    public static Repository createGitRepository(Project project) {
        return GitRepository.buildGitRepository(project);
    }


    /**
     * {@code gitUrl}의 Git 저장소를 clone 하는 {@code forkingProject}의 Git 저장소를 생성한다.
     *
     * 모든 브랜치를 복사하며 bare 모드로 생성한다.
     *
     * @param gitUrl
     * @param forkingProject
     * @throws GitAPIException
     * @throws IOException
     * * @see <a href="https://www.kernel.org/pub/software/scm/git/docs/gitglossary.html#def_bare_repository">bare repository</a>
     */
    public static void cloneRepository(String gitUrl, Project forkingProject) throws GitAPIException {
        String directory = getGitDirectory(forkingProject);
        Git.cloneRepository()
                .setURI(gitUrl)
                .setDirectory(new File(directory))
                .setCloneAllBranches(true)
                .setBare(true)
                .call();
    }

    public static void deleteMergingDirectory(PullRequest pullRequest) {
        Project toProject = pullRequest.toProject;
        String directoryForMerging = GitRepository.getDirectoryForMerging(toProject.owner, toProject.name);
        FileUtil.rm_rf(new File(directoryForMerging));
    }

    /**
     * {@code repository}에 있는 {@code src} 브랜치에 있는 코드를
     * {@code remote}의 {@code dest} 브랜치로 push 한다.
     *
     * @param repository
     * @param remote
     * @param src
     * @param dest
     * @throws GitAPIException
     */
    public static void push(Repository repository, String remote, String src, String dest) throws GitAPIException {
        new Git(repository).push()
                .setRemote(remote)
                .setRefSpecs(new RefSpec(src + ":" + dest))
                .call();
    }

    /**
     * {@code repository}에서 {@code branchName}에 해당하는 브랜치를 merge 한다.
     *
     * Fast forward 일 경우에도 머지 커밋을 기록하도록 --no-ff 옵션을 추가한다.
     *
     * @param repository
     * @param branchName
     * @return
     * @throws GitAPIException
     * @throws IOException
     */
    public static MergeResult merge(Repository repository, String branchName) throws GitAPIException, IOException {
        ObjectId branch = repository.resolve(branchName);
        return new Git(repository).merge()
                .setFastForward(MergeCommand.FastForwardMode.NO_FF)
                .include(branch)
                .call();
    }

    /**
     * {@code pullRequest}를 자동으로 merge해도 괜찮은지 확인한다.
     *
     * bare repository는 checkout 등의 명령어를 사용할 수 없기 때문에
     * 코드를 받을 프로젝트를 clone하는 non-bare repository를 생성하고,
     * 그 repository로 코드를 보내는 저장소의 브랜치와 코드를 받는 저장소의 브랜치를 체크아웃 받은다음
     * '코드를 받을 저장소의 브랜치'를 fetch 받은 브랜치에서
     * '코드를 보내는 저장소의 브랜치'를 fetch 받은 브랜치를 merge한다.
     *
     * 이때 merge한 결과 conflict가 발생하면 false를 리턴하고 그렇지 않은 경우에는 true를 반환한다.
     *
     * @param pullRequest
     * @return
     */
    public static boolean isSafeToMerge(final PullRequest pullRequest) {
        final MergeResult[] mergeResult = {null};

        cloneAndFetch(pullRequest, new AfterCloneAndFetchOperation() {
            @Override
            public void invoke(CloneAndFetch cloneAndFetch) throws IOException, GitAPIException {
                Repository clonedRepository = cloneAndFetch.getRepository();

                // 코드를 받을 브랜치(toBranch)로 이동(checkout)한다.
                checkout(clonedRepository, cloneAndFetch.getDestToBranchName());

                // 코드를 보낸 브랜치의 코드를 merge 한다.
                mergeResult[0] = merge(clonedRepository, cloneAndFetch.getDestFromBranchName());

                deleteMergingDirectory(pullRequest);
            }
        });

        // merge 결과를 반환한다.
        return mergeResult[0].getMergeStatus().isSuccessful();
    }

    /**
     * {@code repository}에서 {@code branchName}에 해당하는 브랜치로 checkout 한다.
     *
     * @param repository
     * @param branchName
     * @throws GitAPIException
     */
    public static void checkout(Repository repository, String branchName) throws GitAPIException {
        new Git(repository).checkout()
                .setName(branchName)
                .setCreateBranch(false)
                .call();
    }

    /**
     * 코드를 merge할 때 사용할 working tree 경로를 반환한다.
     *
     * @param owner
     * @param projectName
     * @return
     */
    public static String getDirectoryForMerging(String owner, String projectName) {
        return getRepoForMergingPrefix() + owner + "/" + projectName + ".git";
    }

    public static String getDirectoryForMergingObjects(String owner, String projectName) {
        return getDirectoryForMerging(owner, projectName) + "/.git/objects";
    }

    /**
     * {@code pullRequest} 보내는 브랜치에만 있고 받는 브랜치에는 없는 커밋 목록을 반환한다.
     *
     * @param pullRequest
     * @return
     */
    public static List<GitCommit> getPullingCommits(final PullRequest pullRequest) {
        final List<GitCommit> commits = new ArrayList<>();

        cloneAndFetch(pullRequest, new AfterCloneAndFetchOperation() {
            @Override
            public void invoke(CloneAndFetch cloneAndFetch) throws IOException, GitAPIException {
                List<GitCommit> commitList = diffCommits(cloneAndFetch.getRepository(),
                        cloneAndFetch.getDestFromBranchName(), cloneAndFetch.getDestToBranchName());

                for(GitCommit commit : commitList) {
                    commits.add(commit);
                }
            }
        });

        return commits;
    }

    @SuppressWarnings("unchecked")
    public static List<RevCommit> diffRevCommits(Repository repository, String fromBranch, String toBranch) throws IOException {
        RevWalk walk = null;
        try {
            walk = new RevWalk(repository);
            ObjectId from = repository.resolve(fromBranch);
            ObjectId to = repository.resolve(toBranch);

            walk.markStart(walk.parseCommit(from));
            walk.markUninteresting(walk.parseCommit(to));

            return IteratorUtils.toList(walk.iterator());
        } finally {
            if (walk != null) {
                walk.dispose();
            }
        }
    }

    public static List<GitCommit> diffCommits(Repository repository, String fromBranch, String toBranch) throws IOException {
        List<GitCommit> commits = new ArrayList<>();
        List<RevCommit> revCommits = diffRevCommits(repository, fromBranch, toBranch);
        for (RevCommit revCommit : revCommits) {
            commits.add(new GitCommit(revCommit));
        }
        return commits;
    }

    /**
     * {@code pullRequest} 에 의해서 변경되는 코드의 원작자들을 얻는다.
     * 원작자는 변경된 line 을 마지막으로 수정한 사람의 email 을 이용해서
     * yobi 사용자를 찾은 결과이다.
     *
     * @param pullRequest
     * @return
     * @see playRepository.GitCommit#getAuthor()
     */
    public static Set<User> getRelatedAuthors(PullRequest pullRequest) {
        final Set<User> authors = new HashSet<>();
        cloneAndFetch(pullRequest, new AfterCloneAndFetchOperation() {
            @Override
            public void invoke(CloneAndFetch cloneAndFetch) throws IOException,
                    GitAPIException {
                Repository repository = cloneAndFetch.getRepository();
                List<RevCommit> commits = diffRevCommits(repository,
                        cloneAndFetch.destFromBranchName,
                        cloneAndFetch.destToBranchName);
                for (RevCommit revCommit: commits) {
                    findAuthors(revCommit, repository);
                }
            }

            /*
             * 하나의 commit 과 그것의 parent commit 들을 비교해서 변경되는 부분을 구하고
             * 변경된 부분의 이전 author 들을 찾는다.
             */
            private void findAuthors(RevCommit commit, Repository repository)
                    throws IOException, GitAPIException {
                RevCommit[] parents = commit.getParents();
                for (RevCommit parent : parents) {
                    TreeWalk treeWalk = new TreeWalk(repository);
                    treeWalk.setRecursive(true);
                    treeWalk.addTree(parent.getTree());
                    treeWalk.addTree(commit.getTree());
                    List<DiffEntry> diffs = DiffEntry.scan(treeWalk);
                    for (DiffEntry diff : diffs) {
                        DiffFormatter diffFormatter = new DiffFormatter(NullOutputStream.INSTANCE);
                        diffFormatter.setRepository(repository);
                        FileHeader fileHeader = diffFormatter.toFileHeader(diff);
                        EditList edits = fileHeader.toEditList();
                        BlameResult blameResult = new Git(repository).blame()
                                .setFilePath(diff.getOldPath())
                                .setFollowFileRenames(true)
                                .setStartCommit(parent).call();
                        for (Edit edit : edits) {
                            if (edit.getType() != Type.INSERT && edit.getType() != Type.EMPTY) {
                                for (int i = edit.getBeginA(); i < edit.getEndA(); i++) {
                                    PersonIdent personIdent = blameResult.getSourceAuthor(i);
                                    if (personIdent != null) {
                                        authors.add(User.findByEmail(personIdent.getEmailAddress()));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        });
        authors.remove(User.anonymous);
        return authors;
    }

    /**
     * {@code repository}에 있는 {@code branchName}에 해당하는 브랜치를 삭제한다.
     *
     * @param repository
     * @param branchName
     * @throws GitAPIException
     */
    public static void deleteBranch(Repository repository, String branchName) throws GitAPIException {
        new Git(repository).branchDelete()
                .setBranchNames(branchName)
                .setForce(true)
                .call();
    }

    /**
     * {@link Project}의 Git 저장소의 {@code fromBranch}에 있는 내용을
     * 현재 사용중인 Git 저장소의 {@code toBranch}로 fetch 한다.
     *
     * @param repository fetch 실행할 Git 저장소
     * @param project fetch 대상 프로젝트
     * @param fromBranch fetch source 브랜치
     * @param toBranch fetch destination 브랜치
     * @throws GitAPIException
     * @throws IOException
     * @see <a href="https://www.kernel.org/pub/software/scm/git/docs/git-fetch.html">git-fetch</a>
     */
    public static void fetch(Repository repository, Project project, String fromBranch, String toBranch) throws GitAPIException, IOException {
        new Git(repository).fetch()
                .setRemote(GitRepository.getGitDirectoryURL(project))
                .setRefSpecs(new RefSpec(fromBranch + ":" + toBranch))
                .call();
    }

    /**
     * 풀리퀘스트 기능 구현에 필요한 기본 작업을 수행하는 템플릿 메서드
     *
     * when: {@link #isSafeToMerge(models.PullRequest)}, {@link #merge(models.PullRequest)}
     * , {@link #getPullingCommits(models.PullRequest)} 등 {@code pullRequest}의 toProject에 해당하는
     * 저장소를 Clone 하고 fromBranch와 toBranch를 Fetch 한 다음 {@code operation}을 호출하여
     * 이후 작업을 진행한다.
     *
     * @param pullRequest
     * @param operation
     */
    public static synchronized void cloneAndFetch(PullRequest pullRequest, AfterCloneAndFetchOperation operation) {
        Repository cloneRepository = null;
        try {
            cloneRepository = buildMergingRepository(pullRequest);

            String srcToBranchName = pullRequest.toBranch;
            String destToBranchName = srcToBranchName + "-to-" + pullRequest.id;
            String srcFromBranchName = pullRequest.fromBranch;
            String destFromBranchName = srcFromBranchName + "-from-" + pullRequest.id;

            new Git(cloneRepository).reset().setMode(ResetCommand.ResetType.HARD).setRef(Constants.HEAD).call();
            new Git(cloneRepository).clean().setIgnore(true).setCleanDirectories(true).call();
            checkout(cloneRepository, pullRequest.toProject.defaultBranch());

            // 코드를 받아오면서 생성될 브랜치를 미리 삭제한다.
            deleteBranch(cloneRepository, destToBranchName);
            deleteBranch(cloneRepository, destFromBranchName);

            // 코드를 받을 브랜치에 해당하는 코드를 fetch 한다.
            fetch(cloneRepository, pullRequest.toProject, srcToBranchName, destToBranchName);
            // 코드를 보내는 브랜치에 해당하는 코드를 fetch 한다.
            fetch(cloneRepository, pullRequest.fromProject, srcFromBranchName, destFromBranchName);

            CloneAndFetch cloneAndFetch = new CloneAndFetch(cloneRepository, destToBranchName, destFromBranchName);
            operation.invoke(cloneAndFetch);

            // master로 이동
            new Git(cloneRepository).reset().setMode(ResetCommand.ResetType.HARD).setRef(Constants.HEAD).call();
            new Git(cloneRepository).clean().setIgnore(true).setCleanDirectories(true).call();
            checkout(cloneRepository, pullRequest.toProject.defaultBranch());
        } catch (GitAPIException e) {
            throw new IllegalStateException(e);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            if(cloneRepository != null) {
                cloneRepository.close();
            }
        }
    }

    /**
     * {@code pullRequest}의 toProject를 clone 받은 Git 저장소를 참조할 {@link Repository}를 객체를 생성한다.
     *
     * @param pullRequest
     * @return
     * @throws GitAPIException
     * @throws IOException
     */
    public static Repository buildMergingRepository(PullRequest pullRequest) {
        Project toProject = pullRequest.toProject;

        // merge 할 때 사용할 Git 저장소 디렉토리 경로를 생성한다.
        String workingTree = GitRepository.getDirectoryForMerging(toProject.owner, toProject.name);

        try {
            // 이미 만들어둔 clone 디렉토리가 있다면 그걸 사용해서 Repository를 생성하고
            // 없을 때는 새로 만든다.
            File gitDir = new File(workingTree + "/.git");
            if(!gitDir.exists()) {
                return cloneRepository(pullRequest.toProject, workingTree).getRepository();
            } else {
                return new RepositoryBuilder().setGitDir(gitDir).build();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * {@link Project}의 Git 저장소를 {@code workingTreePath}에
     * non-bare 모드로 clone 한다.
     *
     * @param project clone 받을 프로젝트
     * @param workingTreePath clone 프로젝트를 생성할 워킹트리 경로
     * @throws GitAPIException
     * @throws IOException
     */
    private static Git cloneRepository(Project project, String workingTreePath) throws GitAPIException, IOException {
        return Git.cloneRepository()
                .setURI(GitRepository.getGitDirectoryURL(project))
                .setDirectory(new File(workingTreePath))
                .call();
    }

    /**
     * {@code pullRequest}의 fromBranch를 삭제할 수 있는지 확인한다.
     *
     * 삭제하려는 fromBranch가 브랜치 목록에 있는지 확인하고,
     * 브랜치 목록에 있을 때 fromBranch의 HEAD가 toProject에 있는지 확인한다.
     *
     * 브랜치 목록에 있으면서 toProject에 fromBranch의 HEAD가 있을 경우에만 fromBranch를 안전하게 삭제할 수 있다.
     *
     * 브랜치 목록에 없을 때는 이미 삭제 됐거나 현재 위치한 브랜치(master)에 있을 수 있어서 fromBranch를 삭제할 수 없거나,
     * fromBranch에 toProject로 보내지 않은 새로운 커밋이 있어서 fromBranch를 삭제할 수 없다.
     *
     * @param pullRequest
     * @return
     */
    public static boolean canDeleteFromBranch(PullRequest pullRequest) {
        List<Ref> refs;
        Repository fromRepo = null; // repository that sent the pull request
        String currentBranch;
        try {
            fromRepo = buildGitRepository(pullRequest.fromProject);
            currentBranch = fromRepo.getFullBranch();
            refs = new Git(fromRepo).branchList().call();

            for(Ref branchRef : refs) {
                String branchName = branchRef.getName();
                if(branchName.equals(pullRequest.fromBranch) && !branchName.equals(currentBranch)) {
                    RevWalk revWalk = new RevWalk(fromRepo);
                    RevCommit commit = revWalk.parseCommit(fromRepo.resolve(branchName));
                    String commitName = commit.name(); // fromBranch's head commit name
                    revWalk.release();

                    // check whether the target repository has the commit witch is the fromBranch's head commit.
                    Repository toRepo = buildGitRepository(pullRequest.toProject);
                    ObjectId toBranch = toRepo.resolve(commitName);
                    if(toBranch != null) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if(fromRepo != null) {
                fromRepo.close();
            }
        }

        return false;
    }

    /**
     * {@code pullRequest}의 fromBranch를 삭제한다.
     *
     * @param pullRequest
     * @return {@code fromBranch}의 HEAD를 반환한다.
     * @see PullRequest#lastCommitId;
     */
    public static String deleteFromBranch(PullRequest pullRequest) {
        if(!canDeleteFromBranch(pullRequest)) {
            return null;
        }

        RevWalk revWalk = null;
        String lastCommitId;
        Repository repo = null;
        try {
            repo = buildGitRepository(pullRequest.fromProject);
            ObjectId branch = repo.resolve(pullRequest.fromBranch);
            revWalk = new RevWalk(repo);
            RevCommit commit = revWalk.parseCommit(branch);
            lastCommitId = commit.getName();
            deleteBranch(repo, pullRequest.fromBranch);
            return lastCommitId;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if(revWalk != null) {
                revWalk.release();
            }
            if(repo != null) {
                repo.close();
            }
        }
    }

    /**
     * {@code pullRequest}의 fromBranch를 복구한다.
     *
     * @param pullRequest
     */
    public static void restoreBranch(PullRequest pullRequest) {
        if(!canRestoreBranch(pullRequest)) {
            return;
        }

        Repository repo = null;
        try {
            repo = buildGitRepository(pullRequest.fromProject);
            new Git(repo).branchCreate()
                    .setName(pullRequest.fromBranch.replaceAll("refs/heads/", ""))
                    .setStartPoint(pullRequest.lastCommitId)
                    .call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if(repo != null) {
                repo.close();
            }
        }
    }

    /**
     * {@code pullRequest}의 fromBranch를 복구할 수 있는지 확인한다.
     *
     * when: 완료된 PullRequest 조회 화면에서 브랜치를 삭제 했을 때 해당 브랜치를 복구할 수 있는지 확인한다.
     *
     * {@link PullRequest#lastCommitId}가 저장되어 있어야 하며, fromBranch가 없어야 복구할 수 있다.
     *
     * @param pullRequest
     * @return
     */
    public static boolean canRestoreBranch(PullRequest pullRequest) {
        Repository repo = null;
        try {
            repo = buildGitRepository(pullRequest.fromProject);
            ObjectId resolve = repo.resolve(pullRequest.fromBranch);
            if(resolve == null && pullRequest.lastCommitId != null) {
                return true;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if(repo != null) {
                repo.close();
            }
        }

        return false;
    }

    public static List<GitCommit> diffCommits(PullRequest pullRequest) {
        List<GitCommit> commits = new ArrayList<>();
        if(pullRequest.mergedCommitIdFrom == null || pullRequest.mergedCommitIdTo == null) {
            return commits;
        }

        Repository repo;
        try {
            if(pullRequest.isClosed()) {
                repo = buildGitRepository(pullRequest.toProject);
            } else {
                repo = buildGitRepository(pullRequest.fromProject);
            }

            ObjectId untilId = repo.resolve(pullRequest.mergedCommitIdTo);
            if(untilId == null) {
                return commits;
            }
            ObjectId sinceId = repo.resolve(pullRequest.mergedCommitIdFrom);
            if(sinceId == null) {
                return commits;
            }

            Iterable<RevCommit> logIterator = new Git(repo).log().addRange(sinceId, untilId).call();
            for(RevCommit commit : logIterator) {
                commits.add(new GitCommit(commit));
            }

            return commits;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String getPatch(Repository repository, String fromBranch, String toBranch) {
        TreeWalk treeWalk = new TreeWalk(repository);
        RevWalk walk = new RevWalk(repository);
        try {
            ObjectId from = repository.resolve(fromBranch);
            ObjectId to = repository.resolve(toBranch);
            RevTree fromTree = walk.parseTree(from);
            RevTree toTree = walk.parseTree(to);

            treeWalk.addTree(toTree);
            treeWalk.addTree(fromTree);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DiffFormatter diffFormatter = new DiffFormatter(out);
            diffFormatter.setRepository(repository);
            treeWalk.setRecursive(true);
            diffFormatter.format(DiffEntry.scan(treeWalk));

            return out.toString("UTF-8");
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            walk.dispose();
        }
    }

    public static String getPatch(PullRequest pullRequest) {
        if(pullRequest.mergedCommitIdFrom == null || pullRequest.mergedCommitIdTo == null) {
            return "";
        }

        Repository repo;
        try {
            repo = buildGitRepository(pullRequest.toProject);

            ObjectId untilId = repo.resolve(pullRequest.mergedCommitIdTo);
            if(untilId == null) {
                return "";
            }
            ObjectId sinceId = repo.resolve(pullRequest.mergedCommitIdFrom);
            if(sinceId == null) {
                return "";
            }

            return getPatch(repo, untilId.getName(), sinceId.getName());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static List<FileDiff> getDiff(final Repository repository, String revA, String revB) throws IOException {
        return getDiff(repository, revA, repository, revB);
    }

    public List<FileDiff> getDiff(String revA, String revB) throws IOException {
        return getDiff(this.repository, revA, revB);
    }

    public static List<FileDiff> getDiff(final Repository repositoryA, String revA, Repository repositoryB, String revB) throws IOException {
        ObjectId commitA = repositoryA.resolve(revA);
        ObjectId commitB = repositoryB.resolve(revB);

        return getFileDiffs(repositoryA, repositoryB, commitA, commitB);
    }

    private static List<FileDiff> getFileDiffs(final Repository repositoryA, Repository repositoryB, ObjectId commitA, ObjectId commitB) throws IOException {
        class MultipleRepositoryObjectReader extends ObjectReader {
            Collection<ObjectReader> readers = new HashSet<>();

            @Override
            public ObjectReader newReader() {
                return new MultipleRepositoryObjectReader(readers);
            }

            public MultipleRepositoryObjectReader(Collection<ObjectReader> readers) {
                this.readers = readers;
            }

            public MultipleRepositoryObjectReader() {
                this.readers = new HashSet<>();
            }

            public void addObjectReader(ObjectReader reader) {
                this.readers.add(reader);
            }

            @Override
            public Collection<ObjectId> resolve(AbbreviatedObjectId id) throws IOException {
                Set<ObjectId> result = new HashSet<>();
                for (ObjectReader reader : readers) {
                    result.addAll(reader.resolve(id));
                }
                return result;
            }

            @Override
            public ObjectLoader open(AnyObjectId objectId, int typeHint) throws IOException {
                for (ObjectReader reader : readers) {
                    if (reader.has(objectId, typeHint)) {
                        return reader.open(objectId, typeHint);
                    }
                }
                return null;
            }

            @Override
            public Set<ObjectId> getShallowCommits() throws IOException {
                Set<ObjectId> union = new HashSet<>();
                for (ObjectReader reader : readers) {
                    union.addAll(reader.getShallowCommits());
                }
                return union;
            }
        }

        final MultipleRepositoryObjectReader reader = new MultipleRepositoryObjectReader();
        reader.addObjectReader(repositoryA.newObjectReader());
        reader.addObjectReader(repositoryB.newObjectReader());

        @SuppressWarnings("rawtypes")
        Repository fakeRepo = new Repository(new BaseRepositoryBuilder()) {

            @Override
            public void create(boolean bare) throws IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public ObjectDatabase getObjectDatabase() {
                throw new UnsupportedOperationException();
            }

            @Override
            public RefDatabase getRefDatabase() {
                throw new UnsupportedOperationException();
            }

            @Override
            public StoredConfig getConfig() {
                return repositoryA.getConfig();
            }

            @Override
            public void scanForRepoChanges() throws IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public void notifyIndexChanged() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ReflogReader getReflogReader(String refName) throws IOException {
                throw new UnsupportedOperationException();
            }

            public ObjectReader newObjectReader() {
                return reader;
            }
        };

        // formatter로 scan해야 rename detection 가능할 듯
        DiffFormatter formatter = new DiffFormatter(NullOutputStream.INSTANCE);
        formatter.setRepository(fakeRepo);
        formatter.setDetectRenames(true);

        AbstractTreeIterator treeParserA, treeParserB;
        RevTree treeA = null, treeB = null;

        if (commitA != null) {
            treeA = new RevWalk(repositoryA).parseTree(commitA);
            treeParserA = new CanonicalTreeParser();
            ((CanonicalTreeParser) treeParserA).reset(reader, treeA);
        } else {
            treeParserA = new EmptyTreeIterator();
        }

        if (commitB != null) {
            treeB = new RevWalk(repositoryB).parseTree(commitB);
            treeParserB = new CanonicalTreeParser();
            ((CanonicalTreeParser) treeParserB).reset(reader, treeB);
        } else {
            treeParserB = new EmptyTreeIterator();
        }

        List<FileDiff> result = new ArrayList<>();

        for (DiffEntry diff : formatter.scan(treeParserA, treeParserB)) {
            FileDiff fileDiff = new FileDiff();
            fileDiff.commitA = commitA != null ? commitA.getName() : null;
            fileDiff.commitB = commitB != null ? commitB.getName() : null;

            fileDiff.changeType = diff.getChangeType();

            fileDiff.oldMode = diff.getOldMode();
            fileDiff.newMode = diff.getNewMode();

            String pathA = diff.getPath(DiffEntry.Side.OLD);
            String pathB = diff.getPath(DiffEntry.Side.NEW);

            if (treeA != null
                    && Arrays.asList(DELETE, MODIFY, RENAME).contains(diff.getChangeType())) {
                TreeWalk t1 = TreeWalk.forPath(repositoryA, pathA, treeA);
                ObjectId blobA = t1.getObjectId(0);
                byte[] rawA = repositoryA.open(blobA).getBytes();

                fileDiff.isBinaryA = RawText.isBinary(rawA);
                fileDiff.a = fileDiff.isBinaryA ? null : new RawText(rawA);
                fileDiff.pathA = pathA;
            }

            if (treeB != null
                    && Arrays.asList(ADD, MODIFY, RENAME).contains(diff.getChangeType())) {
                TreeWalk t2 = TreeWalk.forPath(repositoryB, pathB, treeB);
                ObjectId blobB = t2.getObjectId(0);
                byte[] rawB = repositoryB.open(blobB).getBytes();

                fileDiff.isBinaryB = RawText.isBinary(rawB);
                fileDiff.b = fileDiff.isBinaryB ? null : new RawText(rawB);
                fileDiff.pathB = pathB;
            }

            if (!(fileDiff.isBinaryA || fileDiff.isBinaryB) && Arrays.asList(MODIFY, RENAME).contains(diff.getChangeType())) {
                DiffAlgorithm diffAlgorithm = DiffAlgorithm.getAlgorithm(
                        repositoryB.getConfig().getEnum(
                                ConfigConstants.CONFIG_DIFF_SECTION, null,
                                ConfigConstants.CONFIG_KEY_ALGORITHM,
                                DiffAlgorithm.SupportedAlgorithm.HISTOGRAM));
                fileDiff.editList = diffAlgorithm.diff(RawTextComparator.DEFAULT, fileDiff.a,
                        fileDiff.b);
            }

            result.add(fileDiff);
        }

        return result;
    }

    /**
     * 로컬에 있는 저장소를 복제한다. 디스크 공간을 절약하기 위해, Git object들은 복사하지 않고 대신 하드링크를
     * 건다.
     *
     * @param originalProject
     * @param forkProject
     * @throws IOException
     */
    protected static void cloneHardLinkedRepository(Project originalProject,
                                                    Project forkProject) throws IOException {
        Repository origin = GitRepository.buildGitRepository(originalProject);
        Repository forked = GitRepository.buildGitRepository(forkProject);
        forked.create();

        final Path originObjectsPath =
                Paths.get(new File(origin.getDirectory(), "objects").getAbsolutePath());
        final Path forkedObjectsPath =
                Paths.get(new File(forked.getDirectory(), "objects").getAbsolutePath());

        // Hardlink files .git/objects/ directory to save disk space,
        // but copy .git/info/alternates because the file can be modified.
        SimpleFileVisitor<Path> visitor =
                new SimpleFileVisitor<Path>() {
                    public FileVisitResult visitFile(Path file,
                                                     BasicFileAttributes attr) throws IOException {
                        Path newPath = forkedObjectsPath.resolve(
                                originObjectsPath.relativize(file.toAbsolutePath()));
                        if (file.equals(forkedObjectsPath.resolve("/info/alternates"))) {
                            Files.copy(file, newPath);
                        } else {
                            FileUtils.mkdirs(newPath.getParent().toFile(), true);
                            Files.createLink(newPath, file);
                        }
                        return java.nio.file.FileVisitResult.CONTINUE;
                    }
                };
        Files.walkFileTree(originObjectsPath, visitor);

        // Import refs.
        for (Map.Entry<String, Ref> entry : origin.getAllRefs().entrySet()) {
            RefUpdate updateRef = forked.updateRef(entry.getKey());
            Ref ref = entry.getValue();
            if (ref.isSymbolic()) {
                updateRef.link(ref.getTarget().getName());
            } else {
                updateRef.setNewObjectId(ref.getObjectId());
                updateRef.update();
            }
        }
    }

    public GitBranch getHeadBranch() {
        try {
            String headBranchName = getDefaultBranch();

            ObjectId branchObjectId = repository.resolve(headBranchName);
            RevWalk walk = new RevWalk(repository);
            RevCommit head = walk.parseCommit(branchObjectId);
            walk.dispose();

            return new GitBranch(headBranchName, new GitCommit(head));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Clone과 Fetch 이후 작업에 필요한 정보를 담을 객체로 사용한다.
     */
    public static class CloneAndFetch {

        /**
         * 코드 받을 저장소의 코드를 non-bare 모드로 clone 받은 Git 저장소
         */
        private Repository repository;

        /**
         * 코드를 받을 브랜치의 코드를 fetch 받은 브랜치 이름
         */
        private String destToBranchName;

        /**
         * 코드를 보내는 브랜치의 코드를 fetch 받은 브랜치 이름
         */
        private String destFromBranchName;

        public Repository getRepository() {
            return repository;
        }

        public String getDestToBranchName() {
            return destToBranchName;
        }

        public String getDestFromBranchName() {
            return destFromBranchName;
        }

        private CloneAndFetch(Repository repository, String destToBranchName, String destFromBranchName) {
            this.repository = repository;
            this.destToBranchName = destToBranchName;
            this.destFromBranchName = destFromBranchName;
        }
    }

    /**
     * Clone과 Fetch 이후에 진행할 작업을 정의한다.
     *
     * @see #cloneAndFetch(models.PullRequest, playRepository.GitRepository.AfterCloneAndFetchOperation)
     */
    public static interface AfterCloneAndFetchOperation {
        public void invoke(CloneAndFetch cloneAndFetch) throws IOException, GitAPIException;
    }

    public void close() {
        repository.close();
    }

    /**
     * 코드저장소 프로젝트명을 변경하고 결과를 반환한다.
     *
     * 변경전 {@code repository.close()}를 통해 open된 repository의 리소스를 반환하고
     * repository 내부에서 사용하는 {@code WindowCache}를 초기화하여 packFile의 참조를 제거한다.
     *
     * @param projectName
     * @return 코드저장소 이름 변경성공시 true / 실패시 false
     * @see playRepository.PlayRepository#renameTo(String)
     */
    @Override
    public boolean renameTo(String projectName) {

        repository.close();
        WindowCacheConfig config = new WindowCacheConfig();
        config.install();
        File src = new File(getGitDirectory(this.ownerName, this.projectName));
        File dest = new File(getGitDirectory(this.ownerName, projectName));

        src.setWritable(true);

        return src.renameTo(dest);
    }

    @Override
    public String getDefaultBranch() throws IOException {
        return repository.getRef(Constants.HEAD).getTarget().getName();
    }

    @Override
    public void setDefaultBranch(String target) throws IOException {
        Result result = repository.updateRef(Constants.HEAD).link(target);
        switch (result) {
        case NEW:
        case FORCED:
        case NO_CHANGE:
            break;
        default:
            throw new IOException("Failed to update symbolic ref, got: " + result);
        }
    }

    /**
     * {@code #commitIdString}에 해당하는 커밋의 부모 커밋 정보를 반환하다.
     *
     * @param commitIdString
     * @return
     */
    @Override
    public Commit getParentCommitOf(String commitIdString) {
        try {
            ObjectId commitId = repository.resolve(commitIdString);
            RevWalk revWalk = new RevWalk(repository);
            RevCommit commit = revWalk.parseCommit(commitId);
            if(commit.getParentCount() > 0) {
                ObjectId parentId = commit.getParent(0).getId();
                return new GitCommit(revWalk.parseCommit(parentId));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return null;
    }

    @Override
    public boolean isEmpty() {
        return this.getBranches().isEmpty();
    }

    /*
     * 주어진 git 객체 참조 값에 해당하는 것을 가져온다
     */
    private ObjectId getObjectId(String revstr) throws IOException {
        if (revstr == null) {
            return repository.resolve(Constants.HEAD);
        } else {
            return repository.resolve(revstr);
        }
    }

    /*
     * 주어진 git 객체 참조 값을 이용해서 commit 객체를 가져온다
     */
    private RevCommit getRevCommit(String revstr) throws IOException {
        ObjectId objectId = getObjectId(revstr);
        if (objectId == null) {
            return null;
        }
        RevWalk revWalk = new RevWalk(repository);
        return revWalk.parseCommit(objectId);
    }
}
