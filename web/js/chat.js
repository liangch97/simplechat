/**
 * SYSU Chat - 聊天核心逻辑
 * 域名: sysu.asia
 */

class ChatApp {
    constructor() {
        this.nickname = '';
        this.eventSource = null;
        this.reconnectAttempts = 0;
        this.isConnected = false;
        
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
        this.chatPanel = document.getElementById('chatPanel');
        this.loginForm = document.getElementById('loginForm');
        this.nicknameInput = document.getElementById('nickname');
        
        // 用户信息
        this.userInfo = document.getElementById('userInfo');
        this.userAvatar = document.getElementById('userAvatar');
        this.userName = document.getElementById('userName');
        
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
    }

    bindEvents() {
        // 登录表单
        this.loginForm.addEventListener('submit', (e) => {
            e.preventDefault();
            this.login();
        });

        // 发送消息
        this.messageForm.addEventListener('submit', (e) => {
            e.preventDefault();
            this.sendMessage();
        });

        // 退出登录
        this.logoutBtn.addEventListener('click', () => {
            this.logout();
        });

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
            }
        });

        // 页面关闭前断开连接
        window.addEventListener('beforeunload', () => {
            this.disconnect();
        });
    }

    restoreSession() {
        const savedNickname = localStorage.getItem('sysu_chat_nickname');
        if (savedNickname) {
            this.nickname = savedNickname;
            this.enterChatRoom();
        }
    }

    login() {
        const nickname = this.nicknameInput.value.trim();
        
        if (nickname.length < CONFIG.chat.minNicknameLength) {
            this.showToast('昵称至少需要 ' + CONFIG.chat.minNicknameLength + ' 个字符', 'warning');
            return;
        }
        
        if (nickname.length > CONFIG.chat.maxNicknameLength) {
            this.showToast('昵称不能超过 ' + CONFIG.chat.maxNicknameLength + ' 个字符', 'warning');
            return;
        }

        this.nickname = nickname;
        localStorage.setItem('sysu_chat_nickname', nickname);
        this.enterChatRoom();
    }

    logout() {
        this.disconnect();
        localStorage.removeItem('sysu_chat_nickname');
        this.nickname = '';
        this.exitChatRoom();
    }

    enterChatRoom() {
        // 更新 UI
        this.loginPanel.classList.add('hidden');
        this.chatPanel.classList.remove('hidden');
        
        // 更新用户信息
        this.userAvatar.textContent = this.nickname.charAt(0).toUpperCase();
        this.userName.textContent = this.nickname;
        
        // 清空之前的消息
        this.clearMessages();
        
        // 连接 SSE
        this.connect();
        
        // 聚焦输入框
        this.messageInput.focus();
    }

    exitChatRoom() {
        this.chatPanel.classList.add('hidden');
        this.loginPanel.classList.remove('hidden');
        this.nicknameInput.value = '';
        this.nicknameInput.focus();
        
        // 重置用户信息
        this.userAvatar.textContent = '?';
        this.userName.textContent = '未登录';
    }

    connect() {
        if (this.eventSource) {
            this.eventSource.close();
        }

        this.updateStatus('connecting');

        const eventsUrl = CONFIG.getApiUrl('eventsEndpoint');
        
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

    handleMessage(data) {
        // 解析消息格式: [HH:mm:ss] 昵称: 内容
        const match = data.match(/^\[(\d{2}:\d{2}:\d{2})\]\s+(.+?):\s+(.+)$/);
        
        if (match) {
            const [, time, sender, text] = match;
            this.addChatMessage(sender, text, time);
        } else {
            // 如果格式不匹配，显示为系统消息
            this.addSystemMessage(data);
        }
    }

    async sendMessage() {
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

        const sendUrl = CONFIG.getApiUrl('sendEndpoint');
        
        try {
            const response = await fetch(sendUrl, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    name: this.nickname,
                    message: message
                })
            });

            if (response.ok) {
                this.messageInput.value = '';
            } else {
                this.showToast('发送失败，请重试', 'error');
            }
        } catch (error) {
            console.error('[Send] Error:', error);
            this.showToast('发送失败，请检查网络连接', 'error');
        }
    }

    addChatMessage(sender, text, time) {
        const isSelf = sender === this.nickname;
        const isServer = sender === 'SERVER';
        
        if (isServer) {
            this.addSystemMessage(text);
            return;
        }

        const messageEl = document.createElement('div');
        messageEl.className = `message ${isSelf ? 'self' : ''}`;
        
        messageEl.innerHTML = `
            <div class="message-avatar">${sender.charAt(0).toUpperCase()}</div>
            <div class="message-content">
                <div class="message-header">
                    <span class="message-sender">${this.escapeHtml(sender)}</span>
                    <span class="message-time">${time}</span>
                </div>
                <div class="message-text">${this.escapeHtml(text)}</div>
            </div>
        `;

        this.appendMessage(messageEl);
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

    appendMessage(messageEl) {
        // 移除欢迎消息
        const welcomeMsg = this.messagesContainer.querySelector('.welcome-message');
        if (welcomeMsg) {
            welcomeMsg.remove();
        }

        this.messagesContainer.appendChild(messageEl);
        
        // 限制消息数量
        const messages = this.messagesContainer.querySelectorAll('.message');
        if (messages.length > CONFIG.chat.messageHistoryLimit) {
            messages[0].remove();
        }

        // 滚动到底部
        this.scrollToBottom();
    }

    clearMessages() {
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
}

// 启动应用
document.addEventListener('DOMContentLoaded', () => {
    window.chatApp = new ChatApp();
});
