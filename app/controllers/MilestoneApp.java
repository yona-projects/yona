package controllers;

import models.Milestone;
import models.Project;
import models.enumeration.Direction;
import models.enumeration.MilestoneState;
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
                MilestoneState.getValue(state),
                sort,
                Direction.getValue(direction));
        return ok(list.render("마일스톤 리스트", milestones, projectName, state, sort, direction, project));
    }

    public static Result newMilestone(String projectName) {
        Project project = Project.findByName(projectName);
        if(project == null ) {
            return notFound();
        }

        return ok(create.render("새 마일스톤", new Form<Milestone>(Milestone.class), projectName, project));
    }

    public static Result createMilestone(String projectName) {
        Form<Milestone> milestoneForm = new Form<Milestone>(Milestone.class).bindFromRequest();
        Project project = Project.findByName(projectName);
        if(project == null ) {
            return internalServerError();
        }
        if (milestoneForm.hasErrors()) {
            return ok(create.render("새 마일스톤", milestoneForm, projectName, project));
        } else {
            Milestone newMilestone = milestoneForm.get();
            newMilestone.projectId = project.id;
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
                MilestoneState.ALL,
                sort,
                Direction.getValue(direction));
        return ok(manage.render("마일스톤 관리", milestones, projectName, sort, direction, project));
    }

    public static Result editMilestone(String projectName, Long milestoneId) {
        Project project = Project.findByName(projectName);
        if(project == null ) {
            return notFound();
        }

        Milestone milestone = Milestone.findById(milestoneId);
        Form<Milestone> editForm = new Form<Milestone>(Milestone.class).fill(milestone);
        return ok(edit.render("마일스톤 수정", editForm, projectName, milestoneId, project));
    }

    public static Result updateMilestone(String projectName, Long milestoneId) {
        Form<Milestone> milestoneForm = new Form<Milestone>(Milestone.class).bindFromRequest();
        if (milestoneForm.hasErrors()) {
            return ok(edit.render("마일스톤 수정", milestoneForm, projectName, milestoneId, Project.findByName(projectName)));
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

        if(project.id != Milestone.findById(id).projectId) {
            return internalServerError();
        }
        Milestone.delete(id);
        return redirect(routes.MilestoneApp.manageMilestones(projectName, "dueDate", Direction.ASC.name()));
    }
}
