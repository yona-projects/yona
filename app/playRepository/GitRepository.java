package playRepository;

import controllers.ProjectApp;
import controllers.UserApp;
import models.Project;
import models.PullRequest;
import models.User;
import models.enumeration.ResourceType;
import models.enumeration.State;
import models.resource.Resource;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.codehaus.jackson.node.ObjectNode;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.WindowCache;
import org.eclipse.jgit.storage.file.WindowCacheConfig;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.tmatesoft.svn.core.SVNException;
import play.Logger;
import play.libs.Json;
import utils.FileUtil;
import utils.GravatarUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Git 저장소
 */
public class GitRepository implements PlayRepository {

    private static final long ALLOWED_MAX_FILESIZE = 1024 * 1024;
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
    public GitRepository(String ownerName, String projectName) throws IOException {
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
    public static Repository buildGitRepository(String ownerName, String projectName) throws IOException {
        return new RepositoryBuilder().setGitDir(
                new File(getGitDirectory(ownerName, projectName))).build();
    }

    /**
     * {@code project}의 {@link Project#owner}와 {@link Project#name}을 사용하여 {@link Repository} 객체를 생성한다.
     *
     * @param project
     * @return
     * @throws IOException
     * @see #buildGitRepository(String, String)
     */
    public static Repository buildGitRepository(Project project) throws IOException {
        return buildGitRepository(project.owner, project.name);
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
     * @see #findFileInfo(String, String)
     */
    @Override
    public ObjectNode findFileInfo(String path) throws IOException, NoHeadException, GitAPIException, SVNException {
        return findFileInfo(null, path);
    }

    public boolean isFile(String path, String revStr) throws IOException {
        ObjectId commitId;

        if (revStr == null){
            commitId = repository.resolve(Constants.HEAD);
        } else {
            commitId = repository.resolve(revStr);
        }

        if (commitId == null) {
            return false;
        }

        RevWalk revWalk = new RevWalk(repository);
        RevTree revTree = revWalk.parseTree(commitId);
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
    public ObjectNode findFileInfo(String branch, String path) throws IOException, GitAPIException {
        ObjectId headCommit;

        if (branch == null){
            headCommit = repository.resolve(Constants.HEAD);
        } else {
            headCommit = repository.resolve(branch);
        }
        // 만약 특정 커밋을 얻오오고싶다면 바꾸어 주면 된다.
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

        RevCommit commit = git.log()
            .add(untilCommitId)
            .addPath(treeWalk.getPathString())
            .call()
            .iterator()
            .next();

        ObjectNode result = Json.newObject();
        long commitTime = commit.getCommitTime() * 1000L;
        result.put("type", "file");
        result.put("msg", commit.getShortMessage());
        result.put("author", commit.getAuthorIdent().getName());
        setAvatar(result, commit.getAuthorIdent().getEmailAddress());
        result.put("createdDate", commitTime);
        result.put("commitMessage", commit.getShortMessage());
        result.put("commiter", commit.getAuthorIdent().getName());
        result.put("commitDate", commitTime);
        ObjectLoader file = repository.open(treeWalk.getObjectId(0));
        result.put("size", file.getSize());

        boolean isBinary = FileUtil.isBinary(file.openStream());
        result.put("isBinary", isBinary);
        if (!isBinary && file.getSize() < MAX_FILE_SIZE_CAN_BE_VIEWED) {
            String str = new String(file.getBytes(), "UTF-8");
            result.put("data", str);
        }
        Metadata meta = new Metadata();
        meta.add(Metadata.RESOURCE_NAME_KEY, treeWalk.getNameString());
        result.put("mimeType", new Tika().detect(file.openStream(), meta));

        return result;
    }

    private void setAvatar(ObjectNode objectNode, String emailAddress) {
        User user = User.findByEmail(emailAddress);
        if(user.isAnonymous() || user.avatarUrl.equals(UserApp.DEFAULT_AVATAR_URL)) {
            String defaultImageUrl = "http://ko.gravatar.com/userimage/53495145/0eaeeb47c620542ad089f17377298af6.png";
            objectNode.put("avatar", GravatarUtil.getAvatar(emailAddress, 34, defaultImageUrl));
        } else {
            objectNode.put("avatar", user.avatarUrl);
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
        Git git = new Git(repository);
        ObjectNode result = Json.newObject();
        result.put("type", "folder");

        ObjectNode listData = Json.newObject();

        LogCommand logCommand = git.log().add(untilCommitId);

        if (!basePath.isEmpty()) {
            logCommand.addPath(basePath);
        }

        Set<String> paths = new HashSet<>();
        Map<String, String> modes = new HashMap<>();

        while (treeWalk.next()) {
            paths.add(treeWalk.getNameString());
            modes.put(treeWalk.getNameString(), treeWalk.isSubtree() ? "folder" : "file");
        }

        Iterator<RevCommit> iterator = logCommand.call().iterator();

        while (iterator.hasNext()) {
            RevCommit curr = iterator.next();

            TreeWalk tw;
            if (basePath.isEmpty()) {
                tw = new TreeWalk(repository);
                tw.addTree(curr.getTree());
            } else {
                tw = TreeWalk.forPath(repository, basePath, curr.getTree());
                tw.enterSubtree();
            }
            Map<String, ObjectId> objects = new HashMap<>();
            while(tw.next()) {
                if (paths.contains(tw.getNameString())) {
                    objects.put(tw.getNameString(), tw.getObjectId(0));
                }
            }

            // Remove objects not changed.
            for(RevCommit parent : curr.getParents()) {
                TreeWalk tw2;
                if (basePath.isEmpty()) {
                    tw2 = new TreeWalk(repository);
                    tw2.addTree(parent.getTree());
                } else {
                    tw2 = TreeWalk.forPath(repository, basePath, parent.getTree());
                    tw2.enterSubtree();
                }

                while(tw2.next()) {
                    if (tw2.getObjectId(0).equals(objects.get(tw2.getNameString()))) {
                        objects.remove(tw2.getNameString());
                    }
                }
            }

            for (String path : objects.keySet()) {
                ObjectNode data = Json.newObject();
                data.put("type", modes.get(path));
                data.put("msg", curr.getShortMessage());
                setAvatar(data, curr.getAuthorIdent().getEmailAddress());
                data.put("createdDate", curr.getCommitTime() * 1000l);
                data.put("author", curr.getAuthorIdent().getName());
                listData.put(path, data);
                paths.remove(path);
            }

            if (paths.isEmpty()) {
                break;
            }
        }

        result.put("data", listData);
        return result;
    }

    /* (non-Javadoc)
     *
     */

    /**
     * {@link Constants#HEAD}에서 {@code path}에 해당하는 파일을 반환한다.
     *
     * @param path
     * @return {@code path}가 디렉토리일 경우에는 null, 아닐때는 해당 파일
     * @throws IOException
     */
    @Override
    public byte[] getRawFile(String path) throws IOException {
        RevTree tree = new RevWalk(repository).parseTree(repository.resolve(Constants.HEAD));
        TreeWalk treeWalk = TreeWalk.forPath(repository, path, tree);
        if (treeWalk.isSubtree()) {
            return null;
        } else {
            return repository.open(treeWalk.getObjectId(0)).getBytes();
        }
    }

    /**
     * Git 저장소 디렉토리를 삭제한다.
     */
    @Override
    public void delete() {
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
    public String getPatch(String rev) throws GitAPIException, IOException {
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
    public List<Commit> getHistory(int pageNumber, int pageSize, String untilRevName)
            throws IOException, GitAPIException {
        // Get the list of commits from HEAD to the given pageNumber.
        LogCommand logCommand = new Git(repository).log();
        if (untilRevName != null) {
            ObjectId objectId = repository.resolve(untilRevName);
            if (objectId == null) {
                return null;
            }
            logCommand.add(objectId);
        }
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
    public static Repository createGitRepository(Project project) throws IOException {
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
    public static void cloneRepository(String gitUrl, Project forkingProject) throws GitAPIException, IOException {
        String directory = getGitDirectory(forkingProject);
        Git.cloneRepository()
                .setURI(gitUrl)
                .setDirectory(new File(directory))
                .setCloneAllBranches(true)
                .setBare(true)
                .call();
    }

    /**
     * {@code pullRequest}를 merge 한다.
     *
     * 자동으로 merge해도 안전한지 확인한 다음 admin 계정으로 코드를 받을 저장소로 자동으로 병합한 코드를 push 한다.
     *
     * @param pullRequest
     */
    public static void merge(final PullRequest pullRequest) {
        cloneAndFetch(pullRequest, new AfterCloneAndFetchOperation() {
            @Override
            public void invoke(CloneAndFetch cloneAndFetch) throws IOException, GitAPIException {
                Repository cloneRepository = cloneAndFetch.getRepository();
                String srcToBranchName = pullRequest.toBranch;
                String destToBranchName = cloneAndFetch.destToBranchName;

                // 코드를 받을 브랜치(toBranch)로 이동(checkout)한다.
                checkout(cloneRepository, cloneAndFetch.getDestToBranchName());

                // 코드를 보낸 브랜치(fromBranch)의 코드를 merge 한다.
                MergeResult mergeResult = merge(cloneRepository, cloneAndFetch.getDestFromBranchName());

                if (mergeResult.getMergeStatus().isSuccessful()) {
                    // merge 커밋 메시지 수정
                    amend(cloneRepository, UserApp.currentUser(), pullRequest);

                    // 코드 받을 프로젝트의 코드 받을 브랜치(srcToBranchName)로 clone한 프로젝트의
                    // merge 한 브랜치(destToBranchName)의 코드를 push 한다.
                    push(cloneRepository, getGitDirectoryURL(pullRequest.toProject), destToBranchName, srcToBranchName);

                    // 풀리퀘스트 완료
                    pullRequest.state = State.CLOSED;
                }
            }
        });
    }

    private static void amend(Repository cloneRepository, User user, PullRequest pullRequest) throws GitAPIException {
        Project fromProject = pullRequest.fromProject;
        new Git(cloneRepository).commit()
                .setAmend(true).setAuthor(user.name, user.email)
                .setMessage("Merge pull request #" + pullRequest.id +
                        " from " + fromProject.owner + "/" + fromProject.name + " " + pullRequest.fromBranch)
                .setCommitter(pullRequest.contributor.name, pullRequest.contributor.email)
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

                deleteMergingDirectory(pullRequest);
            }
        });

        return commits;
    }

    public static List<GitCommit> diffCommits(Repository repository, String fromBranch, String toBranch) throws IOException {
        List<GitCommit> commits = new ArrayList<>();

        RevWalk walk = null;
        try {
            walk = new RevWalk(repository);
            ObjectId from = repository.resolve(fromBranch);
            ObjectId to = repository.resolve(toBranch);

            walk.markStart(walk.parseCommit(from));
            walk.markUninteresting(walk.parseCommit(to));

            Iterator<RevCommit> iterator = walk.iterator();
            while (iterator.hasNext()) {
                RevCommit commit = iterator.next();
                commits.add(new GitCommit(commit));
            }

            return commits;
        } finally {
            if(walk != null) {
                walk.release();
            }
        }
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
    public static void cloneAndFetch(PullRequest pullRequest, AfterCloneAndFetchOperation operation) {
        Repository cloneRepository = null;
        try {
            cloneRepository = buildCloneRepository(pullRequest);

            String srcToBranchName = pullRequest.toBranch;
            String destToBranchName = srcToBranchName + "-to-" + pullRequest.id;
            String srcFromBranchName = pullRequest.fromBranch;
            String destFromBranchName = srcFromBranchName + "-from-" + pullRequest.id;

            new Git(cloneRepository).reset().setMode(ResetCommand.ResetType.HARD).setRef(Constants.HEAD).call();
            checkout(cloneRepository, Constants.MASTER);

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
            checkout(cloneRepository, Constants.MASTER);
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
    public static Repository buildCloneRepository(PullRequest pullRequest) throws GitAPIException, IOException {
        Project toProject = pullRequest.toProject;

        // merge 할 때 사용할 Git 저장소 디렉토리 경로를 생성한다.
        String directory = GitRepository.getDirectoryForMerging(toProject.owner, toProject.name);

        // 이미 만들어둔 clone 디렉토리가 있다면 그걸 사용해서 Repository를 생성하고
        // 없을 때는 새로 만든다.
        File workingTreeDirectory = new File(directory + "/.git");
        if(!workingTreeDirectory.exists()) {
            return cloneRepository(pullRequest.toProject, directory).getRepository();
        } else {
            return new RepositoryBuilder().setGitDir(workingTreeDirectory).build();
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
        WindowCache.reconfigure(new WindowCacheConfig());

        File src = new File(getGitDirectory(this.ownerName, this.projectName));
        File dest = new File(getGitDirectory(this.ownerName, projectName));

        src.setWritable(true);

        return src.renameTo(dest);
    }

}