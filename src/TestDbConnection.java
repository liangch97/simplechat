import java.sql.Connection;
import java.sql.Statement;
import java.sql.ResultSet;

/**
 * 数据库连接测试程序
 */
public class TestDbConnection {
    public static void main(String[] args) {
        System.out.println("========== 数据库连接测试 ==========");
        System.out.println();
        
        // 读取配置
        String url = util.Env.get("DB_URL", "未配置");
        String user = util.Env.get("DB_USER", "未配置");
        String password = util.Env.get("DB_PASSWORD", "未配置");
        
        System.out.println("配置信息：");
        System.out.println("  DB_URL:      " + url);
        System.out.println("  DB_USER:     " + (user.isEmpty() ? "未配置（使用Windows集成身份）" : user));
        System.out.println("  DB_PASSWORD: " + (password.isEmpty() ? "未配置" : "***"));
        System.out.println();
        
        // 检查是否启用
        if (!db.Db.enabled()) {
            System.out.println("❌ 数据库未配置");
            return;
        }
        
        // 检查数据库类型
        System.out.println("检测到数据库类型: " + db.Db.type());
        System.out.println();
        
        // 尝试连接
        System.out.println("正在连接数据库...");
        try {
            // 加载驱动
            db.Db.ensureDriver();
            System.out.println("✓ 驱动加载成功");
            
            // 建立连接
            Connection conn = db.Db.getConnection();
            System.out.println("✓ 连接成功！");
            System.out.println("  连接类型: " + conn.getClass().getName());
            
            // 测试查询
            var stmt = conn.createStatement();
            var rs = stmt.executeQuery("SELECT COUNT(*) as count FROM messages");
            if (rs.next()) {
                int count = rs.getInt("count");
                System.out.println("✓ 数据库查询成功！");
                System.out.println("  messages 表中共有 " + count + " 条消息");
            }
            
            rs.close();
            stmt.close();
            conn.close();
            
            System.out.println();
            System.out.println("========== 连接测试通过 ==========");
            
        } catch (ClassNotFoundException e) {
            System.out.println("❌ 驱动加载失败: " + e.getMessage());
            System.out.println("请确保 SQL Server JDBC 驱动已添加到 lib/ 目录");
            e.printStackTrace();
        } catch (Exception e) {
            System.out.println("❌ 连接失败: " + e.getMessage());
            System.out.println();
            System.out.println("可能的原因：");
            System.out.println("  1. SQL Server 服务未启动");
            System.out.println("  2. 连接字符串不正确");
            System.out.println("  3. 数据库不存在或无权限访问");
            System.out.println("  4. 防火墙阻止了连接");
            e.printStackTrace();
        }
    }
}
