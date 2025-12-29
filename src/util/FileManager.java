package util;

import db.FileDao;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.List;

/**
 * 文件管理器 - 负责文件的物理存储和管理
 * 为每个用户创建独立的分区目录
 */
public class FileManager {
    
    private static final String BASE_STORAGE_PATH = "storage";  // 基础存储路径
    private static final long DEFAULT_QUOTA = 1073741824L;  // 默认1GB配额
    
    /**
     * 获取用户存储根目录
     */
    public static String getUserStorageRoot(long userId, String roomKey) {
        return BASE_STORAGE_PATH + File.separator + "room_" + roomKey + File.separator + "user_" + userId;
    }
    
    /**
     * 获取用户文件完整路径
     */
    public static String getUserFilePath(long userId, String roomKey, String folderPath, String fileName) {
        String root = getUserStorageRoot(userId, roomKey);
        if (folderPath == null || folderPath.equals("/")) {
            return root + File.separator + fileName;
        }
        // 移除前导斜杠并确保安全
        String safeFolderPath = folderPath.replaceAll("^\\/+", "").replaceAll("\\.\\.", "");
        return root + File.separator + safeFolderPath + File.separator + fileName;
    }
    
    /**
     * 确保用户目录存在
     */
    public static void ensureUserDirectory(long userId, String roomKey) throws IOException {
        Path userDir = Paths.get(getUserStorageRoot(userId, roomKey));
        if (!Files.exists(userDir)) {
            Files.createDirectories(userDir);
        }
    }
    
    /**
     * 确保用户子目录存在
     */
    public static void ensureUserSubDirectory(long userId, String roomKey, String folderPath) throws IOException {
        String root = getUserStorageRoot(userId, roomKey);
        String safeFolderPath = folderPath.replaceAll("^\\/+", "").replaceAll("\\.\\.", "");
        Path subDir = Paths.get(root, safeFolderPath);
        if (!Files.exists(subDir)) {
            Files.createDirectories(subDir);
        }
    }
    
    /**
     * 保存文件到用户分区
     * @return 保存后的文件信息
     */
    public static FileDao.FileInfo saveFile(long userId, String roomKey, String fileName, 
                                           String folderPath, byte[] fileData, String contentType) 
            throws IOException, SQLException {
        
        // 检查配额
        FileDao.StorageQuota quota = FileDao.getUserQuota(userId, roomKey);
        if (quota == null) {
            FileDao.initUserQuota(userId, roomKey);
            quota = FileDao.getUserQuota(userId, roomKey);
        }
        
        long fileSize = fileData.length;
        if (quota != null && quota.usedSpace + fileSize > quota.totalQuota) {
            throw new IOException("存储空间不足");
        }
        
        // 创建用户目录
        ensureUserDirectory(userId, roomKey);
        if (folderPath != null && !folderPath.equals("/")) {
            ensureUserSubDirectory(userId, roomKey, folderPath);
        }
        
        // 生成唯一文件名（防止重复）
        String uniqueFileName = generateUniqueFileName(fileName);
        String filePath = getUserFilePath(userId, roomKey, folderPath, uniqueFileName);
        
        // 保存文件
        Path path = Paths.get(filePath);
        Files.write(path, fileData);
        
        // 创建文件信息对象
        FileDao.FileInfo fileInfo = new FileDao.FileInfo();
        fileInfo.userId = userId;
        fileInfo.fileName = fileName;
        fileInfo.filePath = filePath;
        fileInfo.fileSize = fileSize;
        fileInfo.fileType = contentType;
        fileInfo.fileExtension = getFileExtension(fileName);
        fileInfo.storagePath = getUserStorageRoot(userId, roomKey);
        fileInfo.folderPath = folderPath == null ? "/" : folderPath;
        fileInfo.isPublic = false;
        
        // 保存到数据库
        long fileId = FileDao.insertFile(fileInfo, roomKey);
        fileInfo.id = fileId;
        
        // 更新用户配额
        FileDao.updateUserSpace(userId, fileSize, 1, roomKey);
        
        return fileInfo;
    }
    
