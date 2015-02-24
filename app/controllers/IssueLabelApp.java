/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @author Yi EungJun
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
import models.IssueLabel;
import models.IssueLabelCategory;
import models.Project;
import models.enumeration.Operation;
import models.enumeration.ResourceType;
import play.data.DynamicForm;
import play.data.Form;
import play.data.validation.Constraints;
import play.db.ebean.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import utils.ErrorViews;
import utils.HttpUtil;

import javax.validation.constraints.Size;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static play.data.Form.form;
import static play.libs.Json.toJson;

@AnonymousCheck
public class IssueLabelApp extends Controller {
    /**
     * Responds to a request for issue labels of the specified project.
     *
     * This method is used when put a label on an issue and list labels in
     * issue list page.
     *
     * Returns 403 Forbidden if the user has no permission to access to the
     * project.
     *
     * @param ownerName    the name of a project owner
     * @param projectName  the name of a project
     * @return the response to the request for issue labels
     */
    @IsAllowed(Operation.READ)
    public static Result labels(String ownerName, String projectName) {
        if (HttpUtil.isPJAXRequest(request())){
            return labelsAsPjax(ownerName, projectName);
        }

        return labelsAsJSON(ownerName, projectName);
    }

    /**
     * Retrieves a project corresponding to {@code ownerName} and {@code
     * projectName}, and returns its list of all issue labels in {@code
     * application/json}. Each label has four fields: {@link IssueLabel#id},
     * {@link IssueLabel#category}, {@link IssueLabel#color}, and {@link
     * IssueLabel#name}.
     *
     * Returns 406 Not Acceptable if the client cannot accept
     * {@code application/json}. Success response can only be returned when the
     * content type of the body is {@code application/json}.
     *
     * @param ownerName
     * @param projectName
     * @return the response to the request for issue labels
     */
    private static Result labelsAsJSON(String ownerName, String projectName) {
        if (!request().accepts("application/json")) {
            return status(Http.Status.NOT_ACCEPTABLE);
        }

        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);

        List<Map<String, String>> labels = new ArrayList<>();
        for (IssueLabel label : IssueLabel.findByProject(project)) {
            Map<String, String> labelPropertyMap = new HashMap<>();
            labelPropertyMap.put("id", "" + label.id);
            labelPropertyMap.put("category", label.category.name);
            labelPropertyMap.put("categoryId", "" + label.category.id);
            labelPropertyMap.put("color", label.color);
            labelPropertyMap.put("name", label.name);
            labels.add(labelPropertyMap);
        }

