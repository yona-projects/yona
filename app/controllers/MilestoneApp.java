package controllers;

import models.*;
import models.enumeration.*;
import play.data.*;
import play.mvc.*;
import views.html.milestone.*;

import java.util.*;

import static play.data.Form.form;

public class MilestoneApp extends Controller {

    public static class MilestoneCondition {

        public String state = "open";
        public String sort = "dueDate";
        public String direction = "asc";

        public MilestoneCondition() {
            this.state = "open";
            this.sort = "dueDate";
            this.direction = "asc";
        }
    }

    public static Result milestones(String userName, String projectName) {

        Project project = ProjectApp.getProject(userName, projectName);
        if(project == null ) {
            return notFound();
        }
        MilestoneCondition mCondition = form(MilestoneCondition.class).bindFromRequest().get();

        List<Milestone> milestones = Milestone.findMilestones(project.id,
                State.getValue(mCondition.state),
                mCondition.sort,
                Direction.getValue(mCondition.direction));

        return ok(list.render("title.milestoneList", milestones, project, mCondition));
    }

    public static Result newMilestoneForm(String userName, String projectName) {
        Project project = ProjectApp.getProject(userName, projectName);
        if(project == null ) {
            return notFound();
        }

        return ok(create.render("title.newMilestone", new Form<Milestone>(Milestone.class), project));
    }

    public static Result newMilestone(String userName, String projectName) {
        Form<Milestone> milestoneForm = new Form<Milestone>(Milestone.class).bindFromRequest();
        Project project = ProjectApp.getProject(userName, projectName);
        if(project == null ) {
            return notFound();
        }
        if (milestoneForm.hasErrors()) {
            return ok(create.render("title.newMilestone", milestoneForm, project));
        } else {
            Milestone newMilestone = milestoneForm.get();
            newMilestone.project = project;
            Milestone.create(newMilestone);
            return redirect(routes.MilestoneApp.manageMilestones(userName, projectName));
        }
    }

    public static Result manageMilestones(String userName, String projectName) {
        Project project = ProjectApp.getProject(userName, projectName);
        if(project == null ) {
            return notFound();
        }
        MilestoneCondition mCondition = form(MilestoneCondition.class).bindFromRequest().get();
        List<Milestone> milestones = Milestone.findMilestones(project.id,
                State.ALL,
                mCondition.sort,
                Direction.getValue(mCondition.direction));
        return ok(manage.render("title.milestoneManage", milestones, project, mCondition));
    }

    public static Result editMilestoneForm(String userName, String projectName, Long milestoneId) {
        Project project = ProjectApp.getProject(userName, projectName);
        if(project == null ) {
            return notFound();
        }

        Milestone milestone = Milestone.findById(milestoneId);
        Form<Milestone> editForm = new Form<Milestone>(Milestone.class).fill(milestone);
        return ok(edit.render("title.editMilestone", editForm, milestoneId, project));
    }

    public static Result editMilestone(String userName, String projectName, Long milestoneId) {
        Project project = ProjectApp.getProject(userName, projectName);
        if(project == null ) {
            return notFound();
        }

        Form<Milestone> milestoneForm = new Form<Milestone>(Milestone.class).bindFromRequest();
        if (milestoneForm.hasErrors()) {
            return ok(edit.render("title.editMilestone", milestoneForm, milestoneId, project));
        } else {
            Milestone existingMilestone = Milestone.findById(milestoneId);
            existingMilestone.updateWith(milestoneForm.get());
            return redirect(routes.MilestoneApp.manageMilestones(userName, projectName));
        }
    }

    public static Result deleteMilestone(String userName, String projectName, Long id) {
        Project project = ProjectApp.getProject(userName, projectName);
        if(project == null ) {
            return notFound();
        }

        if(!project.id.equals(Milestone.findById(id).project.id)) {
            return internalServerError();
        }
        Milestone.delete(Milestone.findById(id));
        return redirect(routes.MilestoneApp.manageMilestones(userName, projectName));

    }
}
