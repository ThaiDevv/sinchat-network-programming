# Đánh giá & Đề xuất hoàn thiện Đồ án Lập trình mạng: Hệ thống Realtime Chat via TCP (SinChat)

Tài liệu này phân tích chi tiết các chức năng hiện có của hệ thống SinChat, chỉ ra những điểm còn thiếu để trở thành một đồ án Lập trình mạng hoàn chỉnh đạt điểm tối đa (A+), và đề xuất các chức năng cần bổ sung cụ thể cho cả Server và Client.

---

## 1. Phân tích hiện trạng SinChat (Tính năng hiện tại)
Hệ thống hiện tại đã có một nền tảng kỹ thuật khá tốt về mặt lập trình Socket TCP và xử lý dữ liệu:
*   **Networking:** Giao thức kết nối TCP Socket liên tục (Stateful), truyền nhận dữ liệu định dạng JSON kết thúc bằng ký tự xuống dòng (`\n`). Server sử dụng Java 25 với **Virtual Threads** giúp tối ưu hóa hiệu năng xử lý đa luồng đồng thời (high concurrency) mà không bị nghẽn. Xử lý đồng bộ ghi socket (`synchronized`) chống phân mảnh gói tin. Xử lý rò rỉ socket tốt.
*   **Security cơ bản:** Sử dụng **BCrypt** để băm mật khẩu, mã hóa OTP bằng `SecureRandom`, chống brute force OTP với giới hạn 5 lần nhập sai và TTL 5 phút, chống Timing Attack và User Enumeration trong luồng Quên mật khẩu. Sử dụng **PreparedStatement** chống SQL Injection.
*   **Database:** Kết nối MySQL 5.7 qua thư viện pooling **HikariCP** giúp tái sử dụng kết nối tối ưu.
*   **Tính năng ứng dụng cơ bản:** Đăng ký, Đăng nhập, Thay đổi Avatar, Lấy thông tin cá nhân (Profile), Lấy danh sách cuộc hội thoại, Lấy danh sách tin nhắn, Gửi tin nhắn văn bản, Trạng thái đang soạn thảo (Typing event).

---

## 2. Các chức năng còn thiếu & Đề xuất nâng cấp cho Đồ án Lập trình mạng
Để trở thành một đồ án xuất sắc cho môn **Lập trình mạng (Network Programming)**, hệ thống cần bổ sung các tính năng tập trung vào đặc trưng truyền thông mạng, quản lý kết nối vật lý, tối ưu hóa băng thông, bảo mật đường truyền và các tính năng tương tác thời gian thực cao cấp.

Dưới đây là bảng phân tích chi tiết các chức năng thiếu và giải pháp thực hiện:

### 2.1. Quản lý trạng thái kết nối & Cơ chế duy trì kết nối mạng (Heartbeat / Keep-Alive)
*   **Vấn đề hiện tại:** Socket TCP có thể bị đứt ngầm do tường lửa, mạng chập chờn, hoặc Client tắt đột ngột mà không đóng kết nối sạch sẽ. Server vẫn giữ Socket mở vô thời hạn gây lãng phí tài nguyên và sai lệch trạng thái người dùng (người dùng vẫn hiển thị online dù đã mất kết nối).
*   **Đề xuất bổ sung:**
    *   **Heartbeat Mechanism (Ping/Pong):** Định kỳ (ví dụ mỗi 30 giây), Client gửi gói tin `PING` lên Server. Server nhận được sẽ phản hồi `PONG` và cập nhật timestamp hoạt động cuối cùng của kết nối đó.
    *   **Idle Timeout:** Nếu quá 60 giây Server không nhận được bất kỳ dữ liệu nào (bao gồm cả `PING`) từ một kết nối, Server sẽ tự động đóng kết nối, giải phóng tài nguyên và broadcast trạng thái offline của User đó.
    *   **Auto Reconnect (Client):** Client tự động phát hiện kết nối đứt và thực hiện cơ chế thử lại (Backoff retry) để kết nối lại mà không yêu cầu người dùng đăng nhập lại từ đầu (sử dụng Token/Session tạm thời).

