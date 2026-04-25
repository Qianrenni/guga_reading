# Online Reading Plus System

一个基于FastAPI的在线阅读系统,支持多角色(用户、作者、管理员)的跨平台在线阅读平台。

## 项目概述

本系统旨在构建一个支持多角色(作者、用户、管理员)的跨平台在线阅读系统。

### 业务目标

- 打造一个用户友好、内容丰富、运营高效的在线阅读平台
- 支持跨平台访问(Web、Android)

### 技术目标

- 保障核心阅读功能的高并发访问(目标 QPS ≥ 500)
- 提升首屏加载速度(目标 < 1s)
- 实现基于标签的个性化推荐
- 确保系统稳定性和数据安全性

## 功能特性

### 普通用户功能

- 账户管理:用户注册/登录(支持手机号、邮箱)、个人资料修改、密码找回/修改
- 内容浏览与搜索:小说/书籍分类浏览、标签筛选、全文搜索、书籍详情页
- 阅读功能:章节阅读(支持翻页、滑动、字体大小/颜色/背景调节、夜间模式)
- 互动功能:书籍收藏、追更、章节评论、点赞
- 阅读进度同步(断点续读)
- 个性化推荐:首页/发现页推荐、书架推荐(猜你喜欢)

### 作者功能

- 作者认证申请与管理
- 作品管理:上传书籍(支持 EPUB、TXT 等格式)、书籍信息编辑、章节管理
- 数据统计:查看作品的阅读量、收藏数、评论数

### 管理员功能

- 用户管理:用户列表查询、禁言、封号、作者认证审核
- 内容管理:书籍/章节内容审核、下架、评论管理、违规内容举报处理
- 数据统计与分析:平台总用户数、活跃用户数、书籍/章节阅读量统计
- 系统管理:系统配置、日志管理、分类/标签管理、通知公告发布

## 技术栈

- **后端框架**: FastAPI (异步)
- **数据库**: MySQL (存储用户信息、书籍元数据、章节内容等所有数据)
- **缓存**: Redis (缓存热点数据、实现限流、存储阅读进度)
- **ORM**: SQLAlchemy (异步)
- **搜索引擎**(可选): Elasticsearch (用于全文搜索)
- **文件存储**: 华为云OBS 或本地存储
- **部署**: Docker + Docker Compose,运行在华为云 FLEXIUS (2C2G) 服务器

## 项目结构

