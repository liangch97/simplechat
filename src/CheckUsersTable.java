import java.sql.*;
import db.Db;

public class CheckUsersTable {
    public static void main(String[] args) {
        System.out.println("========== 检查 users 表结构 ==========\n");
        
        try {
            Db.ensureDriver();
            checkDatabase("24336064");
            checkDatabase("061318");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void checkDatabase(String roomKey) {
        System.out.println("--- 检查 roomKey: " + roomKey + " ---");
        
        try (Connection c = Db.getConnection(roomKey);
             Statement st = c.createStatement()) {
            
            // 查询表是否存在
            ResultSet rs = st.executeQuery(
                "SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'users' ORDER BY ORDINAL_POSITION");
            
            boolean hasAny = false;
            while (rs.next()) {
                hasAny = true;
                String colName = rs.getString("COLUMN_NAME");
                String dataType = rs.getString("DATA_TYPE");
                System.out.println("  列: " + colName + " (" + dataType + ")");
            }
            
            if (!hasAny) {
                System.out.println("  ✗ users 表不存在或没有列");
            }
            
        } catch (SQLException e) {
            System.err.println("  错误: " + e.getMessage());
        }
    }
}
