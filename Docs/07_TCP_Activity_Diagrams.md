# 📊 TCP Connection & Activity Diagrams

Scenario: Conversation ID = 12, consisting of 3 members: An (ID: 5), Binh (ID: 8), and Chi (ID: 11).

---

## 1. Server RAM Cache Mapping (Stateful Session Management)

The Server manages active socket sessions dynamically in RAM using the `TcpConnectionManager` class to allow fast, real-time downstream deliveries:

```mermaid
graph LR
    subgraph userConnections ["userConnections (userId -> Set of ClientConnection)"]
        direction LR
        U5["userId = 5 (An)"] --> WS_A["ClientConnection (Socket A)"]
        U8["userId = 8 (Binh)"] --> WS_B["ClientConnection (Socket B)"]
        U11["userId = 11 (Chi)"] --> WS_C1["ClientConnection (Socket C1 - Laptop)"]
        U11 --> WS_C2["ClientConnection (Socket C2 - Mobile)"]
    end

    subgraph connectionUsers ["connectionUsers (ClientConnection -> userId)"]
        direction LR
        R_A["Socket A"] --> RU5["userId = 5"]
        R_B["Socket B"] --> RU8["userId = 8"]
        R_C1["Socket C1"] --> RU11a["userId = 11"]
        R_C2["Socket C2"] --> RU11b["userId = 11"]
    end
```

**Key Data Structure Roles:**
*   **userConnections Map**: Maps a `userId` to a thread-safe Set of active `ClientConnection` objects, enabling targeted multi-device pushes.
*   **connectionUsers Map**: Maps a single active socket connection back to its owning `userId`, enabling fast cleanup during connection loss.

---

## 2. Identity Registration (JOIN Flow)

This sequence diagrams the connection flow when a client authenticates and registers its socket connection with the Server's memory manager to start receiving push events.

```mermaid
sequenceDiagram
    participant Client as Client (JavaFX)
    participant Server as TCP Server Thread
    participant Manager as TcpConnectionManager
    participant RAM1 as userConnections Map
    participant RAM2 as connectionUsers Map

    Note over Client,Server: Connection Establishment
    Client->>Server: Opens TCP socket connection to port 3000
    Server->>Server: Accepts socket, wraps in ClientConnection thread
    
    Note over Client,Server: Identity Registration (JOIN)
    Client->>Server: Writes JSON frame: {"action":"JOIN", "userId":5}\n
    Server->>Manager: addConnection(5, ClientConnection)

    Manager->>RAM1: userConnections[5].add(ClientConnection)
    Manager->>RAM2: connectionUsers.put(ClientConnection, 5)
    
    Note over Server: An (ID: 5) is registered online in RAM
```

---

## 3. Realtime Downstream Messaging

This sequence illustrates An (ID: 5) sending a message to chat ID 12.

```mermaid
sequenceDiagram
    participant An as An (ID: 5)
    participant Server as TCP Server (Router)
    participant DB as Database (MySQL)
    participant Manager as TcpConnectionManager
    participant Binh as Binh (ID: 8)
    participant Chi as Chi (ID: 11)

    An->>Server: {"action":"SEND_MESSAGE", "conversationId":12, "senderId":5, "content":"Hello!"}\n
    
    Note over Server,DB: Database Persistence
    Server->>DB: INSERT INTO messages (conv_id: 12, sender: 5, content: "Hello!")
    DB-->>Server: messageId = 1002

    Note over Server: Downstream Broadcast
    Server->>DB: SELECT user_id FROM conversation_members WHERE conversation_id = 12
    DB-->>Server: [5, 8, 11]

    Note over Server: Targeted Realtime Delivery
    Server->>An: Writes SEND_MESSAGE_RESPONSE back to Socket A
    Server->>Manager: Triggers NEW_MESSAGE broadcast
    Manager->>Binh: Writes directly to Socket B: {"action":"NEW_MESSAGE", ...}\n
    Manager->>Chi: Writes directly to Socket C1 & Socket C2: {"action":"NEW_MESSAGE", ...}\n
```

---

## 4. Connection Loss & Cleanup Flow

This sequence diagrams the cleanup operations when a client socket drops or disconnects.

```mermaid
sequenceDiagram
    participant Client as Client (JavaFX)
    participant Server as ClientConnection Thread
    participant Manager as TcpConnectionManager
    participant RAM1 as userConnections Map
    participant RAM2 as connectionUsers Map

    Client->>Server: Drops connection or closes application socket
    Server->>Server: readLine() returns null (InputStream closed)
    
    Note over Server: Enters connection finally block
    Server->>Manager: removeConnection(ClientConnection)
    
    Manager->>RAM2: connectionUsers.remove(ClientConnection)
    RAM2-->>Manager: Returns userId = 5
    
    Manager->>RAM1: userConnections[5].remove(ClientConnection)
    
    Note over Server: If An's socket Set is empty -> Set An Offline
```

---

## 5. Source Code Mapping Reference

| Operation | Handler Class | Handler Method |
| :--- | :--- | :--- |
| **Session Cache Add** | `com.server.tcp.TcpConnectionManager` | `addConnection()` |
| **Message Save** | `com.server.repository.MessageRepository` | `save()` |
| **Conversation Membership** | `com.server.repository.ConversationRepository` | `getMemberIds()` |
| **Realtime Broadcast Push** | `com.server.tcp.TcpConnectionManager` | `broadcastToUser()` |
| **Session Cache Cleanup** | `com.server.tcp.TcpConnectionManager` | `removeConnection()` |
