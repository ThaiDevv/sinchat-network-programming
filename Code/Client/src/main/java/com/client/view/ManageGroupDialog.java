package com.client.view;

import com.client.controller.ChatController;
import com.client.util.ImageUtils;
import com.client.util.StyleConstants;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Dialog for managing a group conversation.
 * Displays member list with roles, and provides admin actions:
 *   - Rename group
 *   - Add member
 *   - Kick member
 *   - Transfer admin
 *   - Disband group
 */
public class ManageGroupDialog {

    private final Stage owner;
    private final ChatController controller;
    private final long conversationId;
    private final Runnable onGroupChanged;  // callback to refresh ChatView

    // ---- state ----
    private String myRole = "MEMBER";
    private long createdBy = -1;
    private String groupName = "";
    private Stage dialog;

    // ---- UI ----
    private VBox memberListBox;
    private Label titleLabel;
    private Label errorLabel;
    private Label groupNameLabel;
    private Button editGroupNameBtn;
    private Button addMemberBtn;
    private Button disbandBtn;

    // ---- for add member sub-dialog ----
    private final List<Long> allContactIds = new ArrayList<>();
    private final Map<Long, String> allContactNames = new HashMap<>();

    public ManageGroupDialog(Stage owner, ChatController controller,
                             long conversationId, Runnable onGroupChanged) {
        this.owner = owner;
        this.controller = controller;
        this.conversationId = conversationId;
        this.onGroupChanged = onGroupChanged;
    }

    public void show() {
        dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.setTitle("Quản lý nhóm");

        VBox root = buildUI();
        Scene scene = new Scene(root, 520, 620);
        scene.setFill(Color.TRANSPARENT);
        dialog.setScene(scene);
        dialog.show();

        loadGroupMembers();
    }

    // ==================== UI BUILD ====================

    private VBox buildUI() {
        VBox card = new VBox(14);
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

        titleLabel = new Label("⚙️ Quản lý nhóm");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: " + StyleConstants.TEXT_WHITE + ";");

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

        header.getChildren().addAll(titleLabel, spacer, closeBtn);

        // ---- Group name section ----
        HBox groupNameRow = buildGroupNameRow();

        // ---- Members section ----
        Label membersLabel = new Label("👥 Thành viên");
        membersLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: " + StyleConstants.TEXT_MUTED + "; -fx-font-weight: bold;");

        memberListBox = new VBox(4);
        memberListBox.setPadding(new Insets(4));

        ScrollPane memberScroll = new ScrollPane(memberListBox);
        memberScroll.setFitToWidth(true);
        memberScroll.setPrefHeight(320);
        memberScroll.setMaxHeight(400);
        memberScroll.setStyle("-fx-background: #111111; -fx-background-color: #111111; -fx-border-color: " + StyleConstants.BORDER_COLOR + "; -fx-border-radius: 10px; -fx-background-radius: 10px;");
        memberScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        // ---- Error label ----
        errorLabel = new Label("");
        errorLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #ff6666;");
        errorLabel.setWrapText(true);

        // ---- Admin actions ----
        HBox adminActions = buildAdminActions();

        card.getChildren().addAll(header, groupNameRow, membersLabel, memberScroll, errorLabel, adminActions);

        VBox wrapper = new VBox(card);
        wrapper.setAlignment(Pos.CENTER);
        wrapper.setPadding(new Insets(20));
        wrapper.setStyle("-fx-background-color: transparent;");
        return wrapper;
    }

