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
                            created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
                            room_key NVARCHAR(128) NOT NULL DEFAULT ''
                        );
                    END
                """;
            } else {
                ddl = """
                    CREATE TABLE IF NOT EXISTS messages (
                      id BIGINT PRIMARY KEY AUTO_INCREMENT,
                      nickname VARCHAR(64) NOT NULL,
                      content TEXT NOT NULL,
                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      room_key VARCHAR(128) NOT NULL DEFAULT ''
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """;
            }
            st.executeUpdate(ddl);
            ensureRoomKeyColumn(c);
            // 迁移旧数据：将 room_key='public' 的消息更新为对应数据库的正确秘钥
            migratePublicRoomKey(c);
        }
    }

    public static void save(String nickname, String content, LocalDateTime createdAt, String roomKey) {
        if (!Db.enabled()) return; // 未配置数据库则跳过
        if (roomKey == null || roomKey.isBlank()) {
            System.err.println("[DB] save failed: roomKey is required");
            return;
        }
        ensureTable(roomKey);
        String sql = "INSERT INTO messages(nickname, content, created_at, room_key) VALUES(?,?,?,?)";
        try (Connection c = Db.getConnection(roomKey);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, nickname);
            ps.setString(2, content);
            ps.setTimestamp(3, Timestamp.valueOf(createdAt));
            ps.setString(4, normalizeRoomKey(roomKey));
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] save failed: " + e.getMessage());
        }
    }

    public static List<String> latest(int limit, int offset, String roomKey) {
        if (!Db.enabled()) return Collections.emptyList();
        ensureTable(roomKey);
        if (limit <= 0) limit = 50;
        if (offset < 0) offset = 0;
        String sql;
        if (Db.type() == Db.DbType.SQLSERVER) {
            sql = "SELECT nickname, content, created_at FROM messages WHERE room_key = ? ORDER BY id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        } else {
            sql = "SELECT nickname, content, created_at FROM messages WHERE room_key = ? ORDER BY id DESC LIMIT ? OFFSET ?";
        }
        List<String> list = new ArrayList<>();
        try (Connection c = Db.getConnection(roomKey);
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (Db.type() == Db.DbType.SQLSERVER) {
                ps.setString(1, normalizeRoomKey(roomKey));
                ps.setInt(2, offset);
                ps.setInt(3, limit);
            } else {
                ps.setString(1, normalizeRoomKey(roomKey));
                ps.setInt(2, limit);
                ps.setInt(3, offset);
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
    public static int getTotalCount(String roomKey) {
        if (!Db.enabled()) return 0;
        if (roomKey == null || roomKey.isBlank()) return 0;
        ensureTable(roomKey);
        String sql = "SELECT COUNT(*) FROM messages WHERE room_key = ?";
           try (Connection c = Db.getConnection(roomKey);
               PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, normalizeRoomKey(roomKey));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("[DB] getTotalCount failed: " + e.getMessage());
        }
        return 0;
    }

    /**
     * 获取指定时间之后的消息（用于增量加载）
     * @param since 时间戳（毫秒）
     */
    public static List<String> latestSince(long since, String roomKey) {
        if (!Db.enabled()) return Collections.emptyList();
        ensureTable(roomKey);
        String sql;
        if (Db.type() == Db.DbType.SQLSERVER) {
            sql = "SELECT nickname, content, created_at FROM messages WHERE room_key = ? AND created_at > ? ORDER BY id ASC";
        } else {
            sql = "SELECT nickname, content, created_at FROM messages WHERE room_key = ? AND created_at > ? ORDER BY id ASC";
        }
        List<String> list = new ArrayList<>();
        try (Connection c = Db.getConnection(roomKey);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, normalizeRoomKey(roomKey));
            ps.setTimestamp(2, new Timestamp(since));
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
            System.err.println("[DB] latestSince failed: " + e.getMessage());
        }
        return list;
    }

    private static String normalizeRoomKey(String roomKey) {
        if (roomKey == null) return "";
        String trimmed = roomKey.trim();
        if (trimmed.isEmpty()) return "";
        // 仅允许字母、数字、下划线和中划线，其他字符移除
        String safe = trimmed.replaceAll("[^A-Za-z0-9_-]", "");
        return safe;
    }

    /**
     * 迁移旧数据：将 room_key='public' 的消息更新为当前数据库对应的正确秘钥
     */
    private static void migratePublicRoomKey(Connection c) {
        // 获取当前数据库对应的正确秘钥
        String correctRoomKey = getCorrectRoomKeyForCurrentDb();
        if (correctRoomKey == null || correctRoomKey.isEmpty()) {
            return; // 无法确定正确秘钥，跳过迁移
        }
        
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE messages SET room_key = ? WHERE room_key = 'public'")) {
            ps.setString(1, correctRoomKey);
            int updated = ps.executeUpdate();
            if (updated > 0) {
                System.out.println("[DB] Migrated " + updated + " messages from 'public' to '" + correctRoomKey + "'");
            }
        } catch (SQLException e) {
            System.err.println("[DB] migratePublicRoomKey failed: " + e.getMessage());
        }
    }
    
    /**
     * 根据当前数据库 URL 确定正确的房间秘钥
     */
    private static String getCorrectRoomKeyForCurrentDb() {
        String url = Db.URL;
        if (url == null) return null;
        
        // 检查 URL 中包含的数据库名
        if (url.contains("simplechat")) {
            return "24336064";
        } else if (url.contains("homechat")) {
            return "061318";
        }
        return null;
    }

    private static void ensureRoomKeyColumn(Connection c) throws SQLException {
        // 检查 room_key 列是否存在，若不存在则添加
        boolean exists = false;
        String checkSql;
        if (Db.type() == Db.DbType.SQLSERVER) {
            checkSql = "SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'messages' AND COLUMN_NAME = 'room_key'";
        } else {
            checkSql = "SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'messages' AND column_name = 'room_key'";
        }
        try (PreparedStatement ps = c.prepareStatement(checkSql);
             ResultSet rs = ps.executeQuery()) {
            exists = rs.next();
        }

        if (!exists) {
            String alter;
            if (Db.type() == Db.DbType.SQLSERVER) {
                alter = "ALTER TABLE messages ADD room_key NVARCHAR(128) NOT NULL CONSTRAINT DF_messages_room_key DEFAULT ('')";
            } else {
                alter = "ALTER TABLE messages ADD COLUMN room_key VARCHAR(128) NOT NULL DEFAULT '' AFTER nickname";
            }
            try (Statement st = c.createStatement()) {
                st.executeUpdate(alter);
            }
        }
    }

    private static void ensureTable(String roomKey) {
        try {
            Db.ensureDriver();
            String ddl;
            if (Db.type() == Db.DbType.SQLSERVER) {
                ddl = """
                    IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'messages')
                    BEGIN
                        CREATE TABLE dbo.messages (
                            id BIGINT IDENTITY(1,1) PRIMARY KEY,
                            nickname NVARCHAR(64) NOT NULL,
                            content NVARCHAR(MAX) NOT NULL,
                            created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
                            room_key NVARCHAR(128) NOT NULL DEFAULT ''
                        );
                    END
                """;
            } else {
                ddl = """
                    CREATE TABLE IF NOT EXISTS messages (
                      id BIGINT PRIMARY KEY AUTO_INCREMENT,
                      nickname VARCHAR(64) NOT NULL,
                      content TEXT NOT NULL,
                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      room_key VARCHAR(128) NOT NULL DEFAULT ''
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """;
            }
            try (Connection c = Db.getConnection(roomKey);
                 Statement st = c.createStatement()) {
                st.execute(ddl);
            }
        } catch (Exception e) {
            System.err.println("[DB] ensureTable failed: " + e.getMessage());
        }
    }
}
