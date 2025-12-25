import java.sql.*;
import db.Db;

public class FixUsersTable {
    public static void main(String[] args) {
        System.out.println("========== 为 users 表添加 room_key 列 ==========\n");
        
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
            
            System.out.println("  连接到: " + c.getCatalog() + ", schema: " + c.getSchema());
            
            // 先检查列是否存在
            boolean exists = false;
            try {
                ResultSet rs = st.executeQuery("SELECT TOP 1 room_key FROM users");
                exists = true;
                System.out.println("  ✓ room_key 列已存在");
            } catch (SQLException e) {
                System.out.println("  列不存在，错误: " + e.getMessage());
            }
            
            if (!exists) {
                // 添加 room_key 列（允许 NULL，有默认值）
                String sql = "ALTER TABLE users ADD room_key NVARCHAR(128) NULL CONSTRAINT DF_room_key_" + roomKey + " DEFAULT ''";
                System.out.println("  执行: " + sql);
                st.execute(sql);
                System.out.println("  ✓ room_key 列添加成功");
            }
            
            // 更新所有用户的 room_key
            int updated = st.executeUpdate("UPDATE users SET room_key = '" + roomKey + "' WHERE room_key = '' OR room_key IS NULL");
            System.out.println("  ✓ 更新了 " + updated + " 个用户的 room_key");
            
        } catch (SQLException e) {
            System.err.println("  错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
