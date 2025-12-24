/**
 * SYSU Chat - 聊天核心逻辑
 * 域名: sysu.asia
 */

class ChatApp {
    constructor() {
        this.nickname = '';
        this.token = '';
        this.eventSource = null;
        this.reconnectAttempts = 0;
        this.isConnected = false;
        this.onlineUsers = []; // 在线用户列表，用于@功能
        this.mentionIndex = -1; // @提及选择索引
        this.realOnlineCount = 0; // 服务器实时推送的在线人数
        this.historyOffset = 0; // 历史消息加载偏移量
        this.isLoadingHistory = false; // 是否正在加载历史消息
        this.hasMoreHistory = true; // 是否还有更多历史消息
        
        this.init();
    }

    init() {
        this.cacheElements();
        this.bindEvents();
        this.restoreSession();
    }

    cacheElements() {
        // 登录相关
        this.loginPanel = document.getElementById('loginPanel');
        // chatPanel 在新设计中不再需要，聊天区域始终可见
        this.loginForm = document.getElementById('loginForm');
        this.registerForm = document.getElementById('registerForm');
        this.switchToRegister = document.getElementById('switchToRegister');
        this.switchToLogin = document.getElementById('switchToLogin');
        this.switchToRegisterLink = document.getElementById('switchToRegisterLink');
        this.switchToLoginLink = document.getElementById('switchToLoginLink');
        
        // 登录表单字段
        this.loginUsername = document.getElementById('loginUsername');
        this.loginPassword = document.getElementById('loginPassword');
        
        // 注册表单字段
        this.regUsername = document.getElementById('regUsername');
        this.regPassword = document.getElementById('regPassword');
        this.regNickname = document.getElementById('regNickname');
        this.regSecretKey = document.getElementById('regSecretKey');
        
        // 用户信息（侧边栏）
        this.userInfo = document.getElementById('userInfo');
        this.userAvatar = document.getElementById('userAvatar');
        this.userName = document.getElementById('userName');
        this.userList = document.getElementById('userList'); // 新设计中的用户列表容器
        
        // 聊天相关
        this.messagesContainer = document.getElementById('messagesContainer');
        this.messageForm = document.getElementById('messageForm');
        this.messageInput = document.getElementById('messageInput');
        this.logoutBtn = document.getElementById('logoutBtn');
        
        // 状态显示
        this.statusDot = document.getElementById('statusDot');
        this.statusText = document.getElementById('statusText');
        this.onlineCount = document.getElementById('onlineCount');
        
        // 弹窗
        this.aboutModal = document.getElementById('aboutModal');
        this.closeAbout = document.getElementById('closeAbout');
        
        // 文件上传相关
        this.imageBtn = document.getElementById('imageBtn');
        this.fileBtn = document.getElementById('fileBtn');
        this.imageInput = document.getElementById('imageInput');
        this.fileInput = document.getElementById('fileInput');
        this.uploadProgress = document.getElementById('uploadProgress');
        this.progressFill = document.getElementById('progressFill');
        this.progressText = document.getElementById('progressText');
        
        // @提及相关
        this.mentionPopup = document.getElementById('mentionPopup');
        this.mentionList = document.getElementById('mentionList');
        
        // 表情选择器相关
        this.emojiBtn = document.getElementById('emojiBtn');
        this.emojiPopup = document.getElementById('emojiPopup');
        this.emojiList = document.getElementById('emojiList');
        this.emojiClose = document.getElementById('emojiClose');
        this.emojiTabs = document.querySelectorAll('.emoji-tab');
        this.emojiPanels = document.querySelectorAll('.emoji-panel');
        this.stickerList = document.getElementById('stickerList');
        this.uploadStickerBtn = document.getElementById('uploadStickerBtn');
        this.stickerInput = document.getElementById('stickerInput');
        
        // 主题切换相关
        this.themeBtn = document.getElementById('themeBtn');
        this.themePanel = document.getElementById('themePanel');
        this.themeOptions = document.querySelectorAll('.theme-option');
        
        // 移动端侧边栏相关
        this.sidebar = document.getElementById('sidebar');
        this.sidebarOverlay = document.getElementById('sidebarOverlay');
        this.menuBtn = document.getElementById('menuBtn');
    }

