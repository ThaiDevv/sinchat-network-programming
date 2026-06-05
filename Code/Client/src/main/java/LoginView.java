import java.util.concurrent.CompletableFuture;
import javafx.scene.input.KeyCode;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class LoginView {

    private final BorderPane root;
    private final Stage stage;
    private final ChatTcpClient tcpClient = ChatTcpClient.getInstance();

    private String resetCode;
    private String resetAccount;

    public LoginView(Stage stage) {
        this.stage = stage;
        root = new BorderPane();
        root.setStyle("-fx-background-color: black;");
        showLoginView("");
    }

    private void showLoginView(String username) {
        VBox loginBox = createFormBox();

        Label title = createTitle("\u0110\u0103ng nh\u1eadp v\u00e0o SinChat");
        Label subtitle = createSubtitle(
                "\u0110\u0103ng nh\u1eadp v\u00e0o t\u00e0i kho\u1ea3n c\u1ee7a b\u1ea1n \u0111\u1ec3 ti\u1ebfp t\u1ee5c"
        );

        TextField usernameField = createInput("T\u00ean ng\u01b0\u1eddi d\u00f9ng / Email");
        usernameField.setText(username);

        PasswordField passwordField = createPasswordInput("M\u1eadt kh\u1ea9u");
        TextField visiblePassword = createInput("M\u1eadt kh\u1ea9u");
        visiblePassword.setManaged(false);
        visiblePassword.setVisible(false);

        Button eyeButton = createPasswordToggleButton();
        StackPane passwordPane = createPasswordPane(passwordField, visiblePassword, eyeButton);

        Label message = createMessageLabel();

        Button loginButton = createPrimaryButton("\u0110\u0103ng nh\u1eadp");
        loginButton.setOnAction(e -> {
            String account = usernameField.getText().trim();
            String password = getPasswordValue(passwordField, visiblePassword);

            if (account.isEmpty() || password.isBlank()) {
                setMessage(message, "Vui l\u00f2ng nh\u1eadp username v\u00e0 m\u1eadt kh\u1ea9u.", "#ff7777");
                return;
            }

            runTcpCall(
                    loginButton,
                    message,
                    "\u0110ang \u0111\u0103ng nh\u1eadp...",
                    () -> tcpClient.login(account, password),
                    response -> {
                        if (response.isSuccess()) {
                            long userId = response.userId() != null ? response.userId() : 0;
                            ChatView chatView = new ChatView(stage, userId);
                            stage.setScene(chatView.createScene());
                            return;
                        }
                        setMessage(message, response.message(), "#ff7777");
                    }
            );
        });

        // Nhấn Enter trên ô username hoặc password => thực hiện đăng nhập
        usernameField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) loginButton.fire();
        });
        passwordField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) loginButton.fire();
        });
        visiblePassword.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) loginButton.fire();
        });

        Hyperlink forgotPassword = createLink("Qu\u00ean m\u1eadt kh\u1ea9u?");
        forgotPassword.setOnAction(e -> showForgotAccountView(usernameField.getText().trim()));

        Hyperlink registerLink = createLink("Ch\u01b0a c\u00f3 t\u00e0i kho\u1ea3n?");
        registerLink.setOnAction(e -> showRegisterView(usernameField.getText().trim()));

        Label footer = new Label("\u1ee8ng d\u1ee5ng Chat (GUI) via TCP");
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
                message,
                loginButton,
                forgotPassword,
                registerLink,
                footer
        );

        setCenter(loginBox);
    }

    private void showRegisterView(String username) {
        VBox registerBox = createFormBox();

        Label title = createTitle("\u0110\u0103ng k\u00fd SinChat");
        Label subtitle = createSubtitle("T\u1ea1o t\u00e0i kho\u1ea3n m\u1edbi \u0111\u1ec3 b\u1eaft \u0111\u1ea7u chat.");

        TextField usernameField = createInput("T\u00ean ng\u01b0\u1eddi d\u00f9ng");
        usernameField.setText(username);

        TextField emailField = createInput("Email");
        PasswordField passwordField = createPasswordInput("M\u1eadt kh\u1ea9u");
        PasswordField confirmPasswordField = createPasswordInput("Nh\u1eadp l\u1ea1i m\u1eadt kh\u1ea9u");
        Label message = createMessageLabel();

        Button registerButton = createPrimaryButton("\u0110\u0103ng k\u00fd");
        registerButton.setOnAction(e -> {
            String account = usernameField.getText().trim();
            String email = emailField.getText().trim();
            String password = passwordField.getText();
            String confirmPassword = confirmPasswordField.getText();

            if (account.isEmpty() || email.isEmpty() || password.isBlank()) {
                setMessage(message, "Vui l\u00f2ng nh\u1eadp \u0111\u1ee7 username, email v\u00e0 m\u1eadt kh\u1ea9u.", "#ff7777");
                return;
            }

            if (!password.equals(confirmPassword)) {
                setMessage(message, "M\u1eadt kh\u1ea9u nh\u1eadp l\u1ea1i kh\u00f4ng kh\u1edbp.", "#ff7777");
                return;
            }

            runTcpCall(
                    registerButton,
                    message,
                    "\u0110ang \u0111\u0103ng k\u00fd...",
                    () -> tcpClient.register(account, password, email),
                    response -> {
                        if (response.isSuccess()) {
                            setMessage(message, response.message(), "#4ade80");
                            return;
                        }
                        setMessage(message, response.message(), "#ff7777");
                    }
            );
        });

        Hyperlink backToLogin = createLink("Quay l\u1ea1i \u0111\u0103ng nh\u1eadp");
        backToLogin.setOnAction(e -> showLoginView(usernameField.getText().trim()));

        registerBox.getChildren().addAll(
                title,
                subtitle,
                usernameField,
                emailField,
                passwordField,
                confirmPasswordField,
                message,
                registerButton,
                backToLogin
        );

        setCenter(registerBox);
    }

    private void showForgotAccountView(String username) {
        VBox forgotBox = createFormBox();

        Label title = createTitle("Qu\u00ean m\u1eadt kh\u1ea9u");
        Label subtitle = createSubtitle(
                "Nh\u1eadp username \u0111\u1ec3 server t\u1ea1o m\u00e3 x\u00e1c nh\u1eadn."
        );

        TextField accountField = createInput("T\u00ean ng\u01b0\u1eddi d\u00f9ng");
        accountField.setText(username);

        Label message = createMessageLabel();

        Button createCodeButton = createPrimaryButton("T\u1ea1o m\u00e3 x\u00e1c nh\u1eadn");
        createCodeButton.setOnAction(e -> {
            String account = accountField.getText().trim();

            if (account.isEmpty()) {
                setMessage(message, "Vui l\u00f2ng nh\u1eadp username.", "#ff7777");
                return;
            }

            runTcpCall(
                    createCodeButton,
                    message,
                    "\u0110ang t\u1ea1o m\u00e3...",
                    () -> tcpClient.requestPasswordResetCode(account),
                    response -> {
                        if (response.isSuccess()) {
                            resetAccount = account;
                            resetCode = response.code();
                            showVerifyCodeView();
                            return;
                        }
                        setMessage(message, response.message(), "#ff7777");
                    }
            );
        });

        Hyperlink backToLogin = createLink("Quay l\u1ea1i \u0111\u0103ng nh\u1eadp");
        backToLogin.setOnAction(e -> showLoginView(accountField.getText().trim()));

        forgotBox.getChildren().addAll(
                title,
                subtitle,
                accountField,
                message,
                createCodeButton,
                backToLogin
        );

        setCenter(forgotBox);
    }

    private void showVerifyCodeView() {
        VBox verifyBox = createFormBox();

        Label title = createTitle("X\u00e1c nh\u1eadn t\u00e0i kho\u1ea3n");
        Label subtitle = createSubtitle(
                hasResetCode()
                        ? "Server \u0111ang tr\u1ea3 m\u00e3 demo tr\u00ean UI. Sau n\u00e0y c\u00f3 th\u1ec3 chuy\u1ec3n sang g\u1eedi email."
                        : "Nh\u1eadp m\u00e3 x\u00e1c nh\u1eadn b\u1ea1n nh\u1eadn \u0111\u01b0\u1ee3c."
        );

        Label accountLabel = createSubtitle("T\u00e0i kho\u1ea3n: " + resetAccount);

        TextField codeField = createInput("Nh\u1eadp m\u00e3 x\u00e1c nh\u1eadn");
        Label message = createMessageLabel();

        Button confirmButton = createPrimaryButton("X\u00e1c nh\u1eadn");
        confirmButton.setOnAction(e -> {
            String enteredCode = codeField.getText().trim();

            if (enteredCode.isEmpty()) {
                setMessage(message, "Vui l\u00f2ng nh\u1eadp m\u00e3 x\u00e1c nh\u1eadn.", "#ff7777");
                return;
            }

            showNewPasswordView(enteredCode);
        });

        Hyperlink changeAccount = createLink("Nh\u1eadp l\u1ea1i username");
        changeAccount.setOnAction(e -> showForgotAccountView(resetAccount));

        verifyBox.getChildren().addAll(title, subtitle, accountLabel);
        if (hasResetCode()) {
            verifyBox.getChildren().add(createCodeLabel(resetCode));
        }
        verifyBox.getChildren().addAll(
                codeField,
                message,
                confirmButton,
                changeAccount
        );

        setCenter(verifyBox);
    }

    private void showNewPasswordView(String code) {
        VBox passwordBox = createFormBox();

        Label title = createTitle("\u0110\u1eb7t m\u1eadt kh\u1ea9u m\u1edbi");
        Label subtitle = createSubtitle("Nh\u1eadp m\u1eadt kh\u1ea9u m\u1edbi cho t\u00e0i kho\u1ea3n: " + resetAccount);

        PasswordField newPasswordField = createPasswordInput("M\u1eadt kh\u1ea9u m\u1edbi");
        PasswordField confirmPasswordField = createPasswordInput("Nh\u1eadp l\u1ea1i m\u1eadt kh\u1ea9u m\u1edbi");
        Label message = createMessageLabel();

        Button saveButton = createPrimaryButton("\u0110\u1ed5i m\u1eadt kh\u1ea9u");
        saveButton.setOnAction(e -> {
            String newPassword = newPasswordField.getText();
            String confirmPassword = confirmPasswordField.getText();

            if (newPassword.isBlank()) {
                setMessage(message, "Vui l\u00f2ng nh\u1eadp m\u1eadt kh\u1ea9u m\u1edbi.", "#ff7777");
                return;
            }

            if (newPassword.length() < 6) {
                setMessage(message, "M\u1eadt kh\u1ea9u m\u1edbi ph\u1ea3i c\u00f3 \u00edt nh\u1ea5t 6 k\u00fd t\u1ef1.", "#ff7777");
                return;
            }

            if (!newPassword.equals(confirmPassword)) {
                setMessage(message, "M\u1eadt kh\u1ea9u nh\u1eadp l\u1ea1i kh\u00f4ng kh\u1edbp.", "#ff7777");
                return;
            }

            runTcpCall(
                    saveButton,
                    message,
                    "\u0110ang \u0111\u1ed5i m\u1eadt kh\u1ea9u...",
                    () -> tcpClient.resetPassword(code, newPassword),
                    response -> {
                        if (response.isSuccess()) {
                            resetCode = null;
                            setMessage(message, response.message(), "#4ade80");
                            return;
                        }
                        setMessage(message, response.message(), "#ff7777");
                    }
            );
        });

        Hyperlink backToLogin = createLink("Quay l\u1ea1i \u0111\u0103ng nh\u1eadp");
        backToLogin.setOnAction(e -> showLoginView(resetAccount == null ? "" : resetAccount));

        passwordBox.getChildren().addAll(
                title,
                subtitle,
                newPasswordField,
                confirmPasswordField,
                message,
                saveButton,
                backToLogin
        );

        setCenter(passwordBox);
    }

    private StackPane createPasswordPane(
            PasswordField passwordField,
            TextField visiblePassword,
            Button eyeButton
    ) {
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
                eyeButton.setText("\u1ea8n");
            } else {
                passwordField.setText(visiblePassword.getText());
                visiblePassword.setVisible(false);
                visiblePassword.setManaged(false);
                passwordField.setVisible(true);
                passwordField.setManaged(true);
                eyeButton.setText("Hi\u1ec7n");
            }
        });

        return passwordPane;
    }

    private Button createPasswordToggleButton() {
        Button eyeButton = new Button("Hi\u1ec7n");
        eyeButton.setStyle("""
                -fx-background-color: transparent;
                -fx-text-fill: #aaa;
                -fx-font-size: 13px;
                -fx-cursor: hand;
                """);
        return eyeButton;
    }

    private Label createCodeLabel(String code) {
        Label codeLabel = new Label("M\u00e3 x\u00e1c nh\u1eadn: " + code);
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
        title.setStyle("""
                -fx-font-size: 42px;
                -fx-font-weight: bold;
                -fx-text-fill: white;
                """);
        return title;
    }

    private Label createSubtitle(String text) {
        Label subtitle = new Label(text);
        subtitle.setWrapText(true);
        subtitle.setAlignment(Pos.CENTER);
        subtitle.setStyle("""
                -fx-text-fill: #888;
                -fx-font-size: 16px;
                """);
        return subtitle;
    }

    private TextField createInput(String prompt) {
        TextField input = new TextField();
        input.setPromptText(prompt);
        input.setStyle("""
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
        input.setPrefHeight(65);
        return input;
    }

    private PasswordField createPasswordInput(String prompt) {
        PasswordField input = new PasswordField();
        input.setPromptText(prompt);
        input.setStyle("""
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
        input.setPrefHeight(65);
        return input;
    }

    private Button createPrimaryButton(String text) {
        Button button = new Button(text);
        button.setStyle("""
                -fx-background-color: white;
                -fx-text-fill: black;
                -fx-font-size: 20px;
                -fx-font-weight: bold;
                -fx-background-radius: 24px;
                -fx-cursor: hand;
                """);
        button.setPrefHeight(65);
        button.setMaxWidth(Double.MAX_VALUE);
        return button;
    }

    private Hyperlink createLink(String text) {
        Hyperlink link = new Hyperlink(text);
        link.setStyle("""
                -fx-text-fill: #aaa;
                -fx-border-color: transparent;
                """);
        return link;
    }

    private Label createMessageLabel() {
        Label label = new Label("");
        label.setWrapText(true);
        label.setAlignment(Pos.CENTER);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setStyle("""
                -fx-text-fill: transparent;
                -fx-font-size: 14px;
                """);
        return label;
    }

    private String getPasswordValue(PasswordField passwordField, TextField visiblePassword) {
        if (passwordField.isVisible()) {
            return passwordField.getText();
        }
        return visiblePassword.getText();
    }

    private boolean hasResetCode() {
        return resetCode != null && !resetCode.isBlank();
    }

    private void setMessage(Label label, String text, String color) {
        label.setText(text);
        label.setStyle("""
                -fx-text-fill: %s;
                -fx-font-size: 14px;
                """.formatted(color));
    }

    private void setCenter(VBox content) {
        root.setCenter(content);
        BorderPane.setAlignment(content, Pos.CENTER);
    }

    private void runTcpCall(
            Button actionButton,
            Label message,
            String loadingText,
            TcpCall tcpCall,
            Consumer<ChatTcpClient.ApiResponse> onComplete
    ) {
        actionButton.setDisable(true);
        setMessage(message, loadingText, "#aaa");

        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return tcpCall.execute();
                    } catch (Exception exception) {
                        return new ChatTcpClient.ApiResponse(
                                0,
                                "error",
                                "Không kết nối được server TCP. Kiểm tra host, port hoặc backend.",
                                "",
                                null,
                                exception.getMessage()
                        );
                    }
                })
                .thenAccept(response -> Platform.runLater(() -> {
                    actionButton.setDisable(false);
                    onComplete.accept(response);
                }));
    }

    @FunctionalInterface
    private interface TcpCall {
        ChatTcpClient.ApiResponse execute() throws Exception;
    }

    public Scene createScene() {
        return new Scene(root, 1400, 800);
    }
}
