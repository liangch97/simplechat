package db;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户文件数据访问对象
 */
public class FileDao {
    
    /**
     * 初始化表结构
     */
    public static void init() throws SQLException, ClassNotFoundException {
        if (!Db.enabled()) {
            System.out.println("[FileDao] Database not configured, skipping init.");
            return;
        }
        Db.ensureDriver();
        
        // 这里可以检查表是否存在，或者执行建表语句
        // 实际生产环境建议在外部执行SQL脚本
        System.out.println("[FileDao] File tables initialized (run file-storage-schema.sql if needed)");
    }
    
    /**
     * 文件信息实体类
     */
    public static class FileInfo {
        public long id;
        public long userId;
        public String fileName;
        public String filePath;
        public long fileSize;
        public String fileType;
        public String fileExtension;
        public String storagePath;
        public boolean isPublic;
        public long downloadCount;
        public String folderPath;
        public String roomKey;
        public Timestamp createdAt;
        public Timestamp updatedAt;
        
        public FileInfo() {}
        
        public FileInfo(ResultSet rs) throws SQLException {
            this.id = rs.getLong("id");
            this.userId = rs.getLong("user_id");
            this.fileName = rs.getString("file_name");
            this.filePath = rs.getString("file_path");
            this.fileSize = rs.getLong("file_size");
            this.fileType = rs.getString("file_type");
            this.fileExtension = rs.getString("file_extension");
            this.storagePath = rs.getString("storage_path");
            this.isPublic = rs.getBoolean("is_public");
            this.downloadCount = rs.getLong("download_count");
            this.folderPath = rs.getString("folder_path");
            this.roomKey = rs.getString("room_key");
            this.createdAt = rs.getTimestamp("created_at");
            this.updatedAt = rs.getTimestamp("updated_at");
        }
    }
    
    /**
     * 用户存储配额实体类
     */
    public static class StorageQuota {
        public long id;
        public long userId;
        public long totalQuota;
        public long usedSpace;
        public int fileCount;
        public Timestamp createdAt;
        public Timestamp updatedAt;
        
        public StorageQuota() {}
        
        public StorageQuota(ResultSet rs) throws SQLException {
            this.id = rs.getLong("id");
            this.userId = rs.getLong("user_id");
            this.totalQuota = rs.getLong("total_quota");
            this.usedSpace = rs.getLong("used_space");
            this.fileCount = rs.getInt("file_count");
            this.createdAt = rs.getTimestamp("created_at");
            this.updatedAt = rs.getTimestamp("updated_at");
        }
    }
    
    /**
     * 插入文件记录
     */
    public static long insertFile(FileInfo file, String roomKey) throws SQLException {
        String sql = "INSERT INTO user_files (user_id, file_name, file_path, file_size, file_type, " +
                     "file_extension, storage_path, is_public, folder_path, room_key) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = Db.getConnection(roomKey);
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            ps.setLong(1, file.userId);
            ps.setString(2, file.fileName);
            ps.setString(3, file.filePath);
            ps.setLong(4, file.fileSize);
            ps.setString(5, file.fileType);
            ps.setString(6, file.fileExtension);
            ps.setString(7, file.storagePath);
            ps.setBoolean(8, file.isPublic);
            ps.setString(9, file.folderPath);
            ps.setString(10, roomKey);
            
            ps.executeUpdate();
            
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return -1;
    }
    
