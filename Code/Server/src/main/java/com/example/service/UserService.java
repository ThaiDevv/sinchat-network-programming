package com.example.service;

import com.example.config.Database;
import com.example.model.User;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class UserService {

    public boolean registerUser(User user) {
        // 1. Kiểm tra xem username hoặc email đã tồn tại trong DB chưa
        String checkSql = "SELECT id FROM users WHERE username = ? OR email = ?";
        try (Connection conn = Database.getConnection();
                PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {

            checkStmt.setString(1, user.getUsername());
            checkStmt.setString(2, user.getEmail());
            ResultSet rs = checkStmt.executeQuery();

            if (rs.next()) {
                // Nếu tìm thấy kết quả nghĩa là user hoặc email đã tồn tại
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        // 2. Mã hóa mật khẩu trước khi lưu
        String hashedPassword = BCrypt.hashpw(user.getPassword(), BCrypt.gensalt());

        // 3. Lưu user mới vào database
        String insertSql = "INSERT INTO users (username, email, password_hash) VALUES (?, ?, ?)";
        try (Connection conn = Database.getConnection();
                PreparedStatement stmt = conn.prepareStatement(insertSql)) {

            stmt.setString(1, user.getUsername());
            stmt.setString(2, user.getEmail());
            stmt.setString(3, hashedPassword); // Lưu mật khẩu đã mã hóa

            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
