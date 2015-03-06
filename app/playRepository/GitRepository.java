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
import models.PullRequest;
import models.User;
import models.enumeration.ResourceType;
import models.resource.Resource;
import models.support.ModelLock;
import org.apache.commons.collections.IteratorUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.diff.*;
import org.eclipse.jgit.diff.Edit.Type;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.WindowCacheConfig;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
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

import javax.naming.LimitExceededException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

import static org.eclipse.jgit.diff.DiffEntry.ChangeType.*;

public class GitRepository implements PlayRepository {
    private static final ModelLock<Project> PROJECT_LOCK = new ModelLock<>();

    public static final int DIFF_SIZE_LIMIT = 3 * FileDiff.SIZE_LIMIT;
    public static final int DIFF_LINE_LIMIT = 3 * FileDiff.LINE_LIMIT;
    public static final int DIFF_FILE_LIMIT = 2000;
    public static final int COMMIT_HISTORY_LIMIT = 1000 * 1000;
    public static final int BLAME_FILE_LIMIT = 10;

    /**
     * The base directory of Git repository
     */
    private static String repoPrefix = "repo/git/";

    /**
     * The base directory of Git pull-request repository
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

    private final Repository repository;
    private final String ownerName;
    private final String projectName;

    /**
     * @see #buildGitRepository(String, String, boolean)
     */
    public GitRepository(String ownerName, String projectName, boolean alternatesMergeRepo) {
        this.ownerName = ownerName;
        this.projectName = projectName;
        this.repository = buildGitRepository(ownerName, projectName, alternatesMergeRepo);
    }

    public GitRepository(String ownerName, String projectName) {
        this(ownerName, projectName, true);
    }

    /**
     * @see #GitRepository(String, String)
     */
    public GitRepository(Project project) throws IOException {
        this(project.owner, project.name, true);
    }

