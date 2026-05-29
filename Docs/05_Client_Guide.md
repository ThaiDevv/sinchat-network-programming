# 📱 SinChat JavaFX Client Manual

This document details the code structure, UI layouts, network communications, and operational steps for the **SinChat Client** desktop application built with JavaFX and Raw TCP Sockets.

---

## 1. Client Architecture Overview

SinChat Client is a JavaFX-based desktop application. It maintains a stateful TCP connection to the server for authentication, profile updates, and real-time chatting through the `ChatTcpClient` network wrapper.

### GUI Layout & Network Threading Model

```
                   ┌─────────────────────────────────────────┐
                   │               Main.java                 │
                   │  - Extends javafx.application.Application│
                   │  - Sets up primary window Stage         │
                   └──────────────────┬──────────────────────┘
                                      │ Loads initial scene
                   ┌──────────────────▼──────────────────────┐
                   │             LoginView.java              │
                   │  - Controls Login/Register/Reset UI     │
                   │  - Switches scene to ChatView on Success│
                   └──────────────────┬──────────────────────┘
                                      │ Authenticated userId
                   ┌──────────────────▼──────────────────────┐
                   │              ChatView.java              │
                   │  - Three-panel chat interface           │
                   │  - Renders bubbles into scroll window   │
                   └──────────────────▲──────────────────────┘
                                      │
        Platform.runLater()           │ Receives NEW_MESSAGE (Downstream)
┌─────────────────────────────────────┴──────────────────────┐
│                            ChatTcpClient                   │
│  - Establishes connection socket to localhost:3000         │
│  - Runs reader thread to keep GUI highly responsive        │
│  - Tracks CompletableFutures for sync requests             │
└─────────────────────────────────────┬──────────────────────┘
                                      │ Sends JSON actions (Upstream)
                                      ▼
                            Server (TCP Socket:3000)
```

---

## 2. Technology Stack

| Layer | Technology | Version | Purpose |
|---|---|---|---|
| **Core Runtime** | Java Development Kit (JDK) | 21 (LTS) | Core runtime environment |
| **GUI Framework** | [JavaFX](https://openjfx.io/) | 25 | Rich native UI elements |
| **GUI Modules** | javafx-controls, javafx-fxml | 25 | UI container Nodes (Buttons, inputs, scroll pan) |
| **JSON Parser** | [Gson](https://github.com/google/gson) | 2.10.1 | JSON-to-Java serialization and parsing |
| **Build Tool** | Apache Maven | 3.x | Build and dependency manager |

---

## 3. Directory Structure

The Client module directory is streamlined to maintain high cohesion:

```
Code/Client/
├── pom.xml                                 # Maven build dependencies
└── src/main/java/
    ├── Main.java                           # App bootstrapper; loads LoginView
    ├── LoginView.java                      # Multi-form view for Auth actions
    ├── ChatView.java                       # Main conversation interface
    └── ChatTcpClient.java                  # Manages persistent raw TCP socket
```

---

## 4. TCP Network Layer (`ChatTcpClient.java`)

`ChatTcpClient` acts as the client's network controller.

### Design Features
1.  **Asynchronous Connection (`connectAsync`)**: Initiates the TCP socket connection to the server on a background worker thread. This prevents locking the main **JavaFX Application Thread**.
2.  **Multithreaded Listener Loop**: Spawns a dedicated thread that loops indefinitely, reading string lines from the server Socket via `reader.readLine()`.
3.  **Non-blocking Request-Response Model**:
    *   To execute an action, a thread invokes `sendRequestSync(request)`. The client instantiates a `CompletableFuture<ApiResponse>`, inserts it into `pendingRequests` with a unique `requestId`, and writes the serialized JSON line followed by `\n` to the server.
    *   The thread blocks synchronously calling `future.get()`.
    *   When the background reader thread receives a JSON frame containing a matching `requestId`, it resolves the future from the map and calls `.complete(response)`.
4.  **Hang Protection on Disconnect**:
    When the socket closes or the connection drops, `disconnect()` iterates over `pendingRequests` and resolves all unresolved futures with a failed `ApiResponse` (Status 500: "Connection lost or closed"). This instantly frees any blocked UI threads and shows a descriptive connection error.

---

## 5. UI Layout & View Structures

All UI components are styled dynamically in Java code (no FXML layouts are used), allowing programmatic styling.

### 5.1. LoginView (Dynamic Form Switch)
Implements a clean border pane that switches between **4 functional screens**:
1.  **Login screen**: Username and password fields (includes a toggle eye button to show/hide the input text).
2.  **Register screen**: Username, email, password, and confirmation password inputs.
3.  **Forgot Password - Step 1**: Username input to trigger a 6-digit Reset Code generated by the server.
4.  **Forgot Password - Step 2**: Reset code, new password, and confirmation password inputs.

### 5.2. ChatView (Three-Panel Layout)
Uses `BorderPane` to create a robust, responsive workspace:
*   **Left Panel (250px)**: 
    *   Search bar to filter contacts.
    *   Scrollable contact list showing active chats, color-coded circle avatars with initials, name, last message preview, and time.
    *   **"+ Cuộc trò chuyện mới"** button to start a private chat with any user ID.
*   **Center Panel (Flexible Width)**:
    *   Header displaying the active contact's name.
    *   Scrollable messages panel (`ScrollPane` containing message bubbles):
        *   Sent bubbles: aligned right, background `#7c5cfc`, white text.
        *   Received bubbles: aligned left, background `#1a1a2e`, white text.
    *   **Typing Indicator**: Shows "User is typing..." when an event is received, auto-hiding after 3 seconds of silence.
    *   Input message field + send button (➤), with Enter key support.
*   **Right Panel (220px)**:
    *   Contact profile sidebar displaying avatar and details.

---

## 6. Running the Client

### Prerequisites
*   **Java JDK 21** or later.
*   **Maven** added to the system path.

### Quick Start
You can launch the Client instantly by double-clicking:
📂 **`run_client.cmd`** in the repository root folder.

Alternatively, you can run the commands manually:
```bash
# 1. Navigate to Client directory
cd Code/Client

# 2. Compile source code
mvn compile

# 3. Launch JavaFX Client
mvn javafx:run
```
