/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @author Tae
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
import controllers.annotation.IsAllowed;
import controllers.annotation.IsCreatable;
import models.Attachment;
import models.Milestone;
import models.Project;
import models.enumeration.Direction;
import models.enumeration.Operation;
import models.enumeration.ResourceType;
import models.enumeration.State;
import play.data.Form;
import play.db.ebean.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import utils.*;
import views.html.milestone.create;
import views.html.milestone.edit;
import views.html.milestone.list;
import views.html.milestone.view;

import java.util.List;

import static play.data.Form.form;

@AnonymousCheck
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

    /**
     * when: GET /:user/:project/milestones
     */
    @IsAllowed(Operation.READ)
    public static Result milestones(String userName, String projectName) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        MilestoneCondition mCondition = form(MilestoneCondition.class).bindFromRequest().get();

        List<Milestone> milestones = Milestone.findMilestones(project.id,
                State.getValue(mCondition.state),
                mCondition.orderBy,
                Direction.getValue(mCondition.orderDir));

        return ok(list.render("title.milestoneList", milestones, project, mCondition));
    }

    /**
     * when: GET /:user/:project/newMilestoneForm
     */
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @IsCreatable(ResourceType.MILESTONE)
    public static Result newMilestoneForm(String userName, String projectName) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        return ok(create.render("title.newMilestone", new Form<>(Milestone.class), project));
    }

    /**
     * when: POST /:user/:project/milestones
     *
     * @see {@link #validateTitle(models.Project, play.data.Form)}
     */
    @Transactional
    @IsCreatable(ResourceType.MILESTONE)
    public static Result newMilestone(String userName, String projectName) {
        Form<Milestone> milestoneForm = new Form<>(Milestone.class).bindFromRequest();
        Project project = Project.findByOwnerAndProjectName(userName, projectName);

        validateTitle(project, milestoneForm);
        validateDueDate(milestoneForm);
        if (milestoneForm.hasErrors()) {
            return ok(create.render("title.newMilestone", milestoneForm, project));
        } else {
            Milestone newMilestone = milestoneForm.get();

            if (newMilestone.contents == null) {
                return status(REQUEST_ENTITY_TOO_LARGE,
                        ErrorViews.RequestTextEntityTooLarge.render());
            }

            newMilestone.project = project;
            newMilestone.dueDate = JodaDateUtil.lastSecondOfDay(newMilestone.dueDate);
            Milestone.create(newMilestone);
            Attachment.moveAll(UserApp.currentUser().asResource(), newMilestone.asResource());
            return redirect(routes.MilestoneApp.milestone(userName, projectName, newMilestone.id));
        }
    }

    private static void validateTitle(Project project, Form<Milestone> milestoneForm) {
        if (!Milestone.isUniqueProjectIdAndTitle(project.id, milestoneForm.field("title").value())) {
            milestoneForm.reject("title", "milestone.title.duplicated");
            flash(Constants.WARNING, "milestone.title.duplicated");
        }
    }

    private static void validateDueDate(Form<Milestone> milestoneForm) {
        if (milestoneForm.hasErrors() && milestoneForm.errors().containsKey("dueDate")) {
            flash(Constants.WARNING, "milestone.error.duedateFormat");
        }
    }

    /**
     * when: GET /:user/:project/milestone/:id/editform
     */
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @IsAllowed(value = Operation.UPDATE, resourceType = ResourceType.MILESTONE)
    public static Result editMilestoneForm(String userName, String projectName, Long milestoneId) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        Milestone milestone = Milestone.findById(milestoneId);

        Form<Milestone> editForm = new Form<>(Milestone.class).fill(milestone);
        return ok(edit.render("title.editMilestone", editForm, milestoneId, project));
    }

    /**
     * when: POST /:user/:project/milestone/:id/edit
     */
    @Transactional
    @IsAllowed(value = Operation.UPDATE, resourceType = ResourceType.MILESTONE)
    public static Result editMilestone(String userName, String projectName, Long milestoneId) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        Form<Milestone> milestoneForm = new Form<>(Milestone.class).bindFromRequest();
        Milestone original = Milestone.findById(milestoneId);

        if(!original.title.equals(milestoneForm.field("title").value())) {
            validateTitle(project, milestoneForm);
        }
        validateDueDate(milestoneForm);
        if (milestoneForm.hasErrors()) {
            return ok(edit.render("title.editMilestone", milestoneForm, milestoneId, project));
        } else {
            Milestone existingMilestone = Milestone.findById(milestoneId);
            Milestone milestone = milestoneForm.get();

            if (milestone.contents == null) {
                return status(REQUEST_ENTITY_TOO_LARGE,
                        ErrorViews.RequestTextEntityTooLarge.render());
            }

            milestone.dueDate = JodaDateUtil.lastSecondOfDay(milestone.dueDate);
            existingMilestone.updateWith(milestone);
            Attachment.moveAll(UserApp.currentUser().asResource(), existingMilestone.asResource());
            return redirect(routes.MilestoneApp.milestone(userName, projectName, existingMilestone.id));
        }
    }

    /**
     * when: GET /:user/:project/milestone/:id/delete
     */
    @Transactional
    @IsAllowed(value = Operation.DELETE, resourceType = ResourceType.MILESTONE)
    public static Result deleteMilestone(String userName, String projectName, Long id) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        Milestone milestone = Milestone.findById(id);

        if(!project.id.equals(milestone.project.id)) {
            return internalServerError();
        }
        milestone.delete();

        if(HttpUtil.isRequestedWithXHR(request())){
            response().setHeader("Location", routes.MilestoneApp.milestones(userName, projectName).toString());
            return status(204);
        }

        return redirect(routes.MilestoneApp.milestones(userName, projectName));
    }

    @Transactional
    @IsAllowed(value = Operation.UPDATE, resourceType = ResourceType.MILESTONE)
    public static Result open(String userName, String projectName, Long id) {
        Milestone milestone = Milestone.findById(id);
        milestone.open();
        return redirect(routes.MilestoneApp.milestone(userName, projectName, id));
    }

    @Transactional
    @IsAllowed(value = Operation.UPDATE, resourceType = ResourceType.MILESTONE)
    public static Result close(String userName, String projectName, Long id) {
        Milestone milestone = Milestone.findById(id);
        milestone.close();
        return redirect(routes.MilestoneApp.milestone(userName, projectName, id));
    }

    /**
     * when: GET /:user/:project/milestone/:id
     */
    @IsAllowed(value = Operation.READ, resourceType = ResourceType.MILESTONE)
    public static Result milestone(String userName, String projectName, Long id) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        Milestone milestone = Milestone.findById(id);

        String paramState = request().getQueryString("state");
        State state = State.getValue(paramState);
        UserApp.currentUser().visits(project);
        return ok(view.render(milestone.title, milestone, project, state));
    }
}
