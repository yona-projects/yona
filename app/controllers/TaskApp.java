package controllers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import models.Project;
import models.task.Card;
import models.task.TaskBoard;
import models.task.TaskComment;

import org.codehaus.jackson.JsonNode;

import play.Logger;
import play.libs.F.Callback;
import play.libs.F.Callback0;
import play.libs.Json;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.WebSocket;
import views.html.task.cardView;
import views.html.task.taskView;

public class TaskApp extends Controller {
    public static Result index(String userName, String projectName) {
        return ok(taskView.render(ProjectApp.getProject(userName, projectName)));
    }

    public static Result card(String userName, String projectName, Long cardId) {
        return ok(Card.findById(cardId).toJSON());
    }

    public static Result getLabels(String userName, String projectName) {
        Project project = ProjectApp.getProject(userName, projectName);
        TaskBoard taskBoard = TaskBoard.findByProject(project);
        return ok(taskBoard.getLabel());
    }

    public static Result getMember(String userName, String projectName) {
        Project project = ProjectApp.getProject(userName, projectName);
        TaskBoard taskBoard = TaskBoard.findByProject(project);
        return ok(taskBoard.getMember());
    }

    // TestCode
    public static Result cardTest(String userName, String projectName) {
        return ok(cardView.render());
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

        Logger.info("comment added!");
        return ok();
    }

    // TestCode End

    public static WebSocket<String> connect(String userName, String projectName) {
        return WebSocketServer.handelWebSocket(userName, projectName);
    }

    private static class WebSocketServer {

        private static HashMap<String, WebSocketServer> serverList = new HashMap<String, WebSocketServer>();

        public static WebSocketConnector handelWebSocket(String userName, String projectName) {
            String key = userName + "/" + projectName;
            WebSocketServer server = serverList.get(key);
            if (server == null) {
                server = new WebSocketServer(key);
            }
            WebSocketConnector webSocketConnector = new WebSocketConnector(userName, projectName,
                    server);
            server.addWebSocket(webSocketConnector);
            return webSocketConnector;
        }

        public WebSocketServer(String key) {
            serverList.put(key, this);
        }

        private ArrayList<WebSocketConnector> sockets = new ArrayList<WebSocketConnector>();

        private void addWebSocket(WebSocketConnector webSocket) {
            sockets.add(webSocket);
        }

        public void removeWebSocket(WebSocketConnector webSocket) {
            sockets.remove(webSocket);
        }

        public void sendNotify(WebSocketConnector that, String msg) {
            for (int i = 0; i < sockets.size(); i++) {
                WebSocketConnector socket = sockets.get(i);
                if (socket != that) {
                    socket.sendMessage(msg);
                }
            }
        }
    }

    private static class WebSocketConnector extends WebSocket<String> implements Callback<String>,
            Callback0 {

        private String userName;
        private String projectName;
        private play.mvc.WebSocket.Out<String> out;
        private WebSocketServer server;
        private TaskBoard taskBoard;

        public WebSocketConnector(String userName, String projectName,
                WebSocketServer webSocketServer) {
            this.userName = userName;
            this.projectName = projectName;
            this.server = webSocketServer;
        }

        @Override
        public void onReady(play.mvc.WebSocket.In<String> in, play.mvc.WebSocket.Out<String> out) {
            // For each event received on the socket,
            in.onMessage(this);
            in.onClose(this);

            this.out = out;

            taskBoard = TaskBoard.findByProject(ProjectApp.getProject(userName, projectName));
            out.write(Json.stringify(taskBoard.toJSON()));
        }

        public void sendMessage(String msg) {
            out.write(msg);
        }

        @Override
        public void invoke(String event) throws Throwable {
            // 클라이언트에서 모델을 보내올때
            this.server.sendNotify(this, event);
            this.taskBoard.accecptJSON(Json.parse(event));
        }

        @Override
        public void invoke() throws Throwable {
            // 닫혔을떄.
            this.server.removeWebSocket(this);
            Logger.info("Disconnected");
        }

    }
}
