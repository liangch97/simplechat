// 文件管理器 JavaScript

// 全局变量
const API_BASE_URL = (typeof CONFIG !== 'undefined' ? CONFIG.getApiConfig().baseUrl : '') || '';
const TOKEN_KEYS = ['sysu_chat_token', 'token'];
const ROOM_KEYS = ['sysu_chat_room_key', 'roomKey'];
let currentFolder = '/';
let selectedFile = null;
let selectedItem = null; // 选中的项目（文件或文件夹）
let selectedIsFolder = false; // 选中的是否是文件夹
let files = [];
let folders = []; // 文件夹列表
let token = TOKEN_KEYS.map(k => localStorage.getItem(k)).find(Boolean);
let roomKey = ROOM_KEYS.map(k => localStorage.getItem(k)).find(Boolean) || '24336064';

// 初始化
document.addEventListener('DOMContentLoaded', () => {
    // 初始化图标
    lucide.createIcons();
    
    // 检查登录状态
    if (!token) {
        alert('请先登录');
        window.location.href = '/';
        return;
    }
    
    // 加载存储配额和文件列表
    loadStorageQuota();
    loadFiles();
    updateBreadcrumb();
    
    // 事件监听器
    document.getElementById('btnRefresh').addEventListener('click', () => {
        loadFiles();
        loadStorageQuota();
    });
    
    document.getElementById('btnUpload').addEventListener('click', openUploadModal);
    document.getElementById('fileInput').addEventListener('change', handleFileSelect);
    
    // 新建文件夹按钮
    const btnNewFolder = document.getElementById('btnNewFolder');
    if (btnNewFolder) {
        btnNewFolder.addEventListener('click', openNewFolderModal);
    }
    
    document.getElementById('searchInput').addEventListener('input', (e) => {
        if (e.target.value.trim()) {
            searchFiles(e.target.value.trim());
        } else {
            loadFiles();
        }
    });
    
    document.getElementById('sortSelect').addEventListener('change', () => {
        sortFiles();
    });
    
    // 拖放上传
    const uploadArea = document.getElementById('uploadArea');
    uploadArea.addEventListener('click', () => {
        document.getElementById('fileInput').click();
    });
    
    uploadArea.addEventListener('dragover', (e) => {
        e.preventDefault();
        uploadArea.classList.add('drag-over');
    });
    
    uploadArea.addEventListener('dragleave', () => {
        uploadArea.classList.remove('drag-over');
    });
    
    uploadArea.addEventListener('drop', (e) => {
        e.preventDefault();
        uploadArea.classList.remove('drag-over');
        const files = Array.from(e.dataTransfer.files);
        uploadFiles(files);
    });
    
    // 点击模态框外部关闭
    document.getElementById('uploadModal').addEventListener('click', (e) => {
        if (e.target.id === 'uploadModal') {
            closeUploadModal();
        }
    });
    
    document.getElementById('fileInfoModal').addEventListener('click', (e) => {
        if (e.target.id === 'fileInfoModal') {
            closeFileInfoModal();
        }
    });
    
    // 新建文件夹对话框
    const newFolderModal = document.getElementById('newFolderModal');
    if (newFolderModal) {
        newFolderModal.addEventListener('click', (e) => {
            if (e.target.id === 'newFolderModal') {
                closeNewFolderModal();
            }
        });
    }
    
    // 右键菜单
    document.addEventListener('click', () => {
        document.getElementById('contextMenu').classList.remove('active');
        const folderContextMenu = document.getElementById('folderContextMenu');
        if (folderContextMenu) {
            folderContextMenu.classList.remove('active');
        }
    });
});

