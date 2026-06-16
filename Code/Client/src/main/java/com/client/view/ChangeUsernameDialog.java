package com.client.view;

import com.client.controller.ChatController;
import com.client.util.StyleConstants;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Dialog for changing the current user's display name.
 * Extracted from ChatView.
 */
public class ChangeUsernameDialog {

    private final Stage owner;
    private final ChatController chatController;
    private final Label nameLabel;   // the label to update on success
    private final Runnable onSuccess;

    public ChangeUsernameDialog(Stage owner, ChatController chatController,
                                Label nameLabel, Runnable onSuccess) {
        this.owner = owner;
        this.chatController = chatController;
        this.nameLabel = nameLabel;
        this.onSuccess = onSuccess;
    }

    public void show() {
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle("Đổi tên người dùng");
        dialog.setResizable(false);

        VBox content = new VBox(14);
        content.setPadding(new Insets(28, 28, 24, 28));
        content.setPrefWidth(360);
        content.setStyle("-fx-background-color: " + StyleConstants.PANEL_DARK + ";");

        Label title = new Label("Đổi tên người dùng");
        title.setStyle("-fx-text-fill: " + StyleConstants.TEXT_WHITE + "; -fx-font-size: 22px; -fx-font-weight: bold;");

        Label subtitle = new Label("Nhập tên mới cho tài khoản của bạn.");
        subtitle.setWrapText(true);
        subtitle.setStyle("-fx-text-fill: " + StyleConstants.TEXT_MUTED + "; -fx-font-size: 13px;");

        TextField newNameField = new TextField();
        newNameField.setPromptText("Tên người dùng mới");
        newNameField.setStyle("""
                -fx-background-color: %s;
                -fx-border-color: %s;
                -fx-border-width: 1.5px;
                -fx-border-radius: 8px;
                -fx-background-radius: 8px;
                -fx-text-fill: %s;
                -fx-prompt-text-fill: %s;
                -fx-font-size: 14px;
                -fx-padding: 10px 14px;
                """.formatted(StyleConstants.BG_BLACK, StyleConstants.INPUT_BORDER,
                StyleConstants.TEXT_WHITE, StyleConstants.TEXT_DIM));

        Label msgLabel = new Label("");
        msgLabel.setWrapText(true);
        msgLabel.setMinHeight(20);
        msgLabel.setStyle("-fx-text-fill: transparent; -fx-font-size: 12px;");

        Button cancelBtn = createButton("Hủy", false);
        cancelBtn.setOnAction(e -> dialog.close());

        Button confirmBtn = createButton("Xác nhận", true);
        confirmBtn.setOnAction(e -> {
            String newName = newNameField.getText().trim();
            if (newName.isEmpty()) {
                msgLabel.setText("Tên không được để trống.");
                msgLabel.setStyle("-fx-text-fill: #ff7777; -fx-font-size: 12px;");
                return;
            }
            chatController.changeUsername(newName,
                    name -> {
                        if (nameLabel != null) nameLabel.setText(name);
                        if (onSuccess != null) onSuccess.run();
                        dialog.close();
                    },
                    errMsg -> {
                        msgLabel.setText(errMsg);
                        msgLabel.setStyle("-fx-text-fill: #ff7777; -fx-font-size: 12px;");
                    });
        });

        HBox buttons = new HBox(10, cancelBtn, confirmBtn);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        content.getChildren().addAll(title, subtitle, newNameField, msgLabel, buttons);

        Scene scene = new Scene(content);
        scene.setFill(javafx.scene.paint.Color.web(StyleConstants.PANEL_DARK));
        dialog.setScene(scene);
        dialog.show();
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
}
