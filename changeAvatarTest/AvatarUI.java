import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;

import java.io.File;

public class AvatarUI extends Application {

    private Image currentAvatarImage =
            new Image("file:avatarMacDinh.jpg");

    private Circle currentAvatarCircle;

    private Label toast;

    private Image selectedImage;

    // Drag and Zoom state
    private double mouseAnchorX;
    private double mouseAnchorY;
    private double translateAnchorX;
    private double translateAnchorY;

    private StackPane activeOldAvatarContainer = null;
    private Slider zoomSlider;

    @Override
    public void start(Stage primaryStage) {
        // ROOT
        StackPane root = new StackPane();
        root.setStyle("-fx-background-color: #111111;");

        // PROFILE AREA
        VBox profileArea = new VBox(20);
        profileArea.setAlignment(Pos.CENTER);

        currentAvatarCircle = new Circle(90);
        currentAvatarCircle.setFill(new ImagePattern(currentAvatarImage));
        currentAvatarCircle.setStroke(Color.web("#333333"));
        currentAvatarCircle.setStrokeWidth(4);

        Button changeAvatarBtn = new Button("Đổi avatar");
        changeAvatarBtn.setStyle("""
                -fx-background-color: #1877f2;
                -fx-text-fill: white;
                -fx-font-size: 16px;
                -fx-font-weight: bold;
                -fx-background-radius: 14px;
                -fx-padding: 14 26;
                -fx-cursor: hand;
                """);
        changeAvatarBtn.setOnMouseEntered(e -> changeAvatarBtn.setStyle("""
                -fx-background-color: #1565c0;
                -fx-text-fill: white;
                -fx-font-size: 16px;
                -fx-font-weight: bold;
                -fx-background-radius: 14px;
                -fx-padding: 14 26;
                -fx-cursor: hand;
                """));
        changeAvatarBtn.setOnMouseExited(e -> changeAvatarBtn.setStyle("""
                -fx-background-color: #1877f2;
                -fx-text-fill: white;
                -fx-font-size: 16px;
                -fx-font-weight: bold;
                -fx-background-radius: 14px;
                -fx-padding: 14 26;
                -fx-cursor: hand;
                """));

        profileArea.getChildren().addAll(currentAvatarCircle, changeAvatarBtn);

        // TOAST
        toast = new Label("Thành công đổi avatar");
        toast.setVisible(false);
        toast.setStyle("""
                -fx-background-color: #1f883d;
                -fx-text-fill: white;
                -fx-font-size: 14px;
                -fx-font-weight: bold;
                -fx-padding: 14 22;
                -fx-background-radius: 14px;
                """);

        StackPane.setAlignment(toast, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(toast, new Insets(0, 30, 30, 0));

        root.getChildren().addAll(profileArea, toast);

        changeAvatarBtn.setOnAction(e -> openAvatarModal(primaryStage));

        Scene scene = new Scene(root, 1200, 800);
        primaryStage.setTitle("Đổi Avatar");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void openAvatarModal(Stage owner) {
        Stage modal = new Stage();
        modal.initOwner(owner);
        modal.initModality(Modality.APPLICATION_MODAL);
        modal.setTitle("Chọn ảnh đại diện");

        BorderPane modalRoot = new BorderPane();
        modalRoot.setStyle("-fx-background-color: #1c1c1c;");

        // HEADER (sticky at top)
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
                -fx-min-width: 42px;
                -fx-max-width: 42px;
                -fx-min-height: 42px;
                -fx-max-height: 42px;
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

        // SCROLL CONTENT
        VBox scrollContent = new VBox();
        scrollContent.setStyle("-fx-background-color: #1c1c1c;");
        scrollContent.setAlignment(Pos.TOP_CENTER);

        // 1. PREVIEW SECTION
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

        ImageView previewImage = new ImageView(currentAvatarImage);
        previewImage.setPreserveRatio(true);
        previewImage.setSmooth(true);

        // Circular clip inside 500x500 container
        Circle containerClip = new Circle(250, 250, 250);
        previewContainer.setClip(containerClip);
        previewContainer.getChildren().add(previewImage);

        // Slider for Zoom
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

        // Drag and Zoom handlers on previewContainer
        previewContainer.setOnMousePressed(evt -> {
            mouseAnchorX = evt.getSceneX();
            mouseAnchorY = evt.getSceneY();
            translateAnchorX = previewImage.getTranslateX();
            translateAnchorY = previewImage.getTranslateY();
        });

        previewContainer.setOnMouseDragged(evt -> {
            double deltaX = evt.getSceneX() - mouseAnchorX;
            double deltaY = evt.getSceneY() - mouseAnchorY;
            previewImage.setTranslateX(translateAnchorX + deltaX);
            previewImage.setTranslateY(translateAnchorY + deltaY);
            clampImagePosition(previewImage);
        });

        selectedImage = currentAvatarImage;
        updatePreviewFit(previewImage, selectedImage);

        previewSection.getChildren().addAll(previewContainer, zoomRow);
        scrollContent.getChildren().add(previewSection);

        // 2. OLD AVATAR SECTION
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

        String[] oldAvatars = {
                "https://i.pravatar.cc/300?img=1",
                "https://i.pravatar.cc/300?img=2",
                "https://i.pravatar.cc/300?img=3",
                "https://i.pravatar.cc/300?img=4",
                "https://i.pravatar.cc/300?img=5"
        };

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

        activeOldAvatarContainer = null; // reset

        for (String url : oldAvatars) {
            Image img = new Image(url, true); // background load
            ImageView oldAvatarView = new ImageView(img);
            oldAvatarView.setFitWidth(90);
            oldAvatarView.setFitHeight(90);
            oldAvatarView.setPreserveRatio(false);

            // 18px rounded corner clipping (arc width/height = 2 * radius = 36)
            Rectangle rClip = new Rectangle(90, 90);
            rClip.setArcWidth(36);
            rClip.setArcHeight(36);
            oldAvatarView.setClip(rClip);

            StackPane imgContainer = new StackPane(oldAvatarView);
            imgContainer.setPrefSize(96, 96);
            imgContainer.setMaxSize(96, 96);
            imgContainer.setStyle(containerDefaultStyle);

            // Hover effect
            imgContainer.setOnMouseEntered(ev -> {
                imgContainer.setScaleX(1.05);
                imgContainer.setScaleY(1.05);
            });
            imgContainer.setOnMouseExited(ev -> {
                imgContainer.setScaleX(1.0);
                imgContainer.setScaleY(1.0);
            });

            // Select effect
            imgContainer.setOnMouseClicked(ev -> {
                if (activeOldAvatarContainer != null) {
                    activeOldAvatarContainer.setStyle(containerDefaultStyle);
                }
                activeOldAvatarContainer = imgContainer;
                imgContainer.setStyle(containerSelectedStyle);

                selectedImage = img;
                previewImage.setImage(img);
                updatePreviewFit(previewImage, img);
            });

            oldAvatarList.getChildren().add(imgContainer);
        }

        oldAvatarSection.getChildren().addAll(oldAvatarTitle, oldAvatarList);
        scrollContent.getChildren().add(oldAvatarSection);

        // 3. ACTIONS SECTION
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

        // Upload action
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
                Image img = new Image(file.toURI().toString());
                selectedImage = img;
                previewImage.setImage(img);
                updatePreviewFit(previewImage, img);
            }
        });