    /**
     * 读取文件内容
     * 同一房间的成员都可以访问该房间的文件（群文件共享）
     */
    public static byte[] readFile(long fileId, long userId, String roomKey) 
            throws IOException, SQLException {
        
        FileDao.FileInfo fileInfo = FileDao.getFileById(fileId, roomKey);
        if (fileInfo == null) {
            throw new FileNotFoundException("文件不存在");
        }
        
        // 同一房间的成员都可以访问该房间的文件（群文件共享）
        // 权限已在调用方通过 roomKey 验证
        
        Path path = Paths.get(fileInfo.filePath);
        if (!Files.exists(path)) {
            throw new FileNotFoundException("文件不存在: " + fileInfo.filePath);
        }
        
        // 更新下载次数
        FileDao.incrementDownloadCount(fileId, roomKey);
        
        return Files.readAllBytes(path);
    }
    
    /**
     * 删除文件
     */
    public static boolean deleteFile(long fileId, long userId, String roomKey) 
            throws IOException, SQLException {
        
        FileDao.FileInfo fileInfo = FileDao.getFileById(fileId, roomKey);
        if (fileInfo == null) {
            return false;
        }
        
        // 检查权限
        if (fileInfo.userId != userId) {
            throw new SecurityException("无权限删除该文件");
        }
        
        // 删除物理文件
        Path path = Paths.get(fileInfo.filePath);
        if (Files.exists(path)) {
            Files.delete(path);
        }
        
        // 删除数据库记录
        boolean deleted = FileDao.deleteFile(fileId, userId, roomKey);
        
        if (deleted) {
            // 更新用户配额
            FileDao.updateUserSpace(userId, -fileInfo.fileSize, -1, roomKey);
        }
        
        return deleted;
    }
    
    /**
     * 获取用户文件列表
     */
    public static List<FileDao.FileInfo> getUserFiles(long userId, String folderPath, String roomKey) 
            throws SQLException {
        return FileDao.getUserFiles(userId, folderPath, roomKey);
    }
    
    /**
     * 搜索用户文件
     */
    public static List<FileDao.FileInfo> searchFiles(long userId, String keyword, String roomKey) 
            throws SQLException {
        return FileDao.searchUserFiles(userId, keyword, roomKey);
    }
    
    /**
     * 获取房间存储信息（整个群聊共享配额）
     */
    public static FileDao.StorageQuota getUserStorageInfo(long userId, String roomKey) 
            throws SQLException {
        // 获取整个房间的存储使用情况
        return FileDao.getRoomQuota(roomKey);
    }
    
