package controllers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import models.ProjectUser;
import org.apache.commons.mail.*;

import models.Project;
import models.User;
import org.apache.commons.mail.SimpleEmail;
import org.codehaus.jackson.JsonNode;
import play.Configuration;
import play.Logger;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import utils.Constants;

import views.html.site.setting;
import views.html.site.mail;
import views.html.site.massMail;
import views.html.site.userList;
import views.html.site.projectList;

import com.avaje.ebean.Page;
import static play.data.Form.form;
import static play.libs.Json.toJson;
import play.i18n.Messages;

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
        Logger.info(">>>" + result);
        sended = true;
        return writeMail(errorMessage, sended);
    }

    public static Result writeMail(String errorMessage, boolean sended) {

        Configuration config = play.Play.application().configuration();
        List<String> notConfiguredItems = new ArrayList<String>();
        String[] requiredItems = {"smtp.host", "smtp.user", "smtp.password"};
        for(String key : requiredItems) {
            if (config.getString(key) == null) {
                notConfiguredItems.add(key);
            }
        }

        String sender = config.getString("smtp.user") + "@" + config.getString("smtp.domain");

        return ok(mail.render("title.sendMail", notConfiguredItems, sender, errorMessage, sended));
    }

    public static Result massMail() {
        Configuration config = play.Play.application().configuration();
        List<String> notConfiguredItems = new ArrayList<String>();
        String[] requiredItems = {"smtp.host", "smtp.user", "smtp.password"};
        for(String key : requiredItems) {
            if (config.getString(key) == null) {
                notConfiguredItems.add(key);
            }
        }

        String sender = config.getString("smtp.user") + "@" + config.getString("smtp.domain");

        return ok(massMail.render("title.massMail", notConfiguredItems, sender));
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
        if( User.findByLoginId(session().get("loginId")).isSiteManager() ){
            if(Project.isOnlyManager(userId).size() == 0)
                User.find.byId(userId).delete();
            else
                flash(Constants.WARNING, "site.userList.deleteAlert");
        } else {
            flash(Constants.WARNING, "auth.unauthorized.waringMessage");
        }

        return redirect(routes.SiteApp.userList(0, null));
    }
        
    public static Result projectList(String filter) {
        Page<Project> projects = Project.findByName(filter, 25, 0);
        return ok(projectList.render("title.projectList", projects, filter));
    }
    
    public static Result deleteProject(Long projectId){
        if( User.findByLoginId(session().get("loginId")).isSiteManager() ){
            Project.find.byId(projectId).delete();
        } else {
            flash(Constants.WARNING, "auth.unauthorized.waringMessage");
        }
        return redirect(routes.SiteApp.projectList(""));
    }
    
    public static Result softwareMap() {
        return TODO;
    }

    public static Result toggleAccountLock(String loginId){
        if( User.findByLoginId(session().get("loginId")).isSiteManager() ){
            User targetUser = User.findByLoginId(loginId);
            if (targetUser.isAnonymous()){
                flash(Constants.WARNING, "user.notExists.name");
                return redirect(routes.SiteApp.userList(0, null));
            }
            targetUser.isLocked = !targetUser.isLocked;
            targetUser.save();
            return ok(userList.render("title.siteSetting", User.findUsers(0, null)));
        }
        flash(Constants.WARNING, "auth.unauthorized.waringMessage");
        return redirect(routes.Application.index());
    }

    public static Result mailList() {
        Set<String> emails = new HashSet<String>();
        Map<String, String[]> projects = request().body().asFormUrlEncoded();

        if(!UserApp.currentUser().isSiteManager()) {
            return forbidden(Messages.get("auth.unauthorized.waringMessage"));
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
                Project project = Project.findByNameAndOwner(owner, name);
                for (ProjectUser projectUser : ProjectUser.findMemberListByProject(project.id)) {
                    Logger.debug(projectUser.user.email);
                    emails.add(projectUser.user.email);
                }
            }
        }

        return ok(toJson(emails));
    }
}
