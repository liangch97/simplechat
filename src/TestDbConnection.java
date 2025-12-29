package app;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.ResultSet;

import db.Db;
import util.Env;

/**
 * 数据库连接测试程序
 */
public class TestDbConnection {
    public static void main(String[] args) {
        System.out.println("========== 数据库连接测试 ==========");
        System.out.println();
        
        // 读取配置
        String url = Env.get("DB_URL", "未配置");
        String user = Env.get("DB_USER", "未配置");
        String password = Env.get("DB_PASSWORD", "未配置");
        
        System.out.println("配置信息：");
        System.out.println("  DB_URL:      " + url);
        System.out.println("  DB_USER:     " + (user.isEmpty() ? "未配置（使用Windows集成身份）" : user));
        System.out.println("  DB_PASSWORD: " + (password.isEmpty() ? "未配置" : "***"));
        System.out.println();
        
        // 检查是否启用
        if (!Db.enabled()) {
            System.out.println("❌ 数据库未配置");
            return;
        }
        
        // 检查数据库类型
        System.out.println("检测到数据库类型: " + db.Db.type());
        System.out.println();
        
        // 测试白名单中的所有房间秘钥
        String[] testRoomKeys = {"24336064", "061318", "public", "invalid_key"};
        
        for (String roomKey : testRoomKeys) {
            System.out.println("--- 测试房间秘钥: " + roomKey + " ---");
            
            // 检查秘钥是否有效
            if (!db.Db.isValidRoomKey(roomKey)) {
                System.out.println("  ✓ 正确拒绝无效秘钥");
                System.out.println();
                continue;
            }
            
            String dbName = db.Db.getDbNameForRoom(roomKey);
            System.out.println("  映射到数据库: " + dbName);
            
            try {
                // 加载驱动
                db.Db.ensureDriver();
                
                // 建立连接
                Connection conn = db.Db.getConnection(roomKey);
                System.out.println("  ✓ 连接成功！");
                
                // 测试查询
                var stmt = conn.createStatement();
                try {
                    var rs = stmt.executeQuery("SELECT COUNT(*) as count FROM messages");
                    if (rs.next()) {
                        int count = rs.getInt("count");
                        System.out.println("  ✓ messages 表存在，当前 " + count + " 条消息");
                    }
                    rs.close();
                } catch (Exception e) {
                    System.out.println("  ⚠ messages 表不存在或查询失败: " + e.getMessage());
                }
                
                stmt.close();
                conn.close();
                System.out.println("  ✓ 连接已关闭");
            } catch (Exception e) {
                System.out.println("  ❌ 连接失败: " + e.getMessage());
            }
            System.out.println();
        }
        
        System.out.println("========== 测试完成 ==========");
    }
}
