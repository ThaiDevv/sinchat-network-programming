import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

public class LoginView {

    private final BorderPane root;

    public LoginView(Stage stage) {

        root = new BorderPane();
        root.setStyle("""
                -fx-background-color: black;
                """);

        VBox loginBox = new VBox(20);
        loginBox.setAlignment(Pos.CENTER);
        loginBox.setMaxWidth(600);

        // TITLE
        Label title = new Label("Đăng nhập vào SinChat");
        title.setWrapText(true);
        title.setAlignment(Pos.CENTER);
        title.setStyle("""
                -fx-font-size: 42px;
                -fx-font-weight: bold;
                -fx-text-fill: white;
                """);

        Label subtitle = new Label(
                "Đăng nhập vào tài khoản của bạn để tiếp tục"
        );

        subtitle.setStyle("""
                -fx-text-fill: #888;
                -fx-font-size: 16px;
                """);

        // USERNAME
        TextField usernameField = new TextField();
        usernameField.setPromptText("Tên người dùng / Email");

        usernameField.setStyle("""
                -fx-background-color: black;
                -fx-border-color: #444;
                -fx-border-width: 2px;
                -fx-border-radius: 24px;
                -fx-background-radius: 24px;
                -fx-text-fill: white;
                -fx-prompt-text-fill: #666;
                -fx-font-size: 18px;
                -fx-padding: 18px;
                """);

        usernameField.setPrefHeight(65);

        // PASSWORD
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Mật khẩu");

        passwordField.setStyle("""
                -fx-background-color: black;
                -fx-border-color: #444;
                -fx-border-width: 2px;
                -fx-border-radius: 24px;
                -fx-background-radius: 24px;
                -fx-text-fill: white;
                -fx-prompt-text-fill: #666;
                -fx-font-size: 18px;
                -fx-padding: 18px;
                """);

        passwordField.setPrefHeight(65);

        TextField visiblePassword = new TextField();
        visiblePassword.setManaged(false);
        visiblePassword.setVisible(false);

        visiblePassword.setStyle(passwordField.getStyle());
        visiblePassword.setPrefHeight(65);

        Button eyeButton = new Button("👁");

        eyeButton.setStyle("""
                -fx-background-color: transparent;
                -fx-font-size: 18px;
                -fx-cursor: hand;
                """);

        StackPane passwordPane = new StackPane();

        passwordPane.getChildren().addAll(
                passwordField,
                visiblePassword,
                eyeButton
        );

        StackPane.setAlignment(eyeButton, Pos.CENTER_RIGHT);
        StackPane.setMargin(eyeButton, new Insets(0, 20, 0, 0));

        eyeButton.setOnAction(e -> {

            if (passwordField.isVisible()) {

                visiblePassword.setText(passwordField.getText());

                passwordField.setVisible(false);
                passwordField.setManaged(false);

                visiblePassword.setVisible(true);
                visiblePassword.setManaged(true);

            }

            else {

                passwordField.setText(visiblePassword.getText());

                visiblePassword.setVisible(false);
                visiblePassword.setManaged(false);

                passwordField.setVisible(true);
                passwordField.setManaged(true);
            }
        });

        // LOGIN BUTTON
        Button loginButton = new Button("Đăng nhập");

        loginButton.setStyle("""
                -fx-background-color: white;
                -fx-text-fill: black;
                -fx-font-size: 20px;
                -fx-font-weight: bold;
                -fx-background-radius: 24px;
                -fx-cursor: hand;
                """);

        loginButton.setPrefHeight(65);
        loginButton.setMaxWidth(Double.MAX_VALUE);

        // Login action → switch to ChatView
        loginButton.setOnAction(e -> {
            ChatView chatView = new ChatView(stage);
            stage.setScene(chatView.createScene());
        });

        // LINKS
        Hyperlink forgotPassword = new Hyperlink(
                "Quên mật khẩu?"
        );

        Hyperlink registerLink = new Hyperlink(
                "Chưa có tài khoản?"
        );

        forgotPassword.setStyle("""
                -fx-text-fill: #aaa;
                -fx-border-color: transparent;
                """);

        registerLink.setStyle("""
                -fx-text-fill: #aaa;
                -fx-border-color: transparent;
                """);

        Label footer = new Label(
                "Ứng dụng Chat (GUI) via TCP"
        );

        footer.setStyle("""
                -fx-text-fill: #555;
                -fx-font-size: 13px;
                """);

        VBox.setMargin(footer, new Insets(30, 0, 0, 0));

        loginBox.getChildren().addAll(
                title,
                subtitle,
                usernameField,
                passwordPane,
                loginButton,
                forgotPassword,
                registerLink,
                footer
        );

        root.setCenter(loginBox);

        BorderPane.setAlignment(loginBox, Pos.CENTER);
    }

    public Scene createScene() {
        return new Scene(root, 1400, 800);
    }
}