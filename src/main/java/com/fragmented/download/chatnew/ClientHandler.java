package com.fragmented.download.chatnew;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private final Server server;
    private PrintWriter writer;
    private BufferedReader reader;
    private String username;

    public ClientHandler(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);

            String message;
            while ((message = reader.readLine()) != null) {
                System.out.println("Received from client " + socket.getInetAddress() + ": " + message);
                handleMessage(message);
            }
        } catch (IOException e) {
            // Lỗi xảy ra (ví dụ: client ngắt kết nối đột ngột)
            System.out.println("Client disconnected: " + socket.getInetAddress());
        } finally {
            if (username != null) {
                server.removeOnlineClient(this);
            }
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleMessage(String message) {
        String[] parts = message.split("::", 3); // Split into command, target, and content
        String command = parts[0];

        // User must be authenticated for most commands
        if (this.username == null && !command.equals("AUTH")) {
            sendMessage("ERROR::Authentication required.");
            return;
        }

        switch (command) {
            case "AUTH":
                if (parts.length == 3) { // Should be AUTH::type::user::pass, so split by 4
                    String[] authParts = message.split("::", 4);
                    if(authParts.length == 4) handleAuth(authParts);
                } else {
                    sendMessage("ERROR::Invalid AUTH command format.");
                }
                break;
            case "BROADCAST":
                if (parts.length == 3) {
                    server.broadcastMessage("MESSAGE::" + this.username + "::" + parts[2], this);
                }
                break;
            case "UNICAST":
                if (parts.length == 3) {
                    handleUnicast(parts[1], parts[2]);
                }
                break;
            case "MULTICAST":
                if (parts.length == 3) {
                    handleMulticast(parts[1], parts[2]);
                }
                break;
            case "CREATE_GROUP":
                if (parts.length == 2) {
                    handleCreateGroup(parts[1]);
                }
                break;
            case "INVITE_TO_GROUP":
                if (parts.length == 3) {
                    handleInviteToGroup(parts[1], parts[2]);
                }
                break;
            case "JOIN_GROUP":
                if (parts.length == 2) {
                    handleJoinGroup(parts[1]);
                }
                break;
            case "LEAVE_GROUP":
                if (parts.length == 2) {
                    handleLeaveGroup(parts[1]);
                }
                break;
            default:
                sendMessage("ERROR::Unknown command");
                break;
        }
    }

    private void handleUnicast(String recipient, String content) {
        ClientHandler recipientHandler = server.getOnlineClients().get(recipient);
        if (recipientHandler != null) {
            recipientHandler.sendMessage("UNICAST_MESSAGE::" + this.username + "::" + content);
        } else {
            sendMessage("ERROR::User '" + recipient + "' is not online.");
        }
    }

    private void handleMulticast(String groupName, String content) {
        List<ClientHandler> groupMembers = server.getChatGroups().get(groupName);
        if (groupMembers != null && groupMembers.contains(this)) {
            String message = String.format("MULTICAST_MESSAGE::%s::%s::%s", groupName, this.username, content);
            for (ClientHandler member : groupMembers) {
                // The client will handle displaying their own message
                if (member != this) {
                    member.sendMessage(message);
                }
            }
        } else {
            sendMessage("ERROR::You are not a member of group '" + groupName + "'.");
        }
    }

    private void handleCreateGroup(String groupName) {
        // Use computeIfAbsent to atomically create the group and add the creator
        List<ClientHandler> members = server.getChatGroups().computeIfAbsent(groupName, k -> new CopyOnWriteArrayList<>());
        if (!members.contains(this)) {
            members.add(this);
        }
        server.broadcastGroupUpdate(groupName); // Update all members in the group
    }

    private void handleInviteToGroup(String groupName, String inviteeName) {
        ClientHandler invitee = server.getOnlineClients().get(inviteeName);
        if (invitee != null) {
            List<ClientHandler> members = server.getChatGroups().get(groupName);
            if (members != null && members.contains(this)) {
                invitee.sendMessage("GROUP_INVITE::" + groupName + "::" + this.username);
            } else {
                sendMessage("ERROR::You are not a member of group '" + groupName + "'.");
            }
        } else {
            sendMessage("ERROR::User '" + inviteeName + "' is not online.");
        }
    }

    private void handleJoinGroup(String groupName) {
        List<ClientHandler> members = server.getChatGroups().get(groupName);
        if (members != null) {
            if (!members.contains(this)) {
                members.add(this);
            }
            server.broadcastGroupUpdate(groupName); // Update all members
        } else {
            sendMessage("ERROR::Group '" + groupName + "' does not exist.");
        }
    }

    private void handleLeaveGroup(String groupName) {
        List<ClientHandler> members = server.getChatGroups().get(groupName);
        if (members != null) {
            members.remove(this);
            // Notify remaining members
            server.broadcastGroupUpdate(groupName);
            // Confirm leaving to the user
            sendMessage("LEAVE_SUCCESS::" + groupName);
        }
    }

    private void handleAuth(String[] parts) {
        String authType = parts[1];
        String user = parts[2];
        String pass = parts[3];

        if (authType.equals("dangnhap")) {
            if (server.getUsers().containsKey(user) && server.getUsers().get(user).equals(pass)) {
                if (server.isUserOnline(user)) {
                    sendMessage("AUTH_FAIL::User is already logged in.");
                } else {
                    this.username = user;
                    server.addOnlineClient(user, this);
                    sendMessage("AUTH_SUCCESS");
                    // Send the user their current group list
                    server.updateUserGroupLists(this);
                }
            } else {
                sendMessage("AUTH_FAIL::Invalid username or password.");
            }
        } else if (authType.equals("dangky")) {
            if (server.getUsers().containsKey(user)) {
                sendMessage("AUTH_FAIL::Username already exists.");
            } else {
                server.getUsers().put(user, pass);
                sendMessage("AUTH_SUCCESS::Registration successful. Please log in.");
            }
        }
    }

    // Gửi tin nhắn đến client này
    public void sendMessage(String message) {
        writer.println(message);
    }

    public String getUsername() {
        return username;
    }
}
