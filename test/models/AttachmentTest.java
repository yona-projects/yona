/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
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
package models;

import static org.fest.assertions.Assertions.assertThat;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class AttachmentTest extends ModelTest<Attachment> {

    @Before
    public void before() {
        Attachment.setUploadDirectory("resources/test/uploads");
    }

    @Test
    public void testSaveInUserTemporaryArea() throws IOException, NoSuchAlgorithmException {
        // Given
        File file = createFileWithContents("foo.txt", "Hello".getBytes());
        Long userId = 1L;

        // When
        Attachment attach = new Attachment();
        attach.store(file, "bar.txt", User.find.byId(userId).asResource());

        FileInputStream is = new FileInputStream(attach.getFile());
        byte[] b = new byte[1024];
        int length = is.read(b);
        is.close();

        // Then
        assertThat(attach.name).isEqualTo("bar.txt");
        assertThat(attach.mimeType).isEqualTo("text/plain; charset=UTF-8");
        assertThat(new String(b, 0, length)).isEqualTo(new String("Hello"));
    }

    @Test
    public void testMoveOnlySelected() throws Exception {
        // Given
        File foo = createFileWithContents("foo.txt", "Hello".getBytes());
        File bar = createFileWithContents("bar.html", "<p>Bye</p>".getBytes());
        Issue issue = Issue.finder.byId(1L);
        User user = User.findByLoginId("doortts");

        Attachment thisOne = new Attachment();
        Attachment notThis = new Attachment();
        thisOne.store(foo, "foo.txt", user.asResource());
        notThis.store(bar, "bar.html", user.asResource());
        String[] selectedFileIds = {thisOne.id + ""};

        // When
        Attachment.moveOnlySelected(user.asResource(), issue.asResource(), selectedFileIds);

        // Then
        List<Attachment> attachedFiles = Attachment.findByContainer(issue.asResource());
        List<Attachment> unattachedFiles = Attachment.findByContainer(user.asResource());

        assertThat(attachedFiles.size()).isEqualTo(1);
        assertThat(unattachedFiles.size()).isEqualTo(1);
    }

    public void testAttachFiles() throws IOException, NoSuchAlgorithmException {
        // Given
        File foo = createFileWithContents("foo.txt", "Hello".getBytes());
        File bar = createFileWithContents("bar.html", "<p>Bye</p>".getBytes());
        Issue issue = Issue.finder.byId(1L);
        Long userId = 1L;

        // When
        new Attachment().store(foo, "foo.txt", User.find.byId(userId).asResource());
        new Attachment().store(bar, "bar.html", User.find.byId(userId).asResource());
        Attachment.moveAll(User.find.byId(userId).asResource(), issue.asResource());
        List<Attachment> attachedFiles = Attachment.findByContainer(issue.asResource());

        // Then
        assertThat(attachedFiles.size()).isEqualTo(2);
        assertThat(attachedFiles.get(0).name).isEqualTo("foo.txt");
        assertThat(attachedFiles.get(0).mimeType).isEqualTo("text/plain");
        assertThat(attachedFiles.get(1).name).isEqualTo("bar.html");
        assertThat(attachedFiles.get(1).mimeType).isEqualTo("text/html");
    }

    public File createFileWithContents(String name, byte[] contents) throws IOException {
        File tempFile = java.io.File.createTempFile(name, null);
        tempFile.deleteOnExit();
        FileOutputStream os = new FileOutputStream(tempFile);
        os.write(contents);
        os.close();
        return tempFile;
    }
}
