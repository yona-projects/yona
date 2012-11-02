package controllers.webSocket;

import java.util.ArrayList;
import java.util.HashMap;

public class WebSocketServer {

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
