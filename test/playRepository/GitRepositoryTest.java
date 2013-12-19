package playRepository;

import static play.test.Helpers.*;
import models.Project;
import models.PullRequest;

import org.apache.commons.io.FileUtils;
import org.codehaus.jackson.node.ObjectNode;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import play.test.FakeApplication;
import play.test.Helpers;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.Fail.fail;

public class GitRepositoryTest {

    @Before
    public void before() {
        GitRepository.setRepoPrefix("resources/test/repo/git/");
        GitRepository.setRepoForMergingPrefix("resources/test/repo/git-merging/");
    }

    @After
    public void after() {
        rm_rf(new File(GitRepository.getRepoPrefix()));
        rm_rf(new File(GitRepository.getRepoForMergingPrefix()));
    }

    @Test
    public void gitRepository() throws Exception {
        // Given
        String userName = "yobi";
        String projectName = "testProject";
        // When
        GitRepository repo = new GitRepository(userName, projectName);
        // Then
        assertThat(repo).isNotNull();
    }

    @Test
    public void create() throws Exception {
        // Given
        String userName = "yobi";
        String projectName = "testProject";
        GitRepository repo = new GitRepository(userName, projectName);
        // When
        repo.create();
        // Then
        File file = new File(GitRepository.getRepoPrefix() + userName + "/" + projectName + ".git");
        assertThat(file.exists()).isTrue();
        file = new File(GitRepository.getRepoPrefix() + userName + "/" + projectName + ".git"
                + "/objects");
        assertThat(file.exists()).isTrue();
        file = new File(GitRepository.getRepoPrefix() + userName + "/" + projectName + ".git" + "/refs");
        assertThat(file.exists()).isTrue();

        // cleanup
        repo.close();
    }

    @Test
    public void getPatch() throws IOException, NoFilepatternException, GitAPIException {
        // given
        String userName = "yobi";
        String projectName = "testProject";
        String wcPath = GitRepository.getRepoPrefix() + userName + "/" + projectName;

        String repoPath = wcPath + "/.git";
        File repoDir = new File(repoPath);
        Repository repo = new RepositoryBuilder().setGitDir(repoDir).build();
        repo.create(false);

        Git git = new Git(repo);
        String testFilePath = wcPath + "/readme.txt";
        BufferedWriter out = new BufferedWriter(new FileWriter(testFilePath));

        // when
        out.write("hello 1");
        out.flush();
        git.add().addFilepattern("readme.txt").call();
        git.commit().setMessage("commit 1").call();

        out.write("hello 2");
        out.flush();
        git.add().addFilepattern("readme.txt").call();
        RevCommit commit = git.commit().setMessage("commit 2").call();

        GitRepository gitRepo = new GitRepository(userName, projectName + "/");
        String patch = gitRepo.getPatch(commit.getId().getName());

        // then
        assertThat(patch).contains("-hello 1");
        assertThat(patch).contains("+hello 1hello 2");
    }

    @Test
    public void getHistory() throws IOException, NoFilepatternException, GitAPIException {
        // given
        String userName = "yobi";
        String projectName = "testProject";
        String wcPath = GitRepository.getRepoPrefix() + userName + "/" + projectName;

        String repoPath = wcPath + "/.git";
        File repoDir = new File(repoPath);
        Repository repo = new RepositoryBuilder().setGitDir(repoDir).build();
        repo.create(false);

        Git git = new Git(repo);
        String testFilePath = wcPath + "/readme.txt";
        BufferedWriter out = new BufferedWriter(new FileWriter(testFilePath));

        // when
        out.write("hello 1");
        out.flush();
        git.add().addFilepattern("readme.txt").call();
        git.commit().setMessage("commit 1").call();

        out.write("hello 2");
        out.flush();
        git.add().addFilepattern("readme.txt").call();
        git.commit().setMessage("commit 2").call();

        git.tag().setName("tag").setAnnotated(true).call();

        out.write("hello 3");
        out.flush();
        git.add().addFilepattern("readme.txt").call();
        git.commit().setMessage("commit 3").call();

        GitRepository gitRepo = new GitRepository(userName, projectName + "/");

        List<Commit> history2 = gitRepo.getHistory(0, 2, "HEAD", null);
        List<Commit> history5 = gitRepo.getHistory(0, 5, null, null);
        List<Commit> tagHistory2 = gitRepo.getHistory(0, 2, "tag", null);

        // then
        assertThat(history2.size()).isEqualTo(2);
        assertThat(history2.get(0).getMessage()).isEqualTo("commit 3");
        assertThat(history2.get(1).getMessage()).isEqualTo("commit 2");

        assertThat(history5.size()).isEqualTo(3);
        assertThat(history5.get(0).getMessage()).isEqualTo("commit 3");
        assertThat(history5.get(1).getMessage()).isEqualTo("commit 2");
        assertThat(history5.get(2).getMessage()).isEqualTo("commit 1");

        assertThat(tagHistory2.size()).isEqualTo(2);
        assertThat(tagHistory2.get(0).getMessage()).isEqualTo("commit 2");
        assertThat(tagHistory2.get(1).getMessage()).isEqualTo("commit 1");
    }

