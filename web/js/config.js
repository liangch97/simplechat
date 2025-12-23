/**
 * SYSU Chat - 配置文件
 * 域名: sysu.asia
 */

const CONFIG = {
    // 网站信息
    site: {
        name: 'SYSU Chat',
        domain: 'sysu.asia',
        title: 'SYSU Chat - 中山大学在线聊天室',
        description: '中山大学学生在线聊天交流平台'
    },

    // API 配置
    api: {
        // 开发环境
        development: {
            baseUrl: 'http://localhost:7070',
            eventsEndpoint: '/events',
            sendEndpoint: '/send'
        },
        // 生产环境 - 当域名启用后使用
        production: {
            baseUrl: 'https://sysu.asia:7070',
            eventsEndpoint: '/events',
            sendEndpoint: '/send'
        }
    },

    // 当前环境
    env: 'development', // 'development' 或 'production'

    // 聊天配置
    chat: {
        maxMessageLength: 500,
        maxNicknameLength: 20,
        minNicknameLength: 2,
        reconnectDelay: 3000,       // 重连延迟 (毫秒)
        maxReconnectAttempts: 5,     // 最大重连次数
        messageHistoryLimit: 100     // 本地保存的消息历史数量
    },

    // 获取当前环境的 API 配置
    getApiConfig() {
        return this.api[this.env];
    },

    // 获取完整的 API URL
    getApiUrl(endpoint) {
        const config = this.getApiConfig();
        return config.baseUrl + config[endpoint];
    }
};

// 根据当前域名自动切换环境
if (typeof window !== 'undefined') {
    if (window.location.hostname === 'sysu.asia' || 
        window.location.hostname === 'www.sysu.asia') {
        CONFIG.env = 'production';
    }
}

// 防止修改配置
Object.freeze(CONFIG.site);
Object.freeze(CONFIG.api.development);
Object.freeze(CONFIG.api.production);
Object.freeze(CONFIG.chat);
