import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;

public class ChatView {

    private final BorderPane root;
    private VBox messagesBox;
    private VBox contactList;
    private TextField messageInput;

    // ── Color Palette (matching LoginView) ──
    private static final String BG_BLACK      = "#000000";
    private static final String PANEL_DARK    = "#111111";
    private static final String BORDER_COLOR  = "#333333";
    private static final String TEXT_WHITE     = "#ffffff";
    private static final String TEXT_MUTED     = "#888888";
    private static final String TEXT_DIM       = "#555555";
    private static final String INPUT_BORDER   = "#444444";
    private static final String ACCENT         = "#7c5cfc";
    private static final String ACCENT_HOVER   = "#6a4ee6";

    public ChatView(Stage stage) {

        root = new BorderPane();
        root.setStyle("-fx-background-color: " + BG_BLACK + ";");

        // ═══════════════════════════════════════
        //  LEFT SIDEBAR — Contacts
        // ═══════════════════════════════════════

        VBox leftPanel = createLeftPanel();

        // ═══════════════════════════════════════
        //  CENTER — Chat Area
        // ═══════════════════════════════════════

        VBox centerPanel = createCenterPanel();

        // ═══════════════════════════════════════
        //  RIGHT SIDEBAR — Profile
        // ═══════════════════════════════════════

        VBox rightPanel = createRightPanel(stage);

        root.setLeft(leftPanel);
        root.setCenter(centerPanel);
        root.setRight(rightPanel);
    }

    // ─────────────────────────────────────────
    //  LEFT PANEL
    // ─────────────────────────────────────────

    private VBox createLeftPanel() {

        VBox panel = new VBox(10);
        panel.setPrefWidth(260);
        panel.setPadding(new Insets(20));
        panel.setStyle("""
                -fx-background-color: %s;
                -fx-border-color: %s;
                -fx-border-width: 0 1 0 0;
                """.formatted(PANEL_DARK, BORDER_COLOR));

        // Header
        Label header = new Label("Chats");
        header.setStyle("""
                -fx-font-size: 24px;
                -fx-font-weight: bold;
                -fx-text-fill: %s;
                """.formatted(TEXT_WHITE));

        // Search
        TextField searchField = new TextField();
        searchField.setPromptText("Tìm kiếm...");
        searchField.setStyle("""
                -fx-background-color: %s;
                -fx-border-color: %s;
                -fx-border-width: 1.5px;
                -fx-border-radius: 20px;
                -fx-background-radius: 20px;
                -fx-text-fill: %s;
                -fx-prompt-text-fill: %s;
                -fx-font-size: 14px;
                -fx-padding: 10px 16px;
                """.formatted(BG_BLACK, INPUT_BORDER, TEXT_WHITE, TEXT_DIM));

        // Contact list
        contactList = new VBox(4);

        ScrollPane scrollContacts = new ScrollPane(contactList);
        scrollContacts.setFitToWidth(true);
        scrollContacts.setStyle("""
                -fx-background: %s;
                -fx-background-color: %s;
                -fx-border-color: transparent;
                """.formatted(PANEL_DARK, PANEL_DARK));
        VBox.setVgrow(scrollContacts, Priority.ALWAYS);

        // Sample contacts
        addContact("Github", "Đang nhập...", true);
        addContact("Bob", "You: Canongocsthang", false);
        addContact("Vua tài xỉu", "Acc premium pornhub đây\nfb: baosaygox@gmail.com", false);
        addContact("Nguyễn Sun Sin", "hi", false);

        panel.getChildren().addAll(header, searchField, scrollContacts);

        return panel;
    }

    private void addContact(String name, String lastMsg, boolean selected) {

        HBox contact = new HBox(12);
        contact.setAlignment(Pos.CENTER_LEFT);
        contact.setPadding(new Insets(12, 14, 12, 14));

        String bgColor = selected ? ACCENT : "transparent";
        String radius = "16px";

        contact.setStyle("""
                -fx-background-color: %s;
                -fx-background-radius: %s;
                -fx-cursor: hand;
                """.formatted(bgColor, radius));

        // Avatar circle
        Circle avatar = new Circle(22);
        avatar.setFill(Color.web("#444"));
        avatar.setStroke(Color.web(BORDER_COLOR));

        // Text info
        VBox info = new VBox(3);

        Label nameLabel = new Label(name);
        nameLabel.setStyle("""
                -fx-font-size: 15px;
                -fx-font-weight: bold;
                -fx-text-fill: %s;
                """.formatted(TEXT_WHITE));

        Label msgLabel = new Label(lastMsg);
        msgLabel.setStyle("""
                -fx-font-size: 12px;
                -fx-text-fill: %s;
                """.formatted(selected ? "#ddd" : TEXT_MUTED));
        msgLabel.setMaxWidth(150);

        info.getChildren().addAll(nameLabel, msgLabel);

        contact.getChildren().addAll(avatar, info);

        // Hover effect
        if (!selected) {
            contact.setOnMouseEntered(e ->
                    contact.setStyle("""
                            -fx-background-color: #1e1e1e;
                            -fx-background-radius: %s;
                            -fx-cursor: hand;
                            """.formatted(radius)));
            contact.setOnMouseExited(e ->
                    contact.setStyle("""
                            -fx-background-color: transparent;
                            -fx-background-radius: %s;
                            -fx-cursor: hand;
                            """.formatted(radius)));
        }

        contactList.getChildren().add(contact);
    }

