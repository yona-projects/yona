package git;

import java.io.*;
import java.util.Date;

import org.codehaus.jackson.node.ObjectNode;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.errors.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.*;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

import play.libs.Json;
import controllers.GitApp;

public class GitRepository {
    public static final String REPO_PREFIX = "repo/git/";

    public static Repository createRepository(String projectName) throws IOException {
        Repository repository = new RepositoryBuilder().setGitDir(
                new File(GitApp.REPO_PREFIX + projectName + ".git")).build();
        boolean bare = true;
        repository.create(bare); // create bare repository
        // TODO 최초의 커밋 미리만들기? 아님 그냥 안내 보여주기?

        return repository;
    }

    public static Repository getRepository(String projectName) throws IOException {
        return new RepositoryBuilder().setGitDir(new File(REPO_PREFIX + projectName + ".git"))
                .build();
    }

    private Repository repository;

    public GitRepository(String projectName) throws IOException {
        this.repository = getRepository(projectName);
    }
    
    /**
     * path를 받아 파일 정보 JSON객체를 return 하는 함수 일단은 HEAD 만 가능하다.
     * @param path              정보를 얻고싶은 파일의 path
     * @return JSON객체         파일 정보를 담고 있다.
     * @throws IOException
     * @throws NoHeadException
     * @throws GitAPIException
     */
    public ObjectNode findFileInfo(String path) throws IOException, NoHeadException,
            GitAPIException {
        // 파일 정보를 찾아서 Json으로 리턴?
        Git git = new Git(repository);

        ObjectId headCommit = repository.resolve(Constants.HEAD);// 만약 특정 커밋을
                                                                 // 얻오오고싶다면 바꾸어
                                                                 // 주면 된다.

        if (headCommit == null) {
            throw new NoHeadException("HEAD가 존재하지 않습니다.");
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
            //root
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
            RevCommit commit = git.log().addPath(treeWalk.getPathString()).call().iterator()
                    .next();

            ObjectNode data = Json.newObject();
            data.put("type", treeWalk.isSubtree() ? "folder" : "file");
            data.put("commitMessage", commit.getShortMessage());
            data.put("commiter", commit.getAuthorIdent().getName());
            data.put("commitDate", new Date(commit.getCommitTime() * 1000l).toString());
            result.put(treeWalk.getNameString(), data);
        }
        return result;
    }
}