    private HBox buildGroupNameRow() {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 12, 8, 12));
        row.setStyle("-fx-background-color: #111111; -fx-background-radius: 10px; -fx-border-color: " + StyleConstants.BORDER_COLOR + "; -fx-border-radius: 10px; -fx-border-width: 1px;");

        Label nameIcon = new Label("📝");
        nameIcon.setStyle("-fx-font-size: 16px;");

        groupNameLabel = new Label("Đang tải...");
        groupNameLabel.setStyle("-fx-text-fill: " + StyleConstants.TEXT_WHITE + "; -fx-font-size: 15px; -fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        editGroupNameBtn = new Button("✏️");
        editGroupNameBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + StyleConstants.TEXT_MUTED + "; -fx-font-size: 14px; -fx-cursor: hand;");
        editGroupNameBtn.setVisible(false);
        editGroupNameBtn.setManaged(false);
        editGroupNameBtn.setOnAction(e -> showRenameDialog());

        row.getChildren().addAll(nameIcon, groupNameLabel, spacer, editGroupNameBtn);
        return row;
    }

    private HBox buildAdminActions() {
        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER);
        actions.setPadding(new Insets(8, 0, 0, 0));

        addMemberBtn = new Button("➕ Thêm thành viên");
        addMemberBtn.setStyle(accentBtnStyle());
        addMemberBtn.setOnMouseEntered(e -> addMemberBtn.setStyle(accentBtnHoverStyle()));
        addMemberBtn.setOnMouseExited(e -> addMemberBtn.setStyle(accentBtnStyle()));
        addMemberBtn.setOnAction(e -> showAddMemberDialog());
        addMemberBtn.setVisible(false);
        addMemberBtn.setManaged(false);

        disbandBtn = new Button("🗑️ Giải tán nhóm");
        disbandBtn.setStyle(dangerBtnStyle());
        disbandBtn.setOnMouseEntered(e -> disbandBtn.setStyle(dangerBtnHoverStyle()));
        disbandBtn.setOnMouseExited(e -> disbandBtn.setStyle(dangerBtnStyle()));
        disbandBtn.setOnAction(e -> confirmDisband());
        disbandBtn.setVisible(false);
        disbandBtn.setManaged(false);

        actions.getChildren().addAll(addMemberBtn, disbandBtn);
        return actions;
    }

    // ==================== DATA LOADING ====================

    private void loadGroupMembers() {
        controller.getGroupMembers(conversationId,
                data -> {
                    myRole = data.has("myRole") ? data.get("myRole").getAsString() : "MEMBER";
                    groupName = data.has("groupName") ? data.get("groupName").getAsString() : "";
                    createdBy = data.has("createdBy") ? data.get("createdBy").getAsLong() : -1;

                    // Update group name
                    updateGroupNameLabel();

                    // Check permissions
                    boolean isCreator = createdBy == controller.getCurrentUserId();

                    // Show/hide controls based on role
                    showMemberControls(isCreator);

                    // Render members
                    if (data.has("members")) {
                        renderMembers(data.getAsJsonArray("members"), isCreator);
                    }
                },
                err -> showError(err)
        );
    }

    private void updateGroupNameLabel() {
        if (groupNameLabel != null) {
            groupNameLabel.setText(groupName);
        }
    }

    private void showMemberControls(boolean isCreator) {
        // Edit name button — any member can rename
        if (editGroupNameBtn != null) {
            editGroupNameBtn.setVisible(true);
            editGroupNameBtn.setManaged(true);
        }
        // Add member button — any member can add
        if (addMemberBtn != null) {
            addMemberBtn.setVisible(true);
            addMemberBtn.setManaged(true);
        }
        // Disband button — only the creator can disband
        if (disbandBtn != null) {
            disbandBtn.setVisible(isCreator);
            disbandBtn.setManaged(isCreator);
        }
    }

    private void renderMembers(JsonArray members, boolean isCreator) {
        memberListBox.getChildren().clear();

        for (JsonElement el : members) {
            JsonObject member = el.getAsJsonObject();
            long userId = member.get("userId").getAsLong();
            String username = member.get("username").getAsString();
            String role = member.get("role").getAsString();
            boolean isOnline = member.has("isOnline") && member.get("isOnline").getAsBoolean();
            boolean isMe = userId == controller.getCurrentUserId();

            memberListBox.getChildren().add(buildMemberRow(userId, username, role, isOnline, isMe, isCreator));
        }

        if (members.size() == 0) {
            Label empty = new Label("Không có thành viên.");
            empty.setStyle("-fx-text-fill: " + StyleConstants.TEXT_MUTED + "; -fx-font-size: 13px; -fx-padding: 10px;");
            memberListBox.getChildren().add(empty);
        }
    }

    private HBox buildMemberRow(long userId, String username, String role, boolean isOnline, boolean isMe, boolean isCreator) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 14, 10, 14));
        row.setStyle("-fx-background-color: transparent; -fx-background-radius: 8px;");

        // Avatar circle
        javafx.scene.shape.Circle avatar = new javafx.scene.shape.Circle(18);
        avatar.setFill(Color.web("ADMIN".equals(role) ? "#7c5cfc" : "#444444"));
        avatar.setStroke(Color.web(isOnline ? "#4ade80" : StyleConstants.BORDER_COLOR));
        avatar.setStrokeWidth(isOnline ? 2.5 : 1.5);

        Label initLabel = new Label(username.substring(0, 1).toUpperCase());
        initLabel.setStyle("-fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold;");
        StackPane avatarPane = new StackPane(avatar, initLabel);

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

        // Name + role
        VBox nameBox = new VBox(2);
        Label nameLabel = new Label(username + (isMe ? " (Bạn)" : ""));
        nameLabel.setStyle("-fx-text-fill: " + StyleConstants.TEXT_WHITE + "; -fx-font-size: 14px; -fx-font-weight: bold;");

        boolean isCreatorUser = userId == createdBy;
        Label roleLabel = new Label(
            isCreatorUser ? "👑 Chủ sở hữu" :
            "ADMIN".equals(role) ? "👑 Trưởng nhóm" : "Thành viên"
        );
        roleLabel.setStyle("-fx-text-fill: " + (isCreatorUser ? "#f59e0b" : "ADMIN".equals(role) ? "#a78bfa" : StyleConstants.TEXT_MUTED) + "; -fx-font-size: 11px;");

        nameBox.getChildren().addAll(nameLabel, roleLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        row.getChildren().addAll(avatarPane, nameBox, spacer);

        // Online indicator
        if (isOnline) {
            Label onlineLabel = new Label("●");
            onlineLabel.setStyle("-fx-text-fill: #4ade80; -fx-font-size: 10px;");
            row.getChildren().add(onlineLabel);
        }

        // Creator-only actions: kick + transfer admin
        if (isCreator && !isMe) {
            HBox btnBox = new HBox(6);
            btnBox.setAlignment(Pos.CENTER_RIGHT);

            // Transfer admin: only the creator can transfer
            Button transferBtn = new Button("👑");
            transferBtn.setTooltip(new Tooltip("Chuyển quyền chủ sở hữu"));
            transferBtn.setStyle("-fx-background-color: rgba(245, 158, 11, 0.15); -fx-text-fill: #f59e0b; -fx-font-size: 12px; -fx-background-radius: 14px; -fx-min-width: 32px; -fx-min-height: 28px; -fx-cursor: hand;");
            transferBtn.setOnMouseEntered(e -> transferBtn.setStyle("-fx-background-color: #f59e0b; -fx-text-fill: white; -fx-font-size: 12px; -fx-background-radius: 14px; -fx-min-width: 32px; -fx-min-height: 28px; -fx-cursor: hand;"));
            transferBtn.setOnMouseExited(e -> transferBtn.setStyle("-fx-background-color: rgba(245, 158, 11, 0.15); -fx-text-fill: #f59e0b; -fx-font-size: 12px; -fx-background-radius: 14px; -fx-min-width: 32px; -fx-min-height: 28px; -fx-cursor: hand;"));
            transferBtn.setOnAction(e -> confirmTransferAdmin(userId, username));
            btnBox.getChildren().add(transferBtn);

            // Kick button: only the creator can kick (but not the creator themself)
            if (!isCreatorUser) {
                Button kickBtn = new Button("Xóa");
                kickBtn.setStyle(smallDangerBtnStyle());
                kickBtn.setOnMouseEntered(e -> kickBtn.setStyle(smallDangerBtnHoverStyle()));
                kickBtn.setOnMouseExited(e -> kickBtn.setStyle(smallDangerBtnStyle()));
                kickBtn.setOnAction(e -> confirmKick(userId, username));
                btnBox.getChildren().add(kickBtn);
            }

            row.getChildren().add(btnBox);
        }

        // Hover effect
        row.setOnMouseEntered(e -> row.setStyle("-fx-background-color: #202020; -fx-background-radius: 8px;"));
        row.setOnMouseExited(e -> row.setStyle("-fx-background-color: transparent; -fx-background-radius: 8px;"));

        return row;
    }

    // ==================== ACTIONS ====================

    private void showRenameDialog() {
        TextInputDialog dlg = new TextInputDialog(groupName);
        dlg.setTitle("Đổi tên nhóm");
        dlg.setHeaderText(null);
        dlg.setContentText("Tên nhóm mới:");
        dlg.initOwner(dialog);

        Optional<String> result = dlg.showAndWait();
        result.ifPresent(newName -> {
            String trimmed = newName.trim();
            if (trimmed.isEmpty()) {
                showError("Tên nhóm không được để trống.");
                return;
            }
            if (trimmed.length() > 100) {
                showError("Tên nhóm không được vượt quá 100 ký tự.");
                return;
            }
            controller.renameGroup(conversationId, trimmed,
                    name -> {
                        groupName = name;
                        updateGroupNameLabel();
                        if (onGroupChanged != null) onGroupChanged.run();
                    },
                    this::showError
            );
        });
    }

    private void showAddMemberDialog() {
        // Load contacts to pick from
        controller.loadConversations(
                conversations -> {
                    allContactIds.clear();
                    allContactNames.clear();

                    for (JsonElement el : conversations) {
                        JsonObject conv = el.getAsJsonObject();
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

                    Platform.runLater(() -> showAddMemberPicker());
                }, null
        );
    }

    private void showAddMemberPicker() {
        Stage picker = new Stage();
        picker.initOwner(dialog);
        picker.initModality(Modality.APPLICATION_MODAL);
        picker.initStyle(StageStyle.TRANSPARENT);
        picker.setTitle("Thêm thành viên");

        VBox card = new VBox(12);
        card.setPadding(new Insets(24, 26, 24, 26));
        card.setStyle(
                "-fx-background-color: #1a1a1a;" +
                "-fx-background-radius: 16px;" +
                "-fx-border-color: " + StyleConstants.BORDER_COLOR + ";" +
                "-fx-border-width: 1.5px;" +
                "-fx-border-radius: 16px;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 16, 0.3, 0, 6);"
        );

        // Header
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("➕ Thêm thành viên");
        title.setStyle("-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: " + StyleConstants.TEXT_WHITE + ";");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Button closeBtn = new Button("✕");
        closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #888; -fx-font-size: 16px; -fx-cursor: hand;");
        closeBtn.setOnAction(e -> picker.close());
        header.getChildren().addAll(title, sp, closeBtn);

        // Search
        TextField searchField = new TextField();
        searchField.setPromptText("Tìm kiếm...");
        searchField.setStyle(inputStyle());

        VBox contactList = new VBox(4);
        contactList.setPadding(new Insets(4));

        ScrollPane scroll = new ScrollPane(contactList);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(250);
        scroll.setStyle("-fx-background: #111; -fx-background-color: #111; -fx-border-color: " + StyleConstants.BORDER_COLOR + "; -fx-border-radius: 8px; -fx-background-radius: 8px;");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        Label pickerError = new Label("");
        pickerError.setStyle("-fx-text-fill: #ff6666; -fx-font-size: 12px;");
        pickerError.setWrapText(true);

        // Populate contacts
        Runnable populateContacts = () -> {
            contactList.getChildren().clear();
            String query = searchField.getText().trim().toLowerCase();
            for (Long id : allContactIds) {
                String name = allContactNames.getOrDefault(id, "");
                if (!query.isEmpty() && !name.toLowerCase().contains(query)) continue;

                HBox row = new HBox(10);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(8, 12, 8, 12));
                row.setStyle("-fx-background-color: transparent; -fx-background-radius: 8px; -fx-cursor: hand;");

                Label nameL = new Label(name);
                nameL.setStyle("-fx-text-fill: " + StyleConstants.TEXT_WHITE + "; -fx-font-size: 14px;");

                Region s = new Region();
                HBox.setHgrow(s, Priority.ALWAYS);

                Button addBtn = new Button("Thêm");
                addBtn.setStyle(smallAccentBtnStyle());
                addBtn.setOnMouseEntered(e -> addBtn.setStyle(smallAccentBtnHoverStyle()));
                addBtn.setOnMouseExited(e -> addBtn.setStyle(smallAccentBtnStyle()));
                addBtn.setOnAction(e -> {
                    addBtn.setDisable(true);
                    addBtn.setText("...");
                    controller.addGroupMember(conversationId, id,
                            () -> {
                                picker.close();
                                loadGroupMembers();
                                if (onGroupChanged != null) onGroupChanged.run();
                            },
                            err -> {
                                pickerError.setText(err);
                                addBtn.setDisable(false);
                                addBtn.setText("Thêm");
                            }
                    );
                });

                row.getChildren().addAll(nameL, s, addBtn);
                row.setOnMouseEntered(e -> row.setStyle("-fx-background-color: #202020; -fx-background-radius: 8px; -fx-cursor: hand;"));
                row.setOnMouseExited(e -> row.setStyle("-fx-background-color: transparent; -fx-background-radius: 8px; -fx-cursor: hand;"));
                contactList.getChildren().add(row);
            }
            if (contactList.getChildren().isEmpty()) {
                Label empty = new Label("Không tìm thấy.");
                empty.setStyle("-fx-text-fill: " + StyleConstants.TEXT_MUTED + "; -fx-font-size: 13px; -fx-padding: 10px;");
                contactList.getChildren().add(empty);
            }
        };

        searchField.textProperty().addListener((obs, o, n) -> populateContacts.run());
        populateContacts.run();

        card.getChildren().addAll(header, searchField, scroll, pickerError);

        VBox wrapper = new VBox(card);
        wrapper.setAlignment(Pos.CENTER);
        wrapper.setPadding(new Insets(20));
        wrapper.setStyle("-fx-background-color: transparent;");

        Scene scene = new Scene(wrapper, 420, 440);
        scene.setFill(Color.TRANSPARENT);
        picker.setScene(scene);
        picker.show();
    }

    private void confirmKick(long userId, String username) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Xác nhận xóa thành viên");
        alert.setHeaderText(null);
        alert.setContentText("Bạn có chắc chắn muốn xóa \"" + username + "\" khỏi nhóm?");
        alert.initOwner(dialog);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            controller.kickGroupMember(conversationId, userId,
                    () -> {
                        loadGroupMembers();
                        if (onGroupChanged != null) onGroupChanged.run();
                    },
                    this::showError
            );
        }
    }

    private void confirmTransferAdmin(long userId, String username) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Chuyển quyền chủ sở hữu");
        alert.setHeaderText(null);
        alert.setContentText("Bạn có chắc chắn muốn chuyển quyền chủ sở hữu nhóm cho \"" + username + "\"?\nBạn sẽ trở thành thành viên thường.");
        alert.initOwner(dialog);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            controller.transferGroupAdmin(conversationId, userId,
                    () -> {
                        loadGroupMembers();
                        if (onGroupChanged != null) onGroupChanged.run();
                    },
                    this::showError
            );
        }
    }

    private void confirmDisband() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Giải tán nhóm");
        alert.setHeaderText(null);
        alert.setContentText("Bạn có chắc chắn muốn giải tán nhóm \"" + groupName + "\"?\nHành động này không thể hoàn tác!");
        alert.initOwner(dialog);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            controller.disbandGroup(conversationId,
                    () -> {
                        dialog.close();
                        if (onGroupChanged != null) onGroupChanged.run();
                    },
                    this::showError
            );
        }
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

    private String accentBtnStyle() {
        return "-fx-background-color: " + StyleConstants.ACCENT + ";" +
               "-fx-text-fill: white;" +
               "-fx-font-size: 13px;" +
               "-fx-font-weight: bold;" +
               "-fx-background-radius: 14px;" +
               "-fx-min-height: 36px;" +
               "-fx-padding: 0 18px;" +
               "-fx-cursor: hand;";
    }

    private String accentBtnHoverStyle() {
        return "-fx-background-color: #6a4ee8;" +
               "-fx-text-fill: white;" +
               "-fx-font-size: 13px;" +
               "-fx-font-weight: bold;" +
               "-fx-background-radius: 14px;" +
               "-fx-min-height: 36px;" +
               "-fx-padding: 0 18px;" +
               "-fx-cursor: hand;";
    }

    private String dangerBtnStyle() {
        return "-fx-background-color: rgba(255, 59, 48, 0.15);" +
               "-fx-text-fill: #ff453a;" +
               "-fx-border-color: rgba(255, 69, 58, 0.3);" +
               "-fx-border-width: 1.2px;" +
               "-fx-border-radius: 14px;" +
               "-fx-font-weight: bold;" +
               "-fx-font-size: 13px;" +
               "-fx-background-radius: 14px;" +
               "-fx-min-height: 36px;" +
               "-fx-padding: 0 18px;" +
               "-fx-cursor: hand;";
    }

    private String dangerBtnHoverStyle() {
        return "-fx-background-color: #ff3b30;" +
               "-fx-text-fill: white;" +
               "-fx-border-color: transparent;" +
               "-fx-border-width: 1.2px;" +
               "-fx-border-radius: 14px;" +
               "-fx-font-weight: bold;" +
               "-fx-font-size: 13px;" +
               "-fx-background-radius: 14px;" +
               "-fx-min-height: 36px;" +
               "-fx-padding: 0 18px;" +
               "-fx-cursor: hand;";
    }

    private String smallDangerBtnStyle() {
        return "-fx-background-color: rgba(255, 59, 48, 0.15);" +
               "-fx-text-fill: #ff453a;" +
               "-fx-font-size: 11px;" +
               "-fx-font-weight: bold;" +
               "-fx-background-radius: 12px;" +
               "-fx-min-width: 44px;" +
               "-fx-min-height: 26px;" +
               "-fx-cursor: hand;";
    }

    private String smallDangerBtnHoverStyle() {
        return "-fx-background-color: #ff3b30;" +
               "-fx-text-fill: white;" +
               "-fx-font-size: 11px;" +
               "-fx-font-weight: bold;" +
               "-fx-background-radius: 12px;" +
               "-fx-min-width: 44px;" +
               "-fx-min-height: 26px;" +
               "-fx-cursor: hand;";
    }

    private String smallAccentBtnStyle() {
        return "-fx-background-color: " + StyleConstants.ACCENT + ";" +
               "-fx-text-fill: white;" +
               "-fx-font-size: 12px;" +
               "-fx-font-weight: bold;" +
               "-fx-background-radius: 12px;" +
               "-fx-min-width: 50px;" +
               "-fx-min-height: 28px;" +
               "-fx-cursor: hand;";
    }

    private String smallAccentBtnHoverStyle() {
        return "-fx-background-color: #6a4ee8;" +
               "-fx-text-fill: white;" +
               "-fx-font-size: 12px;" +
               "-fx-font-weight: bold;" +
               "-fx-background-radius: 12px;" +
               "-fx-min-width: 50px;" +
               "-fx-min-height: 28px;" +
               "-fx-cursor: hand;";
    }
}
