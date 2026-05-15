# Tài Liệu Giải Thích Kiến Trúc Code Chi Tiết - SinChat Server (Tối Ưu Layer)

Tài liệu này cung cấp cái nhìn sâu sắc về cách tổ chức mã nguồn theo mô hình phân tầng chuẩn (Layered Architecture).

---

## 1. Tổng Quan Về Kiến Trúc 4 Tầng

Hệ thống được thiết kế để đảm bảo tính **tách biệt trách nhiệm (Separation of Concerns)**. Mỗi tầng thực hiện một nhiệm vụ chuyên biệt:

`Client <--> [Handler] <--> [Service] <--> [Repository] <--> Database`

---

## 2. Chi Tiết Các Tầng

### 2.1. Tầng Handler (Controller - API)
**Vị trí**: `com.server.handler.*`
- **Trách nhiệm**: Xử lý giao thức truyền tải (HTTP).
- **Nhiệm vụ cụ thể**:
    - Lắng nghe yêu cầu (Request) từ người dùng.
    - Phân tích cú pháp dữ liệu JSON (sử dụng Gson).
    - **Không xử lý logic**: Handler chỉ gọi xuống tầng Service và chờ kết quả.
    - Phản hồi (Response): Trả về kết quả kèm mã trạng thái HTTP (200, 401, 405, 500).

### 2.2. Tầng Service (Logic Nghiệp Vụ)
**Vị trí**: `com.server.service.*`
- **Trách nhiệm**: Chứa toàn bộ logic xử lý chính của ứng dụng.
- **Nhiệm vụ cụ thể**:
    - Thực hiện các phép tính toán, kiểm tra điều kiện.
    - **Bảo mật**: Mã hóa mật khẩu bằng BCrypt, so sánh Hash.
    - Phối hợp giữa các Repository: Ví dụ khi đăng ký, Service sẽ tạo đối tượng User, gọi Repository để lưu.
    - **Tính độc lập**: Tầng này không quan tâm dữ liệu đến từ HTTP hay giao diện nào khác, nó chỉ xử lý dữ liệu thô.

### 2.3. Tầng Repository (Data Access Object - DAO)
**Vị trí**: `com.server.repository.*`
- **Trách nhiệm**: Giao tiếp trực tiếp với Cơ sở dữ liệu (MySQL).
- **Nhiệm vụ cụ thể**:
    - Mở/Đóng kết nối thông qua Connection Pool (HikariCP).
    - Viết các câu lệnh SQL (`SELECT`, `INSERT`, `UPDATE`).
    - Ngăn chặn **SQL Injection** bằng cách dùng `PreparedStatement`.
    - Chuyển đổi kết quả từ Database thành các đối tượng Java (Model).

### 2.4. Tầng Model (Entities)
**Vị trí**: `com.server.model.*`
- **Trách nhiệm**: Định nghĩa các "khuôn mẫu" dữ liệu.
- **Đặc điểm**:
    - Khớp hoàn toàn với Schema của Database (Dùng `long` cho `bigint`, `Timestamp` cho `datetime`).
    - Bao gồm các lớp: `User`, `Message`, `Conversation`, `Friendship`, v.v.

---

## 3. Ví Dụ Luồng Xử Lý (Quy Trình Đăng Ký)

1. **Handler**: Nhận JSON `{username, password, email}`, parse ra dữ liệu và gọi `authService.register(...)`.
2. **Service**:
    - Nhận dữ liệu thô.
    - Gọi BCrypt để tạo chuỗi Hash từ mật khẩu.
    - Tạo đối tượng `User` mới với thông tin đã hash.
    - Gọi `userRepository.save(user)`.
3. **Repository**: Thực thi lệnh `INSERT` vào bảng `users`.
4. **Kết quả**: Thành công/Thất bại được truyền ngược lại từ Repository -> Service -> Handler để báo cho người dùng.

---

## 4. Các Công Nghệ Sử Dụng

- **HikariCP**: Quản lý bể kết nối (Pool) giúp tăng tốc độ truy cập DB gấp nhiều lần.
- **BCrypt**: Mã hóa mật khẩu một chiều cực kỳ an toàn.
- **Gson**: Chuyển đổi linh hoạt giữa String JSON và Object Java.
- **Dotenv**: Bảo mật thông tin cấu hình hệ thống.
- **SLF4J/Logback**: Ghi lại nhật ký hoạt động của server để dễ dàng theo dõi và sửa lỗi.
