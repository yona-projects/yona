package controllers;

import java.io.IOException;

import javax.servlet.ServletException;

import models.Project;
import models.enumeration.Operation;
import models.enumeration.Resource;

import org.eclipse.jgit.transport.ServiceMayNotContinueException;

import play.Logger;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.With;
import playRepository.RepositoryService;
import utils.AccessControl;
import utils.BasicAuthAction;
import utils.RequestUtil;

public class GitApp extends Controller {

    public static boolean isSupportedService(String service) {
        return service != null
                && (service.equals("git-upload-pack") || service.equals("git-receive-pack"));
    }

    public static boolean isAllowed(String userName, String projectName, String service) {
        Project project = ProjectApp.getProject(userName, projectName);

        Operation operation = Operation.WRITE;
        if (service.equals("git-upload-pack")) {
            operation = Operation.READ;
        }

        if (AccessControl.isAllowed(
                UserApp.currentUser().id, project.id, Resource.CODE, operation, null)) {
            return true;
        }

        return false;
    }

    public static Result service(String userName, String projectName, String service,
            boolean isAdvertise) throws IOException, ServiceMayNotContinueException,
            UnsupportedOperationException, ServletException {
        if (!isSupportedService(service)) {
            return forbidden(String.format("Unsupported service: '%s'", service));
        }

        if (!isAllowed(userName, projectName, service)) {
            if (UserApp.currentUser().id == UserApp.anonymous.id) {
                return BasicAuthAction.unauthorized(response());
            } else {
                return forbidden();
            }
        }

        if (isAdvertise) {
            return ok(RepositoryService
                    .gitAdvertise(userName, projectName, service, response()));
        } else {
            return ok(RepositoryService
                    .gitRpc(userName, projectName, service, request(), response()));
        }
    }

    /**
     * 서비스의 사용가능 상태를 확인
     *
     * @param projectName
     *            해당 레파지토리를 가지고 있는 프로젝트명
     * @param service
     *            사용하려는 서비스 이름. 두가지 밖에 없음.
     * @return 클라이언트에게 줄 응답
     * @throws ServletException
     * @throws IOException
     * @throws UnsupportedOperationException
     * @throws ServiceMayNotContinueException
     */
    @With(BasicAuthAction.class)
    public static Result advertise(String userName, String projectName)
            throws ServiceMayNotContinueException, UnsupportedOperationException, IOException,
            ServletException {
        String service = RequestUtil.getFirstValueFromQuery(request().queryString(), "service");
        if (service == null) {
            // If service parameter is not specified then git server should do getanyfile service,
            // but we don't support that.
            return forbidden("Unsupported service: getanyfile");
        }

        Logger.debug("GitApp.advertise:" + request().toString());

        return GitApp.service(userName, projectName, service, true);
    }

    /**
     * 클라이언트로 부터 요청을 받아 레파지토리에 push pull 하는 함수.
     *
     * @param projectName
     *            서비스를 받아야할 프로젝트 이름
     * @param service
     *            받는 서비스.
     * @return 클라이언트에게 줄 응답 몸통
     * @throws ServletException
     * @throws IOException
     * @throws UnsupportedOperationException
     * @throws ServiceMayNotContinueException
     */
    @With(BasicAuthAction.class)
    public static Result serviceRpc(String userName, String projectName, String service)
            throws ServiceMayNotContinueException, UnsupportedOperationException, IOException,
            ServletException {
        Logger.debug("GitApp.advertise: " + request().toString());

        return GitApp.service(userName, projectName, service, false);
    }

}