// 加载存储配额
async function loadStorageQuota() {
    try {
        const response = await fetch(`${API_BASE_URL}/api/files/quota?roomKey=${roomKey}`, {
            headers: {
                'Authorization': `Bearer ${token}`
            }
        });
        
        if (!response.ok) throw new Error('获取配额失败');
        
        const data = await response.json();
        const quota = data.quota;
        
        // 更新存储条
        const percent = (quota.used / quota.total) * 100;
        document.getElementById('storageBar').style.width = percent + '%';
        document.getElementById('storageText').textContent = 
            `${quota.usedFormatted} / ${quota.totalFormatted} (${Math.round(percent)}%)`;
            
    } catch (error) {
        console.error('加载配额失败:', error);
    }
}

// 加载文件列表
async function loadFiles() {
    const loading = document.getElementById('loading');
    const fileList = document.getElementById('fileList');
    const emptyState = document.getElementById('emptyState');
    
    loading.style.display = 'flex';
    emptyState.style.display = 'none';
    
    try {
        const response = await fetch(
            `${API_BASE_URL}/api/files/list?roomKey=${roomKey}&folder=${encodeURIComponent(currentFolder)}`,
            {
                headers: {
                    'Authorization': `Bearer ${token}`
                }
            }
        );
        
        if (!response.ok) throw new Error('获取文件列表失败');
        
        const data = await response.json();
        files = data.files || [];
        folders = data.folders || [];
        
        loading.style.display = 'none';
        
        if (files.length === 0 && folders.length === 0) {
            emptyState.style.display = 'flex';
            fileList.innerHTML = '';
            return;
        }
        
        renderFilesAndFolders(folders, files);
        
    } catch (error) {
        console.error('加载文件失败:', error);
        loading.style.display = 'none';
        alert('加载文件失败: ' + error.message);
    }
}

// 更新面包屑导航
function updateBreadcrumb() {
    const breadcrumb = document.getElementById('breadcrumb');
    breadcrumb.innerHTML = '';
    
    // 根目录
    const homeItem = document.createElement('a');
    homeItem.href = '#';
    homeItem.className = 'breadcrumb-item';
    homeItem.dataset.path = '/';
    homeItem.innerHTML = '<i data-lucide="home"></i> 根目录';
    homeItem.addEventListener('click', (e) => {
        e.preventDefault();
        navigateToFolder('/');
    });
    breadcrumb.appendChild(homeItem);
    
    // 子目录
    if (currentFolder !== '/') {
        const parts = currentFolder.split('/').filter(p => p);
        let path = '';
        
        parts.forEach((part, index) => {
            path += '/' + part;
            
            const separator = document.createElement('span');
            separator.className = 'breadcrumb-separator';
            separator.innerHTML = '<i data-lucide="chevron-right"></i>';
            breadcrumb.appendChild(separator);
            
            const item = document.createElement('a');
            item.href = '#';
            item.className = 'breadcrumb-item';
            item.dataset.path = path;
            item.textContent = part;
            
            const currentPath = path; // 闭包
            item.addEventListener('click', (e) => {
                e.preventDefault();
                navigateToFolder(currentPath);
            });
            
            breadcrumb.appendChild(item);
        });
    }
    
    lucide.createIcons();
}

// 导航到文件夹
function navigateToFolder(path) {
    currentFolder = path;
    selectedFile = null;
    selectedItem = null;
    selectedIsFolder = false;
    updateBreadcrumb();
    loadFiles();
}

