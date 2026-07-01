package com.client.view;

import com.client.controller.ChatController;
import com.client.util.ImageUtils;
import com.client.util.StyleConstants;
import javafx.scene.image.Image;
import javafx.scene.paint.ImagePattern;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Dialog for creating a new group chat.
 * Lets the user:
 *   1. Enter a group name
 *   2. Search and select members from their existing contacts
 *   3. Submit to create the group
 */
public class CreateGroupDialog {

    private final Stage owner;
    private final ChatController controller;
    /** Called with (conversationId, groupName) on successful creation */
    private final BiConsumer<Long, String> onGroupCreated;

    // ---- selected members ----
    private final Map<Long, String> selectedMembers = new HashMap<>();   // userId -> username
    private final List<Long> allContactIds = new ArrayList<>();
    private final Map<Long, String> allContactNames = new HashMap<>();

    // ---- UI ----
    private TextField groupNameField;
    private TextField searchField;
    private VBox memberListBox;      // search results to pick from
    private VBox selectedChipsBox;  // chips of already-selected members
    private Label errorLabel;
    private Button createBtn;
    private Stage dialog;

    public CreateGroupDialog(Stage owner, ChatController controller,
                             BiConsumer<Long, String> onGroupCreated) {
        this.owner = owner;
        this.controller = controller;
        this.onGroupCreated = onGroupCreated;
    }

    public void show() {
        dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.setTitle("Tạo nhóm chat");

        VBox root = buildUI();

        Scene scene = new Scene(root, 480, 580);
        scene.setFill(Color.TRANSPARENT);
        dialog.setScene(scene);
        dialog.show();

        // Pre-load user's existing contacts so they can pick members
        loadContacts();
    }

    // ==================== UI BUILD ====================

