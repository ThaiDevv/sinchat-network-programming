# Network Programming Project - Java Codebase Structure

**Date:** May 18, 2026  
**Project Name:** SinChat (Network Chat Application)  
**Architecture:** Client-Server with TCP/JSON protocol and Database backend

---

## TABLE OF CONTENTS
1. [Project Architecture Overview](#project-architecture-overview)
2. [Server Structure](#server-structure)
3. [Client Structure](#client-structure)
4. [Database Schema](#database-schema)
5. [Message Flow](#message-flow)
6. [Key Design Patterns](#key-design-patterns)

---

## PROJECT ARCHITECTURE OVERVIEW

### Technology Stack
- **Server:** Java with Maven, TCP Sockets, JDBC with HikariCP connection pooling
- **Client:** Java with JavaFX (GUI), TCP Socket client
- **Database:** MySQL 5.7
- **Build:** Maven (pom.xml)
- **Containerization:** Docker
- **JSON:** Google Gson library
- **Security:** BCrypt for password hashing
- **Logging:** SLF4J

### High-Level Flow
```
Client (JavaFX) 
    ↓ (JSON over TCP Socket)
    ↓ Port 3000
Server (Main.java → TcpServer)
    ↓ (Routes to Handlers)
    ↓ (Services → Repositories → Database)
Database (MySQL)
```

---

## SERVER STRUCTURE

### Location
```
Code/Server/
├── src/main/java/com/server/
│   ├── Main.java
│   ├── ProfileHandler.java
│   ├── config/
│   ├── handler/
│   ├── model/
│   ├── repository/
│   ├── service/
│   ├── tcp/
│   └── websocket/
└── src/test/java/com/server/
```

### Main Entry Point

**[Main.java](../Code/Server/src/main/java/com/server/Main.java)**
- **Purpose:** Server entry point
- **Functionality:**
  - Loads environment variables from `.env` file (default PORT=3000)
  - Creates TcpServer instance
  - Starts the server on configured port
  - Logs using SLF4J

---

### Configuration

**[Database.java](../Code/Server/src/main/java/com/server/config/Database.java)** (`config/`)
- **Purpose:** Database connection pooling configuration
- **Functionality:**
  - Uses HikariCP connection pool (optimized for cloud deployments)
  - Pool size: 5 connections max, 1 minimum
  - Environment variables: `DB_URL`, `DB_USER`, `DB_PASSWORD`
  - Connection timeout: 10 seconds
  - Idle timeout: 5 minutes
  - Max lifetime: 10 minutes
  - Keepalive: 1 minute ping to prevent stale connections
  - Singleton pattern for connection access

---

### TCP Layer (Network Communication)

**[TcpServer.java](../Code/Server/src/main/java/com/server/tcp/TcpServer.java)** (`tcp/`)
- **Purpose:** Main server socket listener
- **Functionality:**
  - Binds to configured port
  - Accepts incoming TCP connections
  - Delegates each connection to ClientConnection in thread pool
  - Thread pool size: 100 concurrent connections
  - Logging of connection events

**[ClientConnection.java](../Code/Server/src/main/java/com/server/tcp/ClientConnection.java)** (`tcp/`)
- **Purpose:** Handles individual client connection lifecycle
- **Key Properties:**
  - `socket`: TCP socket for communication
  - `reader`: BufferedReader for receiving JSON
  - `writer`: PrintWriter for sending JSON responses
  - `userId`: Authenticated user ID (set after login)
- **Functionality:**
  - Runs in dedicated thread (from TcpServer thread pool)
  - Reads JSON lines from client
  - Parses JSON and routes to appropriate handler
  - Maintains `userId` for authenticated operations
  - Error handling for invalid JSON
  - Graceful disconnection handling
  - Methods:
    - `run()`: Main loop for reading and routing requests
    - `setUserId(Long)`: Store authenticated user ID
    - `sendError(String)`: Send error response to client
    - `sendResponse(JsonObject)`: Send JSON response to client

**[Router.java](../Code/Server/src/main/java/com/server/tcp/Router.java)** (`tcp/`)
- **Purpose:** Central request routing based on action field
- **Supported Actions:**
  - `LOGIN`: LoginHandler → User authentication
  - `REGISTER`: RegisterHandler → New user registration
  - `FORGOT_PASSWORD`: ForgotPasswordHandler → Password reset flow
  - `PROFILE`: ProfileHandler → Get/update user profile
  - `GET_MESSAGES`: GetMessagesHandler → Retrieve conversation messages
  - `SEND_MESSAGE`: SendMessageHandler → Send new message
  - `GET_OR_CREATE_CONVERSATION`: ConversationHandle → Get or create conversation
  - `GET_USER_CONVERSATIONS`: GetConversationsHandler → List user's conversations
  - `CHANGE_AVATAR`: AvatarHandler → Update user avatar
- **Functionality:**
  - Singleton handler instances for efficiency
  - Validates `action` field presence
  - Optional `requestId` field for request tracking
  - Error responses for unknown actions
  - Exception handling with internal server error responses

**[TcpConnectionManager.java](../Code/Server/src/main/java/com/server/tcp/TcpConnectionManager.java)** (`tcp/`)
- **Purpose:** Connection state management (likely for tracking active connections)

---

### Handlers (Request Processing)

Handlers follow a common pattern:
- `handleTcp(JsonObject request, ClientConnection conn)` method
- Return `JsonObject` response
- Execute business logic via Services
- Error handling and validation

#### Authentication Handlers (`handler/auth/`)

**[LoginHandler.java](../Code/Server/src/main/java/com/server/handler/auth/LoginHandler.java)**
- **Request Fields:** `username`, `password`
- **Response:**
  - Success: `status: "success"`, `userId`, `username`
  - Error: `status: "error"`, `message`
- **Logic:** Delegates to `AuthService.login()` → password verification via BCrypt

**[RegisterHandler.java](../Code/Server/src/main/java/com/server/handler/auth/RegisterHandler.java)**
- **Request Fields:** `username`, `password`, `email`
- **Response:**
  - Success: `status: "success"`, registration confirmation
  - Error: `status: "error"`, `message` (duplicate username/email)
- **Logic:** Delegates to `AuthService.register()` → password hashing via BCrypt

**[ForgotPasswordHandler.java](../Code/Server/src/main/java/com/server/handler/auth/ForgotPasswordHandler.java)**
- **Request Fields:** `username` (to initiate), `resetCode`, `newPassword` (to confirm)
- **Response:** Reset code or success/error confirmation
- **Logic:** 
  - Generates 6-digit reset code on demand
  - Validates code and updates password
  - Delegates to `AuthService`

#### Message Handlers (`handler/message/`)

**[SendMessageHandler.java](../Code/Server/src/main/java/com/server/handler/message/SendMessageHandler.java)**
- **Request Fields:** `conversationId`, `message` (content), `type` (TEXT/IMAGE/VIDEO/VOICE/FILE)
- **Response:** Message confirmation with ID
- **Logic:** Delegates to `MessageService.sendMessage()`

**[GetMessagesHandler.java](../Code/Server/src/main/java/com/server/handler/message/GetMessagesHandler.java)**
- **Request Fields:** `conversationId`, optional pagination parameters
- **Response:** Array of Message objects with metadata
- **Logic:** Delegates to `MessageService.getMessages()`

**[ConversationHandle.java](../Code/Server/src/main/java/com/server/handler/message/ConversationHandle.java)**
- **Purpose:** Get or create conversation between users
- **Request Fields:** `userId1`, `userId2` (for private), or group creation parameters
- **Response:** Conversation object with ID
- **Logic:** Checks existence or creates new conversation

**[GetConversationsHandler.java](../Code/Server/src/main/java/com/server/handler/message/GetConversationsHandler.java)**
- **Purpose:** Get all conversations for authenticated user
- **Request Fields:** `userId`
- **Response:** Array of Conversation objects
- **Logic:** Delegates to `ConversationService`

#### Avatar Handler (`handler/changeavatar/`)

**[AvatarHandler.java](../Code/Server/src/main/java/com/server/handler/changeavatar/AvatarHandler.java)**
- **Request Fields:** `userId`, `avatarUrl`
- **Response:** Success/error with new avatar URL
- **Logic:** Delegates to `AvatarService.updateAvatar()`

---

### Services (Business Logic)

Services encapsulate business logic and coordinate with repositories.

**[AuthService.java](../Code/Server/src/main/java/com/server/service/AuthService.java)**
- **Methods:**
  - `login(username, password)` → User or null
    - Retrieves user via UserRepository
    - Verifies password hash with BCrypt
  - `register(username, password, email)` → boolean
    - Hashes password with BCrypt.gensalt()
    - Creates User object
    - Saves via UserRepository
  - `generateResetCode(username)` → 6-digit code string
    - Generates random 6-digit reset code
    - Stores in `passwordResetCodes` ConcurrentHashMap
  - `verifyResetCode(username, code)` → boolean
  - `resetPassword(username, newPassword)` → boolean
- **Key Design:** Centralized password security operations

**[MessageService.java](../Code/Server/src/main/java/com/server/service/MessageService.java)**
- **Methods:** (assumed based on handler usage)
  - `sendMessage(conversationId, senderId, type, content)` → Message
  - `getMessages(conversationId, limit, offset)` → List<Message>
  - `getMessageStatus()` → message delivery status tracking
- **Responsibility:** Message persistence and retrieval

**[ConversationService.java](../Code/Server/src/main/java/com/server/service/ConversationService.java)**
- **Methods:** (assumed)
  - `getOrCreateConversation(userId1, userId2)` → Conversation
  - `getUserConversations(userId)` → List<Conversation>
  - `createGroupConversation(name, memberIds)` → Conversation
- **Responsibility:** Conversation management

**[AvatarService.java](../Code/Server/src/main/java/com/server/service/AvatarService.java)**
- **Methods:**
  - `updateAvatar(userId, avatarUrl)` → boolean
- **Responsibility:** User avatar URL management

**[ProfileHandler.java](../Code/Server/src/main/java/com/server/ProfileHandler.java)** (Main package)
- **Purpose:** User profile retrieval and updates
- **Methods:** `handleTcp(request, connection)`
- **Responsibility:** Get/update username, status message, online status

---

### Repositories (Data Access)

Repositories handle database queries with JDBC.

**[UserRepository.java](../Code/Server/src/main/java/com/server/repository/UserRepository.java)**
- **Methods:**
  - `findByUsername(username)` → User or null
  - `findById(userId)` → User or null
  - `save(user)` → boolean (insert new user)
  - `update(user)` → boolean (update existing user)
  - `delete(userId)` → boolean
- **Implementation:** JDBC prepared statements with Database connection pool

**[MessageRepository.java](../Code/Server/src/main/java/com/server/repository/MessageRepository.java)**
- **Methods:** (assumed)
  - `save(message)` → boolean
  - `findByConversationId(conversationId)` → List<Message>
  - `findById(messageId)` → Message or null
- **Database Table:** `messages` with indexes on conversation_id and sender_id

**[ConversationRepository.java](../Code/Server/src/main/java/com/server/repository/ConversationRepository.java)**
- **Methods:** (assumed)
  - `save(conversation)` → boolean
  - `findById(conversationId)` → Conversation or null
  - `findByUserId(userId)` → List<Conversation>
  - `addMember(conversationId, userId, role)` → boolean

---

### Models (Data Objects)

**[User.java](../Code/Server/src/main/java/com/server/model/User.java)**
```java
public class User {
    private long id;                  // PK, auto-increment
    private String username;          // UNIQUE, VARCHAR(50)
    private String passwordHash;      // VARCHAR(255)
    private String email;             // UNIQUE, VARCHAR(100)
    private String avatarUrl;         // TEXT (URL)
    private String statusMessage;     // VARCHAR(255)
    private boolean isOnline;         // TINYINT(1)
    private Timestamp lastSeen;       // TIMESTAMP
    private Timestamp createdAt;      // TIMESTAMP
}
```

**[Message.java](../Code/Server/src/main/java/com/server/model/Message.java)**
```java
public class Message {
    public enum MessageType { TEXT, IMAGE, VIDEO, VOICE, FILE, SYSTEM }
    
    private long id;                    // PK, auto-increment
    private long conversationId;        // FK → conversations.id
    private long senderId;              // FK → users.id
    private MessageType type;           // ENUM, default TEXT
    private String content;             // TEXT
    private Timestamp createdAt;        // TIMESTAMP
}
```

**[Conversation.java](../Code/Server/src/main/java/com/server/model/Conversation.java)**
```java
public class Conversation {
    public enum ConversationType { PRIVATE, GROUP }
    
    private long id;                        // PK, auto-increment
    private ConversationType type;          // ENUM: PRIVATE or GROUP
    private String name;                    // VARCHAR(100), null for PRIVATE
    private String avatarUrl;               // TEXT (group avatar)
    private Long createdBy;                 // FK → users.id
    private Timestamp createdAt;            // TIMESTAMP
    private Timestamp lastMessageAt;        // TIMESTAMP
}
```

**[Attachment.java](../Code/Server/src/main/java/com/server/model/Attachment.java)**
```java
public class Attachment {
    private long id;
    private long messageId;             // FK → messages.id
    private String fileUrl;             // TEXT
    private String fileName;            // VARCHAR(255)
    private long fileSize;              // BIGINT
    private String mimeType;            // VARCHAR(100)
}
```

**[MessageStatus.java](../Code/Server/src/main/java/com/server/model/MessageStatus.java)**
```java
public class MessageStatus {
    public enum Status { SENT, DELIVERED, SEEN }
    
    private long messageId;             // FK → messages.id
    private long userId;                // FK → users.id
    private Status status;              // ENUM
    private Timestamp updatedAt;        // TIMESTAMP
}
```

**[Friendship.java](../Code/Server/src/main/java/com/server/model/Friendship.java)**
```java
public class Friendship {
    public enum FriendshipStatus { PENDING, ACCEPTED, BLOCKED }
    
    private long user1Id;               // FK, part of composite PK
    private long user2Id;               // FK, part of composite PK
    private FriendshipStatus status;    // ENUM
}
```

**[ChangeAvatar.java](../Code/Server/src/main/java/com/server/model/ChangeAvatar.java)**
- Purpose: Data transfer object for avatar update requests

---

### WebSocket Layer

**[websocket/](../Code/Server/src/main/java/com/server/websocket/)**
- Currently empty folder
- Planned for real-time features (future enhancement)

---

### Test Suite

**Test Location:** `src/test/java/com/server/`

#### Unit Tests

**Handler Tests (`handler/`)**
- [ForgotPasswordHandlerTest.java](../Code/Server/src/test/java/com/server/handler/auth/ForgotPasswordHandlerTest.java)
  - Tests reset code generation and password reset flow
- [RegisterHandlerTest.java](../Code/Server/src/test/java/com/server/handler/auth/RegisterHandlerTest.java)
  - Tests user registration with various inputs

**Model Tests (`model/`)**
- [UserTest.java](../Code/Server/src/test/java/com/server/model/UserTest.java)
- [MessageTest.java](../Code/Server/src/test/java/com/server/model/MessageTest.java)
- [ConversationTest.java](../Code/Server/src/test/java/com/server/model/ConversationTest.java)
- [AttachmentTest.java](../Code/Server/src/test/java/com/server/model/AttachmentTest.java)
- [MessageStatusTest.java](../Code/Server/src/test/java/com/server/model/MessageStatusTest.java)
- [FriendshipTest.java](../Code/Server/src/test/java/com/server/model/FriendshipTest.java)
- [ChangeAvatarTest.java](../Code/Server/src/test/java/com/server/model/ChangeAvatarTest.java)

**Service Tests (`service/`)**
- [AuthServiceTest.java](../Code/Server/src/test/java/com/server/service/AuthServiceTest.java)
  - Tests authentication logic, BCrypt hashing
- [MessageServiceTest.java](../Code/Server/src/test/java/com/server/service/MessageServiceTest.java)
- [ConversationServiceTest.java](../Code/Server/src/test/java/com/server/service/ConversationServiceTest.java)

#### Integration Tests (`integration/`)
- [AuthEndpointIntegrationTest.java](../Code/Server/src/test/java/com/server/integration/AuthEndpointIntegrationTest.java)
  - End-to-end authentication flow (register, login, forgot password)
- [EndpointIntegrationTest.java](../Code/Server/src/test/java/com/server/integration/EndpointIntegrationTest.java)
  - General endpoint integration tests
- [MessageEndpointIntegrationTest.java](../Code/Server/src/test/java/com/server/integration/MessageEndpointIntegrationTest.java)
  - Message sending and retrieval flow
- [AdditionalEndpointsIntegrationTest.java](../Code/Server/src/test/java/com/server/integration/AdditionalEndpointsIntegrationTest.java)
  - Profile, avatar, and other endpoint tests

---

## CLIENT STRUCTURE

### Location
```
Code/Client/
└── src/main/java/
    ├── Main.java
    ├── LoginView.java
    ├── ChatView.java
    └── ChatTcpClient.java
```

### Entry Point

**[Main.java](../Code/Client/src/main/java/Main.java)**
- **Purpose:** JavaFX application entry point
- **Functionality:**
  - Extends `Application` class (JavaFX)
  - Creates login scene on startup
  - Sets stage title to "SinChat"
  - Displays stage with login UI

---

### UI Components

**[LoginView.java](../Code/Client/src/main/java/LoginView.java)**
- **Purpose:** Login screen UI and logic
- **Key Features:**
  - Username/email input field
  - Password field with show/hide toggle
  - "Forgot Password" flow
  - "Register" navigation to registration screen
  - Form validation
  - Loading states during authentication
- **Styling:** Black background theme
- **Integration:** Uses `ChatTcpClient` for server communication

**[ChatView.java](../Code/Client/src/main/java/ChatView.java)**
- **Purpose:** Main chat interface after login
- **Key Features:**
  - Conversation list sidebar
  - Message display area
  - Message input field
  - User profile display (avatar, status message)
  - Real-time message updates
  - Conversation selection
  - Online status indicators
- **Styling:** Matches LoginView theme

---

### Network Client

**[ChatTcpClient.java](../Code/Client/src/main/java/ChatTcpClient.java)**
- **Purpose:** TCP socket client for server communication
- **Architecture:** Singleton pattern
- **Key Properties:**
  - `HOST`: "localhost"
  - `PORT`: 3000
  - `socket`: TCP socket connection
  - `reader`: BufferedReader for JSON responses
  - `writer`: PrintWriter for JSON requests
  - `gson`: Gson JSON serializer
  - `pendingRequests`: Map of request IDs to CompletableFutures for async responses

**Connection Methods:**
- `getInstance()`: Get or create singleton instance
- `connectAsync()`: Establish connection in background thread
  - Retry logic for connection failures
  - Reader/writer initialization with UTF-8 encoding
  - Separate thread for handling incoming messages

**Request Methods:**
- `login(username, password)` → CompletableFuture<ApiResponse>
- `register(username, password, email)` → CompletableFuture<ApiResponse>
- `sendMessage(conversationId, message)` → CompletableFuture<ApiResponse>
- `getMessages(conversationId)` → CompletableFuture<ApiResponse>
- `getConversations()` → CompletableFuture<ApiResponse>
- `getOrCreateConversation(userId1, userId2)` → CompletableFuture<ApiResponse>
- `updateAvatar(avatarUrl)` → CompletableFuture<ApiResponse>

**Event Callbacks:**
- `onNewMessage`: Consumer<JsonObject> - fired on incoming messages
- `onUserTyping`: Consumer<JsonObject> - fired on typing indicators
- `onConnected`: Runnable - fired on successful connection
- `onDisconnected`: Consumer<String> - fired on disconnection
- `onError`: Consumer<String> - fired on errors

**Internal Mechanism:**
- Each request gets unique `requestId` (UUID)
- Request is sent to server with `requestId`
- Server echoes `requestId` in response
- Client matches response to pending request via `requestId`
- CompletableFuture is completed with response
- Message listener thread (background) processes all incoming JSON

---

## DATABASE SCHEMA

### Database: `roacqgfa_ltm`
### Engine: MySQL 5.7
### Charset: utf8mb4_unicode_ci

### Tables

#### 1. **users**
```sql
CREATE TABLE users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  email VARCHAR(100) UNIQUE,
  avatar_url TEXT,
  status_message VARCHAR(255),
  is_online TINYINT(1) DEFAULT 0,
  last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
)
```
- **Sample Data:** 2 test users (testuser, admin) with bcrypt hashed passwords

#### 2. **conversations**
```sql
CREATE TABLE conversations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  type ENUM('PRIVATE', 'GROUP') NOT NULL,
  name VARCHAR(100),  -- null for PRIVATE
  avatar_url TEXT,    -- group avatar URL
  created_by BIGINT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  last_message_at TIMESTAMP,
  FOREIGN KEY (created_by) REFERENCES users(id)
)
```

#### 3. **conversation_members**
```sql
CREATE TABLE conversation_members (
  conversation_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  role ENUM('MEMBER', 'ADMIN') DEFAULT 'MEMBER',
  joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (conversation_id, user_id),
  FOREIGN KEY (conversation_id) REFERENCES conversations(id),
  FOREIGN KEY (user_id) REFERENCES users(id)
)
```

#### 4. **messages**
```sql
CREATE TABLE messages (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  conversation_id BIGINT NOT NULL,
  sender_id BIGINT NOT NULL,
  type ENUM('TEXT', 'IMAGE', 'VIDEO', 'VOICE', 'FILE', 'SYSTEM') DEFAULT 'TEXT',
  content TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  KEY idx_messages_conversation (conversation_id),
  FOREIGN KEY (conversation_id) REFERENCES conversations(id),
  FOREIGN KEY (sender_id) REFERENCES users(id)
)
```

#### 5. **message_status**
```sql
CREATE TABLE message_status (
  message_id BIGINT,
  user_id BIGINT,
  status ENUM('SENT', 'DELIVERED', 'SEEN'),
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE,
  FOREIGN KEY (user_id) REFERENCES users(id)
)
```
- **Purpose:** Track delivery status of messages per user

#### 6. **attachments**
```sql
CREATE TABLE attachments (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  message_id BIGINT,
  file_url TEXT,
  file_name VARCHAR(255),
  file_size BIGINT,
  mime_type VARCHAR(100),
  FOREIGN KEY (message_id) REFERENCES messages(id)
)
```
- **Purpose:** Store file attachments with metadata

#### 7. **friendships**
```sql
CREATE TABLE friendships (
  user1_id BIGINT NOT NULL,
  user2_id BIGINT NOT NULL,
  status ENUM('PENDING', 'ACCEPTED', 'BLOCKED'),
  PRIMARY KEY (user1_id, user2_id),
  FOREIGN KEY (user1_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (user2_id) REFERENCES users(id) ON DELETE CASCADE
)
```
- **Purpose:** Friend request and friend list management

#### 8. **calls** (Planned/Not Implemented)
```sql
CREATE TABLE calls (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  conversation_id BIGINT,
  started_by BIGINT,
  type ENUM('VOICE', 'VIDEO'),
  started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  ended_at TIMESTAMP,
  FOREIGN KEY (conversation_id) REFERENCES conversations(id),
  FOREIGN KEY (started_by) REFERENCES users(id)
)
```
- **Purpose:** Voice/video call history

#### 9. **call_participants** (Planned/Not Implemented)
```sql
CREATE TABLE call_participants (
  call_id BIGINT,
  user_id BIGINT,
  joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  left_at TIMESTAMP,
  FOREIGN KEY (call_id) REFERENCES calls(id),
  FOREIGN KEY (user_id) REFERENCES users(id)
)
```
- **Purpose:** Track participants in multi-user calls

---

## MESSAGE FLOW

### 1. Login Flow
```
Client                          Server
  |  {"action":"LOGIN",          |
  |   "username":"admin",        |
  |   "password":"pass123"}  --> Router
                                 |
                                 |---> LoginHandler
                                        |
                                        |---> AuthService.login()
                                              |
                                              |---> UserRepository.findByUsername()
                                                   (Database Query)
                                              |
                                              |---> BCrypt.checkpw()
                                                   (Password Verification)
                                        |
                                        |<--- User object or null
                                 |
  | <-- {"status":"success",
  |      "userId":1,
  |      "username":"admin"}
```

### 2. Send Message Flow
```
Client                          Server
  | {"action":"SEND_MESSAGE",    |
  |  "conversationId":1,         |
  |  "message":"Hello"}     --> Router
                                 |
                                 |---> SendMessageHandler
                                        |
                                        |---> MessageService.sendMessage()
                                              |
                                              |---> MessageRepository.save()
                                                   (Insert into messages table)
                                        |
                                        |<--- Message object
                                 |
  | <-- {"status":"success",
  |      "messageId":123,
  |      "createdAt":"..."}
```

### 3. Get Conversations Flow
```
Client                          Server
  | {"action":"GET_USER_CONVERSATIONS",
  |  "userId":1}            --> Router
                                 |
                                 |---> GetConversationsHandler
                                        |
                                        |---> ConversationService
                                              |
                                              |---> ConversationRepository.findByUserId()
                                                   (Query with conversation_members join)
                                        |
                                        |<--- List<Conversation>
                                 |
  | <-- {"status":"success",
  |      "conversations":[
  |        {"id":1, "type":"PRIVATE", ...}
  |      ]}
```

---

## KEY DESIGN PATTERNS

### 1. **Singleton Pattern**
- `ChatTcpClient`: Single instance per client (lazy initialized)
- `Router`: Reusable handler instances
- `AuthService`, etc.: Single instances per service

**Benefit:** Reduce object creation overhead, centralized state management

### 2. **Handler Pattern**
- Each action has dedicated handler class
- `handleTcp(JsonObject, ClientConnection)` method signature
- Loose coupling between router and handlers

**Benefit:** Separation of concerns, easy to test and extend

### 3. **Service Layer Pattern**
- Business logic isolated in service classes
- Services delegate data access to repositories
- Testable independently

**Benefit:** Clean architecture, business logic centralization

### 4. **Repository Pattern**
- Data access abstraction via repository classes
- JDBC queries wrapped in repository methods
- Connection pooling managed centrally

**Benefit:** Database independence, query optimization, easy mocking

### 5. **Model/Entity Pattern**
- Plain Java objects representing database entities
- Getters/setters for all properties
- Enums for fixed values (MessageType, ConversationType, etc.)

**Benefit:** Type safety, clear data structure

### 6. **Factory Pattern**
- `Database.getConnection()`: Returns pooled connections
- Connection pooling via HikariCP

**Benefit:** Connection reuse, resource efficiency

### 7. **Observer Pattern (Callbacks)**
- ChatTcpClient event callbacks: `onNewMessage`, `onConnected`, etc.
- View components subscribe to events

**Benefit:** Decoupled event handling, reactive UI updates

### 8. **JSON RPC Style**
- Request/Response with unique IDs
- `requestId` echoed in response
- Supports async request matching

**Benefit:** Correlation of requests/responses over TCP

### 9. **Thread Pool Pattern**
- TcpServer uses ExecutorService (100 threads)
- Each client connection handled in separate thread
- Prevents blocking on I/O

**Benefit:** Scalability, non-blocking server

### 10. **Async/Future Pattern**
- Client sends requests and gets CompletableFuture
- Background thread receives responses
- Future completed when response arrives

**Benefit:** Non-blocking client, responsive UI

---

## BUILD & DEPLOYMENT

### Server Build
```bash
cd Code/Server
mvn clean package
```

### Client Build
```bash
cd Code/Client
mvn clean package
```

### Docker Deployment
```bash
docker-compose up
```
- Server: Container running Java application on port 3000
- Database: MySQL container

### Configuration
- `.env` file in `Code/Server` directory:
  - `PORT`: Server port (default 3000)
  - `DB_URL`: Database JDBC URL
  - `DB_USER`: Database username
  - `DB_PASSWORD`: Database password

---

## SUMMARY

This is a well-architected **client-server chat application** with:
- **Clean Separation:** UI (Client) → Network (TCP/JSON) → Server → Database
- **Scalable:** Thread pool on server, connection pooling on DB
- **Secure:** BCrypt password hashing, prepared statements to prevent SQL injection
- **Testable:** Comprehensive unit and integration tests
- **Extensible:** Handler pattern allows easy addition of new endpoints
- **Production-Ready:** Logging, error handling, environment configuration

The project demonstrates solid Java enterprise patterns and is suitable for real-world deployment on cloud platforms.

