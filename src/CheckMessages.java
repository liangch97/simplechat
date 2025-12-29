package app;

import java.sql.*;
import db.Db;

public class CheckMessages {
    public static void main(String[] args) {
        try {
            Db.ensureDriver();
            
            System.out.println("========== 检查 messages 表 ==========\n");
            
            try (Connection c = Db.getConnection("24336064");
                 Statement st = c.createStatement()) {
                
                // 检查 room_key 列是否存在
                System.out.println("--- messages 表结构 ---");
                ResultSet rs = st.executeQuery(
                    "SELECT COLUMN_NAME, DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'messages'");
                while (rs.next()) {
                    System.out.println("  " + rs.getString("COLUMN_NAME") + " (" + rs.getString("DATA_TYPE") + ")");
                }
                
                // 统计各个 room_key 的消息数量
                System.out.println("\n--- room_key 分布 ---");
                rs = st.executeQuery(
                    "SELECT room_key, COUNT(*) as cnt FROM messages GROUP BY room_key ORDER BY cnt DESC");
                while (rs.next()) {
                    String key = rs.getString("room_key");
                    int cnt = rs.getInt("cnt");
                    System.out.println("  '" + (key == null ? "NULL" : key) + "': " + cnt + " 条消息");
                }
                
                // 总数
                rs = st.executeQuery("SELECT COUNT(*) FROM messages");
                if (rs.next()) {
                    System.out.println("\n总计: " + rs.getInt(1) + " 条消息");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