    @Test
    public void cloneRepository() throws Exception {
        // Given
        String userName = "whiteship";
        String projectName = "testProject";

        Project original = createProject(userName, projectName);
        Project fork = createProject("keesun", projectName);

        rm_rf(new File(GitRepository.getGitDirectory(original)));
        rm_rf(new File(GitRepository.getGitDirectory(fork)));

        GitRepository fromRepo = new GitRepository(userName, projectName);
        fromRepo.create();

        // When
        String gitUrl = GitRepository.getGitDirectoryURL(original);
        GitRepository.cloneRepository(gitUrl, fork);

        // Then
        File file = new File(GitRepository.getGitDirectory(fork));
        assertThat(file.exists()).isTrue();
    }

    @Test
    public void cloneRepositoryWithNonBareMode() throws IOException, GitAPIException {
        // Given
        Project originProject = createProject("whiteship", "test");
        rm_rf(new File(GitRepository.getGitDirectory(originProject)));
        new GitRepository(originProject.owner, originProject.name).create();

        String cloneWorkingTreePath = GitRepository.getDirectoryForMerging(originProject.owner, originProject.name);
        rm_rf(new File(cloneWorkingTreePath));

        // When
        Git.cloneRepository()
                .setURI(GitRepository.getGitDirectoryURL(originProject))
                .setDirectory(new File(cloneWorkingTreePath))
                .call();

        // Then
        assertThat(new File(cloneWorkingTreePath).exists()).isTrue();
        assertThat(new File(cloneWorkingTreePath + "/.git").exists()).isTrue();

        Repository cloneRepository = new RepositoryBuilder()
                .setWorkTree(new File(cloneWorkingTreePath))
                .setGitDir(new File(cloneWorkingTreePath + "/.git"))
                .build();

        assertThat(cloneRepository.getFullBranch()).isEqualTo("refs/heads/master");

        // When
        Git cloneGit = new Git(cloneRepository);

        // toProject를 clone 받은 워킹 디렉토리에서 테스트 파일 만들고 커밋하고 푸쉬하기
        String readmeFileName = "readme.md";
        String testFilePath = cloneWorkingTreePath + "/" + readmeFileName;
        BufferedWriter out = new BufferedWriter(new FileWriter(testFilePath));
        out.write("hello 1");
        out.flush();
        cloneGit.add().addFilepattern(readmeFileName).call();
        cloneGit.commit().setMessage("commit 1").call();
        cloneGit.push().call();

        // Then
        Repository originRepository = GitRepository.buildGitRepository(originProject);
        String readmeFileInClone = new String(getRawFile(cloneRepository, readmeFileName));
        assertThat(readmeFileInClone).isEqualTo("hello 1");
        String readmeFileInOrigin = new String(getRawFile(originRepository, readmeFileName));
        assertThat(readmeFileInOrigin).isEqualTo("hello 1");

        cloneRepository.close();
        originRepository.close();
    }


