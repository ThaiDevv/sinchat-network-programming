# Tai Lieu Giai Thich Kien Truc He Thong - SinChat Server

Tai lieu nay mo ta cach to chuc ma nguon theo mo hinh phan tang (Layered Architecture) ap dung trong SinChat Server.

---

## 1. Tong Quan Ve Kien Truc 4 Tang

He thong duoc thiet ke dua tren nguyen tac tach biet trach nhiem (Separation of Concerns). Ma nguon duoc chia thanh 4 tang chinh:

`Client <--> Handler Layer <--> Service Layer <--> Repository Layer <--> Database`

---

## 2. Chi Tiet Cac Tang

### 2.1. Tang Handler (Controller)
**Package**: `com.server.handler.*`
- **Chuc nang**: Xu ly giao thuc truyen tai (HTTP/WebSocket).
- **Nhiem vu**:
    - Tiep nhan yeu cau (Request) tu Client.
    - Phan tich du lieu JSON (Gson).
    - Dieu huong den cac Service tuong ung.
    - Phan hoi (Response) kem HTTP Status Code phu hop.

### 2.2. Tang Service (Business Logic)
**Package**: `com.server.service.*`
- **Chuc nang**: Chua toan bo logic nghiep vu cua ung dung.
- **Nhiem vu**:
    - Xu ly cac quy tac nghiep vu, tinh toan va kiem tra dieu kien.
    - Ma hoa du lieu nhay cam (BCrypt).
    - Dieu phoi giua cac Repository de thuc hien cac tac vu phuc tap.
    - Dam bao tinh doc lap voi giao thuc truyen tai.

### 2.3. Tang Repository (Data Access)
**Package**: `com.server.repository.*`
- **Chuc nang**: Giao tiep voi he quan tri co so du lieu (MySQL).
- **Nhiem vu**:
    - Quan ly ket noi thong qua Connection Pool (HikariCP).
    - Thuc thi cac cau lenh SQL (CRUD operations).
    - Su dung PreparedStatement de ngan chan tan cong SQL Injection.
    - Anh xa (Mapping) du lieu tu Database sang Object Java.

### 2.4. Tang Model (Domain Entities)
**Package**: `com.server.model.*`
- **Chuc nang**: Dinh nghia cau truc du lieu trong he thong.
- **Dac diem**:
    - Khop voi schema cua Database.
    - Bao gom cac thuc the chinh: User, Message, Conversation, Attachment.

---

## 3. Luong Xu Ly Du Lieu (Data Flow Example)

Quy trinh thuc hien mot tac vu (vi du: Dang ky tai khoan):

1. **Handler**: Tiep nhan JSON, trich xuat thong tin va goi Service.
2. **Service**: Thuc hien hashing mat khau, khoi tao doi tuong Model va goi Repository.
3. **Repository**: Thuc thi lenh INSERT vao Database.
4. **Ket qua**: Trang thai thanh cong hoac loi duoc tra nguoc ve theo chu trinh: Repository -> Service -> Handler -> Client.

---

## 4. Cong Nghe Su Dung (Technology Stack)

- **Connection Pool**: HikariCP (Toi uu hieu suat ket noi DB).
- **Security**: BCrypt (Ma hoa mat khau mot chieu).
- **JSON Parser**: Gson (Chuyen doi JSON - Java Object).
- **Configuration**: Dotenv (Quan ly bien moi truong).
- **Logging**: SLF4J / Logback (Ghi nhat ky he thong).
