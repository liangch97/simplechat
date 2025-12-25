import java.sql.*;
import db.Db;

/**
 * 为 users 表添加 room_key 列
 */
public class AddRoomKeyColumn {
    public static void main(String[] args) {
        System.out.println("========== 初始化 users 表并添加 room_key 列 ==========\n");
        
        if (!Db.enabled()) {
            System.err.println("数据库未配置");
            return;
        }
        
        try {
            Db.ensureDriver();
            initDatabase("simplechat", "24336064");
            initDatabase("homechat", "061318");
            System.out.println("\n========== 完成 ==========");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void initDatabase(String dbName, String defaultRoomKey) {
        System.out.println("--- 处理数据库: " + dbName + " ---");
        
        try (Connection c = Db.getConnection(defaultRoomKey);
             Statement st = c.createStatement()) {
            if (c == null) {
                System.out.println("  无法连接到数据库 " + dbName);
                return;
            }
            
            // 创建表（如果不存在）
            String createTable = """
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'users')
                CREATE TABLE users (
                    id BIGINT IDENTITY(1,1) PRIMARY KEY,
                    username NVARCHAR(64) NOT NULL UNIQUE,
                    password_hash NVARCHAR(128) NOT NULL,
                    nickname NVARCHAR(64) NOT NULL,
                    room_key NVARCHAR(128) NOT NULL DEFAULT '',
                    created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
                    last_login DATETIME2 NULL
                )
                """;
            st.execute(createTable);
            System.out.println("  ✓ users 表已确保存在");
            
            // 检查是否有 room_key 列 - 用 SQL 查询代替 getMetaData
            boolean hasRoomKey = false;
            try {
                ResultSet rs = st.executeQuery("SELECT TOP 1 room_key FROM users");
                hasRoomKey = true;
                System.out.println("  ✓ room_key 列已存在");
            } catch (SQLException e) {
                // 列不存在
                hasRoomKey = false;
            }
            
            if (!hasRoomKey) {
                System.out.println("  → 添加 room_key 列...");
                st.execute("ALTER TABLE users ADD room_key NVARCHAR(128) NOT NULL DEFAULT ''");
                System.out.println("  ✓ room_key 列添加成功");
            } else {
                System.out.println("  ✓ room_key 列已存在");
            }
            
            // 更新空值
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE users SET room_key = ? WHERE room_key = '' OR room_key IS NULL")) {
                ps.setString(1, defaultRoomKey);
                int updated = ps.executeUpdate();
                if (updated > 0) {
                    System.out.println("  ✓ 已迁移 " + updated + " 个用户的 room_key 为 '" + defaultRoomKey + "'");
                } else {
                    System.out.println("  ✓ 无需迁移");
                }
            }
            
        } catch (SQLException e) {
            System.err.println("  ✗ 错误: " + e.getMessage());
        }
    }
}