    private VBox buildUI() {
        VBox card = new VBox(16);
        card.setPadding(new Insets(28, 30, 28, 30));
        card.setStyle(
                "-fx-background-color: #181818;" +
                "-fx-background-radius: 18px;" +
                "-fx-border-color: " + StyleConstants.BORDER_COLOR + ";" +
                "-fx-border-width: 1.5px;" +
                "-fx-border-radius: 18px;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.7), 24, 0.3, 0, 8);"
        );

        // ---- Header ----
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("🫂 Tạo nhóm chat");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: " + StyleConstants.TEXT_WHITE + ";");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = new Button("✕");
        closeBtn.setStyle(
                "-fx-background-color: transparent;" +
                "-fx-text-fill: #888888;" +
                "-fx-font-size: 18px;" +
                "-fx-cursor: hand;"
        );
        closeBtn.setOnAction(e -> dialog.close());

        header.getChildren().addAll(title, spacer, closeBtn);

        // ---- Group name input ----
        Label nameLabel = new Label("Tên nhóm");
        nameLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: " + StyleConstants.TEXT_MUTED + "; -fx-font-weight: bold;");

        groupNameField = new TextField();
        groupNameField.setPromptText("Nhập tên nhóm...");
        groupNameField.setStyle(inputStyle());
        // Limit to 100 characters
        groupNameField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.length() > 100) {
                groupNameField.setText(newVal.substring(0, 100));
            }
        });

        // ---- Selected members chips ----
        Label selectedLabel = new Label("Thành viên đã chọn");
        selectedLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: " + StyleConstants.TEXT_MUTED + "; -fx-font-weight: bold;");

        selectedChipsBox = new VBox(6);
        selectedChipsBox.setMinHeight(36);

        ScrollPane chipsScroll = new ScrollPane(selectedChipsBox);
        chipsScroll.setFitToWidth(true);
        chipsScroll.setMaxHeight(120);
        chipsScroll.setPrefHeight(60);
        chipsScroll.setStyle("-fx-background: #111111; -fx-background-color: #111111; -fx-border-color: " + StyleConstants.BORDER_COLOR + "; -fx-border-radius: 10px; -fx-background-radius: 10px;");
        chipsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        // ---- Member search ----
        Label searchLabel = new Label("Tìm thành viên");
        searchLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: " + StyleConstants.TEXT_MUTED + "; -fx-font-weight: bold;");

        searchField = new TextField();
        searchField.setPromptText("Tìm kiếm người dùng...");
        searchField.setStyle(inputStyle());
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterContacts(newVal.trim()));

        memberListBox = new VBox(4);
        memberListBox.setPadding(new Insets(4));

        ScrollPane memberScroll = new ScrollPane(memberListBox);
        memberScroll.setFitToWidth(true);
        memberScroll.setPrefHeight(180);
        memberScroll.setMaxHeight(200);
        memberScroll.setStyle("-fx-background: #111111; -fx-background-color: #111111; -fx-border-color: " + StyleConstants.BORDER_COLOR + "; -fx-border-radius: 10px; -fx-background-radius: 10px;");
        memberScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        // ---- Error label ----
        errorLabel = new Label("");
        errorLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #ff6666;");
        errorLabel.setWrapText(true);

        // ---- Create button ----
        createBtn = new Button("Tạo nhóm");
        createBtn.setMaxWidth(Double.MAX_VALUE);
        createBtn.setPrefHeight(46);
        createBtn.setStyle(
                "-fx-background-color: " + StyleConstants.ACCENT + ";" +
                "-fx-text-fill: white;" +
                "-fx-font-size: 15px;" +
                "-fx-font-weight: bold;" +
                "-fx-background-radius: 12px;" +
                "-fx-cursor: hand;"
        );
        createBtn.setOnMouseEntered(e -> createBtn.setStyle(
                "-fx-background-color: #6a4ee8;" +
                "-fx-text-fill: white;" +
                "-fx-font-size: 15px;" +
                "-fx-font-weight: bold;" +
                "-fx-background-radius: 12px;" +
                "-fx-cursor: hand;"
        ));
        createBtn.setOnMouseExited(e -> createBtn.setStyle(
                "-fx-background-color: " + StyleConstants.ACCENT + ";" +
                "-fx-text-fill: white;" +
                "-fx-font-size: 15px;" +
                "-fx-font-weight: bold;" +
                "-fx-background-radius: 12px;" +
                "-fx-cursor: hand;"
        ));
        createBtn.setOnAction(e -> onCreateClicked());

        card.getChildren().addAll(
                header,
                nameLabel, groupNameField,
                selectedLabel, chipsScroll,
                searchLabel, searchField, memberScroll,
                errorLabel,
                createBtn
        );

        VBox wrapper = new VBox(card);
        wrapper.setAlignment(Pos.CENTER);
        wrapper.setPadding(new Insets(20));
        wrapper.setStyle("-fx-background-color: transparent;");
        return wrapper;
    }

    // ==================== LOGIC ====================

    private void loadContacts() {
        controller.loadConversations(
                conversations -> {
                    allContactIds.clear();
                    allContactNames.clear();
                    for (JsonElement el : conversations) {
                        JsonObject conv = el.getAsJsonObject();
                        // Only PRIVATE conversations have a peer to add
                        String type = conv.has("type") ? conv.get("type").getAsString() : "";
                        if (!"PRIVATE".equals(type)) continue;
                        if (!conv.has("peerId")) continue;
                        long peerId = conv.get("peerId").getAsLong();
                        String name = conv.has("displayName") ? conv.get("displayName").getAsString() : "Người dùng";
                        if (!allContactIds.contains(peerId)) {
                            allContactIds.add(peerId);
                            allContactNames.put(peerId, name);
                        }
                    }
                    Platform.runLater(() -> filterContacts(""));
                }, null
        );
    }

    private void filterContacts(String query) {
        memberListBox.getChildren().clear();
        String lower = query.toLowerCase();

        for (Long id : allContactIds) {
            String name = allContactNames.getOrDefault(id, "");
            if (!lower.isEmpty() && !name.toLowerCase().contains(lower)) continue;
            memberListBox.getChildren().add(buildContactRow(id, name));
        }

        if (memberListBox.getChildren().isEmpty()) {
            Label empty = new Label(query.isEmpty() ? "Không có liên hệ nào." : "Không tìm thấy.");
            empty.setStyle("-fx-text-fill: " + StyleConstants.TEXT_MUTED + "; -fx-font-size: 13px; -fx-padding: 10px;");
            memberListBox.getChildren().add(empty);
        }
    }

    private HBox buildContactRow(long userId, String username) {
        boolean selected = selectedMembers.containsKey(userId);

        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(9, 12, 9, 12));
        row.setCursor(javafx.scene.Cursor.HAND);
        row.setStyle(rowStyle(selected));

        // Avatar circle
        javafx.scene.shape.Circle avatar = new javafx.scene.shape.Circle(16);
        avatar.setFill(Color.web("#444444"));
        avatar.setStroke(Color.web(StyleConstants.BORDER_COLOR));

        Label initLabel = new Label(username.isEmpty() ? "" : username.substring(0, 1).toUpperCase());
        initLabel.setStyle("-fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold;");
        if (selected) {
            initLabel.setVisible(false);
        }

        javafx.scene.shape.Circle overlay = new javafx.scene.shape.Circle(16);
        overlay.setFill(Color.web(StyleConstants.ACCENT, 0.75));
        overlay.setVisible(selected);

        Label checkMark = new Label(selected ? "✓" : "");
        checkMark.setStyle("-fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold;");

        StackPane avatarPane = new StackPane(avatar, initLabel, overlay, checkMark);

        // Load avatar image asynchronously
        controller.loadPeerAvatar(userId,
                dataUrl -> {
                    Image img = ImageUtils.decodeAvatarDataUrl(dataUrl);
                    if (img != null) {
                        avatar.setFill(new ImagePattern(img));
                        initLabel.setVisible(false);
                    }
                },
                null
        );

        Label nameLabel = new Label(username);
        nameLabel.setStyle("-fx-text-fill: " + StyleConstants.TEXT_WHITE + "; -fx-font-size: 14px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label addLabel = new Label(selected ? "Đã chọn" : "Thêm");
        addLabel.setStyle("-fx-text-fill: " + (selected ? "#4ade80" : StyleConstants.TEXT_MUTED) + "; -fx-font-size: 12px; -fx-font-weight: bold;");

        row.getChildren().addAll(avatarPane, nameLabel, spacer, addLabel);

        row.setOnMouseClicked(e -> toggleMember(userId, username));
        row.setOnMouseEntered(e -> row.setStyle(rowStyle(selected) + "-fx-background-color: #202020;"));
        row.setOnMouseExited(e -> row.setStyle(rowStyle(selected)));

        return row;
    }

    private void toggleMember(long userId, String username) {
        if (selectedMembers.containsKey(userId)) {
            selectedMembers.remove(userId);
        } else {
            selectedMembers.put(userId, username);
        }
        refreshSelectedChips();
        filterContacts(searchField.getText().trim());
    }

    private void refreshSelectedChips() {
        selectedChipsBox.getChildren().clear();

        if (selectedMembers.isEmpty()) {
            Label hint = new Label("Chưa chọn thành viên nào");
            hint.setStyle("-fx-text-fill: " + StyleConstants.TEXT_DIM + "; -fx-font-size: 12px; -fx-padding: 8px 12px;");
            selectedChipsBox.getChildren().add(hint);
            return;
        }

        // Show chips in a wrapping flow
        javafx.scene.layout.FlowPane flow = new javafx.scene.layout.FlowPane(6, 6);
        flow.setPadding(new Insets(8, 10, 8, 10));
        for (Map.Entry<Long, String> entry : selectedMembers.entrySet()) {
            long uid = entry.getKey();
            String name = entry.getValue();

            HBox chip = new HBox(6);
            chip.setAlignment(Pos.CENTER);
            chip.setPadding(new Insets(5, 10, 5, 10));
            chip.setStyle(
                    "-fx-background-color: " + StyleConstants.ACCENT + ";" +
                    "-fx-background-radius: 14px;"
            );

            Label chipName = new Label(name);
            chipName.setStyle("-fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: bold;");

            Button removeChip = new Button("✕");
            removeChip.setStyle(
                    "-fx-background-color: transparent;" +
                    "-fx-text-fill: rgba(255,255,255,0.7);" +
                    "-fx-font-size: 11px;" +
                    "-fx-cursor: hand;" +
                    "-fx-padding: 0;"
            );
            removeChip.setOnAction(e -> toggleMember(uid, name));

            chip.getChildren().addAll(chipName, removeChip);
            flow.getChildren().add(chip);
        }
        selectedChipsBox.getChildren().add(flow);
    }

    private void onCreateClicked() {
        String groupName = groupNameField.getText().trim();
        if (groupName.isEmpty()) {
            showError("Vui lòng nhập tên nhóm.");
            return;
        }
        if (groupName.length() > 100) {
            showError("Tên nhóm không được vượt quá 100 ký tự.");
            return;
        }
        if (selectedMembers.size() < 1) {
            showError("Vui lòng chọn ít nhất 1 thành viên khác.");
            return;
        }

        createBtn.setDisable(true);
        createBtn.setText("Đang tạo...");
        errorLabel.setText("");

        List<Long> memberIds = new ArrayList<>(selectedMembers.keySet());

        controller.createGroup(groupName, memberIds,
                convId -> {
                    dialog.close();
                    if (onGroupCreated != null) {
                        onGroupCreated.accept(convId, groupName);
                    }
                },
                err -> {
                    showError(err);
                    createBtn.setDisable(false);
                    createBtn.setText("Tạo nhóm");
                }
        );
    }

    // ==================== HELPERS ====================

    private void showError(String msg) {
        errorLabel.setText(msg);
    }

    private String inputStyle() {
        return "-fx-background-color: " + StyleConstants.BG_BLACK + ";" +
               "-fx-border-color: " + StyleConstants.INPUT_BORDER + ";" +
               "-fx-border-width: 1.5px;" +
               "-fx-border-radius: 10px;" +
               "-fx-background-radius: 10px;" +
               "-fx-text-fill: " + StyleConstants.TEXT_WHITE + ";" +
               "-fx-prompt-text-fill: " + StyleConstants.TEXT_DIM + ";" +
               "-fx-font-size: 14px;" +
               "-fx-padding: 10px 14px;";
    }

    private String rowStyle(boolean selected) {
        String bg = selected ? "#1a1535" : "transparent";
        return "-fx-background-color: " + bg + ";" +
               "-fx-background-radius: 8px;";
    }
}
