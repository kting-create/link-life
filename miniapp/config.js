// 上线前必须将 BASE_URL 改为生产 https 域名（见 docs/TODO.md 1.3）
// 本地开发走 deploy compose 的 nginx（80 端口反代 /api 到 app:8080）
const BASE_URL = 'http://localhost';

module.exports = { BASE_URL };
