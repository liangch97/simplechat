package db;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.Base64;
import java.util.UUID;

/**
 * 用户 DAO：注册、登录、Token 验证
 * 使用密钥: 24336064
 */
public class UserDao {
    
    private static final String SECRET_KEY = "24336064";
    
    /**
     * 初始化用户表
     */
    public static void init() throws SQLException, ClassNotFoundException {
        if (!Db.enabled()) return;
        Db.ensureDriver();
        
        try (Connection c = Db.getConnection();
             Statement st = c.createStatement()) {
            
            String ddl;
            if (Db.type() == Db.DbType.SQLSERVER) {
                ddl = """
                    IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'users')
                    CREATE TABLE users (
                        id BIGINT IDENTITY(1,1) PRIMARY KEY,
                        username NVARCHAR(64) NOT NULL UNIQUE,
                        password_hash NVARCHAR(128) NOT NULL,
                        nickname NVARCHAR(64) NOT NULL,
                        created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
                        last_login DATETIME2 NULL
                    )
                    """;
            } else {
                ddl = """
                    CREATE TABLE IF NOT EXISTS users (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        username VARCHAR(64) NOT NULL UNIQUE,
                        password_hash VARCHAR(128) NOT NULL,
                        nickname VARCHAR(64) NOT NULL,
                        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        last_login DATETIME NULL
                    )
                    """;
            }
            st.execute(ddl);
        }
    }
    
    /**
     * 注册用户
     * @return null 表示成功，否则返回错误信息
     */
    public static String register(String username, String password, String nickname) {
        if (!Db.enabled()) return "数据库未配置";
        
        if (username == null || username.length() < 3 || username.length() > 20) {
            return "用户名长度需要3-20个字符";
        }
        if (password == null || password.length() < 6) {
            return "密码长度至少6个字符";
        }
        if (nickname == null || nickname.length() < 2 || nickname.length() > 20) {
            return "昵称长度需要2-20个字符";
        }
        
        // 检查用户名是否已存在
        if (userExists(username)) {
            return "用户名已存在";
        }
        
        String passwordHash = hashPassword(password);
        
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO users (username, password_hash, nickname) VALUES (?, ?, ?)")) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.setString(3, nickname);
            ps.executeUpdate();
            return null; // 成功
        } catch (SQLException e) {
            e.printStackTrace();
            return "注册失败: " + e.getMessage();
        }
    }
    
    /**
     * 登录验证
     * @return Token 字符串，失败返回 null
     */
    public static LoginResult login(String username, String password) {
        if (!Db.enabled()) return new LoginResult(null, null, "数据库未配置");
        
        if (username == null || password == null) {
            return new LoginResult(null, null, "用户名或密码不能为空");
        }
        
        String passwordHash = hashPassword(password);
        
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT id, nickname FROM users WHERE username = ? AND password_hash = ?")) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                long userId = rs.getLong("id");
                String nickname = rs.getString("nickname");
                
                // 更新最后登录时间
                updateLastLogin(userId);
                
                // 生成 Token
                String token = generateToken(userId, username);
                return new LoginResult(token, nickname, null);
            } else {
                return new LoginResult(null, null, "用户名或密码错误");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return new LoginResult(null, null, "登录失败: " + e.getMessage());
        }
    }
    
    /**
     * 验证 Token
     * @return 用户昵称，无效返回 null
     */
    public static String validateToken(String token) {
        if (token == null || token.isEmpty()) return null;
        
        try {
            // Token 格式: base64(userId:username:timestamp:signature)
            String decoded = new String(Base64.getDecoder().decode(token));
            String[] parts = decoded.split(":");
            if (parts.length != 4) return null;
            
            long userId = Long.parseLong(parts[0]);
            String username = parts[1];
            long timestamp = Long.parseLong(parts[2]);
            String signature = parts[3];
            
            // 检查签名
            String expectedSig = generateSignature(userId, username, timestamp);
            if (!signature.equals(expectedSig)) return null;
            
            // 检查是否过期 (24小时)
            long now = System.currentTimeMillis();
            if (now - timestamp > 24 * 60 * 60 * 1000) return null;
            
            // 从数据库获取昵称
            return getNickname(userId);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 检查用户名是否存在
     */
    private static boolean userExists(String username) {
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT 1 FROM users WHERE username = ?")) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }
    
    /**
     * 获取用户昵称
     */
    private static String getNickname(long userId) {
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT nickname FROM users WHERE id = ?")) {
            ps.setLong(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("nickname");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    /**
     * 更新最后登录时间
     */
    private static void updateLastLogin(long userId) {
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 Db.type() == Db.DbType.SQLSERVER 
                     ? "UPDATE users SET last_login = SYSUTCDATETIME() WHERE id = ?"
                     : "UPDATE users SET last_login = NOW() WHERE id = ?")) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 使用 SHA-256 + 密钥哈希密码
     */
    private static String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String salted = password + SECRET_KEY;
            byte[] hash = md.digest(salted.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * 生成 Token
     */
    private static String generateToken(long userId, String username) {
        long timestamp = System.currentTimeMillis();
        String signature = generateSignature(userId, username, timestamp);
        String tokenData = userId + ":" + username + ":" + timestamp + ":" + signature;
        return Base64.getEncoder().encodeToString(tokenData.getBytes());
    }
    
    /**
     * 生成签名
     */
    private static String generateSignature(long userId, String username, long timestamp) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String data = userId + ":" + username + ":" + timestamp + ":" + SECRET_KEY;
            byte[] hash = md.digest(data.getBytes());
            // 只取前8字节作为签名
            byte[] shortHash = new byte[8];
            System.arraycopy(hash, 0, shortHash, 0, 8);
            return Base64.getEncoder().encodeToString(shortHash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * 登录结果
     */
    public static class LoginResult {
        public final String token;
        public final String nickname;
        public final String error;
        
        public LoginResult(String token, String nickname, String error) {
            this.token = token;
            this.nickname = nickname;
            this.error = error;
        }
        
        public boolean success() {
            return error == null && token != null;
        }
    }
}