```
online-reading-plus-system/                          # 在线阅读系统主目录
├── .dockerignore                                    # Docker构建时忽略的文件
├── .env                                             # 环境变量配置文件
├── .gitignore                                       # Git版本控制忽略的文件
├── .plantuml/                                       # PlantUML图表生成目录
│   └── uml/                                         # UML图表文件
│       ├── database_model/                          # 数据库模型图
│       │   └── OnlineReadingPlusDatabaseModel_Full.svg
│       ├── deployment/                              # 部署图
│       │   └── OnlineReadingPlusSystemDeploymentDiagram.svg
│       ├── entity_relation/                         # 实体关系图
│       │   └── OnlineReadingSystem_ER.svg
│       ├── right_database_model/                    # 权限数据库模型图
│       │   └── right_database_model.svg
│       ├── right_database_model.png                 # 权限数据库模型图(PNG格式)
│       └── right_er.png                             # 权限实体关系图(PNG格式)
├── Dockerfile                                       # Docker容器构建文件
├── LICENSE                                          # 项目许可证文件
├── README.md                                        # 项目说明文档
├── alembic/                                         # Alembic数据库迁移工具配置
│   ├── README                                       # Alembic使用说明
│   ├── env.py                                       # Alembic环境配置
│   ├── script.py.mako                               # Alembic脚本模板
│   └── versions/                                    # 数据库迁移版本历史
│       └── 827369622db9_inital.py                   # 初始数据库迁移脚本
├── alembic.ini                                      # Alembic配置文件
├── app/                                             # 应用主代码目录
│   ├── __init__.py                                  # 应用包初始化文件
│   ├── algorithm/                                   # 算法实现模块
│   │   ├── __init__.py                              # 算法包初始化
│   │   └── tfidf_vectorizer.py                      # TF-IDF向量化器实现
│   ├── api/                                         # API接口定义
│   │   ├── __init__.py                              # API包初始化
│   │   └── v1/                                      # API版本1
│   │       ├── __init__.py                          # API v1初始化
│   │       ├── book.py                              # 图书相关API接口
│   │       ├── captcha.py                           # 验证码相关API接口
│   │       ├── right.py                             # 权限相关API接口
│   │       ├── shelf.py                             # 书架相关API接口
│   │       ├── token.py                             # 认证令牌相关API接口
│   │       ├── user.py                              # 用户相关API接口
│   │       └── user_reading_progress.py             # 用户阅读进度相关API接口
│   ├── core/                                        # 核心功能模块
│   │   ├── __init__.py                              # 核心包初始化
│   │   ├── config.py                                # 项目配置管理
│   │   ├── database.py                              # 数据库连接和初始化
│   │   ├── error_handler.py                         # 错误处理机制
│   │   └── security.py                              # 安全相关功能(加密、认证等)
│   ├── enum/                                        # 枚举定义
│   │   ├── __init__.py                              # 枚举包初始化
│   │   └── enum.py                                  # 枚举类定义
│   ├── epub_parser.py                               # EPUB电子书解析器
│   ├── main.py                                      # FastAPI应用主入口
│   ├── middleware/                                  # 中间件定义
│   │   ├── __init__.py                              # 中间件包初始化
│   │   ├── logging.py                               # 日志记录中间件
│   │   └── rate_limit.py                            # 限流中间件
│   ├── models/                                      # 数据模型定义
│   │   ├── __init__.py                              # 模型包初始化
│   │   ├── response_model.py                        # 响应数据模型
│   │   └── sql/                                     # SQL数据库模型
│   │       ├── Author.py                            # 作者模型
│   │       ├── Book.py                              # 图书模型
│   │       ├── BookChapter.py                       # 图书章节模型
│   │       ├── Right.py                             # 权限模型
│   │       ├── Shelf.py                             # 书架模型
│   │       ├── User.py                              # 用户模型
│   │       ├── UserReadingProgress.py               # 用户阅读进度模型
│   │       └── __init__.py                          # SQL模型包初始化
│   ├── schema/                                      # 数据验证模式
│   │   ├── __init__.py                              # 模式包初始化
│   │   ├── book.py                                  # 图书相关数据模式
│   │   └── common.py                                # 通用数据模式
│   ├── services/                                    # 业务逻辑服务层
│   │   ├── __init__.py                              # 服务包初始化
│   │   ├── book_service.py                          # 图书业务服务
│   │   ├── cache_service.py                         # 缓存服务
│   │   ├── captcha_service.py                       # 验证码服务
│   │   ├── email_service.py                         # 邮件发送服务
│   │   ├── recommend_service.py                     # 推荐服务
│   │   ├── right_service.py                         # 权限服务
│   │   ├── shelf_service.py                         # 书架服务
│   │   ├── user_reading_progress_service.py         # 用户阅读进度服务
│   │   └── user_service.py                          # 用户服务
│   └── utils/                                       # 工具函数
│       ├── __init__.py                              # 工具包初始化
│       ├── class_property.py                        # 类属性工具
│       ├── codec.py                                 # 编解码工具
│       ├── distribute_lock.py                       # 分布式锁工具
│       └── sort.py                                  # 排序算法工具
├── book.json                                        # 图书数据文件
├── design.md                                        # 系统设计文档
├── docker-compose.yml                               # Docker Compose服务编排
├── docs/                                            # 文档目录
│   ├── permission.sql                               # 权限相关SQL脚本
│   └── permissions.yaml                             # 权限配置文件
├── example/                                         # 配置示例目录
│   ├── .env.example                                 # 环境变量配置示例
│   ├── alembic.ini.example                          # Alembic配置示例
│   └── docker-compose.yaml.example                  # Docker Compose配置示例
├── model.pkl.npz                                    # 推荐系统模型文件
├── requirements.txt                                 # Python依赖包列表
├── run.py                                           # 项目启动脚本
├── scripts/                                         # 脚本目录
│   └── scratch_book_from_internet.py                # 从互联网抓取图书的脚本
├── static/                                          # 静态资源目录(CSS, JS, 图片等)
├── test/                                            # 测试代码目录
│   ├── __init__.py                                  # 测试包初始化
│   ├── api/                                         # API接口测试
│   │   ├── __init__.py                              # API测试包初始化
│   │   └── v1/                                      # API v1测试
│   │       ├── __init__.py                          # API v1测试初始化
│   │       ├── test_book.py                         # 图书API测试
│   │       ├── test_right.py                        # 权限API测试
│   │       ├── test_shelf.py                        # 书架API测试
│   │       ├── test_token.py                        # 令牌API测试
│   │       ├── test_user.py                         # 用户API测试
│   │       └── test_user_reading_progress.py        # 用户阅读进度API测试
│   ├── config.py                                    # 测试配置
│   └── conftest.py                                  # Pytest配置文件
```

## 安装与运行

### 本地运行

1. 安装依赖:

   ```bash
   pip install -r requirements.txt
   ```

2. 配置环境变量:
   复制 `.env.example` 为 `.env` 并填写相应配置
   复制 ’alembic.ini.example`为`alembic.ini`并填写相应配置
复制`docker-compose.yaml.example`为`docker-compose.yaml` 并填写相应配置

3. 运行应用:
   ```bash
   python run.py
   ```

### Docker运行

```bash
docker-compose up -d
```

## 性能优化策略

### 后端优化

- **缓存策略**:
  - Redis 缓存热门书籍列表、书籍详情、章节内容(首次加载后缓存)
  - 缓存用户阅读进度
  - 缓存推荐结果(可定时更新)
- **数据库优化**:
  - 为高频查询字段建立索引
  - 读写分离(主库写,从库读)
  - 合理设计分表(如按用户ID或书籍ID哈希分表,应对大数据量)
- **接口优化**:
  - 分页查询,避免一次性加载大量数据
  - 对于书籍内容,可考虑分段加载

## 高并发与高可用设计

- **限流**:使用 Redis 实现分布式限流(令牌桶算法),保护核心接口
- **负载均衡**:在华为云上配置负载均衡器,将流量分发到多个 FastAPI 应用实例
- **服务冗余**:MySQL 主从复制,Redis 主从或哨兵模式,提高数据可靠性
- **健康检查**:Nginx 配置后端健康检查,自动剔除故障实例

## 安全性设计

- **认证与授权**:使用 JWT(JSON Web Token)实现用户身份认证,后端接口进行权限校验
- **数据校验**:对所有用户输入进行严格校验,防止 SQL 注入、XSS
- **文件上传安全**:校验文件类型、大小、内容,防止恶意文件上传
- **日志审计**:记录用户关键操作日志,便于审计和问题追踪

## API文档

运行后访问以下地址查看API文档:

- Swagger UI: http://localhost:8000/docs
- ReDoc: http://localhost:8000/redoc