    // ─────────────────────────────────────────
    //  CENTER PANEL — Chat
    // ─────────────────────────────────────────

    private VBox createCenterPanel() {

        VBox panel = new VBox();
        panel.setStyle("-fx-background-color: " + BG_BLACK + ";");

        // ── Chat header ──
        HBox chatHeader = new HBox(12);
        chatHeader.setAlignment(Pos.CENTER_LEFT);
        chatHeader.setPadding(new Insets(16, 24, 16, 24));
        chatHeader.setStyle("""
                -fx-background-color: %s;
                -fx-border-color: %s;
                -fx-border-width: 0 0 1 0;
                """.formatted(PANEL_DARK, BORDER_COLOR));

        Circle headerAvatar = new Circle(20);
        headerAvatar.setFill(Color.web("#444"));

        VBox headerInfo = new VBox(2);
        Label chatName = new Label("Github");
        chatName.setStyle("""
                -fx-font-size: 17px;
                -fx-font-weight: bold;
                -fx-text-fill: %s;
                """.formatted(TEXT_WHITE));

        Label chatStatus = new Label("Online");
        chatStatus.setStyle("""
                -fx-font-size: 12px;
                -fx-text-fill: #4ade80;
                """);
        headerInfo.getChildren().addAll(chatName, chatStatus);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Action buttons
        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_RIGHT);

        Button callBtn = createIconButton("📞");
        Button videoBtn = createIconButton("🎥");
        Button moreBtn = createIconButton("⋯");

        actions.getChildren().addAll(callBtn, videoBtn, moreBtn);

        chatHeader.getChildren().addAll(headerAvatar, headerInfo, spacer, actions);

        // ── Messages area ──
        messagesBox = new VBox(12);
        messagesBox.setPadding(new Insets(20, 24, 20, 24));

        ScrollPane scrollMessages = new ScrollPane(messagesBox);
        scrollMessages.setFitToWidth(true);
        scrollMessages.setStyle("""
                -fx-background: %s;
                -fx-background-color: %s;
                -fx-border-color: transparent;
                """.formatted(BG_BLACK, BG_BLACK));
        VBox.setVgrow(scrollMessages, Priority.ALWAYS);

        // Sample messages
        addReceivedMessage("Hi, there! 👋");
        addSentMessage("git add .");
        addSentMessage("git commit -m \"fix json\"");
        addSentMessage("git push origin main --force");
        addReceivedMessage("Committed ✅");
        addReceivedMessage("...");

        // ── Message input ──
        HBox inputBar = new HBox(12);
        inputBar.setAlignment(Pos.CENTER);
        inputBar.setPadding(new Insets(16, 24, 16, 24));
        inputBar.setStyle("""
                -fx-background-color: %s;
                -fx-border-color: %s;
                -fx-border-width: 1 0 0 0;
                """.formatted(PANEL_DARK, BORDER_COLOR));

        Button attachBtn = new Button("+");
        attachBtn.setStyle("""
                -fx-background-color: %s;
                -fx-text-fill: %s;
                -fx-font-size: 20px;
                -fx-font-weight: bold;
                -fx-background-radius: 50%%;
                -fx-min-width: 40px;
                -fx-min-height: 40px;
                -fx-cursor: hand;
                """.formatted(ACCENT, TEXT_WHITE));

        messageInput = new TextField();
        messageInput.setPromptText("Nhập tin nhắn...");
        messageInput.setStyle("""
                -fx-background-color: %s;
                -fx-border-color: %s;
                -fx-border-width: 1.5px;
                -fx-border-radius: 24px;
                -fx-background-radius: 24px;
                -fx-text-fill: %s;
                -fx-prompt-text-fill: %s;
                -fx-font-size: 15px;
                -fx-padding: 12px 18px;
                """.formatted(BG_BLACK, INPUT_BORDER, TEXT_WHITE, TEXT_DIM));
        messageInput.setPrefHeight(48);
        HBox.setHgrow(messageInput, Priority.ALWAYS);

        Button sendBtn = new Button("Gửi");
        sendBtn.setStyle("""
                -fx-background-color: %s;
                -fx-text-fill: %s;
                -fx-font-size: 15px;
                -fx-font-weight: bold;
                -fx-background-radius: 24px;
                -fx-padding: 12px 24px;
                -fx-cursor: hand;
                """.formatted(TEXT_WHITE, BG_BLACK));

        // Send action
        sendBtn.setOnAction(e -> sendMessage());
        messageInput.setOnAction(e -> sendMessage());

        inputBar.getChildren().addAll(attachBtn, messageInput, sendBtn);

        panel.getChildren().addAll(chatHeader, scrollMessages, inputBar);

