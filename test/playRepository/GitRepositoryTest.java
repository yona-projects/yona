package playRepository;

import static org.fest.assertions.Assertions.assertThat;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.*;

public class GitRepositoryTest {
    @Before
    public void before() {
        GitRepository.setRepoPrefix("resources/test/repo/git/");
    }

    @After
    public void after() {
        rm_rf(new File(GitRepository.getRepoPrefix()));
    }

    @Test
    public void gitRepository() throws Exception {
        // Given
        String userName = "hobi";
        String projectName = "testProject";
        // When
        GitRepository repo = new GitRepository(userName, projectName);
        // Then
        assertThat(repo).isNotNull();
    }

    @Test
    public void create() throws Exception {
        // Given
        String userName = "hobi";
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
        rm_rf(new File(GitRepository.getRepoPrefix() + userName + "/" + projectName + ".git"));
    }

    @Test
    public void getPatch() throws IOException, NoFilepatternException, GitAPIException {
        // given
        String userName = "hobi";
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
        String userName = "hobi";
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

        out.write("hello 3");
        out.flush();
        git.add().addFilepattern("readme.txt").call();
        git.commit().setMessage("commit 3").call();

        GitRepository gitRepo = new GitRepository(userName, projectName + "/");

        List<Commit> history2 = gitRepo.getHistory(0, 2);
        List<Commit> history5 = gitRepo.getHistory(0, 5);

        // then
        assertThat(history2.size()).isEqualTo(2);
        assertThat(history2.get(0).getMessage()).isEqualTo("commit 3");
        assertThat(history2.get(1).getMessage()).isEqualTo("commit 2");

        assertThat(history5.size()).isEqualTo(3);
        assertThat(history5.get(0).getMessage()).isEqualTo("commit 3");
        assertThat(history5.get(1).getMessage()).isEqualTo("commit 2");
        assertThat(history5.get(2).getMessage()).isEqualTo("commit 1");
    }

    @Ignore
    @Test
    public void findFileInfo() throws Exception {
        // Given
        String userName = "hobi";
        String projectName = "testProject";
        GitRepository repo = new GitRepository(userName, projectName);
        // When
        repo.findFileInfo("readme");
        // Then
    }

    @Ignore
    @Test
    public void getRawFile() throws Exception {
        // Given
        String userName = "hobi";
        String projectName = "testProject";
        GitRepository repo = new GitRepository(userName, projectName);
        // When
        repo.getRawFile("readme");
        // Then
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
        file.delete();
    }
}
