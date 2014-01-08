package controllers;

import models.NotificationEvent;
import models.Project;
import models.User;
import models.enumeration.RequestState;
import play.db.ebean.Transactional;
import play.mvc.Controller;
import play.mvc.Result;

/**
 * 프로젝트에 멤버로 등록해달라는 요청을 처리하는 컨트롤러
 */
public class EnrollProjectApp extends Controller {

    /**
     * {@code loginId}의 {@code proejctName}에 해당하는 프로젝트에
     * 멤버 등록 요청을 생성합니다.
     *
     * @param loginId
     * @param projectName
     * @return
     */
    @Transactional
    public static Result enroll(String loginId, String projectName) {
        Project project = Project.findByOwnerAndProjectName(loginId, projectName);
        if(project == null) {
            return badRequest();
        }

        User user = UserApp.currentUser();
        if (user.isAnonymous()) {
            return badRequest();
        }

        user.enroll(project);
        NotificationEvent.afterMemberRequest(project, user, RequestState.REQUEST, routes.ProjectApp.members(loginId, projectName).url());
        return ok();
    }

    /**
     * {@code loginId}의 {@code proejctName}에 해당하는 프로젝트에
     * 멤버 등록 요청을 취소한다.
     *
     * @param loginId
     * @param proejctName
     * @return
     */
    @Transactional
    public static Result cancelEnroll(String loginId, String proejctName) {
        Project project = Project.findByOwnerAndProjectName(loginId, proejctName);
        if(project == null) {
            return badRequest();
        }

        User user = UserApp.currentUser();
        if (user.isAnonymous()) {
            return badRequest();
        }

        user.cancelEnroll(project);
        NotificationEvent.afterMemberRequest(project, user, RequestState.CANCEL, routes.ProjectApp.members(loginId, proejctName).url());
        return ok();
    }
}
