/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Suwon Chae
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package controllers;

import controllers.annotation.AnonymousCheck;
import info.schleichardt.play2.mailplugin.Mailer;
import models.AuthInfo;
import models.User;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;
import play.Configuration;
import play.Logger;
import play.data.DynamicForm;
import play.i18n.Messages;
import play.mvc.Controller;
import play.mvc.Result;
import utils.Config;
import utils.Constants;
import utils.ErrorViews;
import utils.PasswordReset;
import utils.Url;
import views.html.site.lostPassword;
import views.html.user.login;
import views.html.user.resetPassword;

import static play.data.Form.form;

@AnonymousCheck
public class PasswordResetApp extends Controller {

    public static Result lostPassword(){
        // render(message: String, sender: String, errorMessage: String, isSent: Boolean)
        return ok(lostPassword.render("site.resetPasswordEmail.title", null, null, false));
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
            errorMessage = Messages.get("site.resetPasswordEmail.invalidRequest");
        }
        return ok(lostPassword.render("site.resetPasswordEmail.title", emailAddress, errorMessage, isMailSent));
    }

    private static boolean sendPasswordResetMail(User user, String hashString) {
        String sender = Config.getEmailFromSmtp();
        String resetPasswordUrl = getResetPasswordUrl(hashString);

        try {
            SimpleEmail email = new SimpleEmail();
            email.setFrom(sender)
                 .setSubject("[" + utils.Config.getSiteName() + "] " + Messages.get("site.resetPasswordEmail.title"))
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

    private static String getResetPasswordUrl(String hashString) {
        return Url.create(controllers.routes.PasswordResetApp.resetPasswordForm(hashString).url());
    }

    public static Result resetPasswordForm(String hashString){
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
            return badRequest(ErrorViews.BadRequest.render("site.resetPasswordEmail.wrongUrl"));
        }
        flash(Constants.WARNING, "user.loginWithNewPassword");
        return ok(login.render("title.login", form(AuthInfo.class), null));
    }
}
