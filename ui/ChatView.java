import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

public class ChatView {

    private final BorderPane root;
    private final Stage stage;
    private VBox messagesBox;
    private VBox contactList;
    private TextField messageInput;

    private static final String BG_BLACK = "#000000";
    private static final String PANEL_DARK = "#111111";
    private static final String BORDER_COLOR = "#333333";
    private static final String TEXT_WHITE = "#ffffff";
    private static final String TEXT_MUTED = "#888888";
    private static final String TEXT_DIM = "#555555";
    private static final String INPUT_BORDER = "#444444";
    private static final String ACCENT = "#7c5cfc";

    public ChatView(Stage stage) {
        this.stage = stage;
        root = new BorderPane();
        root.setStyle("-fx-background-color: " + BG_BLACK + ";");

        root.setLeft(createLeftPanel());
        root.setCenter(createCenterPanel());
        root.setRight(createRightPanel());
    }

    private VBox createLeftPanel() {
        VBox panel = new VBox(10);
        panel.setPrefWidth(270);
        panel.setPadding(new Insets(20));
        panel.setStyle("""
                -fx-background-color: %s;
                -fx-border-color: %s;
                -fx-border-width: 0 1 0 0;
                """.formatted(PANEL_DARK, BORDER_COLOR));

        Label header = new Label("SinChat");
        header.setStyle("""
                -fx-font-size: 24px;
                -fx-font-weight: bold;
                -fx-text-fill: %s;
                """.formatted(TEXT_WHITE));

        TextField searchField = new TextField();
        searchField.setPromptText("T\u00ecm ki\u1ebfm cu\u1ed9c tr\u00f2 chuy\u1ec7n...");
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

        contactList = new VBox(4);
        ScrollPane scrollContacts = new ScrollPane(contactList);
        scrollContacts.setFitToWidth(true);
        scrollContacts.setStyle("""
                -fx-background: %s;
                -fx-background-color: %s;
                -fx-border-color: transparent;
                """.formatted(PANEL_DARK, PANEL_DARK));
        VBox.setVgrow(scrollContacts, Priority.ALWAYS);

        // Temporary data for the UI prototype. Later this list should come from the server.
        addContact("Github", "\u0110ang nh\u1eadp...", true);
        addContact("Bob", "You: Canongocsthang", false);
        addContact("Vua t\u00e0i x\u1ec9u", "Acc premium pornhub \u0111\u00e2y\nfb: baosaygox@gmail.com", false);
        addContact("Nguy\u1ec5n Sun Sin", "hi", false);

        panel.getChildren().addAll(header, searchField, scrollContacts);
        return panel;
    }

