package controllers;

import git.GitRepository;

import java.io.*;

import models.Project;

import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.*;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.transport.RefAdvertiser.PacketLineOutRefAdvertiser;
import org.eclipse.jgit.treewalk.TreeWalk;

import play.Logger;
import play.mvc.*;
import views.html.code.gitView;

public class GitApp extends Controller {
    public static final String REPO_PREFIX = "repo/git/";

    /**
     * 클라이언트로 부터 요청을 받아 레파지토리에 push pull 하는 함수.
     * @param projectName       서비스를 받아야할 프로젝트 이름
     * @param service           받는 서비스. 
     * @return                  글라이언트에게 줄 응답 몸통
     * @throws IOException
     */
    public static Result advertise(String ownerName, String projectName, String service) throws IOException {
        Logger.debug("GitApp.advertise : " + request().toString());

        response().setContentType("application/x-" + service + "-advertisement");

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        PacketLineOut out = new PacketLineOut(byteArrayOutputStream);
        out.writeString("# service=" + service + "\n");
        out.end();

        Repository repository = GitRepository.getRepository(ownerName, projectName);
        if (service.equals("git-upload-pack")) {
            UploadPack uploadPack = new UploadPack(repository);

            uploadPack.setBiDirectionalPipe(false);
            uploadPack.sendAdvertisedRefs(new PacketLineOutRefAdvertiser(out));
        } else if (service.equals("git-receive-pack")) {
            ReceivePack receivePack = new ReceivePack(repository);

            receivePack.sendAdvertisedRefs(new PacketLineOutRefAdvertiser(out));
        } else {
            return forbidden("Unsupported service: '" + service + "'");
        }

        return ok(byteArrayOutputStream.toByteArray());
    }

    /**
     * 서비스의 사용가능 상태를 확인
     * @param projectName   해당 레파지토리를 가지고 있는 프로젝트명
     * @param service       사용하려는 서비스 이름. 두가지 밖에 없음.
     * @return              클라이언트에게 줄 응답
     * @throws IOException
     */
    public static Result serviceRpc(String ownerName, String projectName, String service) throws IOException {
        Logger.debug("GitApp.advertise : " + request().toString());

        response().setContentType("application/x-" + service + "-result");

        Repository repository = GitRepository.getRepository(ownerName, projectName);

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
            return forbidden("Unsupported service: '" + service + "'");
        }
        out.close();
        return ok(out.toByteArray());
    }

    /**
     * Raw포맷으로 소스를 보여주는 코드 
     * TODO gitRepository로 위치를 옯겨야 한다.
     * @param ownerName
     * @param projectName
     * @param path
     * @return
     * @throws IOException
     */
    public static Result viewCode(String ownerName, String projectName, String path) throws IOException {
        if (path.equals("")) {
            return ok(gitView.render("http://localhost:9000/" + projectName,
                    Project.findByName(projectName)));
        } else {
            Repository repository = GitRepository.getRepository(ownerName, projectName);
            RevTree tree = new RevWalk(repository).parseTree(repository.resolve("HEAD"));
            TreeWalk treeWalk = TreeWalk.forPath(repository, path, tree);
            if (treeWalk.isSubtree())
                return badRequest();
            else
                return ok(repository.open(treeWalk.getObjectId(0)).getBytes());
        }

    }
}
