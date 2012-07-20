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

    public static Result milestones(Long projectId, String state,
                                    String sort, String direction) {
        List<Milestone> milestones = Milestone.findMilestones(projectId,
            MilestoneState.getValue(state),
            sort,
            Direction.getValue(direction));
        return ok(list.render("마일스톤 리스트", milestones, projectId, state, sort, direction));
    }

    public static Result newMilestone(Long projectId) {
        return ok(create.render("새 마일스톤", new Form<Milestone>(Milestone.class), projectId));
    }

    public static Result createMilestone(Long projectId) {
        Form<Milestone> milestoneForm = new Form<Milestone>(Milestone.class).bindFromRequest();
        if (milestoneForm.hasErrors()) {
            return ok(create.render("새 마일스톤", milestoneForm, projectId));
        } else {
            Milestone newMilestone = milestoneForm.get();
            newMilestone.project = Project.findById(projectId);
            Milestone.create(newMilestone);
            return redirect(routes.MilestoneApp.manageMilestones(projectId, "dueDate", Direction.ASC.name()));
        }
    }

    public static Result manageMilestones(Long projectId, String sort, String direction) {
        List<Milestone> milestones = Milestone.findMilestones(projectId,
            MilestoneState.ALL,
            sort,
            Direction.getValue(direction));
        return ok(manage.render("마일스톤 관리", milestones, projectId, sort, direction));
    }

    public static Result editMilestone(Long projectId, Long milestoneId) {
        Milestone milestone = Milestone.findById(milestoneId);
        Form<Milestone> editForm = new Form<Milestone>(Milestone.class).fill(milestone);
        return ok(edit.render("마일스톤 수정", editForm, projectId, milestoneId));
    }

    public static Result updateMilestone(Long projectId, Long milestoneId) {
        Form<Milestone> milestoneForm = new Form<Milestone>(Milestone.class).bindFromRequest();
        if (milestoneForm.hasErrors()) {
            return ok(edit.render("마일스톤 수정", milestoneForm, projectId, milestoneId));
        } else {
            Milestone existingMilestone = Milestone.findById(milestoneId);
            existingMilestone.updateWith(milestoneForm.get());
            Milestone.update(existingMilestone, milestoneId);
            return redirect(routes.MilestoneApp.manageMilestones(projectId, "dueDate", Direction.ASC.name()));
        }
    }

    public static Result deleteMilestone(Long pId, Long id) {
        Milestone.delete(id);
        return redirect(routes.MilestoneApp.manageMilestones(pId, "dueDate", Direction.ASC.name()));
    }
}
