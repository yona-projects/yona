package models;

import static org.fest.assertions.Assertions.assertThat;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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

    public File createFileWithContents(String name, byte[] contents) throws IOException, FileNotFoundException {
        File tempFile = java.io.File.createTempFile(name, null);
        tempFile.deleteOnExit();
        FileOutputStream os = new FileOutputStream(tempFile);
        os.write(contents);
        os.close();
        return tempFile;
    }
}
