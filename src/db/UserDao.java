package db;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * 用户 DAO：注册、登录、Token 验证
 * 使用密钥: 24336064
 */
public class UserDao {
    
    private static final String SECRET_KEY = "24336064";
    // 管理员用户名列表
    private static final java.util.Set<String> ADMIN_USERNAMES = java.util.Set.of("liangch97");
    
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
                        room_key NVARCHAR(128) NOT NULL DEFAULT '',
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
                        room_key VARCHAR(128) NOT NULL DEFAULT '',
                        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        last_login DATETIME NULL
                    )
                    """;
            }
            st.execute(ddl);
            
            // 尝试添加 room_key 字段（如果表已存在但没有该字段）
            try {
                if (Db.type() == Db.DbType.SQLSERVER) {
                    st.execute("IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('users') AND name = 'room_key') ALTER TABLE users ADD room_key NVARCHAR(128) NOT NULL DEFAULT ''");
                } else {
                    // MySQL: 尝试添加，如果已存在则忽略
                    st.execute("ALTER TABLE users ADD COLUMN room_key VARCHAR(128) NOT NULL DEFAULT ''");
                }
            } catch (SQLException ignored) {
                // 字段已存在，忽略
            }
            
            // 迁移旧用户：将空 room_key 的用户更新为当前数据库对应的正确秘钥
            migrateEmptyRoomKey(c);
        }
    }
    
    /**
     * 迁移旧用户：将空 room_key 的用户更新为当前数据库对应的正确秘钥
     */
    private static void migrateEmptyRoomKey(Connection c) {
        String correctRoomKey = getCorrectRoomKeyForCurrentDb();
        if (correctRoomKey == null || correctRoomKey.isEmpty()) {
            return;
        }
        
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE users SET room_key = ? WHERE room_key = '' OR room_key IS NULL")) {
            ps.setString(1, correctRoomKey);
            int updated = ps.executeUpdate();
            if (updated > 0) {
                System.out.println("[DB] Migrated " + updated + " users with empty room_key to '" + correctRoomKey + "'");
            }
        } catch (SQLException e) {
            System.err.println("[DB] migrateEmptyRoomKey failed: " + e.getMessage());
        }
    }
    
    /**
     * 根据当前数据库 URL 确定正确的房间秘钥
     */
    private static String getCorrectRoomKeyForCurrentDb() {
        String url = Db.URL;
        if (url == null) return null;
        
        if (url.contains("simplechat")) {
            return "24336064";
        } else if (url.contains("homechat")) {
            return "061318";
        }
        return null;
    }
    
    /**
     * 注册用户
     * @param roomKey 注册秘钥，同时作为用户的房间秘钥
     * @return null 表示成功，否则返回错误信息
     */
    public static String register(String username, String password, String nickname, String roomKey) {
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
        if (roomKey == null || roomKey.isBlank()) {
            return "注册秘钥不能为空";
        }
        
        // 验证秘钥是否有效（必须在白名单中）
        if (!Db.isValidRoomKey(roomKey)) {
            return "无效的注册秘钥";
        }
        
        // 检查用户名是否已存在
        if (userExists(username)) {
            return "用户名已存在";
        }
        
        String passwordHash = hashPassword(password);
        
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO users (username, password_hash, nickname, room_key) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.setString(3, nickname);
            ps.setString(4, roomKey);
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
        if (!Db.enabled()) return new LoginResult(null, null, null, false, "数据库未配置");
        
        if (username == null || password == null) {
            return new LoginResult(null, null, null, false, "用户名或密码不能为空");
        }
        
        String passwordHash = hashPassword(password);
        
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT id, nickname, room_key FROM users WHERE username = ? AND password_hash = ?")) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                long userId = rs.getLong("id");
                String nickname = rs.getString("nickname");
                String roomKey = rs.getString("room_key");
                
                // 更新最后登录时间
                updateLastLogin(userId);
                
                // 生成 Token
                String token = generateToken(userId, username);
                boolean admin = isAdmin(username);
                return new LoginResult(token, nickname, roomKey, admin, null);
            } else {
                return new LoginResult(null, null, null, false, "用户名或密码错误");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return new LoginResult(null, null, null, false, "登录失败: " + e.getMessage());
        }
    }
    
    /**
     * 验证 Token
     * @return 用户信息，无效返回 null
     */
    public static UserInfo validateToken(String token) {
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
            
            // 从数据库获取用户信息
            return getUserInfo(userId);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 用户信息类（用于 Token 验证结果）
     */
    public static class UserInfo {
        public final long userId;
        public final String username;
        public final String nickname;
        public final String roomKey;
        public final boolean isAdmin;
        
        public UserInfo(long userId, String username, String nickname, String roomKey) {
            this.userId = userId;
            this.username = username;
            this.nickname = nickname;
            this.roomKey = roomKey;
            this.isAdmin = ADMIN_USERNAMES.contains(username);
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
     * 获取用户完整信息（昵称和房间秘钥）
     */
    private static UserInfo getUserInfo(long userId) {
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT username, nickname, room_key FROM users WHERE id = ?")) {
            ps.setLong(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new UserInfo(userId, rs.getString("username"), rs.getString("nickname"), rs.getString("room_key"));
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
        public final String roomKey;
        public final boolean isAdmin;
        public final String error;
        
        public LoginResult(String token, String nickname, String roomKey, boolean isAdmin, String error) {
            this.token = token;
            this.nickname = nickname;
            this.roomKey = roomKey;
            this.isAdmin = isAdmin;
            this.error = error;
        }
        
        public boolean success() {
            return error == null && token != null;
        }
    }
    
    // ========== 管理员功能 ==========
    
    /**
     * 检查用户是否是管理员
     */
    public static boolean isAdmin(String username) {
        return ADMIN_USERNAMES.contains(username);
    }
    
    /**
     * 用户列表项
     */
    public static class UserListItem {
        public final long id;
        public final String username;
        public final String nickname;
        public final String roomKey;
        public final String createdAt;
        public final String lastLogin;
        public final boolean isAdmin;
        
        public UserListItem(long id, String username, String nickname, String roomKey, 
                           String createdAt, String lastLogin) {
            this.id = id;
            this.username = username;
            this.nickname = nickname;
            this.roomKey = roomKey;
            this.createdAt = createdAt;
            this.lastLogin = lastLogin;
            this.isAdmin = ADMIN_USERNAMES.contains(username);
        }
    }
    
    /**
     * 获取用户列表（管理员功能）
     */
    public static List<UserListItem> listUsers(String roomKey) {
        List<UserListItem> users = new ArrayList<>();
        if (!Db.enabled()) return users;
        
        String sql = "SELECT id, username, nickname, room_key, created_at, last_login FROM users";
        if (roomKey != null && !roomKey.isBlank()) {
            sql += " WHERE room_key = ?";
        }
        sql += " ORDER BY id DESC";
        
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (roomKey != null && !roomKey.isBlank()) {
                ps.setString(1, roomKey);
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String createdAt = "";
                String lastLogin = "";
                Timestamp ts = rs.getTimestamp("created_at");
                if (ts != null) createdAt = ts.toString();
                ts = rs.getTimestamp("last_login");
                if (ts != null) lastLogin = ts.toString();
                
                users.add(new UserListItem(
                    rs.getLong("id"),
                    rs.getString("username"),
                    rs.getString("nickname"),
                    rs.getString("room_key"),
                    createdAt,
                    lastLogin
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return users;
    }
    
    /**
     * 删除用户（管理员功能）
     */
    public static String deleteUser(long userId, String adminUsername) {
        if (!isAdmin(adminUsername)) {
            return "无管理员权限";
        }
        if (!Db.enabled()) return "数据库未配置";
        
        // 检查要删除的用户是否存在
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT username FROM users WHERE id = ?")) {
            ps.setLong(1, userId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                return "用户不存在";
            }
            String targetUsername = rs.getString("username");
            // 不能删除管理员
            if (isAdmin(targetUsername)) {
                return "不能删除管理员账户";
            }
        } catch (SQLException e) {
            return "查询失败: " + e.getMessage();
        }
        
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM users WHERE id = ?")) {
            ps.setLong(1, userId);
            int deleted = ps.executeUpdate();
            if (deleted > 0) {
                return null; // 成功
            } else {
                return "删除失败";
            }
        } catch (SQLException e) {
            return "删除失败: " + e.getMessage();
        }
    }
    
    /**
     * 更新用户信息（管理员功能）
     */
    public static String updateUser(long userId, String newNickname, String newRoomKey, String adminUsername) {
        if (!isAdmin(adminUsername)) {
            return "无管理员权限";
        }
        if (!Db.enabled()) return "数据库未配置";
        
        // 验证新的房间秘钥是否有效
        if (newRoomKey != null && !newRoomKey.isBlank() && !Db.isValidRoomKey(newRoomKey)) {
            return "无效的房间秘钥";
        }
        
        StringBuilder sql = new StringBuilder("UPDATE users SET ");
        List<Object> params = new ArrayList<>();
        boolean hasUpdate = false;
        
        if (newNickname != null && !newNickname.isBlank()) {
            sql.append("nickname = ?");
            params.add(newNickname);
            hasUpdate = true;
        }
        if (newRoomKey != null && !newRoomKey.isBlank()) {
            if (hasUpdate) sql.append(", ");
            sql.append("room_key = ?");
            params.add(newRoomKey);
            hasUpdate = true;
        }
        
        if (!hasUpdate) {
            return "没有要更新的内容";
        }
        
        sql.append(" WHERE id = ?");
        params.add(userId);
        
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object param = params.get(i);
                if (param instanceof String) {
                    ps.setString(i + 1, (String) param);
                } else if (param instanceof Long) {
                    ps.setLong(i + 1, (Long) param);
                }
            }
            int updated = ps.executeUpdate();
            if (updated > 0) {
                return null; // 成功
            } else {
                return "用户不存在";
            }
        } catch (SQLException e) {
            return "更新失败: " + e.getMessage();
        }
    }
    
    /**
     * 重置用户密码（管理员功能）
     */
    public static String resetPassword(long userId, String newPassword, String adminUsername) {
        if (!isAdmin(adminUsername)) {
            return "无管理员权限";
        }
        if (!Db.enabled()) return "数据库未配置";
        
        if (newPassword == null || newPassword.length() < 6) {
            return "密码长度至少6个字符";
        }
        
        String passwordHash = hashPassword(newPassword);
        
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE users SET password_hash = ? WHERE id = ?")) {
            ps.setString(1, passwordHash);
            ps.setLong(2, userId);
            int updated = ps.executeUpdate();
            if (updated > 0) {
                return null; // 成功
            } else {
                return "用户不存在";
            }
        } catch (SQLException e) {
            return "重置密码失败: " + e.getMessage();
        }
    }
}
