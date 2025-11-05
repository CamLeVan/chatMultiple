package com.fragmented.download.chatnew;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class NetworkService {

    private static NetworkService instance;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    // Properties for data binding with UI
    private final ObservableList<String> onlineUsers = FXCollections.observableArrayList();
    private final ObservableList<String> joinedGroups = FXCollections.observableArrayList();
    private final StringProperty newRawMessage = new SimpleStringProperty();
    private final StringProperty incomingInvite = new SimpleStringProperty();

    private String errorMessage;

    private NetworkService() {}

    public static synchronized NetworkService getInstance() {
        if (instance == null) {
            instance = new NetworkService();
        }
        return instance;
    }

    public boolean connect(String ip, int port, String user, String pass, boolean isRegister) {
        try {
            if (socket != null && !socket.isClosed()) {
                closeConnection();
            }

            socket = new Socket(ip, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            String command = isRegister ? "AUTH::dangky::" : "AUTH::dangnhap::";
            String fullCommand = command + user + "::" + pass;
            out.println(fullCommand);

            String response = in.readLine();

            if (response != null && response.startsWith("AUTH_SUCCESS")) {
                startListening();
                return true;
            } else {
                errorMessage = (response != null) ? response.replace("AUTH_FAIL::", "") : "No response from server.";
                closeConnection();
                return false;
            }

        } catch (IOException e) {
            errorMessage = "Connection error: " + e.getMessage();
            return false;
        }
    }

    private void startListening() {
        Task<Void> listenerTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try {
                    String messageFromServer;
                    while ((messageFromServer = in.readLine()) != null) {
                        final String msg = messageFromServer;
                        Platform.runLater(() -> parseServerMessage(msg));
                    }
                } catch (IOException e) {
                    if (socket != null && !socket.isClosed()) {
                        Platform.runLater(() -> parseServerMessage("ERROR::Connection lost: " + e.getMessage()));
                    }
                }
                return null;
            }
        };

        Thread listenerThread = new Thread(listenerTask);
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    private void parseServerMessage(String message) {
        String[] parts = message.split("::", 2); // Tách lệnh và phần còn lại
        String command = parts[0];

        switch (command) {
            case "UPDATE_USERS":
                if (parts.length > 1) {
                    onlineUsers.clear();
                    onlineUsers.addAll(parts[1].split(","));
                }
                break;
            case "UPDATE_GROUPS":
                if (parts.length > 1) {
                    joinedGroups.clear();
                    joinedGroups.addAll(parts[1].split(","));
                } else {
                    joinedGroups.clear();
                }
                break;
            case "GROUP_INVITE":
                if (parts.length > 1) {
                    incomingInvite.set(parts[1]);
                }
                break;
            case "LEAVE_SUCCESS":
                if (parts.length > 1) {
                    joinedGroups.remove(parts[1]);
                }
                break;
            default:
                newRawMessage.set(message);
                break;
        }
    }

    public StringProperty getIncomingInviteProperty() {
        return incomingInvite;
    }

    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    // Getters for JavaFX properties
    public ObservableList<String> getOnlineUsers() {
        return onlineUsers;
    }

    public ObservableList<String> getJoinedGroups() {
        return joinedGroups;
    }

    public StringProperty getNewRawMessageProperty() {
        return newRawMessage;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void closeConnection() {
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("Error closing connection: " + e.getMessage());
        }
    }
}
