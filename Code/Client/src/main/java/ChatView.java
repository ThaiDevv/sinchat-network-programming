import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ChatView {

    private final BorderPane root;
    private final Stage stage;
    private VBox messagesBox;
    private VBox contactList;
    private TextField messageInput;
    private ScrollPane scrollMessages;
    private Label headerChatName;
    private Label chatStatus;

    private ChatTcpClient tcpClient;
    private final long currentUserId;
    private long currentConversationId;
    private final Gson gson = new Gson();
    private final java.util.Map<Long, Label> contactLastMsgLabels = new java.util.HashMap<>();
    private final java.util.Map<Long, Circle> statusDotsByPeerId = new java.util.HashMap<>();
    private final java.util.Map<Long, Long> conversationIdByPeerId = new java.util.HashMap<>();
    private final java.util.Map<Long, Long> peerIdByConversationId = new java.util.HashMap<>();
    private final java.util.Map<Long, String> peerLastSeenByPeerId = new java.util.HashMap<>();

    private Label typingLabel;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "typing-scheduler");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> typingHideTask;
    private long lastTypingSentTime = 0;
    private static final long TYPING_THROTTLE_MS = 1000; // Chi gui typing toi da 1 lan/giay.

    // Trang thai phan trang tin nhan.
    private int currentMessageOffset = 0;
    private boolean hasMoreMessages = true;
    private boolean isLoadingMore = false;
    private static final int PAGE_SIZE = 50;

    private final javafx.beans.value.ChangeListener<Number> scrollListener = (obs, oldVal, newVal) -> {
        // Khi scroll len dau thi load them tin nhan cu.
        if (newVal.doubleValue() < 0.05 && hasMoreMessages && !isLoadingMore && currentConversationId > 0) {
            loadMessagesForCurrentConversation(false);
        }
    };

    private Image currentAvatarImage;
    private Image initialAvatarImage;
    private final java.util.List<Image> previouslyUsedAvatars = new java.util.ArrayList<>();
    private Circle profileAvatarCircle;
    private Image selectedAvatarImage;

    private StackPane activeOldAvatarContainer = null;
    private Slider zoomSlider;
    private double mouseAnchorX, mouseAnchorY;
    private double translateAnchorX, translateAnchorY;

    private static final String BG_BLACK = "#000000";
    private static final String PANEL_DARK = "#111111";
    private static final String BORDER_COLOR = "#333333";
    private static final String TEXT_WHITE = "#ffffff";
    private static final String TEXT_MUTED = "#888888";
    private static final String TEXT_DIM = "#555555";
    private static final String INPUT_BORDER = "#444444";
    private static final String ACCENT = "#7c5cfc";


    public ChatView(Stage stage, long currentUserId) {
        this.stage = stage;
        this.currentUserId = currentUserId;
        this.currentConversationId = -1; // Chua chon conversation nao.

        // Tao san vai avatar mau de UI khong bi trong luc moi mo.
        String[] defaultOldAvatars = {
                "https://i.pravatar.cc/300?img=1",
                "https://i.pravatar.cc/300?img=2",
                "https://i.pravatar.cc/300?img=3",
                "https://i.pravatar.cc/300?img=4",
                "https://i.pravatar.cc/300?img=5"
        };
        for (String url : defaultOldAvatars) {
            previouslyUsedAvatars.add(new Image(url, true));
        }

        root = new BorderPane();
        root.setStyle("-fx-background-color: " + BG_BLACK + ";");

        root.setLeft(createLeftPanel());
        root.setCenter(createCenterPanel());
        root.setRight(createRightPanel());

        connectTcp();


        loadConversations();
    }


    public ChatView(Stage stage) {
        this(stage, 0);
    }

    private void connectTcp() {
        
        tcpClient = ChatTcpClient.getInstance();


        tcpClient.setOnNewMessage(this::onNewMessageReceived);


        tcpClient.setOnUserTyping(this::onUserTyping);


        tcpClient.setOnUserStatusChange(this::onUserStatusChange);


        tcpClient.setOnConnected(() -> {
            System.out.println("TCP socket connected for user " + currentUserId);
        });


        tcpClient.setOnDisconnected(reason -> {
            System.out.println("TCP socket disconnected: " + reason);
        });

        tcpClient.join(currentUserId);
        loadUserAvatar();
    }

    /**
     * Tai avatar nguoi dung sau khi client ket noi server.
     */
    private void loadUserAvatar() {
        ChatTcpClient.ApiResponse response = tcpClient.getUserProfile(currentUserId);
        if (response.isSuccess() && response.rawBody() != null) {
            try {
                JsonObject profile = JsonParser.parseString(response.rawBody()).getAsJsonObject();
                if (profile.has("avatar_url") && !profile.get("avatar_url").isJsonNull()) {
                    String avatarUrl = profile.get("avatar_url").getAsString();
                    if (avatarUrl != null && !avatarUrl.isEmpty() && !avatarUrl.equals("uploads/avatars/avatar_default.png")) {
                        if (avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://") || avatarUrl.startsWith("file://")) {
                            Image newAvatar = new Image(avatarUrl, true);
                            newAvatar.progressProperty().addListener((obs, oldVal, newVal) -> {
                                if (newVal.doubleValue() >= 1.0 && !newAvatar.isError()) {
                                    Platform.runLater(() -> {
                                        currentAvatarImage = newAvatar;
                                        if (profileAvatarCircle != null) {
                                            profileAvatarCircle.setFill(new ImagePattern(currentAvatarImage));
                                        }
                                    });
                                }
                            });
                        } else {
                            // Neu server tra duong dan tuong doi thi lay anh qua TCP.
                            ChatTcpClient.ApiResponse avatarResponse = tcpClient.getAvatar(currentUserId);
                            if (avatarResponse.isSuccess() && avatarResponse.rawBody() != null) {
                                JsonObject avatarData = JsonParser.parseString(avatarResponse.rawBody()).getAsJsonObject();
                                if (avatarData.has("avatarUrl") && !avatarData.get("avatarUrl").isJsonNull()) {
                                    String dataUrl = avatarData.get("avatarUrl").getAsString();
                                    if (dataUrl.startsWith("data:image/")) {
                                        try {
                                            String base64 = dataUrl.substring(dataUrl.indexOf(",") + 1);
                                            byte[] imgBytes = java.util.Base64.getDecoder().decode(base64);
                                            Image newAvatar = new Image(new java.io.ByteArrayInputStream(imgBytes));
                                            Platform.runLater(() -> {
                                                currentAvatarImage = newAvatar;
                                                if (profileAvatarCircle != null) {
                                                    profileAvatarCircle.setFill(new javafx.scene.paint.ImagePattern(currentAvatarImage));
                                                }
                                            });
                                        } catch (Exception e) {
                                            System.err.println("Error decoding avatar base64: " + e.getMessage());
                                        }
                                    } else {
                                        Image newAvatar = new Image(dataUrl, true);
                                        newAvatar.progressProperty().addListener((obs, oldVal, newVal) -> {
                                            if (newVal.doubleValue() >= 1.0 && !newAvatar.isError()) {
                                                Platform.runLater(() -> {
                                                    currentAvatarImage = newAvatar;
                                                    if (profileAvatarCircle != null) {
                                                        profileAvatarCircle.setFill(new javafx.scene.paint.ImagePattern(currentAvatarImage));
                                                    }
                                                });
                                            }
                                        });
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to load user avatar: " + e.getMessage());
            }
        }
    }

    /**
     * Xu ly tin nhan moi nhan qua TCP.
     * Chi hien thi neu tin nhan thuoc conversation dang mo.
     */
    private void onNewMessageReceived(JsonObject json) {
        long conversationId = json.get("conversationId").getAsLong();
        long senderId = json.get("senderId").getAsLong();
        String content = json.get("content").getAsString();

        // Chi hien thi neu user dang mo dung conversation.
        if (conversationId == currentConversationId) {
            // An trang thai dang go.
            if (typingLabel != null) {
                typingLabel.setVisible(false);
            }

            if (senderId == currentUserId) {
                addSentMessage(content);
            } else {
                addReceivedMessage(content);
            }
            scrollToBottom();
        }

        // Load lai danh sach conversation de item moi nhat duoc day len tren.
        Platform.runLater(this::loadConversations);
    }

    /**
     * Xu ly khi nguoi ben kia dang go trong conversation hien tai.
     */
    private void onUserTyping(JsonObject json) {
        long conversationId = json.get("conversationId").getAsLong();
        if (conversationId != currentConversationId || typingLabel == null) return;

        // Khong hien thi trang thai go cua chinh minh.
        long userId = json.has("userId") ? json.get("userId").getAsLong() : -1;
        if (userId == currentUserId) return;

        boolean isTyping = json.has("isTyping") && json.get("isTyping").getAsBoolean();
        if (!isTyping) {
            Platform.runLater(() -> typingLabel.setVisible(false));
            return;
        }

        String username = json.has("username") ? json.get("username").getAsString() : "Ai đó";
        Platform.runLater(() -> {
            typingLabel.setText(username + " đang gõ...");
            typingLabel.setVisible(true);
        });

        // Huy task an cu neu co.
        if (typingHideTask != null && !typingHideTask.isDone()) {
            typingHideTask.cancel(false);
        }

        // Tu an sau 3 giay neu khong co event dung typing gui ve.
        typingHideTask = scheduler.schedule(() ->
            javafx.application.Platform.runLater(() -> typingLabel.setVisible(false)),
            3, TimeUnit.SECONDS
        );
    }

    private void onUserStatusChange(JsonObject json) {
        if (!json.has("userId") || !json.has("status")) return;
        long peerId = json.get("userId").getAsLong();
        String status = json.get("status").getAsString();
        boolean isOnline = "online".equals(status);
        String lastSeenStr = json.has("lastSeen") ? json.get("lastSeen").getAsString() : null;
        if (lastSeenStr != null) {
            peerLastSeenByPeerId.put(peerId, lastSeenStr);
        }

        Platform.runLater(() -> {
            // Cap nhat cham trang thai o danh sach lien he.
            Circle dot = statusDotsByPeerId.get(peerId);
            if (dot != null) {
                dot.setFill(Color.web(isOnline ? "#4ade80" : "#888888"));
            }

            // Cap nhat header neu dang mo dung cuoc tro chuyen cua user nay.
            Long convId = conversationIdByPeerId.get(peerId);
            if (convId != null && convId == currentConversationId) {
                updateHeaderPresence(isOnline, isOnline ? null : lastSeenStr);
            }
        });
    }

    /**
     * Cuon xuong cuoi danh sach tin nhan.
     */
    private void scrollToBottom() {
        javafx.application.Platform.runLater(() -> {
            scrollMessages.setVvalue(1.0);
        });
    }

    /**
     * Doi conversation dang active.
     */
    public void setCurrentConversation(long conversationId, String name) {
        this.currentConversationId = conversationId;
        // Xoa tin nhan cu tren UI.
        messagesBox.getChildren().clear();
        // Reset phan trang cho conversation moi.
        currentMessageOffset = 0;
        hasMoreMessages = true;
        isLoadingMore = false;
        
        // Cap nhat ten tren header.
        if (headerChatName != null) {
            headerChatName.setText(name);
        }

        // Tai lai danh sach ben trai de lam noi bat item dang chon.
        loadConversations();
        
        // Load lich su tin nhan theo phan trang.
        loadMessagesForCurrentConversation(true);
    }

    /**
     * Load tin nhan cua conversation hien tai theo phan trang.
     * @param reset true: tai lai tu dau; false: tai them tin nhan cu khi scroll len.
     */
    private void loadMessagesForCurrentConversation(boolean reset) {
        if (currentConversationId <= 0) return;
        if (isLoadingMore) return;
        if (!reset && !hasMoreMessages) return;

        isLoadingMore = true;

        if (reset) {
            currentMessageOffset = 0;
            hasMoreMessages = true;
        }

        long capturedConversationId = currentConversationId;
        int offset = currentMessageOffset;

        CompletableFuture.supplyAsync(() -> {
            try {
                return tcpClient.getMessages(capturedConversationId, PAGE_SIZE, offset);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }).thenAccept(response -> {
            if (response != null && response.isSuccess()) {
                Platform.runLater(() -> {
                    try {
                        // Neu user da chuyen conversation thi bo qua ket qua cu.
                        if (this.currentConversationId != capturedConversationId) {
                            isLoadingMore = false;
                            return;
                        }

                        JsonObject json = gson.fromJson(response.rawBody(), JsonObject.class);
                        JsonArray messages = json.getAsJsonArray("messages");

                        if (reset) {
                            messagesBox.getChildren().clear();
                        }

                        // Chen tin nhan cu len dau danh sach.
                        int insertIndex = 0;
                        for (JsonElement element : messages) {
                            JsonObject msg = element.getAsJsonObject();
                            long senderId = msg.get("senderId").getAsLong();
                            String content = msg.get("content").getAsString();

                            HBox wrapper = new HBox();
                            if (senderId == currentUserId) {
                                wrapper.setAlignment(Pos.CENTER_RIGHT);
                                Label bubble = createMessageBubble(content, ACCENT, "18px 18px 4px 18px");
                                wrapper.getChildren().add(bubble);
                            } else {
                                wrapper.setAlignment(Pos.CENTER_LEFT);
                                Label bubble = createMessageBubble(content, "#1e1e1e", "18px 18px 18px 4px");
                                wrapper.getChildren().add(bubble);
                            }
                            messagesBox.getChildren().add(insertIndex++, wrapper);
                        }

                        // Cap nhat phan trang theo hasMore server tra ve.
                        int msgCount = messages.size();
                        currentMessageOffset = offset + msgCount;
                        if (json.has("hasMore")) {
                            hasMoreMessages = json.get("hasMore").getAsBoolean();
                        } else {
                            hasMoreMessages = msgCount >= PAGE_SIZE;
                        }

                        // Load moi thi cuon xuong cuoi, load them thi giu vi tri.
                        if (reset) {
                            scrollToBottom();
                        }
                    } catch (Exception e) {
                        System.err.println("Failed to parse messages JSON: " + e.getMessage());
                    } finally {
                        isLoadingMore = false;
                    }
                });
            } else {
                isLoadingMore = false;
            }
        });
    }

    private void loadConversations() {
        statusDotsByPeerId.clear();
        conversationIdByPeerId.clear();
        peerIdByConversationId.clear();
        CompletableFuture.supplyAsync(() -> {
            try {
                return tcpClient.getConversations(currentUserId);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }).thenAccept(response -> {
            if (response != null && response.isSuccess()) {
                Platform.runLater(() -> {
                    contactList.getChildren().clear();
                    contactLastMsgLabels.clear();
                    try {
                        JsonObject json = gson.fromJson(response.rawBody(), JsonObject.class);
                        JsonArray data = json.getAsJsonArray("conversations");
                        boolean activeConvStillExists = false;
                        for (JsonElement element : data) {
                            JsonObject conv = element.getAsJsonObject();
                            long id = conv.get("conversationId").getAsLong();
                            String name = conv.get("displayName").getAsString();
                            String lastMsg = conv.get("lastMessage").getAsString();
                            boolean isSelected = (id == currentConversationId);
                            if (isSelected) {
                                activeConvStillExists = true;
                                boolean isPeerOnline = conv.has("isOnline") && conv.get("isOnline").getAsBoolean();
                                String lastSeen = conv.has("lastSeen") ? conv.get("lastSeen").getAsString() : null;
                                updateHeaderPresence(isPeerOnline, lastSeen);
                            }
                            addContactWithPresence(id, name, lastMsg, isSelected, conv);
                        }
                        if (!activeConvStillExists && chatStatus != null) {
                            updateHeaderPresence(false, null);
                        }
                    } catch (Exception e) {
                        System.err.println("Failed to parse conversations JSON");
                    }
                });
            }
        });
    }

    private void updateHeaderPresence(boolean isOnline, String lastSeen) {
        if (chatStatus != null) {
            if (isOnline) {
                chatStatus.setText("Online");
                chatStatus.setStyle("-fx-font-size: 12px; -fx-text-fill: #4ade80;");
            } else {
                String statusText = "Offline";
                if (lastSeen != null && !lastSeen.isEmpty()) {
                    statusText = "Offline • seen " + lastSeen;
                }
                chatStatus.setText(statusText);
                chatStatus.setStyle("-fx-font-size: 12px; -fx-text-fill: #888888;");
            }
        }
    }

    private void addContactWithPresence(long conversationId, String name, String lastMsg, boolean selected, JsonObject conv) {
        HBox contact = new HBox(12);
        contact.setAlignment(Pos.CENTER_LEFT);
        contact.setPadding(new Insets(12, 14, 12, 14));

        String radius = "16px";
        contact.setStyle("""
                -fx-background-color: %s;
                -fx-background-radius: %s;
                -fx-cursor: hand;
                """.formatted(selected ? ACCENT : "transparent", radius));

        StackPane avatarContainer = new StackPane();
        Circle avatar = new Circle(22);
        avatar.setFill(Color.web("#444"));
        avatar.setStroke(Color.web(BORDER_COLOR));
        avatarContainer.getChildren().add(avatar);

        if (conv.has("peerId")) {
            long peerId = conv.get("peerId").getAsLong();
            conversationIdByPeerId.put(peerId, conversationId);
            peerIdByConversationId.put(conversationId, peerId);
        }

        if (conv.has("isOnline")) {
            boolean isOnline = conv.get("isOnline").getAsBoolean();
            Circle statusDot = new Circle(6);
            statusDot.setFill(Color.web(isOnline ? "#4ade80" : "#888888"));
            statusDot.setStroke(Color.web(BG_BLACK));
            statusDot.setStrokeWidth(1.5);
            StackPane.setAlignment(statusDot, Pos.BOTTOM_RIGHT);
            avatarContainer.getChildren().add(statusDot);

            // Luu cham trang thai de cap nhat dung user khi co event online/offline.
            if (conv.has("peerId")) {
                long peerId = conv.get("peerId").getAsLong();
                statusDotsByPeerId.put(peerId, statusDot);
            }
        }

        VBox info = new VBox(3);

        Label nameLabel = new Label(name);
        nameLabel.setStyle("""
                -fx-font-size: 15px;
                -fx-font-weight: bold;
                -fx-text-fill: %s;
                """.formatted(TEXT_WHITE));

        String formattedLastMsg = lastMsg;
        if (lastMsg != null && !lastMsg.isEmpty()) {
            if (conv.has("lastMessageSenderId")) {
                long senderId = conv.get("lastMessageSenderId").getAsLong();
                if (senderId == currentUserId) {
                    formattedLastMsg = lastMsg;
                } else {
                    formattedLastMsg = name + ": " + lastMsg;
                }
            }
        }

        Label msgLabel = new Label(formattedLastMsg);
        msgLabel.setMaxWidth(160);
        msgLabel.setWrapText(true);
        msgLabel.setStyle("""
                -fx-font-size: 12px;
                -fx-text-fill: %s;
                """.formatted(selected ? "#dddddd" : TEXT_MUTED));

        contactLastMsgLabels.put(conversationId, msgLabel);

        info.getChildren().addAll(nameLabel, msgLabel);
        contact.getChildren().addAll(avatarContainer, info);

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

        contact.setOnMouseClicked(e -> {
            setCurrentConversation(conversationId, name);
        });

        contactList.getChildren().add(contact);
    }
    // Cac thanh phan UI chinh.

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
        searchField.setPromptText("T\u00ecm ki\u1ebfm...");
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

        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            String query = newVal.trim();
            if (query.isEmpty()) {
                loadConversations();
            } else {
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return tcpClient.searchUsers(query);
                    } catch (Exception e) {
                        e.printStackTrace();
                        return null;
                    }
                }).thenAccept(response -> {
                    if (response != null && response.isSuccess()) {
                        Platform.runLater(() -> {
                            contactList.getChildren().clear();
                            contactLastMsgLabels.clear();
                            try {
                                JsonObject json = gson.fromJson(response.rawBody(), JsonObject.class);
                                JsonArray users = json.getAsJsonArray("users");
                                for (JsonElement element : users) {
                                    JsonObject user = element.getAsJsonObject();
                                    long uId = user.get("userId").getAsLong();
                                    String username = user.get("username").getAsString();
                                    
                                    // Tao item hien thi cho user tim duoc.
                                    HBox contact = new HBox(12);
                                    contact.setAlignment(Pos.CENTER_LEFT);
                                    contact.setPadding(new Insets(12, 14, 12, 14));
                                    String radius = "16px";
                                    contact.setStyle("""
                                            -fx-background-color: transparent;
                                            -fx-background-radius: %s;
                                            -fx-cursor: hand;
                                            """.formatted(radius));

                                    Circle avatar = new Circle(22);
                                    avatar.setFill(Color.web("#444"));
                                    avatar.setStroke(Color.web(BORDER_COLOR));

                                    VBox info = new VBox(3);
                                    Label nameLabel = new Label(username);
                                    nameLabel.setStyle("""
                                            -fx-font-size: 15px;
                                            -fx-font-weight: bold;
                                            -fx-text-fill: %s;
                                            """.formatted(TEXT_WHITE));
                                    Label msgLabel = new Label("Nh\u1eadp \u0111\u1ec3 nh\u1eafn tin");
                                    msgLabel.setStyle("""
                                            -fx-font-size: 12px;
                                            -fx-text-fill: %s;
                                            """.formatted(TEXT_MUTED));

                                    info.getChildren().addAll(nameLabel, msgLabel);
                                    contact.getChildren().addAll(avatar, info);

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

                                    contact.setOnMouseClicked(e -> {
                                        CompletableFuture.supplyAsync(() -> {
                                            try {
                                                return tcpClient.getOrCreateConversation(currentUserId, uId);
                                            } catch (Exception ex) {
                                                ex.printStackTrace();
                                                return null;
                                            }
                                        }).thenAccept(convResp -> {
                                            if (convResp != null && convResp.isSuccess()) {
                                                Platform.runLater(() -> {
                                                    try {
                                                        JsonObject convJson = gson.fromJson(convResp.rawBody(), JsonObject.class);
                                                        long conversationId = convJson.get("conversationId").getAsLong();
                                                        searchField.clear();
                                                        loadConversations();
                                                        setCurrentConversation(conversationId, username);
                                                    } catch (Exception ex) {
                                                        ex.printStackTrace();
                                                    }
                                                });
                                            }
                                        });
                                    });

                                    contactList.getChildren().add(contact);
                                }
                            } catch (Exception e) {
                                System.err.println("Failed to parse search users JSON");
                            }
                        });
                    }
                });
            }
        });

        contactList = new VBox(4);
        ScrollPane scrollContacts = new ScrollPane(contactList);
        scrollContacts.setFitToWidth(true);
        scrollContacts.setStyle("""
                -fx-background: %s;
                -fx-background-color: %s;
                -fx-border-color: transparent;
                """.formatted(PANEL_DARK, PANEL_DARK));
        VBox.setVgrow(scrollContacts, Priority.ALWAYS);

        // Khong dung du lieu ao nua.
        panel.getChildren().addAll(header, searchField, scrollContacts);
        return panel;
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
        headerChatName = new Label("Chọn người để chat");
        headerChatName.setStyle("""
                -fx-font-size: 17px;
                -fx-font-weight: bold;
                -fx-text-fill: %s;
                """.formatted(TEXT_WHITE));

        chatStatus = new Label("Offline");
        chatStatus.setStyle("""
                -fx-font-size: 12px;
                -fx-text-fill: #888888;
                """);
        headerInfo.getChildren().addAll(headerChatName, chatStatus);

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

        // Vung hien thi tin nhan.
        messagesBox = new VBox(12);
        messagesBox.setPadding(new Insets(20, 24, 20, 24));

        scrollMessages = new ScrollPane(messagesBox);
        scrollMessages.setFitToWidth(true);
        scrollMessages.setStyle("""
                -fx-background: %s;
                -fx-background-color: %s;
                -fx-border-color: transparent;
                """.formatted(BG_BLACK, BG_BLACK));
        VBox.setVgrow(scrollMessages, Priority.ALWAYS);

        // Gan listener de load them tin nhan khi scroll len dau.
        scrollMessages.vvalueProperty().addListener(scrollListener);

        // Dong hien thi trang thai dang go.
        typingLabel = new Label("Đang gõ...");
        typingLabel.setVisible(false);
        typingLabel.setPadding(new Insets(4, 24, 4, 24));
        typingLabel.setStyle("""
                -fx-font-size: 12px;
                -fx-text-fill: %s;
                -fx-font-style: italic;
                """.formatted(TEXT_MUTED));

        // Thanh nhap tin nhan.
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

        // Gui typing indicator khi user dang go, gioi han toi da 1 lan/giay.
        messageInput.textProperty().addListener((obs, oldVal, newVal) -> {
            if (tcpClient != null && tcpClient.isConnected() && currentConversationId > 0) {
                if (newVal.trim().isEmpty()) {
                    // Neu xoa het chu thi bao ngay la da dung go.
                    tcpClient.sendTyping(currentConversationId, -1, false);
                } else {
                    long now = System.currentTimeMillis();
                    if (now - lastTypingSentTime >= TYPING_THROTTLE_MS) {
                        lastTypingSentTime = now;
                        tcpClient.sendTyping(currentConversationId, -1, true);
                    }
                }
            }
        });

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
        panel.getChildren().addAll(chatHeader, scrollMessages, typingLabel, inputBar);
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

    /**
     * Gui tin nhan qua TCP.
     * Server luu vao DB roi broadcast lai cho cac thanh vien, gom ca sender.
     * UI cap nhat khi nhan broadcast NEW_MESSAGE tu server.
     */
    private void sendMessage() {
        String text = messageInput.getText().trim();

        if (!text.isEmpty()) {
            if (tcpClient != null && tcpClient.isConnected() && currentConversationId > 0) {
                // Gui qua TCP, server luu DB roi broadcast ve cho moi nguoi.
                tcpClient.sendMessage(currentConversationId, currentUserId, text);
                
                // Bao ngay la minh da dung go sau khi gui tin.
                tcpClient.sendTyping(currentConversationId, -1, false);
            }
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

        currentAvatarImage = createDefaultAvatarImage();
        initialAvatarImage = currentAvatarImage;
        profileAvatarCircle = new Circle(55);
        // Chi dung ImagePattern khi anh load xong va khong loi.
        if (!currentAvatarImage.isError() && currentAvatarImage.getProgress() >= 1.0) {
            profileAvatarCircle.setFill(new ImagePattern(currentAvatarImage));
        } else {
            profileAvatarCircle.setFill(Color.web(ACCENT));
            currentAvatarImage.progressProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal.doubleValue() >= 1.0 && !currentAvatarImage.isError()) {
                    profileAvatarCircle.setFill(new ImagePattern(currentAvatarImage));
                }
            });
        }
        profileAvatarCircle.setStroke(Color.web(ACCENT));
        profileAvatarCircle.setStrokeWidth(3);
        profileAvatarCircle.setCursor(javafx.scene.Cursor.HAND);
        profileAvatarCircle.setOnMouseClicked(e -> {
            ContextMenu menu = createAvatarContextMenu();
            menu.show(profileAvatarCircle, e.getScreenX(), e.getScreenY());
        });

        Label nameLabel = new Label("Sinh vi\u00ean");
        nameLabel.setStyle("""
                -fx-font-size: 20px;
                -fx-font-weight: bold;
                -fx-text-fill: %s;
                """.formatted(TEXT_WHITE));
        VBox.setMargin(nameLabel, new Insets(8, 0, 12, 0));

        Button avatarBtn = createProfileButton("\u0110\u1ed5i avatar", true);
        avatarBtn.setOnAction(e -> {
            ContextMenu menu = createAvatarContextMenu();
            menu.show(avatarBtn, javafx.geometry.Side.BOTTOM, 0, 0);
        });

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
            // Dung resetInstance de dung connectLoop cu va tao instance moi khi login lai.
            ChatTcpClient.resetInstance();
            LoginView loginView = new LoginView(stage);
            stage.setScene(loginView.createScene());
        });

        panel.getChildren().addAll(
                profileAvatarCircle,
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

    // Modal doi avatar, lay tu man test doi avatar roi ghep vao UI chinh.
    private ContextMenu createAvatarContextMenu() {
        ContextMenu contextMenu = new ContextMenu();
        
        contextMenu.setStyle("""
                -fx-background-color: #222222;
                -fx-border-color: #333333;
                -fx-border-width: 1px;
                -fx-background-radius: 10px;
                -fx-border-radius: 10px;
                -fx-padding: 6px;
                """);
        
        MenuItem changeItem = new MenuItem("Đổi avatar");
        changeItem.setStyle("""
                -fx-text-fill: white;
                -fx-font-size: 14px;
                -fx-font-weight: bold;
                -fx-padding: 8px 16px;
                """);
        changeItem.setOnAction(e -> openAvatarModal(stage));
        
        MenuItem restoreItem = new MenuItem("Khôi phục lại avatar ban đầu");
        restoreItem.setStyle("""
                -fx-text-fill: #cccccc;
                -fx-font-size: 14px;
                -fx-font-weight: bold;
                -fx-padding: 8px 16px;
                """);
        restoreItem.setOnAction(e -> {
            if (currentAvatarImage == initialAvatarImage) {
                showAvatarToast("Ảnh đại diện đã là ảnh ban đầu!", "#555555");
                return;
            }
            if (initialAvatarImage != null) {
                currentAvatarImage = initialAvatarImage;
                profileAvatarCircle.setFill(new ImagePattern(currentAvatarImage));
                
                // Dong bo avatar qua luong TCP hien tai.
                CompletableFuture.supplyAsync(() -> uploadAvatarViaTcp(initialAvatarImage))
                        .thenAccept(response -> Platform.runLater(() -> {
                            if (response != null && response.isSuccess()) {
                                showAvatarToast("Đã khôi phục avatar ban đầu!", "#1f883d");
                            } else {
                                String errMsg = response != null ? response.message() : "Lỗi kết nối server";
                                showAvatarToast("Lỗi khi đồng bộ: " + errMsg, "#cc3333");
                            }
                        }));
            }
        });
        
        contextMenu.getItems().addAll(changeItem, restoreItem);
        return contextMenu;
    }

    private void openAvatarModal(Stage owner) {
        Stage modal = new Stage();
        modal.initOwner(owner);
        modal.initModality(Modality.APPLICATION_MODAL);
        modal.setTitle("Chọn ảnh đại diện");

        BorderPane modalRoot = new BorderPane();
        modalRoot.setStyle("-fx-background-color: #1c1c1c;");

        // Header cua modal.
        StackPane header = new StackPane();
        header.setPrefHeight(80);
        header.setMinHeight(80);
        header.setMaxHeight(80);
        header.setStyle("""
                -fx-background-color: #1c1c1c;
                -fx-border-color: transparent transparent #333333 transparent;
                -fx-border-width: 0 0 1 0;
                """);

        Label title = new Label("Chọn ảnh đại diện");
        title.setStyle("""
                -fx-text-fill: white;
                -fx-font-size: 30px;
                -fx-font-weight: bold;
                -fx-font-family: Arial;
                """);
        StackPane.setAlignment(title, Pos.CENTER);

        Button closeBtn = new Button("✕");
        String closeBtnStyle = """
                -fx-background-color: #333333;
                -fx-text-fill: white;
                -fx-font-size: 16px;
                -fx-font-weight: bold;
                -fx-background-radius: 21px;
                -fx-min-width: 42px; -fx-max-width: 42px;
                -fx-min-height: 42px; -fx-max-height: 42px;
                -fx-cursor: hand;
                """;
        closeBtn.setStyle(closeBtnStyle);
        closeBtn.setOnMouseEntered(ev -> closeBtn.setStyle(closeBtnStyle + "-fx-background-color: #444444;"));
        closeBtn.setOnMouseExited(ev -> closeBtn.setStyle(closeBtnStyle));
        closeBtn.setOnAction(ev -> modal.close());
        StackPane.setAlignment(closeBtn, Pos.CENTER_RIGHT);
        StackPane.setMargin(closeBtn, new Insets(0, 20, 0, 0));

        header.getChildren().addAll(title, closeBtn);
        modalRoot.setTop(header);

        // Noi dung cuon cua modal.
        VBox scrollContent = new VBox();
        scrollContent.setStyle("-fx-background-color: #1c1c1c;");
        scrollContent.setAlignment(Pos.TOP_CENTER);

        // Khu vuc xem truoc avatar.
        VBox previewSection = new VBox(20);
        previewSection.setAlignment(Pos.CENTER);
        previewSection.setPadding(new Insets(30));

        StackPane previewContainer = new StackPane();
        previewContainer.setPrefSize(500, 500);
        previewContainer.setMaxSize(500, 500);
        previewContainer.setMinSize(500, 500);
        previewContainer.setStyle("""
                -fx-background-color: #111111;
                -fx-background-radius: 16px;
                """);

        // Tai avatar hien tai, neu khong co thi dung avatar mac dinh.
        Image previewImg = currentAvatarImage != null ? currentAvatarImage : createDefaultAvatarImage();
        ImageView previewImage = new ImageView(previewImg);
        previewImage.setPreserveRatio(true);
        previewImage.setSmooth(true);

        // Cat anh theo khung tron.
        Circle containerClip = new Circle(250, 250, 250);
        previewContainer.setClip(containerClip);
        previewContainer.getChildren().add(previewImage);

        // Thanh zoom anh.
        zoomSlider = new Slider(1.0, 3.0, 1.0);
        zoomSlider.setPrefWidth(300);
        zoomSlider.setBlockIncrement(0.05);

        Label zoomLabel = new Label("Zoom:");
        zoomLabel.setStyle("-fx-text-fill: #aaaaaa; -fx-font-size: 14px; -fx-font-weight: bold;");

        Label zoomPercentLabel = new Label("100%");
        zoomPercentLabel.setStyle("-fx-text-fill: #aaaaaa; -fx-font-size: 14px; -fx-font-weight: bold; -fx-min-width: 40px;");

        HBox zoomRow = new HBox(12, zoomLabel, zoomSlider, zoomPercentLabel);
        zoomRow.setAlignment(Pos.CENTER);

        zoomSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double zoom = newVal.doubleValue();
            previewImage.setScaleX(zoom);
            previewImage.setScaleY(zoom);
            zoomPercentLabel.setText(String.format("%d%%", (int) Math.round(zoom * 100)));
            clampImagePosition(previewImage);
        });

        // Xu ly keo anh trong khung preview.
        previewContainer.setOnMousePressed(evt -> {
            mouseAnchorX = evt.getSceneX();
            mouseAnchorY = evt.getSceneY();
            translateAnchorX = previewImage.getTranslateX();
            translateAnchorY = previewImage.getTranslateY();
        });
        previewContainer.setOnMouseDragged(evt -> {
            previewImage.setTranslateX(translateAnchorX + evt.getSceneX() - mouseAnchorX);
            previewImage.setTranslateY(translateAnchorY + evt.getSceneY() - mouseAnchorY);
            clampImagePosition(previewImage);
        });

        selectedAvatarImage = previewImg;
        updatePreviewFit(previewImage, selectedAvatarImage);

        previewSection.getChildren().addAll(previewContainer, zoomRow);
        scrollContent.getChildren().add(previewSection);

        // Danh sach avatar da dung hoac avatar mau.
        VBox oldAvatarSection = new VBox(18);
        oldAvatarSection.setPadding(new Insets(0, 30, 30, 30));

        Label oldAvatarTitle = new Label("Ảnh đại diện đã từng dùng");
        oldAvatarTitle.setStyle("""
                -fx-text-fill: #cccccc;
                -fx-font-size: 18px;
                -fx-font-weight: bold;
                -fx-font-family: Arial;
                """);

        FlowPane oldAvatarList = new FlowPane();
        oldAvatarList.setHgap(14);
        oldAvatarList.setVgap(14);

        String containerDefaultStyle = """
                -fx-border-color: transparent;
                -fx-border-width: 3px;
                -fx-border-radius: 21px;
                -fx-background-radius: 21px;
                -fx-padding: 0;
                -fx-cursor: hand;
                """;
        String containerSelectedStyle = """
                -fx-border-color: #1877f2;
                -fx-border-width: 3px;
                -fx-border-radius: 21px;
                -fx-background-radius: 21px;
                -fx-padding: 0;
                -fx-cursor: hand;
                """;

        activeOldAvatarContainer = null;

        for (Image img : previouslyUsedAvatars) {
            ImageView oldAvatarView = new ImageView(img);
            oldAvatarView.setFitWidth(90);
            oldAvatarView.setFitHeight(90);
            oldAvatarView.setPreserveRatio(false);

            Rectangle rClip = new Rectangle(90, 90);
            rClip.setArcWidth(36);
            rClip.setArcHeight(36);
            oldAvatarView.setClip(rClip);

            StackPane imgContainer = new StackPane(oldAvatarView);
            imgContainer.setPrefSize(96, 96);
            imgContainer.setMaxSize(96, 96);
            imgContainer.setStyle(containerDefaultStyle);

            imgContainer.setOnMouseEntered(ev -> {
                imgContainer.setScaleX(1.05);
                imgContainer.setScaleY(1.05);
            });
            imgContainer.setOnMouseExited(ev -> {
                imgContainer.setScaleX(1.0);
                imgContainer.setScaleY(1.0);
            });
            imgContainer.setOnMouseClicked(ev -> {
                if (activeOldAvatarContainer != null)
                    activeOldAvatarContainer.setStyle(containerDefaultStyle);
                activeOldAvatarContainer = imgContainer;
                imgContainer.setStyle(containerSelectedStyle);

                selectedAvatarImage = img;
                previewImage.setImage(img);
                updatePreviewFit(previewImage, img);
            });

            oldAvatarList.getChildren().add(imgContainer);
        }

        oldAvatarSection.getChildren().addAll(oldAvatarTitle, oldAvatarList);
        scrollContent.getChildren().add(oldAvatarSection);

        // Cac nut thao tac upload va xoa.
        HBox actionsSection = new HBox(16);
        actionsSection.setPadding(new Insets(0, 30, 30, 30));

        Button uploadBtn = new Button("Tải ảnh lên");
        String uploadBtnStyle = """
                -fx-background-color: #1877f2;
                -fx-text-fill: white;
                -fx-font-size: 15px;
                -fx-font-weight: bold;
                -fx-background-radius: 14px;
                -fx-pref-height: 52px;
                -fx-cursor: hand;
                """;
        uploadBtn.setStyle(uploadBtnStyle);
        uploadBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(uploadBtn, Priority.ALWAYS);
        uploadBtn.setOnMouseEntered(ev -> uploadBtn.setStyle(uploadBtnStyle + "-fx-background-color: #1565c0;"));
        uploadBtn.setOnMouseExited(ev -> uploadBtn.setStyle(uploadBtnStyle));

        Button deleteBtn = new Button("Xóa avatar");
        String deleteBtnStyle = """
                -fx-background-color: #2d2d2d;
                -fx-text-fill: white;
                -fx-font-size: 15px;
                -fx-font-weight: bold;
                -fx-background-radius: 14px;
                -fx-pref-height: 52px;
                -fx-cursor: hand;
                """;
        deleteBtn.setStyle(deleteBtnStyle);
        deleteBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(deleteBtn, Priority.ALWAYS);
        deleteBtn.setOnMouseEntered(ev -> deleteBtn.setStyle(deleteBtnStyle + "-fx-background-color: #3d3d3d;"));
        deleteBtn.setOnMouseExited(ev -> deleteBtn.setStyle(deleteBtnStyle));

        uploadBtn.setOnAction(evt -> {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg")
            );
            File file = chooser.showOpenDialog(modal);
            if (file != null) {
                if (activeOldAvatarContainer != null) {
                    activeOldAvatarContainer.setStyle(containerDefaultStyle);
                    activeOldAvatarContainer = null;
                }
               Image img = new Image(
                    file.toURI().toString(),
                    true
                );
                selectedAvatarImage = img;
                previewImage.setImage(img);
                updatePreviewFit(previewImage, img);
            }
        });

        deleteBtn.setOnAction(evt -> {
            if (activeOldAvatarContainer != null) {
                activeOldAvatarContainer.setStyle(containerDefaultStyle);
                activeOldAvatarContainer = null;
            }
            Image defaultImg = createDefaultAvatarImage();
            selectedAvatarImage = defaultImg;
            previewImage.setImage(defaultImg);
            updatePreviewFit(previewImage, defaultImg);
        });

        actionsSection.getChildren().addAll(uploadBtn, deleteBtn);
        scrollContent.getChildren().add(actionsSection);

        // Khung cuon.
        ScrollPane scrollPane = new ScrollPane(scrollContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("""
                -fx-background: #1c1c1c;
                -fx-background-color: #1c1c1c;
                -fx-viewport-background-color: transparent;
                """);
        modalRoot.setCenter(scrollPane);

        // Footer chua nut dong va luu.
        HBox footer = new HBox(14);
        footer.setPadding(new Insets(20));
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setStyle("""
                -fx-background-color: #1c1c1c;
                -fx-border-color: #333333 transparent transparent transparent;
                -fx-border-width: 1 0 0 0;
                """);

        Button cancelBtn = new Button("Hủy");
        String cancelBtnStyle = """
                -fx-background-color: #333333;
                -fx-text-fill: white;
                -fx-font-size: 15px;
                -fx-font-weight: bold;
                -fx-background-radius: 12px;
                -fx-min-width: 120px;
                -fx-min-height: 50px;
                -fx-cursor: hand;
                """;
        cancelBtn.setStyle(cancelBtnStyle);
        cancelBtn.setOnMouseEntered(ev -> cancelBtn.setStyle(cancelBtnStyle + "-fx-background-color: #444444;"));
        cancelBtn.setOnMouseExited(ev -> cancelBtn.setStyle(cancelBtnStyle));
        cancelBtn.setOnAction(ev -> modal.close());

        Button saveBtn = new Button("Lưu");
        String saveBtnStyle = """
                -fx-background-color: #1877f2;
                -fx-text-fill: white;
                -fx-font-size: 15px;
                -fx-font-weight: bold;
                -fx-background-radius: 12px;
                -fx-min-width: 120px;
                -fx-min-height: 50px;
                -fx-cursor: hand;
                """;
        saveBtn.setStyle(saveBtnStyle);
        saveBtn.setOnMouseEntered(ev -> saveBtn.setStyle(saveBtnStyle + "-fx-background-color: #1565c0;"));
        saveBtn.setOnMouseExited(ev -> saveBtn.setStyle(saveBtnStyle));

        saveBtn.setOnAction(evt -> {
            // Chup phan preview avatar dang crop hinh tron.
            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.TRANSPARENT);
            WritableImage croppedImage = previewContainer.snapshot(params, null);

            // Cap nhat avatar tren UI truoc.
            currentAvatarImage = croppedImage;
            profileAvatarCircle.setFill(new ImagePattern(currentAvatarImage));

            // Them anh vua doi vao danh sach avatar da tung dung.
            previouslyUsedAvatars.add(0, croppedImage);

            // Gui avatar len server qua TCP o background.
            saveBtn.setDisable(true);
            saveBtn.setText("Đang lưu...");

            CompletableFuture.supplyAsync(() -> uploadAvatarViaTcp(croppedImage)).thenAccept(response -> Platform.runLater(() -> {
                saveBtn.setDisable(false);
                saveBtn.setText("Lưu");
                if (response != null && response.isSuccess()) {
                    modal.close();
                    showAvatarToast("Cập nhật avatar thành công!", "#1f883d");
                } else {
                    String errMsg = response != null ? response.message() : "Lỗi kết nối server";
                    showAvatarToast(errMsg, "#cc3333");
                }
            }));
        });

        footer.getChildren().addAll(cancelBtn, saveBtn);
        modalRoot.setBottom(footer);

        Scene scene = new Scene(modalRoot, 920, 850);
        modal.setScene(scene);
        modal.showAndWait();
    }

    // Cac ham ho tro avatar.

    private void updatePreviewFit(ImageView previewImage, Image image) {
        if (image == null) return;

        if (image.getWidth() <= 0 || image.getHeight() <= 0) {
            image.widthProperty().addListener((obs, oldW, newW) -> {
                if (newW.doubleValue() > 0) {
                    Platform.runLater(() -> updatePreviewFit(previewImage, image));
                }
            });
            return;
        }

        double targetSize = 500;
        double scale = Math.max(targetSize / image.getWidth(), targetSize / image.getHeight());

        previewImage.setFitWidth(image.getWidth() * scale);
        previewImage.setFitHeight(image.getHeight() * scale);

        previewImage.setTranslateX(0);
        previewImage.setTranslateY(0);
        previewImage.setScaleX(1.0);
        previewImage.setScaleY(1.0);

        if (zoomSlider != null) zoomSlider.setValue(1.0);
    }

    private void clampImagePosition(ImageView previewImage) {
        double scaleX = previewImage.getScaleX();
        double scaleY = previewImage.getScaleY();
        double w = previewImage.getFitWidth() * scaleX;
        double h = previewImage.getFitHeight() * scaleY;

        double maxX = Math.max(0, w / 2 - 250);
        double maxY = Math.max(0, h / 2 - 250);

        double tx = previewImage.getTranslateX();
        double ty = previewImage.getTranslateY();

        if (tx < -maxX) previewImage.setTranslateX(-maxX);
        if (tx >  maxX) previewImage.setTranslateX(maxX);
        if (ty < -maxY) previewImage.setTranslateY(-maxY);
        if (ty >  maxY) previewImage.setTranslateY(maxY);
    }

    private Image createDefaultAvatarImage() {
    try {
        File file = new File("avatarMacDinh.jpg");

        if (file.exists()) {
            // Tai anh tu file local.
            Image img = new Image(file.toURI().toString(), false);
            if (!img.isError()) {
                return img;
            }
        }
    } catch (Exception e) {
        e.printStackTrace();
    }

    // Avatar online dung khi chua co avatar rieng.
    try {
        Image img = new Image("https://i.pravatar.cc/300?img=0", false);

        if (!img.isError()) {
            return img;
        }
    } catch (Exception ignored) {
    }

        // Fallback cuoi cung la avatar mau.
        javafx.scene.canvas.Canvas canvas =
                new javafx.scene.canvas.Canvas(110, 110);
    
        javafx.scene.canvas.GraphicsContext gc =
                canvas.getGraphicsContext2D();
    
        gc.setFill(javafx.scene.paint.Color.web(ACCENT));
        gc.fillOval(0, 0, 110, 110);
    
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
    
        return canvas.snapshot(params, null);
    }

    private byte[] imageToPngBytes(Image image) {
        try {
            java.awt.image.BufferedImage bImage =
                    SwingFXUtils.fromFXImage(image, null);
    
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
    
            ImageIO.write(bImage, "png", baos);
    
            return baos.toByteArray();
    
        } catch (Exception e) {
            e.printStackTrace();
            return new byte[0];
        }
    }

    private ChatTcpClient.ApiResponse uploadAvatarViaTcp(Image image) {
        byte[] pngBytes = imageToPngBytes(image);
        if (pngBytes.length == 0) {
            return new ChatTcpClient.ApiResponse(400, "error", "Không thể đọc dữ liệu ảnh", null, null, "");
        }

        String avatarDataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(pngBytes);
        return tcpClient.changeAvatar(currentUserId, avatarDataUrl);
    }

    private void showAvatarToast(String text, String bgColor) {
        // Tao thong bao nho de bao ket qua thao tac avatar.
        Label toast = new Label(text);
        toast.setStyle("""
                -fx-background-color: %s;
                -fx-text-fill: white;
                -fx-font-size: 14px;
                -fx-font-weight: bold;
                -fx-padding: 14 22;
                -fx-background-radius: 14px;
                """.formatted(bgColor));

        // Dat toast trong Stage tam de khong pha layout chinh.
        Stage toastStage = new Stage();
        toastStage.initOwner(stage);
        toastStage.setAlwaysOnTop(true);
        StackPane toastRoot = new StackPane(toast);
        toastRoot.setStyle("-fx-background-color: transparent;");
        toastRoot.setPadding(new Insets(10));
        Scene toastScene = new Scene(toastRoot);
        toastScene.setFill(Color.TRANSPARENT);
        toastStage.initStyle(javafx.stage.StageStyle.TRANSPARENT);
        toastStage.setScene(toastScene);
        toastStage.show();

        // Tu dong sau 2.5 giay.
        scheduler.schedule(
            () -> Platform.runLater(toastStage::close),
            2500,
            TimeUnit.MILLISECONDS
        );
    }
}
