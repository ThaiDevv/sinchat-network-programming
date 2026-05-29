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
        // Properly disconnect from server when the application window is closed
        System.out.println("[Main] Application stopping - disconnecting from server...");
        ChatTcpClient client = ChatTcpClient.getInstance();
        client.shutdown();
    }

    public static void main(String[] args) {
        launch();
    }
}