        response().setHeader("Content-Type", "application/json");
        return ok(toJson(labels));
    }

    private static Result labelsAsPjax(String ownerName, String projectName){
        response().setHeader("Cache-Control", "no-cache, no-store");

        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);
        List<IssueLabel> labels = IssueLabel.findByProject(project);

        return ok(views.html.project.partial_issuelabels_list.render(project, labels));
    }

    @IsAllowed(Operation.UPDATE)
    public static Result labelsForm(String ownerName, String projectName){
        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);
        List<IssueLabel> labels = IssueLabel.findByProject(project);

        return ok(views.html.project.issuelabels.render(project, labels));
    }

    public static class NewLabel {
        @Constraints.Required(message="label.error.labelName.empty")
        @Size(max=255, message="label.error.labelName.tooLongSize")
        public String labelName;

        @Constraints.Required(message="label.error.color.empty")
        public String labelColor;

        @Constraints.Required(message="label.error.categoryName.empty")
        @Size(max=255, message="label.error.categoryName.tooLongSize")
        public String categoryName;

        public boolean categoryIsExclusive;

        public IssueLabelCategory getIssueLabelCategory(Project project) {
            IssueLabelCategory category = new IssueLabelCategory();
            category.project = project;
            category.name = categoryName;
            category.isExclusive = categoryIsExclusive;
            return category;
        }

        public IssueLabel getIssueLabel(Project project, IssueLabelCategory category) {
            IssueLabel label = new IssueLabel();
            label.project = project;
            label.name = labelName;
            label.color = labelColor;
            label.category = category;
            return label;
        }
    }

    /**
     * Responds to a request to add an issue label for the specified project.
     *
     * This method is used when a user tries to add a issue label in issue list,
     * editing issue or new issue page.
     *
     * Adds an issue label created with values taken from
     * {@link Form#bindFromRequest(java.util.Map, String...)} in the project
     * specified by the {@code ownerName} and {@code projectName}. But if there
     * has already been the same issue label in category, name and color, then
     * this method returns an empty 204 No Content response.
     *
     * When a new label is added, this method encodes the label's fields:
     * {@link IssueLabel#id}, {@link IssueLabel#name}, {@link IssueLabel#color},
     * and {@link IssueLabel#category} into {@code application/json}, and
     * includes them in the body of the 201 Created response. But if the client
     * cannot accept {@code application/json}, it returns the 201 Created with
     * no response body.
     *
     * Returns 403 Forbidden, if the user has no permission to add a new label
     * in the project.
     *
     * @param ownerName    the name of a project owner
     * @param projectName  the name of a project
     * @return             the response to the request to add a new issue label
     */
    @Transactional
    @IsCreatable(ResourceType.ISSUE_LABEL)
    public static Result newLabel(String ownerName, String projectName) {
        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);

        Form<NewLabel> newLabelForm = form(NewLabel.class).bindFromRequest();

        if (newLabelForm.hasErrors()) {
            return badRequest(newLabelForm.errorsAsJson());
        }

        IssueLabelCategory category = newLabelForm.get().getIssueLabelCategory(project);

        if (category.exists()) {
            category = IssueLabelCategory.findBy(category);
        } else {
            category.save();
        }

        IssueLabel label = newLabelForm.get().getIssueLabel(project, category);

        if (label.exists()) {
            return noContent();
        } else {
            label.save();

            if (!request().accepts("application/json")) {
                return created();
            }

            response().setHeader("Content-Type", "application/json");

            Map<String, String> labelPropertyMap = new HashMap<>();
            labelPropertyMap.put("id", "" + label.id);
            labelPropertyMap.put("name", label.name);
            labelPropertyMap.put("color", label.color);
            labelPropertyMap.put("category", label.category.name);
            labelPropertyMap.put("categoryId", "" + label.category.id);

            return created(toJson(labelPropertyMap));
        }
    }

    /**
     * Responds to a request to delete the specified issue label.
     *
     * This method is used when a user click a button to delete a issue label
     * in issue list, editing issue or new issue page.
     *
     * Deletes an issue label corresponding to the given {@code id}.
     *
     * - Returns {@code 200 OK} if the issue label is deleted succesfully.
     * - Returns {@code 403 Forbidden} if the user has no permission to delete
     *   the issue label.
     * - Returns {@code 404 Not Found} if no issue label is found.
     *
     * The request must have a {@code _method} parameter and the parameter
     * value must be case-insensitive "delete"; otherwise, returns 400 Bad
     * Request. We use this trick because an HTML Form does not support the
     * DELETE method.
     *
     * @param ownerName    Don't use.
     * @param projectName  Don't use.
     * @param id           the id of the label to be deleted
     * @return             the response to the request to delete an issue label
     */
    @Transactional
    @IsAllowed(value = Operation.DELETE, resourceType = ResourceType.ISSUE_LABEL)
    public static Result delete(String ownerName, String projectName, Long id) {
        // _method must be 'delete'
        DynamicForm bindedForm = form().bindFromRequest();
        if (!bindedForm.get("_method").toLowerCase()
                .equals("delete")) {
            return badRequest(ErrorViews.BadRequest.render("_method must be 'delete'."));
        }

        IssueLabel label = IssueLabel.finder.byId(id);
        label.delete();
        return ok();
    }

    @IsAllowed(value = Operation.UPDATE, resourceType = ResourceType.ISSUE_LABEL)
    public static Result update(String ownerName, String projectName, Long id) {
        Form<IssueLabel> form = new Form<>(IssueLabel.class).bindFromRequest();

        if (form.hasErrors()) {
            return badRequest(form.errorsAsJson());
        }

        IssueLabel label = form.get();
        label.id = id;
        label.project = Project.findByOwnerAndProjectName(ownerName, projectName);

        label.update();

        return ok();
    }

    /**
     * Responds to a request for the CSS styles of all issue labels in the
     * specified project.
     *
     * This method is used when CSS styles of issue labels are required in any
     * page which uses issue label.
     *
     * @param ownerName    the name of a project owner
     * @param projectName  the name of a project
     * @return the response to the request for the css styles in text/css.
     */
    @IsAllowed(Operation.READ)
    public static Result labelStyles(String ownerName, String projectName) {
        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);
        List<IssueLabel> labels = IssueLabel.findByProject(project);

        String eTag = "\"" + labels.hashCode() + "\"";
        String ifNoneMatchValue = request().getHeader("If-None-Match");

        if(ifNoneMatchValue != null && ifNoneMatchValue.equals(eTag)) {
            response().setHeader("ETag", eTag);
            return status(NOT_MODIFIED);
        }

        response().setHeader("ETag", eTag);

        return ok(views.html.common.issueLabelColor.render(labels)).as("text/css");
    }

    /**
     * Responds to a request for issue label categories of the specified
     * project.
     *
     * Retrieves a project corresponding to {@code ownerName} and
     * {@code projectName}, and returns its list of all issue label categories
     * in {@code application/json}. Each category has three fields:
     * {@link IssueLabelCategory#id}, {@link IssueLabelCategory#name},
     * {@link IssueLabelCategory#isExclusive}.
     *
     * Returns 403 Forbidden if the user has no permission to access to the
     * project.
     *
     * Returns 406 Not Acceptable if the client cannot accept
     * {@code application/json}. Success response can only be returned when the
     * content type of the body is {@code application/json}.
     *
     * @param ownerName    the name of a project owner
     * @param projectName  the name of a project
     * @return the response to the request for issue label categories
     */
    @IsAllowed(Operation.READ)
    public static Result categories(String ownerName, String projectName) {
        if (!request().accepts("application/json")) {
            return status(Http.Status.NOT_ACCEPTABLE);
        }

        Project project =
            Project.findByOwnerAndProjectName(ownerName, projectName);

        List<Map<String, String>> categories = new ArrayList<>();
        for (IssueLabelCategory category
                : IssueLabelCategory.findByProject(project)) {
            categories.add(toMap(category));
        }

        return ok(toJson(categories)).as("application/json");
    }

    /**
     * Responds to a request for an issue label category.
     *
     * Returns 406 Not Acceptable if the client cannot accept
     * {@code application/json}. Success response can only be returned when the
     * content type of the body is {@code application/json}.
     *
     * @param ownerName    Don't use.
     * @param projectName  Don't use.
     * @param id           the id of the category
     * @return the response to the request for an issue label category
     */
    @IsAllowed(value = Operation.READ,
            resourceType = ResourceType.ISSUE_LABEL_CATEGORY)
    public static Result category(String ownerName, String projectName,
            Long id) {
        if (!request().accepts("application/json")) {
            return status(Http.Status.NOT_ACCEPTABLE);
        }

        Project project =
            Project.findByOwnerAndProjectName(ownerName, projectName);

        IssueLabelCategory category = IssueLabelCategory.find.byId(id);

        return ok(toJson(toMap(category))).as("application/json");
    }

    @IsAllowed(value = Operation.UPDATE,
            resourceType = ResourceType.ISSUE_LABEL_CATEGORY)
    public static Result updateCategory(String ownerName, String projectName,
            Long id) {
        Form<IssueLabelCategory> form =
            new Form<>(IssueLabelCategory.class).bindFromRequest();

        if (form.hasErrors()) {
            return badRequest(form.errorsAsJson());
        }

        IssueLabelCategory category = form.get();
        category.id = id;
        category.project =
            Project.findByOwnerAndProjectName(ownerName, projectName);

        category.update();

        return ok();
    }

    /**
     * Responds to a request to add an issue label category for the specified
     * project.
     *
     * Adds an issue label category created with values taken from
     * {@link Form#bindFromRequest(java.util.Map, String...)} in the project
     * specified by the {@code ownerName} and {@code projectName}. But if there
     * has already been the same issue label category in name, then this method
     * returns an empty 204 No Content response.
     *
     * When a new category is added, this method encodes the category's fields:
     * {@link IssueLabelCategory#id}, {@link IssueLabelCategory#name},
     * {@link IssueLabelCategory#isExclusive}, and includes them in the body of
     * the 201 Created response. But if the client cannot accept
     * {@code application/json}, it returns the 201 Created with no response
     * body.
     *
     * @param ownerName    the name of a project owner
     * @param projectName  the name of a project
     * @return             the response to the request to add a new issue label
     *                     category
     */
    @IsCreatable(ResourceType.ISSUE_LABEL_CATEGORY)
    public static Result newCategory(String ownerName, String projectName) {
        Form<IssueLabelCategory> form =
            new Form<>(IssueLabelCategory.class).bindFromRequest();

        if (form.hasErrors()) {
            return badRequest();
        }

        IssueLabelCategory category = form.get();

        category.project =
            Project.findByOwnerAndProjectName(ownerName, projectName);

        if (category.exists()) {
            return noContent();
        }

        category.save();

        if (!request().accepts("application/json")) {
            return created();
        }

        Map<String, String> categoryPropertyMap = new HashMap<>();
        categoryPropertyMap.put("id", "" + category.id);
        categoryPropertyMap.put("name", category.name);
        categoryPropertyMap.put("isExclusive", "" + category.isExclusive);

        return created(toJson(categoryPropertyMap)).as("application/json");
    }

    @Transactional
    @IsAllowed(value = Operation.DELETE, resourceType = ResourceType.ISSUE_LABEL_CATEGORY)
    public static Result deleteCategory(String ownerName, String projectName, Long id) {
        IssueLabelCategory.find.byId(id).delete();
        return ok();
    }

    private static Map<String, String> toMap(IssueLabelCategory category) {
        Map<String, String> map = new HashMap<>();
        map.put("id", "" + category.id);
        map.put("name", category.name);
        map.put("isExclusive", "" + category.isExclusive);
        return map;
    }
}
