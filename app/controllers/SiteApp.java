package controllers;

import java.util.Map;

import models.Project;
import models.User;
import play.Configuration;
import play.mvc.Controller;
import play.mvc.Result;
import utils.Constants;

import views.html.site.setting;
import views.html.site.mail;
import views.html.site.userList;
import views.html.site.projectList;

import com.avaje.ebean.Page;
import com.typesafe.plugin.MailerAPI;
import com.typesafe.plugin.MailerPlugin;

public class SiteApp extends Controller {

    public static Result sendMail() {
        MailerAPI email = play.Play.application().plugin(MailerPlugin.class).email();

        Map<String, String[]> formData = request().body().asFormUrlEncoded();
        email.addFrom(play.Play.application().configuration().getString("smtp.user"));
        email.setSubject(utils.RequestUtil.getFirstValueFromQuery(formData, "subject"));
        email.addRecipient(utils.RequestUtil.getFirstValueFromQuery(formData, "to"));
        email.send(utils.RequestUtil.getFirstValueFromQuery(formData, "body"));

        return ok(mail.render("title.sendMail", true));
    }

    public static Result writeMail() {
        boolean isConfigured = false;
        Configuration config = play.Play.application().configuration();
        if (config.getString("smtp.user") != null && config.getString("smtp.password") != null) {
            isConfigured = true;
        }

        return ok(mail.render("title.sendMail", isConfigured));
    }

    public static Result setting() {
        return ok(setting.render("title.siteSetting"));
    }
    
    public static Result userList(int pageNum, String loginId) {
        return ok(userList.render("title.siteSetting", User.findUsers(pageNum, loginId)));
    }
    
    public static Result searchUser() {
        String loginId = form(User.class).bindFromRequest().get().loginId;
        return redirect(routes.SiteApp.userList(0, loginId));
    }
    
    public static Result deleteUser(Long userId) {
        if(Project.isOnlyManager(userId).size() == 0)
            User.findById(userId).delete();
        else
            flash(Constants.WARNING, "site.userList.deleteAlert");
            
        return redirect(routes.SiteApp.userList(0, null));
    }
        
    public static Result projectList() {
        Page<Project> projects = ProjectApp.projectList(0);
        return ok(projectList.render("title.siteList", projects));
    }
    public static Result deleteProject(Long projectId){
        Project.delete(projectId);
        return redirect(routes.SiteApp.projectList());
    }
    
    public static Result softwareMap() {
        return TODO;
    }
}
