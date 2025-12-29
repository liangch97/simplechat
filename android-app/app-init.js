/**
 * SYSU Chat App - 移动端适配初始化脚本
 * 这个文件会在 Capacitor App 中自动加载
 */

(function() {
    'use strict';

    // 检测是否在 Capacitor App 中运行
    window.isCapacitorApp = typeof window.Capacitor !== 'undefined';

    // 等待 Capacitor 准备就绪
    document.addEventListener('DOMContentLoaded', async function() {
        if (!window.isCapacitorApp) {
            console.log('[App] 运行在浏览器环境');
            return;
        }

        console.log('[App] 运行在 Capacitor App 环境');

        try {
            // 导入 Capacitor 插件
            const { App } = await import('@capacitor/app');
            const { StatusBar, Style } = await import('@capacitor/status-bar');
            const { SplashScreen } = await import('@capacitor/splash-screen');
            const { Keyboard } = await import('@capacitor/keyboard');
            const { Network } = await import('@capacitor/network');

            // 设置状态栏
            try {
                await StatusBar.setBackgroundColor({ color: '#006633' });
                await StatusBar.setStyle({ style: Style.Dark });
            } catch (e) {
                console.log('[App] 状态栏设置失败:', e);
            }

            // 隐藏启动画面
            await SplashScreen.hide();

            // 监听返回按钮
            App.addListener('backButton', ({ canGoBack }) => {
                if (canGoBack) {
                    window.history.back();
                } else {
                    // 显示退出确认
                    if (confirm('确定要退出应用吗？')) {
                        App.exitApp();
                    }
                }
            });

            // 监听键盘事件
            Keyboard.addListener('keyboardWillShow', (info) => {
                document.body.classList.add('keyboard-open');
                document.body.style.setProperty('--keyboard-height', info.keyboardHeight + 'px');
            });

            Keyboard.addListener('keyboardWillHide', () => {
                document.body.classList.remove('keyboard-open');
                document.body.style.setProperty('--keyboard-height', '0px');
            });

            // 监听网络状态
            Network.addListener('networkStatusChange', (status) => {
                console.log('[App] 网络状态变化:', status);
                if (!status.connected) {
                    showToast('网络连接已断开', 'warning');
                } else {
                    showToast('网络已恢复', 'success');
                }
            });

            // 监听应用状态
            App.addListener('appStateChange', ({ isActive }) => {
                console.log('[App] 应用状态:', isActive ? '前台' : '后台');
                if (isActive) {
                    // 应用回到前台，可能需要重新连接
                    if (window.reconnectChat) {
                        window.reconnectChat();
                    }
                }
            });

            console.log('[App] Capacitor 插件初始化完成');

        } catch (error) {
            console.error('[App] Capacitor 初始化错误:', error);
        }
    });

    // Toast 提示函数
    function showToast(message, type = 'info') {
        const toast = document.createElement('div');
        toast.className = 'app-toast app-toast-' + type;
        toast.textContent = message;
        toast.style.cssText = `
            position: fixed;
            bottom: 100px;
            left: 50%;
            transform: translateX(-50%);
            background: ${type === 'warning' ? '#ff9800' : type === 'success' ? '#4caf50' : '#2196f3'};
            color: white;
            padding: 12px 24px;
            border-radius: 8px;
            font-size: 14px;
            z-index: 10000;
            box-shadow: 0 4px 12px rgba(0,0,0,0.3);
            animation: toastIn 0.3s ease;
        `;
        document.body.appendChild(toast);
        
        setTimeout(() => {
            toast.style.animation = 'toastOut 0.3s ease';
            setTimeout(() => toast.remove(), 300);
        }, 3000);
    }

    // 添加 Toast 动画样式
    const style = document.createElement('style');
    style.textContent = `
        @keyframes toastIn {
            from { opacity: 0; transform: translateX(-50%) translateY(20px); }
            to { opacity: 1; transform: translateX(-50%) translateY(0); }
        }
        @keyframes toastOut {
            from { opacity: 1; transform: translateX(-50%) translateY(0); }
            to { opacity: 0; transform: translateX(-50%) translateY(20px); }
        }
        .keyboard-open {
            --keyboard-height: 0px;
        }
        .keyboard-open .chat-input-area {
            padding-bottom: calc(var(--keyboard-height) + 10px);
        }
    `;
    document.head.appendChild(style);

    // 暴露到全局
    window.showAppToast = showToast;

})();
