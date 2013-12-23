package models;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import playRepository.GitRepository;
import utils.JodaDateUtil;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;
import static utils.FileUtil.rm_rf;

/**
 * @author Changsung Kim
 */
public class CodeCommentThreadTest extends ModelTest<CodeCommentThread>  {
    private static final String MERGING_REPO_PREFIX = "resources/test/repo/git-merging/";
    private static final String REPO_PREFIX = "resources/test/repo/git/";
    private Project project;
    private RevCommit baseCommit;
    private RevCommit firstCommit;
    private RevCommit secondCommit;
    private RevCommit thirdCommit;

    @Before
    public void before() throws IOException, GitAPIException {
        project = Project.findByOwnerAndProjectName("yobi", "projectYobi");
    }

    @After
    public void after() {
        rm_rf(new File(REPO_PREFIX));
        rm_rf(new File(MERGING_REPO_PREFIX));
    }

    private void addTestRepository() throws IOException, GitAPIException {
        GitRepository.setRepoPrefix(REPO_PREFIX);
        GitRepository.setRepoForMergingPrefix(MERGING_REPO_PREFIX);

        // given
        String wcPath
                = GitRepository.getRepoForMergingPrefix() + project.owner + "/" + project.name +
                ".git";
        File repoDir = new File(
                GitRepository.getDirectoryForMerging(project.owner, project.name) + "/.git");
        Repository repo = new RepositoryBuilder().setGitDir(repoDir).build();
        repo.create(false);

        // when
        baseCommit = support.Git.commit(repo, wcPath, "a.txt", "read me", "base commit");
        firstCommit = support.Git.commit(repo, wcPath, "a.txt", "hello", "commit 1");
        secondCommit = support.Git.commit(repo, wcPath, "b.txt", "world", "commit 2");
        thirdCommit = support.Git.commit(repo, wcPath, "a.txt", "HELLO", "commit 3");
    }

    @Test
    public void checkDefaultValueForPrevCommitId() {
        // given
        CodeCommentThread codeCommentThread = new CodeCommentThread();
        codeCommentThread.createdDate = JodaDateUtil.now();
        codeCommentThread.commitId = "1234568";
        codeCommentThread.project = project;
        codeCommentThread.save();
        Long id = codeCommentThread.id;

        // when
        CodeCommentThread savedCodeCommentThread = CodeCommentThread.find.byId(id);

        // then
        assertThat(savedCodeCommentThread.prevCommitId).isEqualTo(StringUtils.EMPTY);
    }

    @Test
    public void getCodeCommentThreadForPullRequestChanges() throws IOException, GitAPIException {
        addTestRepository();

        PullRequest pullRequest = new PullRequest();
        pullRequest.mergedCommitIdFrom = baseCommit.getName();
        pullRequest.mergedCommitIdTo = thirdCommit.getName();
        pullRequest.toProject = project;

        CodeCommentThread threadOnCommit = new CodeCommentThread();
        threadOnCommit.prevCommitId = StringUtils.EMPTY;
        threadOnCommit.commitId = secondCommit.getName();
        threadOnCommit.codeRange = new CodeRange();
        threadOnCommit.codeRange.path = "b.txt";

        CodeCommentThread outdatedThread = new CodeCommentThread();
        outdatedThread.prevCommitId = baseCommit.getName();
        outdatedThread.commitId = secondCommit.getName();
        outdatedThread.codeRange = new CodeRange();
        outdatedThread.codeRange.path = "a.txt";

        CodeCommentThread threadOnChanges = new CodeCommentThread();
        threadOnChanges.prevCommitId = baseCommit.getName();
        threadOnChanges.commitId = secondCommit.getName();
        threadOnChanges.codeRange = new CodeRange();
        threadOnChanges.codeRange.path = "b.txt";

        SimpleCommentThread simpleThread = new SimpleCommentThread();
        pullRequest.commentThreads.add(simpleThread);
        pullRequest.commentThreads.add(threadOnCommit);
        pullRequest.commentThreads.add(outdatedThread);
        pullRequest.commentThreads.add(threadOnChanges);

        List<CodeCommentThread> threads = pullRequest.getCodeCommentThreadsForChanges();

        assertThat(threads).describedAs("Exclude simple threads").excludes(simpleThread);
        assertThat(threads).describedAs("Exclude threads on commit").excludes(threadOnCommit);
        assertThat(threads).describedAs("Exclude outdated threads").excludes(outdatedThread);
        assertThat(threads).describedAs("Contain threads on changes").contains(threadOnChanges);
    }
}
