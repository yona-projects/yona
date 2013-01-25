package controllers;

import java.util.Map;

import models.Project;
import models.enumeration.Operation;
import models.task.Card;
import models.task.Line;
import models.task.TaskBoard;
import models.task.TaskComment;

import org.codehaus.jackson.JsonNode;

import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.WebSocket;
import utils.AccessControl;
import views.html.task.cardView;
import views.html.task.taskView;
import controllers.webSocket.WebSocketServer;

public class TaskApp extends Controller {
    public static Result index(String userName, String projectName) {
        Project project = ProjectApp.getProject(userName, projectName);
        if (!AccessControl.isAllowed(UserApp.currentUser(), project.asResource(), Operation.READ)) {
            return unauthorized(views.html.project.unauthorized.render(project));
        }
        return ok(taskView.render(project));
    }

    public static Result card(String userName, String projectName, Long cardId) {
        Project project = ProjectApp.getProject(userName, projectName);
        if (!AccessControl.isAllowed(UserApp.currentUser(), project.asResource(), Operation.READ)) {
            return unauthorized(views.html.project.unauthorized.render(project));
        }

        return ok(Card.findById(cardId).toJSON());
    }

    public static Result getLabels(String userName, String projectName) {
        Project project = ProjectApp.getProject(userName, projectName);
        if (!AccessControl.isAllowed(UserApp.currentUser(), project.asResource(), Operation.READ)) {
            return unauthorized(views.html.project.unauthorized.render(project));
        }
        TaskBoard taskBoard = TaskBoard.findByProject(project);
        return ok(taskBoard.getLabel());
    }

    public static Result getMember(String userName, String projectName) {
        Project project = ProjectApp.getProject(userName, projectName);
        if (!AccessControl.isAllowed(UserApp.currentUser(), project.asResource(), Operation.READ)) {
            return unauthorized(views.html.project.unauthorized.render(project));
        }
        TaskBoard taskBoard = TaskBoard.findByProject(project);
        return ok(taskBoard.getMember());
    }

    // TestCode 나중에 전부 웹소켓으로 바꾼다. 당연히 그걸 고려해서 짜야한다.
    public static Result cardView(String userName, String projectName) {
        Project project = ProjectApp.getProject(userName, projectName);
        if (!AccessControl.isAllowed(UserApp.currentUser(), project.asResource(), Operation.READ)) {
            return unauthorized(views.html.project.unauthorized.render(project));
        }
        return ok(cardView.render(project));
    }

    @BodyParser.Of(BodyParser.Json.class)
    public static Result newCard(String userName, String projectName) {
        JsonNode json = request().body().asJson();
        Long line_id = json.findPath("line_id").asLong();
        Line line = Line.findById(line_id);
        Card card = new Card();
        card.title = json.get("card_data").asText();
        line.addCard(card);
        json = card.toJSON();
        return ok(json);
    }

    @BodyParser.Of(BodyParser.Json.class)
    public static Result saveCard(String userName, String projectName) {
        JsonNode json = request().body().asJson();
        Long cardid = json.findPath("_id").asLong();
        Card.findById(cardid).accecptJSON(json);
        return ok();
    }

    public static Result addComment(String userName, String projectName) {
        Map<String, String[]> data = request().body().asFormUrlEncoded();
        Long cardid = Long.parseLong(data.get("_id")[0]);
        String body = data.get("body")[0];
        Card card = Card.findById(cardid);

        TaskComment comment = new TaskComment();
        comment.body = body;
        // ProjectUser 추가방법 생각할것.

        card.addComment(comment);

        return ok();
    }

    // TestCode End

    public static WebSocket<String> connect(String userName, String projectName) {
        return WebSocketServer.handelWebSocket(userName, projectName);
    }

}