package db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import util.Env;


/**
 * 数据库连接工具类（MySQL）。
 * 通过环境变量或 .env 配置以下项：
 *  - DB_URL      例如 jdbc:mysql://localhost:3306/simplechat?useSSL=false&serverTimezone=UTC&characterEncoding=utf8&allowPublicKeyRetrieval=true
 *  - DB_USER     数据库用户名
 *  - DB_PASSWORD 数据库密码
 */
public class Db {
    public enum DbType { MYSQL, SQLSERVER, UNKNOWN }
    public static final String URL = Env.get("DB_URL",
            "");
    public static final String USER = Env.get("DB_USER", "");
    public static final String PASSWORD = Env.get("DB_PASSWORD", "");
    // 房间秘钥 -> 数据库名 白名单映射（只允许这些秘钥）
    private static final java.util.Map<String, String> ROOM_DB_WHITELIST = java.util.Map.of(
        "24336064", "simplechat",
        "061318", "homechat"
    );

    public static boolean enabled() {
        return URL != null && !URL.isBlank();
    }

    public static DbType type() {
        if (URL == null) return DbType.UNKNOWN;
        String u = URL.toLowerCase();
        if (u.startsWith("jdbc:mysql:")) return DbType.MYSQL;
        if (u.startsWith("jdbc:sqlserver:")) return DbType.SQLSERVER;
        return DbType.UNKNOWN;
    }

    public static void ensureDriver() throws ClassNotFoundException {
        DbType t = type();
        if (t == DbType.MYSQL) {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } else if (t == DbType.SQLSERVER) {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        } else {
            // 尝试加载常见驱动以便容错
            try { Class.forName("com.mysql.cj.jdbc.Driver"); } catch (Throwable ignored) {}
            try { Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver"); } catch (Throwable ignored) {}
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    /**
     * 获取指定房间对应数据库的连接。只允许白名单中的秘钥，否则抛出异常。
     */
    public static Connection getConnection(String roomKey) throws SQLException {
        if (roomKey == null || roomKey.isBlank()) {
            throw new SQLException("房间秘钥不能为空");
        }
        String dbName = getDbNameForRoom(roomKey);
        if (dbName == null) {
            throw new SQLException("无效的房间秘钥: " + roomKey);
        }
        String roomUrl = buildRoomDbUrl(dbName);
        return DriverManager.getConnection(roomUrl, USER, PASSWORD);
    }

    /**
     * 根据房间秘钥查询白名单，返回对应的数据库名。如果秘钥不在白名单中，返回 null。
     */
    public static String getDbNameForRoom(String roomKey) {
        if (roomKey == null || roomKey.isBlank()) {
            return null;  // 空秘钥无效
        }
        return ROOM_DB_WHITELIST.get(roomKey.trim());
    }

    /**
     * 检查房间秘钥是否有效（在白名单中）
     */
    public static boolean isValidRoomKey(String roomKey) {
        if (roomKey == null || roomKey.isBlank()) {
            return false;  // 空秘钥无效
        }
        return ROOM_DB_WHITELIST.containsKey(roomKey.trim());
    }

    private static String buildRoomDbUrl(String dbName) {
        String u = URL;
        if (u.contains("{db}")) return u.replace("{db}", dbName);
        if (u.contains("${DB}")) return u.replace("${DB}", dbName);
        if (u.contains("${DATABASE}")) return u.replace("${DATABASE}", dbName);
        DbType t = type();
        if (t == DbType.MYSQL) {
            // jdbc:mysql://host:port/db?params
            int q = u.indexOf('?');
            String params = q >= 0 ? u.substring(q) : "";
            String withoutParams = q >= 0 ? u.substring(0, q) : u;
            int slash = withoutParams.indexOf("//");
            if (slash < 0) return u; // 异常格式，原样返回
            int firstPath = withoutParams.indexOf('/', slash + 2);
            if (firstPath < 0) {
                // 没有 db 段
                return withoutParams + "/" + dbName + params;
            }
            // 替换 db 段
            return withoutParams.substring(0, firstPath + 1) + dbName + params;
        } else if (t == DbType.SQLSERVER) {
            // jdbc:sqlserver://host;...;databaseName=db;...
            String key = ";databaseName=";
            int idx = u.toLowerCase().indexOf(";databasename=");
            if (idx >= 0) {
                int start = idx + key.length();
                int end = u.indexOf(';', start);
                if (end < 0) end = u.length();
                return u.substring(0, start) + dbName + u.substring(end);
            }
            return u + ";databaseName=" + dbName;
        }
        return u;
    }

    // 不再自动创建数据库，只允许白名单中预先创建好的数据库
}
