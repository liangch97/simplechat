package db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
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
}
