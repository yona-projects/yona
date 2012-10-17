package controllers;

import java.util.ArrayList;
import java.util.HashMap;

import org.codehaus.jackson.JsonNode;

import play.Logger;
import play.libs.F.Callback;
import play.libs.F.Callback0;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.WebSocket;
import views.html.task.*;

public class TaskApp extends Controller {
    public static Result index(String userName, String projectName) {
        return ok(taskView.render(ProjectApp.getProject(userName, projectName)));
    }

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

            // When the socket is closed.
            in.onClose(this);

            this.out = out;
            // Send a single 'Hello!' message
            out.write("Hello!");
        }

        public void sendMessage(String msg) {
            out.write(msg);
        }

        @Override
        public void invoke(String event) throws Throwable {
            // out.write()로 응답

            JsonNode data = Json.parse(event);
            for (int i = 0; i < data.size(); i++) {
                Logger.info(data.get(i).findValue("_id").asText());
            }

            // model에 뭔가 저장한다.
            // 같은 것에 접속해 있는 모든사람에게 노티를 보낸다.
            this.server.sendNotify(this, event);
        }

        @Override
        public void invoke() throws Throwable {
            // 닫혔을떄.
            this.server.removeWebSocket(this);
            Logger.info("Disconnected");
        }

    }
}
