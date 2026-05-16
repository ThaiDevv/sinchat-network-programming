# SinChat API and WebSocket Reference

Tai lieu nay liet ke cac diem cuoi (endpoints) HTTP va giao thuc WebSocket duoc su dung trong he thong SinChat.

---

## 1. HTTP API (Port: 8080)

Tat ca cac yeu cau HTTP deu su dung dinh dang JSON.

### Authentication

| Endpoint | Method | Chuc nang | Body Request |
| :--- | :--- | :--- | :--- |
| `/api/login` | `POST` | Dang nhap he thong | `{"username": "...", "password": "..."}` |
| `/api/register` | `POST` | Dang ky tai khoan moi | `{"username": "...", "password": "...", "email": "..."}` |

### Messaging and Conversations

| Endpoint | Method | Chuc nang | Tham so / Body |
| :--- | :--- | :--- | :--- |
| `/api/messages` | `GET` | Lay lich su tin nhanh | `?conversationId=12` |
| `/api/messages/send` | `POST` | Gui tin nhanh (HTTP) | `{"conversationId": 12, "senderId": 5, "content": "..."}` |
| `/api/conversations/get-or-create` | `POST` | Lay hoac tao phong chat rieng | `{"user1Id": 5, "user2Id": 8}` |

---

## 2. WebSocket Protocol (Port: 8887)

Ket noi theo dia chi: `ws://localhost:8887`

### Client to Server (Upstream)

| Action | Chuc nang | Payload JSON |
| :--- | :--- | :--- |
| `join` | Dang ky dinh danh (Identity Registration) | `{"action": "join", "userId": 5}` |
| `send_message` | Gui tin nhanh realtime | `{"action": "send_message", "conversationId": 12, "senderId": 5, "content": "..."}` |
| `typing` | Thong bao trang thai dang nhap lieu | `{"action": "typing", "conversationId": 12, "userId": 5}` |

### Server to Client (Downstream)

| Action | Chuc nang | Payload JSON |
| :--- | :--- | :--- |
| `joined` | Xac nhan dang ky dinh danh thanh cong | `{"action": "joined", "userId": 5}` |
| `new_message` | Thong bao tin nhanh moi | `{"action": "new_message", "messageId": 99, "conversationId": 12, "senderId": 5, "content": "...", "createdAt": "..."}` |
| `user_typing` | Thong bao nguoi dung khac dang nhap lieu | `{"action": "user_typing", "conversationId": 12, "userId": 5}` |
| `error` | Thong bao loi he thong | `{"action": "error", "message": "..."}` |

---

## 3. Technical Notes

1. **Dual-Path Delivery:** He thong ho tro gui tin nhanh qua ca HTTP va WebSocket. Server se dong bo hoa du lieu va broadcast qua WebSocket cho tat ca cac thanh vien dang truc tuyen.
2. **Identification:** Hien tai he thong dinh danh dua tren `userId`. Cac phien ban tiep theo se trien khai xac thuc dua tren Token (JWT).
3. **Realtime Synchronization:** Khi nhan duoc action `new_message`, Client thuc hien kiem tra `senderId` de dieu huong hien thi giao dien (sent/received bubble).