    /**
     * 获取房间文件列表（群文件共享，所有用户的文件都可见）
     */
    public static List<FileInfo> getUserFiles(long userId, String folderPath, String roomKey) throws SQLException {
        List<FileInfo> files = new ArrayList<>();
        // 查询整个房间的文件，不限制 user_id
        String sql = "SELECT * FROM user_files WHERE folder_path = ? AND room_key = ? " +
                     "ORDER BY created_at DESC";
        
        try (Connection conn = Db.getConnection(roomKey);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, folderPath);
            ps.setString(2, roomKey);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    files.add(new FileInfo(rs));
                }
            }
        }
        return files;
    }
    
    /**
     * 搜索房间文件（所有成员共享）
     */
    public static List<FileInfo> searchUserFiles(long userId, String keyword, String roomKey) throws SQLException {
        List<FileInfo> files = new ArrayList<>();
        String sql = "SELECT TOP 100 * FROM user_files WHERE room_key = ? AND file_name LIKE ? " +
                     "ORDER BY created_at DESC";
        
        try (Connection conn = Db.getConnection(roomKey);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, roomKey);
            ps.setString(2, "%" + keyword + "%");
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    files.add(new FileInfo(rs));
                }
            }
        }
        return files;
    }
    
    /**
     * 根据ID获取文件信息
     */
    public static FileInfo getFileById(long fileId, String roomKey) throws SQLException {
        String sql = "SELECT * FROM user_files WHERE id = ? AND room_key = ?";
        
        try (Connection conn = Db.getConnection(roomKey);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setLong(1, fileId);
            ps.setString(2, roomKey);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new FileInfo(rs);
                }
            }
        }
        return null;
    }
    
    /**
     * 删除文件记录
     */
    public static boolean deleteFile(long fileId, long userId, String roomKey) throws SQLException {
        String sql = "DELETE FROM user_files WHERE id = ? AND user_id = ? AND room_key = ?";
        
        try (Connection conn = Db.getConnection(roomKey);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setLong(1, fileId);
            ps.setLong(2, userId);
            ps.setString(3, roomKey);
            
            return ps.executeUpdate() > 0;
        }
    }
    
    /**
     * 更新文件下载次数
     */
    public static void incrementDownloadCount(long fileId, String roomKey) throws SQLException {
        String sql = "UPDATE user_files SET download_count = download_count + 1 WHERE id = ? AND room_key = ?";
        
        try (Connection conn = Db.getConnection(roomKey);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setLong(1, fileId);
            ps.setString(2, roomKey);
            ps.executeUpdate();
        }
    }
    
    /**
     * 获取用户存储配额
     */
    public static StorageQuota getUserQuota(long userId, String roomKey) throws SQLException {
        String sql = "SELECT * FROM user_storage_quota WHERE user_id = ?";
        
        try (Connection conn = Db.getConnection(roomKey);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setLong(1, userId);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new StorageQuota(rs);
                }
            }
        }
        return null;
    }
    
    /**
     * 初始化用户配额（默认100GB）
     */
    public static void initUserQuota(long userId, String roomKey) throws SQLException {
        // 先检查是否存在
        String checkSql = "SELECT 1 FROM user_storage_quota WHERE user_id = ?";
        String insertSql = "INSERT INTO user_storage_quota (user_id, total_quota, used_space, file_count) " +
                           "VALUES (?, 107374182400, 0, 0)";
        
        try (Connection conn = Db.getConnection(roomKey);
             PreparedStatement checkPs = conn.prepareStatement(checkSql)) {
            
            checkPs.setLong(1, userId);
            try (ResultSet rs = checkPs.executeQuery()) {
                if (rs.next()) {
                    // 已存在，不需要插入
                    return;
                }
            }
            
            // 不存在，插入新记录
            try (PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
                insertPs.setLong(1, userId);
                insertPs.executeUpdate();
            }
        }
    }
    
    /**
     * 更新用户已使用空间
     */
    public static void updateUserSpace(long userId, long sizeDelta, int fileCountDelta, String roomKey) throws SQLException {
        String sql = "UPDATE user_storage_quota SET used_space = used_space + ?, file_count = file_count + ? " +
                     "WHERE user_id = ?";
        
        try (Connection conn = Db.getConnection(roomKey);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setLong(1, sizeDelta);
            ps.setInt(2, fileCountDelta);
            ps.setLong(3, userId);
            ps.executeUpdate();
        }
    }
    
    /**
     * 获取用户总文件数
     */
    public static int getUserFileCount(long userId, String roomKey) throws SQLException {
        String sql = "SELECT COUNT(*) FROM user_files WHERE user_id = ? AND room_key = ?";
        
        try (Connection conn = Db.getConnection(roomKey);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setLong(1, userId);
            ps.setString(2, roomKey);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }
    
    /**
     * 获取用户总使用空间
     */
    public static long getUserTotalSize(long userId, String roomKey) throws SQLException {
        String sql = "SELECT SUM(file_size) FROM user_files WHERE user_id = ? AND room_key = ?";
        
        try (Connection conn = Db.getConnection(roomKey);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setLong(1, userId);
            ps.setString(2, roomKey);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return 0;
    }
    
    /**
     * 获取房间配额（整个群聊共享100GB）
     */
    public static StorageQuota getRoomQuota(String roomKey) throws SQLException {
        // 统计整个房间的文件总大小和数量
        String sql = "SELECT COUNT(*) as file_count, COALESCE(SUM(file_size), 0) as used_space FROM user_files WHERE room_key = ?";
        
        try (Connection conn = Db.getConnection(roomKey);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, roomKey);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    StorageQuota quota = new StorageQuota();
                    quota.totalQuota = 107374182400L; // 100GB
                    quota.usedSpace = rs.getLong("used_space");
                    quota.fileCount = rs.getInt("file_count");
                    return quota;
                }
            }
        }
        // 返回默认配额
        StorageQuota quota = new StorageQuota();
        quota.totalQuota = 107374182400L; // 100GB
        quota.usedSpace = 0;
        quota.fileCount = 0;
        return quota;
    }
    
    // ========== 文件夹相关操作 ==========
    
    /**
     * 文件夹信息实体类
     */
    public static class FolderInfo {
        public long id;
        public long userId;
        public String folderName;
        public String folderPath;
        public String parentPath;
        public String roomKey;
        public Timestamp createdAt;
        public Timestamp updatedAt;
        
        public FolderInfo() {}
        
        public FolderInfo(ResultSet rs) throws SQLException {
            this.id = rs.getLong("id");
            this.userId = rs.getLong("user_id");
            this.folderName = rs.getString("folder_name");
            this.folderPath = rs.getString("folder_path");
            this.parentPath = rs.getString("parent_path");
            this.roomKey = rs.getString("room_key");
            this.createdAt = rs.getTimestamp("created_at");
            this.updatedAt = rs.getTimestamp("updated_at");
        }
    }
    
    /**
     * 创建文件夹
     */
    public static long createFolder(long userId, String folderName, String parentPath, String roomKey) throws SQLException {
        // 构建完整路径
        String folderPath;
        if (parentPath == null || parentPath.equals("/")) {
            folderPath = "/" + folderName;
        } else {
            folderPath = parentPath + "/" + folderName;
        }
        
        String sql = "INSERT INTO user_folders (user_id, folder_name, folder_path, parent_path, room_key) " +
                     "VALUES (?, ?, ?, ?, ?)";
        
        try (Connection conn = Db.getConnection(roomKey);
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            ps.setLong(1, userId);
            ps.setString(2, folderName);
            ps.setString(3, folderPath);
            ps.setString(4, parentPath == null ? "/" : parentPath);
            ps.setString(5, roomKey);
            
            ps.executeUpdate();
            
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return -1;
    }
    
    /**
     * 获取指定路径下的文件夹列表（房间共享）
     */
    public static List<FolderInfo> getUserFolders(long userId, String parentPath, String roomKey) throws SQLException {
        List<FolderInfo> folders = new ArrayList<>();
        String sql = "SELECT * FROM user_folders WHERE parent_path = ? AND room_key = ? " +
                     "ORDER BY folder_name ASC";
        
        try (Connection conn = Db.getConnection(roomKey);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, parentPath);
            ps.setString(2, roomKey);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    folders.add(new FolderInfo(rs));
                }
            }
        }
        return folders;
    }
    
    /**
     * 根据路径获取文件夹信息（房间共享）
     */
    public static FolderInfo getFolderByPath(long userId, String folderPath, String roomKey) throws SQLException {
        String sql = "SELECT * FROM user_folders WHERE folder_path = ? AND room_key = ?";
        
        try (Connection conn = Db.getConnection(roomKey);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, folderPath);
            ps.setString(2, roomKey);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new FolderInfo(rs);
                }
            }
        }
        return null;
    }
    
    /**
     * 删除文件夹（需要先删除其中的文件和子文件夹）
     */
    public static boolean deleteFolder(long folderId, long userId, String roomKey) throws SQLException {
        String sql = "DELETE FROM user_folders WHERE id = ? AND user_id = ? AND room_key = ?";
        
        try (Connection conn = Db.getConnection(roomKey);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setLong(1, folderId);
            ps.setLong(2, userId);
            ps.setString(3, roomKey);
            
            return ps.executeUpdate() > 0;
        }
    }
    
    /**
     * 重命名文件夹
     */
    public static boolean renameFolder(long folderId, long userId, String newName, String roomKey) throws SQLException {
        // 先获取文件夹信息以构建新路径
        FolderInfo folder = getFolderById(folderId, userId, roomKey);
        if (folder == null) {
            return false;
        }
        
        // 构建新的 folder_path
        String newFolderPath;
        if (folder.parentPath == null || folder.parentPath.equals("/")) {
            newFolderPath = "/" + newName;
        } else {
            newFolderPath = folder.parentPath + "/" + newName;
        }
        
        String sql = "UPDATE user_folders SET folder_name = ?, folder_path = ?, updated_at = CURRENT_TIMESTAMP " +
                     "WHERE id = ? AND room_key = ?";
        
        try (Connection conn = Db.getConnection(roomKey);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, newName);
            ps.setString(2, newFolderPath);
            ps.setLong(3, folderId);
            ps.setString(4, roomKey);
            
            return ps.executeUpdate() > 0;
        }
    }
    
    /**
     * 根据ID获取文件夹信息
     */
    public static FolderInfo getFolderById(long folderId, long userId, String roomKey) throws SQLException {
        String sql = "SELECT * FROM user_folders WHERE id = ? AND room_key = ?";
        
        try (Connection conn = Db.getConnection(roomKey);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setLong(1, folderId);
            ps.setString(2, roomKey);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new FolderInfo(rs);
                }
            }
        }
        return null;
    }
    
    /**
     * 检查文件夹是否为空（没有子文件夹和文件）- 房间共享
     */
    public static boolean isFolderEmpty(long userId, String folderPath, String roomKey) throws SQLException {
        // 检查是否有子文件夹
        String folderSql = "SELECT COUNT(*) FROM user_folders WHERE parent_path = ? AND room_key = ?";
        // 检查是否有文件
        String fileSql = "SELECT COUNT(*) FROM user_files WHERE folder_path = ? AND room_key = ?";
        
        try (Connection conn = Db.getConnection(roomKey)) {
            try (PreparedStatement ps = conn.prepareStatement(folderSql)) {
                ps.setString(1, folderPath);
                ps.setString(2, roomKey);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        return false;
                    }
                }
            }
            
            try (PreparedStatement ps = conn.prepareStatement(fileSql)) {
                ps.setString(1, folderPath);
                ps.setString(2, roomKey);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
    
    /**
     * 重命名文件
     */
    public static boolean renameFile(long fileId, long userId, String newName, String roomKey) throws SQLException {
        String sql = "UPDATE user_files SET file_name = ?, updated_at = CURRENT_TIMESTAMP " +
                     "WHERE id = ? AND user_id = ? AND room_key = ?";
        
        try (Connection conn = Db.getConnection(roomKey);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, newName);
            ps.setLong(2, fileId);
            ps.setLong(3, userId);
            ps.setString(4, roomKey);
            
            return ps.executeUpdate() > 0;
        }
    }
    
    /**
     * 移动文件到新文件夹
     */
    public static boolean moveFile(long fileId, long userId, String newFolderPath, String roomKey) throws SQLException {
        String sql = "UPDATE user_files SET folder_path = ?, updated_at = CURRENT_TIMESTAMP " +
                     "WHERE id = ? AND user_id = ? AND room_key = ?";
        
        try (Connection conn = Db.getConnection(roomKey);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, newFolderPath);
            ps.setLong(2, fileId);
            ps.setLong(3, userId);
            ps.setString(4, roomKey);
            
            return ps.executeUpdate() > 0;
        }
    }
    
    /**
     * 获取文件夹内的文件数量（房间共享）
     */
    public static int getFileCountInFolder(long userId, String folderPath, String roomKey) throws SQLException {
        String sql = "SELECT COUNT(*) FROM user_files WHERE folder_path = ? AND room_key = ?";
        
        try (Connection conn = Db.getConnection(roomKey);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, folderPath);
            ps.setString(2, roomKey);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }
    
    /**
     * 获取文件夹内的子文件夹数量（房间共享）
     */
    public static int getSubFolderCount(long userId, String folderPath, String roomKey) throws SQLException {
        String sql = "SELECT COUNT(*) FROM user_folders WHERE parent_path = ? AND room_key = ?";
        
        try (Connection conn = Db.getConnection(roomKey);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, folderPath);
            ps.setString(2, roomKey);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }
}
