# GUGA Reading - 在线阅读平台

一个基于 FastAPI + Vue 3 的多角色在线阅读平台,支持用户、作者和管理员三种角色的完整功能。

## 📖 项目概述

GUGA Reading
是一个现代化的在线阅读系统,提供小说/书籍的在线阅读、创作和管理功能。系统采用前后端分离架构,支持跨平台访问。  
[用户端预览](http://1.95.141.194)  
[作者端预览](http://1.95.141.194/author/#)

### ✨ 核心特性

- **多角色支持**:用户、作者、管理员三种角色,权限分离
- **个性化推荐**:基于 TF-IDF 算法的个性化书籍推荐
- **实时阅读进度**:支持断点续读,多设备同步
- **丰富的互动功能**:收藏、评论、点赞等社交功能
- **完善的内容管理**:从创作到发布的全流程管理
- **高并发设计**:支持单机上百用户同时访问,首屏加载 < 1s

## 🏗️ 系统架构

```
┌─────────────────────────────────────────────────────────┐
│                     GUGA Reading                        │
├─────────────────────────────────────────────────────────┤
│  Frontend (Vue 3 + TypeScript)                          │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐    │
│  │   User      │  │   Author    │  │   Admin     │    │
│  │   Client    │  │   Client    │  │   Panel     │    │
│  └─────────────┘  └─────────────┘  └─────────────┘    │
├─────────────────────────────────────────────────────────┤
│  Backend (FastAPI + SQLAlchemy)                         │
│  ┌─────────────────────────────────────────────────┐   │
│  │  API Gateway / Authentication / Rate Limiting   │   │
│  └─────────────────────────────────────────────────┘   │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────────┐  │
│  │  Book   │ │  User   │ │Recommend│ │  Statistics │  │
│  │ Service │ │ Service │ │ Service │ │   Service   │  │
│  └─────────┘ └─────────┘ └─────────┘ └─────────────┘  │
├─────────────────────────────────────────────────────────┤
│  Data Layer                                             │
│  ┌─────────────┐  ┌─────────────┐  ┌───────────────┐  │
│  │   MySQL     │  │   Redis     │  │  File Storage │  │
│  │  (Database) │  │   (Cache)   │  │  (OBS/Local)  │  │
│  └─────────────┘  └─────────────┘  └───────────────┘  │
└─────────────────────────────────────────────────────────┘
```

## 📁 项目结构

```
guga_reading/
├── backend/                          # 后端服务 (FastAPI)
│   ├── app/
│   │   ├── api/v1/                   # API v1 接口定义
│   │   ├── core/                     # 核心功能 (配置、数据库、安全)
│   │   ├── models/                   # SQLAlchemy 数据模型
│   │   ├── schema/                   # Pydantic 数据验证模式
│   │   ├── services/                 # 业务逻辑服务层
│   │   ├── algorithm/                # 推荐算法 (TF-IDF)
│   │   ├── middleware/               # 中间件 (日志、限流)
│   │   ├── utils/                    # 工具函数
│   │   └── enum/                     # 枚举定义
│   ├── alembic/                      # 数据库迁移工具
│   ├── static/                       # 静态资源 (书籍文件)
│   ├── store/                        # 数据存储目录
│   ├── logs/                         # 日志文件
│   ├── test/                         # 测试代码 (pytest)
│   ├── scripts/                      # 脚本工具
│   ├── requirements.txt              # Python 依赖
│   ├── pyproject.toml                # Python 项目配置
│   ├── docker-compose.yml            # Docker 编排
│   └── run.py                        # 启动脚本
│
├── author/                           # 作者端应用 (Vue 3)
│   ├── src/
│   │   ├── components/               # Vue 组件
│   │   ├── views/                    # 页面视图
│   │   ├── store/                    # Pinia 状态管理
│   │   ├── config/                   # 配置文件
│   │   └── route.ts                  # 路由配置
│   ├── public/                       # 公共资源
│   ├── package.json
│   └── vite.config.ts
│
├── user/                             # 用户端应用 (Vue 3)
│   ├── src/
│   │   ├── components/               # Vue 组件
│   │   ├── views/                    # 页面视图
│   │   ├── store/                    # Pinia 状态管理
│   │   └── config/                   # 配置文件
│   ├── public/                       # 公共资源
│   └── package.json
│
├── admin/                            # 管理后台 (Vue 3)
│   ├── src/
│   │   └── views/                    # 页面视图
│   ├── public/                       # 公共资源
│   └── package.json
│
├── packages/                         # 共享包 (Monorepo)
│   ├── eslint/                       # ESLint 配置
│   ├── shares/                       # 共享工具和常量
│   └── types/                        # TypeScript 类型定义
│
├── .husky/                           # Git Hooks 配置
├── .github/                          # GitHub 工作流
├── docker-compose.yml                # 根目录 Docker 编排
├── nginx.conf                        # Nginx 配置
├── package.json                      # 根目录 pnpm 配置
├── pnpm-workspace.yaml               # pnpm 工作区配置
└── README.md                         # 项目说明文档
```

## 🎯 功能模块

### 👤 用户功能

- **账户管理**:注册/登录(手机号、邮箱)、个人资料、密码管理
- **内容浏览**:分类浏览、标签筛选、全文搜索、书籍详情
- **阅读体验**:章节阅读、翻页/滑动、字体/背景调节、夜间模式
- **互动功能**:书籍收藏、追更、章节评论、点赞
- **阅读进度**:自动同步、断点续读
- **个性化推荐**:首页推荐、书架推荐、猜你喜欢

### ✍️ 作者功能

- **作者认证**:在线申请、审核管理
- **作品管理**:创建书籍、编辑信息、章节管理
- **内容创作**:富文本编辑器、草稿系统、版本管理
- **数据统计**:阅读量、收藏数、评论数、收益分析
- **上传支持**:EPUB、TXT 等多种格式

### 🔧 管理员功能

- **用户管理**:用户列表、禁言、封号、作者认证审核
- **内容管理**:书籍/章节审核、下架、评论管理、举报处理
- **数据统计**:平台数据、活跃用户、阅读量统计
- **系统管理**:配置管理、日志查看、分类/标签管理

## 🛠️ 技术栈

### 后端技术

| 技术         | 版本               | 说明             |
| ------------ | ------------------ | ---------------- |
| **框架**     | FastAPI 0.119.0    | 异步 Web 框架    |
| **数据库**   | MySQL              | 主数据存储       |
| **缓存**     | Redis 6.4.0        | 缓存、限流、会话 |
| **ORM**      | SQLAlchemy 2.0.44  | 异步 ORM         |
| **推荐算法** | TF-IDF + NumPy     | 个性化推荐       |
| **认证**     | PyJWT 2.10.1       | JWT 身份认证     |
| **任务调度** | APScheduler 3.11.2 | 定时任务         |
| **部署**     | Docker + Gunicorn  | 容器化部署       |

### 前端技术

| 技术         | 版本                   | 说明           |
| ------------ | ---------------------- | -------------- |
| **框架**     | Vue 3.5.24             | 渐进式框架     |
| **语言**     | TypeScript 5.9.3       | 类型安全       |
| **状态管理** | Pinia 3.0.4            | Vue 3 官方推荐 |
| **路由**     | Vue Router 4.6.4       | SPA 路由       |
| **UI 组件**  | qyani-components 1.5.3 | 自定义组件库   |
| **构建工具** | Vite 7.2.5             | 快速开发服务器 |
| **图表**     | ECharts 6.0.0          | 数据可视化     |
| **HTTP**     | Axios 1.13.6           | HTTP 客户端    |

## 🚀 快速开始

### 环境要求

- **后端**:Python 3.8+
- **前端**:Node.js >= 16.0.0, pnpm >= 8.0.0
- **数据库**:MySQL 5.7+
- **缓存**:Redis 5.0+

### 后端部署

#### 方式一:本地运行

1. **安装依赖**

```bash
cd backend
pip install -r requirements.txt
```

2. **配置环境变量**

```bash
# 复制并编辑配置文件
cp .env.example .env
# 编辑 .env 文件,配置数据库、Redis、邮箱等信息
```

3. **初始化数据库**

```bash
# 执行数据库迁移
alembic upgrade head
```

4. **运行服务**

```bash
python run.py
# 或使用 gunicorn 生产环境部署
gunicorn -w 4 -k uvicorn.workers.UvicornWorker app.main:app --bind 0.0.0.0:8000
```

#### 方式二:Docker 部署

```bash
cd backend
docker-compose up -d
```

访问 API 文档:http://localhost:8000/docs

### 前端部署

#### Monorepo 统一管理

本项目采用 pnpm workspace 管理多个前端应用。

**安装所有依赖**

```bash
# 在根目录执行
pnpm install
```

**常用命令**

```bash
# 启动作者端开发服务器
pnpm dev:author

# 启动用户端开发服务器
pnpm dev:user

# 启动管理端开发服务器
pnpm dev:admin

# 构建所有前端应用
pnpm build:all

# 代码格式化
pnpm prettier
```

#### 单独运行某个应用

**作者端应用**

```bash
cd author
pnpm install
pnpm dev
# 访问 http://localhost:80
```

**用户端应用**

```bash
cd user
pnpm install
pnpm dev
```

**管理端应用**

```bash
cd admin
pnpm install
pnpm dev
```

## 📊 性能优化

### 后端优化策略

- **缓存策略**
  - Redis 缓存热门书籍、章节内容、推荐结果
  - 用户阅读进度实时缓存
  - 分布式限流保护核心接口

- **数据库优化**
  - 高频查询字段索引优化
  - 读写分离设计
  - 分页查询避免大量数据加载

- **并发处理**
  - 异步 IO 提升吞吐量
  - 分布式锁保证数据一致性
  - 线程池管理资源

### 前端优化策略

- **加载优化**:路由懒加载、组件异步加载
- **渲染优化**:虚拟列表、防抖节流
- **缓存优化**:本地存储、请求缓存
- **响应式设计**:移动端/桌面端自适应

## 🔒 安全设计

- **认证授权**:JWT Token + 权限校验
- **数据校验**:防止 SQL 注入、XSS 攻击
- **限流保护**:Redis 令牌桶算法限流
- **文件安全**:上传文件类型/内容校验
- **日志审计**:关键操作记录追踪

## 📈 高可用设计

- **负载均衡**:Nginx 反向代理 + 健康检查
- **服务冗余**:MySQL 主从复制、Redis 哨兵模式
- **故障恢复**:自动重启、异常监控
- **数据备份**:定时备份、灾难恢复

## 📝 API 文档

后端服务启动后,访问以下地址查看 API 文档:

- **Swagger UI**: http://localhost:8000/docs
- **ReDoc**: http://localhost:8000/redoc

## 🧪 测试

### 后端测试

```bash
cd backend
pytest
# 或指定测试文件
pytest test/api/v1/test_book.py
```

### 前端测试

```bash
# 代码检查和格式化（根目录）
pnpm prettier

# 单个应用检查
cd author
pnpm run lint
```

## 📦 部署架构

```
                    ┌─────────────┐
                    │   Nginx     │
                    │ Load Balancer│
                    └──────┬──────┘
                           │
          ┌────────────────┼────────────────┐
          │                │                │
    ┌─────▼─────┐   ┌─────▼─────┐   ┌─────▼─────┐
    │ FastAPI   │   │ FastAPI   │   │ FastAPI   │
    │ Instance 1│   │ Instance 2│   │ Instance 3│
    └─────┬─────┘   └─────┬─────┘   └─────┬─────┘
          │                │                │
          └────────────────┼────────────────┘
                           │
         ┌─────────────────┼─────────────────┐
         │                 │                 │
   ┌─────▼─────┐    ┌──────▼─────┐   ┌──────▼─────┐
   │   MySQL   │    │   Redis    │   │ File Store │
   │  Primary  │    │   Master   │   │   (OBS)    │
   └─────┬─────┘    └──────┬─────┘   └────────────┘
         │                 │
   ┌─────▼─────┐    ┌──────▼─────┐
   │   MySQL   │    │   Redis    │
   │   Slave   │    │   Slave    │
   └───────────┘    └────────────┘
```

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！

## 📄 许可证

ISC License

## 👥 联系方式

- **作者**:qianrenni
- **邮箱**:2112183503@qq.com

---

**注意**:本项目仅供学习交流使用,请勿用于商业用途。