### 2.2. Gửi và Nhận File (File Transfer / Attachment) qua TCP
*   **Vấn đề hiện tại:** Hệ thống đã định nghĩa model `Attachment` nhưng luồng giao thức TCP chưa hỗ trợ truyền tải file thực tế (hình ảnh, tài liệu) mà chỉ mới hỗ trợ tin nhắn văn bản thuần túy.
*   **Đề xuất bổ sung:**
    *   **Truyền file dạng Binary Stream hoặc Base64 Chunking:** Vì TCP Socket truyền JSON kết thúc bằng `\n`, việc gửi file lớn trực tiếp trong JSON sẽ làm phình gói tin và dễ gây crash bộ nhớ (OOM). 
    *   **Giải pháp 1 (Khuyên dùng cho TCP thuần):** Thiết kế cổng TCP thứ hai (File Port - ví dụ 3001) chuyên để truyền nhận luồng byte file trực tiếp. Khi Client muốn gửi file, nó sẽ yêu cầu Server cấp một File ID qua cổng 3000, sau đó kết nối đến cổng 3001, gửi header chứa `[File ID][File Size]` tiếp theo là ghi trực tiếp Byte Stream của file.
    *   **Giải pháp 2 (Tích hợp HTTP):** Server dựng một endpoint HTTP nhỏ chuyên dùng để upload/download file. Client tải file lên qua HTTP, nhận về URL tĩnh, rồi gửi URL này làm nội dung tin nhắn qua cổng TCP 3000.

### 2.3. Mã hóa Bảo mật đường truyền (SSL/TLS Socket)
*   **Vấn đề hiện tại:** Dữ liệu truyền trên đường truyền TCP (bao gồm cả mật khẩu đăng nhập, nội dung tin nhắn) đều là văn bản JSON thuần túy (Plaintext). Kẻ tấn công trên cùng mạng LAN có thể dễ dàng sử dụng các công cụ như Wireshark để bắt gói tin và đọc trộm toàn bộ thông tin (Eavesdropping).
*   **Đề xuất bổ sung:**
    *   Tích hợp mã hóa **SSL/TLS** bằng cách chuyển đổi kết nối từ Socket thường sang **SSLSocket** / **SSLServerSocket** trong Java.
    *   Tự tạo hoặc sử dụng chứng chỉ số (Keystore/Truststore) để thiết lập kênh truyền mã hóa bảo mật toàn vẹn (Transport Layer Security).

### 2.4. Trạng thái hoạt động trực tuyến thời gian thực (Realtime Online Status & Presence)
*   **Vấn đề hiện tại:** Cột `is_online` và `last_seen` đã có trong database nhưng hệ thống chưa tự động cập nhật và phát đi (broadcast) trạng thái online/offline của người dùng tới danh sách bạn bè khi họ kết nối hoặc ngắt kết nối.
*   **Đề xuất bổ sung:**
    *   Khi một Client kết nối và thực hiện hành động `JOIN` thành công, Server tự động đổi trạng thái User thành `online = true`, sau đó gửi thông báo sự kiện `USER_STATUS_EVENT` tới tất cả các Client đang online khác là bạn bè của user đó.
    *   Khi Client ngắt kết nối (`disconnect` hoặc bị ngắt kết nối ngầm), Server tự động bắt sự kiện, cập nhật database `is_online = false`, `last_seen = CURRENT_TIMESTAMP` và phát thông báo offline tới bạn bè.

### 2.5. Phòng chat nhóm nâng cao (Group Chat Features & Broadcast)
*   **Vấn đề hiện tại:** Hệ thống có bảng cuộc hội thoại loại `GROUP` nhưng chưa có giao thức hoàn chỉnh để người dùng tự tạo nhóm, mời thành viên vào nhóm, và phát tin nhắn đến toàn bộ thành viên trong nhóm đang hoạt động trực tuyến.
*   **Đề xuất bổ sung:**
    *   **Action `CREATE_GROUP`:** Cho phép một user tạo cuộc trò chuyện nhóm mới với danh sách thành viên ban đầu.
    *   **Action `ADD_GROUP_MEMBERS` / `LEAVE_GROUP`:** Thêm người vào nhóm hoặc rời khỏi nhóm.
    *   **Group Broadcast:** Khi một tin nhắn được gửi tới một `GROUP` conversation, Server phải truy vấn toàn bộ danh sách `user_id` thuộc nhóm đó từ bảng `conversation_members`, và gửi tin nhắn tới mọi kết nối đang hoạt động của các thành viên trực tuyến đó.

