# Ghi Chú Triển Khai: Message Search Qua TCP

Tài liệu này ghi lại chi tiết phần triển khai task **SCRUM-24: Message search**.

Mục tiêu của task là cho phép người dùng tìm tin nhắn trong cuộc trò chuyện đang mở, theo đúng kiến trúc TCP socket của dự án.

## 1. Mục Tiêu Chức Năng

Chức năng tìm kiếm tin nhắn hoạt động trong một conversation cụ thể.

Luồng người dùng:

```text
Người dùng mở một cuộc trò chuyện
 -> nhập từ khóa vào ô "Tìm tin nhắn..."
 -> bấm nút "Tìm" hoặc nhấn Enter
 -> client gửi action SEARCH_MESSAGES qua TCP
 -> server tìm trong bảng messages
 -> server trả danh sách tin nhắn phù hợp
 -> UI hiển thị kết quả ngay dưới header chat
```

## 2. Vì Sao Là TCP Action, Không Phải HTTP Endpoint?

Dự án hiện tại dùng TCP socket. Vì vậy message search không gọi endpoint kiểu:

```text
/api/messages/search
```

Thay vào đó, client gửi JSON action qua `ChatTcpClient`.

Action mới là:

```text
SEARCH_MESSAGES
```

## 3. Request TCP

Client gửi request dạng:

```json
{
  "action": "SEARCH_MESSAGES",
  "conversationId": 1,
  "keyword": "hello",
  "limit": 20,
  "offset": 0,
  "requestId": "..."
}
```

Ý nghĩa:

| Field | Ý nghĩa |
|---|---|
| `action` | Tên action để Router biết cần gọi handler nào. |
| `conversationId` | Chỉ tìm trong conversation đang mở. |
| `keyword` | Từ khóa người dùng nhập. |
| `limit` | Số kết quả tối đa trả về. |
| `offset` | Vị trí bắt đầu, để sau này có thể phân trang. |
| `requestId` | Dùng để ghép response với request ban đầu. |

## 4. Response TCP

Nếu thành công:

```json
{
  "action": "SEARCH_MESSAGES_RESPONSE",
  "status": "success",
  "conversationId": 1,
  "keyword": "hello",
  "count": 2,
  "messages": [
    {
      "id": 10,
      "conversationId": 1,
      "senderId": 3,
      "type": "TEXT",
      "content": "hello",
      "createdAt": "..."
    }
  ],
  "requestId": "..."
}
```

Nếu lỗi:

```json
{
  "action": "SEARCH_MESSAGES_RESPONSE",
  "status": "error",
  "message": "Missing conversationId or keyword",
  "requestId": "..."
}
```

## 5. File Client Đã Sửa

### 5.1. `ChatTcpClient.java`

File:

```text
Code/Client/src/main/java/ChatTcpClient.java
```

Mình thêm hàm:

```java
public ApiResponse searchMessages(long conversationId, String keyword, int limit, int offset)
```

Hàm này tạo JSON action `SEARCH_MESSAGES` rồi gửi qua TCP bằng `sendRequestSync`.

### 5.2. `ChatView.java`

File:

```text
Code/Client/src/main/java/ChatView.java
```

Mình thêm UI tìm kiếm ở header của khung chat:

- Ô nhập `Tìm tin nhắn...`
- Nút `Tìm`
- Panel kết quả nằm dưới header chat

Các hàm mới:

```java
searchMessagesInCurrentConversation()
renderMessageSearchResults(...)
createMessageSearchResultItem(...)
showMessageSearchStatus(...)
clearMessageSearchResults()
```

Logic kiểm tra phía client:

1. Nếu keyword rỗng thì ẩn panel kết quả.
2. Nếu keyword ngắn hơn 2 ký tự thì báo lỗi.
3. Nếu chưa chọn conversation thì báo lỗi.
4. Nếu chưa kết nối TCP thì báo lỗi.
5. Nếu hợp lệ thì gọi `tcpClient.searchMessages(...)`.

Kết quả tìm kiếm được hiển thị thành danh sách nhỏ gồm:

- Người gửi.
- Thời gian tạo tin nhắn nếu server trả về.
- Nội dung tin nhắn.

## 6. File Server Đã Thêm Và Sửa

### 6.1. `SearchMessagesHandler.java`

File mới:

```text
Code/Server/src/main/java/com/server/handler/message/SearchMessagesHandler.java
```

Handler này nhận action `SEARCH_MESSAGES` từ Router.

Nó kiểm tra:

1. Có `conversationId` và `keyword` không.
2. `conversationId` có hợp lệ không.
3. `keyword` có rỗng không.
4. `limit` không vượt quá giới hạn server cho phép.
5. User hiện tại có thuộc conversation này không.

