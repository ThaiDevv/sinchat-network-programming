# WebSocket Flow Diagrams - Multi-User Conversation

Kich ban: Conversation ID = 12, bao gom 3 thanh vien: An (ID: 5), Binh (ID: 8), Chi (ID: 11).

---

## 1. Server-Side Data Structures

Server su dung 2 bang bam (ConcurrentHashMap) de quan ly trang thai ket noi:

```mermaid
graph LR
    subgraph userConnections ["userConnections (userId -> Set of WebSocket)"]
        direction LR
        U5["userId = 5 (An)"] --> WS_A["WebSocket conn-A"]
        U8["userId = 8 (Binh)"] --> WS_B["WebSocket conn-B"]
        U11["userId = 11 (Chi)"] --> WS_C1["WebSocket conn-C1 (Laptop)"]
        U11 --> WS_C2["WebSocket conn-C2 (Mobile)"]
    end

    subgraph connectionUserMap ["connectionUserMap (WebSocket -> userId)"]
        direction LR
        R_A["conn-A"] --> RU5["userId = 5"]
        R_B["conn-B"] --> RU8["userId = 8"]
        R_C1["conn-C1"] --> RU11a["userId = 11"]
        R_C2["conn-C2"] --> RU11b["userId = 11"]
    end
```

**Muc dich su dung:**
- **userConnections**: Cho phep Server tim thay tat ca cac ket noi dang mo cua mot nguoi dung cu the de thuc hien gui tin nhan (Targeted Broadcast).
- **connectionUserMap**: Cho phep Server xac dinh nhanh nguoi dung nao vua ngat ket noi de cap nhat trang thai (Resource Cleanup).

---

## 2. Identity Registration Flow (JOIN)

Day la quy trinh xac thuc danh tinh ngay sau khi thiet lap ket noi WebSocket.

```mermaid
sequenceDiagram
    participant Client
    participant Server
    participant Map1 as userConnections
    participant Map2 as connectionUserMap

    Note over Client,Server: Connection Phase
    Client->>Server: Handshake & WebSocket Upgrade
    Server->>Server: onOpen(conn-A)
    
    Note over Client,Server: Identity Phase
    Client->>Server: {"action":"join", "userId":5}
    Server->>Server: handleJoin(conn-A, 5)

    Server->>Map2: connectionUserMap.put(conn-A, 5)
    Server->>Map1: userConnections[5].add(conn-A)
    
    Server-->>Client: {"action":"joined", "userId":5}
```

---

## 3. Realtime Messaging Flow

Kich ban: An (ID: 5) gui tin nhan den Conversation 12.

```mermaid
sequenceDiagram
    participant An as An (ID: 5)
    participant Server
    participant DB as Database
    participant Binh as Binh (ID: 8)
    participant Chi as Chi (ID: 11)

    An->>Server: {"action":"send_message", "conversationId":12, "senderId":5, "content":"..."}
    
    Note over Server,DB: Persistence Phase
    Server->>DB: INSERT INTO messages (conv_id: 12, sender: 5, content: "...")
    DB-->>Server: messageId = 99

    Note over Server: Broadcast Phase
    Server->>DB: SELECT user_id FROM members WHERE conv_id = 12
    DB-->>Server: [5, 8, 11]

    Note over Server: Targeted Push
    Server->>An: WS Push (conn-A)
    Server->>Binh: WS Push (conn-B)
    Server->>Chi: WS Push (conn-C1 & conn-C2)
```

---

## 4. Connection Termination (Cleanup)

Quy trinh xu ly khi mot ket noi bi ngat (vi du: User dong trinh duyet).

```mermaid
sequenceDiagram
    participant Client
    participant Server
    participant Map1 as userConnections
    participant Map2 as connectionUserMap

    Client->>Server: Close connection
    Server->>Server: onClose(conn-A)

    Server->>Map2: connectionUserMap.remove(conn-A)
    Map2-->>Server: userId = 5
    
    Server->>Map1: userConnections[5].remove(conn-A)
    
    Note over Server: Neu Set rong -> User hoan toan Offline
```

---

## 5. Code Mapping Reference

| Thanh phan | Package / File | Method |
| :--- | :--- | :--- |
| Identity Mapping | `com.server.websocket.ChatWebSocket` | `handleJoin()` |
| Message Persistence | `com.server.repository.MessageRepository` | `save()` |
| Member Lookup | `com.server.repository.ConversationRepository` | `getMemberIds()` |
| WebSocket Broadcast | `com.server.websocket.ChatWebSocket` | `broadcastToMembers()` |
| Resource Cleanup | `com.server.websocket.ChatWebSocket` | `onClose()` |
