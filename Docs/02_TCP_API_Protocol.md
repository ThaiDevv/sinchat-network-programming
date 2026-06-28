# 🔌 TCP Socket Protocol & API Reference

All communication between the Client and Server in SinChat is performed via **Raw TCP Sockets (Port: 3000)**. 
The transmitted data consists of UTF-8 encoded **JSON** strings terminated by a newline character (`\n`).

The system uses an asynchronous **Request-Response** pairing model utilizing a `requestId` field. This enables the Client to accurately map incoming responses to their original requests.

---

## 1. Basic Frame Protocol

### Request Frame (Client → Server)
```json
{
  "action": "ACTION_NAME",
  "requestId": "unique-uuid-or-timestamp",
  "...": "additional fields depending on the action"
}
```

### Response Frame (Server → Client)
```json
{
  "action": "ACTION_NAME_RESPONSE",
  "requestId": "matches the requestId from the request",
  "status": "success | error",
  "message": "Human-readable result message (optional)",
  "...": "additional returned fields"
}
```

---

## 2. TCP API Actions Reference

### 2.1. Authentication (LOGIN)
*   **Action**: `LOGIN`
*   **Request Payload**:
    ```json
    {
      "action": "LOGIN",
      "requestId": "req-login-1",
      "username": "john_doe",
      "password": "MyP@ssw0rd"
    }
    ```
*   **Success Response**:
    ```json
    {
      "action": "LOGIN_RESPONSE",
      "requestId": "req-login-1",
      "status": "success",
      "userId": 12,
      "username": "john_doe"
    }
    ```
*   **Error Response**:
    ```json
    {
      "action": "LOGIN_RESPONSE",
      "requestId": "req-login-1",
      "status": "error",
      "message": "Sai tên đăng nhập hoặc mật khẩu"
    }
    ```

---

### 2.2. Registration (REGISTER)
*   **Action**: `REGISTER`
*   **Request Payload**:
    ```json
    {
      "action": "REGISTER",
      "requestId": "req-reg-1",
      "username": "new_user",
      "password": "SecurePassword123",
      "email": "newuser@example.com"
    }
    ```
*   **Success Response**:
    ```json
    {
      "action": "REGISTER_RESPONSE",
      "requestId": "req-reg-1",
      "status": "success",
      "message": "Registration successful"
    }
    ```

---

### 2.3. Forgot Password (FORGOT_PASSWORD)
Supports 2 sub-flows depending on the presence of request fields:

#### Flow 1: Request Code
*   **Request Payload**:
    ```json
    {
      "action": "FORGOT_PASSWORD",
      "requestId": "req-forgot-1",
      "username": "john_doe"
    }
    ```
*   **Success Response**:
    ```json
    {
      "action": "FORGOT_PASSWORD_RESPONSE",
      "requestId": "req-forgot-1",
      "status": "success",
      "message": "Reset code generated.",
      "code": "482719"
    }
    ```

#### Flow 2: Reset Password
*   **Request Payload**:
    ```json
    {
      "action": "FORGOT_PASSWORD",
      "requestId": "req-forgot-2",
      "code": "482719",
      "password": "NewSecurePassword123"
    }
    ```
*   **Success Response**:
    ```json
    {
      "action": "FORGOT_PASSWORD_RESPONSE",
      "requestId": "req-forgot-2",
      "status": "success",
      "message": "Password reset successful"
    }
    ```

---

### 2.4. Profile Details (PROFILE)
Supports fetching and updating profile information via a `subAction` parameter.

#### Sub-Action: Get Profile (GET_PROFILE)
*   **Request Payload**:
    ```json
    {
      "action": "PROFILE",
      "subAction": "GET_PROFILE",
      "requestId": "req-prof-1",
      "userId": 12
    }
    ```
*   **Success Response**:
    ```json
    {
      "action": "PROFILE_RESPONSE",
      "requestId": "req-prof-1",
      "status": "success",
      "username": "john_doe",
      "full_name": "John Doe",
      "email": "john@example.com",
      "phone_number": "0987654321",
      "date_of_birth": "2000-01-01",
      "avatar": "http://link-to-avatar.png"
    }
    ```

#### Sub-Action: Update Profile (UPDATE_PROFILE)
*   **Request Payload** (omit or pass `null` for fields that are not changing):
    ```json
    {
      "action": "PROFILE",
      "subAction": "UPDATE_PROFILE",
      "requestId": "req-prof-2",
      "userId": 12,
      "full_name": "John Doe Updated",
      "email": "john_new@example.com",
      "phone_number": "0912345678",
      "date_of_birth": "2000-01-01",
      "avatar": "http://link-to-avatar-new.png"
    }
    ```
*   **Success Response**:
    ```json
    {
      "action": "PROFILE_RESPONSE",
      "requestId": "req-prof-2",
      "status": "success",
      "message": "Profile updated successfully"
    }
    ```

---

### 2.5. Change Avatar (CHANGE_AVATAR)
*   **Action**: `CHANGE_AVATAR`
*   **Request Payload**:
    ```json
    {
      "action": "CHANGE_AVATAR",
      "requestId": "req-avatar-1",
      "userId": 12,
      "avatarUrl": "http://example.com/new-avatar.jpg"
    }
    ```
