package controllers;

import info.schleichardt.play2.mailplugin.Mailer;
import models.PasswordReset;
import models.User;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;
import play.Configuration;
import play.Logger;
import play.data.DynamicForm;
import play.mvc.*;
import play.i18n.Messages;
import utils.Constants;
import views.html.login;
import views.html.site.lostPassword;
import views.html.user.resetPassword;

import static play.data.Form.form;

public class PasswordResetApp extends Controller {

    public static final String LOCAL_HOST = "127.0.0.1";
    public static final String DEV_MODE = "9000";

    public static Result lostPassword(){
        return ok(lostPassword.render("admin.resetPasswordEmail.title", null, null, false));
    }

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
            errorMessage = Messages.get("admin.resetPasswordEmail.invalidRequest");
        }
        return ok(lostPassword.render("admin.resetPasswordEmail.title", emailAddress, errorMessage, isMailSent));
    }

    //ToDo SiteApp.sendMail()과 통합할 것
    //ToDo site email setting check 하는 부분이 빠져 있음
    private static boolean sendPasswordResetMail(User user, String hashString) {
        Configuration config = play.Play.application().configuration();
        String sender = config.getString("smtp.user") + "@" + config.getString("smtp.domain");
        String resetPasswordUrl = getResetPasswordUrl(hashString);

        try {
            SimpleEmail email = new SimpleEmail();
            email.setFrom(sender)
                 .setSubject(Messages.get("admin.resetPasswordEmail.title"))
                 .addTo(user.email)
                 .setMsg(Messages.get("admin.resetPasswordEmail.mailcontents") + "\n\n" + resetPasswordUrl)
                 .setCharset("utf-8");

            Mailer.send(email);
            return true;
        } catch (EmailException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static String getResetPasswordUrl(String hashString) {
        Configuration config = play.Play.application().configuration();
        String hostname = config.getString("application.hostname");
        String port = config.getString("application.port");

        if(hostname == null) hostname = LOCAL_HOST;
        if(port == null) port = DEV_MODE;

        return "http://" + hostname + ":" + port + "/resetPassword?s=" + hashString;
    }

    public static Result resetPasswordForm(String s){
        String hashString = s;
        return ok(resetPassword.render("title.resetPassword", form(User.class), hashString));
    }

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
