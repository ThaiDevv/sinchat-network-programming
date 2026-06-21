package com.client.view;

import com.client.controller.ChatController;
import com.client.emoji.EmojiManager;
import com.client.service.ChatService;
import com.client.util.ImageUtils;
import com.client.util.StyleConstants;
import com.client.util.TimeUtils;
import com.google.gson.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.scene.text.TextFlow;
import javafx.stage.Modality;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.concurrent.*;

/**
 * Main chat view — composes left panel (contacts), center panel (messages),
 * and right panel (profile). All TCP business logic is delegated to
 * {@link ChatController}. Dialogs and modals are extracted into separate classes.
 */
public class ChatView {

    private final BorderPane root;
    private final Stage stage;
    private final ChatController controller;
    private final long currentUserId;
    private final Gson gson = new Gson();

    // --- UI fields ---
    private VBox messagesBox;
    private VBox contactList;
    private TextField messageInput;
    private ScrollPane scrollMessages;
    private Label headerChatName;
    private Label chatStatus;
    private Label nameLabel;
    private Label typingLabel;
    private Label loadingIndicator;
    private Button leaveGroupBtn;

    // Message search fields
    private TextField messageSearchField;
    private VBox messageSearchPanel;
    private VBox messageSearchResults;
    private ScrollPane messageSearchResultsScroll;
    private Label messageSearchStatus;
    private HBox messageSearchNavigator;
    private Label messageSearchCounter;
    private Button messageSearchButton;
    private Button messageSearchPrevBtn, messageSearchNextBtn;

    // Avatar
    private Circle profileAvatarCircle;
    private Image currentAvatarImage;
    private Image initialAvatarImage;
    private final List<Image> previouslyUsedAvatars = new ArrayList<>();

    // --- State maps ---
    private final Map<Long, Label> contactLastMsgLabels = new HashMap<>();
    private final Map<Long, Circle> statusDotsByPeerId = new HashMap<>();
    private final Map<Long, Long> conversationIdByPeerId = new HashMap<>();
    private final Map<Long, Long> peerIdByConversationId = new HashMap<>();
    private final Map<Long, String> peerLastSeenByPeerId = new HashMap<>();
    private final Map<Long, Label> messageStatusLabels = new HashMap<>();
    private final Map<Long, Boolean> peerOnlineByPeerId = new HashMap<>();
    private final Map<Long, Node> messageBubbleById = new HashMap<>();
    private final Map<Long, Integer> unreadCounts = new HashMap<>();
    private final Map<Long, String> conversationDisplayNames = new HashMap<>();
    private final Map<Long, Label> unreadBadgesByConvId = new HashMap<>();
    private final List<Stage> activeNotificationStages = new ArrayList<>();
    private final Map<Long, List<Circle>> peerAvatarCircles = new ConcurrentHashMap<>();
    private final Map<Long, Image> peerAvatarCache = new ConcurrentHashMap<>();
    private final Map<Long, HBox> messageSeenContainers = new HashMap<>();
    private final Map<Long, List<ReaderInfo>> messageSeenUsers = new HashMap<>();

    // Message search state
    private final List<JsonObject> messageSearchMatches = new ArrayList<>();
    private final List<VBox> messageSearchItems = new ArrayList<>();
    private Node highlightedMessageBubble;
    private String highlightedMessageStyle;
    private int activeMessageSearchIndex = -1;
    private String activeMessageSearchKeyword = "";

    // Message reply state
    private HBox replyPreviewBar;
    private Label replyPreviewUserLabel;
    private Label replyPreviewContentLabel;
    private Long activeReplyToId = null;

    // Pagination
    private long currentConversationId = -1;
    private int currentMessageOffset = 0;
    private boolean hasMoreMessages = true;
    private boolean isLoadingMore = false;
    private static final int PAGE_SIZE = 20;
    private static final int MAX_MESSAGE_SEARCH_KEYWORD_LENGTH = 100;
    private boolean pendingScrollToBottom = false;