*   **Success Response**:
    ```json
    {
      "action": "CHANGE_AVATAR_RESPONSE",
      "requestId": "req-avatar-1",
      "status": "success",
      "message": "Avatar updated successfully",
      "avatarUrl": "http://example.com/new-avatar.jpg"
    }
    ```

---

### 2.6. Get or Create Conversation (GET_OR_CREATE_CONVERSATION)
*   **Action**: `GET_OR_CREATE_CONVERSATION`
*   **Request Payload**:
    ```json
    {
      "action": "GET_OR_CREATE_CONVERSATION",
      "requestId": "req-conv-1",
      "user1Id": 12,
      "user2Id": 15
    }
    ```
*   **Success Response**:
    ```json
    {
      "action": "GET_OR_CREATE_CONVERSATION_RESPONSE",
      "requestId": "req-conv-1",
      "status": "success",
      "conversationId": 45
    }
    ```

---

### 2.7. Get User Conversations (GET_USER_CONVERSATIONS)
*   **Action**: `GET_USER_CONVERSATIONS`
*   **Request Payload**:
    ```json
    {
      "action": "GET_USER_CONVERSATIONS",
      "requestId": "req-convs-1",
      "userId": 12
    }
    ```
*   **Success Response**:
    ```json
    {
      "action": "GET_USER_CONVERSATIONS_RESPONSE",
      "requestId": "req-convs-1",
      "status": "success",
      "conversations": [
        {
          "conversationId": 45,
          "type": "PRIVATE",
          "name": "Jane Smith",
          "avatar": "http://example.com/jane.jpg",
          "lastMessage": "Hello there!",
          "lastMessageAt": "2026-05-20T21:10:00"
        }
      ]
    }
    ```

---

### 2.8. Get Messages (GET_MESSAGES)
*   **Action**: `GET_MESSAGES`
*   **Request Payload**:
    ```json
    {
      "action": "GET_MESSAGES",
      "requestId": "req-msgs-1",
      "conversationId": 45
    }
    ```
*   **Success Response**:
    ```json
    {
      "action": "GET_MESSAGES_RESPONSE",
      "requestId": "req-msgs-1",
      "status": "success",
      "conversationId": 45,
      "count": 1,
      "messages": [
        {
          "id": 1001,
          "conversationId": 45,
          "senderId": 15,
          "type": "TEXT",
          "content": "Hello there!",
          "createdAt": "2026-05-20T21:10:00"
        }
      ]
    }
    ```

---

### 2.9. Identity Registration (JOIN)
Once successfully authenticated, the Client must send a `JOIN` request to map the Socket connection with the `userId` in Server RAM, enabling real-time messaging delivery.
*   **Action**: `JOIN`
*   **Request Payload**:
    ```json
    {
      "action": "JOIN",
      "userId": 12
    }
    ```
*   *(No direct response; Server registers connection in RAM)*

---

### 2.10. Send Message (SEND_MESSAGE)
*   **Action**: `SEND_MESSAGE`
*   **Request Payload**:
    ```json
    {
      "action": "SEND_MESSAGE",
      "requestId": "req-send-1",
      "conversationId": 45,
      "senderId": 12,
      "content": "Hi! Nice to meet you."
    }
    ```
*   **Optional reply fields** (for replying to a message):
    ```json
    {
      "replyToId": 1001
    }
    ```
*   **Optional forward fields** (for forwarding a message from another conversation):
    ```json
    {
      "forwardFromId": 1000
    }
    ```
    > `forwardFromId`: ID of the original message being forwarded. The server resolves the sender username and original content automatically.
*   **Direct Success Response to Sender**:
    ```json
    {
      "action": "SEND_MESSAGE_RESPONSE",
      "requestId": "req-send-1",
      "status": "success",
      "messageId": 1002,
      "conversationId": 45,
      "senderId": 12,
      "content": "Hi! Nice to meet you."
    }
    ```
*   **Downstream Broadcast to other members**:
    The Server automatically locates online members of the conversation and pushes the following frame:
    ```json
    {
      "action": "NEW_MESSAGE",
      "conversationId": 45,
      "senderId": 12,
      "content": "Hi! Nice to meet you.",
      "messageId": 1002
    }
    ```
    **With reply metadata** (if `replyToId` was provided):
    ```json
    {
      "action": "NEW_MESSAGE",
      "conversationId": 45,
      "senderId": 12,
      "senderUsername": "alice",
      "content": "OK!",
      "messageId": 1003,
      "replyToId": 1002,
      "replyToUsername": "bob",
      "replyToContent": "Hi! Nice to meet you."
    }
    ```
    **With forward metadata** (if `forwardFromId` was provided):
    ```json
    {
      "action": "NEW_MESSAGE",
      "conversationId": 46,
      "senderId": 12,
      "senderUsername": "alice",
      "content": "Check this out!",
      "messageId": 1004,
      "forwardFromId": 1000,
      "forwardFromUsername": "bob",
      "forwardFromContent": "Tin nhắn gốc được chuyển tiếp"
    }
    ```

---

### 2.11. Typing Indicators (TYPING)
*   **Action**: `TYPING`
*   **Request Payload**:
    ```json
    {
      "action": "TYPING",
      "conversationId": 45,
      "userId": 12
    }
    ```
*   **Downstream Broadcast to other members**:
    ```json
    {
      "action": "USER_TYPING",
      "conversationId": 45,
      "userId": 12
    }
    ```
