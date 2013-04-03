package controllers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.mail.*;

import models.Project;
import models.User;
import org.apache.commons.mail.SimpleEmail;
import play.Configuration;
import play.mvc.Controller;
import play.mvc.Result;
import utils.Constants;

import views.html.site.setting;
import views.html.site.mail;
import views.html.site.userList;
import views.html.site.projectList;

import com.avaje.ebean.Page;
import static play.data.Form.form;
import info.schleichardt.play2.mailplugin.Mailer;

public class SiteApp extends Controller {
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
        System.out.println(">>>" + result);
        sended = true;
        return writeMail(errorMessage, sended);
    }

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
            User.find.byId(userId).delete();
        else
            flash(Constants.WARNING, "site.userList.deleteAlert");
            
        return redirect(routes.SiteApp.userList(0, null));
    }
        
    public static Result projectList(String filter) {
        Page<Project> projects = Project.findByName(filter, 25, 0);
        return ok(projectList.render("title.projectList", projects, filter));
    }
    
    public static Result deleteProject(Long projectId){
        Project.find.byId(projectId).delete();
        return redirect(routes.SiteApp.projectList(""));
    }
    
    public static Result softwareMap() {
        return TODO;
    }
}
