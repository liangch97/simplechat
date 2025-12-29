package app;

import java.sql.*;
import db.Db;

public class FixUsersTableV2 {
    public static void main(String[] args) {
        System.out.println("========== 为 users 表添加 room_key 列 (V2) ==========\n");
        
        try {
            Db.ensureDriver();
            fixDatabase("24336064");
            fixDatabase("061318");
            System.out.println("\n========== 完成 ==========");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void fixDatabase(String roomKey) {
        System.out.println("--- 修复 roomKey: " + roomKey + " ---");
        
        try (Connection c = Db.getConnection(roomKey);
             Statement st = c.createStatement()) {
            
            String catalog = c.getCatalog();
            System.out.println("  连接到: " + catalog);
            
            // 检查用户权限
            System.out.println("  检查权限...");
            ResultSet rs = st.executeQuery("SELECT HAS_PERMS_BY_NAME('users', 'OBJECT', 'ALTER') AS can_alter");
            if (rs.next()) {
                System.out.println("  ALTER权限: " + rs.getInt("can_alter"));
            }
            
            // 用 INFORMATION_SCHEMA 检查列是否存在
            rs = st.executeQuery("SELECT COUNT(*) AS cnt FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'users' AND COLUMN_NAME = 'room_key'");
            boolean exists = false;
            if (rs.next()) {
                exists = rs.getInt("cnt") > 0;
            }
            System.out.println("  room_key 列存在: " + exists);
            
            if (!exists) {
                // 使用 sp_executesql 执行 ALTER
                String sql = "EXEC sp_executesql N'ALTER TABLE [dbo].[users] ADD [room_key] NVARCHAR(128) NULL'";
                System.out.println("  执行: " + sql);
                st.execute(sql);
                System.out.println("  ✓ room_key 列添加成功");
            }
            
            // 更新所有用户的 room_key
            int updated = st.executeUpdate("UPDATE [" + catalog + "].[dbo].[users] SET room_key = '" + roomKey + "' WHERE room_key = '' OR room_key IS NULL");
            System.out.println("  ✓ 更新了 " + updated + " 个用户的 room_key");
            
        } catch (SQLException e) {
            System.err.println("  错误: " + e.getMessage());
        }
    }
}
