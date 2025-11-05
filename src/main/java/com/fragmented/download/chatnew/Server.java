package com.fragmented.download.chatnew;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Server {

    private static final int PORT = 8080;
    // Lưu trữ user/pass, key: username, value: password
    private final ConcurrentHashMap<String, String> users = new ConcurrentHashMap<>();
    // Lưu trữ các client đang online, key: username, value: ClientHandler
    private final ConcurrentHashMap<String, ClientHandler> onlineClients = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<ClientHandler>> chatGroups = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        new Server().startServer();
    }

    public void startServer() {
        // Dữ liệu mẫu
        users.put("admin", "admin");
        users.put("user1", "pass1");
        users.put("user2", "pass2");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server is listening on port " + PORT);
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("New client connected: " + socket.getInetAddress().getHostAddress());
                ClientHandler clientHandler = new ClientHandler(socket, this);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            System.err.println("Server exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Gửi tin nhắn đến tất cả client đang online
    public void broadcastMessage(String message, ClientHandler excludeClient) {
        for (ClientHandler client : onlineClients.values()) {
            if (client != excludeClient) {
                client.sendMessage(message);
            }
        }
    }

    // Thêm client vào danh sách online
    public void addOnlineClient(String username, ClientHandler handler) {
        onlineClients.put(username, handler);
        System.out.println("User '" + username + "' is now online.");
        updateOnlineUsers();
    }

    public void removeClientFromAllGroups(ClientHandler clientHandler) {
        String username = clientHandler.getUsername();
        if (username == null) return; // Should not happen if called after login

        for (Map.Entry<String, List<ClientHandler>> entry : chatGroups.entrySet()) {
            String groupName = entry.getKey();
            List<ClientHandler> members = entry.getValue();
            if (members.remove(clientHandler)) {
                System.out.println("User '" + username + "' removed from group '" + groupName + "'.");
                broadcastGroupUpdate(groupName); // Notify remaining members
            }
        }
    }

    // Xóa client khỏi danh sách online
    public void removeOnlineClient(ClientHandler clientHandler) {
        String username = clientHandler.getUsername();
        if (username == null) return; // Should not happen if called after login

        removeClientFromAllGroups(clientHandler); // First, remove from all groups
        onlineClients.remove(username);
        System.out.println("User '" + username + "' has gone offline.");
        updateOnlineUsers();
    }

    // Cập nhật danh sách người dùng online cho tất cả mọi người
    private void updateOnlineUsers() {
        String userList = String.join(",", onlineClients.keySet());
        broadcastMessage("UPDATE_USERS::" + userList, null);
    }

    // Getter và các phương thức kiểm tra
    public ConcurrentHashMap<String, String> getUsers() {
        return users;
    }

    public void updateUserGroupLists(ClientHandler userHandler) {
        String username = userHandler.getUsername();
        if (username == null) return;

        List<String> userGroups = new ArrayList<>();
        for (Map.Entry<String, List<ClientHandler>> entry : chatGroups.entrySet()) {
            if (entry.getValue().contains(userHandler)) {
                userGroups.add(entry.getKey());
            }
        }
        String groupList = String.join(",", userGroups);
        userHandler.sendMessage("UPDATE_GROUPS::" + groupList);
    }

    public void broadcastGroupUpdate(String groupName) {
        List<ClientHandler> members = chatGroups.get(groupName);
        if (members != null) {
            for (ClientHandler member : members) {
                updateUserGroupLists(member);
            }
        }
    }

    public ConcurrentHashMap<String, ClientHandler> getOnlineClients() {
        return onlineClients;
    }

    public ConcurrentHashMap<String, List<ClientHandler>> getChatGroups() {
        return chatGroups;
    }

    public boolean isUserOnline(String username) {
        return onlineClients.containsKey(username);
    }
}