### 2.6. Quản lý danh sách bạn bè & Yêu cầu kết bạn (Friendship Management)
*   **Vấn đề hiện tại:** Giao tiếp chat hiện tại chỉ là kết nối tự do, chưa có sự ràng buộc về mặt quan hệ bạn bè để kiểm soát quyền riêng tư.
*   **Đề xuất bổ sung:**
    *   Các hành động TCP: `SEND_FRIEND_REQUEST` (Gửi yêu cầu kết bạn), `ACCEPT_FRIEND_REQUEST` (Đồng ý kết bạn), `DECLINE_FRIEND_REQUEST` (Từ chối kết bạn), `REMOVE_FRIEND` (Hủy kết bạn), `GET_FRIENDS_LIST` (Lấy danh sách bạn bè kèm trạng thái online/offline).
    *   Chỉ cho phép những người đã là bạn bè nhắn tin trực tiếp với nhau (Private Chat) để bảo vệ sự riêng tư.

### 2.7. Giao diện Client trực quan & Thân thiện hơn (UI/UX)
*   **Vấn đề hiện tại:** Client sử dụng JavaFX cơ bản, cần cải tiến giao diện trực quan và phản hồi mượt mà hơn với các sự kiện mạng.
*   **Đề xuất bổ sung:**
    *   Hiển thị dấu tròn xanh/xám thể hiện trạng thái Online/Offline trực tiếp trên avatar của danh sách bạn bè/cuộc hội thoại.
    *   Thêm âm thanh thông báo sinh động khi nhận được tin nhắn mới khi đang ẩn ứng dụng.
    *   Hiển thị bong bóng chat sinh động chứa thông tin người gửi, thời gian gửi tin nhắn chi tiết.

---

## 3. Bản thiết kế chi tiết Giao thức TCP bổ sung (TCP API Specification)

Dưới đây là đặc tả chi tiết các gói tin JSON cần bổ sung cho các tính năng trên:

### 3.1. Giao thức Duy trì kết nối (Heartbeat)
#### Client gửi Ping (Định kỳ 30s)
```json
{
  "action": "PING",
  "requestId": "ping-1716281000"
}
```
#### Server phản hồi Pong
```json
{
  "action": "PING_RESPONSE",
  "requestId": "ping-1716281000",
  "status": "success"
}
```

### 3.2. Giao thức Trạng thái hoạt động (Presence Status)
#### Server tự động phát (Broadcast) trạng thái khi User A online/offline
```json
{
  "action": "USER_STATUS_EVENT",
  "userId": 42,
  "status": "online", // hoặc "offline"
  "lastSeen": "2026-05-21 08:45:00"
}
```

### 3.3. Giao thức Tạo nhóm trò chuyện (Create Group Chat)
#### Client gửi yêu cầu tạo nhóm
```json
{
  "action": "CREATE_GROUP",
  "requestId": "req-group-1",
  "name": "Nhóm Lập Trình Mạng",
  "creatorId": 12,
  "memberIds": [12, 13, 14, 15]
}
```
#### Server phản hồi kết quả tạo nhóm thành công
```json
{
  "action": "CREATE_GROUP_RESPONSE",
  "requestId": "req-group-1",
  "status": "success",
  "conversationId": 202,
  "name": "Nhóm Lập Trình Mạng",
  "type": "GROUP"
}
```

### 3.4. Giao thức Quản lý kết bạn (Friendship Actions)
#### Client gửi yêu cầu kết bạn
```json
{
  "action": "FRIEND_REQUEST",
  "subAction": "SEND",
  "requestId": "req-friend-1",
  "senderId": 12,
  "receiverUsername": "alice"
}
```
#### Server phản hồi kết quả gửi
```json
{
  "action": "FRIEND_REQUEST_RESPONSE",
  "requestId": "req-friend-1",
  "status": "success",
  "message": "Đã gửi yêu cầu kết bạn thành công."
}
```

---

## 4. Kết luận & Định hướng thực hiện đồ án xuất sắc
Việc SinChat đã đạt được mức độ tối ưu hóa hiệu năng cực cao bằng **Virtual Threads (Java 25)** kết hợp với bảo mật chống brute-force và mã hóa băm bảo vệ cơ sở dữ liệu đã là một bước tiến vượt bậc so với các đồ án Socket thông thường sử dụng ThreadPool truyền thống.

Để đạt được điểm tối đa, nhóm phát triển nên ưu tiên triển khai thêm **2 tính năng quan trọng nhất của Lập trình mạng** là:
1.  **Cơ chế Heartbeat (Ping/Pong)** để giải phóng tài nguyên hệ thống thực tế và quản lý trạng thái online chính xác.
2.  **Kênh truyền SSL/TLS mã hóa** nhằm bảo vệ dữ liệu trên mạng truyền thông thuần TCP.

Các tài liệu kỹ thuật chi tiết đã có sẵn và các thay đổi bổ sung trên có thể được phát triển tích hợp dễ dàng vào cấu trúc lớp hiện tại của hệ thống.