    /**
     * 生成唯一文件名
     */
    private static String generateUniqueFileName(String originalName) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String extension = getFileExtension(originalName);
        String baseName = originalName.substring(0, originalName.lastIndexOf('.') >= 0 ? 
                                                  originalName.lastIndexOf('.') : originalName.length());
        // 清理文件名中的特殊字符
        baseName = baseName.replaceAll("[^a-zA-Z0-9_\\-\\u4e00-\\u9fa5]", "_");
        return timestamp + "_" + baseName + (extension.isEmpty() ? "" : "." + extension);
    }
    
    /**
     * 获取文件扩展名
     */
    private static String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }
    
    /**
     * 计算文件MD5哈希值（可用于去重）
     */
    public static String calculateMD5(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 格式化文件大小
     */
    public static String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
        }
    }
    
    // ========== 文件夹管理 ==========
    
    /**
     * 创建文件夹
     */
    public static FileDao.FolderInfo createFolder(long userId, String folderName, String parentPath, String roomKey) 
            throws SQLException, IOException {
        
        // 验证文件夹名
        if (folderName == null || folderName.trim().isEmpty()) {
            throw new IllegalArgumentException("文件夹名称不能为空");
        }
        
        // 清理文件夹名中的特殊字符
        String safeFolderName = folderName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (safeFolderName.isEmpty()) {
            throw new IllegalArgumentException("文件夹名称无效");
        }
        
        // 创建数据库记录
        long folderId = FileDao.createFolder(userId, safeFolderName, parentPath, roomKey);
        
        if (folderId > 0) {
            // 构建完整路径
            String folderPath;
            if (parentPath == null || parentPath.equals("/")) {
                folderPath = "/" + safeFolderName;
            } else {
                folderPath = parentPath + "/" + safeFolderName;
            }
            
            // 创建物理目录
            ensureUserSubDirectory(userId, roomKey, folderPath);
            
            // 返回文件夹信息
            return FileDao.getFolderByPath(userId, folderPath, roomKey);
        }
        
        throw new SQLException("创建文件夹失败");
    }
    
    /**
     * 获取文件夹列表
     */
    public static List<FileDao.FolderInfo> getUserFolders(long userId, String parentPath, String roomKey) 
            throws SQLException {
        return FileDao.getUserFolders(userId, parentPath, roomKey);
    }
    
    /**
     * 删除文件夹（必须为空）
     */
    public static boolean deleteFolder(long folderId, long userId, String roomKey) 
            throws SQLException, IOException {
        
        // 获取文件夹信息
        // 需要先通过id查询folderPath
        // 这里简化处理，直接删除
        return FileDao.deleteFolder(folderId, userId, roomKey);
    }
    
    /**
     * 重命名文件夹
     */
    public static boolean renameFolder(long folderId, long userId, String newName, String roomKey) 
            throws SQLException, IOException {
        
        // 验证新名称
        if (newName == null || newName.trim().isEmpty()) {
            throw new IllegalArgumentException("文件夹名称不能为空");
        }
        
        // 清理文件夹名中的特殊字符
        String safeFolderName = newName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (safeFolderName.isEmpty()) {
            throw new IllegalArgumentException("文件夹名称无效");
        }
        
        // 重命名数据库记录
        return FileDao.renameFolder(folderId, userId, safeFolderName, roomKey);
    }
    
    /**
     * 删除文件夹及其所有内容（递归）
     */
    public static boolean deleteFolderRecursive(long userId, String folderPath, String roomKey) 
            throws SQLException, IOException {
        
        // 先删除所有子文件夹
        List<FileDao.FolderInfo> subFolders = FileDao.getUserFolders(userId, folderPath, roomKey);
        for (FileDao.FolderInfo folder : subFolders) {
            deleteFolderRecursive(userId, folder.folderPath, roomKey);
        }
        
        // 删除文件夹中的所有文件
        List<FileDao.FileInfo> files = FileDao.getUserFiles(userId, folderPath, roomKey);
        for (FileDao.FileInfo file : files) {
            deleteFile(file.id, userId, roomKey);
        }
        
        // 删除文件夹记录
        FileDao.FolderInfo folder = FileDao.getFolderByPath(userId, folderPath, roomKey);
        if (folder != null) {
            FileDao.deleteFolder(folder.id, userId, roomKey);
        }
        
        // 删除物理目录
        String root = getUserStorageRoot(userId, roomKey);
        String safeFolderPath = folderPath.replaceAll("^\\/+", "").replaceAll("\\.\\.", "");
        Path dirPath = Paths.get(root, safeFolderPath);
        if (Files.exists(dirPath)) {
            try {
                Files.delete(dirPath);
            } catch (Exception e) {
                // 目录不为空则忽略
            }
        }
        
        return true;
    }
    
    /**
     * 重命名文件
     */
    public static boolean renameFile(long fileId, long userId, String newName, String roomKey) 
            throws SQLException {
        return FileDao.renameFile(fileId, userId, newName, roomKey);
    }
    
    /**
     * 移动文件到新文件夹
     */
    public static boolean moveFile(long fileId, long userId, String newFolderPath, String roomKey) 
            throws SQLException, IOException {
        
        // 获取文件信息
        FileDao.FileInfo fileInfo = FileDao.getFileById(fileId, roomKey);
        if (fileInfo == null || fileInfo.userId != userId) {
            return false;
        }
        
        // 确保目标文件夹存在
        if (newFolderPath != null && !newFolderPath.equals("/")) {
            ensureUserSubDirectory(userId, roomKey, newFolderPath);
        }
        
        // 移动物理文件
        Path oldPath = Paths.get(fileInfo.filePath);
        String newPhysicalPath = getUserFilePath(userId, roomKey, newFolderPath, 
            oldPath.getFileName().toString());
        Path newPath = Paths.get(newPhysicalPath);
        
        if (Files.exists(oldPath)) {
            Files.move(oldPath, newPath, StandardCopyOption.REPLACE_EXISTING);
        }
        
        // 更新数据库
        return FileDao.moveFile(fileId, userId, newFolderPath, roomKey);
    }
    
    /**
     * 检查文件夹是否为空
     */
    public static boolean isFolderEmpty(long userId, String folderPath, String roomKey) 
            throws SQLException {
        return FileDao.isFolderEmpty(userId, folderPath, roomKey);
    }
}
