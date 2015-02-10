/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Changseong Kim
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

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import playRepository.BareCommit;
import playRepository.GitRepository;
import utils.JodaDateUtil;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;
import static utils.FileUtil.rm_rf;

/**
 * @author Changsung Kim
 */
public class CodeCommentThreadTest extends ModelTest<CodeCommentThread>  {
    private static final String REPO_PREFIX = "resources/test/repo/git/";
    private Project project;
    private ObjectId baseCommit;
    private ObjectId firstCommit;
    private ObjectId secondCommit;
    private ObjectId thirdCommit;

    @Before
    public void before() throws IOException, GitAPIException {
        project = Project.findByOwnerAndProjectName("yobi", "projectYobi");
    }

    @After
    public void after() throws Exception {
        rm_rf(new File(REPO_PREFIX));
    }

    private void addTestRepository() throws IOException, GitAPIException {
        GitRepository.setRepoPrefix(REPO_PREFIX);

        // given
        GitRepository.buildGitRepository(project).create(true);
        BareCommit committer = new BareCommit(project, User.anonymous);

        // when
        baseCommit = committer.commitTextFile("a.txt", "read me", "base commit");
        firstCommit = committer.commitTextFile("a.txt", "hello", "commit 1");
        secondCommit = committer.commitTextFile("b.txt", "world", "commit 2");
        thirdCommit = committer.commitTextFile("a.txt", "HELLO", "commit 3");
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

        List<CodeCommentThread> threads = pullRequest.getCodeCommentThreadsForChanges(null);

        assertThat(threads).describedAs("Exclude simple threads").excludes(simpleThread);
        assertThat(threads).describedAs("Exclude threads on commit").excludes(threadOnCommit);
        assertThat(threads).describedAs("Exclude outdated threads").excludes(outdatedThread);
        assertThat(threads).describedAs("Contain threads on changes").contains(threadOnChanges);
    }
}