    public static Repository buildGitRepository(String ownerName, String projectName,
                                                boolean alternatesMergeRepo) {
        try {
            RepositoryBuilder repo = new RepositoryBuilder()
                    .setGitDir(getGitDirectory(ownerName, projectName));

            if (alternatesMergeRepo) {
                repo.addAlternateObjectDirectory(getDirectoryForMergingObjects(ownerName,
                        projectName));
            }

            return repo.build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Repository buildGitRepository(String ownerName, String projectName) {
        return buildGitRepository(ownerName, projectName, true);
    }

    /**
     * @see #buildGitRepository(String, String, boolean)
     */
    public static Repository buildGitRepository(Project project) {
        return buildGitRepository(project, true);
    }

    public static Repository buildGitRepository(Project project, boolean alternatesMergeRepo) {
        return buildGitRepository(project.owner, project.name, alternatesMergeRepo);
    }

    public static void cloneLocalRepository(Project originalProject, Project forkProject)
            throws Exception {
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
     * @see Repository#create()
     */
    @Override
    public void create() throws IOException {
        this.repository.create(true);
    }

    /**
     * @see #getMetaDataFromPath(String, String)
     */
    @Override
    public ObjectNode getMetaDataFromPath(String path) throws IOException, GitAPIException, SVNException {
        return getMetaDataFromPath(null, path);
    }

    /**
     * Returns the path extended so that empty intermediate folders
     * are skipped.
     *
     * @see <a href="https://github.com/blog/1877-folder-jumping">Folder Jumping on GitHub blog</a>
     */
    public String extendPath(String basePath, String path) {
        try {
            ObjectId objectId = repository.resolve(Constants.HEAD);
            RevWalk revWalk = new RevWalk(repository);
            RevTree revTree = revWalk.parseTree(objectId);
            while (true) {
                String fullPath;
                if (StringUtils.isEmpty(basePath)) {
                    fullPath = path;
                } else {
                    fullPath = basePath + "/" + path;
                }

                TreeWalk treeWalk = TreeWalk.forPath(repository, fullPath, revTree);
                // path is not a folder
                if (treeWalk == null || !treeWalk.isSubtree()) return path;
                treeWalk.enterSubtree();
                treeWalk.next();
                // path contains a file
                if (!treeWalk.isSubtree()) return path;
                String next = path + "/" + treeWalk.getNameString();
                // path contains more than a single entry
                if (treeWalk.next()) return path;
                path = next;
            }
        } catch (IOException e) {
            return path;
        }
    }

    public boolean isIntermediateFolder(String path) {
        try {
            ObjectId objectId = repository.resolve(Constants.HEAD);
            RevWalk revWalk = new RevWalk(repository);
            RevTree revTree = revWalk.parseTree(objectId);
            if (StringUtils.isEmpty(path)) {
                return false;
            }
            TreeWalk treeWalk = TreeWalk.forPath(repository, path, revTree);
            // path is not a folder
            if (treeWalk == null || !treeWalk.isSubtree()) return false;
            treeWalk.enterSubtree();
            treeWalk.next();
            // path contains a file
            if (!treeWalk.isSubtree()) return false;
            // patch contains more than a single entry
            if (treeWalk.next()) return false;
            return true;
        } catch (IOException e) {
            return false;
        }
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

        treeWalk.setRecursive(true);
        treeWalk.setFilter(PathFilter.create(path));
        return treeWalk.next();
    }

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
            try {
                return fileAsJson(treeWalk, headCommit);
            } catch (MissingObjectException e) {
                Logger.debug("Unavailable access. " + branch + "/" + path + " does not exist.");
                return null;
            }
        }
    }

    /**
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
        if (!isBinary && file.getSize() <= MAX_FILE_SIZE_CAN_BE_VIEWED) {
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
     * Returns the metadata of a directory in which {@code treeWalk} is located
     * and those of its subdirectories and all files under the directory.
     *
     * The metadata consist of a set of entries to describe all files and
     * subdirectories of the current directory. Each entry also has information
     * about the last commit made for files or directories.
     *
     * @param treeWalk
     * @param untilCommitId
     * @return the metadata of the directory in json
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
        return (listData.size() == 0) ? null : result;
    }

    public class ObjectFinder {
        private SortedMap<String, JsonNode> found = new TreeMap<>();
        private Map<String, JsonNode> targets = new HashMap<>();
        private String basePath;
        private Iterator<RevCommit> commitIterator;
        private AnyObjectId untilCommitId;

        public ObjectFinder(String basePath, TreeWalk treeWalk, AnyObjectId untilCommitId) throws IOException, GitAPIException {
            while (treeWalk.next()) {
                String path = treeWalk.getNameString();
                ObjectNode object = Json.newObject();
                object.put("type", treeWalk.isSubtree() ? "folder" : "file");
                targets.put(path, object);
            }
            this.basePath = basePath;
            this.untilCommitId = untilCommitId;
            this.commitIterator = getCommitIterator(untilCommitId);
        }

        public SortedMap<String, JsonNode> find() throws IOException {
            RevCommit prev = null;
            RevCommit curr = null;
            int i = 0;

            // Empty targets means we have found every interested objects and
            // no need to continue.
            for (; i < COMMIT_HISTORY_LIMIT; i++) {
                if (targets.isEmpty()) {
                    break;
                }

                if (commitIterator.hasNext()) {
                    curr = commitIterator.next();
                } else {
                    // ** Illegal state detected! (JGit bug?) **
                    //
                    // If targets still remain but there is no next commit,
                    // something is wrong because the directory contains the
                    // targets does not have any commit modified them. Sometimes
                    // it occurs and it seems a bug of JGit. For the bug report,
                    // see http://dev.eclipse.org/mhonarc/lists/jgit-dev/msg02461.html
                    try {
                        commitIterator = getCommitIterator(curr.getId());

                        // If the new iterator returns a same commit as the
                        // previous one, just skip it.
                        curr = commitIterator.next();
                        if (curr.equals(prev)) {
                            curr = commitIterator.next();
                        }
                    } catch (Exception e) {
                        play.Logger.warn("An exception occurred while traversing git history", e);
                        break;
                    }
                }

                found(curr, findObjects(curr));

                prev = curr;
            }

            // Fall back in a slow way for the remainder of targets.
            for (String path : targets.keySet()) {
                Git git = new Git(repository);
                Iterator<RevCommit> iterator;

                try {
                    String targetPath = new File(basePath, path).getPath();
                    iterator = git.log().add(untilCommitId).addPath(targetPath)
                            .setMaxCount(1).call().iterator();
                } catch (GitAPIException e) {
                    play.Logger.warn("An exception occurred while traversing git history", e);
                    continue;
                }

                if (iterator.hasNext()) {
                    setLatestCommit(fixRevCommitNoParents(iterator.next()), path);
                }
            }

            return found;
        }

        /*
         * get commit logs with untilCommitId and basePath
         */
        private Iterator<RevCommit> getCommitIterator(AnyObjectId untilCommitId) throws
                IOException, GitAPIException {
            Git git = new Git(repository);
            LogCommand logCommand = git.log().add(untilCommitId);
            if (StringUtils.isNotEmpty(basePath)) {
                logCommand.addPath(basePath);
             }
            final Iterator<RevCommit> iterator = logCommand.call().iterator();
            return new Iterator<RevCommit>() {
                @Override
                public void remove() {
                    iterator.remove();
                }

                @Override
                public RevCommit next() {
                    // This may be a bug of JGit; RevWalk.iterator().next()
                    // should do this but doesn't.
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }

                    return fixRevCommitNoParents(iterator.next());
                }

                @Override
                public boolean hasNext() {
                    return iterator.hasNext();
                }
            };
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

            // Choose only objects that interest us among blobs and trees. We are interested in
            // blobs and trees which has change between the last commit and the current commit.
            traverseTree(commit, objectCollector);
            for(RevCommit parent : commit.getParents()) {
                RevCommit fixedParent = fixRevCommitNoTree(parent);
                traverseTree(fixedParent, objectRemover);
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

        private RevCommit fixRevCommitNoParents(RevCommit commit) {
            if (commit.getParentCount() == 0) {
                return fixRevCommit(commit);
            }
            return commit;
        }

        private RevCommit fixRevCommitNoTree(RevCommit commit) {
            if (commit.getTree() == null) {
                return fixRevCommit(commit);
            }
            return commit;
        }

        private RevCommit fixRevCommit(RevCommit commit) {
            RevWalk revWalk = new RevWalk(repository);
            try {
                return revWalk.parseCommit(commit);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        /*
         * Now, all `objects` are interesting. Get metadata from the objects, put
         * them into `found` and remove from `targets`.
         */
        private void found(RevCommit revCommit, Map<String, ObjectId> objects) {
            for (String path : objects.keySet()) {
                setLatestCommit(revCommit, path);
                targets.remove(path);
            }
        }

        private void setLatestCommit(RevCommit revCommit, String path) {
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
            data.put("commitId", commit.getShortId());
            data.put("commitUrl", routes.CodeHistoryApp.show(ownerName, projectName, commit.getShortId()).url());
            found.put(extendPath(basePath, path), data);
        }
    }

    public static interface TreeWalkHandler {
        void handle(TreeWalk treeWalk);
    }

    /**
     * Returns the contents of a file matched up with the given revision and
     * path.
     *
     * @param revision
     * @param path
     * @return null if the {@code path} denotes a directory; the contents otherwise.
     * @throws IOException
     */
    @Override
    public byte[] getRawFile(String revision, String path) throws IOException {
        RevTree tree = new RevWalk(repository).parseTree(repository.resolve(revision));
        TreeWalk treeWalk = TreeWalk.forPath(repository, path, tree);

        if (treeWalk == null || treeWalk.isSubtree()) {
            throw new FileNotFoundException();
        }

        return repository.open(treeWalk.getObjectId(0)).getBytes();
    }

    /**
     * Deletes the directory of this Git repository.
     *
     * This method will close the opened resources of the repository through
     * {@code repository.close()} and remove the references from packFile by
     * initializing {@code Cache} used in the repository.
     */
    @Override
    public void delete() throws Exception {
        repository.close();
        WindowCacheConfig config = new WindowCacheConfig();
        config.install();
        FileUtil.rm_rf(repository.getDirectory());
    }

    /**
     * Returns changes made through the commit denoted by the given revision.
     *
     * The patch comes from the difference between the root tree of the commit
     * denoted by the given revision and the root tree of the parent commit. If
     * there is no parent commit, compare it with an empty tree.
     *
     * @param rev
     * @return the string form of the patch in unified diff format
     * @throws GitAPIException
     * @throws IOException
     */
    @Override
    public String getPatch(String rev) throws IOException {
        RevCommit commit = getRevCommit(rev);

        if (commit == null) {
            return null;
        }

        RevCommit parent = null;
        if (commit.getParentCount() > 0) {
            parent = parseCommit(commit.getParent(0));
        }

        return getPatch(parent, commit);
    }

    @Override
    public String getPatch(String revA, String revB) throws IOException {
        RevCommit commitA = getRevCommit(revA);
        RevCommit commitB = getRevCommit(revB);

        if (commitA == null || commitB == null) {
            return null;
        }

        return getPatch(commitA, commitB);
    }

    /*
     * Render the difference from treeWalk which has two trees.
     */
    private String getPatch(RevCommit commitA, RevCommit commitB) throws IOException {
        TreeWalk treeWalk = new TreeWalk(repository);
        addTree(treeWalk, commitA);
        addTree(treeWalk, commitB);
        treeWalk.setRecursive(true);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DiffFormatter diffFormatter = new DiffFormatter(out);
        diffFormatter.setRepository(repository);
        diffFormatter.format(DiffEntry.scan(treeWalk));

        return out.toString("UTF-8");
    }

    private void addTree(TreeWalk treeWalk, RevCommit commit) throws IOException {
        if (commit == null) {
            treeWalk.addTree(new EmptyTreeIterator());
        } else {
            treeWalk.addTree(commit.getTree());
        }
    }

    /**
     * Returns the difference made by the commit denoted by the given revision.
     *
     * The patch comes from the difference between the root tree of the commit
     * denoted by the given revision and the root tree of the parent commit. If
     * there is no parent commit, compare it with an empty tree.
     *
     * @param rev the revision
     * @return the list of differences of each file
     * @throws GitAPIException
     * @throws IOException
     */
    public List<FileDiff> getDiff(String rev) throws IOException {
        return getDiff(repository, rev);
    }

    static public List<FileDiff> getDiff(Repository repository, String rev) throws IOException {
        return getDiff(repository, repository.resolve(rev));
    }

    public List<FileDiff> getDiff(ObjectId commitId) throws IOException {
        return getDiff(repository, commitId);
    }

    static public List<FileDiff> getDiff(Repository repository, ObjectId commitId) throws
            IOException {
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

    public static List<FileDiff> getDiff(Repository repository, RevCommit commit) throws IOException {
        ObjectId commitIdA = null;
        if (commit.getParentCount() > 0) {
            commitIdA = commit.getParent(0).getId();
        }

        return getFileDiffs(repository, repository, commitIdA, commit.getId());
    }


    /**
     * Returns all commits up to the given revision.
     *
     * @param pageNumber a zero-based page number
     * @param pageSize
     * @param untilRevName a revision; If null, it refers to HEAD.
     * @return a list of the commits
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
     * Check if the given ref name is under a well-known namespace.
     *
     * @param refName
     * @return true if the refName starts with "refs/heads/", "refs/tags/" or
     *         "refs/remotes/"
     */
    private static boolean isWellKnownRef(String refName) {
        return refName.startsWith(Constants.R_HEADS)
              || refName.startsWith(Constants.R_TAGS)
              || refName.startsWith(Constants.R_REMOTES);
    }

    /**
     * Returns names of all branches.
     *
     * @return a list of the name strings
     */
    @Override
    public List<String> getRefNames() {
        List<String> branches = new ArrayList<>();

        for(String refName : repository.getAllRefs().keySet()) {
            if (!isWellKnownRef(refName)) {
                continue;
            }

            branches.add(refName);
        }

        return branches;
    }

    public List<GitBranch> getBranches() throws IOException, GitAPIException {
        List<GitBranch> branches = new ArrayList<>();

        for(Ref ref : repository.getAllRefs().values()) {
            if (!isWellKnownRef(ref.getName())) {
                continue;
            }

            GitCommit commit = new GitCommit(
                    new RevWalk(getRepository()).parseCommit(ref.getObjectId()));
            GitBranch newBranch = new GitBranch(ref.getName(), commit);
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
                return Project.findByOwnerAndProjectName(ownerName, projectName);
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
     * Returns a directory path of a Git repository.
     *
     * This method is used when creating {@link Repository} object that refers
     * to a Git repository.
     *
     * @param project
     * @return
     * @see #getGitDirectory(String, String)
     */
    public static File getGitDirectory(Project project) {
        return getGitDirectory(project.owner, project.name);
    }

    /**
     * Returns a url of a Git repository.
     *
     * This method is used when a URL to Git repository is required for clone,
     * fetch and push command.
     *
     * @param project
     * @return
     * @throws IOException
     */
    public static String getGitDirectoryURL(Project project) throws IOException {
        return getGitDirectory(project).getCanonicalPath();
    }

    /**
     * Returns the directory path of a Git repository.
     *
     * This method is used when creating {@link Repository} object that refers
     * to a Git repository.
     *
     * @param ownerName
     * @param projectName
     * @return
     */
    public static File getGitDirectory(String ownerName, String projectName) {
        return new File(getRootDirectory(), ownerName + "/" + projectName + ".git");
    }

    private static File getRootDirectory() {
        return new File(utils.Config.getYobiHome(), getRepoPrefix());
    }

    private static File getRootDirectoryForMerging() {
        return new File(utils.Config.getYobiHome(), getRepoForMergingPrefix());
    }

    /**
     * Clones a Git repository.
     *
     * Creates a bare repository and copies all branches from the origin
     * repository into the bare repository.
     *
     * @param gitUrl the url of the origin repository
     * @param forkingProject the project of the cloned repository
     * @throws GitAPIException
     * @throws IOException
     * * @see <a href="https://www.kernel.org/pub/software/scm/git/docs/gitglossary.html#def_bare_repository">bare repository</a>
     */
    public static void cloneRepository(String gitUrl, Project forkingProject) throws GitAPIException {
        Git.cloneRepository()
                .setURI(gitUrl)
                .setDirectory(getGitDirectory(forkingProject))
                .setCloneAllBranches(true)
                .setBare(true)
                .call();
    }

    public static void cloneRepository(String gitUrl, Project forkingProject, String authId, String authPw) throws GitAPIException {
        Git.cloneRepository()
                .setURI(gitUrl)
                .setDirectory(getGitDirectory(forkingProject))
                .setCloneAllBranches(true)
                .setBare(true)
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(authId, authPw))
                .call();
    }

    /**
     * Checks out the branch.
     *
     * @param repository
     * @param branchName the name of a branch to be checked out
     * @throws GitAPIException
     */
    public static void checkout(Repository repository, String branchName) throws GitAPIException {
        new Git(repository).checkout()
                .setName(branchName)
                .setCreateBranch(false)
                .call();
    }

    /**
     * Returns a path to a working tree.
     *
     * This method is used for merge.
     *
     * @param owner
     * @param projectName
     * @return
     */
    public static File getDirectoryForMerging(String owner, String projectName) {
        return new File(getRootDirectoryForMerging(), owner + "/" + projectName + ".git");
    }

    public static File getDirectoryForMergingObjects(String owner, String projectName) {
        return new File(getDirectoryForMerging(owner, projectName), ".git/objects");
    }

    @SuppressWarnings("unchecked")
    public static List<RevCommit> diffRevCommits(Repository repository, ObjectId from, ObjectId to) throws IOException, GitAPIException {
        return IteratorUtils.toList(
                new Git(repository).log().addRange(from, to).call().iterator());
    }

    public static List<GitCommit> diffCommits(Repository repository, ObjectId from, ObjectId to) throws IOException, GitAPIException {
        return wrapInGitCommits(diffRevCommits(repository, from, to));
    }

    public static List<GitCommit> wrapInGitCommits(List<RevCommit> revCommits) throws IOException, GitAPIException {
        List<GitCommit> commits = new ArrayList<>();
        for (RevCommit revCommit : revCommits) {
            commits.add(new GitCommit(revCommit));
        }
        return commits;
    }

    /**
     * Finds authors who have made changes by comparing the differences in the
     * revisions.
     *
     * This method retrieves authors by mapping author names to email addresses
     * of the people who last modified the line.
     *
     * @param repository
     * @param revA
     * @param revB
     * @return
     * @throws IOException
     * @throws GitAPIException
     */
    public static Set<User> getRelatedAuthors(Repository repository, String revA, String revB)
            throws IOException, GitAPIException, LimitExceededException {
        Set<User> authors = new HashSet<>();
        RevWalk revWalk = null;

        try {
            revWalk = new RevWalk(repository);
            RevCommit commitA = revWalk.parseCommit(repository.resolve(revA));
            RevCommit commitB = revWalk.parseCommit(repository.resolve(revB));
            List<DiffEntry> diffs = getDiffEntries(repository, commitA, commitB);

            if (diffs.size() > BLAME_FILE_LIMIT) {
                String msg = String.format("Reject to get related authors " +
                        "from changes because of performance issue: The " +
                        "changes include %d files and it exceeds our limit " +
                        "of '%d' files.", diffs.size(), BLAME_FILE_LIMIT);
                throw new LimitExceededException(msg);
            }

            for (DiffEntry diff : diffs) {
                if (isTypeMatching(diff.getChangeType(), MODIFY, DELETE)) {
                    authors.addAll(getAuthorsFromDiffEntry(repository, diff, commitA));
                }
                if (isTypeMatching(diff.getChangeType(), RENAME)) {
                    authors.add(getAuthorFromFirstCommit(repository, diff.getOldPath(), commitA));
                }
            }
        } finally {
            if (revWalk != null) {
                revWalk.dispose();
            }
        }

        authors.remove(User.anonymous);
        return authors;
    }

    /**
     * Returns the difference between the given commits.
     *
     * @param repository
     * @param commitA
     * @param commitB
     * @return a list of {@link org.eclipse.jgit.diff.DiffEntry}
     * @throws IOException
     */
    private static List<DiffEntry> getDiffEntries(Repository repository, RevCommit commitA,
            RevCommit commitB) throws IOException {
        DiffFormatter diffFormatter = new DiffFormatter(NullOutputStream.INSTANCE);
        try {
            diffFormatter.setRepository(repository);
            diffFormatter.setDetectRenames(true);
            return diffFormatter.scan(commitA, commitB);
        } finally {
            diffFormatter.release();
        }
    }

    /**
     * Checks if {@code types} contain the given {@code type}.
     *
     * @param type
     * @param types
     * @return
     */
    private static boolean isTypeMatching(Object type, Object... types) {
        return ArrayUtils.contains(types, type);
    }

    /**
     * Finds authors who last modified or removed lines from the given edit
     * list.
     *
     * @param repository
     * @param diff   an edit list
     * @param start  the oldest commit to be considered
     * @return
     * @throws GitAPIException
     * @throws IOException
     */
    private static Set<User> getAuthorsFromDiffEntry(Repository repository, DiffEntry diff,
            RevCommit start) throws GitAPIException, IOException {
        DiffFormatter diffFormatter = new DiffFormatter(NullOutputStream.INSTANCE);
        try {
            diffFormatter.setRepository(repository);
            EditList edits = diffFormatter.toFileHeader(diff).toEditList();
            BlameResult blameResult = new Git(repository).blame()
                    .setFilePath(diff.getOldPath())
                    .setFollowFileRenames(true)
                    .setStartCommit(start).call();
            return getAuthorsFromBlameResult(edits, blameResult);
        } finally {
            diffFormatter.release();
        }
    }

    /**
     * Finds authors who modified or removed lines from the given edit list and
     * the result of git blame.
     *
     * @param edits an edit list
     * @param blameResult the result of blame
     * @return a set of authors
     */
    private static Set<User> getAuthorsFromBlameResult(EditList edits, BlameResult blameResult) {
        Set<User> authors = new HashSet<>();
        for (Edit edit : edits) {
            if (isTypeMatching(edit.getType(), Type.REPLACE, Type.DELETE)) {
                for (int i = edit.getBeginA(); i < edit.getEndA(); i++) {
                    authors.add(findAuthorByPersonIdent(blameResult.getSourceAuthor(i)));
                }
            }
        }
        return authors;
    }

    /**
     * Finds the author of a file denoted by the given path.
     *
     * @param repository
     * @param path
     * @param start Considers only the commits made since {@code start}.
     * @return
     * @throws IOException
     */
    private static User getAuthorFromFirstCommit(Repository repository, String path, RevCommit start)
            throws IOException {
        RevWalk revWalk = null;
        try {
            revWalk = new RevWalk(repository);
            revWalk.markStart(start);
            revWalk.setTreeFilter(PathFilter.create(path));
            revWalk.sort(RevSort.REVERSE);
            RevCommit commit = revWalk.next();
            if (commit == null) {
                return User.anonymous;
            }
            return findAuthorByPersonIdent(commit.getAuthorIdent());
        } finally {
            if (revWalk != null) {
                revWalk.dispose();
            }
        }
    }

    /**
     * Finds a user who matches up with the {@code personIdent}.
     *
     * @param personIdent
     * @return
     */
    private static User findAuthorByPersonIdent(PersonIdent personIdent) {
        if (personIdent == null) {
            return User.anonymous;
        }
        return User.findByEmail(personIdent.getEmailAddress());
    }

    /**
     * Deletes a branch denoted by the given name.
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

    public static Repository buildMergingRepository(PullRequest pullRequest) {
        return buildMergingRepository(pullRequest.toProject);
    }

    public static Repository buildMergingRepository(Project project) {
        File workingDirectory = GitRepository.getDirectoryForMerging(project.owner, project.name);

        try {
            File gitDir = new File(workingDirectory + "/.git");
            if(!gitDir.exists()) {
                return cloneRepository(project, workingDirectory).getRepository();
            } else {
                return new RepositoryBuilder().setGitDir(gitDir).build();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Git cloneRepository(Project project, File workingDirectory) throws GitAPIException, IOException {
        return Git.cloneRepository()
                .setURI(GitRepository.getGitDirectoryURL(project))
                .setDirectory(workingDirectory)
                .call();
    }

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

    @Override
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
        int size = 0;
        int lines = 0;

        for (DiffEntry diff : formatter.scan(treeParserA, treeParserB)) {
            FileDiff fileDiff = new FileDiff();
            fileDiff.commitA = commitA != null ? commitA.getName() : null;
            fileDiff.commitB = commitB != null ? commitB.getName() : null;

            fileDiff.changeType = diff.getChangeType();

            fileDiff.oldMode = diff.getOldMode();
            fileDiff.newMode = diff.getNewMode();

            String pathA = diff.getPath(DiffEntry.Side.OLD);
            String pathB = diff.getPath(DiffEntry.Side.NEW);

            byte[] rawA = null;
            if (treeA != null
                    && Arrays.asList(DELETE, MODIFY, RENAME, COPY).contains(diff.getChangeType())) {
                TreeWalk t1 = TreeWalk.forPath(repositoryA, pathA, treeA);
                ObjectId blobA = t1.getObjectId(0);
                fileDiff.pathA = pathA;

                try {
                    rawA = repositoryA.open(blobA).getBytes();
                    fileDiff.isBinaryA = RawText.isBinary(rawA);
                    fileDiff.a = fileDiff.isBinaryA ? null : new RawText(rawA);
                } catch (org.eclipse.jgit.errors.LargeObjectException e) {
                    fileDiff.addError(FileDiff.Error.A_SIZE_EXCEEDED);
                }
            }

            byte[] rawB = null;
            if (treeB != null
                    && Arrays.asList(ADD, MODIFY, RENAME, COPY).contains(diff.getChangeType())) {
                TreeWalk t2 = TreeWalk.forPath(repositoryB, pathB, treeB);
                ObjectId blobB = t2.getObjectId(0);
                fileDiff.pathB = pathB;

                try {
                    rawB = repositoryB.open(blobB).getBytes();
                    fileDiff.isBinaryB = RawText.isBinary(rawB);
                    fileDiff.b = fileDiff.isBinaryB ? null : new RawText(rawB);
                } catch (org.eclipse.jgit.errors.LargeObjectException e) {
                    fileDiff.addError(FileDiff.Error.B_SIZE_EXCEEDED);
                }
            }

            if (size > DIFF_SIZE_LIMIT || lines > DIFF_LINE_LIMIT) {
                fileDiff.addError(FileDiff.Error.OTHERS_SIZE_EXCEEDED);
                result.add(fileDiff);
                continue;
            }

            // Get diff if necessary
            if (fileDiff.a != null
                    && fileDiff.b != null
                    && !(fileDiff.isBinaryA || fileDiff.isBinaryB)
                    && Arrays.asList(MODIFY, RENAME).contains(diff.getChangeType())) {
                DiffAlgorithm diffAlgorithm = DiffAlgorithm.getAlgorithm(
                        repositoryB.getConfig().getEnum(
                                ConfigConstants.CONFIG_DIFF_SECTION, null,
                                ConfigConstants.CONFIG_KEY_ALGORITHM,
                                DiffAlgorithm.SupportedAlgorithm.HISTOGRAM));
                fileDiff.editList = diffAlgorithm.diff(RawTextComparator.DEFAULT, fileDiff.a,
                        fileDiff.b);
                size += fileDiff.getHunks().size;
                lines += fileDiff.getHunks().lines;
            }

            // update lines and sizes
            if (fileDiff.b != null && !fileDiff.isBinaryB && diff.getChangeType().equals(ADD)) {
                lines += fileDiff.b.size();
                size += rawB.length;
            }

            // update lines and sizes
            if (fileDiff.a != null && !fileDiff.isBinaryA && diff.getChangeType().equals(DELETE)) {
                lines += fileDiff.a.size();
                size += rawA.length;
            }

            // Stop if exceeds the limit for total number of files
            if (result.size() > DIFF_FILE_LIMIT) {
                break;
            }

            result.add(fileDiff);
        }

        return result;
    }

    /**
     * Clones a local repository.
     *
     * This doesn't copy Git objects but hardlink them to save disk space.
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

    public static class CloneAndFetch {

        private Repository repository;

        private String destToBranchName;

        private String destFromBranchName;

        private String mergingBranchName;

        public Repository getRepository() {
            return repository;
        }

        public String getDestToBranchName() {
            return destToBranchName;
        }

        public String getDestFromBranchName() {
            return destFromBranchName;
        }

        public String getMergingBranchName() {
            return mergingBranchName;
        }

        private CloneAndFetch(Repository repository, String destToBranchName, String destFromBranchName, String mergingBranchName) {
            this.repository = repository;
            this.destToBranchName = destToBranchName;
            this.destFromBranchName = destFromBranchName;
            this.mergingBranchName = Constants.R_HEADS + mergingBranchName;
        }
    }

    public static interface AfterCloneAndFetchOperation {
        public void invoke(CloneAndFetch cloneAndFetch) throws IOException, GitAPIException;
    }

    public void close() {
        repository.close();
    }

    /**
     * @see playRepository.PlayRepository#renameTo(String)
     */
    @Override
    public boolean renameTo(String projectName) {
        return move(this.ownerName, this.projectName, this.ownerName, projectName);
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
        return this.getRefNames().isEmpty();
    }

    /*
     * Finds a Git object denoted by the given revision.
     */
    private ObjectId getObjectId(String revstr) throws IOException {
        if (revstr == null) {
            return repository.resolve(Constants.HEAD);
        } else {
            return repository.resolve(revstr);
        }
    }

    /*
     * Finds a RevCommit object denoted by the given revision.
     */
    private RevCommit getRevCommit(String revstr) throws IOException {
        ObjectId objectId = getObjectId(revstr);
        return parseCommit(objectId);
    }

    /*
     * Finds a RevCommit object with the given id.
     */
    private RevCommit parseCommit(AnyObjectId objectId) throws IOException {
        if (objectId == null) {
            return null;
        }
        RevWalk revWalk = new RevWalk(repository);
        return revWalk.parseCommit(objectId);
    }

    public boolean move(String srcProjectOwner, String srcProjectName, String desrProjectOwner, String destProjectName) {
        repository.close();
        WindowCacheConfig config = new WindowCacheConfig();
        config.install();

        File srcGitDirectory = getGitDirectory(srcProjectOwner, srcProjectName);
        File destGitDirectory = getGitDirectory(desrProjectOwner, destProjectName);
        File srcGitDirectoryForMerging = getDirectoryForMerging(srcProjectOwner, srcProjectName);
        File destGitDirectoryForMerging = getDirectoryForMerging(desrProjectOwner, destProjectName);
        srcGitDirectory.setWritable(true);
        srcGitDirectoryForMerging.setWritable(true);

        try {
            if(srcGitDirectory.exists()) {
                org.apache.commons.io.FileUtils.moveDirectory(srcGitDirectory, destGitDirectory);
                play.Logger.debug("[Transfer] Move from: " + srcGitDirectory.getAbsolutePath()
                        + "to " + destGitDirectory);
            } else {
                play.Logger.warn("[Transfer] Nothing to move from: " + srcGitDirectory.getAbsolutePath());
            }

            if(srcGitDirectoryForMerging.exists()) {
                org.apache.commons.io.FileUtils.moveDirectory(srcGitDirectoryForMerging, destGitDirectoryForMerging);
                play.Logger.debug("[Transfer] Move from: " + srcGitDirectoryForMerging.getAbsolutePath()
                        + "to " + destGitDirectoryForMerging);
            } else {
                play.Logger.warn("[Transfer] Nothing to move from: " + srcGitDirectoryForMerging.getAbsolutePath());
            }
            return true;
        } catch (IOException e) {
            play.Logger.error("[Transfer] Move Failed", e);
            return false;
        }
    }

    @Override
    public File getDirectory() {
        return this.repository.getDirectory();
    }

    public Repository getRepository() {
        return repository;
    }
}
