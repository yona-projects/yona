package controllers;

import models.Milestone;
import models.Project;
import models.enumeration.Direction;
import models.enumeration.StateType;
import play.data.Form;
import play.mvc.Controller;
import play.mvc.Result;
import views.html.milestone.create;
import views.html.milestone.edit;
import views.html.milestone.list;
import views.html.milestone.manage;

import java.util.List;

public class MilestoneApp extends Controller {

    public static Result milestones(String projectName, String state,
                                    String sort, String direction) {

        Project project = Project.findByName(projectName);
        if(project == null ) {
            return notFound();
        }
        List<Milestone> milestones = Milestone.findMilestones(project.id,
                StateType.getValue(state),
                sort,
                Direction.getValue(direction));
        return ok(list.render("title.milestoneList", milestones, projectName, state, sort, direction, project));
    }

    public static Result newMilestone(String projectName) {
        Project project = Project.findByName(projectName);
        if(project == null ) {
            return notFound();
        }

        return ok(create.render("title.newMilestone", new Form<Milestone>(Milestone.class), projectName, project));
    }

    public static Result saveMilestone(String projectName) {
        Form<Milestone> milestoneForm = new Form<Milestone>(Milestone.class).bindFromRequest();
        Project project = Project.findByName(projectName);
        if(project == null ) {
            return internalServerError();
        }
        if (milestoneForm.hasErrors()) {
            return ok(create.render("title.newMilestone", milestoneForm, projectName, project));
        } else {
            Milestone newMilestone = milestoneForm.get();
            newMilestone.project = project;
            Milestone.create(newMilestone);
            return redirect(routes.MilestoneApp.manageMilestones(projectName, "dueDate", Direction.ASC.name()));
        }
    }

    public static Result manageMilestones(String projectName, String sort, String direction) {
        Project project = Project.findByName(projectName);
        if(project == null ) {
            return notFound();
        }

        List<Milestone> milestones = Milestone.findMilestones(project.id,
                StateType.ALL,
                sort,
                Direction.getValue(direction));
        return ok(manage.render("title.milestoneManage", milestones, projectName, sort, direction, project));
    }

    public static Result editMilestone(String projectName, Long milestoneId) {
        Project project = Project.findByName(projectName);
        if(project == null ) {
            return notFound();
        }

        Milestone milestone = Milestone.findById(milestoneId);
        Form<Milestone> editForm = new Form<Milestone>(Milestone.class).fill(milestone);
        return ok(edit.render("title.editMilestone", editForm, projectName, milestoneId, project));
    }

    public static Result updateMilestone(String projectName, Long milestoneId) {
        Form<Milestone> milestoneForm = new Form<Milestone>(Milestone.class).bindFromRequest();
        if (milestoneForm.hasErrors()) {
            return ok(edit.render("title.editMilestone", milestoneForm, projectName, milestoneId, Project.findByName(projectName)));
        } else {
            Milestone existingMilestone = Milestone.findById(milestoneId);
            existingMilestone.updateWith(milestoneForm.get());
            Milestone.update(existingMilestone, milestoneId);
            return redirect(routes.MilestoneApp.manageMilestones(projectName, "dueDate", Direction.ASC.name()));
        }
    }

    public static Result deleteMilestone(String projectName, Long id) {
        Project project = Project.findByName(projectName);
        if(project == null ) {
            return notFound();
        }

        if(project.id.equals(Milestone.findById(id).project.id)) {
            return internalServerError();
        }
        Milestone.delete(Milestone.findById(id));
        return redirect(routes.MilestoneApp.manageMilestones(projectName, "dueDate", Direction.ASC.name()));
    }
}
