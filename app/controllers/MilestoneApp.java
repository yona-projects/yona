package controllers;

import models.*;
import models.enumeration.*;
import play.data.*;
import play.mvc.*;
import utils.Constants;
import views.html.milestone.*;

import java.util.*;

import static play.data.Form.form;

public class MilestoneApp extends Controller {

    public static class MilestoneCondition {

        public String state    = "open";
        public String orderBy  = "dueDate";
        public String orderDir = "asc";

        public MilestoneCondition() {
            this.state    = "open";
            this.orderBy  = "dueDate";
            this.orderDir = "asc";
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
                mCondition.orderBy,
                Direction.getValue(mCondition.orderDir));

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
        validate(project, milestoneForm);
        if (milestoneForm.hasErrors()) {
            return ok(create.render("title.newMilestone", milestoneForm, project));
        } else {
            Milestone newMilestone = milestoneForm.get();
            newMilestone.project = project;
            Milestone.create(newMilestone);
            return redirect(routes.MilestoneApp.milestones(userName, projectName));
        }
    }

    private static void validate(Project project, Form<Milestone> milestoneForm) {
        // 중복된 제목으로 만들 수 없다
        if (!Milestone.isUniqueProjectIdAndTitle(project.id, milestoneForm.field("title").value())) {
            milestoneForm.reject("title", "milestone.title.duplicated");
            flash(Constants.WARNING, "milestone.title.duplicated");
        }
    }

    /*
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
    */

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
        Milestone original = Milestone.findById(milestoneId);
        if(!original.title.equals(milestoneForm.field("title").value())) {
            validate(project, milestoneForm);
        }
        if (milestoneForm.hasErrors()) {
            return ok(edit.render("title.editMilestone", milestoneForm, milestoneId, project));
        } else {
            Milestone existingMilestone = Milestone.findById(milestoneId);
            existingMilestone.updateWith(milestoneForm.get());
            return redirect(routes.MilestoneApp.milestones(userName, projectName));
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
        Milestone.findById(id).delete();
        return redirect(routes.MilestoneApp.milestones(userName, projectName));

    }
}
