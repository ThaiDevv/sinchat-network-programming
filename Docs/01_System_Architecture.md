# 🏗️ SinChat System Architecture (TCP Socket)

This document describes in detail the layered architecture pattern combined with a pure TCP Socket communication protocol in the SinChat system.

---

## 1. System Architecture Overview

To ensure high performance, minimize latency, and completely eliminate the stateless constraints of traditional HTTP protocols, SinChat has been fully migrated to a **Stateful TCP Socket** architecture. The system maintains a continuous, bidirectional connection between the Client and the Server.

```
┌──────────────────────────────────────────────────────────────┐
│                      Client (JavaFX)                         │
│   Maintains 1 continuous TCP Socket connection to Server:3000│
└──────────────────────────────┬───────────────────────────────┘
                               │ TCP Socket (JSON Stream \n)
┌──────────────────────────────▼───────────────────────────────┐
│                      TCP Server (Port: 3000)                 │
│   Accepts connections, manages ThreadPool & ConnectionManager│
└──────────────────────────────┬───────────────────────────────┘
                               │
┌──────────────────────────────▼───────────────────────────────┐
│                      Handler Layer                           │
│   Decodes JSON from Stream -> Router routes to Handler       │
└──────────────────────────────┬───────────────────────────────┘
                               │
┌──────────────────────────────▼───────────────────────────────┐
│                      Service Layer (Business)                │
│   Handles core business logic (BCrypt, validation, etc.)     │
└──────────────────────────────┬───────────────────────────────┘
                               │
┌──────────────────────────────▼───────────────────────────────┐
│                      Repository Layer (Data)                 │
│   Communicates with MySQL DB via HikariCP Connection Pool    │
└──────────────────────────────┬───────────────────────────────┘
                               │
┌──────────────────────────────▼───────────────────────────────┐
│                      Database (MySQL)                        │
└──────────────────────────────────────────────────────────────┘
```

---

## 2. Layered Responsibilities

The system is designed according to the principle of **Separation of Concerns (SoC)**, making the codebase highly maintainable and scalable:

### 2.1. Networking & Routing Layer
*   **TCP Server (`TcpServer.java`)**: Listens on a configured port (default `3000`). When a client connects, it assigns a separate thread from the ThreadPool (`ClientConnection.java`).
*   **Client Connection (`ClientConnection.java`)**: Maintains a continuous reading loop (`reader.readLine()`) from the Socket. Each message is formatted as a single-line JSON string terminated by a newline character (`\n`).
*   **Router (`Router.java`)**: Receives the JSON string from the reader, extracts the `action` attribute (e.g., `LOGIN`, `SEND_MESSAGE`), and routes the processing to the corresponding Handler. It includes a comprehensive error-handling mechanism (`catch (Throwable t)`) which prevents connection crashes by recovering gracefully when serious errors occur.

### 2.2. Handler Layer (Controller Layer)
*   **Package**: `com.server.handler.*`
*   **Responsibilities**:
    *   Receives business parameters from the JSON object.
    *   Performs initial input validation.
    *   Calls the appropriate business operations in the Service layer.
    *   Returns a standardized `JsonObject` payload to be sent back to the Client via the TCP Socket.

### 2.3. Service Layer (Business Logic Layer)
*   **Package**: `com.server.service.*`
*   **Responsibilities**:
    *   Contains the actual business logic of the application.
    *   Performs secure password hashing using the **BCrypt** algorithm (`AuthService`).
    *   Manages message delivery and real-time state broadcasting through the `TcpConnectionManager` (`MessageService`).
    *   Remains completely decoupled from the transport protocol (independent of Sockets or JSON).

### 2.4. Repository & Model Layer (Data Access & Domain Entities)
*   **Package**: `com.server.repository.*` & `com.server.model.*`
*   **Responsibilities**:
    *   **Model**: POJOs representing the database tables (User, Message, Conversation, etc.).
    *   **Repository**: Uses pure JDBC to execute SQL. Strictly utilizes **PreparedStatement** to eliminate **SQL Injection** vulnerabilities.
    *   **Database Setup (`Database.java`)**: Configures the **HikariCP** connection pool to reuse database connections efficiently and prevent overloading remote MySQL instances.

---

## 3. Data Flow Example

When a user performs a **Login (LOGIN)** action:

1.  **Client**: Packages the login information into a JSON string:
    `{"action": "LOGIN", "username": "...", "password": "...", "requestId": "..."}\n` and writes it to the Socket Output Stream.
2.  **Server (ClientConnection)**: Reads the single-line string from the Socket and passes it to the **Router**.
3.  **Router**: Identifies the action as `"LOGIN"` and dispatches the payload to the `LoginHandler`.
4.  **LoginHandler**: Extracts the `username` and `password` and invokes `authService.login(username, password)`.
5.  **AuthService**: Invokes `userRepository.findByUsername(username)` to retrieve user details from the database and verifies the password using `BCrypt.checkpw()`.
6.  **Response**:
    *   On success: returns `status = success` along with the user's details.
    *   On failure: returns `status = error` along with the error message.
    *   The **Router** writes the response JSON string back to the Client via the TCP Socket.