    private void addContact(String name, String lastMsg, boolean selected) {
        HBox contact = new HBox(12);
        contact.setAlignment(Pos.CENTER_LEFT);
        contact.setPadding(new Insets(12, 14, 12, 14));

        String radius = "16px";
        contact.setStyle("""
                -fx-background-color: %s;
                -fx-background-radius: %s;
                -fx-cursor: hand;
                """.formatted(selected ? ACCENT : "transparent", radius));

        Circle avatar = new Circle(22);
        avatar.setFill(Color.web("#444"));
        avatar.setStroke(Color.web(BORDER_COLOR));

        VBox info = new VBox(3);

        Label nameLabel = new Label(name);
        nameLabel.setStyle("""
                -fx-font-size: 15px;
                -fx-font-weight: bold;
                -fx-text-fill: %s;
                """.formatted(TEXT_WHITE));

        Label msgLabel = new Label(lastMsg);
        msgLabel.setMaxWidth(160);
        msgLabel.setWrapText(true);
        msgLabel.setStyle("""
                -fx-font-size: 12px;
                -fx-text-fill: %s;
                """.formatted(selected ? "#dddddd" : TEXT_MUTED));

        info.getChildren().addAll(nameLabel, msgLabel);
        contact.getChildren().addAll(avatar, info);

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

    private VBox createCenterPanel() {
        VBox panel = new VBox();
        panel.setStyle("-fx-background-color: " + BG_BLACK + ";");

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

        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getChildren().addAll(
                createIconButton("Call"),
                createIconButton("Video"),
                createIconButton("...")
        );

        chatHeader.getChildren().addAll(headerAvatar, headerInfo, spacer, actions);

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

        addReceivedMessage("Hi, there! \uD83D\uDC4B");
        addSentMessage("git add .");
        addSentMessage("git commit -m \"fix json\"");
        addSentMessage("git push origin main --force");
        addReceivedMessage("Committed \u2705");
        addReceivedMessage("...");

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
        messageInput.setPromptText("Nh\u1eadp tin nh\u1eafn...");
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

        Button sendBtn = new Button("G\u1eedi");
        sendBtn.setStyle("""
                -fx-background-color: %s;
                -fx-text-fill: %s;
                -fx-font-size: 15px;
                -fx-font-weight: bold;
                -fx-background-radius: 24px;
                -fx-padding: 12px 24px;
                -fx-cursor: hand;
                """.formatted(TEXT_WHITE, BG_BLACK));
        sendBtn.setOnAction(e -> sendMessage());
        messageInput.setOnAction(e -> sendMessage());

        inputBar.getChildren().addAll(attachBtn, messageInput, sendBtn);
        panel.getChildren().addAll(chatHeader, scrollMessages, inputBar);
        return panel;
    }

    private Button createIconButton(String text) {
        Button btn = new Button(text);
        btn.setStyle("""
                -fx-background-color: #222;
                -fx-text-fill: %s;
                -fx-font-size: 13px;
                -fx-background-radius: 18px;
                -fx-min-height: 38px;
                -fx-padding: 0 14px;
                -fx-cursor: hand;
                """.formatted(TEXT_WHITE));

        btn.setOnMouseEntered(e ->
                btn.setStyle("""
                        -fx-background-color: %s;
                        -fx-text-fill: %s;
                        -fx-font-size: 13px;
                        -fx-background-radius: 18px;
                        -fx-min-height: 38px;
                        -fx-padding: 0 14px;
                        -fx-cursor: hand;
                        """.formatted(ACCENT, TEXT_WHITE)));
        btn.setOnMouseExited(e ->
                btn.setStyle("""
                        -fx-background-color: #222;
                        -fx-text-fill: %s;
                        -fx-font-size: 13px;
                        -fx-background-radius: 18px;
                        -fx-min-height: 38px;
                        -fx-padding: 0 14px;
                        -fx-cursor: hand;
                        """.formatted(TEXT_WHITE)));
        return btn;
    }

    private void addReceivedMessage(String text) {
        HBox wrapper = new HBox();
        wrapper.setAlignment(Pos.CENTER_LEFT);

        Label bubble = createMessageBubble(text, "#1e1e1e", "18px 18px 18px 4px");
        wrapper.getChildren().add(bubble);
        messagesBox.getChildren().add(wrapper);
    }

    private void addSentMessage(String text) {
        HBox wrapper = new HBox();
        wrapper.setAlignment(Pos.CENTER_RIGHT);

        Label bubble = createMessageBubble(text, ACCENT, "18px 18px 4px 18px");
        wrapper.getChildren().add(bubble);
        messagesBox.getChildren().add(wrapper);
    }

    private Label createMessageBubble(String text, String backgroundColor, String radius) {
        Label bubble = new Label(text);
        bubble.setWrapText(true);
        bubble.setMaxWidth(360);
        bubble.setStyle("""
                -fx-background-color: %s;
                -fx-text-fill: %s;
                -fx-font-size: 15px;
                -fx-padding: 12px 18px;
                -fx-background-radius: %s;
                """.formatted(backgroundColor, TEXT_WHITE, radius));
        return bubble;
    }

    private void sendMessage() {
        String text = messageInput.getText().trim();

        if (!text.isEmpty()) {
            addSentMessage(text);
            messageInput.clear();
        }
    }

    private VBox createRightPanel() {
        VBox panel = new VBox(16);
        panel.setPrefWidth(250);
        panel.setAlignment(Pos.TOP_CENTER);
        panel.setPadding(new Insets(30, 20, 20, 20));
        panel.setStyle("""
                -fx-background-color: %s;
                -fx-border-color: %s;
                -fx-border-width: 0 0 0 1;
                """.formatted(PANEL_DARK, BORDER_COLOR));

        Circle profileAvatar = new Circle(55);
        profileAvatar.setFill(Color.web("#333"));
        profileAvatar.setStroke(Color.web(ACCENT));
        profileAvatar.setStrokeWidth(3);

        Label nameLabel = new Label("Sinh vi\u00ean");
        nameLabel.setStyle("""
                -fx-font-size: 20px;
                -fx-font-weight: bold;
                -fx-text-fill: %s;
                """.formatted(TEXT_WHITE));
        VBox.setMargin(nameLabel, new Insets(8, 0, 12, 0));

        Button avatarBtn = createProfileButton("\u0110\u1ed5i avatar", true);
        Button nameBtn = createProfileButton("\u0110\u1ed5i t\u00ean ng\u01b0\u1eddi d\u00f9ng", false);
        Button passBtn = createProfileButton("\u0110\u1ed5i m\u1eadt kh\u1ea9u", false);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Button logoutBtn = createProfileButton("\u0110\u0103ng xu\u1ea5t", false);
        logoutBtn.setStyle(logoutBtn.getStyle() + """
                -fx-border-color: #cc3333;
                -fx-text-fill: #cc3333;
                """);
        logoutBtn.setOnAction(e -> {
            LoginView loginView = new LoginView(stage);
            stage.setScene(loginView.createScene());
        });

        panel.getChildren().addAll(
                profileAvatar,
                nameLabel,
                avatarBtn,
                nameBtn,
                passBtn,
                spacer,
                logoutBtn
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