// 渲染文件夹和文件列表
function renderFilesAndFolders(folders, files) {
    const fileList = document.getElementById('fileList');
    fileList.innerHTML = '';
    
    // 如果不是根目录，添加返回上级按钮
    if (currentFolder !== '/') {
        const backItem = document.createElement('div');
        backItem.className = 'file-item folder-item back-item';
        backItem.innerHTML = `
            <i data-lucide="corner-left-up" class="file-icon folder-icon"></i>
            <div class="file-name">..</div>
            <div class="file-info">返回上级目录</div>
        `;
        backItem.addEventListener('dblclick', () => {
            const parts = currentFolder.split('/').filter(p => p);
            parts.pop();
            navigateToFolder('/' + parts.join('/'));
        });
        fileList.appendChild(backItem);
    }
    
    // 渲染文件夹
    folders.forEach(folder => {
        const folderItem = document.createElement('div');
        folderItem.className = 'file-item folder-item';
        folderItem.dataset.folderId = folder.id;
        folderItem.dataset.folderPath = folder.path;
        
        folderItem.innerHTML = `
            <i data-lucide="folder" class="file-icon folder-icon"></i>
            <div class="file-name">${escapeHtml(folder.name)}</div>
            <div class="file-info">${folder.fileCount || 0} 个项目</div>
        `;
        
        // 单击选择
        folderItem.addEventListener('click', () => {
            selectFolder(folderItem, folder);
        });
        
        // 双击进入
        folderItem.addEventListener('dblclick', () => {
            navigateToFolder(folder.path);
        });
        
        // 右键菜单
        folderItem.addEventListener('contextmenu', (e) => {
            e.preventDefault();
            selectFolder(folderItem, folder);
            showFolderContextMenu(e.clientX, e.clientY);
        });
        
        fileList.appendChild(folderItem);
    });
    
    // 渲染文件
    files.forEach(file => {
        const fileItem = document.createElement('div');
        fileItem.className = 'file-item';
        if (file.extension) {
            fileItem.classList.add(`file-type-${file.extension.toLowerCase()}`);
        }
        fileItem.dataset.fileId = file.id;
        
        const icon = getFileIcon(file.extension);
        
        fileItem.innerHTML = `
            <i data-lucide="${icon}" class="file-icon"></i>
            <div class="file-name">${escapeHtml(file.name)}</div>
            <div class="file-info">${file.sizeFormatted}</div>
        `;
        
        // 点击事件
        fileItem.addEventListener('click', () => {
            selectFile(fileItem, file);
        });
        
        // 双击下载
        fileItem.addEventListener('dblclick', () => {
            downloadFileById(file.id, file.name, file.size);
        });
        
        // 右键菜单
        fileItem.addEventListener('contextmenu', (e) => {
            e.preventDefault();
            selectFile(fileItem, file);
            showContextMenu(e.clientX, e.clientY);
        });
        
        fileList.appendChild(fileItem);
    });
    
    // 重新初始化图标
    lucide.createIcons();
}

// 渲染文件列表（保留用于搜索结果）
function renderFiles(files) {
    const fileList = document.getElementById('fileList');
    fileList.innerHTML = '';
    
    files.forEach(file => {
        const fileItem = document.createElement('div');
        fileItem.className = 'file-item';
        if (file.extension) {
            fileItem.classList.add(`file-type-${file.extension.toLowerCase()}`);
        }
        fileItem.dataset.fileId = file.id;
        
        const icon = getFileIcon(file.extension);
        
        fileItem.innerHTML = `
            <i data-lucide="${icon}" class="file-icon"></i>
            <div class="file-name">${escapeHtml(file.name)}</div>
            <div class="file-info">${file.sizeFormatted}</div>
        `;
        
        // 点击事件
        fileItem.addEventListener('click', () => {
            selectFile(fileItem, file);
        });
        
        // 双击下载
        fileItem.addEventListener('dblclick', () => {
            downloadFileById(file.id, file.name, file.size);
        });
        
        // 右键菜单
        fileItem.addEventListener('contextmenu', (e) => {
            e.preventDefault();
            selectFile(fileItem, file);
            showContextMenu(e.clientX, e.clientY);
        });
        
        fileList.appendChild(fileItem);
    });
    
    // 重新初始化图标
    lucide.createIcons();
}

// 获取文件图标
function getFileIcon(extension) {
    const icons = {
        'jpg': 'image', 'jpeg': 'image', 'png': 'image', 'gif': 'image', 'svg': 'image',
        'mp4': 'video', 'avi': 'video', 'mov': 'video',
        'mp3': 'music', 'wav': 'music',
        'pdf': 'file-text',
        'doc': 'file-text', 'docx': 'file-text',
        'xls': 'file-spreadsheet', 'xlsx': 'file-spreadsheet',
        'zip': 'archive', 'rar': 'archive', '7z': 'archive',
        'js': 'code', 'java': 'code', 'py': 'code', 'cpp': 'code', 'c': 'code',
        'html': 'code', 'css': 'code', 'sql': 'code'
    };
    
    return icons[extension?.toLowerCase()] || 'file';
}

