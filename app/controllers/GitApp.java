package controllers;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.nio.ByteBuffer;

import org.eclipse.jgit.http.server.resolver.DefaultUploadPackFactory;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.ReflogReader;
import org.eclipse.jgit.transport.PacketLineOut;
import org.eclipse.jgit.transport.UploadPack;

import play.Logger;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Results.Chunks.Out;

public class GitApp extends Controller {
    public static Result post(String path) {
        Logger.info(request().headers().toString());
        return TODO;
    }

    public static Result get(String path) {
        return TODO;

    }

    public static Result infoRefs(String repoPath) throws IOException {
        response().setContentType("application/x-git-upload-pack-advertisement");

        PacketLineOut out = new PacketLineOut(new ByteArrayOutputStream());
        out.writeString("# service=git-upload-pack\n");
        out.end();

        Repository repository = new RepositoryBuilder().setGitDir(new File(repoPath)).build();
        UploadPack uploadPack = new UploadPack(repository);

        return ok();

    }

}
