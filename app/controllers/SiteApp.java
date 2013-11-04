package controllers;

import com.avaje.ebean.Page;

import info.schleichardt.play2.mailplugin.Mailer;
import models.*;

import models.enumeration.UserState;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;
import play.Configuration;
import play.Logger;
import play.db.ebean.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.With;
import utils.Constants;
import utils.SiteManagerAuthAction;
import utils.ErrorViews;
import views.html.site.*;

import java.util.*;

import static play.data.Form.form;
import static play.libs.Json.toJson;

/**
 * The Class SiteApp.
 */
 @With(SiteManagerAuthAction.class)
public class SiteApp extends Controller {

    private static final int PROJECT_COUNT_PER_PAGE = 25;
    private static final int POSTING_COUNT_PER_PAGE = 30;
    private static final int ISSUE_COUNT_PER_PAGE = 30;

    /**
     * 메일을 발송한다.
     *
     * when 메일발송 페이지에서 발송시
     *
     * 입력폼으로부터 보내는 메일주소, 받는사람 제목, 본문내용을 입력받고 {@code email} 객체에 할당한다.
     * 메일을 발송하고 결과를 {@code sended}에 할당한다.
     * {@code writeMail()} 을 통해 메일 전송여부와 오류메세지를 설정하고 메일발송 페이지로 이동한다.
     *
     * @return the result
     * @throws EmailException the email exception
     * @see {@link SiteApp#writeMail(String, boolean)}
     */
    public static Result sendMail() throws EmailException{
        SimpleEmail email = new SimpleEmail();

        Map<String, String[]> formData = request().body().asFormUrlEncoded();
        email.setFrom(utils.HttpUtil.getFirstValueFromQuery(formData, "from"));
        email.setSubject(utils.HttpUtil.getFirstValueFromQuery(formData, "subject"));
        email.addTo(utils.HttpUtil.getFirstValueFromQuery(formData, "to"));
        email.setMsg(utils.HttpUtil.getFirstValueFromQuery(formData, "body"));
        email.setCharset("utf-8");

        String errorMessage = null;
        boolean sended = false;
        String result = Mailer.send(email);
        Logger.info(">>>" + result);
        sended = true;
        return writeMail(errorMessage, sended);
    }

    /**
     * 메일 발송 페이지로 이동한다.
     *
     * when 관리자 메일 발송페이지
     *
     * {@code application.conf}에서 SMTP 관련 설정을 가져온다.
     * {@code requiredItems} 중 설정되지 않은 item을 {@code notConfiguredItems}에 저장하고 페이지에 전달한다.
     * 메일 sender는 {@code smtp.user}@{@code smtp.domain} 값으로 구성된다.
     *
     * @param errorMessage 메일 발송 오류메세지
     * @param sended 메일 발송여부
     * @return the result
     */
    public static Result writeMail(String errorMessage, boolean sended) {

        Configuration config = play.Play.application().configuration();
        List<String> notConfiguredItems = new ArrayList<>();
        String[] requiredItems = {"smtp.host", "smtp.user", "smtp.password"};
        for(String key : requiredItems) {
            if (config.getString(key) == null) {
                notConfiguredItems.add(key);
            }
        }

        String sender = config.getString("smtp.user") + "@" + config.getString("smtp.domain");

        return ok(mail.render("title.sendMail", notConfiguredItems, sender, errorMessage, sended));
    }

    /**
     * 대량 메일 발송 페이지로 이동한다.
     *
     * when 관리자 대량 메일 발송페이지
     *
     * @return the result
     */
    public static Result massMail() {
        return ok(massMail.render("title.massMail"));
    }

    /**
     * 전체 사용자 목록을 보여준다.
     *
     * when 관리자 페이지의 사용자 관리
     *
     * 사이트에 가입된 사용자 목록을 {@code loginId} 로 조회하여 목록을 {@link Page} 형태로 보여준다.
     * 페이징 사이즈는 {@link User#USER_COUNT_PER_PAGE}를 참조한다.
     *
     * @param pageNum pager number
     * @param loginId loginId
     * @return the result
     * @see {@link User#findUsers(int, String)}
     */
    public static Result userList(int pageNum, String loginId) {
        return ok(userList.render("title.siteSetting", User.findUsers(pageNum, loginId)));
    }

    /**
     * 전체 게시글 목록을 보여준다.
     *
     * when 관리자 페이지의 게시물 관리
     *
     * 최근작성일로 정렬된 {@code pageNum} 에 해당하는 게시물 목록을 가져온다.
     *
     * @param pageNum page number
     * @return the result
     */
    public static Result postList(int pageNum) {
        Page<Posting> page = Posting.finder.order("createdDate DESC").findPagingList(POSTING_COUNT_PER_PAGE).getPage(pageNum - 1);
        return ok(postList.render("title.siteSetting", page));
    }

    /**
     * 전체 이슈 목록을 보여준다.
     *
     * when 관리자 페이지의 이슈 관리
     *
     * 최근작성일로 정렬된 {@code pageNum} 에 해당하는 이슈 목록을 가져온다.
     *
     * @param pageNum page number
     * @return the result
     */
    public static Result issueList(int pageNum) {
        Page<Issue> page = Issue.finder.order("createdDate DESC").findPagingList(ISSUE_COUNT_PER_PAGE).getPage(pageNum - 1);
        return ok(issueList.render("title.siteSetting", page));
    }

