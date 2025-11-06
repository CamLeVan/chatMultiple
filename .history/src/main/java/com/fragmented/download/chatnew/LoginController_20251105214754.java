package com.fragmented.download.chatnew;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;

public class LoginController {

    @FXML
    private TextField ipField;
    @FXML
    private TextField portField;
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Button loginButton;
    @FXML
    private Button registerButton;
    @FXML
    private Label errorLabel;

    @FXML
    public void initialize() {
        ipField.setText("192.168.56.1");
        portField.setText("8080");
        errorLabel.setVisible(false);
    }

    @FXML
    private void onLoginButtonAction() {
        handleAuthentication(false);
    }

    @FXML
    private void onRegisterButtonAction() {
        handleAuthentication(true);
    }

    private void handleAuthentication(boolean isRegister) {
        String ip = ipField.getText().trim();
        String portStr = portField.getText().trim();
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        if (ip.isEmpty() || portStr.isEmpty() || username.isEmpty() || password.isEmpty()) {
            showError("All fields are required.");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            showError("Invalid port number.");
            return;
        }

        NetworkService networkService = NetworkService.getInstance();
        boolean success = networkService.connect(ip, port, username, password, isRegister);

        if (success) {
            // Chuyển sang màn hình chat
            switchToChatScene();
        } else {
            // Hiển thị lỗi
            showError(networkService.getErrorMessage());
        }
    }

    private void switchToChatScene() {
        try {
            Stage stage = (Stage) loginButton.getScene().getWindow();
            FXMLLoader fxmlLoader = new FXMLLoader(MainApp.class.getResource("chat-view.fxml"));
            Scene scene = new Scene(fxmlLoader.load(), 800, 600);
            stage.setTitle("Chat - " + usernameField.getText());
            stage.setScene(scene);
        } catch (IOException e) {
            e.printStackTrace();
            showError("Failed to load chat view.");
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}
