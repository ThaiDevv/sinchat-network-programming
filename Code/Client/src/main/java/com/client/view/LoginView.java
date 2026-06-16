package com.client.view;

import com.client.controller.AuthController;
import com.client.util.StyleConstants;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Login/Register/ForgotPassword view.
 * UI layout only — all TCP calls are delegated to {@link AuthController}.
 */
public class LoginView {

    private final BorderPane root;
    private final Stage stage;
    private final AuthController authController;

    private String resetCode;
    private String resetAccount;

    public LoginView(Stage stage) {
        this.stage = stage;
        this.authController = new AuthController();
        root = new BorderPane();
        root.setStyle("-fx-background-color: black;");
        showLoginView("");
    }

    // ==================== LOGIN ====================

    private void showLoginView(String username) {
        VBox loginBox = createFormBox();

        Label title = createTitle("Đăng nhập vào SinChat");
        Label subtitle = createSubtitle("Đăng nhập vào tài khoản của bạn để tiếp tục");

        TextField usernameField = createInput("Tên người dùng / Email");
        usernameField.setText(username);

        PasswordField passwordField = createPasswordInput("Mật khẩu");
        TextField visiblePassword = createInput("Mật khẩu");
        visiblePassword.setManaged(false);
        visiblePassword.setVisible(false);

        Button eyeButton = createPasswordToggleButton();
        StackPane passwordPane = createPasswordPane(passwordField, visiblePassword, eyeButton);

        Label message = createMessageLabel();

        Button loginButton = createPrimaryButton("Đăng nhập");
        loginButton.setOnAction(e -> {
            String account = usernameField.getText().trim();
            String password = getPasswordValue(passwordField, visiblePassword);

            if (account.isEmpty() || password.isBlank()) {
                setMessage(message, "Vui lòng nhập username và mật khẩu.", "#ff7777");
                return;
            }

            runTcpCall(loginButton, message, "Đang đăng nhập...",
                    () -> authController.login(account, password),
                    response -> {
                        if (response.isSuccess()) {
                            long userId = response.userId() != null ? response.userId() : 0;
                            ChatView chatView = new ChatView(stage, userId);
                            stage.setScene(chatView.createScene());
                            return;
                        }
                        setMessage(message, response.message(), "#ff7777");
                    });
        });

        // Enter key triggers login
        usernameField.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) loginButton.fire(); });
        passwordField.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) loginButton.fire(); });
        visiblePassword.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) loginButton.fire(); });

        Hyperlink forgotPassword = createLink("Quên mật khẩu?");
        forgotPassword.setOnAction(e -> showForgotAccountView(usernameField.getText().trim()));

        Hyperlink registerLink = createLink("Chưa có tài khoản?");
        registerLink.setOnAction(e -> showRegisterView(usernameField.getText().trim()));

        Label footer = new Label("Ứng dụng Chat (GUI) via TCP");
        footer.setStyle("-fx-text-fill: #555; -fx-font-size: 13px;");
        VBox.setMargin(footer, new Insets(30, 0, 0, 0));

        loginBox.getChildren().addAll(title, subtitle, usernameField, passwordPane, message,
                loginButton, forgotPassword, registerLink, footer);
        setCenter(loginBox);
    }

    // ==================== REGISTER ====================

    private void showRegisterView(String username) {
        VBox registerBox = createFormBox();

        Label title = createTitle("Đăng ký SinChat");
        Label subtitle = createSubtitle("Tạo tài khoản mới để bắt đầu chat.");

        TextField usernameField = createInput("Tên người dùng");
        usernameField.setText(username);

        TextField emailField = createInput("Email");
        PasswordField passwordField = createPasswordInput("Mật khẩu");
        PasswordField confirmPasswordField = createPasswordInput("Nhập lại mật khẩu");
        Label message = createMessageLabel();

        Button registerButton = createPrimaryButton("Đăng ký");
        registerButton.setOnAction(e -> {
            String account = usernameField.getText().trim();
            String email = emailField.getText().trim();
            String password = passwordField.getText();
            String confirmPassword = confirmPasswordField.getText();

            if (account.isEmpty() || email.isEmpty() || password.isBlank()) {
                setMessage(message, "Vui lòng nhập đủ username, email và mật khẩu.", "#ff7777");
                return;
            }
            if (!password.equals(confirmPassword)) {
                setMessage(message, "Mật khẩu nhập lại không khớp.", "#ff7777");
                return;
            }

            runTcpCall(registerButton, message, "Đang đăng ký...",
                    () -> authController.register(account, password, email),
                    response -> setMessage(message, response.message(),
                            response.isSuccess() ? "#4ade80" : "#ff7777"));
        });

        Hyperlink backToLogin = createLink("Quay lại đăng nhập");
        backToLogin.setOnAction(e -> showLoginView(usernameField.getText().trim()));

        registerBox.getChildren().addAll(title, subtitle, usernameField, emailField,
                passwordField, confirmPasswordField, message, registerButton, backToLogin);
        setCenter(registerBox);
    }

    // ==================== FORGOT PASSWORD ====================

    private void showForgotAccountView(String username) {
        VBox forgotBox = createFormBox();

        Label title = createTitle("Quên mật khẩu");
        Label subtitle = createSubtitle("Nhập username để server tạo mã xác nhận.");

        TextField accountField = createInput("Tên người dùng");
        accountField.setText(username);

        Label message = createMessageLabel();

        Button createCodeButton = createPrimaryButton("Tạo mã xác nhận");
        createCodeButton.setOnAction(e -> {
            String account = accountField.getText().trim();
            if (account.isEmpty()) {
                setMessage(message, "Vui lòng nhập username.", "#ff7777");
                return;
            }

            runTcpCall(createCodeButton, message, "Đang tạo mã...",
                    () -> authController.requestPasswordResetCode(account),
                    response -> {
                        if (response.isSuccess()) {
                            resetAccount = account;
                            resetCode = response.code();
                            showVerifyCodeView();
                            return;
                        }
                        setMessage(message, response.message(), "#ff7777");
                    });
        });

        Hyperlink backToLogin = createLink("Quay lại đăng nhập");
        backToLogin.setOnAction(e -> showLoginView(accountField.getText().trim()));

        forgotBox.getChildren().addAll(title, subtitle, accountField, message, createCodeButton, backToLogin);
        setCenter(forgotBox);
    }

    private void showVerifyCodeView() {
        VBox verifyBox = createFormBox();

        Label title = createTitle("Xác nhận tài khoản");
        Label subtitle = createSubtitle(
                hasResetCode()
                        ? "Server đang trả mã demo trên UI. Sau này có thể chuyển sang gửi email."
                        : "Nhập mã xác nhận bạn nhận được.");

        Label accountLabel = createSubtitle("Tài khoản: " + resetAccount);

        TextField codeField = createInput("Nhập mã xác nhận");
        Label message = createMessageLabel();

        Button confirmButton = createPrimaryButton("Xác nhận");
        confirmButton.setOnAction(e -> {
            String enteredCode = codeField.getText().trim();
            if (enteredCode.isEmpty()) {
                setMessage(message, "Vui lòng nhập mã xác nhận.", "#ff7777");
                return;
            }
            showNewPasswordView(enteredCode);
        });

        Hyperlink changeAccount = createLink("Nhập lại username");
        changeAccount.setOnAction(e -> showForgotAccountView(resetAccount));

        verifyBox.getChildren().addAll(title, subtitle, accountLabel);
        if (hasResetCode()) {
            verifyBox.getChildren().add(createCodeLabel(resetCode));
        }
        verifyBox.getChildren().addAll(codeField, message, confirmButton, changeAccount);
        setCenter(verifyBox);
    }

    private void showNewPasswordView(String code) {
        VBox passwordBox = createFormBox();

        Label title = createTitle("Đặt mật khẩu mới");
        Label subtitle = createSubtitle("Nhập mật khẩu mới cho tài khoản: " + resetAccount);

        PasswordField newPasswordField = createPasswordInput("Mật khẩu mới");
        PasswordField confirmPasswordField = createPasswordInput("Nhập lại mật khẩu mới");
        Label message = createMessageLabel();

        Button saveButton = createPrimaryButton("Đổi mật khẩu");
        saveButton.setOnAction(e -> {
            String newPassword = newPasswordField.getText();
            String confirmPassword = confirmPasswordField.getText();

            if (newPassword.isBlank()) {
                setMessage(message, "Vui lòng nhập mật khẩu mới.", "#ff7777");
                return;
            }
            if (newPassword.length() < 6) {
                setMessage(message, "Mật khẩu mới phải có ít nhất 6 ký tự.", "#ff7777");
                return;
            }
            if (!newPassword.equals(confirmPassword)) {
                setMessage(message, "Mật khẩu nhập lại không khớp.", "#ff7777");
                return;
            }

            runTcpCall(saveButton, message, "Đang đổi mật khẩu...",
                    () -> authController.resetPassword(code, newPassword),
                    response -> {
                        if (response.isSuccess()) {
                            resetCode = null;
                            setMessage(message, response.message(), "#4ade80");
                            return;
                        }
                        setMessage(message, response.message(), "#ff7777");
                    });
        });

        Hyperlink backToLogin = createLink("Quay lại đăng nhập");
        backToLogin.setOnAction(e -> showLoginView(resetAccount == null ? "" : resetAccount));

        passwordBox.getChildren().addAll(title, subtitle, newPasswordField,
                confirmPasswordField, message, saveButton, backToLogin);
        setCenter(passwordBox);
    }

    // ==================== UI COMPONENT FACTORIES ====================

    private StackPane createPasswordPane(PasswordField passwordField, TextField visiblePassword, Button eyeButton) {
        StackPane passwordPane = new StackPane(passwordField, visiblePassword, eyeButton);
        StackPane.setAlignment(eyeButton, Pos.CENTER_RIGHT);
        StackPane.setMargin(eyeButton, new Insets(0, 18, 0, 0));

        eyeButton.setOnAction(e -> {
            if (passwordField.isVisible()) {
                visiblePassword.setText(passwordField.getText());
                passwordField.setVisible(false);
                passwordField.setManaged(false);
                visiblePassword.setVisible(true);
                visiblePassword.setManaged(true);
                eyeButton.setText("Ẩn");
            } else {
                passwordField.setText(visiblePassword.getText());
                visiblePassword.setVisible(false);
                visiblePassword.setManaged(false);
                passwordField.setVisible(true);
                passwordField.setManaged(true);
                eyeButton.setText("Hiện");
            }
        });
        return passwordPane;
    }

    private Button createPasswordToggleButton() {
        Button eyeButton = new Button("Hiện");
        eyeButton.setStyle(StyleConstants.STYLE_EYE_NORMAL);
        eyeButton.focusedProperty().addListener((obs, was, isFocused) ->
                eyeButton.setStyle(isFocused ? StyleConstants.STYLE_EYE_FOCUSED : StyleConstants.STYLE_EYE_NORMAL));
        return eyeButton;
    }

    private Label createCodeLabel(String code) {
        Label codeLabel = new Label("Mã xác nhận: " + code);
        codeLabel.setAlignment(Pos.CENTER);
        codeLabel.setMaxWidth(Double.MAX_VALUE);
        codeLabel.setStyle("""
                -fx-background-color: #111;
                -fx-border-color: #7c5cfc;
                -fx-border-width: 2px;
                -fx-border-radius: 18px;
                -fx-background-radius: 18px;
                -fx-text-fill: white;
                -fx-font-size: 24px;
                -fx-font-weight: bold;
                -fx-padding: 18px;
                """);
        return codeLabel;
    }

    private VBox createFormBox() {
        VBox box = new VBox(20);
        box.setAlignment(Pos.CENTER);
        box.setMaxWidth(600);
        box.setPadding(new Insets(30));
        return box;
    }

    private Label createTitle(String text) {
        Label title = new Label(text);
        title.setWrapText(true);
        title.setAlignment(Pos.CENTER);
        title.setStyle("-fx-font-size: 42px; -fx-font-weight: bold; -fx-text-fill: white;");
        return title;
    }

    private Label createSubtitle(String text) {
        Label subtitle = new Label(text);
        subtitle.setWrapText(true);
        subtitle.setAlignment(Pos.CENTER);
        subtitle.setStyle("-fx-text-fill: #888; -fx-font-size: 16px;");
        return subtitle;
    }

    private void applyFocusHighlight(Control field) {
        field.focusedProperty().addListener((obs, wasFocused, isFocused) ->
                field.setStyle(isFocused ? StyleConstants.STYLE_INPUT_FOCUSED : StyleConstants.STYLE_INPUT_NORMAL));
    }

    private TextField createInput(String prompt) {
        TextField input = new TextField();
        input.setPromptText(prompt);
        input.setStyle(StyleConstants.STYLE_INPUT_NORMAL);
        input.setPrefHeight(65);
        applyFocusHighlight(input);
        return input;
    }

    private PasswordField createPasswordInput(String prompt) {
        PasswordField input = new PasswordField();
        input.setPromptText(prompt);
        input.setStyle(StyleConstants.STYLE_INPUT_NORMAL);
        input.setPrefHeight(65);
        applyFocusHighlight(input);
        return input;
    }

    private Button createPrimaryButton(String text) {
        Button button = new Button(text);
        button.setStyle(StyleConstants.STYLE_BTN_NORMAL);
        button.setPrefHeight(65);
        button.setMaxWidth(Double.MAX_VALUE);
        button.focusedProperty().addListener((obs, was, isFocused) ->
                button.setStyle(isFocused ? StyleConstants.STYLE_BTN_FOCUSED : StyleConstants.STYLE_BTN_NORMAL));
        return button;
    }

    private Hyperlink createLink(String text) {
        Hyperlink link = new Hyperlink(text);
        link.setStyle(StyleConstants.STYLE_LINK_NORMAL);
        link.focusedProperty().addListener((obs, was, isFocused) ->
                link.setStyle(isFocused ? StyleConstants.STYLE_LINK_FOCUSED : StyleConstants.STYLE_LINK_NORMAL));
        return link;
    }

    private Label createMessageLabel() {
        Label label = new Label("");
        label.setWrapText(true);
        label.setAlignment(Pos.CENTER);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setStyle("-fx-text-fill: transparent; -fx-font-size: 14px;");
        return label;
    }

    // ==================== HELPERS ====================

    private String getPasswordValue(PasswordField passwordField, TextField visiblePassword) {
        return passwordField.isVisible() ? passwordField.getText() : visiblePassword.getText();
    }

    private boolean hasResetCode() {
        return resetCode != null && !resetCode.isBlank();
    }

    private void setMessage(Label label, String text, String color) {
        label.setText(text);
        label.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 14px;");
    }

    private void setCenter(VBox content) {
        root.setCenter(content);
        BorderPane.setAlignment(content, Pos.CENTER);
    }

    private void runTcpCall(Button actionButton, Label message, String loadingText,
                            AuthController.TcpCall tcpCall,
                            java.util.function.Consumer<com.client.model.ApiResponse> onComplete) {
        actionButton.setDisable(true);
        setMessage(message, loadingText, "#aaa");

        authController.runTcpCall(
                null,
                null,
                tcpCall,
                response -> Platform.runLater(() -> {
                    actionButton.setDisable(false);
                    onComplete.accept(response);
                }));
    }

    public Scene createScene() {
        return new Scene(root, 1400, 800);
    }
}
