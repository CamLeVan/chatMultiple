package com.fragmented.download.chatnew;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ChatController {

    @FXML private ListView<String> userListView;
    @FXML private ListView<String> groupListView;
    @FXML private Label chatTargetLabel;
    @FXML private TextArea chatArea;
    @FXML private TextField messageField;
    @FXML private Button sendButton;
    @FXML private TextField groupNameField;
    @FXML private Button createGroupButton;
    @FXML private Button globalChatButton;

    private NetworkService networkService;
    private String currentTarget = "Global";
    private String selfUsername;

    private final Map<String, StringBuilder> chatHistories = new ConcurrentHashMap<>();

    @FXML
    public void initialize() {
        this.networkService = NetworkService.getInstance();
        this.selfUsername = networkService.getUsername(); // Assuming NetworkService has this getter
        chatHistories.put("Global", new StringBuilder());

        userListView.setItems(networkService.getOnlineUsers());
        groupListView.setItems(networkService.getJoinedGroups());

        networkService.getNewRawMessageProperty().addListener((obs, oldMsg, newMsg) -> {
            if (newMsg != null && !newMsg.isEmpty()) {
                Platform.runLater(() -> handleIncomingMessage(newMsg));
            }
        });

        networkService.getIncomingInviteProperty().addListener((obs, oldInvite, newInvite) -> {
            if (newInvite != null && !newInvite.isEmpty()) {
                Platform.runLater(() -> handleGroupInvite(newInvite));
            }
        });

        setupGroupListViewContextMenu();
        chatArea.setWrapText(true);
        switchToGlobalChat();
    }

    private String getPrivateChatKey(String otherUser) {
        String[] users = {selfUsername, otherUser};
        Arrays.sort(users);
        return "user:" + String.join("-", users);
    }

    private void handleIncomingMessage(String rawMessage) {
        String[] parts = rawMessage.split("::", 4);
        String command = parts[0];
        String historyKey = "";
        String displayText = "";

        switch (command) {
            case "MESSAGE":
                historyKey = "Global";
                displayText = String.format("[Global - %s]: %s\n", parts[1], parts[2]);
                break;
            case "UNICAST_MESSAGE":
                historyKey = getPrivateChatKey(parts[1]); // parts[1] is the sender
                displayText = String.format("[Private from %s]: %s\n", parts[1], parts[2]);
                break;
            case "MULTICAST_MESSAGE":
                historyKey = "group:" + parts[1];
                displayText = String.format("[%s - %s]: %s\n", parts[1], parts[2], parts[3]);
                break;
            case "ERROR":
                historyKey = currentTarget;
                displayText = String.format("[SERVER ERROR]: %s\n", parts[1]);
                break;
            default:
                historyKey = currentTarget;
                displayText = rawMessage + "\n";
                break;
        }

        chatHistories.computeIfAbsent(historyKey, k -> new StringBuilder()).append(displayText);

        if (historyKey.equals(currentTarget)) {
            updateChatArea();
        }
    }

    private void handleGroupInvite(String inviteInfo) {
        String[] parts = inviteInfo.split("::", 2);
        String groupName = parts[0];
        String inviter = parts[1];

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Group Invitation");
        alert.setHeaderText(String.format("You have been invited to join group '%s' by %s.", groupName, inviter));
        alert.setContentText("Do you want to accept?");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            networkService.sendMessage("JOIN_GROUP::" + groupName);
        }
    }

    private void setupGroupListViewContextMenu() {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem leaveItem = new MenuItem("Leave Group");
        leaveItem.setOnAction(event -> {
            String selectedGroup = groupListView.getSelectionModel().getSelectedItem();
            if (selectedGroup != null) {
                networkService.sendMessage("LEAVE_GROUP::" + selectedGroup);
                chatHistories.remove("group:" + selectedGroup);
                switchToGlobalChat();
            }
        });
        contextMenu.getItems().add(leaveItem);
        groupListView.setContextMenu(contextMenu);
    }

    private void updateChatArea() {
        chatArea.setText(chatHistories.getOrDefault(currentTarget, new StringBuilder()).toString());
        chatArea.setScrollTop(Double.MAX_VALUE);
    }

    @FXML
    private void onGlobalChatClick() {
        switchToGlobalChat();
    }

    private void switchToGlobalChat() {
        currentTarget = "Global";
        chatTargetLabel.setText("Global Chat");
        userListView.getSelectionModel().clearSelection();
        groupListView.getSelectionModel().clearSelection();
        updateChatArea();
    }

    @FXML
    private void onUserListClick(MouseEvent event) {
        String selectedUser = userListView.getSelectionModel().getSelectedItem();
        if (selectedUser != null && !selectedUser.equals(selfUsername)) {
            currentTarget = getPrivateChatKey(selectedUser);
            chatTargetLabel.setText("Private Chat with: " + selectedUser);
            groupListView.getSelectionModel().clearSelection();
            chatHistories.computeIfAbsent(currentTarget, k -> new StringBuilder());
            updateChatArea();
        }
    }

    @FXML
    private void onGroupListClick(MouseEvent event) {
        String selectedGroup = groupListView.getSelectionModel().getSelectedItem();
        if (selectedGroup != null) {
            currentTarget = "group:" + selectedGroup;
            chatTargetLabel.setText("Group Chat in: " + selectedGroup);
            userListView.getSelectionModel().clearSelection();
            chatHistories.computeIfAbsent(currentTarget, k -> new StringBuilder());
            updateChatArea();
        }
    }

    @FXML
    private void onCreateGroupClick() {
        String text = groupNameField.getText().trim();
        if (text.isEmpty()) return;

        String[] parts = text.split("\\s+");
        String groupName = parts[0];
        String members = (parts.length > 1) ? String.join(",", Arrays.copyOfRange(parts, 1, parts.length)) : "";

        networkService.sendMessage("CREATE_GROUP::" + groupName + "::" + members);
        groupNameField.clear();
    }

    @FXML
    private void onSendButtonAction() {
        String message = messageField.getText();
        if (message == null || message.trim().isEmpty()) return;
        message = message.trim();

        String currentChatMode;
        String targetName;

        if (currentTarget.startsWith("user:")) {
            currentChatMode = "UNICAST";
            targetName = currentTarget.substring(currentTarget.lastIndexOf('-') + 1);
            if(targetName.equals(selfUsername)){
                targetName = currentTarget.substring(5, currentTarget.indexOf('-'));
            }

        } else if (currentTarget.startsWith("group:")) {
            currentChatMode = "MULTICAST";
            targetName = currentTarget.substring(6);
        } else {
            currentChatMode = "BROADCAST";
            targetName = "ALL";
        }

        if (message.startsWith("/invite ")) {
            handleInviteCommand(message);
        } else {
            String protocolMessage = String.format("%s::%s::%s", currentChatMode, targetName, message);
            networkService.sendMessage(protocolMessage);

            String displayMessage;
            if ("UNICAST".equals(currentChatMode)) {
                displayMessage = String.format("[Me to %s]: %s\n", targetName, message);
            } else if ("MULTICAST".equals(currentChatMode)) {
                displayMessage = String.format("[Me in %s]: %s\n", targetName, message);
            } else { // BROADCAST
                displayMessage = String.format("[Me - Global]: %s\n", message);
            }
            chatHistories.get(currentTarget).append(displayMessage);
            updateChatArea();
        }
        messageField.clear();
    }

    private void handleInviteCommand(String message) {
        String[] parts = message.split(" ", 3);
        if (parts.length == 3) {
            String group = parts[1];
            String userToInvite = parts[2];
            networkService.sendMessage(String.format("INVITE_TO_GROUP::%s::%s", group, userToInvite));
            String systemMessage = String.format("[SYSTEM]: Invitation sent to %s to join %s.\n", userToInvite, group);
            chatHistories.get(currentTarget).append(systemMessage);
            updateChatArea();
        } else {
            String errorMessage = "[SYSTEM]: Invalid invite format. Use: /invite <group_name> <username>\n";
            chatHistories.get(currentTarget).append(errorMessage);
            updateChatArea();
        }
    }
}
