package controllers.webSocket;

import models.task.TaskBoard;
import play.Logger;
import play.libs.F.Callback;
import play.libs.F.Callback0;
import play.libs.Json;
import play.mvc.WebSocket;
import controllers.ProjectApp;

public class WebSocketConnector extends WebSocket<String> implements Callback<String>,
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
