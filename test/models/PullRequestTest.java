/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Keesun Baik, kjkmadness
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
package models;

import org.apache.commons.lang3.time.DateUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNException;
import play.test.Helpers;
import playRepository.FileDiff;
import playRepository.GitRepository;
import playRepository.PlayRepository;
import playRepository.RepositoryService;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.fest.assertions.Assertions.assertThat;
import static utils.FileUtil.rm_rf;

public class PullRequestTest extends ModelTest<PullRequest> {
    private static final String REPO_PREFIX = "resources/test/repo/git/";
    private static final String LOCAL_REPO_PREFIX = "resources/test/local-repo/git/";

    private RevCommit baseCommit;
    private RevCommit firstCommit;
    private RevCommit secondCommit;
    private PullRequest pullRequest;
    private Project forkedProject;

    @Before
    public void initRepositories() throws Exception {
        GitRepository.setRepoPrefix(REPO_PREFIX);

        app = support.Helpers.makeTestApplication();
        Helpers.start(app);

        Project project = Project.findByOwnerAndProjectName("yobi", "projectYobi");
        forkedProject = Project.findByOwnerAndProjectName("yobi", "projectYobi-1");

        // 1. projectYobi 저장소를 만듦
        RepositoryService.createRepository(project);

        // 2. projectYobi 저장소에 커밋 하나
        {
            String localRepoPath = LOCAL_REPO_PREFIX + project.name;
            Git git = Git.cloneRepository()
                    .setURI(GitRepository.getGitDirectoryURL(project))
                    .setDirectory(new File(localRepoPath))
                    .call();
            Repository repo = git.getRepository();
            baseCommit = support.Git.commit(repo, "test.txt",
                    "apple\nbanana\ncat\n", "commit 1");
            git.push().setRefSpecs(new RefSpec("+refs/heads/master:refs/heads/master")).call();
        }

        // 3. 포크된 프로젝트 클론된 저장소 만들기
        GitRepository.cloneLocalRepository(project, forkedProject);

        // 4. 포크된 저장소에 새 브랜치를 만들어 그 브랜치에 커밋을 두개 하고
        {
            String localRepoPath = LOCAL_REPO_PREFIX + forkedProject.name;
            Git git = Git.cloneRepository()
                    .setURI(GitRepository.getGitDirectoryURL(forkedProject))
                    .setDirectory(new File(localRepoPath))
                    .call();
            git.branchCreate().setName("fix/1").call();
            git.checkout().setName("fix/1").call();
            Repository repo = git.getRepository();
            assertThat(repo.isBare()).describedAs("projectYobi-1 must be non-bare").isFalse();
            firstCommit = support.Git.commit(repo,
                    "test.txt", "apple\nbanana\ncorn\n", "commit 1");
            secondCommit = support.Git.commit(repo,
                    "test.txt", "apple\nbanana\ncake\n", "commit 2");
            git.push().setRefSpecs(new RefSpec("+refs/heads/fix/1:refs/heads/fix/1")).call();
        }

        // 5. 그 브랜치로 projectYobi에 pullrequest를 보낸다.
        pullRequest = PullRequest.createNewPullRequest(forkedProject, project, "refs/heads/fix/1",
                "refs/heads/master");
        pullRequest.save();

        // 6. attempt merge
        boolean isConflict = pullRequest.updateMerge().conflicts();

        assertThat(isConflict).isFalse();
    }

    @After
    public void after() throws Exception {
        rm_rf(new File(REPO_PREFIX));
        rm_rf(new File(LOCAL_REPO_PREFIX));
        Helpers.stop(app);
    }

    @Test
    public void addIssueEvent() {
        // Given
        Pattern issuePattern = Pattern.compile("#\\d+");

        // When
        Matcher m = issuePattern.matcher("blah blah #12, sdl #13 sldkfjsd");

        // Then
        List<String> numberTexts = new ArrayList<>();
        while(m.find()) {
            numberTexts.add(m.group());
        }
        assertThat(numberTexts.size()).isEqualTo(2);
    }

    @Test
    public void getWatchers_default() {
        // Given
        PullRequest pullRequest = PullRequest.findById(1L);

        // When
        Set<User> watchers = pullRequest.getWatchers();

        // Then
        assertThat(watchers).containsOnly(pullRequest.contributor);
    }

    @Test
    public void getWatchers_with_comments() {
        // Given
        PullRequest pullRequest = PullRequest.findById(1L);
        User commentUser = getTestUser();
        ReviewComment comment = new ReviewComment();
        comment.thread = new NonRangedCodeCommentThread();
        comment.thread.pullRequest = pullRequest;
        comment.thread.project = pullRequest.toProject;
        comment.author = new UserIdent(commentUser);
        comment.save();

        // When
        Set<User> watchers = pullRequest.getWatchers();

        // Then
        assertThat(watchers).containsOnly(pullRequest.contributor, commentUser);
    }

