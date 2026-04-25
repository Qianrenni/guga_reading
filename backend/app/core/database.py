# app/core/database.py
from redis.asyncio import Redis
from sqlalchemy.ext.asyncio import create_async_engine
from sqlmodel.ext.asyncio.session import AsyncSession

from app.core.config import SETTING

_redis_pool = None


async def get_redis():
    """获取 Redis 客户端(依赖注入用)"""
    if _redis_pool is None:
        await init_redis()
    return _redis_pool


async def init_redis():
    """初始化 Redis 连接池(在 lifespan 中调用)"""
    global _redis_pool
    if _redis_pool is not None:
        return  # 连接池已初始化
    _redis_pool = Redis.from_url(
        SETTING.REDIS_URL,
        encoding="utf-8",
        max_connections=SETTING.REDIS_CLIENT_POOL_SIZE,
        retry_on_timeout=True,
        socket_connect_timeout=SETTING.REDIS_WAIT_TIMEOUT,
        socket_timeout=SETTING.REDIS_WAIT_TIMEOUT,
    )


async def close_redis() -> None:
    """关闭 Redis 连接池"""
    global _redis_pool
    if _redis_pool:
        await _redis_pool.aclose()
        _redis_pool = None


# redis 配置

# MySQL 异步引擎
engine = create_async_engine(
    url=SETTING.MYSQL_DSN,
    pool_size=20,
    max_overflow=10,
    pool_pre_ping=True,
    pool_recycle=3600,
    # echo=SETTING.ENV=="dev",
)


async def get_session():
    """
    获取数据库连接,异步上下文管理器
    """
    async with AsyncSession(engine) as session:
        yield session


def get_session_context():
    """
    获取数据库连接,同步上下文管理器
    """
    return AsyncSession(engine)


async def close_database() -> None:
    """主动关闭 MySQL 异步引擎的连接池"""
    global engine
    if engine:
        await engine.dispose()
