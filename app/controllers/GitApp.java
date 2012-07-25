package controllers;

import java.io.*;

import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.transport.RefAdvertiser.PacketLineOutRefAdvertiser;

import play.Logger;
import play.mvc.*;

public class GitApp extends Controller {
    public static final String REPO_PREFIX = "repo/git/";

    public static Result post(String path) {
        Logger.debug("GitApp.post : " + request().toString());
        return TODO;
    }

    public static Result get(String path) {
        Logger.debug("GitApp.get : " + request().toString());
        return TODO;
    }

    public static Result advertise(String repoPath, String service) throws IOException {
        Logger.debug("GitApp.advertise : " + request().toString());

        response().setContentType("application/x-" + service + "-advertisement");

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        PacketLineOut out = new PacketLineOut(byteArrayOutputStream);
        out.writeString("# service=" + service + "\n");
        out.end();

        Repository repository = new RepositoryBuilder()
                .setGitDir(new File(REPO_PREFIX + repoPath)).build();
        if (service.equals("git-upload-pack")) {
            UploadPack uploadPack = new UploadPack(repository);

            uploadPack.setBiDirectionalPipe(false);
            uploadPack.sendAdvertisedRefs(new PacketLineOutRefAdvertiser(out));
        } else if (service.equals("git-receive-pack")) {
            ReceivePack receivePack = new ReceivePack(repository);

            receivePack.sendAdvertisedRefs(new PacketLineOutRefAdvertiser(out));
        } else {
            return forbidden("Unsuppoted service: '" + service + "'");
        }

        return ok(byteArrayOutputStream.toByteArray());
    }

    public static Result serviceRpc(String repoPath, String service) throws IOException {
        Logger.debug("GitApp.advertise : " + request().toString());

        response().setContentType("application/x-" + service + "-result");

        Repository repository = new RepositoryBuilder().setGitDir(new File(REPO_PREFIX + repoPath))
                .build();

        // receivePack.setEchoCommandFailures(true);//git버전에 따라서 불린값 설정필요.

        // FIXME 스트림으로..
        byte[] buf = request().body().asRaw().asBytes();
        ByteArrayInputStream in = new ByteArrayInputStream(buf);

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        if (service.equals("git-upload-pack")) {
            UploadPack uploadPack = new UploadPack(repository);

            uploadPack.setBiDirectionalPipe(false);
            uploadPack.upload(in, out, null);

        } else if (service.equals("git-receive-pack")) {
            ReceivePack receivePack = new ReceivePack(repository);

            receivePack.setBiDirectionalPipe(false);
            receivePack.receive(in, out, null);
        } else {
            return forbidden("Unsuppoted service: '" + service + "'");
        }
        out.close();
        return ok(out.toByteArray());
    }

}
