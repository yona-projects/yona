/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
 * http://yobi.io
 *
 * @Author Yi EungJun
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
package mailbox;

import com.sun.mail.imap.IMAPMessage;
import models.*;
import models.enumeration.State;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import play.test.FakeApplication;
import play.test.Helpers;
import utils.JodaDateUtil;

import javax.mail.Message;
import javax.mail.internet.InternetAddress;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CreationViaEmailTest {
    private static FakeApplication app;
    private User member;
    private Project project;

    @BeforeClass
    public static void startApp() {
        app = support.Helpers.makeTestApplication();
        Helpers.start(app);
    }

    @AfterClass
    public static void stopApp() {
        Helpers.stop(app);
    }

    @Before
    public void before() {
        project = Project.findByOwnerAndProjectName("yobi", "projectYobi");
        member = User.findByLoginId("laziel");

    }

    public static Content createTestContent(String body, String type) {
        Content content = new Content();
        content.body = body;
        content.type = type;
        return content;
    }

    @Test
    public void saveIssue() throws Exception {
        // Given
        String title = "title";
        Content content = createTestContent("body", "text/plain");
        InternetAddress[] recipients = new InternetAddress[]{
                new InternetAddress("foo@mail.com")
        };

        // When
        Issue issue = CreationViaEmail.saveIssue(title, project, member, content,
                "<message-id@domain>", recipients);
        issue.refresh();

        // Then
        assertThat(issue.authorId).describedAs("author").isEqualTo(member.id);
        assertThat(issue.title).describedAs("title").isEqualTo(title);
        assertThat(issue.body).describedAs("body").isEqualTo(content.body);
        assertThat(issue.project).describedAs("project").isEqualTo(project);
    }

    @Test
    public void saveComment() throws Exception {
        // Given
        IMAPMessage message = mock(IMAPMessage.class);
        when(message.getAllRecipients()).thenReturn(new InternetAddress[]{
                new InternetAddress("foo@mail.com")
        });

        when(message.getFrom()).thenReturn(new InternetAddress[]{
                new InternetAddress(member.email)
        });
        when(message.getContentType()).thenReturn("text/plain");
        when(message.isMimeType("text/*")).thenReturn(true);
        when(message.getContent()).thenReturn("body");
        when(message.getMessageID()).thenReturn("<message-id-2@domain>");

        Issue issue = new Issue();
        issue.setProject(project);
        issue.setTitle("hello");
        issue.setBody("world");
        issue.setAuthor(member);
        issue.state = State.OPEN;
        issue.save();

        // When
        Comment comment = CreationViaEmail.saveComment(message, issue.asResource());
        comment.refresh();

        // Then
        assertThat(comment.authorId)
                .describedAs("authorId")
                .isEqualTo(member.id);
        assertThat(comment.contents)
                .describedAs("contents")
                .isEqualTo((String) message.getContent());
        assertThat(comment.getParent().id)
                .describedAs("parent")
                .isEqualTo(issue.id);
    }

    @Test
    public void saveReviewComment() throws Exception {
        // Given
        Content content = createTestContent("body", "text/plain");
        InternetAddress[] recipients = new InternetAddress[]{
                new InternetAddress("foo@mail.com")
        };
        NonRangedCodeCommentThread thread = new NonRangedCodeCommentThread();
        thread.project = project;
        thread.commitId = "123321";
        thread.state = CommentThread.ThreadState.OPEN;
        thread.createdDate = JodaDateUtil.before(1);
        thread.save();

        // When
        ReviewComment comment = CreationViaEmail.saveReviewComment(
                thread.asResource(),
                member,
                content,
                "<message-id-3@domain>",
                recipients);

        // Then
        assertThat(comment.author.id)
                .describedAs("author.id")
                .isEqualTo(member.id);
        assertThat(comment.getContents())
                .describedAs("contents")
                .isEqualTo(content.body);
        assertThat(comment.thread.id)
                .describedAs("thread")
                .isEqualTo(thread.id);
    }
}