// 选择文件夹
function selectFolder(folderItem, folder) {
    // 取消其他选择
    document.querySelectorAll('.file-item').forEach(item => {
        item.classList.remove('selected');
    });
    
    folderItem.classList.add('selected');
    selectedItem = folder;
    selectedIsFolder = true;
    selectedFile = null;
}

// 选择文件
function selectFile(fileItem, file) {
    // 取消其他选择
    document.querySelectorAll('.file-item').forEach(item => {
        item.classList.remove('selected');
    });
    
    fileItem.classList.add('selected');
    selectedFile = file;
    selectedItem = file;
    selectedIsFolder = false;
}

// 搜索文件
async function searchFiles(keyword) {
    const loading = document.getElementById('loading');
    const fileList = document.getElementById('fileList');
    
    loading.style.display = 'flex';
    
    try {
        const response = await fetch(
            `${API_BASE_URL}/api/files/search?roomKey=${roomKey}&keyword=${encodeURIComponent(keyword)}`,
            {
                headers: {
                    'Authorization': `Bearer ${token}`
                }
            }
        );
        
        if (!response.ok) throw new Error('搜索失败');
        
        const data = await response.json();
        files = data.files;
        
        loading.style.display = 'none';
        
        if (files.length === 0) {
            fileList.innerHTML = '<div class="empty-state"><p>未找到匹配的文件</p></div>';
            return;
        }
        
        renderFiles(files);
        
    } catch (error) {
        console.error('搜索失败:', error);
        loading.style.display = 'none';
        alert('搜索失败: ' + error.message);
    }
}

// 排序文件
function sortFiles() {
    const sortBy = document.getElementById('sortSelect').value;
    
    // 排序文件夹
    folders.sort((a, b) => {
        switch (sortBy) {
            case 'name':
                return a.name.localeCompare(b.name);
            case 'date':
                return new Date(b.createdAt || 0) - new Date(a.createdAt || 0);
            default:
                return a.name.localeCompare(b.name);
        }
    });
    
    // 排序文件
    files.sort((a, b) => {
        switch (sortBy) {
            case 'name':
                return a.name.localeCompare(b.name);
            case 'date':
                return new Date(b.createdAt) - new Date(a.createdAt);
            case 'size':
                return b.size - a.size;
            case 'type':
                return (a.extension || '').localeCompare(b.extension || '');
            default:
                return 0;
        }
    });
    
    renderFilesAndFolders(folders, files);
}

// 打开上传对话框
function openUploadModal() {
    document.getElementById('uploadModal').classList.add('active');
    document.getElementById('uploadList').innerHTML = '';
}

// 关闭上传对话框
function closeUploadModal() {
    document.getElementById('uploadModal').classList.remove('active');
}

// 处理文件选择
function handleFileSelect(e) {
    const files = Array.from(e.target.files);
    uploadFiles(files);
}

