package controllers;

import java.io.*;
import java.nio.ByteBuffer;

import org.eclipse.jgit.http.server.resolver.DefaultUploadPackFactory;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.ReflogReader;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.transport.RefAdvertiser.PacketLineOutRefAdvertiser;

import play.Logger;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Results.Chunks.Out;

public class GitApp extends Controller {
    public static Result post(String path) {
        Logger.info("post : " + request().toString());
        return TODO;
    }

    public static Result get(String path) {
        Logger.info("get:" + request().toString());
        return TODO;
    }

    public static Result infoRefs(String repoPath, String Service) throws IOException {
        // uploadPackServlet.advertise
        if (Service.equals("git-upload-pack")) {
            return uploadPackAdvertise(repoPath);
        } else {
            return receivePackAdvertise(repoPath);
        }
    }

    private static Result uploadPackAdvertise(final String repoPath) throws IOException, ServiceMayNotContinueException {
        response().setContentType("application/x-git-upload-pack-advertisement");

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        PacketLineOut out = new PacketLineOut(byteArrayOutputStream);
        out.writeString("# service=git-upload-pack\n");
        out.end();

        Repository repository = new RepositoryBuilder().setGitDir(new File(repoPath)).build();
        UploadPack uploadPack = new UploadPack(repository);

        uploadPack.setBiDirectionalPipe(false);
        uploadPack.sendAdvertisedRefs(new PacketLineOutRefAdvertiser(out));

        Logger.info(request().toString());
        Logger.info(byteArrayOutputStream.toByteArray().toString());

        return ok(byteArrayOutputStream.toByteArray());
    }

    private static Result receivePackAdvertise(String repoPath) throws IOException {
        response().setContentType("application/x-git-receive-pack-advertisement");

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        PacketLineOut out = new PacketLineOut(byteArrayOutputStream);
        out.writeString("# service=git-receive-pack\n");
        out.end();

        Repository repository = new RepositoryBuilder().setGitDir(new File(repoPath)).build();
        ReceivePack receivePack = new ReceivePack(repository);

        receivePack.sendAdvertisedRefs(new PacketLineOutRefAdvertiser(out));

        Logger.info(request().toString());
        Logger.info(byteArrayOutputStream.toByteArray().toString());

        return ok(byteArrayOutputStream.toByteArray());
    }

    public static Result postUploadPack(String repoPath) throws IOException {
        // jgit.uploadPackServlet.doPost();
        // http-backend.c service_rpc();
        response().setContentType("application/x-git-upload-pack-result");

        Repository repository = new RepositoryBuilder().setGitDir(new File(repoPath)).build();
        UploadPack uploadPack = new UploadPack(repository);

        uploadPack.setBiDirectionalPipe(false);

        // FIXME 스트림으로..
        byte[] buf = request().body().asRaw().asBytes();
        ByteArrayInputStream in = new ByteArrayInputStream(buf);

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        uploadPack.upload(in, out, null);
        out.close();

        return ok(out.toByteArray());
    }

    public static Result postReceivePack(String repoPath) throws IOException {
        // jgit.uploadPackServlet.doPost();
        // http-backend.c service_rpc();
        response().setContentType("application/x-git-receive-pack-result");

        Repository repository = new RepositoryBuilder().setGitDir(new File(repoPath)).build();
        ReceivePack receivePack = new ReceivePack(repository);

        receivePack.setBiDirectionalPipe(false);
        // receivePack.setEchoCommandFailures(true);//git버전에 따라서 불린값 설정필요.

        // FIXME 스트림으로..
        byte[] buf = request().body().asRaw().asBytes();
        ByteArrayInputStream in = new ByteArrayInputStream(buf);

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        receivePack.receive(in, out, null);
        out.close();

        return ok(out.toByteArray());
    }

}