    @Test
    public void getMetaDataFromPath() throws Exception {
        // Given
        final String userName = "yobi";
        final String projectName = "mytest";
        final String branchName = "branch";
        final String lightWeightTagName = "tag1";
        final String annotatedTagName = "tag2";
        String wcPath = GitRepository.getRepoPrefix() + userName + "/" + projectName;

        Repository repository = GitRepository.buildGitRepository(userName, projectName + "/");
        repository.create();
        Git git = new Git(repository);
        FileUtils.touch(new File(wcPath + "/hello"));
        FileUtils.touch(new File(wcPath + "/dir/world"));
        git.add().addFilepattern("hello").call();
        git.add().addFilepattern("dir").call();
        git.commit().setAuthor("yobi", "yobi@yobi.io").setMessage("test").call();
        git.branchCreate().setName(branchName).call();
        git.tag().setName(lightWeightTagName).setAnnotated(false).call();
        git.tag().setName(annotatedTagName).setAnnotated(true).setMessage("annotated tag").call();
        repository.close();

        running(support.Helpers.makeTestApplication(), new Runnable() {
            @Override
            public void run() {
                try {
                    // When
                    GitRepository gitRepository = new GitRepository(userName, projectName + "/");
                    ObjectNode notExistBranch = gitRepository.getMetaDataFromPath("not_exist_branch", "");
                    ObjectNode root = gitRepository.getMetaDataFromPath("");
                    ObjectNode dir = gitRepository.getMetaDataFromPath("dir");
                    ObjectNode file= gitRepository.getMetaDataFromPath("hello");
                    ObjectNode branch = gitRepository.getMetaDataFromPath(branchName, "");
                    ObjectNode lightWeightTag = gitRepository.getMetaDataFromPath(lightWeightTagName, "");
                    ObjectNode annotatedTag = gitRepository.getMetaDataFromPath(annotatedTagName, "");

                    // Then
                    assertThat(notExistBranch).isNull();
                    assertThat(root.get("type").getTextValue()).isEqualTo("folder");
                    assertThat(root.get("data").get("hello").get("type").getTextValue()).isEqualTo("file");
                    assertThat(root.get("data").get("dir").get("type").getTextValue()).isEqualTo("folder");
                    assertThat(dir.get("type").getTextValue()).isEqualTo("folder");
                    assertThat(dir.get("data").get("world").get("type").getTextValue()).isEqualTo("file");
                    assertThat(file.get("type").getTextValue()).isEqualTo("file");
                    assertThat(branch.toString()).isEqualTo(root.toString());
                    assertThat(lightWeightTag.toString()).isEqualTo(root.toString());
                    assertThat(annotatedTag.toString()).isEqualTo(root.toString());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    @Ignore
    @Test
    public void getRawFile() throws Exception {
        // Given
        String userName = "yobi";
        String projectName = "testProject";
        GitRepository repo = new GitRepository(userName, projectName);
        // When
        repo.getRawFile("HEAD", "readme");
        // Then
    }

    @Test
    public void buildCloneRepository() throws GitAPIException, IOException {
        // Given
        Project original = createProject("keesun", "test");
        PullRequest pullRequest = createPullRequest(original);
        new GitRepository("keesun", "test").create();

        // When
        Repository repository = GitRepository.buildCloneRepository(pullRequest);

        // Then
        assertThat(repository).isNotNull();
        String repositoryWorkingDirectory = GitRepository.getDirectoryForMerging(original.owner, original.name);
        assertThat(repositoryWorkingDirectory).isNotNull();
        String clonePath = "resources/test/repo/git-merging/keesun/test.git";
        assertThat(repositoryWorkingDirectory).isEqualTo(clonePath);
        assertThat(new File(clonePath).exists()).isTrue();
        assertThat(new File(clonePath + "/.git").exists()).isTrue();
    }

    @Test
    public void deleteBranch() throws IOException, GitAPIException {
        // Given
        Project original = createProject("keesun", "test");
        PullRequest pullRequest = createPullRequest(original);
        new GitRepository(original).create();
        Repository repository = GitRepository.buildCloneRepository(pullRequest);

        Git git = new Git(repository);
        String branchName = "refs/heads/master";
        newCommit(original, repository, "readme.md", "Hello World", "Initial commit");
        git.branchCreate().setName("develop").setForce(true).call();
        GitRepository.checkout(repository, "develop");

        // When
        GitRepository.deleteBranch(repository, branchName);

        // Then
        List<Ref> refs = git.branchList().call();
        for(Ref ref : refs) {
            if(ref.getName().equals(branchName)) {
                fail("deleting branch was failed");
            }
        }
    }

    @Test
    public void fetch() throws IOException, GitAPIException {
        // Given
        Project original = createProject("keesun", "test");
        PullRequest pullRequest = createPullRequest(original);
        new GitRepository(original).create();
        Repository repository = GitRepository.buildCloneRepository(pullRequest);

        RevCommit commit = newCommit(original, repository, "readme.md", "hello 1", "commit 1");
        new Git(repository).push().call();

        // When
        String dstBranchName = "refs/heads/master-fetch";
        GitRepository.fetch(repository, original, "refs/heads/master", dstBranchName);

        // Then
        ObjectId branch = repository.resolve(dstBranchName);
        assertThat(branch.getName()).isEqualTo(commit.getId().getName());
    }

    private RevCommit newCommit(Project project, Repository repository, String fileName, String content, String message) throws IOException, GitAPIException {
        String workingTreePath = GitRepository.getDirectoryForMerging(project.owner, project.name);
        String testFilePath = workingTreePath + "/" + fileName;
        BufferedWriter out = new BufferedWriter(new FileWriter(testFilePath));
        out.write(content);
        out.flush();
        out.close();

        Git git = new Git(repository);
        git.add().addFilepattern(fileName).call();
        return git.commit().setMessage(message).call();
    }

    @Test
    public void checkout() throws IOException, GitAPIException {
        // Given
        Project original = createProject("keesun", "test");
        PullRequest pullRequest = createPullRequest(original);
        new GitRepository(original).create();
        Repository repository = GitRepository.buildCloneRepository(pullRequest);
        // 커밋이 없으면 HEAD도 없어서 브랜치 만들 때 에러가 발생하기 때문에 일단 하나 커밋한다.
        newCommit(original, repository, "readme.md", "hello 1", "commit 1");

        Git git = new Git(repository);
        String branchName = "new-branch";
        git.branchCreate()
                .setName(branchName)
                .setForce(true)
                .call();

        // When
        GitRepository.checkout(repository, branchName);

        // Then
        assertThat(repository.getFullBranch()).isEqualTo("refs/heads/" + branchName);
    }

    @Test
    public void merge() throws IOException, GitAPIException {
        // Given
        Project original = createProject("keesun", "test");
        PullRequest pullRequest = createPullRequest(original);
        GitRepository gitRepository = new GitRepository(original);
        gitRepository.create();
        Repository repository = GitRepository.buildCloneRepository(pullRequest);

        // master에 commit 1 추가
        newCommit(original, repository, "readme.md", "hello 1", "commit 1");
        // new-branch 생성
        Git git = new Git(repository);
        String branchName = "new-branch";
        git.branchCreate()
                .setName(branchName)
                .setForce(true)
                .call();
        // new-branch 로 이동
        GitRepository.checkout(repository, branchName);
        // new-branch에 commit 2 추가
        String fileName = "hello.md";
        String content = "hello 2";
        newCommit(original, repository, fileName, content, "commit 2");

        // master 로 이동
        GitRepository.checkout(repository, "master");
        // When
        GitRepository.merge(repository, branchName);
        // Then
        assertThat(new String(getRawFile(repository, fileName))).isEqualTo(content);

        gitRepository.close();
        repository.close();
    }

    @Test
    public void push() throws IOException, GitAPIException {
        // Given
        Project original = createProject("keesun", "test");
        PullRequest pullRequest = createPullRequest(original);
        new GitRepository(original).create();
        Repository repository = GitRepository.buildCloneRepository(pullRequest);
        // master에 commit 1 추가
        String fileName = "readme.md";
        String content = "hello 1";
        newCommit(original, repository, fileName, content, "commit 1");

        // When
        String branchName = "master";
        GitRepository.push(repository, GitRepository.getGitDirectoryURL(original), branchName, branchName);

        // Then
        Repository originalRepo = new RepositoryBuilder()
                .setGitDir(new File(GitRepository.getGitDirectory(original)))
                .build();
        assertThat(new String(getRawFile(originalRepo, fileName))).isEqualTo(content);

        originalRepo.close();
    }

    @Test
    public void isFile() throws Exception {
        // Given
        String userName = "yobi";
        String projectName = "mytest";
        String wcPath = GitRepository.getRepoPrefix() + userName + "/" + projectName;
        String repoPath = wcPath + "/.git";
        String dirName = "dir";
        String fileName = "file";

        Repository repository = new RepositoryBuilder().setGitDir(new File(repoPath)).build();
        repository.create(false);
        Git git = new Git(repository);
        FileUtils.forceMkdir(new File(wcPath + "/" + dirName));
        FileUtils.touch(new File(wcPath + "/" + fileName));
        git.add().addFilepattern(dirName).call();
        git.add().addFilepattern(fileName).call();
        git.commit().setMessage("test").call();
        repository.close();

        // When
        GitRepository gitRepository = new GitRepository(userName, projectName + "/");

        // Then
        assertThat(gitRepository.isFile(dirName)).isEqualTo(false);
        assertThat(gitRepository.isFile(fileName)).isEqualTo(true);
        assertThat(gitRepository.isFile("not_exist_file")).isEqualTo(false);
        assertThat(gitRepository.isFile(fileName, "not_exist_branch")).isEqualTo(false);
    }

    @Test
    public void testGetAllBranches() throws IOException, GitAPIException {
        FakeApplication app = support.Helpers.makeTestApplication();
        Helpers.start(app);

        // Given
        String userName = "wansoon";
        String projectName = "test";
        Project project = createProject(userName, projectName);
        project.save();

        String email = "test@email.com";
        String wcPath = GitRepository.getRepoPrefix() + userName + "/" + "clone-" + projectName + ".git";
        String repoPath = wcPath + "/.git";
        String dirName = "dir";
        String fileName = "file";

        GitRepository gitRepository = new GitRepository(userName, projectName);
        gitRepository.create();

        Repository repository = new RepositoryBuilder().setGitDir(new File(repoPath)).build();
        repository.create(false);

        Git git = new Git(repository);
        FileUtils.forceMkdir(new File(wcPath + "/" + dirName));
        FileUtils.touch(new File(wcPath + "/" + fileName));
        git.add().addFilepattern(dirName).call();
        git.add().addFilepattern(fileName).call();
        git.commit().setMessage("test").setAuthor(userName, email).call();

        String branchName = "testBranch";
        git.branchCreate()
                .setName(branchName)
                .setForce(true)
                .call();

        git.push()
        .setRemote(GitRepository.getGitDirectoryURL(project))
        .setRefSpecs(new RefSpec(branchName + ":" + branchName))
        .setForce(true)
        .call();

        repository.close();

        // When
        List<GitBranch> gitBranches = gitRepository.getAllBranches();
        gitRepository.close();

        // Then
        assertThat(gitBranches.size()).isEqualTo(1);
        assertThat(gitBranches.get(0).getShortName()).isEqualTo(branchName);

        Helpers.stop(app);
    }

    private Project createProject(String owner, String name) {
        Project project = new Project();
        project.owner = owner;
        project.name = name;
        return project;
    }

    private PullRequest createPullRequest(Project project) {
        PullRequest pullRequest = new PullRequest();
        pullRequest.toProject = project;
        return pullRequest;
    }

    private void rm_rf(File file) {
        assert file != null;
        if (file.isDirectory()) {
            File[] list = file.listFiles();
            assert list != null;
            for(int i = 0; i < list.length; i++){
                rm_rf(list[i]);
            }
        }
        System.gc();
        file.delete();
    }

    private byte[] getRawFile(Repository repository, String path) throws IOException {
        RevTree tree = new RevWalk(repository).parseTree(repository.resolve(Constants.HEAD));
        TreeWalk treeWalk = TreeWalk.forPath(repository, path, tree);
        if (treeWalk.isSubtree()) {
            return null;
        } else {
            return repository.open(treeWalk.getObjectId(0)).getBytes();
        }
    }
}