// 上传文件
async function uploadFiles(files) {
    const uploadList = document.getElementById('uploadList');
    
    for (const file of files) {
        const uploadItem = document.createElement('div');
        uploadItem.className = 'upload-item';
        uploadItem.innerHTML = `
            <div class="upload-item-info">
                <i data-lucide="file"></i>
                <div style="flex: 1;">
                    <div>${escapeHtml(file.name)}</div>
                    <div class="upload-progress">
                        <div class="upload-progress-bar" style="width: 0%"></div>
                    </div>
                </div>
            </div>
            <span class="upload-status">上传中...</span>
        `;
        uploadList.appendChild(uploadItem);
        lucide.createIcons();
        
        try {
            const progressBar = uploadItem.querySelector('.upload-progress-bar');
            const status = uploadItem.querySelector('.upload-status');
            
            // 读取文件
            const fileData = await file.arrayBuffer();
            
            // 模拟进度
            progressBar.style.width = '50%';
            
            // 上传
            const response = await fetch(
                `${API_BASE_URL}/api/files/upload?roomKey=${roomKey}&folder=${encodeURIComponent(currentFolder)}`,
                {
                    method: 'POST',
                    headers: {
                        'Authorization': `Bearer ${token}`,
                        'Content-Type': file.type,
                        'X-File-Name': file.name
                    },
                    body: fileData
                }
            );
            
            if (!response.ok) {
                const error = await response.json();
                throw new Error(error.error || '上传失败');
            }
            
            progressBar.style.width = '100%';
            status.textContent = '完成';
            status.style.color = 'var(--success-color)';
            
        } catch (error) {
            console.error('上传失败:', error);
            const status = uploadItem.querySelector('.upload-status');
            status.textContent = '失败: ' + error.message;
            status.style.color = 'var(--danger-color)';
        }
    }
    
    // 3秒后刷新列表并关闭对话框
    setTimeout(() => {
        closeUploadModal();
        loadFiles();
        loadStorageQuota();
    }, 3000);
}

// 下载相关变量
let downloadController = null;  // 用于取消下载
let downloadStartTime = 0;

// 下载文件
function downloadFile() {
    if (!selectedFile) return;
    downloadFileById(selectedFile.id, selectedFile.name, selectedFile.size);
}

// 格式化文件大小
function formatSize(bytes) {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

// 显示下载进度弹窗
function showDownloadProgress(fileName) {
    const modal = document.getElementById('downloadProgressModal');
    document.getElementById('downloadFileName').textContent = fileName;
    document.getElementById('downloadProgressBar').style.width = '0%';
    document.getElementById('downloadProgressText').textContent = '0%';
    document.getElementById('downloadSizeText').textContent = '0 KB / 0 KB';
    document.getElementById('downloadSpeedText').textContent = '计算中...';
    modal.classList.add('active');
    lucide.createIcons();
}

// 更新下载进度
function updateDownloadProgress(loaded, total) {
    const percent = total > 0 ? Math.round((loaded / total) * 100) : 0;
    document.getElementById('downloadProgressBar').style.width = percent + '%';
    document.getElementById('downloadProgressText').textContent = percent + '%';
    document.getElementById('downloadSizeText').textContent = `${formatSize(loaded)} / ${formatSize(total)}`;
    
    // 计算下载速度
    const elapsed = (Date.now() - downloadStartTime) / 1000;  // 秒
    if (elapsed > 0) {
        const speed = loaded / elapsed;
        document.getElementById('downloadSpeedText').textContent = formatSize(speed) + '/s';
    }
}

// 关闭下载进度弹窗
function closeDownloadProgress() {
    document.getElementById('downloadProgressModal').classList.remove('active');
}

// 取消下载
function cancelDownload() {
    if (downloadController) {
        downloadController.abort();
        downloadController = null;
    }
    closeDownloadProgress();
}

// 下载文件 - 使用 fetch 带进度追踪
async function downloadFileById(fileId, fileName, fileSize) {
    // 构造下载 URL
    const url = `${API_BASE_URL}/api/files/download?roomKey=${encodeURIComponent(roomKey)}&fileId=${fileId}&token=${encodeURIComponent(token)}`;
    
    // 创建 AbortController 用于取消下载
    downloadController = new AbortController();
    downloadStartTime = Date.now();
    
    // 显示进度弹窗
    showDownloadProgress(fileName || 'download');
    
    try {
        const response = await fetch(url, {
            signal: downloadController.signal
        });
        
        if (!response.ok) {
            throw new Error(`下载失败 (HTTP ${response.status})`);
        }
        
        // 获取文件总大小
        const contentLength = response.headers.get('content-length');
        const total = contentLength ? parseInt(contentLength, 10) : (fileSize || 0);
        
        // 使用 ReadableStream 读取数据并追踪进度
        const reader = response.body.getReader();
        const chunks = [];
        let loaded = 0;
        
        while (true) {
            const { done, value } = await reader.read();
            
            if (done) break;
            
            chunks.push(value);
            loaded += value.length;
            
            // 更新进度
            updateDownloadProgress(loaded, total);
        }
        
        // 合并所有数据块
        const blob = new Blob(chunks);
        
        // 创建下载链接
        const downloadUrl = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = downloadUrl;
        a.download = fileName || 'download';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(downloadUrl);
        
        // 关闭进度弹窗
        closeDownloadProgress();
        
    } catch (error) {
        if (error.name === 'AbortError') {
            console.log('下载已取消');
        } else {
            console.error('下载失败:', error);
            alert('下载失败: ' + error.message);
        }
        closeDownloadProgress();
    } finally {
        downloadController = null;
    }
}

// 删除文件
async function deleteFile() {
    if (!selectedFile) return;
    
    if (!confirm(`确定要删除文件 "${selectedFile.name}" 吗？`)) {
        return;
    }
    
    try {
        const response = await fetch(`${API_BASE_URL}/api/files/delete`, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                roomKey: roomKey,
                fileId: selectedFile.id
            })
        });
        
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.error || '删除失败');
        }
        
        alert('删除成功');
        selectedFile = null;
        loadFiles();
        loadStorageQuota();
        
    } catch (error) {
        console.error('删除失败:', error);
        alert('删除失败: ' + error.message);
    }
}

