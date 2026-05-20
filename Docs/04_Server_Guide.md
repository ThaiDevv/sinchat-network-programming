# 🖥️ SinChat TCP Server Manual

This document provides a comprehensive guide to the architecture, configuration, building, and running of the **SinChat Server** utilizing Java and Stateful TCP Sockets.

---

## 1. Server Architecture Overview

SinChat Server is a high-performance backend written in **Java 21**, operating as a stateful TCP socket server. It removes legacy HTTP/WebSocket servers, directing all communication through a single Raw TCP Socket connection to optimize throughput.

### Connection & Routing Pipeline

```
              ┌─────────────────────────────────────────┐
              │           Clients (JavaFX)              │
              └────────┬────────────────────────┬───────┘
                       │ TCP Socket (Port 3000) │
┌──────────────────────▼────────────────────────▼──────────────────────┐
│                            TcpServer                                 │
│  - Listens on the configured ServerSocket port (default: 3000)        │
│  - Uses Cached ThreadPool to delegate socket client threads          │
└──────────────────────┬────────────────────────┬──────────────────────┘
                       │ Assigns Connection     │
┌──────────────────────▼────────────────────────▼──────────────────────┐
│                         ClientConnection                             │
│  - Represents a single connection socket for each Client              │
│  - Thread loops continuously reading data lines (readLine)           │
└──────────────────────┬───────────────────────────────────────────────┘
                       │ parses JSON & routes
┌──────────────────────▼────────────────────────▼──────────────────────┐
│                             Router                                   │
│  - Inspects the "action" field and routes payload to the Handler     │
│  - Features a robust Throwable catch to prevent connection sags      │
└──────────────────────┬───────────────────────────────────────────────┘
                       │ Invokes Business Logic
┌──────────────────────▼───────────────────────────────────────────────┐
│                         Handlers & Services                          │
│  - Performs BCrypt hashing, message persistence, etc.                │
│  - Integrates HikariCP pool to query MySQL database                  │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 2. Technology Stack

| Component | Library / Framework | Version | Purpose |
|---|---|---|---|
| **Core Runtime** | Java Development Kit (JDK) | 21 (LTS) | Core runtime platform |
| **Networking** | `java.net.ServerSocket` / `Socket` | JDK built-in | Multi-threaded Raw TCP Socket |
| **Database Pool** | [HikariCP](https://github.com/brettwooldridge/HikariCP) | 5.1.0 | High-performance DB connection pool |
| **Database** | MySQL Database | 8.x | Relational persistent database |
| **Security** | [jBCrypt](https://www.mindrot.org/projects/jBCrypt/) | 0.4 | Secure password hashing |
| **JSON Parser** | [Gson](https://github.com/google/gson) | 2.10.1 | JSON-to-Java serialization and parsing |
| **Configuration** | [dotenv-java](https://github.com/cdimascio/dotenv-java) | 3.0.0 | Environment configuration loader (`.env`) |
| **Logging** | [SLF4J](https://www.slf4j.org/) + slf4j-simple | 2.0.13 | Structured system logs |
| **Build Tool** | Apache Maven | 3.x | Dependency management & packaging |

---

## 3. Directory Structure

```
Code/Server/
├── pom.xml                             # Maven build configuration & dependencies
├── .env                                # Local configuration variables
├── src/
│   ├── main/java/com/server/
│   │   ├── Main.java                   # Main entry point; starts TcpServer
│   │   ├── ProfileHandler.java         # Processes profile actions
│   │   ├── config/
│   │   │   └── Database.java           # Sets up HikariCP connection pool
│   │   ├── handler/
│   │   │   ├── auth/
│   │   │   │   ├── LoginHandler.java        # Handles LOGIN action
│   │   │   │   ├── RegisterHandler.java     # Handles REGISTER action
│   │   │   │   └── ForgotPasswordHandler.java # Handles FORGOT_PASSWORD action
│   │   │   ├── message/
│   │   │   │   ├── SendMessageHandler.java       # Handles SEND_MESSAGE & real-time broadcasts
│   │   │   │   ├── GetMessagesHandler.java        # Fetches message history
│   │   │   │   ├── GetConversationsHandler.java   # Fetches conversations list
│   │   │   │   └── ConversationHandle.java        # Fetches or initializes a private chat
│   │   │   └── changeavatar/
│   │   │       └── AvatarHandler.java        # Handles CHANGE_AVATAR action
│   │   ├── model/
│   │   │   ├── User.java               # User model entity
│   │   │   ├── Message.java            # Message model entity
│   │   │   └── Conversation.java       # Conversation model entity
│   │   ├── repository/
│   │   │   ├── UserRepository.java     # DB CRUD for users
│   │   │   ├── MessageRepository.java  # DB CRUD for messages
│   │   │   └── ConversationRepository.java # DB CRUD for conversations
│   │   ├── service/
│   │   │   ├── AuthService.java        # Hashing and reset token logic
│   │   │   ├── MessageService.java     # Persistence logic for messages
│   │   │   ├── ConversationService.java # Private and group conversation logic
│   │   │   └── AvatarService.java      # Profile avatar updates
│   │   └── tcp/
│   │       ├── TcpServer.java          # ServerSocket listener setup
│   │       ├── ClientConnection.java   # Loops readLine for a client connection
│   │       ├── Router.java             # Maps action tags to handlers
│   │       └── TcpConnectionManager.java # Thread-safe online connection cache
│   └── test/java/com/server/
│       └── ...                         # Server testing suites
```

---

## 4. Session & Connection Management

The server manages online user sockets via the `TcpConnectionManager` class using thread-safe data structures:

1.  **Cache Structures**:
    *   `ConcurrentHashMap<Long, Set<ClientConnection>> userConnections`: Maps a `userId` to a set of active connections (allows login on multiple devices).
    *   `ConcurrentHashMap<ClientConnection, Long> connectionUsers`: Reverse map used to fetch a user ID quickly when a connection terminates.
2.  **Key Operations**:
    *   `addConnection(userId, connection)`: Registers an online socket connection (triggered by `JOIN`).
    *   `removeConnection(connection)`: Unregisters the socket and frees resources on disconnect.
    *   `broadcastToUser(userId, message)`: Sends real-time JSON frames directly to the mapped socket connections.

---

## 5. Configuration Guide

The Server reads configuration from a `.env` file placed in the `Code/Server/` directory (or the parent project root).

### Example `.env` File
```env
PORT=3000
DB_URL=jdbc:mysql://free02.123host.vn/roacqgfa_ltm
DB_USER=roacqgfa_ltm
DB_PASSWORD=11111111
```

### HikariCP DB Pool Tuning (`Database.java`)
*   **MaximumPoolSize**: 5 (Optimized for Render Free Tier which has tight CPU/Memory constraints).
*   **ConnectionTimeout**: 10,000 ms (Cancels request if DB connection takes too long to avoid blocking thread pools).
*   **KeepaliveTime**: 60,000 ms (Periodically tests idle pool connections with `SELECT 1` every 1 minute to prevent database disconnects by cloud hosts).

---

## 6. Running Locally

### Prerequisites
*   **Java JDK 21** or later installed.
*   **Maven** added to the system environment path.

### Quick Start
You can compile and launch the Server instantly by double-clicking:
📂 **`run_server.cmd`** in the repository root folder.

Alternatively, you can run the commands manually:
```bash
# 1. Navigate to Server directory
cd Code/Server

# 2. Compile source code
mvn compile

# 3. Launch TCP Server
mvn exec:java -Dexec.mainClass="com.server.Main"
```

Once running successfully, the following logs will appear:
```
[com.server.Main.main()] INFO com.server.Main - Main Server started TCP on port 3000
[Thread-2] INFO com.server.tcp.TcpServer - TCP Server started on port 3000
```