    @Test
    public void getWatchers_with_project_watcher() {
        // Given
        PullRequest pullRequest = PullRequest.findById(1L);
        User projectWatcher = getTestUser();
        projectWatcher.addWatching(pullRequest.toProject);

        // When
        Set<User> watchers = pullRequest.getWatchers();

        // Then
        assertThat(watchers).containsOnly(pullRequest.contributor, projectWatcher);
    }

    @Test
    public void getWatchers_explicitly_watch_unwatch() {
        // Given
        PullRequest pullRequest = PullRequest.findById(1L);
        User watcher = getTestUser(2L);
        Watch.watch(watcher, pullRequest.asResource());

        User unwatcher = getTestUser(3L);
        unwatcher.addWatching(pullRequest.toProject);
        Watch.unwatch(unwatcher, pullRequest.asResource());

        // When
        Set<User> watchers = pullRequest.getWatchers();

        // Then
        assertThat(watchers).containsOnly(pullRequest.contributor, watcher);
    }

    @Test
    public void getWatchers_private_project() {
        // Given
        PullRequest pullRequest = PullRequest.findById(2L);
        User watcher = getTestUser();
        Watch.watch(watcher, pullRequest.asResource());

        // When
        Set<User> watchers = pullRequest.getWatchers();

        // Then
        assertThat(watchers).containsOnly(pullRequest.contributor);
    }

    @Test
    public void testReviewPoint() {
        // Given
        PullRequest pullRequest = PullRequest.findById(1L);
        Project project = pullRequest.toProject;

        // When & Then
        assertThat(pullRequest.getRequiredReviewerCount()).isEqualTo(project.defaultReviewerCount);
        assertThat(pullRequest.getRequiredReviewerCount()).isEqualTo(1);
        assertThat(pullRequest.isReviewed()).isFalse();

        // When & Then
        pullRequest.addReviewer(getTestUser());
        assertThat(pullRequest.isReviewed()).isTrue();

        // When & Then
        pullRequest.clearReviewers();
        assertThat(pullRequest.reviewers.size()).isEqualTo(0);
        assertThat(pullRequest.isReviewed()).isFalse();
        assertThat(pullRequest.getLackingReviewerCount()).isEqualTo(1);
    }

    @Test
    public void testReviewer() {
        // Given
        PullRequest pullRequest = PullRequest.findById(2L);
        User user = getTestUser();
        assertThat(pullRequest.reviewers.size()).isEqualTo(0);

        // When & Then
        pullRequest.addReviewer(user);
        assertThat(pullRequest.reviewers.size()).isEqualTo(1);
        assertThat(pullRequest.isReviewedBy(user)).isTrue();

        // When & Then
        pullRequest.removeReviewer(user);
        assertThat(pullRequest.reviewers.size()).isEqualTo(0);
        assertThat(pullRequest.isReviewedBy(user)).isFalse();
    }

    private PullRequestEvent createPullRequestEvent(String str) throws ParseException {
        PullRequestEvent event = new PullRequestEvent();
        event.created = DateUtils.parseDate(str, "yyyy-MM-dd");
        return event;
    }

    @Test
    public void getDiff1() throws IOException {
        // given
        List<FileDiff> expected = new ArrayList<>();
        expected.add(new FileDiff());
        expected.get(0).commitA = baseCommit.getName();
        expected.get(0).commitB = firstCommit.getName();
        expected.get(0).a = new RawText("apple\nbanana\ncat\n".getBytes());
        expected.get(0).b = new RawText("apple\nbanana\ncorn\n".getBytes());
        expected.get(0).pathA = "test.txt";
        expected.get(0).pathB = "test.txt";
        expected.get(0).editList
                = DiffAlgorithm.getAlgorithm(DiffAlgorithm.SupportedAlgorithm.HISTOGRAM)
                .diff(RawTextComparator.DEFAULT, expected.get(0).a, expected.get(0).b);
        expected.get(0).changeType = DiffEntry.ChangeType.MODIFY;

        // when
        List<FileDiff> diff = pullRequest.getDiff(firstCommit.getName());

        // then
        assertThat(diff).isEqualTo(expected);
    }

    @Test
    public void getDiff2() throws IOException, ServletException, GitAPIException, SVNException {
        // given
        PlayRepository repo = RepositoryService.getRepository(forkedProject);
        String commitId = secondCommit.getName();
        List<FileDiff> expected = repo.getDiff(commitId);

        // when
        List<FileDiff> diff = pullRequest.getDiff(secondCommit.getName());

        // then
        assertThat(diff).isEqualTo(expected);
    }
}