// 显示文件信息
function showFileInfo() {
    if (!selectedFile) return;
    
    const modal = document.getElementById('fileInfoModal');
    const content = document.getElementById('fileInfoContent');
    
    content.innerHTML = `
        <div style="display: grid; gap: 15px;">
            <div>
                <strong>文件名：</strong><br>
                ${escapeHtml(selectedFile.name)}
            </div>
            <div>
                <strong>大小：</strong><br>
                ${selectedFile.sizeFormatted} (${selectedFile.size} 字节)
            </div>
            <div>
                <strong>类型：</strong><br>
                ${escapeHtml(selectedFile.type || '未知')}
            </div>
            <div>
                <strong>上传时间：</strong><br>
                ${new Date(selectedFile.createdAt).toLocaleString('zh-CN')}
            </div>
            <div>
                <strong>下载次数：</strong><br>
                ${selectedFile.downloads}
            </div>
            <div>
                <strong>所在文件夹：</strong><br>
                ${escapeHtml(selectedFile.folder)}
            </div>
        </div>
    `;
    
    modal.classList.add('active');
}

// 关闭文件信息对话框
function closeFileInfoModal() {
    document.getElementById('fileInfoModal').classList.remove('active');
}

// 重命名文件（预留）
function renameFile() {
    alert('重命名功能开发中...');
}

// 显示右键菜单
function showContextMenu(x, y) {
    const menu = document.getElementById('contextMenu');
    // 隐藏文件夹菜单
    const folderMenu = document.getElementById('folderContextMenu');
    if (folderMenu) folderMenu.classList.remove('active');
    
    menu.style.left = x + 'px';
    menu.style.top = y + 'px';
    menu.classList.add('active');
}

// 显示文件夹右键菜单
function showFolderContextMenu(x, y) {
    const folderMenu = document.getElementById('folderContextMenu');
    // 隐藏文件菜单
    document.getElementById('contextMenu').classList.remove('active');
    
    if (folderMenu) {
        folderMenu.style.left = x + 'px';
        folderMenu.style.top = y + 'px';
        folderMenu.classList.add('active');
    }
}

// ============ 文件夹功能 ============

// 打开新建文件夹对话框
function openNewFolderModal() {
    const modal = document.getElementById('newFolderModal');
    if (modal) {
        modal.classList.add('active');
        const input = document.getElementById('newFolderName');
        if (input) {
            input.value = '';
            input.focus();
        }
    }
}

