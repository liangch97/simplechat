// 嵌入式网盘面板逻辑（聊天页右侧）
// 依赖：config.js、chat.js、lucide.min.js
(() => {
    const TOKEN_KEY = 'sysu_chat_token';
    const ROOM_KEY = 'sysu_chat_room_key';
    const API_BASE_URL = (typeof CONFIG !== 'undefined' ? CONFIG.getApiConfig().baseUrl : '') || '';

    const state = {
        currentFolder: '/',
        folders: [],
        files: [],
        loading: false,
    };

    const els = {};

    function cacheElements() {
        els.panel = document.getElementById('filesPanel');
        els.list = document.getElementById('filesPanelList');
        els.loading = document.getElementById('filesPanelLoading');
        els.empty = document.getElementById('filesPanelEmpty');
        els.breadcrumb = document.getElementById('filesPanelBreadcrumb');
        els.storageText = document.getElementById('filesPanelStorageText');
        els.storageBar = document.getElementById('filesPanelStorageBar');
        els.search = document.getElementById('filesPanelSearch');
        els.refreshBtn = document.getElementById('filesPanelRefresh');
        els.closeBtn = document.getElementById('filesPanelClose');
        els.filesBtn = document.getElementById('filesBtn');
        els.uploadBtn = document.getElementById('filesPanelUpload');
        els.fileInput = document.getElementById('filesPanelFileInput');
        els.uploadModal = document.getElementById('filesUploadModal');
        els.uploadModalClose = document.getElementById('filesUploadModalClose');
        els.uploadArea = document.getElementById('filesUploadArea');
        els.uploadList = document.getElementById('filesUploadList');
        els.dropZone = document.getElementById('filesPanelDropZone');
        els.newFolderBtn = document.getElementById('filesPanelNewFolder');
        els.folderModal = document.getElementById('filesFolderModal');
        els.folderModalClose = document.getElementById('filesFolderModalClose');
        els.folderNameInput = document.getElementById('filesFolderName');
        els.folderCreateBtn = document.getElementById('filesFolderCreate');
        els.folderCancelBtn = document.getElementById('filesFolderCancel');
        els.contextMenu = document.getElementById('filesContextMenu');
    }

    function getAuth() {
        const token = localStorage.getItem(TOKEN_KEY);
        const roomKey = localStorage.getItem(ROOM_KEY);
        if (!token || !roomKey) {
            if (window.chatApp && typeof window.chatApp.showToast === 'function') {
                window.chatApp.showToast('请先登录后再使用群文件', 'warning');
            } else {
                alert('请先登录后再使用群文件');
            }
            return null;
        }
        return { token, roomKey };
    }

    function showPanel() {
        if (!els.panel) return;
        const auth = getAuth();
        if (!auth) return;
        els.panel.classList.remove('hidden');
        requestAnimationFrame(() => {
            els.panel.classList.add('show');
        });
        refreshAll();
    }

    function hidePanel() {
        if (!els.panel) return;
        els.panel.classList.remove('show');
        setTimeout(() => {
            els.panel.classList.add('hidden');
        }, 250);
    }

    async function refreshAll() {
        await Promise.all([loadQuota(), loadFiles(state.currentFolder)]);
    }

    async function loadQuota() {
        const auth = getAuth();
        if (!auth) return;
        try {
            const resp = await fetch(`${API_BASE_URL}/api/files/quota?roomKey=${encodeURIComponent(auth.roomKey)}`, {
                headers: { 'Authorization': `Bearer ${auth.token}` },
            });
            if (!resp.ok) throw new Error('获取配额失败');
            const data = await resp.json();
            const quota = data.quota || {};
            const used = quota.used || 0;
            const total = quota.total || 1;
            const percent = Math.min(100, Math.round((used / total) * 100));
            if (els.storageBar) els.storageBar.style.width = `${percent}%`;
            if (els.storageText) {
                const usedFormatted = quota.usedFormatted || formatSize(used);
                const totalFormatted = quota.totalFormatted || formatSize(total);
                els.storageText.textContent = `${usedFormatted} / ${totalFormatted} (${percent}%)`;
            }
        } catch (err) {
            console.error('[Files] loadQuota failed', err);
        }
    }

    async function loadFiles(folder = '/') {
        const auth = getAuth();
        if (!auth) return;
        state.loading = true;
        toggleLoading(true);
        try {
            const resp = await fetch(`${API_BASE_URL}/api/files/list?roomKey=${encodeURIComponent(auth.roomKey)}&folder=${encodeURIComponent(folder)}`, {
                headers: { 'Authorization': `Bearer ${auth.token}` },
            });
            if (!resp.ok) throw new Error('获取文件列表失败');
            const data = await resp.json();
            state.currentFolder = folder;
            state.files = data.files || [];
            state.folders = data.folders || [];
            renderBreadcrumb();
            renderList();
        } catch (err) {
            console.error('[Files] loadFiles failed', err);
            if (window.chatApp && window.chatApp.showToast) {
                window.chatApp.showToast('加载文件失败', 'error');
            }
        } finally {
            state.loading = false;
            toggleLoading(false);
        }
    }

    function toggleLoading(show) {
        if (els.loading) els.loading.classList.toggle('hidden', !show);
        if (els.empty) els.empty.classList.toggle('hidden', true);
        if (els.list) els.list.style.display = show ? 'none' : 'grid';
    }

    function renderBreadcrumb() {
        if (!els.breadcrumb) return;
        els.breadcrumb.innerHTML = '';
        const home = document.createElement('a');
        home.href = '#';
        home.className = 'breadcrumb-item';
        home.dataset.path = '/';
        home.innerHTML = '<i data-lucide="home"></i>';
        home.addEventListener('click', (e) => {
            e.preventDefault();
            navigateTo('/');
        });
        els.breadcrumb.appendChild(home);

        if (state.currentFolder !== '/') {
            const parts = state.currentFolder.split('/').filter(Boolean);
            let path = '';
            parts.forEach((part) => {
                path += '/' + part;
                const sep = document.createElement('span');
                sep.className = 'breadcrumb-separator';
                sep.innerHTML = '<i data-lucide="chevron-right"></i>';
                els.breadcrumb.appendChild(sep);
                const item = document.createElement('a');
                item.href = '#';
                item.className = 'breadcrumb-item';
                item.dataset.path = path;
                item.textContent = part;
                item.addEventListener('click', (e) => {
                    e.preventDefault();
                    navigateTo(path);
                });
                els.breadcrumb.appendChild(item);
            });
        }
        lucide.createIcons();
    }

    function renderList() {
        if (!els.list) return;
        els.list.innerHTML = '';
        if (state.currentFolder !== '/') {
            const back = document.createElement('div');
            back.className = 'file-item back-item';
            back.innerHTML = `
                <div class="file-icon"><i data-lucide="corner-left-up"></i></div>
                <div class="file-details">
                    <div class="file-name">返回上级</div>
                    <div class="file-meta">${state.currentFolder}</div>
                </div>`;
            back.addEventListener('dblclick', () => {
                const parts = state.currentFolder.split('/').filter(Boolean);
                parts.pop();
                navigateTo('/' + parts.join('/'));
            });
            els.list.appendChild(back);
        }

        state.folders.forEach((folder) => {
            const item = document.createElement('div');
            item.className = 'file-item folder-item';
            item.innerHTML = `
                <div class="file-icon"><i data-lucide="folder"></i></div>
                <div class="file-details">
                    <div class="file-name">${escapeHtml(folder.name)}</div>
                    <div class="file-meta">${folder.fileCount || 0} 个项目</div>
                </div>
                <div class="file-actions">
                    <button class="icon-btn" title="打开"><i data-lucide="folder-open"></i></button>
                    <button class="icon-btn" title="删除"><i data-lucide="trash-2"></i></button>
                </div>`;
            item.addEventListener('click', () => selectItem(item));
            item.addEventListener('dblclick', () => navigateTo(folder.path));
            const [openBtn, delBtn] = item.querySelectorAll('button');
            openBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                navigateTo(folder.path);
            });
            delBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                deleteFolder(folder);
            });
            els.list.appendChild(item);
        });

        state.files.forEach((file) => {
            const icon = getFileIcon(file.extension);
            const item = document.createElement('div');
            item.className = 'file-item';
            item.innerHTML = `
                <div class="file-icon"><i data-lucide="${icon}"></i></div>
                <div class="file-details">
                    <div class="file-name">${escapeHtml(file.name)}</div>
                    <div class="file-meta">${file.sizeFormatted || formatSize(file.size || 0)}</div>
                </div>
                <div class="file-actions">
                    <button class="icon-btn" title="下载"><i data-lucide="download"></i></button>
                    <button class="icon-btn" title="删除"><i data-lucide="trash-2"></i></button>
                </div>`;
            item.addEventListener('dblclick', () => downloadFile(file));
            const [dlBtn, delBtn] = item.querySelectorAll('button');
            dlBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                downloadFile(file);
            });
            delBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                deleteFile(file);
            });
            els.list.appendChild(item);
        });

        if ((state.files.length + state.folders.length) === 0 && els.empty) {
            els.empty.classList.remove('hidden');
        } else if (els.empty) {
            els.empty.classList.add('hidden');
        }

        lucide.createIcons();
    }

    function selectItem(item) {
        document.querySelectorAll('#filesPanelList .file-item').forEach((el) => el.classList.remove('selected'));
        item.classList.add('selected');
    }

    function navigateTo(path) {
        const normalized = path === '' ? '/' : path;
        loadFiles(normalized);
    }

    async function search(keyword) {
        const auth = getAuth();
        if (!auth) return;
        if (!keyword) {
            loadFiles(state.currentFolder);
            return;
        }
        toggleLoading(true);
        try {
            const resp = await fetch(`${API_BASE_URL}/api/files/search?roomKey=${encodeURIComponent(auth.roomKey)}&keyword=${encodeURIComponent(keyword)}`, {
                headers: { 'Authorization': `Bearer ${auth.token}` },
            });
            if (!resp.ok) throw new Error('搜索失败');
            const data = await resp.json();
            state.files = data.files || [];
            state.folders = [];
            renderList();
        } catch (err) {
            console.error('[Files] search failed', err);
            if (window.chatApp && window.chatApp.showToast) {
                window.chatApp.showToast('搜索失败', 'error');
            }
        } finally {
            toggleLoading(false);
        }
    }

    async function downloadFile(file) {
        const auth = getAuth();
        if (!auth) return;
        try {
            const url = `${API_BASE_URL}/api/files/download?roomKey=${encodeURIComponent(auth.roomKey)}&fileId=${file.id}`;
            const resp = await fetch(url, { headers: { 'Authorization': `Bearer ${auth.token}` } });
            if (!resp.ok) {
                // 尝试解析服务器返回的错误信息
                let errorMsg = '下载失败';
                try {
                    const errorData = await resp.json();
                    errorMsg = errorData.error || errorMsg;
                } catch (e) {
                    errorMsg = `下载失败 (HTTP ${resp.status})`;
                }
                throw new Error(errorMsg);
            }
            const blob = await resp.blob();
            const link = document.createElement('a');
            link.href = URL.createObjectURL(blob);
            link.download = file.name;
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            URL.revokeObjectURL(link.href);
        } catch (err) {
            console.error('[Files] download failed', err);
            alert('下载失败: ' + err.message);
        }
    }

    async function deleteFile(file) {
        const auth = getAuth();
        if (!auth) return;
        if (!confirm(`确定删除文件 "${file.name}" 吗？`)) return;
        try {
            const resp = await fetch(`${API_BASE_URL}/api/files/delete`, {
                method: 'POST',
                headers: {
                    'Authorization': `Bearer ${auth.token}`,
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ roomKey: auth.roomKey, fileId: file.id }),
            });
            const data = await resp.json();
            if (!resp.ok || !data.success) throw new Error(data.error || '删除失败');
            loadFiles(state.currentFolder);
            loadQuota();
        } catch (err) {
            console.error('[Files] delete failed', err);
            alert('删除失败: ' + err.message);
        }
    }

    async function deleteFolder(folder) {
        const auth = getAuth();
        if (!auth) return;
        if (!confirm(`确定删除文件夹 "${folder.name}" 及其内容吗？`)) return;
        try {
            const resp = await fetch(`${API_BASE_URL}/api/folders/delete`, {
                method: 'POST',
                headers: {
                    'Authorization': `Bearer ${auth.token}`,
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ roomKey: auth.roomKey, folderId: folder.id }),
            });
            const data = await resp.json();
            if (!resp.ok || !data.success) throw new Error(data.error || '删除失败');
            loadFiles(state.currentFolder);
            loadQuota();
        } catch (err) {
            console.error('[Files] delete folder failed', err);
            alert('删除文件夹失败: ' + err.message);
        }
    }

    function openUploadModal() {
        if (els.uploadModal) els.uploadModal.classList.remove('hidden');
    }

    function closeUploadModal() {
        if (els.uploadModal) els.uploadModal.classList.add('hidden');
        if (els.uploadList) els.uploadList.innerHTML = '';
        if (els.fileInput) els.fileInput.value = '';
    }

    async function uploadFiles(fileList) {
        const auth = getAuth();
        if (!auth) return;
        if (!fileList || fileList.length === 0) return;
        openUploadModal();
        for (const file of fileList) {
            const item = document.createElement('div');
            item.className = 'upload-item';
            item.innerHTML = `
                <div class="file-info">
                    <div class="file-name">${escapeHtml(file.name)}</div>
                    <div class="file-size">${formatSize(file.size)}</div>
                </div>
                <div class="upload-progress">
                    <div class="progress-bar"><div class="progress-fill" style="width:0%"></div></div>
                    <div class="upload-status">上传中...</div>
                </div>`;
            const progressFill = item.querySelector('.progress-fill');
            const statusText = item.querySelector('.upload-status');
            els.uploadList?.appendChild(item);
            try {
                const arrayBuffer = await file.arrayBuffer();
                progressFill.style.width = '30%';
                const resp = await fetch(`${API_BASE_URL}/api/files/upload?roomKey=${encodeURIComponent(auth.roomKey)}&folder=${encodeURIComponent(state.currentFolder)}`, {
                    method: 'POST',
                    headers: {
                        'Authorization': `Bearer ${auth.token}`,
                        'Content-Type': file.type || 'application/octet-stream',
                        'X-File-Name': encodeURIComponent(file.name),
                    },
                    body: arrayBuffer,
                });
                const data = await resp.json().catch(() => ({}));
                if (!resp.ok || data.success === false) {
                    throw new Error(data.error || '上传失败');
                }
                progressFill.style.width = '100%';
                statusText.textContent = '完成';
                statusText.classList.add('success');
            } catch (err) {
                console.error('[Files] upload failed', err);
                progressFill.style.width = '100%';
                statusText.textContent = '失败: ' + err.message;
                statusText.classList.add('error');
            }
        }
        setTimeout(() => {
            closeUploadModal();
            loadFiles(state.currentFolder);
            loadQuota();
        }, 1200);
    }

    function openFolderModal() {
        if (els.folderModal) {
            els.folderModal.classList.remove('hidden');
            if (els.folderNameInput) {
                els.folderNameInput.value = '';
                els.folderNameInput.focus();
            }
        }
    }

    function closeFolderModal() {
        if (els.folderModal) els.folderModal.classList.add('hidden');
    }

    async function createFolder() {
        const auth = getAuth();
        if (!auth) return;
        const name = (els.folderNameInput?.value || '').trim();
        if (!name) {
            alert('请输入文件夹名称');
            return;
        }
        try {
            const resp = await fetch(`${API_BASE_URL}/api/folders/create`, {
                method: 'POST',
                headers: {
                    'Authorization': `Bearer ${auth.token}`,
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ roomKey: auth.roomKey, parentPath: state.currentFolder, folderName: name }),
            });
            const data = await resp.json();
            if (!resp.ok || data.success === false) throw new Error(data.error || '创建失败');
            closeFolderModal();
            loadFiles(state.currentFolder);
        } catch (err) {
            console.error('[Files] create folder failed', err);
            alert('创建失败: ' + err.message);
        }
    }

    function bindEvents() {
        if (els.filesBtn) {
            els.filesBtn.addEventListener('click', (e) => {
                e.preventDefault();
                showPanel();
            });
        }
        if (els.closeBtn) {
            els.closeBtn.addEventListener('click', hidePanel);
        }
        if (els.refreshBtn) {
            els.refreshBtn.addEventListener('click', () => refreshAll());
        }
        if (els.search) {
            let timer = null;
            els.search.addEventListener('input', (e) => {
                clearTimeout(timer);
                const value = e.target.value.trim();
                timer = setTimeout(() => search(value), 300);
            });
        }
        if (els.uploadBtn && els.fileInput) {
            els.uploadBtn.addEventListener('click', () => els.fileInput.click());
            els.fileInput.addEventListener('change', (e) => {
                const files = Array.from(e.target.files || []);
                uploadFiles(files);
            });
        }
        if (els.uploadArea) {
            els.uploadArea.addEventListener('click', () => els.fileInput?.click());
            ['dragover', 'drop'].forEach((evt) => {
                els.uploadArea.addEventListener(evt, (e) => e.preventDefault());
            });
            els.uploadArea.addEventListener('drop', (e) => {
                e.preventDefault();
                const files = Array.from(e.dataTransfer.files || []);
                uploadFiles(files);
            });
        }
        if (els.uploadModalClose) {
            els.uploadModalClose.addEventListener('click', closeUploadModal);
        }
        if (els.panel) {
            // 拖拽上传
            els.panel.addEventListener('dragover', (e) => {
                e.preventDefault();
                els.dropZone?.classList.remove('hidden');
            });
            els.panel.addEventListener('dragleave', () => {
                els.dropZone?.classList.add('hidden');
            });
            els.panel.addEventListener('drop', (e) => {
                e.preventDefault();
                els.dropZone?.classList.add('hidden');
                const files = Array.from(e.dataTransfer.files || []);
                uploadFiles(files);
            });
        }
        if (els.newFolderBtn) {
            els.newFolderBtn.addEventListener('click', openFolderModal);
        }
        if (els.folderModalClose) {
            els.folderModalClose.addEventListener('click', closeFolderModal);
        }
        if (els.folderCancelBtn) {
            els.folderCancelBtn.addEventListener('click', closeFolderModal);
        }
        if (els.folderCreateBtn) {
            els.folderCreateBtn.addEventListener('click', createFolder);
        }
        document.addEventListener('click', (e) => {
            if (els.uploadModal && !els.uploadModal.classList.contains('hidden') && e.target === els.uploadModal) {
                closeUploadModal();
            }
            if (els.folderModal && !els.folderModal.classList.contains('hidden') && e.target === els.folderModal) {
                closeFolderModal();
            }
            if (els.contextMenu) {
                els.contextMenu.classList.add('hidden');
            }
        });
    }

    function formatSize(bytes) {
        if (!bytes) return '0 B';
        const units = ['B', 'KB', 'MB', 'GB', 'TB'];
        let size = bytes;
        let idx = 0;
        while (size >= 1024 && idx < units.length - 1) {
            size /= 1024;
            idx++;
        }
        return `${size.toFixed(size >= 10 || size < 1 ? 0 : 1)} ${units[idx]}`;
    }

    function getFileIcon(ext) {
        const map = {
            'jpg': 'image', 'jpeg': 'image', 'png': 'image', 'gif': 'image', 'svg': 'image',
            'mp4': 'video', 'avi': 'video', 'mov': 'video',
            'mp3': 'music', 'wav': 'music',
            'pdf': 'file-text',
            'doc': 'file-text', 'docx': 'file-text',
            'xls': 'file-spreadsheet', 'xlsx': 'file-spreadsheet',
            'zip': 'archive', 'rar': 'archive', '7z': 'archive',
            'js': 'code', 'java': 'code', 'py': 'code', 'cpp': 'code', 'c': 'code',
            'html': 'code', 'css': 'code', 'sql': 'code',
        };
        return map[(ext || '').toLowerCase()] || 'file';
    }

    function escapeHtml(str) {
        const div = document.createElement('div');
        div.textContent = str || '';
        return div.innerHTML;
    }

    document.addEventListener('DOMContentLoaded', () => {
        cacheElements();
        bindEvents();
    });
})();
