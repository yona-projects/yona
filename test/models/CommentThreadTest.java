/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Keesun Baik
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

import org.apache.shiro.util.ThreadState;
import org.junit.After;
import org.junit.Test;
import org.junit.Before;
import utils.JodaDateUtil;
import utils.AccessControl;
import models.enumeration.Operation;
import models.enumeration.ProjectScope;

import java.util.ArrayList;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;

/**
 * @author Keesun Baik
 */
public class CommentThreadTest extends ModelTest<CommentThread>  {
    private User admin;
    private User manager;
    private User member;
    private User author;
    private User nonmember;
    private User anonymous;
    private Project project;
    private SimpleCommentThread thread;
    private List<Long> threadIds;
    String commitId = "123123";

    @Before
    public void before() {
        project = Project.findByOwnerAndProjectName("yobi", "projectYobi");
        admin = User.findByLoginId("admin");
        manager = User.findByLoginId("yobi");
        member = User.findByLoginId("laziel");
        author = User.findByLoginId("nori");
        nonmember = User.findByLoginId("doortts");
        anonymous = new NullUser();

        thread = new SimpleCommentThread();
        thread.project = project;
        thread.author = new UserIdent(author);
        thread.state = SimpleCommentThread.ThreadState.OPEN;
        thread.save();

        assertThat(this.admin.isSiteManager()).describedAs("admin is Site Admin.").isTrue();
        assertThat(ProjectUser.isManager(manager.id, project.id)).describedAs("manager is a manager").isTrue();
        assertThat(ProjectUser.isManager(member.id, project.id)).describedAs("member is not a manager").isFalse();
        assertThat(ProjectUser.isMember(member.id, project.id)).describedAs("member is a member").isTrue();
        assertThat(ProjectUser.isMember(author.id, project.id)).describedAs("author is not a member").isFalse();
        assertThat(project.projectScope).isEqualTo(ProjectScope.PUBLIC);

        threadIds = addTestData(commitId);
    }

    @After
    public void after() {
        thread.delete();
        for(Long id : threadIds) {
            CommentThread.find.byId(id).delete();
        }
    }

    @Test
    public void findByCommitId() {
        // when
        List<CommentThread> threadList = CommentThread.findByCommitId(commitId);

        // then
        assertThat(threadList.size()).isEqualTo(2);
        assertThat(threadList.get(0).createdDate).isEqualTo(JodaDateUtil.before(2));
        assertThat(threadList.get(1).createdDate).isEqualTo(JodaDateUtil.before(3));
    }

    @Test
    public void findByCommitIdAndState() {
        // when and then
        List<CommentThread> threadList = CommentThread.findByCommitIdAndState(commitId, CommentThread.ThreadState.OPEN);
        assertThat(threadList.get(0).id).isEqualTo(threadIds.get(0));

        // when and then
        threadList = CommentThread.findByCommitIdAndState(commitId, CommentThread.ThreadState.CLOSED);
        assertThat(threadList.get(0).id).isEqualTo(threadIds.get(1));
    }


    /**
     * {@code commitId}를 가지는 {@link models.NonRangedCodeCommentThread} 한 개 저장.
     *  - state: OPEN
     *  - createDate: 3일전
     * {@code commitId}를 가지는 {@link models.CodeCommentThread} 한 개 저장.
     *  - state: CLOSED
     *  - path: "readme.md"
     *  - createdDate: 2일전
     * 123321을 커밋 ID로 가지는 {@link models.NonRangedCodeCommentThread} 한 개 저장.
     *  - state: OPEN
     *  - createdDate: 1일전
     *
     * @param commitId
     */
    private List<Long> addTestData(String commitId) {
        List<Long> threadIds = new ArrayList<>();

        NonRangedCodeCommentThread thread1 = new NonRangedCodeCommentThread();
        thread1.project = project;
        thread1.commitId = commitId;
        thread1.state = CommentThread.ThreadState.OPEN;
        thread1.createdDate = JodaDateUtil.before(3);
        thread1.save();
        threadIds.add(thread1.id);

        CodeCommentThread thread2 = new CodeCommentThread();
        thread2.project = project;
        thread2.commitId = commitId;
        thread2.state = CommentThread.ThreadState.CLOSED;
        CodeRange codeRange = new CodeRange();
        codeRange.path = "readme.md";
        codeRange.startColumn = 0;
        codeRange.startLine = 1;
        codeRange.startSide = CodeRange.Side.A;
        codeRange.endColumn = 100;
        codeRange.endLine = 10;
        codeRange.endSide = CodeRange.Side.B;
        thread2.codeRange = codeRange;
        thread2.createdDate = JodaDateUtil.before(2);
        thread2.save();
        threadIds.add(thread2.id);

        NonRangedCodeCommentThread thread3 = new NonRangedCodeCommentThread();
        thread3.project = project;
        thread3.commitId = "123321";
        thread3.state = CommentThread.ThreadState.OPEN;
        thread3.createdDate = JodaDateUtil.before(1);
        thread3.save();
        threadIds.add(thread3.id);

        return threadIds;
    }

    @Test
    public void reopenByAuthor() {
        assertThat(AccessControl.isAllowed(author, thread.asResource(), Operation.REOPEN)).isTrue();
    }

    @Test
    public void reopenBySiteAdmin() {
        assertThat(AccessControl.isAllowed(admin, thread.asResource(), Operation.REOPEN)).isTrue();
    }

    public void reopenByManager() {
        assertThat(AccessControl.isAllowed(member, thread.asResource(),
                Operation.REOPEN)).isTrue();
    }

    @Test
    public void reopenByMember() {
        assertThat(AccessControl.isAllowed(member, thread.asResource(),
                Operation.REOPEN)).isTrue();
    }

    @Test
    public void reopenByNonmember() {
        assertThat(AccessControl.isAllowed(nonmember, thread.asResource(),
                Operation.REOPEN)).isFalse();
    }

    @Test
    public void reopenByAnonymous() {
        assertThat(AccessControl.isAllowed(anonymous, thread.asResource(),
                Operation.REOPEN)).isFalse();
    }

    @Test
    public void closeByAuthor() {
        assertThat(AccessControl.isAllowed(author, thread.asResource(), Operation.CLOSE)).isTrue();
    }

    @Test
    public void closeBySiteAdmin() {
        assertThat(AccessControl.isAllowed(admin, thread.asResource(), Operation.CLOSE)).isTrue();
    }

    @Test
    public void closeByManager() {
        assertThat(AccessControl.isAllowed(member, thread.asResource(),
                Operation.CLOSE)).isTrue();
    }

    @Test
    public void closeByMember() {
        assertThat(AccessControl.isAllowed(member, thread.asResource(),
                Operation.CLOSE)).isTrue();
    }

    @Test
    public void closeByNonmember() {
        assertThat(AccessControl.isAllowed(nonmember, thread.asResource(),
                Operation.CLOSE)).isFalse();
    }

    @Test
    public void closeByAnonymous() {
        assertThat(AccessControl.isAllowed(anonymous, thread.asResource(),
                Operation.CLOSE)).isFalse();
    }
}
