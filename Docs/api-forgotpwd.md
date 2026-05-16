# API Quên Mật Khẩu (Forgot Password)

API này cung cấp chức năng hỗ trợ người dùng khôi phục lại mật khẩu khi bị quên. Quy trình gồm 2 bước:
1. Yêu cầu cấp mã xác nhận (Reset Code).
2. Xác minh mã và đặt lại mật khẩu mới.

---

## 1. Yêu cầu mã xác nhận (Request Reset Code)

- **Đường dẫn (URL):** `/api/forgotpwd`
- **Phương thức (Method):** `GET`
- **Content-Type:** `application/json`

*(Lưu ý: Mặc dù là phương thức GET, nhưng theo thiết kế của dự án, API này yêu cầu đính kèm payload trong Request Body).*

### Request Body

```json
{
  "username": "tên_đăng_nhập_của_người_dùng"
}
```

### Responses

**Thành công (200 OK):**
```json
{
  "status": "success",
  "message": "Mã xác nhận đã được tạo.",
  "code": "123456"
}
```
> **⚠️ Cảnh báo Bảo mật:** Trong môi trường Production thực tế, KHÔNG BAO GIỜ trả về mã `code` trực tiếp trong Response để tránh bị hacker chiếm đoạt tài khoản. Mã này cần được gửi riêng tư qua Email hoặc SMS. Việc trả về ở đây chỉ nhằm phục vụ mục đích kiểm thử (testing) đồ án môn học cho tiện lợi.

**Lỗi - Thiếu Payload (400 Bad Request):**
```json
{
  "status": "error",
  "message": "Missing username in request body"
}
```

**Lỗi - Tài khoản không tồn tại (404 Not Found):**
```json
{
  "status": "error",
  "message": "Tài khoản không tồn tại"
}
```

---

## 2. Xác minh và Đổi mật khẩu mới (Reset Password)

- **Đường dẫn (URL):** `/api/forgotpwd`
- **Phương thức (Method):** `POST`
- **Content-Type:** `application/json`

### Request Body

```json
{
  "code": "123456",
  "password": "mat_khau_moi_cua_ban"
}
```

### Responses

**Thành công (200 OK):**
```json
{
  "status": "success",
  "message": "Đổi mật khẩu thành công"
}
```
*(Sau bước này, mật khẩu mới đã được băm (hash) bằng BCrypt và lưu an toàn vào cơ sở dữ liệu. Mã code cũng bị vô hiệu hóa ngay lập tức để tránh tái sử dụng).*

**Lỗi - Thiếu tham số (400 Bad Request):**
```json
{
  "status": "error",
  "message": "Thiếu mã xác nhận hoặc mật khẩu mới"
}
```

**Lỗi - Sai mã / Hết hạn (400 Bad Request):**
```json
{
  "status": "error",
  "message": "Mã xác nhận không hợp lệ hoặc đã hết hạn"
}
```
