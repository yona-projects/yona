package controllers;

import java.util.*;

import play.mvc.*;
import play.data.*;
import play.*;

import models.Milestone;

public class MilestoneApp extends Controller {

	static Form<Milestone> milestoneForm = form(Milestone.class);
	
	/*
	public static Result index() {
		
		return redirect(routes.MilestoneApp.milestoneList());
	}
	*/
	
	public static Result milestoneList(int pageNum) {
		
		
		return TODO;
	 	
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
