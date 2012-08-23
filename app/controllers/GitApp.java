package controllers;


import java.io.*;

import models.Project;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.transport.RefAdvertiser.PacketLineOutRefAdvertiser;

import play.Logger;
import play.mvc.*;
import repository.RepositoryFactory;

public class GitApp extends Controller {

    /**
     * 클라이언트로 부터 요청을 받아 레파지토리에 push pull 하는 함수.
     * @param projectName       서비스를 받아야할 프로젝트 이름
     * @param service           받는 서비스. 
     * @return                  글라이언트에게 줄 응답 몸통
     * @throws Exception 
     */
    public static Result advertise(String userName, String projectName, String service) throws Exception {
        Project project = ProjectApp.getProject(userName, projectName);
        Logger.debug("GitApp.advertise : " + request().toString());

        response().setContentType("application/x-" + service + "-advertisement");

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        PacketLineOut out = new PacketLineOut(byteArrayOutputStream);
        out.writeString("# service=" + service + "\n");
        out.end();

        Repository repository = (Repository) RepositoryFactory.getRepository(project).getCore();
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
     * @throws Exception 
     */
    public static Result serviceRpc(String userName, String projectName, String service) throws Exception {
        Project project = ProjectApp.getProject(userName, projectName);
        Logger.debug("GitApp.advertise : " + request().toString());

        response().setContentType("application/x-" + service + "-result");

        Repository repository =  (Repository) RepositoryFactory.getRepository(project).getCore();

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
    
    public static void createRepository(String userName, String projectName) throws Exception{
        Project project = ProjectApp.getProject(userName, projectName);
        RepositoryFactory.getRepository(project).create();
    }
    
    public static String getURL(String ownerName, String projectName) {
    	return "http://localhost:9000/" + ownerName + "/" + projectName;
    }

    public static Result ajaxRequest(String userName, String projectName, String path) throws Exception {
        Project project = ProjectApp.getProject(userName, projectName);
		Logger.info(project.vcs);
        return ok(RepositoryFactory.getRepository(project).findFileInfo(path));
    }

    /**
     * Raw 소스를 보여주는 코드
     * @param userName
     * @param projectName
     * @param path
     * @return
     * @throws Exception 
     */
    public static Result showRawCode(String userName, String projectName, String path) throws Exception {
        Project project = ProjectApp.getProject(userName, projectName);
        return ok(RepositoryFactory.getRepository(project).getRawFile(path));
        
    }
}