    // Typing
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "typing-scheduler");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> typingHideTask;
    private ScheduledFuture<?> presenceRefreshTask;
    private volatile String currentHeaderLastSeen;
    private long lastTypingSentTime = 0;
    private static final long TYPING_THROTTLE_MS = 1000;

    // Scroll listener for infinite scroll
    private final javafx.beans.value.ChangeListener<Number> scrollListener = (obs, oldVal, newVal) -> {
        if (pendingScrollToBottom) return;
        if (newVal.doubleValue() < 0.05 && hasMoreMessages && !isLoadingMore && currentConversationId > 0) {
            loadMessagesForCurrentConversation(false);
        }
    };

    // ==================== CONSTRUCTOR ====================

    public ChatView(Stage stage, long currentUserId) {
        this.stage = stage;
        this.currentUserId = currentUserId;
        this.controller = new ChatController(currentUserId);

        // Seed default avatars
        String[] defaultOldAvatars = {
                "https://i.pravatar.cc/300?img=1", "https://i.pravatar.cc/300?img=2",
                "https://i.pravatar.cc/300?img=3", "https://i.pravatar.cc/300?img=4",
                "https://i.pravatar.cc/300?img=5"
        };
        for (String url : defaultOldAvatars) {
            previouslyUsedAvatars.add(new Image(url, true));
        }

        root = new BorderPane();
        root.setStyle("-fx-background-color: " + StyleConstants.BG_BLACK + ";");

        root.setLeft(createLeftPanel());
        root.setCenter(createCenterPanel());
        root.setRight(createRightPanel());

        setupTcpCallbacks();
        loadConversations();
    }

    public ChatView(Stage stage) {
        this(stage, 0);
    }

    // ==================== TCP CALLBACKS ====================

    private void setupTcpCallbacks() {
        ChatService tcp = controller.getChatService();

        tcp.setOnNewMessage(this::onNewMessageReceived);
        tcp.setOnUserTyping(this::onUserTyping);
        tcp.setOnUserStatusChange(this::onUserStatusChange);
        tcp.setOnUserAvatarChanged(this::onUserAvatarChanged);
        tcp.setOnMessageStatusChanged(this::onMessageStatusChanged);
        tcp.setOnLeftGroup(this::onLeftGroupReceived);
        tcp.setOnConnected(() -> System.out.println("TCP socket connected for user " + currentUserId));
        tcp.setOnDisconnected(reason -> System.out.println("TCP socket disconnected: " + reason));

        CompletableFuture.runAsync(() -> {
            tcp.join(currentUserId);
            loadUserAvatar();
        });
    }

    // ==================== EVENT HANDLERS ====================

    private void onNewMessageReceived(JsonObject json) {
        long conversationId = json.get("conversationId").getAsLong();
        long senderId = json.get("senderId").getAsLong();
        String content = json.get("content").getAsString();
        long messageId = json.has("messageId") ? json.get("messageId").getAsLong() : -1;
        String senderUsername = json.has("senderUsername") && !json.get("senderUsername").isJsonNull()
                ? json.get("senderUsername").getAsString() : "Unknown";

        Long replyToId = json.has("replyToId") && !json.get("replyToId").isJsonNull()
                ? json.get("replyToId").getAsLong() : null;
        String replyToUsername = json.has("replyToUsername") && !json.get("replyToUsername").isJsonNull()
                ? json.get("replyToUsername").getAsString() : null;
        String replyToContent = json.has("replyToContent") && !json.get("replyToContent").isJsonNull()
                ? json.get("replyToContent").getAsString() : null;

        if (conversationId == currentConversationId) {
            if (typingLabel != null) typingLabel.setVisible(false);

            if (senderId == currentUserId) {
                String status = json.has("messageStatus") ? json.get("messageStatus").getAsString() : "SENT";
                addSentMessage(content, messageId, status, replyToId, replyToUsername, replyToContent);
            } else {
                addReceivedMessage(senderId, senderUsername, content, messageId, replyToId, replyToUsername, replyToContent);
                if (messageId > 0) controller.markMessageSeen(currentConversationId, messageId);
            }
            scrollToBottom();
        } else {
            unreadCounts.merge(conversationId, 1, Integer::sum);
            updateUnreadBadge(conversationId);
            String senderName = json.has("senderUsername") ? json.get("senderUsername").getAsString()
                    : conversationDisplayNames.getOrDefault(conversationId, "Ai đó");
            showNewMessageNotification(senderName, content, conversationId);
        }
        Platform.runLater(this::loadConversations);
    }

    private void onLeftGroupReceived(JsonObject json) {
        long conversationId = json.get("conversationId").getAsLong();
        long leftUserId = json.get("userId").getAsLong();

        if (leftUserId == currentUserId) {
            if (currentConversationId == conversationId) {
                Platform.runLater(() -> {
                    currentConversationId = -1;
                    messagesBox.getChildren().clear();
                    if (headerChatName != null) headerChatName.setText("Chọn người để chat");
                    if (chatStatus != null) chatStatus.setText("Offline");
                    if (leaveGroupBtn != null) {
                        leaveGroupBtn.setVisible(false);
                        leaveGroupBtn.setManaged(false);
                    }
                });
            }
        }
        Platform.runLater(this::loadConversations);
    }

    private void onMessageStatusChanged(JsonObject json) {
        long conversationId = json.has("conversationId") ? json.get("conversationId").getAsLong() : -1;
        if (conversationId != currentConversationId) return;

        if (json.has("messageId")) {
            long messageId = json.get("messageId").getAsLong();
            String status = json.get("status").getAsString();

            if ("SEEN".equals(status) && json.has("username")) {
                long readerId = json.has("userId") ? json.get("userId").getAsLong() : -1;
                String readerUsername = json.get("username").getAsString();
                if (readerId > 0) {
                    Platform.runLater(() -> {
                        List<ReaderInfo> readers = messageSeenUsers.computeIfAbsent(messageId, k -> new ArrayList<>());
                        ReaderInfo newReader = new ReaderInfo(readerId, readerUsername);
                        if (!readers.contains(newReader)) {
                            readers.add(newReader);
                            updateSeenAvatars(messageId, readers);
                        }
                    });
                }
            }

            Platform.runLater(() -> {
                Label label = messageStatusLabels.get(messageId);
                if (label != null) {
                    label.setText(ChatController.getStatusLabelText(status));
                    // Only show status if this is the last sent message
                    if (isLastMessageMine() && isLastSentMessage(messageId)) {
                        label.setVisible(true);
                    }
                }
            });
        } else if (json.has("status") && "SEEN".equals(json.get("status").getAsString())) {
            if (json.has("username")) {
                long readerId = json.has("userId") ? json.get("userId").getAsLong() : -1;
                String readerUsername = json.get("username").getAsString();
                if (readerId > 0) {
                    Platform.runLater(() -> {
                        for (Map.Entry<Long, HBox> entry : messageSeenContainers.entrySet()) {
                            long mId = entry.getKey();
                            List<ReaderInfo> readers = messageSeenUsers.computeIfAbsent(mId, k -> new ArrayList<>());
                            ReaderInfo newReader = new ReaderInfo(readerId, readerUsername);
                            if (!readers.contains(newReader)) {
                                readers.add(newReader);
                                updateSeenAvatars(mId, readers);
                            }
                        }
                    });
                }
            }

            Platform.runLater(() -> {
                if (!messageStatusLabels.isEmpty()) {
                    // Update all visible sent message labels to "Read"
                    long maxId = messageStatusLabels.keySet().stream().max(Long::compare).orElse(-1L);
                    if (maxId > 0) {
                        Label lastLabel = messageStatusLabels.get(maxId);
                        if (lastLabel != null) {
                            lastLabel.setText("Read");
                            // Show the status if the last message overall is mine
                            if (isLastMessageMine()) {
                                lastLabel.setVisible(true);
                            }
                        }
                    }
                }
            });
        }
    }

    /** Returns true if the given messageId belongs to the last sent (right-aligned) message bubble. */
    private boolean isLastSentMessage(long messageId) {
        if (messageStatusLabels.isEmpty()) return false;
        long maxId = messageStatusLabels.keySet().stream().max(Long::compare).orElse(-1L);
        return messageId == maxId;
    }

    private void onUserTyping(JsonObject json) {
        long conversationId = json.get("conversationId").getAsLong();
        if (conversationId != currentConversationId || typingLabel == null) return;

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

        if (typingHideTask != null && !typingHideTask.isDone()) typingHideTask.cancel(false);
        typingHideTask = scheduler.schedule(() ->
                Platform.runLater(() -> typingLabel.setVisible(false)), 3, TimeUnit.SECONDS);
    }

    private void onUserStatusChange(JsonObject json) {
        if (!json.has("userId") || !json.has("status")) return;
        long peerId = json.get("userId").getAsLong();
        boolean isOnline = "online".equals(json.get("status").getAsString());
        String lastSeenStr = json.has("lastSeen") ? json.get("lastSeen").getAsString() : null;
        peerOnlineByPeerId.put(peerId, isOnline);
        if (lastSeenStr != null) peerLastSeenByPeerId.put(peerId, lastSeenStr);

        Platform.runLater(() -> {
            Circle dot = statusDotsByPeerId.get(peerId);
            if (dot != null) dot.setFill(Color.web(isOnline ? "#4ade80" : "#888888"));

            Long convId = conversationIdByPeerId.get(peerId);
            if (convId != null && convId == currentConversationId) {
                updateHeaderPresence(isOnline, isOnline ? null : lastSeenStr);
            }
        });
    }

    private void onUserAvatarChanged(JsonObject json) {
        if (!json.has("userId") || !json.has("avatarUrl")) return;
        long uId = json.get("userId").getAsLong();
        String dataUrl = json.get("avatarUrl").getAsString();

        Image img = decodeAvatarDataUrl(dataUrl);
        if (img != null) {
            if (img.getProgress() >= 1.0) {
                Platform.runLater(() -> updateAvatarCircles(uId, img));
            } else {
                img.progressProperty().addListener((obs, oldVal, newVal) -> {
                    if (newVal.doubleValue() >= 1.0 && !img.isError())
                        Platform.runLater(() -> updateAvatarCircles(uId, img));
                });
            }
        }
    }

    // ==================== AVATAR HELPERS ====================

    private Image decodeAvatarDataUrl(String dataUrl) {
        try {
            if (dataUrl.startsWith("data:image/")) {
                String base64 = dataUrl.substring(dataUrl.indexOf(",") + 1);
                byte[] imgBytes = Base64.getDecoder().decode(base64);
                return new Image(new ByteArrayInputStream(imgBytes));
            }
            return new Image(dataUrl, true);
        } catch (Exception e) {
            System.err.println("Failed to decode avatar: " + e.getMessage());
            return null;
        }
    }

    private void updateAvatarCircles(long uId, Image img) {
        peerAvatarCache.put(uId, img);
        if (peerAvatarCircles.containsKey(uId)) {
            for (Circle circle : peerAvatarCircles.get(uId)) {
                circle.setFill(new ImagePattern(img));
            }
        }
    }

    private void loadUserAvatar() {
        controller.loadUserProfile(profile -> {
            if (profile.has("username") && !profile.get("username").isJsonNull()) {
                String username = profile.get("username").getAsString();
                if (username != null && !username.isEmpty() && nameLabel != null) {
                    Platform.runLater(() -> nameLabel.setText(username));
                }
            }
            if (profile.has("avatar_url") && !profile.get("avatar_url").isJsonNull()) {
                String avatarUrl = profile.get("avatar_url").getAsString();
                if (avatarUrl != null && !avatarUrl.isEmpty()
                        && !avatarUrl.equals("uploads/avatars/avatar_default.png")) {
                    processProfileAvatarUrl(avatarUrl);
                }
            }
        });
    }

    private void processProfileAvatarUrl(String avatarUrl) {
        if (avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://") || avatarUrl.startsWith("file://")) {
            Image newAvatar = new Image(avatarUrl, true);
            newAvatar.progressProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal.doubleValue() >= 1.0 && !newAvatar.isError()) {
                    Platform.runLater(() -> {
                        currentAvatarImage = newAvatar;
                        if (profileAvatarCircle != null) profileAvatarCircle.setFill(new ImagePattern(currentAvatarImage));
                    });
                }
            });
        } else {
            controller.loadPeerAvatar(currentUserId,
                    dataUrl -> {
                        Image img = decodeAvatarDataUrl(dataUrl);
                        if (img != null) {
                            Platform.runLater(() -> {
                                currentAvatarImage = img;
                                if (profileAvatarCircle != null) profileAvatarCircle.setFill(new ImagePattern(currentAvatarImage));
                            });
                        }
                    },
                    null);
        }
    }

    private void loadPeerAvatar(long peerId, Circle targetCircle) {
        if (peerAvatarCache.containsKey(peerId)) {
            targetCircle.setFill(new ImagePattern(peerAvatarCache.get(peerId)));
            return;
        }
        controller.loadPeerAvatar(peerId,
                dataUrl -> {
                    Image img = decodeAvatarDataUrl(dataUrl);
                    if (img != null) {
                        updateAvatarCircles(peerId, img);
                    }
                },
                () -> {
                    Image defaultImg = ImageUtils.createDefaultAvatarImage();
                    updateAvatarCircles(peerId, defaultImg);
                });
    }

    // ==================== CONVERSATION MANAGEMENT ====================

    public void setCurrentConversation(long conversationId, String name) {
        this.currentConversationId = conversationId;
        unreadCounts.remove(conversationId);
        updateUnreadBadge(conversationId);
        messagesBox.getChildren().clear();
        messageBubbleById.clear();
        messageSeenContainers.clear();
        messageSeenUsers.clear();
        highlightedMessageBubble = null;
        highlightedMessageStyle = null;
        clearMessageSearchResults();
        currentMessageOffset = 0;
        hasMoreMessages = true;
        isLoadingMore = false;
        hideLoadingIndicator();

        if (headerChatName != null) headerChatName.setText(name);

        Long peerId = peerIdByConversationId.get(conversationId);
        if (peerId != null) {
            Boolean online = peerOnlineByPeerId.get(peerId);
            String lastSeen = peerLastSeenByPeerId.get(peerId);
            updateHeaderPresence(online != null ? online : false, online == null || !online ? lastSeen : null);

            // Update header avatar
            if (headerChatName != null && headerChatName.getParent() != null
                    && headerChatName.getParent().getParent() instanceof HBox header) {
                if (!header.getChildren().isEmpty() && header.getChildren().get(0) instanceof Circle headerAvatar) {
                    headerAvatar.setFill(Color.web("#444"));
                    peerAvatarCircles.computeIfAbsent(peerId, k -> new ArrayList<>()).add(headerAvatar);
                    loadPeerAvatar(peerId, headerAvatar);
                }
            }
            if (leaveGroupBtn != null) {
                leaveGroupBtn.setVisible(false);
                leaveGroupBtn.setManaged(false);
            }
        } else {
            // Group conversation — show "Members" in status and a group icon colour
            if (chatStatus != null) {
                chatStatus.setText("Nhóm chat");
                chatStatus.setStyle("-fx-font-size: 12px; -fx-text-fill: " + StyleConstants.ACCENT + ";");
            }
            if (headerChatName != null && headerChatName.getParent() != null
                    && headerChatName.getParent().getParent() instanceof HBox header) {
                if (!header.getChildren().isEmpty() && header.getChildren().get(0) instanceof Circle headerAvatar) {
                    headerAvatar.setFill(Color.web("#2d2250"));
                }
            }
            if (leaveGroupBtn != null && conversationId > 0) {
                leaveGroupBtn.setVisible(true);
                leaveGroupBtn.setManaged(true);
            }
        }

        loadConversations();
        loadMessagesForCurrentConversation(true);

        // Mark all messages in this conversation as SEEN for the current user
        // This triggers the server to broadcast MESSAGE_STATUS_EVENT to the sender
        controller.markAllMessagesSeen(conversationId);
    }

    private void loadConversations() {
        statusDotsByPeerId.clear();
        conversationIdByPeerId.clear();
        peerIdByConversationId.clear();

        controller.loadConversations(
                data -> {
                    contactList.getChildren().clear();
                    contactLastMsgLabels.clear();
                    boolean activeConvStillExists = false;
                    for (JsonElement element : data) {
                        JsonObject conv = element.getAsJsonObject();
                        long id = conv.get("conversationId").getAsLong();
                        String name = conv.get("displayName").getAsString();
                        String lastMsg = conv.has("lastMessage") && !conv.get("lastMessage").isJsonNull()
                                ? conv.get("lastMessage").getAsString() : "";
                        boolean isSelected = (id == currentConversationId);
                        if (isSelected) {
                            activeConvStillExists = true;
                            boolean online = conv.has("isOnline") && conv.get("isOnline").getAsBoolean();
                            String lastSeen = conv.has("lastSeen") && !conv.get("lastSeen").isJsonNull()
                                    ? conv.get("lastSeen").getAsString() : null;
                            updateHeaderPresence(online, lastSeen);
                        }
                        addContactWithPresence(id, name, lastMsg, isSelected, conv);
                    }
                    if (!activeConvStillExists && chatStatus != null) {
                        updateHeaderPresence(false, null);
                    }
                }, null);
    }

    private void addContactWithPresence(long conversationId, String name, String lastMsg,
                                         boolean selected, JsonObject conv) {
        conversationDisplayNames.put(conversationId, name);
        HBox contact = new HBox(12);
        contact.setAlignment(Pos.CENTER_LEFT);
        contact.setPadding(new Insets(12, 14, 12, 14));

        String radius = "16px";
        contact.setStyle(StyleConstants.contactItemStyle(selected ? StyleConstants.ACCENT : "transparent", radius));

        StackPane avatarContainer = new StackPane();
        Circle avatar = new Circle(22);
        avatar.setFill(Color.web("#444"));
        avatar.setStroke(Color.web(StyleConstants.BORDER_COLOR));
        avatarContainer.getChildren().add(avatar);

        boolean isGroup = conv.has("type") && "GROUP".equals(conv.get("type").getAsString());

        if (!isGroup && conv.has("peerId")) {
            long peerId = conv.get("peerId").getAsLong();
            conversationIdByPeerId.put(peerId, conversationId);
            peerIdByConversationId.put(conversationId, peerId);
            if (conv.has("isOnline")) peerOnlineByPeerId.put(peerId, conv.get("isOnline").getAsBoolean());
            if (conv.has("lastSeen") && !conv.get("lastSeen").isJsonNull())
                peerLastSeenByPeerId.put(peerId, conv.get("lastSeen").getAsString());

            peerAvatarCircles.computeIfAbsent(peerId, k -> new ArrayList<>()).add(avatar);
            loadPeerAvatar(peerId, avatar);
        }

        if (isGroup) {
            // Group icon — show a group emoji label centered
            Label groupIcon = new Label("👥");
            groupIcon.setStyle("-fx-font-size: 16px;");
            avatarContainer.getChildren().add(groupIcon);
            avatar.setFill(Color.web("#2d2250"));
        } else if (conv.has("isOnline")) {
            boolean online = conv.get("isOnline").getAsBoolean();
            Circle statusDot = new Circle(6);
            statusDot.setFill(Color.web(online ? "#4ade80" : "#888888"));
            statusDot.setStroke(Color.web(StyleConstants.BG_BLACK));
            statusDot.setStrokeWidth(1.5);
            StackPane.setAlignment(statusDot, Pos.BOTTOM_RIGHT);
            avatarContainer.getChildren().add(statusDot);
            if (conv.has("peerId")) statusDotsByPeerId.put(conv.get("peerId").getAsLong(), statusDot);
        }

        VBox info = new VBox(3);
        Label nameLabel = new Label(name);
        nameLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: " + StyleConstants.TEXT_WHITE + ";");

        String formattedLastMsg = lastMsg;
        if (lastMsg != null && !lastMsg.isEmpty() && conv.has("lastMessageSenderId")) {
            long senderId = conv.get("lastMessageSenderId").getAsLong();
            if (senderId != currentUserId) formattedLastMsg = name + ": " + lastMsg;
        }

        Label msgLabel = new Label(formattedLastMsg);
        msgLabel.setMaxWidth(160);
        msgLabel.setWrapText(true);
        msgLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: " + (selected ? "#dddddd" : StyleConstants.TEXT_MUTED) + ";");
        contactLastMsgLabels.put(conversationId, msgLabel);

        // Unread badge
        int unread = unreadCounts.getOrDefault(conversationId, 0);
        Label badge = new Label(unread > 99 ? "99+" : unread > 0 ? String.valueOf(unread) : "");
        badge.setVisible(unread > 0);
        badge.setManaged(unread > 0);
        badge.setStyle("-fx-background-color: #ff4444; -fx-text-fill: white; -fx-font-size: 10px; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-padding: 1px 5px;");
        StackPane.setAlignment(badge, Pos.TOP_RIGHT);
        StackPane.setMargin(badge, new Insets(-5, -5, 0, 0));
        avatarContainer.getChildren().add(badge);
        unreadBadgesByConvId.put(conversationId, badge);

        if (unread > 0 && !selected) {
            msgLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: " + StyleConstants.TEXT_WHITE + "; -fx-font-weight: bold;");
        }

        info.getChildren().addAll(nameLabel, msgLabel);
        contact.getChildren().addAll(avatarContainer, info);

        if (!selected) {
            contact.setOnMouseEntered(e -> contact.setStyle(StyleConstants.contactItemHoverStyle(radius)));
            contact.setOnMouseExited(e -> contact.setStyle(StyleConstants.contactItemNormalStyle(radius)));
        }
        contact.setOnMouseClicked(e -> setCurrentConversation(conversationId, name));
        contactList.getChildren().add(contact);
    }

    // ==================== HEADER PRESENCE ====================

    private void updateHeaderPresence(boolean isOnline, String lastSeen) {
        currentHeaderLastSeen = lastSeen;
        if (presenceRefreshTask != null && !presenceRefreshTask.isDone()) {
            presenceRefreshTask.cancel(false);
            presenceRefreshTask = null;
        }
        if (!isOnline && lastSeen != null && !lastSeen.isEmpty()) {
            presenceRefreshTask = scheduler.scheduleAtFixedRate(() ->
                    Platform.runLater(() -> renderHeaderPresence(false, currentHeaderLastSeen)),
                    60, 60, TimeUnit.SECONDS);
        }
        renderHeaderPresence(isOnline, lastSeen);
    }

    private void renderHeaderPresence(boolean isOnline, String lastSeen) {
        if (chatStatus == null) return;
        if (isOnline) {
            chatStatus.setText("Online");
            chatStatus.setStyle("-fx-font-size: 12px; -fx-text-fill: #4ade80;");
        } else {
            String text = (lastSeen != null && !lastSeen.isEmpty())
                    ? TimeUtils.formatRelativePresence(lastSeen) : "Offline";
            chatStatus.setText(text);
            chatStatus.setStyle("-fx-font-size: 12px; -fx-text-fill: #888888;");
        }
    }

    // ==================== MESSAGE LOADING ====================

    private void loadMessagesForCurrentConversation(boolean reset) {
        if (currentConversationId <= 0 || isLoadingMore) return;
        if (!reset && !hasMoreMessages) return;

        isLoadingMore = true;
        if (reset) {
            currentMessageOffset = 0;
            hasMoreMessages = true;
        } else {
            Platform.runLater(() -> {
                if (loadingIndicator != null) {
                    loadingIndicator.setVisible(true);
                    if (!messagesBox.getChildren().contains(loadingIndicator))
                        messagesBox.getChildren().add(0, loadingIndicator);
                }
            });
        }

        long capturedConvId = currentConversationId;
        int offset = currentMessageOffset;

        controller.loadMessages(capturedConvId, PAGE_SIZE, offset, json -> {
            if (currentConversationId != capturedConvId) { isLoadingMore = false; hideLoadingIndicator(); return; }
            if (reset) messageStatusLabels.clear();
            renderMessagesPage(json, reset, capturedConvId, offset);
            if (reset) scrollToBottom();
            isLoadingMore = false;
            hideLoadingIndicator();
        });
    }

    private void renderMessagesPage(JsonObject json, boolean reset, long capturedConvId, int offset) {
        if (currentConversationId != capturedConvId) return;

        JsonArray messages = json.getAsJsonArray("messages");
        if (reset) {
            messagesBox.getChildren().clear();
            messageBubbleById.clear();
            messageStatusLabels.clear();
            highlightedMessageBubble = null;
            highlightedMessageStyle = null;
        }

        List<JsonElement> msgList = new ArrayList<>();
        for (JsonElement e : messages) msgList.add(e);
        Collections.reverse(msgList);

        int insertIndex = 0;
        for (JsonElement element : msgList) {
            JsonObject msg = element.getAsJsonObject();
            long messageId = msg.has("id") ? msg.get("id").getAsLong() : -1;
            long senderId = msg.get("senderId").getAsLong();
            String content = msg.get("content").getAsString();
            String status = msg.has("status") ? msg.get("status").getAsString() : "SENT";

            List<ReaderInfo> seenUsers = new ArrayList<>();
            if (msg.has("seenByUsers") && msg.get("seenByUsers").isJsonArray()) {
                JsonArray seenArr = msg.getAsJsonArray("seenByUsers");
                for (int i = 0; i < seenArr.size(); i++) {
                    if (seenArr.get(i).isJsonObject()) {
                        JsonObject uObj = seenArr.get(i).getAsJsonObject();
                        long uId = uObj.get("userId").getAsLong();
                        String uName = uObj.get("username").getAsString();
                        seenUsers.add(new ReaderInfo(uId, uName));
                    }
                }
            }
            if (messageId > 0) {
                messageSeenUsers.put(messageId, seenUsers);
            }

            Long replyToId = msg.has("replyToId") && !msg.get("replyToId").isJsonNull()
                    ? msg.get("replyToId").getAsLong() : null;
            String replyToUsername = msg.has("replyToUsername") && !msg.get("replyToUsername").isJsonNull()
                    ? msg.get("replyToUsername").getAsString() : null;
            String replyToContent = msg.has("replyToContent") && !msg.get("replyToContent").isJsonNull()
                    ? msg.get("replyToContent").getAsString() : null;

            boolean isMine = (senderId == currentUserId);
            HBox seenContainer = createSeenContainer(isMine);
            if (messageId > 0) {
                messageSeenContainers.put(messageId, seenContainer);
            }

            HBox wrapper;
            if (isMine) {
                wrapper = new HBox();
                wrapper.setAlignment(Pos.CENTER_RIGHT);
                VBox container = new VBox(2);
                container.setAlignment(Pos.BOTTOM_RIGHT);

                Node bubble = createMessageBubble(content, StyleConstants.ACCENT, "18px 18px 4px 18px");
                if (messageId > 0) messageBubbleById.put(messageId, bubble);
                addContextMenuToBubble(bubble, messageId, "Bạn", content);

                VBox bubbleGroup = new VBox(4);
                bubbleGroup.setAlignment(Pos.TOP_RIGHT);
                if (replyToId != null) {
                    VBox quoteBox = createQuoteBox(replyToId, replyToUsername, replyToContent, true);
                    quoteBox.setMaxWidth(360);
                    bubbleGroup.getChildren().add(quoteBox);
                }
                bubbleGroup.getChildren().add(bubble);

                Label statusLabel = new Label(ChatController.getStatusLabelText(status));
                statusLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #888888; -fx-padding: 0 4px 0 0;");
                if (messageId > 0) messageStatusLabels.put(messageId, statusLabel);

                container.getChildren().addAll(bubbleGroup, statusLabel, seenContainer);
                wrapper.getChildren().add(container);
            } else {
                String senderUsername = msg.has("senderUsername") && !msg.get("senderUsername").isJsonNull()
                        ? msg.get("senderUsername").getAsString() : "Unknown";
                wrapper = createMessageWrapper(senderId, senderUsername, content, messageId, seenContainer, replyToId, replyToUsername, replyToContent);
            }
            messagesBox.getChildren().add(insertIndex++, wrapper);

            if (messageId > 0) {
                updateSeenAvatars(messageId, seenUsers);
            }
        }

        int msgCount = messages.size();
        currentMessageOffset = offset + msgCount;
        hasMoreMessages = json.has("hasMore") ? json.get("hasMore").getAsBoolean() : msgCount >= PAGE_SIZE;
        hideAllStatusLabelsExceptLast();
    }

    private void hideAllStatusLabelsExceptLast() {
        if (messageStatusLabels.isEmpty()) return;

        // Check if the VERY LAST message in the conversation is mine
        boolean lastMessageIsMine = isLastMessageMine();

        if (!lastMessageIsMine) {
            // Last message is from the other party — hide all status labels
            for (Label label : messageStatusLabels.values()) label.setVisible(false);
            return;
        }

        // Last message is mine — find and show only the last sent message's status
        long lastSentId = -1;
        for (int i = messagesBox.getChildren().size() - 1; i >= 0; i--) {
            if (messagesBox.getChildren().get(i) instanceof HBox wrapper
                    && wrapper.getAlignment() == Pos.CENTER_RIGHT && !wrapper.getChildren().isEmpty()) {
                if (wrapper.getChildren().get(0) instanceof VBox container && container.getChildren().size() >= 2) {
                    if (container.getChildren().get(container.getChildren().size() - 1) instanceof Label sl
                            && messageStatusLabels.containsValue(sl)) {
                        for (var e : messageStatusLabels.entrySet()) {
                            if (e.getValue() == sl) { lastSentId = e.getKey(); break; }
                        }
                        break;
                    }
                }
            }
        }
        for (var e : messageStatusLabels.entrySet()) e.getValue().setVisible(e.getKey() == lastSentId);
    }

    /** Returns true if the last message bubble in the chat is right-aligned (sent by me). */
    private boolean isLastMessageMine() {
        for (int i = messagesBox.getChildren().size() - 1; i >= 0; i--) {
            if (messagesBox.getChildren().get(i) instanceof HBox wrapper) {
                return wrapper.getAlignment() == Pos.CENTER_RIGHT;
            }
        }
        return false;
    }

    // ==================== MESSAGE BUBBLES ====================

    private HBox createMessageWrapper(long senderId, String senderUsername, String text, long messageId, HBox seenContainer) {
        return createMessageWrapper(senderId, senderUsername, text, messageId, seenContainer, null, null, null);
    }

    private HBox createMessageWrapper(long senderId, String senderUsername, String text, long messageId, HBox seenContainer, Long replyToId, String replyToUsername, String replyToContent) {
        HBox wrapper = new HBox(8);
        wrapper.setAlignment(senderId == currentUserId ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        if (senderId == currentUserId) {
            String bg = StyleConstants.ACCENT;
            String radius = "18px 18px 4px 18px";
            Node bubble = createMessageBubble(text, bg, radius);
            if (messageId > 0) messageBubbleById.put(messageId, bubble);
            addContextMenuToBubble(bubble, messageId, "Bạn", text);

            VBox bubbleGroup = new VBox(4);
            bubbleGroup.setAlignment(Pos.TOP_RIGHT);
            if (replyToId != null) {
                VBox quoteBox = createQuoteBox(replyToId, replyToUsername, replyToContent, true);
                quoteBox.setMaxWidth(360);
                bubbleGroup.getChildren().add(quoteBox);
            }
            bubbleGroup.getChildren().add(bubble);

            wrapper.getChildren().add(bubbleGroup);
        } else {
            Circle avatar = new Circle(16);
            avatar.setFill(Color.web("#444"));
            peerAvatarCircles.computeIfAbsent(senderId, k -> new ArrayList<>()).add(avatar);
            loadPeerAvatar(senderId, avatar);

            VBox container = new VBox(2);
            container.setAlignment(Pos.TOP_LEFT);

            Label nameLbl = new Label(senderUsername);
            nameLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: " + StyleConstants.TEXT_MUTED + "; -fx-font-weight: bold; -fx-padding: 0 0 2px 4px;");

            String bg = "#1e1e1e";
            String radius = "18px 18px 18px 4px";
            Node bubble = createMessageBubble(text, bg, radius);
            if (messageId > 0) messageBubbleById.put(messageId, bubble);
            addContextMenuToBubble(bubble, messageId, senderUsername, text);

            VBox bubbleGroup = new VBox(4);
            bubbleGroup.setAlignment(Pos.TOP_LEFT);
            if (replyToId != null) {
                VBox quoteBox = createQuoteBox(replyToId, replyToUsername, replyToContent, false);
                quoteBox.setMaxWidth(360);
                bubbleGroup.getChildren().add(quoteBox);
            }
            bubbleGroup.getChildren().add(bubble);

            container.getChildren().addAll(nameLbl, bubbleGroup, seenContainer);
            wrapper.getChildren().addAll(avatar, container);
        }
        return wrapper;
    }

    private void addReceivedMessage(long senderId, String senderUsername, String text, long messageId) {
        addReceivedMessage(senderId, senderUsername, text, messageId, null, null, null);
    }

    private void addReceivedMessage(long senderId, String senderUsername, String text, long messageId, Long replyToId, String replyToUsername, String replyToContent) {
        HBox seenContainer = createSeenContainer(false);
        if (messageId > 0) {
            messageSeenContainers.put(messageId, seenContainer);
            messageSeenUsers.put(messageId, new ArrayList<>());
        }
        messagesBox.getChildren().add(createMessageWrapper(senderId, senderUsername, text, messageId, seenContainer, replyToId, replyToUsername, replyToContent));
        for (Label label : messageStatusLabels.values()) label.setVisible(false);
    }

    private void addSentMessage(String text, long messageId, String status) {
        addSentMessage(text, messageId, status, null, null, null);
    }

    private void addSentMessage(String text, long messageId, String status, Long replyToId, String replyToUsername, String replyToContent) {
        for (Label oldLabel : messageStatusLabels.values()) oldLabel.setVisible(false);

        HBox wrapper = new HBox();
        wrapper.setAlignment(Pos.CENTER_RIGHT);
        VBox container = new VBox(2);
        container.setAlignment(Pos.BOTTOM_RIGHT);

        Node bubble = createMessageBubble(text, StyleConstants.ACCENT, "18px 18px 4px 18px");
        if (messageId > 0) messageBubbleById.put(messageId, bubble);
        addContextMenuToBubble(bubble, messageId, "Bạn", text);

        VBox bubbleGroup = new VBox(4);
        bubbleGroup.setAlignment(Pos.TOP_RIGHT);
        if (replyToId != null) {
            VBox quoteBox = createQuoteBox(replyToId, replyToUsername, replyToContent, true);
            quoteBox.setMaxWidth(360);
            bubbleGroup.getChildren().add(quoteBox);
        }
        bubbleGroup.getChildren().add(bubble);

        Label statusLabel = new Label(ChatController.getStatusLabelText(status));
        statusLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #888888; -fx-padding: 0 4px 0 0;");
        if (messageId > 0) messageStatusLabels.put(messageId, statusLabel);

        HBox seenContainer = createSeenContainer(true);
        if (messageId > 0) {
            messageSeenContainers.put(messageId, seenContainer);
            messageSeenUsers.put(messageId, new ArrayList<>());
        }

        container.getChildren().addAll(bubbleGroup, statusLabel, seenContainer);
        wrapper.getChildren().add(container);
        messagesBox.getChildren().add(wrapper);
    }

    private Node createMessageBubble(String text, String bg, String radius) {
        Node content = EmojiManager.getInstance().renderMessage(text);

        // If the rendered content is a TextFlow (multi-node), wrap in styling container
        if (content instanceof TextFlow) {
            TextFlow flow = (TextFlow) content;
            StackPane wrapper = new StackPane(flow);
            wrapper.setStyle("-fx-background-color: " + bg + "; -fx-background-radius: " + radius + "; -fx-padding: 12px 18px;");
            wrapper.setMaxWidth(360);
            return wrapper;
        }

        // If it's an ImageView (solo emoji GIF), wrap with transparent background
        if (content instanceof ImageView) {
            StackPane wrapper = new StackPane(content);
            wrapper.setStyle("-fx-background-color: transparent; -fx-padding: 4px;");
            return wrapper;
        }

        // If it's a plain Label, style it normally
        if (content instanceof Label) {
            Label bubble = (Label) content;
            bubble.setMaxWidth(360);
            bubble.setStyle("-fx-background-color: " + bg + "; -fx-text-fill: " + StyleConstants.TEXT_WHITE
                    + "; -fx-font-size: 15px; -fx-padding: 12px 18px; -fx-background-radius: " + radius + ";");
            return bubble;
        }

        return content;
    }

    private void addContextMenuToBubble(Node bubble, long messageId, String senderUsername, String content) {
        if (messageId <= 0) return;
        ContextMenu menu = new ContextMenu();
        menu.setStyle("-fx-background-color: #222222; -fx-border-color: #333333; -fx-border-width: 1px; -fx-background-radius: 10px; -fx-border-radius: 10px; -fx-padding: 6px;");

        MenuItem replyItem = new MenuItem("Phản hồi");
        replyItem.setStyle("-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 8px 16px;");
        replyItem.setOnAction(e -> setReplyTarget(messageId, senderUsername, content));

        menu.getItems().add(replyItem);

        if (bubble instanceof javafx.scene.control.Control) {
            ((javafx.scene.control.Control) bubble).setContextMenu(menu);
        }
    }

    private HBox createSeenContainer(boolean isMine) {
        HBox container = new HBox(-4); // overlapping avatars
        container.setAlignment(isMine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        container.setPadding(new Insets(2, 4, 0, 4));
        container.setVisible(false);
        container.setManaged(false);
        return container;
    }

    private void updateSeenAvatars(long messageId, List<ReaderInfo> readers) {
        HBox container = messageSeenContainers.get(messageId);
        if (container == null) return;

        container.getChildren().clear();

        List<ReaderInfo> filteredReaders = new ArrayList<>();
        if (readers != null) {
            for (ReaderInfo reader : readers) {
                if (reader.userId != currentUserId) {
                    filteredReaders.add(reader);
                }
            }
        }

        if (filteredReaders.isEmpty()) {
            container.setVisible(false);
            container.setManaged(false);
            return;
        }

        container.setVisible(true);
        container.setManaged(true);

        for (ReaderInfo reader : filteredReaders) {
            Circle readerAvatar = new Circle(8); // radius = 8px (16px diameter)
            readerAvatar.setFill(Color.web("#444"));
            readerAvatar.setStroke(Color.web("#1e1e1e"));
            readerAvatar.setStrokeWidth(1);

            Tooltip tooltip = new Tooltip(reader.username);
            tooltip.setStyle("-fx-font-size: 11px;");
            Tooltip.install(readerAvatar, tooltip);

            peerAvatarCircles.computeIfAbsent(reader.userId, k -> new ArrayList<>()).add(readerAvatar);
            loadPeerAvatar(reader.userId, readerAvatar);

            container.getChildren().add(readerAvatar);
        }
    }

    private void sendMessage() {
        String text = messageInput.getText().trim();
        if (!text.isEmpty() && controller.getChatService().isConnected() && currentConversationId > 0) {
            Long replyId = activeReplyToId;
            controller.sendMessage(currentConversationId, text, replyId,
                    err -> showToast("Gửi tin nhắn thất bại: " + err));
            messageInput.clear();
            cancelReply();
        }
    }

    private void setReplyTarget(long messageId, String senderUsername, String content) {
        activeReplyToId = messageId;
        String safeUsername = senderUsername != null && !senderUsername.isBlank() ? senderUsername : "tin nhan";
        String safeContent = content != null ? content : "";
        replyPreviewUserLabel.setText("Dang tra loi " + safeUsername);
        replyPreviewContentLabel.setText(truncateText(safeContent, 80));
        if (replyPreviewBar != null) {
            replyPreviewBar.setVisible(true);
            replyPreviewBar.setManaged(true);
        }
        messageInput.requestFocus();
    }

    private void cancelReply() {
        activeReplyToId = null;
        if (replyPreviewBar != null) {
            replyPreviewBar.setVisible(false);
            replyPreviewBar.setManaged(false);
        }
    }

    // ==================== EMOJI PICKER ====================

    private Popup emojiPopup;

    private void showEmojiPicker(Button owner) {
        if (emojiPopup != null && emojiPopup.isShowing()) {
            emojiPopup.hide();
            return;
        }

        emojiPopup = new Popup();
        emojiPopup.setAutoHide(true);
        emojiPopup.setHideOnEscape(true);

        VBox picker = EmojiManager.getInstance().createEmojiPicker(label -> {
            // Insert emoji label at cursor position
            int caretPos = messageInput.getCaretPosition();
            String current = messageInput.getText();
            String before = current.substring(0, caretPos);
            String after = current.substring(caretPos);
            messageInput.setText(before + label + after);
            messageInput.positionCaret(caretPos + label.length());
            messageInput.requestFocus();
        });

        // Wrap in a styled container
        StackPane container = new StackPane(picker);
        container.setStyle("-fx-background-color: #1a1a1a; -fx-background-radius: 12px; -fx-border-color: #444; -fx-border-width: 1px; -fx-border-radius: 12px;");
        container.setPadding(new Insets(8));

        emojiPopup.getContent().add(container);

        // Show popup above the emoji button
        javafx.geometry.Bounds bounds = owner.localToScreen(owner.getBoundsInLocal());
        emojiPopup.show(owner, bounds.getMinX() - 200, bounds.getMinY() - 320);
    }

    private VBox createQuoteBox(long replyToId, String username, String content, boolean isMine) {
        VBox quote = new VBox(2);
        quote.setPadding(new Insets(6, 10, 6, 10));
        String borderCol = isMine ? "#ffffff" : StyleConstants.ACCENT;
        String bgCol = isMine ? "rgba(255, 255, 255, 0.15)" : "#2a2a2a";
        quote.setStyle("-fx-background-color: " + bgCol + "; -fx-border-color: " + borderCol + "; -fx-border-width: 0 0 0 3px; -fx-background-radius: 4px; -fx-border-radius: 0;");
        quote.setCursor(javafx.scene.Cursor.HAND);

        Label userLabel = new Label(username != null && !username.isBlank() ? username : "Tin nhan");
        userLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: " + (isMine ? "#ffd166" : StyleConstants.ACCENT) + ";");

        Label contentLabel = new Label(truncateText(content != null ? content : "", 60));
        contentLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #dddddd;");

        quote.getChildren().addAll(userLabel, contentLabel);

        quote.setOnMouseClicked(e -> {
            e.consume();
            if (messageBubbleById.containsKey(replyToId)) {
                scrollToAndHighlightMessage(replyToId);
            } else {
                openMessageFromSearch(currentConversationId, replyToId);
            }
        });

        return quote;
    }

    private String truncateText(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    // ==================== MESSAGE SEARCH ====================

    private void searchMessagesInCurrentConversation() {
        if (messageSearchField == null) return;
        String keyword = messageSearchField.getText().trim();
        if (keyword.isEmpty()) { clearMessageSearchResults(); return; }
        if (currentConversationId <= 0) { showMessageSearchStatus("Hãy chọn một cuộc trò chuyện trước.", true); return; }
        if (!controller.getChatService().isConnected()) { showMessageSearchStatus("Chưa kết nối được server TCP.", true); return; }

        long capturedConvId = currentConversationId;
        showMessageSearchStatus("Đang tìm tin nhắn...", false);
        resetMessageSearchState(false);
        setMessageSearchBusy(true);

        controller.searchMessages(capturedConvId, keyword, 20, 0,
                json -> renderMessageSearchResults(capturedConvId, keyword, json),
                err -> {
                    setMessageSearchBusy(false);
                    showMessageSearchStatus(err, true);
                });
    }

    private void renderMessageSearchResults(long capturedConvId, String keyword, JsonObject json) {
        setMessageSearchBusy(false);
        if (currentConversationId != capturedConvId) return;
        JsonArray messages = json.getAsJsonArray("messages");
        messageSearchResults.getChildren().clear();
        messageSearchMatches.clear();
        messageSearchItems.clear();
        activeMessageSearchIndex = -1;
        activeMessageSearchKeyword = keyword;

        if (messages == null || messages.isEmpty()) {
            showMessageSearchStatus("Không có tin nhắn phù hợp.", false);
            updateMessageSearchNavigator();
            showSearchResultsView(true);
            return;
        }

        showMessageSearchStatus("Tìm thấy " + messages.size() + " kết quả cho: " + keyword, false);
        int idx = 0;
        for (JsonElement element : messages) {
            JsonObject msg = element.getAsJsonObject();
            messageSearchMatches.add(msg);
            VBox item = createMessageSearchResultItem(msg, idx++);
            messageSearchItems.add(item);
            messageSearchResults.getChildren().add(item);
        }
        activeMessageSearchIndex = 0;
        updateMessageSearchNavigator();
        showSearchResultsView(true);
    }

    private VBox createMessageSearchResultItem(JsonObject msg, int resultIndex) {
        long senderId = msg.has("senderId") ? msg.get("senderId").getAsLong() : -1;
        String sender = senderId == currentUserId ? "Bạn" : "";
        String content = msg.has("content") && !msg.get("content").isJsonNull() ? msg.get("content").getAsString() : "";
        String createdAt = msg.has("createdAt") && !msg.get("createdAt").isJsonNull() ? msg.get("createdAt").getAsString() : "";
        String senderUsername = msg.has("senderUsername") && !msg.get("senderUsername").isJsonNull()
                ? msg.get("senderUsername").getAsString() : "";
        if (senderId != currentUserId && !senderUsername.isBlank()) sender = senderUsername;
        else if (sender.isBlank()) sender = "Người dùng";

        Label senderLabel = new Label(sender);
        senderLabel.setStyle("-fx-text-fill: " + StyleConstants.TEXT_WHITE + "; -fx-font-size: 14px; -fx-font-weight: bold;");

        String preview = content.isBlank() ? "(không có nội dung)" : content.trim();
        if (!createdAt.isBlank()) preview += " - " + createdAt;

        Label previewLabel = new Label(preview);
        previewLabel.setMaxWidth(Double.MAX_VALUE);
        previewLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        previewLabel.setStyle("-fx-text-fill: " + StyleConstants.TEXT_MUTED + "; -fx-font-size: 12px;");

        VBox item = new VBox(3, senderLabel, previewLabel);
        item.setMaxWidth(Double.MAX_VALUE);
        item.setPadding(new Insets(12, 14, 12, 14));
        item.setCursor(javafx.scene.Cursor.HAND);
        styleMessageSearchItem(item, false, false);
        item.setOnMouseEntered(e -> styleMessageSearchItem(item, resultIndex == activeMessageSearchIndex, true));
        item.setOnMouseExited(e -> styleMessageSearchItem(item, resultIndex == activeMessageSearchIndex, false));
        item.setOnMouseClicked(e -> jumpToMessageSearchResultAt(resultIndex));
        return item;
    }

    private void openRelativeMessageSearchResult(int direction) {
        if (messageSearchMatches.isEmpty()) return;
        int next = activeMessageSearchIndex + direction;
        if (next < 0) next = messageSearchMatches.size() - 1;
        else if (next >= messageSearchMatches.size()) next = 0;
        selectMessageSearchResultAt(next);
    }

    private void selectMessageSearchResultAt(int index) {
        if (index < 0 || index >= messageSearchMatches.size()) return;
        activeMessageSearchIndex = index;
        updateMessageSearchNavigator();
    }

    private void jumpToMessageSearchResultAt(int index) {
        if (index < 0 || index >= messageSearchMatches.size()) return;
        selectMessageSearchResultAt(index);
        JsonObject msg = messageSearchMatches.get(index);
        long messageId = msg.has("id") ? msg.get("id").getAsLong() : -1;
        long convId = msg.has("conversationId") ? msg.get("conversationId").getAsLong() : currentConversationId;
        showSearchResultsView(false);
        openMessageFromSearch(convId, messageId);
    }

    private void showSearchResultsView(boolean show) {
        if (messageSearchPanel != null) { messageSearchPanel.setVisible(show); messageSearchPanel.setManaged(show); }
        if (!show) {
            if (messageSearchNavigator != null) { messageSearchNavigator.setVisible(false); messageSearchNavigator.setManaged(false); }
            if (messageSearchResultsScroll != null) { messageSearchResultsScroll.setVisible(false); messageSearchResultsScroll.setManaged(false); }
        }
        if (scrollMessages != null) { scrollMessages.setVisible(!show); scrollMessages.setManaged(!show); }
        if (typingLabel != null) { typingLabel.setManaged(!show); if (show) typingLabel.setVisible(false); }
    }

    private void setMessageSearchBusy(boolean busy) {
        if (messageSearchButton != null) messageSearchButton.setDisable(busy);
        if (messageSearchField != null) messageSearchField.setDisable(busy);
    }

    private void updateMessageSearchNavigator() {
        boolean has = !messageSearchMatches.isEmpty();
        if (messageSearchNavigator != null) { messageSearchNavigator.setVisible(has); messageSearchNavigator.setManaged(has); }
        if (messageSearchResultsScroll != null) { messageSearchResultsScroll.setVisible(has); messageSearchResultsScroll.setManaged(has); }
        if (messageSearchCounter != null) messageSearchCounter.setText(has ? (activeMessageSearchIndex + 1) + "/" + messageSearchMatches.size() : "");
        if (messageSearchPrevBtn != null) messageSearchPrevBtn.setDisable(!has || messageSearchMatches.size() == 1);
        if (messageSearchNextBtn != null) messageSearchNextBtn.setDisable(!has || messageSearchMatches.size() == 1);

        for (int i = 0; i < messageSearchItems.size(); i++)
            styleMessageSearchItem(messageSearchItems.get(i), i == activeMessageSearchIndex, false);
        scrollSearchResultsToActiveItem();
    }

    private void scrollSearchResultsToActiveItem() {
        if (messageSearchResultsScroll == null || activeMessageSearchIndex < 0
                || activeMessageSearchIndex >= messageSearchItems.size()) return;
        Platform.runLater(() -> {
            double itemCount = Math.max(1, messageSearchItems.size() - 1);
            messageSearchResultsScroll.setVvalue(activeMessageSearchIndex / itemCount);
        });
    }

    private void styleMessageSearchItem(VBox item, boolean active, boolean hover) {
        String bg = active ? "#1f1835" : (hover ? "#181818" : "transparent");
        String border = active ? StyleConstants.ACCENT : StyleConstants.BORDER_COLOR;
        item.setStyle("-fx-background-color: " + bg + "; -fx-border-color: " + border + "; -fx-border-width: 0 0 1px 0;");
    }

    private void openMessageFromSearch(long conversationId, long messageId) {
        if (conversationId != currentConversationId) {
            showMessageSearchStatus("Kết quả này không thuộc cuộc trò chuyện đang mở.", true); return;
        }
        if (messageId <= 0) { showMessageSearchStatus("Kết quả này thiếu messageId.", true); return; }
        if (scrollToAndHighlightMessage(messageId)) return;
        if (!hasMoreMessages) { showMessageSearchStatus("Tin nhắn chưa có trong phần UI đang hiển thị.", true); return; }

        showMessageSearchStatus("Đang tải thêm lịch sử...", false);
        loadOlderMessagesUntilVisible(conversationId, messageId, 0);
    }

    private void loadOlderMessagesUntilVisible(long conversationId, long messageId, int attempt) {
        if (conversationId != currentConversationId || attempt >= 8 || !hasMoreMessages || isLoadingMore) return;

        isLoadingMore = true;
        int offset = currentMessageOffset;
        controller.loadMessages(conversationId, PAGE_SIZE, offset, json -> {
            if (currentConversationId != conversationId) { isLoadingMore = false; return; }
            renderMessagesPage(json, false, conversationId, offset);
            isLoadingMore = false;
            if (!scrollToAndHighlightMessage(messageId))
                loadOlderMessagesUntilVisible(conversationId, messageId, attempt + 1);
        });
    }

    private boolean scrollToAndHighlightMessage(long messageId) {
        Node bubble = messageBubbleById.get(messageId);
        if (bubble == null || scrollMessages == null || messagesBox == null) return false;

        Platform.runLater(() -> {
            javafx.geometry.Bounds bubbleBounds = bubble.localToScene(bubble.getBoundsInLocal());
            javafx.geometry.Bounds listBounds = messagesBox.localToScene(messagesBox.getBoundsInLocal());
            double viewportH = scrollMessages.getViewportBounds().getHeight();
            double contentH = Math.max(messagesBox.getBoundsInLocal().getHeight(), viewportH + 1);
            double targetY = bubbleBounds.getMinY() - listBounds.getMinY() - viewportH / 2;
            scrollMessages.setVvalue(Math.max(0, Math.min(1, targetY / Math.max(1, contentH - viewportH))));
            highlightMessageBubble(bubble);
        });
        return true;
    }

    private void highlightMessageBubble(Node bubble) {
        if (highlightedMessageBubble != null && highlightedMessageStyle != null)
            highlightedMessageBubble.setStyle(highlightedMessageStyle);
        highlightedMessageBubble = bubble;
        highlightedMessageStyle = bubble.getStyle();
        bubble.setStyle(highlightedMessageStyle + "-fx-border-color: #ffd166; -fx-border-width: 2px; -fx-border-radius: 18px;");

        javafx.animation.PauseTransition delay = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2.5));
        delay.setOnFinished(e -> {
            if (highlightedMessageBubble == bubble && highlightedMessageStyle != null) {
                bubble.setStyle(highlightedMessageStyle);
                highlightedMessageBubble = null;
                highlightedMessageStyle = null;
            }
        });
        delay.play();
    }

    private void showMessageSearchStatus(String text, boolean error) {
        if (messageSearchPanel == null || messageSearchStatus == null) return;
        messageSearchPanel.setVisible(true);
        messageSearchPanel.setManaged(true);
        messageSearchStatus.setText(text);
        messageSearchStatus.setStyle("-fx-text-fill: " + (error ? "#ff7777" : StyleConstants.TEXT_MUTED) + "; -fx-font-size: 12px;");
        if (error) messageSearchResults.getChildren().clear();
    }

    private void clearMessageSearchResults() {
        if (messageSearchField != null) messageSearchField.clear();
        if (messageSearchResults != null) messageSearchResults.getChildren().clear();
        if (messageSearchStatus != null) messageSearchStatus.setText("");
        if (messageSearchPanel != null) { messageSearchPanel.setVisible(false); messageSearchPanel.setManaged(false); }
        showSearchResultsView(false);
        messageSearchMatches.clear();
        messageSearchItems.clear();
        activeMessageSearchIndex = -1;
        activeMessageSearchKeyword = "";
        updateMessageSearchNavigator();
    }

    private void resetMessageSearchState(boolean clearField) {
        if (clearField && messageSearchField != null) messageSearchField.clear();
        if (messageSearchResults != null) messageSearchResults.getChildren().clear();
        if (messageSearchStatus != null) messageSearchStatus.setText("");
        showSearchResultsView(false);
        messageSearchMatches.clear();
        messageSearchItems.clear();
        activeMessageSearchIndex = -1;
        activeMessageSearchKeyword = "";
    }

    private void limitTextInput(TextInputControl input, int maxLength) {
        input.textProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null && newValue.length() > maxLength) {
                input.setText(newValue.substring(0, maxLength));
                input.positionCaret(maxLength);
            }
        });
    }

    // ==================== UI LAYOUT ====================

    private VBox createLeftPanel() {
        VBox panel = new VBox(10);
        panel.setPrefWidth(270);
        panel.setPadding(new Insets(20));
        panel.setStyle("-fx-background-color: " + StyleConstants.PANEL_DARK + "; -fx-border-color: " + StyleConstants.BORDER_COLOR + "; -fx-border-width: 0 1 0 0;");

        // Header row with title + create group button
        HBox headerRow = new HBox(8);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        Label header = new Label("SinChat");
        header.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: " + StyleConstants.TEXT_WHITE + ";");

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        Button newGroupBtn = new Button("+ Nhóm");
        newGroupBtn.setStyle(
                "-fx-background-color: " + StyleConstants.ACCENT + ";" +
                "-fx-text-fill: white;" +
                "-fx-font-size: 12px;" +
                "-fx-font-weight: bold;" +
                "-fx-background-radius: 10px;" +
                "-fx-padding: 6px 12px;" +
                "-fx-cursor: hand;"
        );
        newGroupBtn.setOnMouseEntered(e -> newGroupBtn.setStyle(
                "-fx-background-color: #6a4ee8;" +
                "-fx-text-fill: white;" +
                "-fx-font-size: 12px;" +
                "-fx-font-weight: bold;" +
                "-fx-background-radius: 10px;" +
                "-fx-padding: 6px 12px;" +
                "-fx-cursor: hand;"
        ));
        newGroupBtn.setOnMouseExited(e -> newGroupBtn.setStyle(
                "-fx-background-color: " + StyleConstants.ACCENT + ";" +
                "-fx-text-fill: white;" +
                "-fx-font-size: 12px;" +
                "-fx-font-weight: bold;" +
                "-fx-background-radius: 10px;" +
                "-fx-padding: 6px 12px;" +
                "-fx-cursor: hand;"
        ));
        newGroupBtn.setOnAction(e -> {
            CreateGroupDialog dlg = new CreateGroupDialog(stage, controller,
                    (convId, groupName) -> {
                        loadConversations();
                        setCurrentConversation(convId, groupName);
                    });
            dlg.show();
        });

        headerRow.getChildren().addAll(header, headerSpacer, newGroupBtn);

        TextField searchField = new TextField();
        searchField.setPromptText("Tìm kiếm...");
        searchField.setStyle("-fx-background-color: " + StyleConstants.BG_BLACK + "; -fx-border-color: " + StyleConstants.INPUT_BORDER
                + "; -fx-border-width: 1.5px; -fx-border-radius: 20px; -fx-background-radius: 20px; -fx-text-fill: "
                + StyleConstants.TEXT_WHITE + "; -fx-prompt-text-fill: " + StyleConstants.TEXT_DIM
                + "; -fx-font-size: 14px; -fx-padding: 10px 16px;");

        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            String query = newVal.trim();
            if (query.isEmpty()) {
                loadConversations();
            } else {
                controller.searchUsers(query, users -> {
                    contactList.getChildren().clear();
                    contactLastMsgLabels.clear();
                    for (JsonElement element : users) {
                        JsonObject user = element.getAsJsonObject();
                        long uId = user.get("userId").getAsLong();
                        String username = user.get("username").getAsString();
                        addSearchResultContact(uId, username, searchField);
                    }
                }, errMsg -> {
                    System.err.println("[ChatView] Search users failed: " + errMsg);
                });
            }
        });

        contactList = new VBox(4);
        ScrollPane scrollContacts = new ScrollPane(contactList);
        scrollContacts.setFitToWidth(true);
        scrollContacts.setStyle("-fx-background: " + StyleConstants.PANEL_DARK + "; -fx-background-color: " + StyleConstants.PANEL_DARK + "; -fx-border-color: transparent;");
        VBox.setVgrow(scrollContacts, Priority.ALWAYS);

        panel.getChildren().addAll(headerRow, searchField, scrollContacts);
        return panel;
    }

    private void addSearchResultContact(long uId, String username, TextField searchField) {
        HBox contact = new HBox(12);
        contact.setAlignment(Pos.CENTER_LEFT);
        contact.setPadding(new Insets(12, 14, 12, 14));
        String radius = "16px";
        contact.setStyle(StyleConstants.contactItemNormalStyle(radius));

        Circle avatar = new Circle(22);
        avatar.setFill(Color.web("#444"));
        avatar.setStroke(Color.web(StyleConstants.BORDER_COLOR));

        VBox info = new VBox(3);
        Label nameLabel = new Label(username);
        nameLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: " + StyleConstants.TEXT_WHITE + ";");
        Label msgLabel = new Label("Nhập để nhắn tin");
        msgLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: " + StyleConstants.TEXT_MUTED + ";");
        info.getChildren().addAll(nameLabel, msgLabel);
        contact.getChildren().addAll(avatar, info);

        contact.setOnMouseEntered(e -> contact.setStyle(StyleConstants.contactItemHoverStyle(radius)));
        contact.setOnMouseExited(e -> contact.setStyle(StyleConstants.contactItemNormalStyle(radius)));
        contact.setOnMouseClicked(e -> {
            controller.getOrCreateConversation(uId, convId -> {
                searchField.clear();
                loadConversations();
                setCurrentConversation(convId, username);
            });
        });
        contactList.getChildren().add(contact);
    }

    private VBox createCenterPanel() {
        VBox panel = new VBox();
        panel.setStyle("-fx-background-color: " + StyleConstants.BG_BLACK + ";");

        // Chat header
        HBox chatHeader = new HBox(12);
        chatHeader.setAlignment(Pos.CENTER_LEFT);
        chatHeader.setPadding(new Insets(16, 24, 16, 24));
        chatHeader.setStyle("-fx-background-color: " + StyleConstants.PANEL_DARK + "; -fx-border-color: " + StyleConstants.BORDER_COLOR + "; -fx-border-width: 0 0 1 0;");

        Circle headerAvatar = new Circle(20);
        headerAvatar.setFill(Color.web("#444"));

        VBox headerInfo = new VBox(2);
        headerChatName = new Label("Chọn người để chat");
        headerChatName.setStyle("-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: " + StyleConstants.TEXT_WHITE + ";");

        chatStatus = new Label("Offline");
        chatStatus.setStyle("-fx-font-size: 12px; -fx-text-fill: #888888;");
        headerInfo.getChildren().addAll(headerChatName, chatStatus);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Message search controls
        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_RIGHT);

        messageSearchField = new TextField();
        messageSearchField.setPromptText("Tìm tin nhắn...");
        messageSearchField.setPrefWidth(190);
        limitTextInput(messageSearchField, MAX_MESSAGE_SEARCH_KEYWORD_LENGTH);
        messageSearchField.setStyle("-fx-background-color: " + StyleConstants.BG_BLACK + "; -fx-border-color: " + StyleConstants.INPUT_BORDER
                + "; -fx-border-width: 1.2px; -fx-border-radius: 18px; -fx-background-radius: 18px; -fx-text-fill: "
                + StyleConstants.TEXT_WHITE + "; -fx-prompt-text-fill: " + StyleConstants.TEXT_DIM
                + "; -fx-font-size: 13px; -fx-padding: 8px 12px;");

        messageSearchButton = createIconButton("Tìm");
        messageSearchButton.setOnAction(e -> searchMessagesInCurrentConversation());
        messageSearchField.setOnAction(e -> searchMessagesInCurrentConversation());

        leaveGroupBtn = new Button("Rời nhóm");
        leaveGroupBtn.setStyle("-fx-background-color: rgba(255, 59, 48, 0.15); -fx-text-fill: #ff453a; -fx-border-color: rgba(255, 69, 58, 0.3); -fx-border-width: 1.2px; -fx-border-radius: 18px; -fx-font-weight: bold; -fx-font-size: 13px; -fx-background-radius: 18px; -fx-min-height: 38px; -fx-padding: 0 16px; -fx-cursor: hand;");
        leaveGroupBtn.setOnMouseEntered(e -> leaveGroupBtn.setStyle("-fx-background-color: #ff3b30; -fx-text-fill: white; -fx-border-color: transparent; -fx-border-width: 1.2px; -fx-border-radius: 18px; -fx-font-weight: bold; -fx-font-size: 13px; -fx-background-radius: 18px; -fx-min-height: 38px; -fx-padding: 0 16px; -fx-cursor: hand;"));
        leaveGroupBtn.setOnMouseExited(e -> leaveGroupBtn.setStyle("-fx-background-color: rgba(255, 59, 48, 0.15); -fx-text-fill: #ff453a; -fx-border-color: rgba(255, 69, 58, 0.3); -fx-border-width: 1.2px; -fx-border-radius: 18px; -fx-font-weight: bold; -fx-font-size: 13px; -fx-background-radius: 18px; -fx-min-height: 38px; -fx-padding: 0 16px; -fx-cursor: hand;"));
        leaveGroupBtn.setVisible(false);
        leaveGroupBtn.setManaged(false);

        leaveGroupBtn.setOnAction(evt -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Xác nhận thoát nhóm");
            alert.setHeaderText(null);
            alert.setContentText("Bạn có chắc chắn muốn thoát khỏi nhóm này không?");

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                controller.leaveGroup(currentConversationId, () -> {
                    showToast("Đã thoát nhóm thành công!");
                    currentConversationId = -1;
                    messagesBox.getChildren().clear();
                    if (headerChatName != null) headerChatName.setText("Chọn người để chat");
                    if (chatStatus != null) chatStatus.setText("Offline");
                    if (leaveGroupBtn != null) {
                        leaveGroupBtn.setVisible(false);
                        leaveGroupBtn.setManaged(false);
                    }
                    loadConversations();
                }, err -> showToast("Không thể thoát nhóm: " + err));
            }
        });

        actions.getChildren().addAll(messageSearchField, messageSearchButton,
                createIconButton("Call"), createIconButton("Video"), leaveGroupBtn);
        chatHeader.getChildren().addAll(headerAvatar, headerInfo, spacer, actions);

        // Search results panel
        messageSearchStatus = new Label("");
        messageSearchStatus.setStyle("-fx-text-fill: " + StyleConstants.TEXT_MUTED + "; -fx-font-size: 12px;");

        messageSearchCounter = new Label("");
        messageSearchCounter.setMinWidth(48);
        messageSearchCounter.setStyle("-fx-text-fill: " + StyleConstants.TEXT_WHITE + "; -fx-font-size: 12px; -fx-font-weight: bold;");

        messageSearchPrevBtn = createSearchNavButton("^");
        messageSearchNextBtn = createSearchNavButton("v");
        Button msgSearchCloseBtn = createSearchNavButton("x");
        messageSearchPrevBtn.setOnAction(e -> openRelativeMessageSearchResult(-1));
        messageSearchNextBtn.setOnAction(e -> openRelativeMessageSearchResult(1));
        msgSearchCloseBtn.setOnAction(e -> clearMessageSearchResults());

        messageSearchNavigator = new HBox(8, messageSearchCounter, messageSearchPrevBtn, messageSearchNextBtn, msgSearchCloseBtn);
        messageSearchNavigator.setAlignment(Pos.CENTER_LEFT);
        messageSearchNavigator.setVisible(false);
        messageSearchNavigator.setManaged(false);

        messageSearchResults = new VBox(6);
        messageSearchResults.setMaxWidth(Double.MAX_VALUE);
        messageSearchResultsScroll = new ScrollPane(messageSearchResults);
        messageSearchResultsScroll.setFitToWidth(true);
        messageSearchResultsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        messageSearchResultsScroll.setVisible(false);
        messageSearchResultsScroll.setManaged(false);
        messageSearchResultsScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-border-color: transparent;");
        VBox.setVgrow(messageSearchResultsScroll, Priority.ALWAYS);

        messageSearchPanel = new VBox(8, messageSearchStatus, messageSearchNavigator, messageSearchResultsScroll);
        messageSearchPanel.setFillWidth(true);
        messageSearchPanel.setPadding(new Insets(14, 24, 14, 24));
        messageSearchPanel.setVisible(false);
        messageSearchPanel.setManaged(false);
        messageSearchPanel.setStyle("-fx-background-color: #101010; -fx-border-color: " + StyleConstants.BORDER_COLOR + "; -fx-border-width: 0 0 1 0;");

        // Messages area
        messagesBox = new VBox(12);
        messagesBox.setPadding(new Insets(20, 24, 20, 24));

        loadingIndicator = new Label("Đang tải tin nhắn cũ hơn...");
        loadingIndicator.setVisible(false);
        loadingIndicator.setMaxWidth(Double.MAX_VALUE);
        loadingIndicator.setAlignment(Pos.CENTER);
        loadingIndicator.setStyle("-fx-font-size: 12px; -fx-text-fill: " + StyleConstants.TEXT_MUTED + "; -fx-padding: 8px 0;");

        scrollMessages = new ScrollPane(messagesBox);
        scrollMessages.setFitToWidth(true);
        scrollMessages.setStyle("-fx-background: " + StyleConstants.BG_BLACK + "; -fx-background-color: " + StyleConstants.BG_BLACK + "; -fx-border-color: transparent;");
        VBox.setVgrow(scrollMessages, Priority.ALWAYS);
        scrollMessages.vvalueProperty().addListener(scrollListener);

        // Typing indicator
        typingLabel = new Label("Đang gõ...");
        typingLabel.setVisible(false);
        typingLabel.setPadding(new Insets(4, 24, 4, 24));
        typingLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: " + StyleConstants.TEXT_MUTED + "; -fx-font-style: italic;");

        // Input bar
        HBox inputBar = new HBox(12);
        inputBar.setAlignment(Pos.CENTER);
        inputBar.setPadding(new Insets(16, 24, 16, 24));
        inputBar.setStyle("-fx-background-color: " + StyleConstants.PANEL_DARK + "; -fx-border-color: " + StyleConstants.BORDER_COLOR + "; -fx-border-width: 1 0 0 0;");

        Button attachBtn = new Button("+");
        attachBtn.setStyle("-fx-background-color: " + StyleConstants.ACCENT + "; -fx-text-fill: " + StyleConstants.TEXT_WHITE
                + "; -fx-font-size: 20px; -fx-font-weight: bold; -fx-background-radius: 50%; -fx-min-width: 40px; -fx-min-height: 40px; -fx-cursor: hand;");

        // Emoji picker button
        Button emojiBtn = new Button("😊");
        emojiBtn.setStyle("-fx-background-color: #222; -fx-text-fill: " + StyleConstants.TEXT_WHITE
                + "; -fx-font-size: 18px; -fx-background-radius: 50%; -fx-min-width: 40px; -fx-min-height: 40px; -fx-cursor: hand;");
        emojiBtn.setOnMouseEntered(e -> emojiBtn.setStyle("-fx-background-color: " + StyleConstants.ACCENT + "; -fx-text-fill: "
                + StyleConstants.TEXT_WHITE + "; -fx-font-size: 18px; -fx-background-radius: 50%; -fx-min-width: 40px; -fx-min-height: 40px; -fx-cursor: hand;"));
        emojiBtn.setOnMouseExited(e -> emojiBtn.setStyle("-fx-background-color: #222; -fx-text-fill: " + StyleConstants.TEXT_WHITE
                + "; -fx-font-size: 18px; -fx-background-radius: 50%; -fx-min-width: 40px; -fx-min-height: 40px; -fx-cursor: hand;"));
        emojiBtn.setOnAction(e -> showEmojiPicker(emojiBtn));

        messageInput = new TextField();
        messageInput.setPromptText("Nhập tin nhắn...");
        messageInput.setStyle("-fx-background-color: " + StyleConstants.BG_BLACK + "; -fx-border-color: " + StyleConstants.INPUT_BORDER
                + "; -fx-border-width: 1.5px; -fx-border-radius: 24px; -fx-background-radius: 24px; -fx-text-fill: "
                + StyleConstants.TEXT_WHITE + "; -fx-prompt-text-fill: " + StyleConstants.TEXT_DIM
                + "; -fx-font-size: 15px; -fx-padding: 12px 18px;");
        messageInput.setPrefHeight(48);
        HBox.setHgrow(messageInput, Priority.ALWAYS);

        messageInput.textProperty().addListener((obs, oldVal, newVal) -> {
            if (controller.getChatService().isConnected() && currentConversationId > 0) {
                if (newVal.trim().isEmpty()) {
                    controller.sendTyping(currentConversationId, false);
                } else {
                    long now = System.currentTimeMillis();
                    if (now - lastTypingSentTime >= TYPING_THROTTLE_MS) {
                        lastTypingSentTime = now;
                        controller.sendTyping(currentConversationId, true);
                    }
                }
            }
        });

        Button sendBtn = new Button("Gửi");
        sendBtn.setStyle("-fx-background-color: " + StyleConstants.TEXT_WHITE + "; -fx-text-fill: " + StyleConstants.BG_BLACK
                + "; -fx-font-size: 15px; -fx-font-weight: bold; -fx-background-radius: 24px; -fx-padding: 12px 24px; -fx-cursor: hand;");
        sendBtn.setOnAction(e -> sendMessage());
        messageInput.setOnAction(e -> sendMessage());

        inputBar.getChildren().addAll(attachBtn, emojiBtn, messageInput, sendBtn);

        // Construct replyPreviewBar (hidden by default)
        replyPreviewBar = new HBox(12);
        replyPreviewBar.setAlignment(Pos.CENTER_LEFT);
        replyPreviewBar.setPadding(new Insets(8, 24, 8, 24));
        replyPreviewBar.setStyle("-fx-background-color: #141026; -fx-border-color: " + StyleConstants.BORDER_COLOR + "; -fx-border-width: 1 0 0 0;");
        replyPreviewBar.setVisible(false);
        replyPreviewBar.setManaged(false);

        VBox replyInfo = new VBox(2);
        replyPreviewUserLabel = new Label("Đang trả lời ai đó");
        replyPreviewUserLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: " + StyleConstants.ACCENT + "; -fx-font-size: 12px;");
        replyPreviewContentLabel = new Label("Nội dung trích dẫn");
        replyPreviewContentLabel.setStyle("-fx-text-fill: " + StyleConstants.TEXT_MUTED + "; -fx-font-size: 13px;");
        replyInfo.getChildren().addAll(replyPreviewUserLabel, replyPreviewContentLabel);
        HBox.setHgrow(replyInfo, Priority.ALWAYS);

        Button cancelReplyBtn = new Button("✕");
        cancelReplyBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + StyleConstants.TEXT_MUTED + "; -fx-font-size: 14px; -fx-cursor: hand; -fx-padding: 4px;");
        cancelReplyBtn.setOnAction(e -> cancelReply());
        replyPreviewBar.getChildren().addAll(replyInfo, cancelReplyBtn);

        panel.getChildren().addAll(chatHeader, messageSearchPanel, scrollMessages, typingLabel, replyPreviewBar, inputBar);
        return panel;
    }

    private VBox createRightPanel() {
        VBox panel = new VBox(16);
        panel.setPrefWidth(250);
        panel.setAlignment(Pos.TOP_CENTER);
        panel.setPadding(new Insets(30, 20, 20, 20));
        panel.setStyle("-fx-background-color: " + StyleConstants.PANEL_DARK + "; -fx-border-color: " + StyleConstants.BORDER_COLOR + "; -fx-border-width: 0 0 0 1;");

        currentAvatarImage = ImageUtils.createDefaultAvatarImage();
        initialAvatarImage = currentAvatarImage;
        profileAvatarCircle = new Circle(55);

        if (!currentAvatarImage.isError() && currentAvatarImage.getProgress() >= 1.0) {
            profileAvatarCircle.setFill(new ImagePattern(currentAvatarImage));
        } else {
            profileAvatarCircle.setFill(Color.web(StyleConstants.ACCENT));
            currentAvatarImage.progressProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal.doubleValue() >= 1.0 && !currentAvatarImage.isError())
                    profileAvatarCircle.setFill(new ImagePattern(currentAvatarImage));
            });
        }
        profileAvatarCircle.setStroke(Color.web(StyleConstants.ACCENT));
        profileAvatarCircle.setStrokeWidth(3);
        profileAvatarCircle.setCursor(javafx.scene.Cursor.HAND);
        profileAvatarCircle.setOnMouseClicked(e -> {
            ContextMenu menu = createAvatarContextMenu();
            menu.show(profileAvatarCircle, e.getScreenX(), e.getScreenY());
        });

        nameLabel = new Label("Sinh viên");
        nameLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: " + StyleConstants.TEXT_WHITE + ";");
        VBox.setMargin(nameLabel, new Insets(8, 0, 12, 0));

        Button avatarBtn = createProfileButton("Đổi avatar", true);
        avatarBtn.setOnAction(e -> {
            ContextMenu menu = createAvatarContextMenu();
            menu.show(avatarBtn, javafx.geometry.Side.BOTTOM, 0, 0);
        });

        Button nameBtn = createProfileButton("Đổi tên người dùng", false);
        nameBtn.setOnAction(e -> new ChangeUsernameDialog(stage, controller, nameLabel,
                () -> showToast("Đổi tên thành công!")).show());

        Button passBtn = createProfileButton("Đổi mật khẩu", false);
        passBtn.setOnAction(e -> new ChangePasswordDialog(stage, controller).show());

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Button logoutBtn = createProfileButton("Đăng xuất", false);
        logoutBtn.setStyle(logoutBtn.getStyle() + "-fx-border-color: #cc3333; -fx-text-fill: #cc3333;");
        logoutBtn.setOnAction(e -> {
            ChatService.resetInstance();
            LoginView loginView = new LoginView(stage);
            stage.setScene(loginView.createScene());
        });

        panel.getChildren().addAll(profileAvatarCircle, nameLabel, avatarBtn, nameBtn, passBtn, spacer, logoutBtn);
        return panel;
    }

    private ContextMenu createAvatarContextMenu() {
        ContextMenu menu = new ContextMenu();
        menu.setStyle("-fx-background-color: #222222; -fx-border-color: #333333; -fx-border-width: 1px; -fx-background-radius: 10px; -fx-border-radius: 10px; -fx-padding: 6px;");

        MenuItem changeItem = new MenuItem("Đổi avatar");
        changeItem.setStyle("-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 8px 16px;");
        changeItem.setOnAction(e -> {
            AvatarModalView modal = new AvatarModalView(stage, controller, profileAvatarCircle,
                    currentAvatarImage, previouslyUsedAvatars);
            modal.show();
            currentAvatarImage = modal.getCurrentAvatarImage();
        });

        MenuItem restoreItem = new MenuItem("Khôi phục lại avatar ban đầu");
        restoreItem.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 8px 16px;");
        restoreItem.setOnAction(e -> {
            if (currentAvatarImage == initialAvatarImage) {
                showToast("Ảnh đại diện đã là ảnh ban đầu!");
                return;
            }
            if (initialAvatarImage != null) {
                currentAvatarImage = initialAvatarImage;
                profileAvatarCircle.setFill(new ImagePattern(currentAvatarImage));
                controller.uploadAvatar(initialAvatarImage,
                        msg -> showToast("Đã khôi phục avatar ban đầu!"),
                        err -> showToast("Lỗi khi đồng bộ: " + err));
            }
        });

        menu.getItems().addAll(changeItem, restoreItem);
        return menu;
    }

    // ==================== UI HELPERS ====================

    private Button createIconButton(String text) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: #222; -fx-text-fill: " + StyleConstants.TEXT_WHITE
                + "; -fx-font-size: 13px; -fx-background-radius: 18px; -fx-min-height: 38px; -fx-padding: 0 14px; -fx-cursor: hand;");
        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: " + StyleConstants.ACCENT + "; -fx-text-fill: "
                + StyleConstants.TEXT_WHITE + "; -fx-font-size: 13px; -fx-background-radius: 18px; -fx-min-height: 38px; -fx-padding: 0 14px; -fx-cursor: hand;"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: #222; -fx-text-fill: " + StyleConstants.TEXT_WHITE
                + "; -fx-font-size: 13px; -fx-background-radius: 18px; -fx-min-height: 38px; -fx-padding: 0 14px; -fx-cursor: hand;"));
        return btn;
    }

    private Button createSearchNavButton(String text) {
        Button btn = new Button(text);
        btn.setMinSize(28, 26);
        btn.setPrefSize(28, 26);
        btn.setStyle("-fx-background-color: #202020; -fx-border-color: " + StyleConstants.BORDER_COLOR
                + "; -fx-border-width: 1px; -fx-border-radius: 13px; -fx-background-radius: 13px; -fx-text-fill: "
                + StyleConstants.TEXT_WHITE + "; -fx-font-size: 12px; -fx-font-weight: bold; -fx-cursor: hand;");
        return btn;
    }

    private Button createProfileButton(String text, boolean accent) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setPrefHeight(42);
        if (accent) {
            btn.setStyle("-fx-background-color: " + StyleConstants.ACCENT + "; -fx-text-fill: " + StyleConstants.TEXT_WHITE
                    + "; -fx-font-size: 14px; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-cursor: hand;");
        } else {
            btn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + StyleConstants.TEXT_MUTED
                    + "; -fx-font-size: 14px; -fx-border-color: " + StyleConstants.INPUT_BORDER
                    + "; -fx-border-width: 1.5px; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-cursor: hand;");
        }
        return btn;
    }

    private void scrollToBottom() {
        pendingScrollToBottom = true;
        Platform.runLater(() -> {
            messagesBox.applyCss();
            messagesBox.layout();
            scrollMessages.applyCss();
            scrollMessages.layout();
            scrollMessages.setVvalue(1.0);
            Platform.runLater(() -> {
                scrollMessages.setVvalue(1.0);
                pendingScrollToBottom = false;
            });
        });
    }

    private void hideLoadingIndicator() {
        if (loadingIndicator != null) {
            loadingIndicator.setVisible(false);
            messagesBox.getChildren().remove(loadingIndicator);
        }
    }

    // ==================== TOAST & NOTIFICATIONS ====================

    private void showToast(String text) {
        Label toast = new Label(text);
        toast.setStyle("-fx-background-color: #cc3333; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 14 22; -fx-background-radius: 14px;");

        Stage toastStage = new Stage();
        toastStage.initOwner(stage);
        toastStage.setAlwaysOnTop(true);
        StackPane toastRoot = new StackPane(toast);
        toastRoot.setStyle("-fx-background-color: transparent;");
        toastRoot.setPadding(new Insets(10));
        Scene toastScene = new Scene(toastRoot);
        toastScene.setFill(Color.TRANSPARENT);
        toastStage.initStyle(StageStyle.TRANSPARENT);
        toastStage.setScene(toastScene);
        toastStage.show();

        scheduler.schedule(() -> Platform.runLater(toastStage::close), 2500, TimeUnit.MILLISECONDS);
    }

    private void updateUnreadBadge(long conversationId) {
        Label badge = unreadBadgesByConvId.get(conversationId);
        if (badge == null) return;
        int count = unreadCounts.getOrDefault(conversationId, 0);
        Platform.runLater(() -> {
            if (count > 0) {
                badge.setText(count > 99 ? "99+" : String.valueOf(count));
                badge.setVisible(true);
                badge.setManaged(true);
            } else {
                badge.setText("");
                badge.setVisible(false);
                badge.setManaged(false);
            }
        });
    }

    private void showNewMessageNotification(String senderName, String content, long conversationId) {
        if (activeNotificationStages.size() >= 3) {
            Stage oldest = activeNotificationStages.remove(0);
            Platform.runLater(oldest::close);
        }

        String preview = content != null && content.length() > 60
                ? content.substring(0, 60) + "…" : (content != null ? content : "");

        Label iconLabel = new Label("💬");
        iconLabel.setStyle("-fx-font-size: 20px;");

        Label nameLabel = new Label(senderName);
        nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        int unread = unreadCounts.getOrDefault(conversationId, 0);
        Label countBadge = new Label(unread > 99 ? "99+" : String.valueOf(unread));
        countBadge.setStyle("-fx-background-color: #ff4444; -fx-text-fill: white; -fx-font-size: 11px; -fx-font-weight: bold; -fx-background-radius: 9px; -fx-padding: 2px 6px;");

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        Button closeBtn = new Button("✕");
        closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #888888; -fx-font-size: 13px; -fx-cursor: hand;");

        HBox headerRow = new HBox(8, iconLabel, nameLabel, countBadge, headerSpacer, closeBtn);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        Label previewLabel = new Label(preview);
        previewLabel.setWrapText(true);
        previewLabel.setMaxWidth(270);
        previewLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #aaaaaa;");

        VBox toastContent = new VBox(8, headerRow, previewLabel);
        toastContent.setPadding(new Insets(14, 16, 14, 16));
        toastContent.setMaxWidth(300);
        toastContent.setStyle("-fx-background-color: #1e1e1e; -fx-background-radius: 14px; -fx-border-color: #7c5cfc; -fx-border-width: 1.5px; -fx-border-radius: 14px;");
        toastContent.setCursor(javafx.scene.Cursor.HAND);

        StackPane toastRoot = new StackPane(toastContent);
        toastRoot.setStyle("-fx-background-color: transparent;");
        toastRoot.setPadding(new Insets(6));

        Stage toastStage = new Stage();
        toastStage.initOwner(stage);
        toastStage.initStyle(StageStyle.TRANSPARENT);
        toastStage.setAlwaysOnTop(true);

        Scene toastScene = new Scene(toastRoot, 314, Region.USE_COMPUTED_SIZE);
        toastScene.setFill(Color.TRANSPARENT);
        toastStage.setScene(toastScene);

        int stackIndex = activeNotificationStages.size();
        toastStage.setX(stage.getX() + stage.getWidth() - 330);
        toastStage.setY(stage.getY() + stage.getHeight() - 130 - stackIndex * 115);
        toastStage.show();
        activeNotificationStages.add(toastStage);

        toastContent.setOnMouseClicked(e -> {
            toastStage.close();
            activeNotificationStages.remove(toastStage);
            setCurrentConversation(conversationId, senderName);
        });
        closeBtn.setOnMouseClicked(e -> {
            e.consume();
            toastStage.close();
            activeNotificationStages.remove(toastStage);
        });

        // Slide-in animation
        toastRoot.setOpacity(0);
        toastRoot.setTranslateX(50);
        javafx.animation.TranslateTransition slideIn = new javafx.animation.TranslateTransition(
                javafx.util.Duration.millis(220), toastRoot);
        slideIn.setToX(0);
        javafx.animation.FadeTransition fadeIn = new javafx.animation.FadeTransition(
                javafx.util.Duration.millis(220), toastRoot);
        fadeIn.setToValue(1.0);
        new javafx.animation.ParallelTransition(slideIn, fadeIn).play();

        scheduler.schedule(() -> Platform.runLater(() -> {
            if (!toastStage.isShowing()) return;
            javafx.animation.FadeTransition fadeOut = new javafx.animation.FadeTransition(
                    javafx.util.Duration.millis(350), toastRoot);
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(ev -> {
                toastStage.close();
                activeNotificationStages.remove(toastStage);
            });
            fadeOut.play();
        }), 4000, TimeUnit.MILLISECONDS);
    }

    // ==================== SCENE ====================

    public Scene createScene() {
        return new Scene(root, 1400, 800);
    }

    public static class ReaderInfo {
        public final long userId;
        public final String username;

        public ReaderInfo(long userId, String username) {
            this.userId = userId;
            this.username = username;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            ReaderInfo other = (ReaderInfo) obj;
            return userId == other.userId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId);
        }
    }
}
