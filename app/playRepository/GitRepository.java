package playRepository;

import java.io.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.codehaus.jackson.node.ObjectNode;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.errors.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.*;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

import com.google.common.collect.Lists;

import play.Logger;
import play.libs.Json;
import utils.FileUtil;

public class GitRepository implements PlayRepository {
    public static final String REPO_PREFIX = "repo/git/";

    private Repository repository;

    public GitRepository(String userName, String projectName) throws IOException {
        this.repository = createGitRepository(userName, projectName);
    }

    public static Repository createGitRepository(String userName, String projectName) throws IOException {
        return new RepositoryBuilder().setGitDir(
                new File(REPO_PREFIX + userName + "/" + projectName + ".git")).build();
    }

    /* (non-Javadoc)
     * @see Repository.repository#create()
     */
    @Override
    public void create() throws IOException {
        this.repository.create(true); // create bare repository
    }

    /**
     * path를 받아 파일 정보 JSON객체를 return 하는 함수 일단은 HEAD 만 가능하다.
     * 
     * @param path
     *            정보를 얻고싶은 파일의 path
     * @return JSON객체 파일 정보를 담고 있다.
     * @throws IOException
     * @throws NoHeadException
     * @throws GitAPIException
     */
    @Override
    public ObjectNode findFileInfo(String path) throws IOException, NoHeadException,
            GitAPIException {
        // 파일 정보를 찾아서 Json으로 리턴?
        Git git = new Git(repository);

        ObjectId headCommit = repository.resolve(Constants.HEAD);
        // 만약 특정 커밋을 얻오오고싶다면 바꾸어 주면 된다.

        if (headCommit == null) {
            Logger.debug("GitRepository : init Project - No Head commit");
            return null;
        }

        RevWalk revWalk = new RevWalk(repository);
        RevTree revTree = revWalk.parseTree(headCommit);
        TreeWalk treeWalk = new TreeWalk(repository);
        treeWalk.addTree(revTree);

        try {
            PathFilter pathFilter = PathFilter.create(path);

            treeWalk.setFilter(pathFilter);

            while (treeWalk.next()) {
                if (pathFilter.isDone(treeWalk)) {
                    break;
                } else if (treeWalk.isSubtree()) {
                    treeWalk.enterSubtree();
                }
            }
        } catch (IllegalArgumentException e) {
            // root
            return folderList(git, treeWalk);
        }

        if (treeWalk.isSubtree()) {
            treeWalk.enterSubtree();
            return folderList(git, treeWalk);
        } else {
            // 파일 내려주기
            ObjectId objectId = treeWalk.getObjectId(0);

            RevCommit commit = git.log().addPath(path).call().iterator().next();
            ObjectNode result = Json.newObject();
            result.put("commitMessage", commit.getShortMessage());
            result.put("commiter", commit.getAuthorIdent().getName());

            result.put("commitDate", new Date(commit.getCommitTime() * 1000l).toString());

            String str = new String(repository.open(objectId).getBytes());
            result.put("data", str);
            return result;
        }
    }

    private ObjectNode folderList(Git git, TreeWalk treeWalk) throws MissingObjectException,
            IncorrectObjectTypeException, CorruptObjectException, IOException, GitAPIException,
            NoHeadException {
        ObjectNode result = Json.newObject();
        while (treeWalk.next()) {
            RevCommit commit = git.log().addPath(treeWalk.getPathString()).call().iterator().next();

            ObjectNode data = Json.newObject();
            data.put("type", treeWalk.isSubtree() ? "folder" : "file");
            data.put("commitMessage", commit.getShortMessage());
            data.put("commiter", commit.getAuthorIdent().getName());
            data.put("commitDate", new Date(commit.getCommitTime() * 1000l).toString());
            result.put(treeWalk.getNameString(), data);
        }
        return result;
    }

    /* (non-Javadoc)
     * @see Repository.repository#getRawFile(java.lang.String)
     */
    @Override
    public byte[] getRawFile(String path) throws MissingObjectException,
            IncorrectObjectTypeException, AmbiguousObjectException, IOException {
        RevTree tree = new RevWalk(repository).parseTree(repository.resolve("HEAD"));
        TreeWalk treeWalk = TreeWalk.forPath(repository, path, tree);
        if (treeWalk.isSubtree()) {
            return null;
        } else {
            return repository.open(treeWalk.getObjectId(0)).getBytes();
        }
    }

    @Override
    public void delete() {
        FileUtil.rm_rf(repository.getDirectory());
    }

    @Override
    public String getPatch(String rev) throws GitAPIException, MissingObjectException,
            IncorrectObjectTypeException, IOException {
        // Get the current commit.
        ObjectId commitId = repository.resolve(rev);
        RevWalk walk = new RevWalk(repository);
        RevCommit commit = walk.parseCommit(commitId);

        // Get the current and parent commit's trees.
        RevTree a = commit.getTree();
        RevTree b = walk.parseCommit(commit.getParent(0).getId()).getTree();

        // Render the difference.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DiffFormatter diffFormatter = new DiffFormatter(out);
        diffFormatter.setRepository(repository);
        diffFormatter.format(diffFormatter.scan(b, a));

        return out.toString("UTF-8");
    }

    @Override
    public List<Commit> getHistory(int page, int limit) throws AmbiguousObjectException,
            IOException, NoHeadException, GitAPIException {
        // Get the list of commits from HEAD to the given page.
        Iterable<RevCommit> iter = new Git(repository).log()
                .setMaxCount(page * limit + limit).call();
        List<RevCommit> list = new LinkedList<RevCommit>();
        for(RevCommit commit : iter) {
            if (list.size() >= limit) {
                list.remove(0);
            }
            list.add(commit);
        }

        List<Commit> result = new ArrayList<Commit>();
        for(RevCommit commit : list) {
            result.add(new GitCommit(commit));
        }

        return result;
    }

}
