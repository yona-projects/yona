package controllers;

import actions.AnonymousCheckAction;
import actions.DefaultProjectCheckAction;
import models.NotificationEvent;
import models.Project;
import models.ProjectUser;
import models.User;
import models.enumeration.RequestState;
import play.db.ebean.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.With;

/**
 * 프로젝트에 멤버로 등록해달라는 요청을 처리하는 컨트롤러
 */
@With(AnonymousCheckAction.class)
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
    @With(DefaultProjectCheckAction.class)
    public static Result enroll(String loginId, String projectName) {
        Project project = Project.findByOwnerAndProjectName(loginId, projectName);

        User user = UserApp.currentUser();
        if (!ProjectUser.isGuest(project, user)) {
            return badRequest();
        }

        if (!User.enrolled(project)) {
            user.enroll(project);
            NotificationEvent.afterMemberRequest(project, user, RequestState.REQUEST);
        }

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
    @With(DefaultProjectCheckAction.class)
    public static Result cancelEnroll(String loginId, String proejctName) {
        Project project = Project.findByOwnerAndProjectName(loginId, proejctName);

        User user = UserApp.currentUser();
        if (!ProjectUser.isGuest(project, user)) {
            return badRequest();
        }

        if (User.enrolled(project)) {
            user.cancelEnroll(project);
            NotificationEvent.afterMemberRequest(project, user, RequestState.CANCEL);
        }

        return ok();
    }
}