Điểm quan trọng:

```text
Server không cho user search tin nhắn của conversation mà user không thuộc về.
```

Nếu hợp lệ, handler gọi:

```java
messageService.searchMessages(conversationId, keyword, limit, offset)
```

### 6.2. `Router.java`

File:

```text
Code/Server/src/main/java/com/server/tcp/Router.java
```

Mình thêm handler:

```java
private static SearchMessagesHandler searchMessagesHandler = new SearchMessagesHandler();
```

Và thêm case:

```java
case "SEARCH_MESSAGES":
    response = searchMessagesHandler.handleTcp(request, conn);
    break;
```

### 6.3. `MessageService.java`

File:

```text
Code/Server/src/main/java/com/server/service/MessageService.java
```

Mình thêm hàm:

```java
public List<Message> searchMessages(long conversationId, String keyword, int limit, int offset)
```

Service chỉ chuyển request xuống repository, giữ đúng mô hình:

```text
Handler -> Service -> Repository -> Database
```

### 6.4. `MessageRepository.java`

File:

```text
Code/Server/src/main/java/com/server/repository/MessageRepository.java
```

Mình thêm hàm:

```java
public List<Message> searchByConversation(long conversationId, String keyword, int limit, int offset)
```

Query đang dùng:

```sql
SELECT id, conversation_id, sender_id, type, content, created_at
FROM messages
WHERE conversation_id = ? AND LOWER(content) LIKE LOWER(?)
ORDER BY created_at DESC
LIMIT ? OFFSET ?
```

Ý nghĩa:

- Chỉ tìm trong conversation hiện tại.
- Tìm không phân biệt hoa thường.
- Sắp xếp tin mới hơn lên trước.
- Có `LIMIT` và `OFFSET` để sau này mở rộng phân trang.

## 7. Vì Sao Cần Kiểm Tra Member Của Conversation?

Nếu chỉ gửi `conversationId`, một client xấu có thể thử search conversation của người khác.

Vì vậy handler kiểm tra:

```java
conversationRepository.getMemberIds(conversationId).contains(userId)
```

Nếu user không thuộc conversation đó, server trả lỗi:

```text
Unauthorized message search request
```

Đây là điểm dễ bị giảng viên hỏi, nên cần giải thích rõ:

```text
Search message không chỉ là query DB. Server phải kiểm tra quyền trước, vì tin nhắn là dữ liệu riêng tư.
```

## 8. Các File Đã Đụng Tới

```text
Code/Client/src/main/java/ChatTcpClient.java
Code/Client/src/main/java/ChatView.java
Code/Server/src/main/java/com/server/handler/message/SearchMessagesHandler.java
Code/Server/src/main/java/com/server/tcp/Router.java
Code/Server/src/main/java/com/server/service/MessageService.java
Code/Server/src/main/java/com/server/repository/MessageRepository.java
Docs/11_Message_Search_TCP_Implementation.md
```

## 9. Cách Giải Thích Với Trưởng Nhóm

Có thể nói:

```text
Mình triển khai Message search theo TCP action SEARCH_MESSAGES. UI có ô tìm kiếm trong header chat. Client gửi conversationId và keyword qua ChatTcpClient. Server route qua Router tới SearchMessagesHandler, kiểm tra user có thuộc conversation không, rồi query messages.content trong DB và trả danh sách kết quả về UI.
```

## 10. Cách Trả Lời Nếu Giảng Viên Hỏi

Nếu thầy hỏi: "Tìm kiếm tin nhắn đi qua giao thức gì?"

Trả lời:

```text
Đi qua TCP socket. Client gửi action SEARCH_MESSAGES qua ChatTcpClient, không gọi HTTP endpoint.
```

Nếu thầy hỏi: "Server tìm theo cái gì?"

Trả lời:

```text
Server tìm theo conversationId và keyword. Query chỉ tìm trong bảng messages của conversation đang mở.
```

Nếu thầy hỏi: "Có kiểm tra quyền không?"

Trả lời:

```text
Có. Handler kiểm tra user hiện tại có thuộc conversation đó không rồi mới cho search.
```

Nếu thầy hỏi: "Tại sao cần limit và offset?"

Trả lời:

```text
Để tránh trả quá nhiều message một lần và để sau này có thể mở rộng phân trang kết quả search.
```

## 11. Kiểm Tra Cần Chạy

Sau khi code, cần chạy:

```powershell
cd Code/Client
mvn -q -DskipTests compile
```

```powershell
cd Code/Server
mvn -q -DskipTests compile
```
