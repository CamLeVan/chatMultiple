package com.fragmented.download.chatnew;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;

import java.util.Optional;

public class ChatController {

    // FXML Fields from chat-view.fxml
    @FXML private ListView<String> userListView;
    @FXML private ListView<String> groupListView;
    @FXML private Label chatTargetLabel;
    @FXML private TextArea chatArea;
    @FXML private TextField messageField;
    @FXML private Button sendButton;
    @FXML private TextField groupNameField;
    @FXML private Button createGroupButton;

    private NetworkService networkService;
    private String currentChatMode = "BROADCAST";
    private String currentTarget = "ALL";

    @FXML
    public void initialize() {
        this.networkService = NetworkService.getInstance();

        // Bind UI lists to data from NetworkService
        userListView.setItems(networkService.getOnlineUsers());
        groupListView.setItems(networkService.getJoinedGroups());

        // Listener for new text messages (private, group, global, error)
        networkService.getNewRawMessageProperty().addListener((obs, oldMsg, newMsg) -> {
            if (newMsg != null && !newMsg.isEmpty()) {
                handleIncomingMessage(newMsg);
            }
        });

        // Listener for group invitations
        networkService.getIncomingInviteProperty().addListener((obs, oldInvite, newInvite) -> {
            if (newInvite != null && !newInvite.isEmpty()) {
                Platform.runLater(() -> handleGroupInvite(newInvite));
            }
        });

        setupGroupListViewContextMenu();
        chatArea.setWrapText(true);
        chatTargetLabel.setText("Global Chat");
    }

    private void handleIncomingMessage(String rawMessage) {
        String[] parts = rawMessage.split("::", 4);
        String command = parts[0];

        String displayText = "";
        switch (command) {
            case "MESSAGE": // Format: MESSAGE::Sender::Content
                displayText = String.format("[Global - %s]: %s\n", parts[1], parts[2]);
                break;
            case "UNICAST_MESSAGE": // Format: UNICAST_MESSAGE::Sender::Content
                displayText = String.format("[Private from %s]: %s\n", parts[1], parts[2]);
                break;
            case "MULTICAST_MESSAGE": // Format: MULTICAST_MESSAGE::Group::Sender::Content
                displayText = String.format("[%s - %s]: %s\n", parts[1], parts[2], parts[3]);
                break;
            case "ERROR": // Format: ERROR::Error message
                displayText = String.format("[SERVER ERROR]: %s\n", parts[1]);
                break;
            default:
                displayText = rawMessage + "\n";
                break;
        }
        chatArea.appendText(displayText);
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
                // Switch back to global chat after leaving
                Platform.runLater(this::switchToGlobalChat);
            }
        });
        contextMenu.getItems().add(leaveItem);
        groupListView.setContextMenu(contextMenu);
    }

    private void switchToGlobalChat(){
        currentChatMode = "BROADCAST";
        currentTarget = "ALL";
        chatTargetLabel.setText("Global Chat");
        userListView.getSelectionModel().clearSelection();
        groupListView.getSelectionModel().clearSelection();
    }

    @FXML
    private void onGlobalChatClick(MouseEvent event) {
        switchToGlobalChat();
    }

    @FXML
    private void onUserListClick(MouseEvent event) {
        String selectedUser = userListView.getSelectionModel().getSelectedItem();
        if (selectedUser != null) {
            currentChatMode = "UNICAST";
            currentTarget = selectedUser;
            chatTargetLabel.setText("Private Chat with: " + selectedUser);
            groupListView.getSelectionModel().clearSelection();
        }
    }

    @FXML
    private void onGroupListClick(MouseEvent event) {
        String selectedGroup = groupListView.getSelectionModel().getSelectedItem();
        if (selectedGroup != null) {
            currentChatMode = "MULTICAST";
            currentTarget = selectedGroup;
            chatTargetLabel.setText("Group Chat in: " + selectedGroup);
            userListView.getSelectionModel().clearSelection();
        }
    }

    @FXML
    private void onCreateGroupClick() {
        String groupName = groupNameField.getText();
        if (groupName != null && !groupName.trim().isEmpty()) {
            networkService.sendMessage("CREATE_GROUP::" + groupName.trim());
            groupNameField.clear();
        }
    }

    @FXML
    private void onSendButtonAction() {
        String message = messageField.getText();
        if (message == null || message.trim().isEmpty()) {
            return;
        }
        message = message.trim();

        // Handle /invite command
        if (message.startsWith("/invite ")) {
            String[] parts = message.split(" ", 3);
            if (parts.length == 3) {
                // Format: /invite <group> <user>
                String group = parts[1];
                String userToInvite = parts[2];
                networkService.sendMessage(String.format("INVITE_TO_GROUP::%s::%s", group, userToInvite));
                chatArea.appendText(String.format("[SYSTEM]: Invitation sent to %s to join %s.\n", userToInvite, group));
            } else {
                chatArea.appendText("[SYSTEM]: Invalid invite format. Use: /invite <group_name> <username>\n");
            }
        } else {
            // Regular chat message
            String protocolMessage = String.format("%s::%s::%s", currentChatMode, currentTarget, message);
            networkService.sendMessage(protocolMessage);

            // Display own message immediately
            String displayMessage;
            if ("UNICAST".equals(currentChatMode)) {
                displayMessage = String.format("[Me to %s]: %s\n", currentTarget, message);
            } else if ("MULTICAST".equals(currentChatMode)) {
                displayMessage = String.format("[Me in %s]: %s\n", currentTarget, message);
            } else { // BROADCAST
                displayMessage = String.format("[Me - Global]: %s\n", message);
            }
            chatArea.appendText(displayMessage);
        }
        messageField.clear();
    }
}
