/**
 * SYSU Chat - Service Worker
 * 提供离线缓存和后台通知支持
 */

const CACHE_NAME = 'sysu-chat-v1';
const STATIC_ASSETS = [
    '/',
    '/index.html',
    '/css/style.css',
    '/js/config.js',
    '/js/chat.js',
    '/js/lucide.min.js',
    '/manifest.json',
    '/favicon.svg',
    '/images/logo.svg'
];

// 安装 Service Worker - 缓存静态资源
self.addEventListener('install', (event) => {
    console.log('[SW] Installing...');
    event.waitUntil(
        caches.open(CACHE_NAME)
            .then((cache) => {
                console.log('[SW] Caching static assets');
                return cache.addAll(STATIC_ASSETS);
            })
            .then(() => {
                console.log('[SW] Install complete');
                return self.skipWaiting(); // 立即激活新版本
            })
            .catch((err) => {
                console.warn('[SW] Cache failed:', err);
            })
    );
});

// 激活 Service Worker - 清理旧缓存
self.addEventListener('activate', (event) => {
    console.log('[SW] Activating...');
    event.waitUntil(
        caches.keys()
            .then((cacheNames) => {
                return Promise.all(
                    cacheNames
                        .filter((name) => name !== CACHE_NAME)
                        .map((name) => {
                            console.log('[SW] Deleting old cache:', name);
                            return caches.delete(name);
                        })
                );
            })
            .then(() => {
                console.log('[SW] Activate complete');
                return self.clients.claim(); // 立即接管所有页面
            })
    );
});

// 拦截网络请求 - 优先使用网络，失败时使用缓存
self.addEventListener('fetch', (event) => {
    const url = new URL(event.request.url);
    
    // 跳过 API 请求和 SSE 连接（包括跨域请求）
    if (url.pathname.startsWith('/api/') || 
        url.pathname.startsWith('/events') || 
        url.pathname.startsWith('/send') ||
        url.href.includes('/api/') ||
        url.href.includes('/events') ||
        url.href.includes('/send') ||
        event.request.method !== 'GET') {
        return; // 不缓存 API 请求和非 GET 请求
    }
    
    // 只缓存同源的静态资源
    if (url.origin !== self.location.origin) {
        return; // 跳过跨域请求
    }
    
    // 静态资源：网络优先，失败时使用缓存
    event.respondWith(
        fetch(event.request)
            .then((response) => {
                // 请求成功，更新缓存
                if (response.ok) {
                    const responseClone = response.clone();
                    caches.open(CACHE_NAME).then((cache) => {
                        cache.put(event.request, responseClone);
                    });
                }
                return response;
            })
            .catch(() => {
                // 网络失败，尝试从缓存获取
                return caches.match(event.request).then((cachedResponse) => {
                    if (cachedResponse) {
                        return cachedResponse;
                    }
                    // 如果是 HTML 请求，返回首页
                    if (event.request.headers.get('accept').includes('text/html')) {
                        return caches.match('/index.html');
                    }
                    return new Response('Offline', { status: 503 });
                });
            })
    );
});

// 接收来自页面的消息
self.addEventListener('message', (event) => {
    if (event.data && event.data.type === 'SKIP_WAITING') {
        self.skipWaiting();
    }
});
