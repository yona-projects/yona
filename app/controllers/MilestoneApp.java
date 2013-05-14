package controllers;

import models.*;
import models.enumeration.*;
import play.data.*;
import play.mvc.*;
import utils.Constants;
import views.html.milestone.*;

import java.util.*;

import static play.data.Form.form;

/**
 * 마일스톤 관리 컨트로러
 */
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
     * {@code userName}과 {@code projectName}에 해당하는 프로젝트의 마일스톤 목록을 조회한다.
     *
     * when: GET /:user/:project/milestones
     *
     * {@link MilestoneCondition} 폼에서 입력받은 값으로 기본 검색 조건 및 정렬 조건을 적용한다.
     *
     * @param userName
     * @param projectName
     * @return
     */
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

    /**
     * {@code userName}과 {@code projectName}에 해당하는 프로젝트에
     * 새 마일스톤을 추가할 수 있는 입력 폼으로 이동한다.
     *
     * when: GET /:user/:project/newMilestoneForm
     *
     * 해당 프로젝트를 찾지 못할 때는 {@link #notFound()}를 반환한다.
     *
     * @param userName
     * @param projectName
     * @return
     */
    public static Result newMilestoneForm(String userName, String projectName) {
        Project project = ProjectApp.getProject(userName, projectName);
        if(project == null ) {
            return notFound();
        }

        return ok(create.render("title.newMilestone", new Form<>(Milestone.class), project));
    }

    /**
     * {@code userName}과 {@code projectName}에 해당하는 프로젝트에
     * 새 마일스톤을 추가한다.
     *
     * when: POST /:user/:project/milestones
     *
     * {@link Milestone} 폼으로 입력받은 데이터를 사용해서 새 마일스톤을 생성한다.
     * 해당 프로젝트를 찾지 못할 때는 {@link #notFound()}를 반환한다.
     * 같은 이름을 가진 마일스톤이 있는지 확인한다.
     * 같은 이름을 가진 마일스톤이 있을 경우 새 마일스톤 입력 폼으로 다시 이동한다.
     *
     * @param userName
     * @param projectName
     * @return
     * @see {@link #validate(models.Project, play.data.Form)}
     */
    public static Result newMilestone(String userName, String projectName) {
        Form<Milestone> milestoneForm = new Form<>(Milestone.class).bindFromRequest();
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

    /**
     * {@code project}에 동일한 이름을 가진 마일스톤이 있는지 확인한다.
     *
     * 동일한 이름을 가진 마일스톤이 있을 경우 마일스톤 이름 중복 에러 메시지를 플래시 스코프에 담는다.
     *
     * @param project
     * @param milestoneForm
     */
    private static void validate(Project project, Form<Milestone> milestoneForm) {
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

    /**
     * {@code userName}과 {@code projectName}에 해당하는 프로젝트에
     * {@code milestoneId}에 해당하는 마일스톤 수정 화면으로 이동한다.
     *
     * when: GET /:user/:project/milestone/:id/editform
     *
     * 해당 프로젝트를 찾지 못할 때는 {@link #notFound()}를 반환한다.
     *
     * @param userName
     * @param projectName
     * @param milestoneId
     * @return
     */
    public static Result editMilestoneForm(String userName, String projectName, Long milestoneId) {
        Project project = ProjectApp.getProject(userName, projectName);
        if(project == null ) {
            return notFound();
        }
        Milestone milestone = Milestone.findById(milestoneId);
        Form<Milestone> editForm = new Form<>(Milestone.class).fill(milestone);
        return ok(edit.render("title.editMilestone", editForm, milestoneId, project));
    }

    /**
     * {@code userName}과 {@code projectName}에 해당하는 프로젝트에
     * {@code milestoneId}에 해당하는 마일스톤을 갱신한다.
     *
     * when: POST /:user/:project/milestone/:id/edit
     *
     * 마일스톤 이름을 변경한 경우에는 이름이 중복되는지 확인한다.
     * 해당 프로젝트를 찾지 못할 때는 {@link #notFound()}를 반환한다.
     *
     * @param userName
     * @param projectName
     * @param milestoneId
     * @return
     */
    public static Result editMilestone(String userName, String projectName, Long milestoneId) {
        Project project = ProjectApp.getProject(userName, projectName);
        if(project == null ) {
            return notFound();
        }
        Form<Milestone> milestoneForm = new Form<>(Milestone.class).bindFromRequest();
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

    /**
     * {@code userName}과 {@code projectName}에 해당하는 프로젝트에
     * {@code milestoneId}에 해당하는 마일스톤을 삭제한다.
     *
     * when: GET /:user/:project/milestone/:id/delete
     *
     * 해당 프로젝트를 찾지 못할 때는 {@link #notFound()}를 반환한다.
     * 프로젝트의 아이디와 마일스톤이 가지고 있는 프로젝트 레퍼런스의 아이디가 다를 경우에
     * {@link #internalServerError()}를 반환한다.
     *
     * @param userName
     * @param projectName
     * @param id
     * @return
     */
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