        return panel;
    }

    private Button createIconButton(String icon) {

        Button btn = new Button(icon);
        btn.setStyle("""
                -fx-background-color: #222;
                -fx-font-size: 16px;
                -fx-background-radius: 50%;
                -fx-min-width: 38px;
                -fx-min-height: 38px;
                -fx-cursor: hand;
                """);

        btn.setOnMouseEntered(e ->
                btn.setStyle("""
                        -fx-background-color: %s;
                        -fx-font-size: 16px;
                        -fx-background-radius: 50%%;
                        -fx-min-width: 38px;
                        -fx-min-height: 38px;
                        -fx-cursor: hand;
                        """.formatted(ACCENT)));
        btn.setOnMouseExited(e ->
                btn.setStyle("""
                        -fx-background-color: #222;
                        -fx-font-size: 16px;
                        -fx-background-radius: 50%;
                        -fx-min-width: 38px;
                        -fx-min-height: 38px;
                        -fx-cursor: hand;
                        """));
        return btn;
    }

    private void addReceivedMessage(String text) {

        HBox wrapper = new HBox();
        wrapper.setAlignment(Pos.CENTER_LEFT);

        Label bubble = new Label(text);
        bubble.setWrapText(true);
        bubble.setMaxWidth(350);
        bubble.setStyle("""
                -fx-background-color: #1e1e1e;
                -fx-text-fill: %s;
                -fx-font-size: 15px;
                -fx-padding: 12px 18px;
                -fx-background-radius: 18px 18px 18px 4px;
                """.formatted(TEXT_WHITE));

        wrapper.getChildren().add(bubble);
        messagesBox.getChildren().add(wrapper);
    }

    private void addSentMessage(String text) {

        HBox wrapper = new HBox();
        wrapper.setAlignment(Pos.CENTER_RIGHT);

        Label bubble = new Label(text);
        bubble.setWrapText(true);
        bubble.setMaxWidth(350);
        bubble.setStyle("""
                -fx-background-color: %s;
                -fx-text-fill: %s;
                -fx-font-size: 15px;
                -fx-padding: 12px 18px;
                -fx-background-radius: 18px 18px 4px 18px;
                """.formatted(ACCENT, TEXT_WHITE));

        wrapper.getChildren().add(bubble);
        messagesBox.getChildren().add(wrapper);
    }

    private void sendMessage() {

        String text = messageInput.getText().trim();

        if (!text.isEmpty()) {
            addSentMessage(text);
            messageInput.clear();
        }
    }

    // ─────────────────────────────────────────
    //  RIGHT PANEL — Profile
    // ─────────────────────────────────────────

    private VBox createRightPanel(Stage stage) {

        VBox panel = new VBox(16);
        panel.setPrefWidth(240);
        panel.setAlignment(Pos.TOP_CENTER);
        panel.setPadding(new Insets(30, 20, 20, 20));
        panel.setStyle("""
                -fx-background-color: %s;
                -fx-border-color: %s;
                -fx-border-width: 0 0 0 1;
                """.formatted(PANEL_DARK, BORDER_COLOR));

        // Avatar
        Circle profileAvatar = new Circle(55);
        profileAvatar.setFill(Color.web("#333"));
        profileAvatar.setStroke(Color.web(ACCENT));
        profileAvatar.setStrokeWidth(3);

        // Name
        Label nameLabel = new Label("Huy Tâm");
        nameLabel.setStyle("""
                -fx-font-size: 20px;
                -fx-font-weight: bold;
                -fx-text-fill: %s;
                """.formatted(TEXT_WHITE));

        VBox.setMargin(nameLabel, new Insets(8, 0, 12, 0));

        // Profile buttons
        Button avatarBtn = createProfileButton("Đổi Avatar", true);
        Button nameBtn = createProfileButton("Đổi tên người dùng", false);
        Button passBtn = createProfileButton("Đổi mật khẩu", false);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Button logoutBtn = createProfileButton("Đăng xuất", false);
        logoutBtn.setStyle(logoutBtn.getStyle() + """
                -fx-border-color: #cc3333;
                -fx-text-fill: #cc3333;
                """);

        panel.getChildren().addAll(
                profileAvatar, nameLabel,
                avatarBtn, nameBtn, passBtn,
                spacer, logoutBtn
        );

        return panel;
    }

    private Button createProfileButton(String text, boolean accent) {

        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setPrefHeight(42);

        if (accent) {
            btn.setStyle("""
                    -fx-background-color: %s;
                    -fx-text-fill: %s;
                    -fx-font-size: 14px;
                    -fx-font-weight: bold;
                    -fx-background-radius: 12px;
                    -fx-cursor: hand;
                    """.formatted(ACCENT, TEXT_WHITE));
        } else {
            btn.setStyle("""
                    -fx-background-color: transparent;
                    -fx-text-fill: %s;
                    -fx-font-size: 14px;
                    -fx-border-color: %s;
                    -fx-border-width: 1.5px;
                    -fx-border-radius: 12px;
                    -fx-background-radius: 12px;
                    -fx-cursor: hand;
                    """.formatted(TEXT_MUTED, INPUT_BORDER));
        }

        return btn;
    }

    public Scene createScene() {
        return new Scene(root, 1400, 800);
    }
}