    bindEvents() {
        console.log('[ChatApp] Binding events...');
        console.log('loginForm:', this.loginForm);
        console.log('registerForm:', this.registerForm);
        console.log('switchToRegisterLink:', this.switchToRegisterLink);
        console.log('switchToLoginLink:', this.switchToLoginLink);
        
        // 登录表单
        if (this.loginForm) {
            this.loginForm.addEventListener('submit', (e) => {
                e.preventDefault();
                console.log('[ChatApp] Login form submitted');
                this.login();
            });
        }
        
        // 注册表单
        if (this.registerForm) {
            this.registerForm.addEventListener('submit', (e) => {
                e.preventDefault();
                console.log('[ChatApp] Register form submitted');
                this.register();
            });
        }
        
        // 切换到注册
        if (this.switchToRegisterLink) {
            this.switchToRegisterLink.addEventListener('click', (e) => {
                e.preventDefault();
                console.log('[ChatApp] Switch to register clicked');
                this.showRegisterForm();
            });
        }
        
        // 切换到登录
        if (this.switchToLoginLink) {
            this.switchToLoginLink.addEventListener('click', (e) => {
                e.preventDefault();
                console.log('[ChatApp] Switch to login clicked');
                this.showLoginForm();
            });
        }

        // 发送消息
        if (this.messageForm) {
            this.messageForm.addEventListener('submit', (e) => {
                e.preventDefault();
                this.sendMessage();
            });
        }

        // 退出登录
        if (this.logoutBtn) {
            this.logoutBtn.addEventListener('click', () => {
                this.logout();
            });
        }

        // 关于弹窗
        document.querySelectorAll('.nav-link').forEach(link => {
            link.addEventListener('click', (e) => {
                if (link.getAttribute('href') === '#about') {
                    e.preventDefault();
                    this.showAboutModal();
                }
            });
        });

        if (this.closeAbout) {
            this.closeAbout.addEventListener('click', () => {
                this.hideAboutModal();
            });
        }

        if (this.aboutModal) {
            this.aboutModal.querySelector('.modal-overlay').addEventListener('click', () => {
                this.hideAboutModal();
            });
        }

        // 快捷键
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') {
                this.hideAboutModal();
                this.hideMentionPopup();
                this.hideEmojiPopup();
            }
        });

        // 点击页面其他地方关闭弹窗
        document.addEventListener('click', (e) => {
            // 关闭表情弹窗
            if (this.emojiPopup && !this.emojiPopup.classList.contains('hidden')) {
                // 检查点击目标是否在表情弹窗内或是表情按钮（包括其子元素）
                const isInsidePopup = this.emojiPopup.contains(e.target);
                const isEmojiBtn = this.emojiBtn && (this.emojiBtn === e.target || this.emojiBtn.contains(e.target));
                
                if (!isInsidePopup && !isEmojiBtn) {
                    this.hideEmojiPopup();
                }
            }
        });

        // 页面关闭前断开连接
        window.addEventListener('beforeunload', () => {
            this.disconnect();
        });
        
        // 滚动到顶部时加载更多历史消息
        if (this.messagesContainer) {
            this.messagesContainer.addEventListener('scroll', () => {
                // 当滚动到接近顶部时（距离顶部小于100px），加载更多历史消息
                if (this.messagesContainer.scrollTop < 100 && !this.isLoadingHistory && this.hasMoreHistory) {
                    this.loadHistory(true);
                }
            });
        }
        
        // 文件上传事件
        if (this.imageBtn) {
            this.imageBtn.addEventListener('click', (e) => {
                e.stopPropagation(); // 阻止事件冒泡
                this.imageInput.click();
            });
        }
        
        if (this.fileBtn) {
            this.fileBtn.addEventListener('click', (e) => {
                e.stopPropagation(); // 阻止事件冒泡
                this.fileInput.click();
            });
        }
        
        if (this.imageInput) {
            this.imageInput.addEventListener('change', (e) => {
                if (e.target.files.length > 0) {
                    this.uploadFile(e.target.files[0], 'image');
                }
            });
        }
        
        if (this.fileInput) {
            this.fileInput.addEventListener('change', (e) => {
                if (e.target.files.length > 0) {
                    this.uploadFile(e.target.files[0], 'file');
                }
            });
        }
        
        // @提及功能
        if (this.messageInput) {
            this.messageInput.addEventListener('input', (e) => {
                this.handleMentionInput(e);
            });
            
            this.messageInput.addEventListener('keydown', (e) => {
                this.handleMentionKeydown(e);
            });
        }
        
        // 表情选择器
        if (this.emojiBtn) {
            this.emojiBtn.addEventListener('click', (e) => {
                e.stopPropagation(); // 阻止事件冒泡，防止文档级别的click监听器关闭弹窗
                this.toggleEmojiPopup();
            });
        }
        
        if (this.emojiClose) {
            this.emojiClose.addEventListener('click', () => {
                this.hideEmojiPopup();
            });
        }
        
        // 表情包标签切换
        this.emojiTabs.forEach(tab => {
            tab.addEventListener('click', () => {
                this.switchEmojiTab(tab.dataset.tab);
            });
        });
        
        // 表情包上传
        if (this.uploadStickerBtn) {
            this.uploadStickerBtn.addEventListener('click', () => {
                this.stickerInput.click();
            });
        }
        
        if (this.stickerInput) {
            this.stickerInput.addEventListener('change', (e) => {
                if (e.target.files.length > 0) {
                    this.uploadStickers(e.target.files);
                }
            });
        }
        
        // 初始化表情列表
        this.initEmojiList();
        this.loadStickers();
        
        // 主题切换
        this.initThemeSwitcher();
        
        // 移动端侧边栏
        this.initMobileSidebar();
    }
    
    // ===== 移动端侧边栏 =====
    initMobileSidebar() {
        // 菜单按钮点击打开侧边栏
        if (this.menuBtn) {
            this.menuBtn.addEventListener('click', (e) => {
                e.stopPropagation(); // 阻止事件冒泡
                this.toggleSidebar();
            });
        }
        
        // 点击遮罩关闭侧边栏
        if (this.sidebarOverlay) {
            this.sidebarOverlay.addEventListener('click', () => {
                this.closeSidebar();
            });
        }
        
        // 点击用户列表项后关闭侧边栏（移动端）
        if (this.userList) {
            this.userList.addEventListener('click', () => {
                if (window.innerWidth <= 768) {
                    this.closeSidebar();
                }
            });
        }
        
        // 点击聊天区域关闭侧边栏（移动端）
        const chatArea = document.querySelector('.chat-area');
        if (chatArea) {
            chatArea.addEventListener('click', (e) => {
                // 确保不是点击菜单按钮或其子元素
                if (window.innerWidth <= 768 && !e.target.closest('#menuBtn')) {
                    this.closeSidebar();
                }
            });
        }
    }
    
    toggleSidebar() {
        if (this.sidebar && this.sidebar.classList.contains('open')) {
            this.closeSidebar();
        } else {
            this.openSidebar();
        }
    }
    
    openSidebar() {
        if (this.sidebar) {
            this.sidebar.classList.add('open');
        }
        if (this.sidebarOverlay) {
            this.sidebarOverlay.classList.add('show');
        }
        document.body.style.overflow = 'hidden';
    }
    
    closeSidebar() {
        if (this.sidebar) {
            this.sidebar.classList.remove('open');
        }
        if (this.sidebarOverlay) {
            this.sidebarOverlay.classList.remove('show');
        }
        document.body.style.overflow = '';
    }
    
    // 主题切换功能
    initThemeSwitcher() {
        // 恢复保存的主题
        const savedTheme = localStorage.getItem('sysu_chat_theme');
        if (savedTheme && savedTheme !== 'default') {
            document.documentElement.setAttribute('data-theme', savedTheme);
        }
        
        // 主题按钮点击
        if (this.themeBtn) {
            this.themeBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                this.themePanel.classList.toggle('active');
            });
        }
        
        // 主题选项点击
        this.themeOptions.forEach(option => {
            option.addEventListener('click', () => {
                const theme = option.dataset.theme;
                if (theme === 'default') {
                    document.documentElement.removeAttribute('data-theme');
                    localStorage.removeItem('sysu_chat_theme');
                } else {
                    document.documentElement.setAttribute('data-theme', theme);
                    localStorage.setItem('sysu_chat_theme', theme);
                }
                this.themePanel.classList.remove('active');
            });
        });
        
        // 点击其他地方关闭主题面板
        document.addEventListener('click', (e) => {
            if (this.themePanel && !this.themePanel.contains(e.target) && e.target !== this.themeBtn) {
                this.themePanel.classList.remove('active');
            }
        });
    }

    restoreSession() {
        const savedToken = localStorage.getItem('sysu_chat_token');
        if (savedToken) {
            this.verifyToken(savedToken);
        }
    }
    
    async verifyToken(token) {
        try {
            const url = CONFIG.getApiUrl('verifyEndpoint');
            const resp = await fetch(url, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ token })
            });
            const data = await resp.json();
            if (data.valid && data.nickname) {
                this.token = token;
                this.nickname = data.nickname;
                this.enterChatRoom();
            } else {
                localStorage.removeItem('sysu_chat_token');
            }
        } catch (e) {
            console.warn('[Auth] Token verify failed:', e);
            localStorage.removeItem('sysu_chat_token');
        }
    }
    
    showRegisterForm() {
        this.loginForm.classList.add('hidden');
        this.registerForm.classList.remove('hidden');
        this.switchToRegister.classList.add('hidden');
        this.switchToLogin.classList.remove('hidden');
        this.regUsername.focus();
    }
    
    showLoginForm() {
        this.registerForm.classList.add('hidden');
        this.loginForm.classList.remove('hidden');
        this.switchToLogin.classList.add('hidden');
        this.switchToRegister.classList.remove('hidden');
        this.loginUsername.focus();
    }
    
    async register() {
        const username = this.regUsername.value.trim();
        const password = this.regPassword.value;
        const nickname = this.regNickname.value.trim();
        const secretKey = this.regSecretKey.value.trim();
        
        if (username.length < 3) {
            this.showToast('用户名至少需要3个字符', 'warning');
            return;
        }
        if (password.length < 6) {
            this.showToast('密码至少需要6个字符', 'warning');
            return;
        }
        if (nickname.length < 2) {
            this.showToast('昵称至少需要2个字符', 'warning');
            return;
        }
        if (!secretKey) {
            this.showToast('请输入注册秘钥', 'warning');
            return;
        }
        
        try {
            const url = CONFIG.getApiUrl('registerEndpoint');
            const resp = await fetch(url, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password, nickname, secretKey })
            });
            const data = await resp.json();
            
            if (data.success) {
                this.showToast('注册成功，请登录', 'success');
                this.showLoginForm();
                this.loginUsername.value = username;
                this.loginPassword.focus();
            } else {
                this.showToast(data.error || '注册失败', 'error');
            }
        } catch (e) {
            console.error('[Auth] Register failed:', e);
            this.showToast('注册失败，请稍后重试', 'error');
        }
    }

    async login() {
        const username = this.loginUsername.value.trim();
        const password = this.loginPassword.value;
        
        if (!username || !password) {
            this.showToast('请输入用户名和密码', 'warning');
            return;
        }
        
        try {
            const url = CONFIG.getApiUrl('loginEndpoint');
            const resp = await fetch(url, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password })
            });
            const data = await resp.json();
            
            if (data.success && data.token) {
                this.token = data.token;
                this.nickname = data.nickname;
                localStorage.setItem('sysu_chat_token', data.token);
                this.showToast('登录成功', 'success');
                this.enterChatRoom();
            } else {
                this.showToast(data.error || '登录失败', 'error');
            }
        } catch (e) {
            console.error('[Auth] Login failed:', e);
            this.showToast('登录失败，请稍后重试', 'error');
        }
    }

    logout() {
        this.disconnect();
        localStorage.removeItem('sysu_chat_token');
        this.token = '';
        this.nickname = '';
        this.onlineUsers = []; // 清空在线用户列表
        this.renderUserList();
        this.exitChatRoom();
    }

    enterChatRoom() {
        // 更新 UI - 隐藏登录覆盖层
        if (this.loginPanel) {
            this.loginPanel.classList.add('hidden');
        }
        
        // 更新用户信息（如果元素存在）
        if (this.userAvatar) {
            this.userAvatar.textContent = this.nickname.charAt(0).toUpperCase();
        }
        if (this.userName) {
            this.userName.textContent = this.nickname;
        }
        
        // 清空之前的消息
        this.clearMessages();
        
        // 加载历史消息
        this.loadHistory();

        // 连接 SSE
        this.connect();
        
        // 重新加载该用户的表情包
        this.loadStickers();
        
        // 聚焦输入框
        if (this.messageInput) {
            this.messageInput.focus();
        }
    }

    exitChatRoom() {
        // 显示登录覆盖层
        if (this.loginPanel) {
            this.loginPanel.classList.remove('hidden');
        }
        if (this.loginUsername) {
            this.loginUsername.value = '';
        }
        if (this.loginPassword) {
            this.loginPassword.value = '';
        }
        this.showLoginForm();
        if (this.loginUsername) {
            this.loginUsername.focus();
        }
        
        // 重置用户信息（如果元素存在）
        if (this.userAvatar) {
            this.userAvatar.textContent = '?';
        }
        if (this.userName) {
            this.userName.textContent = '未登录';
        }
    }

    async loadHistory(loadMore = false) {
        if (this.isLoadingHistory) return;
        if (loadMore && !this.hasMoreHistory) return;
        
        this.isLoadingHistory = true;
        const limit = CONFIG.chat.messageHistoryLimit || 50;
        const offset = loadMore ? this.historyOffset : 0;
        const url = CONFIG.getApiUrl('historyEndpoint') + `?limit=${encodeURIComponent(limit)}&offset=${encodeURIComponent(offset)}`;
        
        try {
            // 如果是加载更多，显示加载提示
            let loadingEl = null;
            if (loadMore) {
                loadingEl = document.createElement('div');
                loadingEl.className = 'loading-more';
                loadingEl.innerHTML = '<span>加载中...</span>';
                this.messagesContainer.insertBefore(loadingEl, this.messagesContainer.firstChild);
            }
            
            const resp = await fetch(url, {
                method: 'GET',
                headers: {
                    'Accept': 'application/json'
                }
            });
            
            if (!resp.ok) {
                console.warn('[History] Server returned error:', resp.status);
                this.isLoadingHistory = false;
                if (loadingEl) loadingEl.remove();
                return;
            }
            
            const data = await resp.json();
            
            // 移除加载提示
            if (loadingEl) loadingEl.remove();
            
            // 兼容新旧 API 格式
            const messages = Array.isArray(data) ? data : (data.messages || []);
            const hasMore = Array.isArray(data) ? (messages.length >= limit) : data.hasMore;
            const total = Array.isArray(data) ? null : data.total;
            
            if (messages.length === 0) {
                this.hasMoreHistory = false;
                if (loadMore) {
                    this.showLoadMoreHint('已加载全部历史消息');
                }
            } else {
                // 记录当前滚动位置
                const scrollHeightBefore = this.messagesContainer.scrollHeight;
                const scrollTopBefore = this.messagesContainer.scrollTop;
                
                if (loadMore) {
                    // 加载更多时，将消息插入到顶部
                    for (let i = messages.length - 1; i >= 0; i--) {
                        this.handleMessage(messages[i], true, true); // 第三个参数表示插入到顶部
                    }
                    // 保持滚动位置
                    const scrollHeightAfter = this.messagesContainer.scrollHeight;
                    this.messagesContainer.scrollTop = scrollTopBefore + (scrollHeightAfter - scrollHeightBefore);
                    
                    // 显示加载进度
                    if (hasMore && total) {
                        const loaded = this.historyOffset + messages.length;
                        this.showLoadMoreHint(`向上滚动加载更多 (已加载 ${loaded}/${total})`, true);
                    } else if (!hasMore) {
                        this.showLoadMoreHint('已加载全部历史消息');
                    }
                } else {
                    // 初始加载
                    for (const line of messages) {
                        this.handleMessage(line, true);
                    }
                    
                    // 初次加载后，如果有更多历史，显示提示
                    if (hasMore && total) {
                        this.showLoadMoreHint(`↑ 向上滚动加载更多历史消息 (${messages.length}/${total})`, true);
                    }
                }
                
                this.historyOffset += messages.length;
                this.hasMoreHistory = hasMore;
            }
        } catch (e) {
            console.warn('[History] load failed:', e);
            // 移除加载提示（如果存在）
            const loadingEl = this.messagesContainer.querySelector('.loading-more');
            if (loadingEl) loadingEl.remove();
            
            // 如果是初次加载失败，显示提示
            if (!loadMore) {
                this.addSystemMessage('历史消息加载失败，请刷新页面重试');
            }
        } finally {
            this.isLoadingHistory = false;
        }
    }
    
    // 显示加载更多提示
    showLoadMoreHint(text, persistent = false) {
        // 移除旧的提示
        const oldHint = this.messagesContainer.querySelector('.load-more-hint');
        if (oldHint) oldHint.remove();
        
        const hint = document.createElement('div');
        hint.className = 'load-more-hint';
        hint.textContent = text;
        this.messagesContainer.insertBefore(hint, this.messagesContainer.firstChild);
        
        // 如果不是持久提示，3秒后自动移除
        if (!persistent) {
            setTimeout(() => hint.remove(), 3000);
        }
    }

    connect() {
        if (this.eventSource) {
            this.eventSource.close();
        }

        this.updateStatus('connecting');

        // 在 SSE URL 中传递用户昵称，以便服务器跟踪在线用户
        let eventsUrl = CONFIG.getApiUrl('eventsEndpoint');
        if (this.nickname) {
            eventsUrl += `?nickname=${encodeURIComponent(this.nickname)}`;
        }
        
        try {
            this.eventSource = new EventSource(eventsUrl);

            this.eventSource.onopen = () => {
                console.log('[SSE] Connected');
                this.isConnected = true;
                this.reconnectAttempts = 0;
                this.updateStatus('connected');
                this.addSystemMessage('已连接到聊天服务器');
            };

            this.eventSource.onmessage = (event) => {
                this.handleMessage(event.data);
            };

            this.eventSource.addEventListener('info', (event) => {
                console.log('[SSE] Info:', event.data);
                try {
                    const info = JSON.parse(event.data);
                    if (info.online !== undefined) {
                        this.updateOnlineCount(info.online);
                    }
                    // 更新在线用户列表
                    if (info.users !== undefined && Array.isArray(info.users)) {
                        this.setOnlineUsers(info.users);
                    }
                } catch (e) {
                    console.warn('[SSE] Failed to parse info event:', e);
                }
            });
            
            // 监听在线人数更新事件
            this.eventSource.addEventListener('online', (event) => {
                console.log('[SSE] Online update:', event.data);
                try {
                    const data = JSON.parse(event.data);
                    if (data.count !== undefined) {
                        this.updateOnlineCount(data.count);
                    }
                    // 更新在线用户列表
                    if (data.users !== undefined && Array.isArray(data.users)) {
                        this.setOnlineUsers(data.users);
                    }
                } catch (e) {
                    console.warn('[SSE] Failed to parse online event:', e);
                }
            });

            this.eventSource.onerror = (error) => {
                console.error('[SSE] Error:', error);
                this.isConnected = false;
                this.updateStatus('disconnected');
                
                if (this.eventSource.readyState === EventSource.CLOSED) {
                    this.scheduleReconnect();
                }
            };

        } catch (error) {
            console.error('[SSE] Failed to connect:', error);
            this.updateStatus('disconnected');
            this.scheduleReconnect();
        }
    }

    disconnect() {
        if (this.eventSource) {
            this.eventSource.close();
            this.eventSource = null;
        }
        this.isConnected = false;
    }

    scheduleReconnect() {
        if (this.reconnectAttempts >= CONFIG.chat.maxReconnectAttempts) {
            this.addSystemMessage('连接失败，请刷新页面重试');
            return;
        }

        this.reconnectAttempts++;
        const delay = CONFIG.chat.reconnectDelay;
        
        this.addSystemMessage(`连接断开，${delay / 1000} 秒后尝试重连 (${this.reconnectAttempts}/${CONFIG.chat.maxReconnectAttempts})`);
        
        setTimeout(() => {
            if (!this.isConnected && this.nickname) {
                this.connect();
            }
        }, delay);
    }

    updateStatus(status) {
        this.statusDot.className = 'status-dot';
        
        switch (status) {
            case 'connected':
                this.statusDot.classList.add('connected');
                this.statusText.textContent = '已连接';
                break;
            case 'connecting':
                this.statusText.textContent = '连接中...';
                break;
            case 'disconnected':
                this.statusDot.classList.add('disconnected');
                this.statusText.textContent = '已断开';
                break;
        }
    }

    handleMessage(data, isHistory = false, insertAtTop = false) {
        // 解析消息格式: [HH:mm:ss] 昵称: 内容
        const match = data.match(/^\[(\d{2}:\d{2}:\d{2})\]\s+(.+?):\s+(.+)$/);
        
        if (match) {
            const [, time, sender, text] = match;
            this.addChatMessage(sender, text, time, isHistory, insertAtTop);
        } else {
            // 如果格式不匹配，显示为系统消息
            this.addSystemMessage(data);
        }
    }

    async sendMessage() {
        // 防止重复发送
        if (this.isSending) return;
        
        const message = this.messageInput.value.trim();
        
        if (!message) return;
        
        if (message.length > CONFIG.chat.maxMessageLength) {
            this.showToast('消息内容过长', 'warning');
            return;
        }

        if (!this.isConnected) {
            this.showToast('未连接到服务器', 'error');
            return;
        }

        // 标记正在发送，清空输入框
        this.isSending = true;
        const messageToSend = message;
        this.messageInput.value = '';
        
        const sendUrl = CONFIG.getApiUrl('sendEndpoint');
        
        try {
            const response = await fetch(sendUrl, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    name: this.nickname,
                    message: messageToSend
                })
            });

            if (!response.ok) {
                // 发送失败，恢复输入框内容
                this.messageInput.value = messageToSend;
                this.showToast('发送失败，请重试', 'error');
            }
        } catch (error) {
            console.error('[Send] Error:', error);
            // 发送失败，恢复输入框内容
            this.messageInput.value = messageToSend;
            this.showToast('发送失败，请检查网络连接', 'error');
        } finally {
            this.isSending = false;
        }
    }

    addChatMessage(sender, text, time, isHistory = false, insertAtTop = false) {
        const isSelf = sender === this.nickname;
        const isServer = sender === 'SERVER';
        
        if (isServer) {
            this.addSystemMessage(text);
            return;
        }
        
        // 更新在线用户列表
        this.updateOnlineUsers(sender);

        const messageEl = document.createElement('div');
        messageEl.className = `message ${isSelf ? 'self' : ''}`;
        
        // 检查是否被@
        const isMentioned = text.includes('@' + this.nickname);
        if (isMentioned) {
            messageEl.classList.add('mentioned');
            // 只有实时消息（非历史消息）才播放提醒并发送通知
            if (!isHistory && sender !== this.nickname) {
                this.playMentionSound();
                this.showMentionNotification(sender, text);
            }
        }
        
        // 处理消息内容（支持@高亮、图片、文件）
        let displayText = this.processMessageContent(text);

        messageEl.innerHTML = `
            <div class="message-avatar">${sender.charAt(0).toUpperCase()}</div>
            <div class="message-content">
                <div class="message-header">
                    <span class="message-sender">${this.escapeHtml(sender)}</span>
                    <span class="message-time">${time}</span>
                </div>
                <div class="message-text">${displayText}</div>
            </div>
        `;

        this.appendMessage(messageEl, insertAtTop);
    }
    
    processMessageContent(text) {
        // 检查是否是图片消息
        const imageMatch = text.match(/\[IMAGE:(.+?)\]/);
        if (imageMatch) {
            const imageUrl = imageMatch[1];
            return `<img src="${this.escapeHtml(imageUrl)}" class="chat-image" onclick="window.open('${this.escapeHtml(imageUrl)}', '_blank')" alt="图片">`;
        }
        
        // 检查是否是表情包消息
        const stickerMatch = text.match(/\[STICKER:(.+?)\]/);
        if (stickerMatch) {
            const stickerUrl = stickerMatch[1];
            return `<img src="${this.escapeHtml(stickerUrl)}" class="chat-sticker" alt="表情包">`;
        }
        
        // 检查是否是文件消息
        const fileMatch = text.match(/\[FILE:(.+?)\|(.+?)\]/);
        if (fileMatch) {
            const fileName = fileMatch[1];
            const fileUrl = fileMatch[2];
            return `<a href="${this.escapeHtml(fileUrl)}" class="chat-file" download="${this.escapeHtml(fileName)}" target="_blank">
                <span class="file-icon">📄</span>
                <span class="file-name">${this.escapeHtml(fileName)}</span>
                <span class="file-download">下载</span>
            </a>`;
        }
        
        // 处理@提及高亮
        let escaped = this.escapeHtml(text);
        escaped = escaped.replace(/@(\S+)/g, '<span class="mention-highlight">@$1</span>');
        
        return escaped;
    }

    addSystemMessage(text) {
        const messageEl = document.createElement('div');
        messageEl.className = 'message system';
        
        messageEl.innerHTML = `
            <div class="message-content">
                <div class="message-text">${this.escapeHtml(text)}</div>
            </div>
        `;

        this.appendMessage(messageEl);
    }

    appendMessage(messageEl, insertAtTop = false) {
        // 移除欢迎消息
        const welcomeMsg = this.messagesContainer.querySelector('.welcome-message');
        if (welcomeMsg) {
            welcomeMsg.remove();
        }

        if (insertAtTop) {
            // 插入到顶部（用于加载更多历史消息）
            const firstMessage = this.messagesContainer.querySelector('.message');
            if (firstMessage) {
                this.messagesContainer.insertBefore(messageEl, firstMessage);
            } else {
                this.messagesContainer.appendChild(messageEl);
            }
        } else {
            this.messagesContainer.appendChild(messageEl);
            // 滚动到底部（只有追加到底部时才滚动）
            this.scrollToBottom();
        }
    }

    clearMessages() {
        // 重置历史消息加载状态
        this.historyOffset = 0;
        this.hasMoreHistory = true;
        this.isLoadingHistory = false;
        
        this.messagesContainer.innerHTML = `
            <div class="welcome-message">
                <p>👋 欢迎来到 SYSU Chat 公共聊天室！</p>
                <p>开始和大家聊天吧~</p>
            </div>
        `;
    }

    scrollToBottom() {
        this.messagesContainer.scrollTop = this.messagesContainer.scrollHeight;
    }

    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // ===== 被@提醒功能 =====
    playMentionSound() {
        // 使用简单的提示音
        try {
            // 复用已有的 AudioContext 或创建新的
            if (!this.audioContext) {
                this.audioContext = new (window.AudioContext || window.webkitAudioContext)();
            }
            
            // 如果 AudioContext 被挂起，尝试恢复
            if (this.audioContext.state === 'suspended') {
                this.audioContext.resume();
            }
            
            const oscillator = this.audioContext.createOscillator();
            const gainNode = this.audioContext.createGain();
            
            oscillator.connect(gainNode);
            gainNode.connect(this.audioContext.destination);
            
            oscillator.frequency.value = 880; // A5音
            oscillator.type = 'sine';
            gainNode.gain.setValueAtTime(0.3, this.audioContext.currentTime);
            gainNode.gain.exponentialRampToValueAtTime(0.01, this.audioContext.currentTime + 0.3);
            
            oscillator.start(this.audioContext.currentTime);
            oscillator.stop(this.audioContext.currentTime + 0.3);
        } catch (e) {
            // 静默失败，不打印错误
        }
    }
    
    showMentionNotification(sender, text) {
        // 页面标题闪烁提示
        this.flashTitle(`${sender} @了你！`);
        
        // 显示页面内通知
        this.showMentionToast(sender, text);
        
        // 尝试发送浏览器通知
        if ('Notification' in window) {
            if (Notification.permission === 'granted') {
                this.sendBrowserNotification(sender, text);
            } else if (Notification.permission !== 'denied') {
                Notification.requestPermission().then(permission => {
                    if (permission === 'granted') {
                        this.sendBrowserNotification(sender, text);
                    }
                });
            }
        }
    }
    
    sendBrowserNotification(sender, text) {
        try {
            const notification = new Notification(`${sender} @了你`, {
                body: text.length > 50 ? text.substring(0, 50) + '...' : text,
                icon: '/favicon.svg',
                tag: 'chat-mention',
                requireInteraction: false
            });
            
            notification.onclick = () => {
                window.focus();
                notification.close();
            };
            
            setTimeout(() => notification.close(), 5000);
        } catch (e) {
            console.log('无法发送浏览器通知:', e);
        }
    }
    
    flashTitle(message) {
        const originalTitle = document.title;
        let isOriginal = true;
        let flashCount = 0;
        
        const flashInterval = setInterval(() => {
            document.title = isOriginal ? message : originalTitle;
            isOriginal = !isOriginal;
            flashCount++;
            
            if (flashCount >= 10 || document.hasFocus()) {
                clearInterval(flashInterval);
                document.title = originalTitle;
            }
        }, 500);
    }
    
    showMentionToast(sender, text) {
        const toast = document.createElement('div');
        toast.className = 'mention-toast';
        toast.innerHTML = `
            <div class="mention-toast-header">
                <span class="mention-toast-icon">🔔</span>
                <span class="mention-toast-title">${this.escapeHtml(sender)} @了你</span>
            </div>
            <div class="mention-toast-body">${this.escapeHtml(text.length > 80 ? text.substring(0, 80) + '...' : text)}</div>
        `;
        
        document.body.appendChild(toast);
        
        // 触发动画
        requestAnimationFrame(() => {
            toast.classList.add('show');
        });
        
        // 自动消失
        setTimeout(() => {
            toast.classList.remove('show');
            setTimeout(() => toast.remove(), 300);
        }, 4000);
    }

    showToast(message, type = 'info') {
        // 简单的 Toast 实现
        const toast = document.createElement('div');
        toast.style.cssText = `
            position: fixed;
            bottom: 100px;
            left: 50%;
            transform: translateX(-50%);
            padding: 12px 24px;
            background: ${type === 'error' ? '#dc3545' : type === 'warning' ? '#ffc107' : '#17a2b8'};
            color: ${type === 'warning' ? '#212529' : 'white'};
            border-radius: 8px;
            font-size: 14px;
            z-index: 9999;
            animation: fadeInOut 3s ease;
        `;
        toast.textContent = message;

        // 添加动画样式
        if (!document.getElementById('toastStyle')) {
            const style = document.createElement('style');
            style.id = 'toastStyle';
            style.textContent = `
                @keyframes fadeInOut {
                    0% { opacity: 0; transform: translateX(-50%) translateY(20px); }
                    15% { opacity: 1; transform: translateX(-50%) translateY(0); }
                    85% { opacity: 1; transform: translateX(-50%) translateY(0); }
                    100% { opacity: 0; transform: translateX(-50%) translateY(-20px); }
                }
            `;
            document.head.appendChild(style);
        }

        document.body.appendChild(toast);
        
        setTimeout(() => {
            toast.remove();
        }, 3000);
    }

    showAboutModal() {
        this.aboutModal.classList.remove('hidden');
    }

    hideAboutModal() {
        this.aboutModal.classList.add('hidden');
    }
    
    // ===== 表情功能 =====
    initEmojiList() {
        const emojis = [
            // 笑脸
            '😀', '😃', '😄', '😁', '😆', '😅', '🤣', '😂', '🙂', '😊',
            '😇', '🥰', '😍', '🤩', '😘', '😗', '😚', '😙', '🥲', '😋',
            '😛', '😜', '🤪', '😝', '🤑', '🤗', '🤭', '🤫', '🤔', '🤐',
            // 手势
            '👍', '👎', '👏', '🙌', '🤝', '👊', '✊', '🤛', '🤜', '🤞',
            '✌️', '🤟', '🤘', '👌', '🤌', '👈', '👉', '👆', '👇', '☝️',
            // 其他表情
            '😎', '🤓', '🧐', '😏', '😒', '😞', '😔', '😟', '😕', '🙁',
            '😣', '😖', '😫', '😩', '🥺', '😢', '😭', '😤', '😠', '😡',
            '🤬', '🤯', '😳', '🥵', '🥶', '😱', '😨', '😰', '😥', '😓',
            // 动物
            '🐶', '🐱', '🐭', '🐹', '🐰', '🦊', '🐻', '🐼', '🐨', '🐯',
            // 物品
            '❤️', '🧡', '💛', '💚', '💙', '💜', '🖤', '🤍', '💔', '💕',
            '💯', '💢', '💥', '💫', '💦', '💨', '🎉', '🎊', '🎁', '🔥',
            // 食物
            '🍎', '🍊', '🍋', '🍌', '🍉', '🍇', '🍓', '🍒', '🍑', '🥝'
        ];
        
        if (this.emojiList) {
            this.emojiList.innerHTML = emojis.map(emoji => 
                `<span class="emoji-item" data-emoji="${emoji}">${emoji}</span>`
            ).join('');
            
            // 绑定点击事件
            this.emojiList.querySelectorAll('.emoji-item').forEach(item => {
                item.addEventListener('click', () => {
                    this.insertEmoji(item.dataset.emoji);
                });
            });
        }
    }
    
    toggleEmojiPopup() {
        if (!this.emojiPopup) return;
        if (this.emojiPopup.classList.contains('hidden')) {
            this.showEmojiPopup();
        } else {
            this.hideEmojiPopup();
        }
    }
    
    showEmojiPopup() {
        if (this.emojiPopup) {
            this.emojiPopup.classList.remove('hidden');
        }
    }
    
    hideEmojiPopup() {
        if (this.emojiPopup) {
            this.emojiPopup.classList.add('hidden');
        }
    }
    
    insertEmoji(emoji) {
        const input = this.messageInput;
        const start = input.selectionStart;
        const end = input.selectionEnd;
        const text = input.value;
        
        input.value = text.substring(0, start) + emoji + text.substring(end);
        
        // 移动光标到表情后面
        const newPos = start + emoji.length;
        input.setSelectionRange(newPos, newPos);
        input.focus();
    }
    
    // 切换表情/表情包标签
    switchEmojiTab(tabName) {
        this.emojiTabs.forEach(tab => {
            tab.classList.toggle('active', tab.dataset.tab === tabName);
        });
        
        this.emojiPanels.forEach(panel => {
            panel.classList.toggle('active', panel.id === tabName + 'Panel');
        });
    }
    
    // ===== 表情包功能 =====
    loadStickers() {
        // 从 localStorage 加载用户的表情包
        const stickers = this.getStoredStickers();
        this.renderStickers(stickers);
        
        // 绑定事件委托（只绑定一次）
        this.bindStickerEvents();
    }
    
    bindStickerEvents() {
        if (this.stickerEventBound || !this.stickerList) return;
        this.stickerEventBound = true;
        
        // 使用事件委托处理点击
        this.stickerList.addEventListener('click', (e) => {
            e.stopPropagation(); // 阻止事件冒泡到document
            const target = e.target;
            
            // 如果点击的是删除按钮
            if (target.classList.contains('sticker-delete')) {
                e.preventDefault();
                const index = parseInt(target.dataset.index);
                if (!isNaN(index)) {
                    this.deleteSticker(index);
                }
                return;
            }
            
            // 如果点击的是空状态提示，不做任何事
            if (target.classList.contains('sticker-empty')) {
                return;
            }
            
            // 找到最近的 sticker-item（可能点击的是img或容器）
            const item = target.closest('.sticker-item');
            if (item) {
                const url = item.dataset.url;
                if (url) {
                    this.sendSticker(url);
                }
            }
        });
    }
    
    getStoredStickers() {
        try {
            // 使用用户名作为 key，每个用户有自己的表情包库
            const key = `sysu_chat_stickers_${this.nickname || 'guest'}`;
            const stored = localStorage.getItem(key);
            return stored ? JSON.parse(stored) : [];
        } catch (e) {
            return [];
        }
    }
    
    saveStickers(stickers) {
        const key = `sysu_chat_stickers_${this.nickname || 'guest'}`;
        localStorage.setItem(key, JSON.stringify(stickers));
    }
    
    renderStickers(stickers) {
        if (!this.stickerList) return;
        
        if (stickers.length === 0) {
            this.stickerList.innerHTML = '<div class="sticker-empty">暂无自定义表情包<br>点击上方按钮上传</div>';
            return;
        }
        
        this.stickerList.innerHTML = stickers.map((sticker, index) => `
            <div class="sticker-item" data-index="${index}" data-url="${sticker.url}">
                <img src="${sticker.url}" alt="表情包" draggable="false">
                <button class="sticker-delete" data-index="${index}" type="button">&times;</button>
            </div>
        `).join('');
        // 事件委托已在 loadStickers 中绑定
    }
    
    async uploadStickers(files) {
        const stickers = this.getStoredStickers();
        let successCount = 0;
        
        for (const file of files) {
            if (!file.type.startsWith('image/')) {
                this.showToast('只能上传图片文件', 'warning');
                continue;
            }
            
            if (file.size > 500 * 1024) {
                this.showToast('表情包图片不能超过500KB', 'warning');
                continue;
            }
            
            try {
                // 将图片转为 base64 存储在本地，不上传到服务器
                const base64 = await this.fileToBase64(file);
                stickers.push({
                    url: base64,
                    name: file.name
                });
                successCount++;
            } catch (error) {
                console.error('[Sticker] Convert error:', error);
                this.showToast('添加失败', 'error');
            }
        }
        
        if (successCount > 0) {
            this.saveStickers(stickers);
            this.renderStickers(stickers);
            this.showToast(`成功添加 ${successCount} 个表情包`, 'success');
        }
        
        if (this.stickerInput) {
            this.stickerInput.value = '';
        }
    }
    
    // 将文件转为 base64
    fileToBase64(file) {
        return new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.onload = () => resolve(reader.result);
            reader.onerror = reject;
            reader.readAsDataURL(file);
        });
    }
    
    deleteSticker(index) {
        const stickers = this.getStoredStickers();
        stickers.splice(index, 1);
        this.saveStickers(stickers);
        this.renderStickers(stickers);
    }
    
    async sendSticker(url) {
        if (!this.isConnected) {
            this.showToast('未连接到服务器', 'error');
            return;
        }
        
        this.hideEmojiPopup();
        
        const sendUrl = CONFIG.getApiUrl('sendEndpoint');
        
        try {
            const response = await fetch(sendUrl, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    name: this.nickname,
                    message: '[STICKER:' + url + ']'
                })
            });

            if (!response.ok) {
                this.showToast('发送失败，请重试', 'error');
            }
        } catch (error) {
            console.error('[Send] Error:', error);
            this.showToast('发送失败，请检查网络', 'error');
        }
    }
    
    // ===== 文件上传功能 =====
    async uploadFile(file, type) {
        if (!this.isConnected) {
            this.showToast('未连接到服务器', 'error');
            return;
        }
        
        const maxSize = 100 * 1024 * 1024; // 100MB
        if (file.size > maxSize) {
            this.showToast('文件大小不能超过100MB', 'warning');
            return;
        }
        
        // 显示上传进度
        this.uploadProgress.classList.remove('hidden');
        this.progressFill.style.width = '0%';
        this.progressText.textContent = '准备上传...';
        
        try {
            const formData = new FormData();
            formData.append('file', file);
            formData.append('name', this.nickname);
            formData.append('type', type);
            
            const uploadUrl = CONFIG.getApiUrl('uploadEndpoint');
            
            const xhr = new XMLHttpRequest();
            
            xhr.upload.addEventListener('progress', (e) => {
                if (e.lengthComputable) {
                    const percent = Math.round((e.loaded / e.total) * 100);
                    this.progressFill.style.width = percent + '%';
                    this.progressText.textContent = `上传中 ${percent}%`;
                }
            });
            
            xhr.addEventListener('load', () => {
                this.uploadProgress.classList.add('hidden');
                if (xhr.status === 200) {
                    this.showToast(type === 'image' ? '图片发送成功' : '文件发送成功', 'success');
                } else {
                    this.showToast('上传失败', 'error');
                }
            });
            
            xhr.addEventListener('error', () => {
                this.uploadProgress.classList.add('hidden');
                this.showToast('上传失败，请检查网络', 'error');
            });
            
            xhr.open('POST', uploadUrl);
            xhr.send(formData);
            
        } catch (error) {
            console.error('[Upload] Error:', error);
            this.uploadProgress.classList.add('hidden');
            this.showToast('上传失败', 'error');
        }
        
        // 清空文件输入
        this.imageInput.value = '';
        this.fileInput.value = '';
    }
    
    // ===== @提及功能 =====
    handleMentionInput(e) {
        const value = this.messageInput.value;
        const cursorPos = this.messageInput.selectionStart;
        
        // 查找光标前最近的@
        const beforeCursor = value.substring(0, cursorPos);
        const atIndex = beforeCursor.lastIndexOf('@');
        
        if (atIndex >= 0) {
            const afterAt = beforeCursor.substring(atIndex + 1);
            // 如果@后没有空格，说明正在输入用户名
            if (!afterAt.includes(' ')) {
                const searchText = afterAt.toLowerCase();
                this.showMentionPopup(searchText);
                return;
            }
        }
        
        this.hideMentionPopup();
    }
    
    handleMentionKeydown(e) {
        if (!this.mentionPopup.classList.contains('hidden')) {
            const items = this.mentionList.querySelectorAll('.mention-item');
            
            if (e.key === 'ArrowDown') {
                e.preventDefault();
                this.mentionIndex = Math.min(this.mentionIndex + 1, items.length - 1);
                this.updateMentionSelection(items);
            } else if (e.key === 'ArrowUp') {
                e.preventDefault();
                this.mentionIndex = Math.max(this.mentionIndex - 1, 0);
                this.updateMentionSelection(items);
            } else if (e.key === 'Enter' || e.key === 'Tab') {
                if (this.mentionIndex >= 0 && items[this.mentionIndex]) {
                    e.preventDefault();
                    this.selectMention(items[this.mentionIndex].dataset.username);
                }
            } else if (e.key === 'Escape') {
                this.hideMentionPopup();
            }
        }
    }
    
    updateMentionSelection(items) {
        items.forEach((item, index) => {
            item.classList.toggle('selected', index === this.mentionIndex);
        });
    }
    
    showMentionPopup(searchText) {
        // 过滤在线用户
        const filtered = this.onlineUsers.filter(user => 
            user.toLowerCase().includes(searchText) && user !== this.nickname
        );
        
        if (filtered.length === 0) {
            this.hideMentionPopup();
            return;
        }
        
        this.mentionList.innerHTML = filtered.slice(0, 10).map(user => 
            `<div class="mention-item" data-username="${this.escapeHtml(user)}">
                <span class="mention-avatar">${user.charAt(0).toUpperCase()}</span>
                <span class="mention-name">${this.escapeHtml(user)}</span>
            </div>`
        ).join('');
        
        this.mentionIndex = 0;
        this.updateMentionSelection(this.mentionList.querySelectorAll('.mention-item'));
        
        // 绑定点击事件
        this.mentionList.querySelectorAll('.mention-item').forEach(item => {
            item.addEventListener('click', () => {
                this.selectMention(item.dataset.username);
            });
        });
        
        this.mentionPopup.classList.remove('hidden');
    }
    
    hideMentionPopup() {
        this.mentionPopup.classList.add('hidden');
        this.mentionIndex = -1;
    }
    
    selectMention(username) {
        const value = this.messageInput.value;
        const cursorPos = this.messageInput.selectionStart;
        const beforeCursor = value.substring(0, cursorPos);
        const afterCursor = value.substring(cursorPos);
        
        const atIndex = beforeCursor.lastIndexOf('@');
        if (atIndex >= 0) {
            const newValue = beforeCursor.substring(0, atIndex) + '@' + username + ' ' + afterCursor;
            this.messageInput.value = newValue;
            const newCursorPos = atIndex + username.length + 2;
            this.messageInput.setSelectionRange(newCursorPos, newCursorPos);
        }
        
        this.hideMentionPopup();
        this.messageInput.focus();
    }
    
    // 设置在线用户列表（从服务器获取的真实列表）
    setOnlineUsers(users) {
        if (Array.isArray(users)) {
            this.onlineUsers = users;
            this.renderUserList();
            console.log('[Online] Users updated:', users);
        }
    }
    
    // 更新在线用户列表（从消息中提取 - 备用方案）
    updateOnlineUsers(sender) {
        // 如果服务器已经推送了用户列表，则不需要从消息中提取
        // 只有在 onlineUsers 为空时才使用此方法
        if (sender && !this.onlineUsers.includes(sender) && sender !== 'SERVER') {
            this.onlineUsers.push(sender);
            // 保持列表不超过100人
            if (this.onlineUsers.length > 100) {
                this.onlineUsers.shift();
            }
            // 更新侧边栏用户列表
            this.renderUserList();
        }
    }
    
    // 更新在线人数（来自服务器的实时推送）
    updateOnlineCount(count) {
        this.realOnlineCount = count;
        if (this.onlineCount) {
            this.onlineCount.textContent = `在线: ${count}`;
        }
        console.log('[SSE] Online count updated:', count);
    }
    
    // 渲染侧边栏用户列表
    renderUserList() {
        if (!this.userList) return;
        
        // 注意：在线人数由 updateOnlineCount 统一管理（来自服务器实时推送）
        // 这里只渲染用户列表（用于@功能）
        
        // 渲染用户列表
        this.userList.innerHTML = this.onlineUsers.map(user => `
            <div class="user-item" data-user="${this.escapeHtml(user)}">
                <div class="user-avatar">${user.charAt(0).toUpperCase()}</div>
                <div class="user-info">
                    <span class="user-name">${this.escapeHtml(user)}</span>
                    <span class="user-status">在线</span>
                </div>
            </div>
        `).join('');
        
        // 点击用户名可以@他
        this.userList.querySelectorAll('.user-item').forEach(item => {
            item.addEventListener('click', () => {
                const userName = item.dataset.user;
                if (this.messageInput) {
                    this.messageInput.value += `@${userName} `;
                    this.messageInput.focus();
                }
            });
        });
    }
}

// 启动应用
document.addEventListener('DOMContentLoaded', () => {
    window.chatApp = new ChatApp();
});
