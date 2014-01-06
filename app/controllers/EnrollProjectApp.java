package controllers;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import models.NotificationEvent;
import models.Project;
import models.User;
import models.enumeration.EventType;
import models.enumeration.RequestState;
import models.enumeration.RoleType;
import models.resource.Resource;
import play.db.ebean.Transactional;
import play.mvc.Call;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import utils.Constants;
import utils.ErrorViews;
import utils.WatchService;
import views.html.board.view;

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
            return badProject(loginId, projectName);
        }

        User user = UserApp.currentUser();
        if(user.isAnonymous()) {
            flash(Constants.WARNING, "user.login.alert");
            return redirect(routes.UserApp.loginForm());
        }

        user.enroll(project);
        addNotificationEvent(
                project,
                user,
                RequestState.REQUEST,
                routes.ProjectApp.members(loginId, projectName));
        return redirect(request().getHeader(Http.HeaderNames.REFERER));
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
            return badProject(loginId, proejctName);
        }

        User user = UserApp.currentUser();
        if(user.isAnonymous()) {
            flash(Constants.WARNING, "user.login.alert");
            return redirect(routes.UserApp.loginForm());
        }

        user.cancelEnroll(project);
        addNotificationEvent(
                project,
                user,
                RequestState.CANCEL,
                routes.ProjectApp.members(loginId, proejctName));
        return redirect(request().getHeader(Http.HeaderNames.REFERER));
    }

    private static Result badProject(String loginId, String projectName) {
        return badRequest(ErrorViews.BadRequest.render("No project matches given user name '" + loginId + "' and project name '" + projectName + "'"));
    }

    /**
     * 멤버 등록 요청에 관련된 알림을 보낸다.
     * @param project
     * @param user
     * @param state
     * @param call
     */
    public static void addNotificationEvent(Project project, User user, RequestState state, Call call) {
        NotificationEvent event = new NotificationEvent();
        Resource resource = project.asResource();
        event.created = new Date();
        event.senderId = UserApp.currentUser().id;
        event.resourceId = resource.getId();
        event.resourceType = resource.getType();
        event.eventType = EventType.MEMBER_ENROLL_REQUEST;
        event.receivers = getReceivers(project);
        event.newValue = state.name();
        event.urlToView = call.url();
        if (state == RequestState.ACCEPT || state == RequestState.REJECT) {
            event.receivers.remove(UserApp.currentUser());
            event.receivers.add(user);
        }
        if (state == RequestState.REQUEST) {
            event.title = NotificationEvent.formatNewTitle(project, user);
            event.oldValue = RequestState.CANCEL.name();
        } else {
            event.title = NotificationEvent.formatReplyTitle(project, user);
            event.oldValue = RequestState.REQUEST.name();
        }
        NotificationEvent.add(event);
    }

    /*
     * 멤버 등록 관련 알림을 받을 대상자 추출
     * - 해당 프로젝트의 매니저이면서 지켜보기를 켜둔 사용자들
     */
    private static Set<User> getReceivers(Project project) {
        Set<User> receivers = new HashSet<>();
        List<User> managers = User.findUsersByProject(project.id, RoleType.MANAGER);
        for (User manager : managers) {
            if (WatchService.isWatching(manager, project.asResource())) {
                receivers.add(manager);
            }
        }
        return receivers;
    }
}
