package app;

import java.sql.*;
import db.Db;

public class DebugDb {
    public static void main(String[] args) {
        try {
            Db.ensureDriver();
            
            try (Connection c = Db.getConnection("24336064");
                 Statement st = c.createStatement()) {
                
                System.out.println("连接成功到数据库");
                System.out.println("Catalog: " + c.getCatalog());
                System.out.println("Schema: " + c.getSchema());
                
                // 列出所有表
                System.out.println("\n所有表:");
                ResultSet rs = st.executeQuery("SELECT TABLE_SCHEMA, TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE = 'BASE TABLE'");
                while (rs.next()) {
                    System.out.println("  " + rs.getString("TABLE_SCHEMA") + "." + rs.getString("TABLE_NAME"));
                }
                
                // 尝试直接查询 users 表
                System.out.println("\n尝试查询 users 表:");
                try {
                    rs = st.executeQuery("SELECT COUNT(*) FROM users");
                    if (rs.next()) {
                        System.out.println("  users 表有 " + rs.getInt(1) + " 条记录");
                    }
                } catch (SQLException e) {
                    System.out.println("  查询失败: " + e.getMessage());
                }
                
                // 尝试查询 dbo.users
                System.out.println("\n尝试查询 dbo.users:");
                try {
                    rs = st.executeQuery("SELECT COUNT(*) FROM dbo.users");
                    if (rs.next()) {
                        System.out.println("  dbo.users 表有 " + rs.getInt(1) + " 条记录");
                    }
                } catch (SQLException e) {
                    System.out.println("  查询失败: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
