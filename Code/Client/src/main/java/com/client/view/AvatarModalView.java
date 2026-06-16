package com.client.view;

import com.client.controller.ChatController;
import com.client.util.ImageUtils;
import com.client.util.StyleConstants;
import javafx.application.Platform;
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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Avatar selection and cropping modal.
 * Extracted from ChatView to keep the main view manageable.
 */
public class AvatarModalView {

    private final Stage owner;
    private final ChatController chatController;
    private final Circle profileAvatarCircle;
    private Image currentAvatarImage;
    private final List<Image> previouslyUsedAvatars;

    private Image selectedAvatarImage;
    private StackPane activeOldAvatarContainer;
    private Slider zoomSlider;

    private double mouseAnchorX, mouseAnchorY;
    private double translateAnchorX, translateAnchorY;

    public AvatarModalView(Stage owner, ChatController chatController,
                           Circle profileAvatarCircle, Image currentAvatarImage,
                           List<Image> previouslyUsedAvatars) {
        this.owner = owner;
        this.chatController = chatController;
        this.profileAvatarCircle = profileAvatarCircle;
        this.currentAvatarImage = currentAvatarImage;
        this.previouslyUsedAvatars = previouslyUsedAvatars;
    }

    public void show() {
        Stage modal = new Stage();
        modal.initOwner(owner);
        modal.initModality(Modality.APPLICATION_MODAL);
        modal.setTitle("Chọn ảnh đại diện");

        BorderPane modalRoot = new BorderPane();
        modalRoot.setStyle("-fx-background-color: #1c1c1c;");

        // --- Header ---
        StackPane header = new StackPane();
        header.setPrefHeight(80);
        header.setMinHeight(80);
        header.setMaxHeight(80);
        header.setStyle("-fx-background-color: #1c1c1c; -fx-border-color: transparent transparent #333333 transparent; -fx-border-width: 0 0 1 0;");

        Label title = new Label("Chọn ảnh đại diện");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 30px; -fx-font-weight: bold;");
        StackPane.setAlignment(title, Pos.CENTER);

        Button closeBtn = new Button("✕");
        String closeBtnStyle = "-fx-background-color: #333333; -fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold; -fx-background-radius: 21px; -fx-min-width: 42px; -fx-max-width: 42px; -fx-min-height: 42px; -fx-max-height: 42px; -fx-cursor: hand;";
        closeBtn.setStyle(closeBtnStyle);
        closeBtn.setOnAction(ev -> modal.close());
        StackPane.setAlignment(closeBtn, Pos.CENTER_RIGHT);
        StackPane.setMargin(closeBtn, new Insets(0, 20, 0, 0));
        header.getChildren().addAll(title, closeBtn);
        modalRoot.setTop(header);

        // --- Scroll content ---
        VBox scrollContent = new VBox();
        scrollContent.setStyle("-fx-background-color: #1c1c1c;");
        scrollContent.setAlignment(Pos.TOP_CENTER);

        // Preview section
        VBox previewSection = new VBox(20);
        previewSection.setAlignment(Pos.CENTER);
        previewSection.setPadding(new Insets(30));

        StackPane previewContainer = new StackPane();
        previewContainer.setPrefSize(500, 500);
        previewContainer.setMaxSize(500, 500);
        previewContainer.setMinSize(500, 500);
        previewContainer.setStyle("-fx-background-color: #111111; -fx-background-radius: 16px;");

        Image previewImg = currentAvatarImage != null ? currentAvatarImage : ImageUtils.createDefaultAvatarImage();
        ImageView previewImage = new ImageView(previewImg);
        previewImage.setPreserveRatio(true);
        previewImage.setSmooth(true);

        Circle containerClip = new Circle(250, 250, 250);
        previewContainer.setClip(containerClip);
        previewContainer.getChildren().add(previewImage);

        // Zoom slider
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

        // --- Previously used avatars ---
        VBox oldAvatarSection = new VBox(18);
        oldAvatarSection.setPadding(new Insets(0, 30, 30, 30));

        Label oldAvatarTitle = new Label("Ảnh đại diện đã từng dùng");
        oldAvatarTitle.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 18px; -fx-font-weight: bold;");

        FlowPane oldAvatarList = new FlowPane();
        oldAvatarList.setHgap(14);
        oldAvatarList.setVgap(14);

        String containerDefaultStyle = "-fx-border-color: transparent; -fx-border-width: 3px; -fx-border-radius: 21px; -fx-background-radius: 21px; -fx-padding: 0; -fx-cursor: hand;";
        String containerSelectedStyle = "-fx-border-color: #1877f2; -fx-border-width: 3px; -fx-border-radius: 21px; -fx-background-radius: 21px; -fx-padding: 0; -fx-cursor: hand;";
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

            imgContainer.setOnMouseEntered(ev -> { imgContainer.setScaleX(1.05); imgContainer.setScaleY(1.05); });
            imgContainer.setOnMouseExited(ev -> { imgContainer.setScaleX(1.0); imgContainer.setScaleY(1.0); });
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

        // --- Upload / delete buttons ---
        HBox actionsSection = new HBox(16);
        actionsSection.setPadding(new Insets(0, 30, 30, 30));

        Button uploadBtn = new Button("Tải ảnh lên");
        String uploadBtnStyle = "-fx-background-color: #1877f2; -fx-text-fill: white; -fx-font-size: 15px; -fx-font-weight: bold; -fx-background-radius: 14px; -fx-pref-height: 52px; -fx-cursor: hand;";
        uploadBtn.setStyle(uploadBtnStyle);
        uploadBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(uploadBtn, Priority.ALWAYS);

        Button deleteBtn = new Button("Xóa avatar");
        String deleteBtnStyle = "-fx-background-color: #2d2d2d; -fx-text-fill: white; -fx-font-size: 15px; -fx-font-weight: bold; -fx-background-radius: 14px; -fx-pref-height: 52px; -fx-cursor: hand;";
        deleteBtn.setStyle(deleteBtnStyle);
        deleteBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(deleteBtn, Priority.ALWAYS);

        uploadBtn.setOnAction(evt -> {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg"));
            File file = chooser.showOpenDialog(modal);
            if (file != null) {
                if (activeOldAvatarContainer != null) {
                    activeOldAvatarContainer.setStyle(containerDefaultStyle);
                    activeOldAvatarContainer = null;
                }
                Image img = new Image(file.toURI().toString(), true);
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
            Image defaultImg = ImageUtils.createDefaultAvatarImage();
            selectedAvatarImage = defaultImg;
            previewImage.setImage(defaultImg);
            updatePreviewFit(previewImage, defaultImg);
        });

        actionsSection.getChildren().addAll(uploadBtn, deleteBtn);
        scrollContent.getChildren().add(actionsSection);

        // Scroll pane
        ScrollPane scrollPane = new ScrollPane(scrollContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background: #1c1c1c; -fx-background-color: #1c1c1c;");
        modalRoot.setCenter(scrollPane);

        // --- Footer ---
        HBox footer = new HBox(14);
        footer.setPadding(new Insets(20));
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setStyle("-fx-background-color: #1c1c1c; -fx-border-color: #333333 transparent transparent transparent; -fx-border-width: 1 0 0 0;");

        Button cancelBtn = new Button("Hủy");
        String cancelBtnStyle = "-fx-background-color: #333333; -fx-text-fill: white; -fx-font-size: 15px; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-min-width: 120px; -fx-min-height: 50px; -fx-cursor: hand;";
        cancelBtn.setStyle(cancelBtnStyle);
        cancelBtn.setOnAction(ev -> modal.close());

        Button saveBtn = new Button("Lưu");
        String saveBtnStyle = "-fx-background-color: #1877f2; -fx-text-fill: white; -fx-font-size: 15px; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-min-width: 120px; -fx-min-height: 50px; -fx-cursor: hand;";
        saveBtn.setStyle(saveBtnStyle);

        saveBtn.setOnAction(evt -> {
            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.TRANSPARENT);
            WritableImage croppedImage = previewContainer.snapshot(params, null);

            currentAvatarImage = croppedImage;
            profileAvatarCircle.setFill(new ImagePattern(currentAvatarImage));
            previouslyUsedAvatars.add(0, croppedImage);

            saveBtn.setDisable(true);
            saveBtn.setText("Đang lưu...");

            chatController.uploadAvatar(croppedImage,
                    successMsg -> {
                        saveBtn.setDisable(false);
                        saveBtn.setText("Lưu");
                        modal.close();
                    },
                    errMsg -> {
                        saveBtn.setDisable(false);
                        saveBtn.setText("Lưu");
                    });
        });

        footer.getChildren().addAll(cancelBtn, saveBtn);
        modalRoot.setBottom(footer);

        Scene scene = new Scene(modalRoot, 920, 850);
        modal.setScene(scene);
        modal.showAndWait();
    }

    private void updatePreviewFit(ImageView previewImage, Image image) {
        if (image == null) return;
        if (image.getWidth() <= 0 || image.getHeight() <= 0) {
            image.widthProperty().addListener((obs, oldW, newW) -> {
                if (newW.doubleValue() > 0)
                    Platform.runLater(() -> updatePreviewFit(previewImage, image));
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

    public Image getCurrentAvatarImage() { return currentAvatarImage; }
}
