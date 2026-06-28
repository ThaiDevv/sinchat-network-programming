package com.client.view;

import com.client.controller.ChatController;
import com.client.util.StyleConstants;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Dialog showing the history of sent and received friend requests.
 * Displays two tabs: "Đã gửi" (Sent) and "Đã nhận" (Received).
 */
public class FriendRequestHistoryDialog {

    private final Stage owner;
    private final ChatController controller;

    public FriendRequestHistoryDialog(Stage owner, ChatController controller) {
        this.owner = owner;
        this.controller = controller;
    }

    public void show() {
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.setTitle("Lịch sử kết bạn");

        VBox root = new VBox();
        root.setStyle("-fx-background-color: #111111; -fx-background-radius: 16px; "
                + "-fx-border-color: #2d2d2d; -fx-border-radius: 16px; -fx-border-width: 1.5px;");
        root.setPrefWidth(480);

        // ---- Header ----
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(20, 24, 16, 24));
        header.setStyle("-fx-border-color: #222222; -fx-border-width: 0 0 1 0;");

        Label title = new Label("📋  Lịch sử kết bạn");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = new Button("✕");
        closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #888888; "
                + "-fx-font-size: 16px; -fx-cursor: hand;");
        closeBtn.setOnMouseEntered(e -> closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ffffff; -fx-font-size: 16px; -fx-cursor: hand;"));
        closeBtn.setOnMouseExited(e -> closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #888888; -fx-font-size: 16px; -fx-cursor: hand;"));
        closeBtn.setOnAction(e -> dialog.close());

        header.getChildren().addAll(title, spacer, closeBtn);

        // ---- Tabs ----
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.setStyle("-fx-background-color: transparent; -fx-tab-min-height: 40px; -fx-tab-max-height: 40px;");

        VBox sentList = new VBox(8);
        sentList.setPadding(new Insets(16));
        ScrollPane sentScroll = createScrollPane(sentList);

        VBox receivedList = new VBox(8);
        receivedList.setPadding(new Insets(16));
        ScrollPane receivedScroll = createScrollPane(receivedList);

        Tab sentTab = new Tab("📤  Đã gửi");
        sentTab.setContent(sentScroll);

        Tab receivedTab = new Tab("📥  Đã nhận");
        receivedTab.setContent(receivedScroll);

        tabPane.getTabs().addAll(sentTab, receivedTab);

        // Loading indicators
        Label loadingLabel = new Label("Đang tải...");
        loadingLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 14px;");

        VBox.setVgrow(tabPane, Priority.ALWAYS);
        root.getChildren().addAll(header, tabPane);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        dialog.setScene(scene);

        // Center over owner
        dialog.setX(owner.getX() + (owner.getWidth() - 480) / 2);
        dialog.setY(owner.getY() + (owner.getHeight() - 520) / 2);
        dialog.setWidth(480);
        dialog.setHeight(520);

        // Load data
        loadFriendRequests(sentList, receivedList, dialog);

        dialog.show();
    }

    private void loadFriendRequests(VBox sentList, VBox receivedList, Stage dialog) {
        Label sentLoading = makeStatusLabel("Đang tải danh sách đã gửi...", false);
        Label receivedLoading = makeStatusLabel("Đang tải danh sách đã nhận...", false);
        sentList.getChildren().add(sentLoading);
        receivedList.getChildren().add(receivedLoading);

        controller.getFriendRequests(
                json -> {
                    sentList.getChildren().remove(sentLoading);
                    receivedList.getChildren().remove(receivedLoading);

                    JsonArray sent = json.has("sent") ? json.getAsJsonArray("sent") : new JsonArray();
                    JsonArray pending = json.has("pending") ? json.getAsJsonArray("pending") : new JsonArray();

                    if (sent.isEmpty()) {
                        sentList.getChildren().add(makeStatusLabel("Bạn chưa gửi lời mời kết bạn nào.", false));
                    } else {
                        for (JsonElement el : sent) {
                            sentList.getChildren().add(createRequestCard(el.getAsJsonObject(), "sent"));
                        }
                    }

                    if (pending.isEmpty()) {
                        receivedList.getChildren().add(makeStatusLabel("Bạn chưa nhận lời mời kết bạn nào.", false));
                    } else {
                        for (JsonElement el : pending) {
                            receivedList.getChildren().add(createRequestCard(el.getAsJsonObject(), "received"));
                        }
                    }
                },
                err -> {
                    sentList.getChildren().clear();
                    receivedList.getChildren().clear();
                    sentList.getChildren().add(makeStatusLabel("Lỗi: " + err, true));
                    receivedList.getChildren().add(makeStatusLabel("Lỗi: " + err, true));
                }
        );
    }

    private HBox createRequestCard(JsonObject user, String type) {
        HBox card = new HBox(14);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(12, 16, 12, 16));
        card.setStyle("-fx-background-color: #1a1a1a; -fx-background-radius: 12px; "
                + "-fx-border-color: #2a2a2a; -fx-border-radius: 12px; -fx-border-width: 1px;");

        // Avatar circle (initial letter)
        String username = user.has("username") ? user.get("username").getAsString() : "?";
        Circle avatar = new Circle(20);
        avatar.setFill(Color.web(StyleConstants.ACCENT));
        Label initial = new Label(username.isEmpty() ? "?" : String.valueOf(username.charAt(0)).toUpperCase());
        initial.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: white;");
        StackPane avatarPane = new StackPane(avatar, initial);

        // Info
        VBox info = new VBox(3);
        Label nameLabel = new Label(username);
        nameLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #f0f0f0;");

        Label typeLabel;
        if ("sent".equals(type)) {
            typeLabel = new Label("⏳ Đang chờ phản hồi");
            typeLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #aaaaaa;");
        } else {
            typeLabel = new Label("📨 Gửi lời mời cho bạn");
            typeLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #7c5cfc;");
        }
        info.getChildren().addAll(nameLabel, typeLabel);

        Region flexSpacer = new Region();
        HBox.setHgrow(flexSpacer, Priority.ALWAYS);

        card.getChildren().addAll(avatarPane, info, flexSpacer);

        // Hover effect
        card.setOnMouseEntered(e -> card.setStyle("-fx-background-color: #222222; -fx-background-radius: 12px; "
                + "-fx-border-color: #333333; -fx-border-radius: 12px; -fx-border-width: 1px;"));
        card.setOnMouseExited(e -> card.setStyle("-fx-background-color: #1a1a1a; -fx-background-radius: 12px; "
                + "-fx-border-color: #2a2a2a; -fx-border-radius: 12px; -fx-border-width: 1px;"));

        return card;
    }

    private ScrollPane createScrollPane(VBox content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(360);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background: #111111; -fx-background-color: #111111; -fx-border-color: transparent;");
        return scroll;
    }

    private Label makeStatusLabel(String text, boolean error) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: " + (error ? "#ff5555" : "#666666")
                + "; -fx-font-size: 13px; -fx-padding: 16px 0;");
        return label;
    }
}
