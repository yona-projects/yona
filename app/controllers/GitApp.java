package controllers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.servlet.ServletException;

import models.Project;
import models.User;
import models.enumeration.Operation;
import models.enumeration.Resource;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PacketLineOut;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.RefAdvertiser.PacketLineOutRefAdvertiser;
import org.eclipse.jgit.transport.ServiceMayNotContinueException;
import org.eclipse.jgit.transport.UploadPack;

import play.Logger;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.With;
import playRepository.RepositoryService;
import utils.AccessControl;
import utils.BasicAuthAction;

public class GitApp extends Controller {

    private static void service(String userName, String projectName, String service,
            ByteArrayOutputStream byteArrayOutputStream, boolean isAdvertise) throws IOException,
            ServiceMayNotContinueException, UnsupportedOperationException, ServletException {

        Repository repository = RepositoryService.createGitRepository(userName, projectName);

        ByteArrayInputStream in = null;
        PacketLineOutRefAdvertiser packetLineOutRefAdvertiser = null;
        if (isAdvertise) {
            PacketLineOut packetLineOut = new PacketLineOut(byteArrayOutputStream);
            packetLineOut.writeString("#service=" + service + "\n");
            packetLineOut.end();
            packetLineOutRefAdvertiser = new PacketLineOutRefAdvertiser(packetLineOut);
        } else {
            // FIXME 스트림으로..
            byte[] buf = request().body().asRaw().asBytes();
            in = new ByteArrayInputStream(buf);
        }

        if (service.equals("git-upload-pack")) {
            UploadPack uploadPack = new UploadPack(repository);
            uploadPack.setBiDirectionalPipe(false);
            if (isAdvertise) {
                uploadPack.sendAdvertisedRefs(packetLineOutRefAdvertiser);
            } else {
                uploadPack.upload(in, byteArrayOutputStream, null);
            }
        } else if (service.equals("git-receive-pack")) {
            ReceivePack receivePack = new ReceivePack(repository);
            if (isAdvertise) {
                receivePack.sendAdvertisedRefs(packetLineOutRefAdvertiser);
            } else {
                receivePack.setBiDirectionalPipe(false);
                receivePack.receive(in, byteArrayOutputStream, null);
            }
        }
    }

    public static Result isPermissionDenied(String userName, String projectName, String service,
            boolean isRpc) {
        Project project = ProjectApp.getProject(userName, projectName);
        Operation operation = Operation.WRITE;
        if (service.equals("git-upload-pack")) {
            operation = Operation.READ;
        }

        User currentUser = UserApp.currentUser();
        if (!AccessControl.isAllowed(currentUser.id, project.id, Resource.CODE, operation, null)) {
            Logger.debug("isnotAllowed?" + currentUser.name + "" + UserApp.anonymous.name);
            if (currentUser.id == UserApp.anonymous.id) {
                Logger.debug("anon?");
                return BasicAuthAction.unauthorized(response());
            } else {
                Logger.debug("notanon?");
                return forbidden();
            }
        }

        return null;
    }

    /**
     * 클라이언트로 부터 요청을 받아 레파지토리에 push pull 하는 함수.
     * @param projectName       서비스를 받아야할 프로젝트 이름
     * @param service           받는 서비스. 
     * @return                  클라이언트에게 줄 응답 몸통
     * @throws ServletException
     * @throws IOException
     * @throws UnsupportedOperationException
     * @throws ServiceMayNotContinueException
     * @throws Exception
     */
    @With(BasicAuthAction.class)
    public static Result advertise(String userName, String projectName)
            throws ServiceMayNotContinueException, UnsupportedOperationException, IOException,
            ServletException {
        if (!request().queryString().containsKey("service")) {
            // If service parameter is not specified then git server should do getanyfile service,
            // but we don't support that.
            return forbidden("Unsupportedservice:getanyfile");
        }
        String service = request().queryString().get("service")[0];
        Logger.debug("GitApp.advertise:" + request().toString());

        Result result = isInvalid(userName, projectName, service, false);
        if (result != null) {
            return result;
        }

        response().setContentType("application/x-" + service + "-advertisement");
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        service(userName, projectName, service, byteArrayOutputStream, true);

        return ok(byteArrayOutputStream.toByteArray());
    }

    public static Result isInvalid(String userName, String projectName, String service,
            boolean isRpc) {
        Result result = null;

        result = isNotSupportedService(service);
        if (result != null) {
            return result;
        }

        result = isPermissionDenied(userName, projectName, service, isRpc);
        if (result != null) {
            return result;
        }

        return null;
    }

    public static Result isNotSupportedService(String service) {
        if (service == null || service.equals("git-upload-pack")
                || service.equals("git-receive-pack")) {
            return null;
        }

        return forbidden("Unsupportedservice:'" + service + "'");
    }

    /**
     * 서비스의 사용가능 상태를 확인
     * @param projectName   해당 레파지토리를 가지고 있는 프로젝트명
     * @param service       사용하려는 서비스 이름. 두가지 밖에 없음.
     * @return              클라이언트에게 줄 응답
     * @throws Exception 
     */
    @With(BasicAuthAction.class)
    public static Result serviceRpc(String userName, String projectName, String service)
            throws Exception {
        Logger.debug("GitApp.advertise:" + request().toString());

        Result result = isInvalid(userName, projectName, service, true);
        if (result != null) {
            return result;
        }

        response().setContentType("application/x-" + service + "-result");
        // receivePack.setEchoCommandFailures(true);//git버전에 따라서 불린값 설정필요.
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        service(userName, projectName, service, byteArrayOutputStream, false);
        byteArrayOutputStream.close();

        return ok(byteArrayOutputStream.toByteArray());
    }
}