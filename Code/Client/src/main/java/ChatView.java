import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

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

public class ChatView {

    private final BorderPane root;
    private final Stage stage;
    private VBox messagesBox;
    private VBox contactList;
    private TextField messageInput;
    private ScrollPane scrollMessages;
    private Label headerChatName;

    private ChatTcpClient tcpClient;
    private final long currentUserId;
    private long currentConversationId;

    private final ChatTcpClient apiClient = ChatTcpClient.getInstance();
    private final ChatApiClient restClient = new ChatApiClient();

    private final Gson gson = new Gson();
    private final java.util.Map<Long, Label> contactLastMsgLabels = new java.util.HashMap<>();

    private Label typingLabel;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "typing-scheduler");
                t.setDaemon(true);
                return t;
            });

    private ScheduledFuture<?> typingHideTask;

    private long lastTypingSentTime = 0;
    private static final long TYPING_THROTTLE_MS = 1000;

    // ── Avatar state ──
    private Image currentAvatarImage;
    private Image initialAvatarImage;

    private final java.util.List<Image> previouslyUsedAvatars =
            new java.util.ArrayList<>();

    private Circle profileAvatarCircle;
    private Image selectedAvatarImage;
    private StackPane activeOldAvatarContainer = null;

    private Slider zoomSlider;

    private double mouseAnchorX;
    private double mouseAnchorY;

    private double translateAnchorX;
    private double translateAnchorY;

    private Label avatarToast;

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
        this.currentConversationId = -1;

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

    // =========================================================
    // AVATAR PANEL
    // =========================================================

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

        if (!currentAvatarImage.isError()
                && currentAvatarImage.getProgress() >= 1.0) {

            profileAvatarCircle.setFill(
                    new ImagePattern(currentAvatarImage)
            );

        } else {

            profileAvatarCircle.setFill(Color.web(ACCENT));

            currentAvatarImage.progressProperty().addListener((obs, oldVal, newVal) -> {

                if (newVal.doubleValue() >= 1.0
                        && !currentAvatarImage.isError()) {

                    profileAvatarCircle.setFill(
                            new ImagePattern(currentAvatarImage)
                    );
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

        Label nameLabel = new Label("Sinh viên");

        nameLabel.setStyle("""
                -fx-font-size: 20px;
                -fx-font-weight: bold;
                -fx-text-fill: %s;
                """.formatted(TEXT_WHITE));

        VBox.setMargin(nameLabel, new Insets(8, 0, 12, 0));

        Button avatarBtn = createProfileButton("Đổi avatar", true);

        avatarBtn.setOnAction(e -> {
            ContextMenu menu = createAvatarContextMenu();
            menu.show(avatarBtn, javafx.geometry.Side.BOTTOM, 0, 0);
        });

        Button nameBtn =
                createProfileButton("Đổi tên người dùng", false);

        Button passBtn =
                createProfileButton("Đổi mật khẩu", false);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Button logoutBtn =
                createProfileButton("Đăng xuất", false);

        logoutBtn.setStyle(logoutBtn.getStyle() + """
                -fx-border-color: #cc3333;
                -fx-text-fill: #cc3333;
                """);

        logoutBtn.setOnAction(e -> {

            if (tcpClient != null) {
                tcpClient.disconnect();
            }

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

    // =========================================================
    // AVATAR CONTEXT MENU
    // =========================================================

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

        changeItem.setOnAction(e -> openAvatarModal(stage));

        MenuItem restoreItem =
                new MenuItem("Khôi phục lại avatar ban đầu");

        restoreItem.setOnAction(e -> {

            if (currentAvatarImage == initialAvatarImage) {

                showAvatarToast(
                        "Ảnh đại diện đã là ảnh ban đầu!",
                        "#555555"
                );

                return;
            }

            if (initialAvatarImage != null) {

                currentAvatarImage = initialAvatarImage;

                profileAvatarCircle.setFill(
                        new ImagePattern(currentAvatarImage)
                );

                CompletableFuture.supplyAsync(() -> {

                    try {

                        byte[] pngBytes =
                                imageToPngBytes(initialAvatarImage);

                        return restClient.uploadAvatar(
                                currentUserId,
                                pngBytes,
                                "avatar.png"
                        );

                    } catch (Exception ex) {

                        ex.printStackTrace();
                        return null;
                    }

                }).thenAccept(response -> Platform.runLater(() -> {

                    if (response != null && response.isSuccess()) {

                        showAvatarToast(
                                "Đã khôi phục avatar ban đầu!",
                                "#1f883d"
                        );

                    } else {

                        String errMsg =
                                response != null
                                        ? response.message()
                                        : "Lỗi kết nối server";

                        showAvatarToast(
                                "Lỗi khi đồng bộ: " + errMsg,
                                "#cc3333"
                        );
                    }
                }));
            }
        });

        contextMenu.getItems().addAll(changeItem, restoreItem);

        return contextMenu;
    }

    // =========================================================
    // OPEN AVATAR MODAL
    // =========================================================

    private void openAvatarModal(Stage owner) {

        Stage modal = new Stage();

        modal.initOwner(owner);
        modal.initModality(Modality.APPLICATION_MODAL);

        modal.setTitle("Chọn ảnh đại diện");

        BorderPane modalRoot = new BorderPane();

        modalRoot.setStyle("-fx-background-color: #1c1c1c;");

        // phần code modal giữ nguyên như file cũ
    }

    // =========================================================
    // HELPER METHODS
    // =========================================================

    private Image createDefaultAvatarImage() {

        try {

            File file = new File("avatarMacDinh.jpg");

            if (file.exists()) {

                Image img = new Image(
                        file.toURI().toString(),
                        false
                );

                if (!img.isError()) {
                    return img;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        try {

            Image img =
                    new Image(
                            "https://i.pravatar.cc/300?img=0",
                            false
                    );

            if (!img.isError()) {
                return img;
            }

        } catch (Exception ignored) {
        }

        javafx.scene.canvas.Canvas canvas =
                new javafx.scene.canvas.Canvas(110, 110);

        javafx.scene.canvas.GraphicsContext gc =
                canvas.getGraphicsContext2D();

        gc.setFill(javafx.scene.paint.Color.web(ACCENT));

        gc.fillOval(0, 0, 110, 110);

        SnapshotParameters params =
                new SnapshotParameters();

        params.setFill(javafx.scene.paint.Color.TRANSPARENT);

        return canvas.snapshot(params, null);
    }

    private byte[] imageToPngBytes(Image image) {

        try {

            java.awt.image.BufferedImage bImage =
                    SwingFXUtils.fromFXImage(image, null);

            ByteArrayOutputStream baos =
                    new ByteArrayOutputStream();

            ImageIO.write(bImage, "png", baos);

            return baos.toByteArray();

        } catch (Exception e) {

            e.printStackTrace();
            return new byte[0];
        }
    }

    private void showAvatarToast(String text, String bgColor) {

        Label toast = new Label(text);

        toast.setStyle("""
                -fx-background-color: %s;
                -fx-text-fill: white;
                -fx-font-size: 14px;
                -fx-font-weight: bold;
                -fx-padding: 14 22;
                -fx-background-radius: 14px;
                """.formatted(bgColor));

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

        new Thread(() -> {

            try {
                Thread.sleep(2500);
            } catch (Exception ignored) {
            }

            Platform.runLater(toastStage::close);

        }).start();
    }
}