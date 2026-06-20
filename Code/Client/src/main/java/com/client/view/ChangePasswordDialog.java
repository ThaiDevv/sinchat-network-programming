package com.client.view;

import com.client.controller.ChatController;
import com.client.util.StyleConstants;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Dialog for changing the current user's password.
 * Extracted from ChatView.
 */
public class ChangePasswordDialog {
    private static final int MAX_PASSWORD_LENGTH = 100;

    private final Stage owner;
    private final ChatController chatController;

    public ChangePasswordDialog(Stage owner, ChatController chatController) {
        this.owner = owner;
        this.chatController = chatController;
    }

    public void show() {
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle("Đổi mật khẩu");

        VBox content = new VBox(16);
        content.setPadding(new Insets(26));
        content.setStyle("""
                -fx-background-color: %s;
                -fx-border-color: %s;
                -fx-border-width: 1px;
                """.formatted(StyleConstants.PANEL_DARK, StyleConstants.BORDER_COLOR));

        Label title = new Label("Đổi mật khẩu");
        title.setStyle("-fx-text-fill: " + StyleConstants.TEXT_WHITE + "; -fx-font-size: 24px; -fx-font-weight: bold;");

        Label subtitle = new Label("Nhập mật khẩu hiện tại trước khi đặt mật khẩu mới.");
        subtitle.setWrapText(true);
        subtitle.setStyle("-fx-text-fill: " + StyleConstants.TEXT_MUTED + "; -fx-font-size: 14px;");

        PasswordField oldPasswordField = createField("Mật khẩu hiện tại");
        limitTextInput(oldPasswordField, MAX_PASSWORD_LENGTH);
        PasswordField newPasswordField = createField("Mật khẩu mới");
        limitTextInput(newPasswordField, MAX_PASSWORD_LENGTH);
        PasswordField confirmPasswordField = createField("Nhập lại mật khẩu mới");
        limitTextInput(confirmPasswordField, MAX_PASSWORD_LENGTH);

        Label message = new Label("");
        message.setWrapText(true);
        message.setMinHeight(24);
        message.setStyle("-fx-text-fill: transparent; -fx-font-size: 13px;");

        Button cancelButton = createButton("Hủy", false);
        cancelButton.setOnAction(e -> dialog.close());

        Button saveButton = createButton("Lưu mật khẩu", true);
        saveButton.setOnAction(e -> {
            String oldPassword = oldPasswordField.getText();
            String newPassword = newPasswordField.getText();
            String confirmPassword = confirmPasswordField.getText();

            if (oldPassword.isBlank() || newPassword.isBlank() || confirmPassword.isBlank()) {
                setMessage(message, "Vui lòng nhập đầy đủ thông tin.", "#ff7777");
                return;
            }
            if (newPassword.length() < 6) {
                setMessage(message, "Mật khẩu mới phải có ít nhất 6 ký tự.", "#ff7777");
                return;
            }
            if (oldPassword.equals(newPassword)) {
                setMessage(message, "Mật khẩu mới không được trùng mật khẩu hiện tại.", "#ff7777");
                return;
            }
            if (!newPassword.equals(confirmPassword)) {
                setMessage(message, "Mật khẩu nhập lại không khớp.", "#ff7777");
                return;
            }

            saveButton.setDisable(true);
            cancelButton.setDisable(true);
            setMessage(message, "Đang đổi mật khẩu...", StyleConstants.TEXT_MUTED);

            chatController.changePassword(oldPassword, newPassword,
                    successMsg -> {
                        saveButton.setDisable(false);
                        cancelButton.setDisable(false);
                        oldPasswordField.clear();
                        newPasswordField.clear();
                        confirmPasswordField.clear();
                        setMessage(message, successMsg, "#4ade80");
                    },
                    errMsg -> {
                        saveButton.setDisable(false);
                        cancelButton.setDisable(false);
                        setMessage(message, errMsg, "#ff7777");
                    });
        });

        HBox actions = new HBox(10, cancelButton, saveButton);
        actions.setAlignment(Pos.CENTER_RIGHT);

        content.getChildren().addAll(title, subtitle, oldPasswordField,
                newPasswordField, confirmPasswordField, message, actions);

        Scene scene = new Scene(content, 460, 390);
        dialog.setScene(scene);
        dialog.setResizable(false);
        dialog.showAndWait();
    }

    private PasswordField createField(String prompt) {
        PasswordField field = new PasswordField();
        field.setPromptText(prompt);
        field.setPrefHeight(46);
        field.setStyle("""
                -fx-background-color: %s;
                -fx-border-color: %s;
                -fx-border-width: 1.5px;
                -fx-border-radius: 12px;
                -fx-background-radius: 12px;
                -fx-text-fill: %s;
                -fx-prompt-text-fill: %s;
                -fx-font-size: 14px;
                -fx-padding: 12px 14px;
                """.formatted(StyleConstants.BG_BLACK, StyleConstants.INPUT_BORDER,
                StyleConstants.TEXT_WHITE, StyleConstants.TEXT_DIM));
        return field;
    }

    private Button createButton(String text, boolean accent) {
        Button button = new Button(text);
        button.setMinWidth(120);
        button.setPrefHeight(42);
        if (accent) {
            button.setStyle("""
                    -fx-background-color: %s;
                    -fx-text-fill: %s;
                    -fx-font-size: 14px;
                    -fx-font-weight: bold;
                    -fx-background-radius: 12px;
                    -fx-cursor: hand;
                    """.formatted(StyleConstants.ACCENT, StyleConstants.TEXT_WHITE));
        } else {
            button.setStyle("""
                    -fx-background-color: transparent;
                    -fx-text-fill: %s;
                    -fx-font-size: 14px;
                    -fx-border-color: %s;
                    -fx-border-width: 1.5px;
                    -fx-border-radius: 12px;
                    -fx-background-radius: 12px;
                    -fx-cursor: hand;
                    """.formatted(StyleConstants.TEXT_MUTED, StyleConstants.INPUT_BORDER));
        }
        return button;
    }

    private void setMessage(Label label, String text, String color) {
        label.setText(text);
        label.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 13px;");
    }

    private void limitTextInput(PasswordField input, int maxLength) {
        input.textProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null && newValue.length() > maxLength) {
                input.setText(newValue.substring(0, maxLength));
                input.positionCaret(maxLength);
            }
        });
    }
}