        // Delete action (sets default avatar)
        deleteBtn.setOnAction(evt -> {
            if (activeOldAvatarContainer != null) {
                activeOldAvatarContainer.setStyle(containerDefaultStyle);
                activeOldAvatarContainer = null;
            }
            Image defaultImg = new Image("file:avatarMacDinh.jpg");
            selectedImage = defaultImg;
            previewImage.setImage(defaultImg);
            updatePreviewFit(previewImage, defaultImg);
        });

        actionsSection.getChildren().addAll(uploadBtn, deleteBtn);
        scrollContent.getChildren().add(actionsSection);

        // MIDDLE SCROLLPANE
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

        // FOOTER (sticky at bottom)
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
            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.TRANSPARENT);
            WritableImage croppedImage = previewContainer.snapshot(params, null);

            currentAvatarImage = croppedImage;
            currentAvatarCircle.setFill(new ImagePattern(currentAvatarImage));
            showToast();
            modal.close();
        });

        footer.getChildren().addAll(cancelBtn, saveBtn);
        modalRoot.setBottom(footer);

        Scene scene = new Scene(modalRoot, 920, 850);
        modal.setScene(scene);
        modal.showAndWait();
    }

    private void updatePreviewFit(ImageView previewImage, Image image) {
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            return;
        }

        double targetSize = 500;
        double scale = Math.max(targetSize / image.getWidth(), targetSize / image.getHeight());

        previewImage.setFitWidth(image.getWidth() * scale);
        previewImage.setFitHeight(image.getHeight() * scale);

        // Reset transformations when loading a new image
        previewImage.setTranslateX(0);
        previewImage.setTranslateY(0);
        previewImage.setScaleX(1.0);
        previewImage.setScaleY(1.0);

        if (zoomSlider != null) {
            zoomSlider.setValue(1.0);
        }
    }

    private void clampImagePosition(ImageView previewImage) {
        double scaleX = previewImage.getScaleX();
        double scaleY = previewImage.getScaleY();
        double w = previewImage.getFitWidth() * scaleX;
        double h = previewImage.getFitHeight() * scaleY;

        double maxX = Math.max(0, w / 2 - 250);
        double minX = -maxX;
        double maxY = Math.max(0, h / 2 - 250);
        double minY = -maxY;

        double tx = previewImage.getTranslateX();
        double ty = previewImage.getTranslateY();

        if (tx < minX) previewImage.setTranslateX(minX);
        if (tx > maxX) previewImage.setTranslateX(maxX);
        if (ty < minY) previewImage.setTranslateY(minY);
        if (ty > maxY) previewImage.setTranslateY(maxY);
    }

    private void showToast() {
        toast.setVisible(true);
        new Thread(() -> {
            try {
                Thread.sleep(2500);
            } catch (Exception ignored) {
            }
            javafx.application.Platform.runLater(() -> {
                toast.setVisible(false);
            });
        }).start();
    }

    public static void main(String[] args) {
        launch(args);
    }
}