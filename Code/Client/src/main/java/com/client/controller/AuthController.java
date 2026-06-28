package com.client.controller;

import com.client.model.ApiResponse;
import com.client.service.ChatService;
import javafx.application.Platform;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Controller for authentication flows: login, register, forgot password, reset password.
 * Extracted from LoginView to separate business logic from UI rendering.
 */
public class AuthController {

    private final ChatService chatService;

    public AuthController() {
        this.chatService = ChatService.getInstance();
    }

    /**
     * Execute a TCP call asynchronously, then run the callback on the JavaFX thread.
     */
    public void runTcpCall(
            Runnable onStart,
            Consumer<String> onLoading,
            TcpCall tcpCall,
            Consumer<ApiResponse> onComplete
    ) {
        if (onStart != null) onStart.run();
        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return tcpCall.execute();
                    } catch (Exception exception) {
                        return new ApiResponse(0, "error",
                                "Không kết nối được server TCP. Kiểm tra host, port hoặc backend.",
                                "", null, exception.getMessage());
                    }
                })
                .thenAccept(response -> Platform.runLater(() -> onComplete.accept(response)));
    }

    // ---- direct API wrappers ----

    public ApiResponse login(String username, String password) {
        return chatService.login(username, password);
    }

    public ApiResponse register(String username, String password, String email) {
        return chatService.register(username, password, email);
    }

    public ApiResponse requestPasswordResetCode(String username) {
        return chatService.requestPasswordResetCode(username);
    }

    public ApiResponse resetPassword(String code, String password) {
        return chatService.resetPassword(code, password);
    }

    @FunctionalInterface
    public interface TcpCall {
        ApiResponse execute() throws Exception;
    }
}
