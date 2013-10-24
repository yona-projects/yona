package controllers;

import info.schleichardt.play2.mailplugin.Mailer;
import utils.PasswordReset;
import models.User;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;
import play.Configuration;
import play.Logger;
import play.data.DynamicForm;
import play.mvc.*;
import play.i18n.Messages;
import utils.Constants;
import views.html.user.login;
import views.html.user.resetPassword;
import views.html.site.lostPassword;

import static play.data.Form.form;

public class PasswordResetApp extends Controller {

    /**
     * 패스워드 재설정 메일 발송 페이지로 이동
     *
     * when: 사용자가 패스워드가 패스워드 재설정(forget password) 링크를 눌렀을 때
     * @return 패스워드 재설정 메일 발송 페이지
     */
    public static Result lostPassword(){
        // render(message: String, sender: String, errorMessage: String, isSent: Boolean)
        return ok(lostPassword.render("site.resetPasswordEmail.title", null, null, false));
    }

    /**
     * password reset 메일 발송 요청
     *
     * when: 로그인 아이디와 이메일을 입력해서 패스워드 재설정 메일 요청을 했을 때
     *
     * - form으로부터 {@code loginId}와 {@code emailAddress}를 가져온다.
     * - 요청한 {@code loginId}가 존재하고 {@code email}이 일치하면 패스워드 reset용 링크가 첨부된 메일을 발송한다.
     * - 불일치하면 에러메시지를 표시한다.
     *
     * @return password reset 메일 요청 페이지
     */
    public static Result requestResetPasswordEmail(){
        DynamicForm requestData = form().bindFromRequest();
        String loginId = requestData.get("loginId");
        String emailAddress = requestData.get("emailAddress");

        Logger.debug("request reset password email by [" + loginId + ":" + emailAddress + "]");

        User targetUser = User.findByLoginId(loginId);

        boolean isMailSent = false;
        String errorMessage = null;
        if(!targetUser.isAnonymous() && targetUser.email.equals(emailAddress)) {
           String hashString = PasswordReset.generateResetHash(targetUser.loginId);
           PasswordReset.addHashToResetTable(targetUser.loginId, hashString);
           isMailSent = sendPasswordResetMail(targetUser, hashString);
        } else {
            Logger.debug("wrong user: " + loginId);
            errorMessage = Messages.get("site.resetPasswordEmail.invalidRequest");
        }
        return ok(lostPassword.render("site.resetPasswordEmail.title", emailAddress, errorMessage, isMailSent));
    }

    /**
     * password reset 페이지로 이동가능한 링크가 첨부된 email을 발송한다.
     *
     * when: password reset을 위해 생성된 {@code hashString}으로 password reset 메일을 발송하고자 할 때
     *
     * - play의 {@code application.conf} 파일에서 {@code smtp.user}와 {@code smtp.domain}을 읽어들여서 sender 주소로 사용한다.
     * - password reset URL을 메일 본문에 첨부해서 발송
     *
     * @param user 요청한 email로 찾아낸 사용자 정보
     * @param hashString 랜덤하게 생성해낸 해당 사용자용 hash 문자열
     * @return
     */
    private static boolean sendPasswordResetMail(User user, String hashString) {
        //ToDo SiteApp.sendMail()과 통합할 것
        //ToDo site email setting check 하는 부분이 빠져 있음
        Configuration config = play.Play.application().configuration();
        String sender = config.getString("smtp.user") + "@" + config.getString("smtp.domain");
        String resetPasswordUrl = getResetPasswordUrl(hashString);

        try {
            SimpleEmail email = new SimpleEmail();
            email.setFrom(sender)
                 .setSubject("[" + Messages.get("app.name") + "] " + Messages.get("site.resetPasswordEmail.title"))
                 .addTo(user.email)
                 .setMsg(Messages.get("site.resetPasswordEmail.mailContents") + "\n\n" + resetPasswordUrl)
                 .setCharset("utf-8");

            Logger.debug("password reset mail send: " +Mailer.send(email));
            return true;
        } catch (EmailException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * password reset을 위한 URL link를 만든다.
     *
     * - application.conf에서 {@code application.hostname}설정과 {@code application.port} 설정을 읽어들인다.
     * - 설정되어 있지 않았을 경우에는 개발환경이라고 가정하고 LOCAL_HOST_IP:DEV_MODE_PORT로 URL link를 만든다.
     * @param hashString
     * @return password reset을 위한 URL link
     */
    private static String getResetPasswordUrl(String hashString) {
        Configuration config = play.Play.application().configuration();
        String hostname = config.getString("application.hostname");
        if(hostname == null) hostname = request().host();

        return "http://" + hostname + "/resetPassword?s=" + hashString;
    }

    /**
     * password를 재설정하는 페이지로 이동
     *
     * @param s
     * @return
     */
    public static Result resetPasswordForm(String s){
        String hashString = s;
        return ok(resetPassword.render("title.resetPassword", form(User.class), hashString));
    }

    /**
     * password reset 메일을 요청한 사용자의 패스워드롤 새로운 패스워드로 교체한다.
     *
     * - 요청된 폼에서 유효검증용 {@code hashString}과 {@code password}를 읽어들인다.
     * - 유효한 {@code hashString}인지를 확인한다.
     * - 유효하면 패스워드를 재설정 아니면 로그에만 남기도 로그인 페이지로 이동한다.
     *
     * @return login 페이지로 이동
     */
    public static Result resetPassword(){
        DynamicForm requestData = form().bindFromRequest();
        String hashString = requestData.get("hashString");
        String newPassword = requestData.get("password");

        if(PasswordReset.isValidResetHash(hashString)){
            PasswordReset.resetPassword(hashString, newPassword);
            Logger.debug("Password was reset");
        } else {
            Logger.debug("Not a valid request!");
        }
        flash(Constants.WARNING, "user.loginWithNewPassword");
        return ok(login.render("title.login", form(User.class)));
    }
}
