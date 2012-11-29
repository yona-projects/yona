package controllers;

import java.util.List;
import java.util.Map;

import controllers.ProjectApp;
import models.IssueLabel;
import models.Project;
import models.Issue;
import models.enumeration.Operation;
import models.enumeration.Resource;
import play.mvc.Controller;
import play.mvc.Result;
import utils.AccessControl;
import utils.RequestUtil;
import static play.libs.Json.toJson;

import play.data.*;
import java.util.ArrayList;
import java.util.HashMap;

public class IssueLabelApp extends Controller {

    public static Result getAll(String userName, String projectName) {
        Project project = ProjectApp.getProject(userName, projectName);

        List<Map<String, String>> labels = new ArrayList<Map<String, String>>();
        for (IssueLabel label : IssueLabel.findByProjectId(project.id)) {
            Map<String, String> labelPropertyMap = new HashMap<String, String>();
            labelPropertyMap.put("id", "" + label.id);
            labelPropertyMap.put("category", label.category);
            labelPropertyMap.put("color", label.color);
            labelPropertyMap.put("name", label.name);
            labels.add(labelPropertyMap);
        }

        response().setHeader("Content-Type", "application/json");
        return ok(toJson(labels));
    }

    public static Result post(String userName, String projectName) {
        Form<IssueLabel> labelForm = new Form<IssueLabel>(IssueLabel.class).bindFromRequest();

        IssueLabel label = labelForm.get();
        label.project = ProjectApp.getProject(userName, projectName);

        if (label.exists()) {
            return ok();
        } else {
            label.save();

            response().setHeader("Content-Type", "application/json");

            Map<String, String> labelPropertyMap = new HashMap<String, String>();
            labelPropertyMap.put("id", "" + label.id);
            labelPropertyMap.put("name", label.name);
            labelPropertyMap.put("color", label.color);
            labelPropertyMap.put("category", label.category);

            return created(toJson(labelPropertyMap));
        }
    }

    public static Result delete(String userName, String projectName, Long id) {
        // _method must be 'delete'
        DynamicForm bindedForm = form().bindFromRequest();
        if (!bindedForm.get("_method").toLowerCase()
                .equals("delete")) {
            return badRequest("_method must be 'delete'.");
        }

        IssueLabel label = IssueLabel.findById(id);

        if (label == null) {
            return notFound();
        }

        if (!AccessControl.isAllowed(UserApp.currentUser().id, label.project.id,
            Resource.ISSUE_LABEL, Operation.DELETE, label.id)) {
            return forbidden();
        }

        label.delete();

        return ok();
    }
}
