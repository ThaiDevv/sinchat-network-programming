package com.client;

import com.client.service.ChatService;
import com.client.view.LoginView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage stage) {
        LoginView loginView = new LoginView(stage);
        Scene scene = loginView.createScene();
        stage.setTitle("SinChat");
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        System.out.println("[Main] Application stopping - disconnecting from server...");
        ChatService client = ChatService.getInstanceOrNull();
        if (client != null) {
            client.shutdown();
        }
    }

    public static void main(String[] args) {
        launch();
    }
}
