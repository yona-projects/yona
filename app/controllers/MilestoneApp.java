package controllers;

import models.Milestone;
import play.mvc.Controller;
import play.mvc.Result;
import views.html.milestone.list;

public class MilestoneApp extends Controller {
    public static Result index() {
        return redirect(routes.MilestoneApp.milestoneList(1));
    }

    public static Result milestoneList(Long projectId) {
        return ok(list.render("마일스톤 리스트",Milestone.findByProjectId(projectId)));
        //find.orderBy("id").findList()));
    }

    public static Result addMilestone() {
        return TODO;
        /*
          Form<Milestone> filledForm = milestoneForm.bindFromRequest();
          if(filledForm.hasErrors()){
              return badRequest(
                      index.render(Milestone.all(), filledForm)
                      );
          }else{
              Milestone.create(filledForm.get());
              return redirect(routes.MilestoneApp.milestoneList());
          }
          */
    }

    public static Result addMilestoneSave() {
        return TODO;

    }

    public static Result deleteMilestone(Long id) {
        return TODO;
    }
}