    /**
     * 사용자를 삭제한다.
     *
     * when 관리자 페이지 사용자 삭제시
     *
     *
     * @param userId the user id
     * @return the result
     * @see {@link Project#isOnlyManager(Long)}
     */
    @Transactional
    public static Result deleteUser(Long userId) {
        if (User.findByLoginId(session().get("loginId")).isSiteManager()){
            if (Project.isOnlyManager(userId)) {
                flash(Constants.WARNING, "site.userList.deleteAlert");
            } else {
                User.find.byId(userId).changeState(UserState.DELETED);
            }
        } else {
            flash(Constants.WARNING, "error.auth.unauthorized.waringMessage");
        }

        return redirect(routes.SiteApp.userList(0, null));
    }

    /**
     * 프로젝트 목록을 가져온다.
     *
     * when 관리자 페이지의 프로젝트 설정
     *
     * 프로젝트명이 {@code project name} 값을 포함하는 프로젝트 목록을 가져온다.
     *
     * @param projectName the project name
     * @param pageNum page number
     * @return the result
     * @see {@link Project#findByName(String, int, int)}
     */
    public static Result projectList(String projectName, int pageNum) {
        Page<Project> projects = Project.findByName(projectName, PROJECT_COUNT_PER_PAGE, pageNum);
        return ok(projectList.render("title.projectList", projects, projectName));
    }

    /**
     * 프로젝트를 삭제한다.
     *
     * when 관리자 페이지의 프로젝트 설정에서 프로젝트 삭제시
     *
     * 세션 {@code loginId} 가 사이트 관리자인지 확인하고 관리자이면 해당 프로젝트를 삭제한다.
     * 관리자가 아니면 경고메세지와 함께 프로젝트 설정 페이지로 리다이렉트 한다.
     *
     * @param projectId the project id
     * @return the result
     */
    @Transactional
    public static Result deleteProject(Long projectId){
        if( User.findByLoginId(session().get("loginId")).isSiteManager() ){
            Project.find.byId(projectId).delete();
        } else {
            flash(Constants.WARNING, "error.auth.unauthorized.waringMessage");
        }
        return redirect(routes.SiteApp.projectList(StringUtils.EMPTY, 0));
    }

    /**
     * 계정을 장금 / 해제한다.
     *
     * when 사용자 관리 페이지의 계정 장금/해제
     *
     * 세션 {@code loginId} 가 사이트 관리자이고 삭제할 {@code loginId}가 {@code anonymous}가 아니면 계정 장금 또는 해제한후 사용자 관리페이지로 리다이렉트한다.
     * 세션 {@code loginId} 가 사이트 관리자이고 삭제할 {@code loginId}가 익명사용자이면 경고메세지와 함께사용자 관리페이지로 리다이렉트한다.
     * 세션 {@code loginId} 가 사이트 관리자가 아니면 경고메세지와 함께 Yobi 첫페이지로 리다이렉트한다.
     *
     * @param loginId the login id
     * @return the result
     */
    @Transactional
    public static Result toggleAccountLock(String loginId){
        if(User.findByLoginId(session().get("loginId")).isSiteManager()){
            User targetUser = User.findByLoginId(loginId);
            if (targetUser.isAnonymous()){
                flash(Constants.WARNING, "user.notExists.name");
                return redirect(routes.SiteApp.userList(0, null));
            }
            if (targetUser.state == UserState.ACTIVE) {
                targetUser.changeState(UserState.LOCKED);
            } else {
                targetUser.changeState(UserState.ACTIVE);
            }
            return ok(userList.render("title.siteSetting", User.findUsers(0, null)));
        }
        flash(Constants.WARNING, "error.auth.unauthorized.waringMessage");
        return redirect(routes.Application.index());
    }

    /**
     * 대량의 메일목록을 JSON으로 반환한다.
     *
     * when 사이트 관리자페이지의 대량 메일 발송시 사용
     *
     * {@code currentUser} 가 사이트관리자가 아니면 경고메세지와 함께 fobidden을 반환한다.
     * 요청 content-type이 application/json이 아니면 {@link Http.Status#NOT_ACCEPTABLE} 을 반환한다.
     * {@code projects}가 null 이면 비어있는 json객체를 반환한다.
     * 모두에게 보내기 요청시에는 모든 사용자 목록을 json으로 반환한다.
     * 대상이 특정 프로젝트 멤버일시에는 해당 프로젝트의 멤버를 json으로 반환한다.
     *
     * @return the result
     */
    public static Result mailList() {
        Set<String> emails = new HashSet<>();
        Map<String, String[]> projects = request().body().asFormUrlEncoded();
        if(!UserApp.currentUser().isSiteManager()) {
            return forbidden(ErrorViews.Forbidden.render("error.auth.unauthorized.waringMessage"));
        }

        if (!request().accepts("application/json")) {
            return status(Http.Status.NOT_ACCEPTABLE);
        }

        if (projects == null) {
            return ok(toJson(new HashSet<String>()));
        }

        if (projects.containsKey("all")) {
            if (projects.get("all")[0].equals("true")) {
                for(User user : User.find.findList()) {
                    emails.add(user.email);
                }
            }
        } else {
            for(String[] projectNames : projects.values()) {
                String projectName = projectNames[0];
                String[] parts = projectName.split("/");
                String owner = parts[0];
                String name = parts[1];
                Project project = Project.findByOwnerAndProjectName(owner, name);
                for (ProjectUser projectUser : ProjectUser.findMemberListByProject(project.id)) {
                    Logger.debug(projectUser.user.email);
                    emails.add(projectUser.user.email);
                }
            }
        }

        return ok(toJson(emails));
    }
}
