package com.example.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Database {
    // TODO: Điền thông tin kết nối Database của bạn vào đây
    private static final String URL = "jdbc:mysql://free02.123host.vn:3306/roacqgfa_ltm";
    private static final String USER = "roacqgfa_ltm";
    private static final String PASSWORD = "11111111";

    public static Connection getConnection() throws SQLException {
        // Mở và trả về một connection tới MySQL
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