// 关闭新建文件夹对话框
function closeNewFolderModal() {
    const modal = document.getElementById('newFolderModal');
    if (modal) {
        modal.classList.remove('active');
    }
}

// 创建文件夹
async function createFolder() {
    const input = document.getElementById('newFolderName');
    const folderName = input?.value.trim();
    
    if (!folderName) {
        alert('请输入文件夹名称');
        return;
    }
    
    // 验证文件夹名称
    if (!/^[^\\/:*?"<>|]+$/.test(folderName)) {
        alert('文件夹名称不能包含特殊字符 \\ / : * ? " < > |');
        return;
    }
    
    try {
        const response = await fetch(`${API_BASE_URL}/api/folders/create`, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                roomKey: roomKey,
                parentPath: currentFolder,
                folderName: folderName
            })
        });
        
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.error || '创建文件夹失败');
        }
        
        closeNewFolderModal();
        loadFiles();
        
    } catch (error) {
        console.error('创建文件夹失败:', error);
        alert('创建文件夹失败: ' + error.message);
    }
}

// 打开文件夹（双击或右键菜单）
function openFolder() {
    if (selectedItem && selectedIsFolder) {
        navigateToFolder(selectedItem.path);
    }
}

// 删除文件夹
async function deleteFolder() {
    if (!selectedItem || !selectedIsFolder) return;
    
    if (!confirm(`确定要删除文件夹 "${selectedItem.name}" 及其中的所有内容吗？此操作不可恢复！`)) {
        return;
    }
    
    try {
        const response = await fetch(`${API_BASE_URL}/api/folders/delete`, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                roomKey: roomKey,
                folderId: selectedItem.id
            })
        });
        
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.error || '删除文件夹失败');
        }
        
        alert('删除成功');
        selectedItem = null;
        selectedIsFolder = false;
        loadFiles();
        loadStorageQuota();
        
    } catch (error) {
        console.error('删除文件夹失败:', error);
        alert('删除文件夹失败: ' + error.message);
    }
}

// 重命名文件夹
async function renameFolder() {
    if (!selectedItem || !selectedIsFolder) return;
    
    const newName = prompt('请输入新的文件夹名称:', selectedItem.name);
    if (!newName || newName.trim() === '' || newName.trim() === selectedItem.name) {
        return;
    }
    
    // 验证文件夹名称
    if (!/^[^\\/:*?"<>|]+$/.test(newName.trim())) {
        alert('文件夹名称不能包含特殊字符 \\ / : * ? " < > |');
        return;
    }
    
    try {
        const response = await fetch(`${API_BASE_URL}/api/folders/rename`, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                roomKey: roomKey,
                folderId: selectedItem.id,
                newName: newName.trim()
            })
        });
        
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.error || '重命名失败');
        }
        
        loadFiles();
        
    } catch (error) {
        console.error('重命名文件夹失败:', error);
        alert('重命名失败: ' + error.message);
    }
}

// 显示文件夹信息
function showFolderInfo() {
    if (!selectedItem || !selectedIsFolder) return;
    
    const modal = document.getElementById('fileInfoModal');
    const content = document.getElementById('fileInfoContent');
    
    content.innerHTML = `
        <div style="display: grid; gap: 15px;">
            <div>
                <strong>文件夹名称：</strong><br>
                ${escapeHtml(selectedItem.name)}
            </div>
            <div>
                <strong>路径：</strong><br>
                ${escapeHtml(selectedItem.path)}
            </div>
            <div>
                <strong>包含项目：</strong><br>
                ${selectedItem.fileCount || 0} 个
            </div>
            <div>
                <strong>创建时间：</strong><br>
                ${selectedItem.createdAt ? new Date(selectedItem.createdAt).toLocaleString('zh-CN') : '未知'}
            </div>
        </div>
    `;
    
    modal.classList.add('active');
}

// HTML 转义
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}
