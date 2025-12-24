package db;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 聊天消息 DAO：创建表、写入、读取最近记录。
 */
public class MessageDao {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static void init() throws SQLException, ClassNotFoundException {
        if (!Db.enabled()) return;
        Db.ensureDriver();
        try (Connection c = Db.getConnection();
             Statement st = c.createStatement()) {
            String ddl;
            if (Db.type() == Db.DbType.SQLSERVER) {
                ddl = """
                    IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'messages')
                    BEGIN
                        CREATE TABLE dbo.messages (
                            id BIGINT IDENTITY(1,1) PRIMARY KEY,
                            nickname NVARCHAR(64) NOT NULL,
                            content NVARCHAR(MAX) NOT NULL,
                            created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME()
                        );
                    END
                """;
            } else {
                ddl = """
                    CREATE TABLE IF NOT EXISTS messages (
                      id BIGINT PRIMARY KEY AUTO_INCREMENT,
                      nickname VARCHAR(64) NOT NULL,
                      content TEXT NOT NULL,
                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """;
            }
            st.executeUpdate(ddl);
        }
    }

    public static void save(String nickname, String content, LocalDateTime createdAt) {
        if (!Db.enabled()) return; // 未配置数据库则跳过
        String sql = "INSERT INTO messages(nickname, content, created_at) VALUES(?,?,?)";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, nickname);
            ps.setString(2, content);
            ps.setTimestamp(3, Timestamp.valueOf(createdAt));
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] save failed: " + e.getMessage());
        }
    }

    public static List<String> latest(int limit) {
        return latest(limit, 0);
    }
    
    public static List<String> latest(int limit, int offset) {
        if (!Db.enabled()) return Collections.emptyList();
        if (limit <= 0) limit = 50;
        if (offset < 0) offset = 0;
        String sql;
        if (Db.type() == Db.DbType.SQLSERVER) {
            sql = "SELECT nickname, content, created_at FROM messages ORDER BY id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        } else {
            sql = "SELECT nickname, content, created_at FROM messages ORDER BY id DESC LIMIT ? OFFSET ?";
        }
        List<String> list = new ArrayList<>();
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (Db.type() == Db.DbType.SQLSERVER) {
                ps.setInt(1, offset);
                ps.setInt(2, limit);
            } else {
                ps.setInt(1, limit);
                ps.setInt(2, offset);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String nick = rs.getString(1);
                    String content = rs.getString(2);
                    Timestamp ts = rs.getTimestamp(3);
                    LocalDateTime time = ts.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                    String line = "[" + time.format(TS) + "] " + nick + ": " + content;
                    list.add(line);
                }
            }
        } catch (SQLException e) {
            System.err.println("[DB] latest failed: " + e.getMessage());
        }
        // 查询按 id DESC，需反转为时间正序
        Collections.reverse(list);
        return list;
    }
    
    /**
     * 获取消息总数
     */
    public static int getTotalCount() {
        if (!Db.enabled()) return 0;
        String sql = "SELECT COUNT(*) FROM messages";
        try (Connection c = Db.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("[DB] getTotalCount failed: " + e.getMessage());
        }
        return 0;
    }
}
