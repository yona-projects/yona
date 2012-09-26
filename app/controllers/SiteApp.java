package controllers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.mail.EmailException;

import models.Project;
import models.User;
import play.Configuration;
import play.mvc.Controller;
import play.mvc.Result;
import utils.Constants;
import utils.Mailer;

import views.html.site.setting;
import views.html.site.mail;
import views.html.site.userList;
import views.html.site.projectList;

import com.avaje.ebean.Page;

public class SiteApp extends Controller {

    public static Result sendMail() {
        Mailer email = new Mailer(play.Play.application());

        Map<String, String[]> formData = request().body().asFormUrlEncoded();
        email.addFrom(utils.RequestUtil.getFirstValueFromQuery(formData, "from"));
        email.setSubject(utils.RequestUtil.getFirstValueFromQuery(formData, "subject"));
        email.addRecipient(utils.RequestUtil.getFirstValueFromQuery(formData, "to"));

        String errorMessage = null;
        try {
            email.send(utils.RequestUtil.getFirstValueFromQuery(formData, "body"));
        } catch (EmailException e) {
            errorMessage = e.toString();
            if (e.getCause() != null) {
               errorMessage += "<br/>Caused by: " + e.getCause();
            }
        }

        return ok(mail.render("title.sendMail", new ArrayList<String>(), errorMessage));
    }

    public static Result writeMail() {
        boolean isConfigured = false;
        Configuration config = play.Play.application().configuration();
        List<String> notConfiguredItems = new ArrayList<String>();
        String[] requiredItems = {"smtp.host", "smtp.user", "smtp.password"};
        for(String key : requiredItems) {
            if (config.getString(key) == null) {
                notConfiguredItems.add(key);
            }
        }

        return ok(mail.render("title.sendMail", notConfiguredItems, null));
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
