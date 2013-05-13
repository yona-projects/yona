package controllers;

import java.util.List;
import java.util.Map;

import models.IssueLabel;
import models.Project;
import models.enumeration.Operation;
import models.enumeration.ResourceType;
import play.mvc.Controller;
import play.mvc.Result;
import utils.AccessControl;

import static play.libs.Json.toJson;

import play.data.*;
import java.util.ArrayList;
import java.util.HashMap;

import static play.data.Form.form;

public class IssueLabelApp extends Controller {

    public static Result labels(String userName, String projectName) {
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

    public static Result newLabel(String userName, String projectName) {
        Form<IssueLabel> labelForm = new Form<IssueLabel>(IssueLabel.class).bindFromRequest();

        Project project = ProjectApp.getProject(userName, projectName);

        if (!AccessControl.isProjectResourceCreatable(UserApp.currentUser(), project, ResourceType.ISSUE_LABEL)) {
            return forbidden();
        }

        IssueLabel label = labelForm.get();
        label.project = project;

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

        if (!AccessControl.isAllowed(UserApp.currentUser(), label.asResource(), Operation.DELETE)) {
            return forbidden();
        }

        label.delete();

        return ok();
    }
}
