# app/services/cache_service.py

import asyncio
import functools
import hashlib
import json
import uuid
from collections.abc import Callable
from typing import Any

from app.core.config import SETTING
from app.core.database import get_redis
from app.middleware.logging import logger
from app.utils.codec import CacheCodec
from app.utils.renew_lock import RenewLock


def generate_cache_key(
    args: list[Any] | None = None,
    kwargs: dict[str, Any] | None = None,
    exclude_args: list[int] | None = None,
    exclude_kwargs: list[str] | None = None,
    key_prefix: str | None = None,
) -> str:
    """
    生成缓存 key

    :param args: 用于生成 key 的位置参数
    :param kwargs: 用于生成 key 的关键字参数
    :param exclude_args: 不参与 key 生成的位置参数索引
    :param exclude_kwargs: 不参与 key 生成的关键字参数名称
    :param key_prefix: key 前缀
    :return: 缓存 key
    """
    args = args or []
    kwargs = kwargs or {}
    exclude_args = exclude_args or []
    exclude_kwargs = exclude_kwargs or []

    filtered_args = [arg for i, arg in enumerate(args) if i not in exclude_args]
    filtered_kwargs = {k: v for k, v in kwargs.items() if k not in exclude_kwargs}

    serialized = json.dumps(
        {"args": filtered_args, "kwargs": filtered_kwargs},
        sort_keys=True,
        default=str,
    )

    key_hash = hashlib.md5(serialized.encode()).hexdigest()
    return f"{key_prefix}:{key_hash}"


async def cache_get(
    *,
    args: list[Any] | None = None,
    kwargs: dict[str, Any] | None = None,
    expire: int = 300,
    ignore_null: bool = True,
    exclude_args: list[int] | None = None,
    exclude_kwargs: list[str] | None = None,
    key_prefix: str | None = None,
    lock_timeout: int = SETTING.FALL_BACK_FUNCTION_WINDOW,
    fallback_func: Callable[..., Any] | None = None,
    codec: CacheCodec = CacheCodec(),
) -> Any:
    """
    获取缓存

    :param args: 用于生成缓存 key 的位置参数
    :param kwargs: 用于生成缓存 key 的关键字参数
    :param expire: 缓存过期时间(秒)
    :param ignore_null: 如果返回值为 None 或空列表,则不缓存,默认值True表示不会存入空值
    :param exclude_args: 不参与缓存 key 生成的位置参数索引
    :param exclude_kwargs: 不参与缓存 key 生成的关键字参数名称
    :param key_prefix: 缓存 key 的前缀
    :param lock_timeout: 锁的过期时间(秒)
    :param fallback_func: 缓存获取失败执行的回源函数
    :param codec: 缓存值编解码器
    """
    cache_key = generate_cache_key(
        args, kwargs, exclude_args, exclude_kwargs, key_prefix
    )
    lock_key = f"lock:{cache_key}"
    redis_pool = await get_redis()

    # 1. 尝试读缓存
    try:
        cached = await redis_pool.get(cache_key)
        if cached is not None:
            logger.info(f"Cache hit: {cache_key}")
            return codec.decode(cached)
    except Exception as e:
        logger.error(f"Cache read failed: {e}")

    # 2. 如果没有 fallback_func,直接返回 None
    if fallback_func is None:
        return None

    # 3. 有 fallback_func:尝试加锁回源
    lock_acquired = False
    lock_value = str(uuid.uuid4())
    renew_lock = None

    try:
        # 尝试获取锁(带唯一值)
        lock_acquired = await redis_pool.set(
            lock_key, lock_value, ex=lock_timeout, nx=True
        )

        if lock_acquired:
            # 启动锁续期任务
            renew_lock = RenewLock(lock_key, lock_value, lock_timeout)
            renew_lock.start()
            # 双重检查:可能在加锁前已被写入
            cached = await redis_pool.get(cache_key)
            if cached is not None:
                return codec.decode(cached)

            # 执行回源逻辑
            result = await fallback_func()

            # 决定是否缓存
            should_cache = not (
                ignore_null and (result is None or result == {} or result == [])
            )
            if should_cache:
                await redis_pool.setex(cache_key, expire, codec.encode(result))
                logger.info(f"Cache set: {cache_key} (expire={expire}s)")

            return result
        else:
            # 未获取到锁:循环等待缓存被写入
            start_time = asyncio.get_event_loop().time()
            max_wait = lock_timeout + 1  # 略大于锁超时,防误判

            while asyncio.get_event_loop().time() - start_time < max_wait:
                await asyncio.sleep(0.05)
                cached = await redis_pool.get(cache_key)
                if cached is not None:
                    logger.info(f"Cache filled by another worker: {cache_key}")
                    return codec.decode(cached)

            # 超时仍未命中,认为锁持有者失败,自己回源
            logger.warning(
                f"Cache still empty after {max_wait}s, "
                f"executing fallback directly: {cache_key}"
            )
            return await fallback_func()

    except Exception as e:
        logger.error(f"Error in cache_get fallback: {e}")
        raise

    finally:
        if renew_lock is not None:
            await renew_lock.stop()

        # 安全释放锁(仅删除属于自己的锁)
        if lock_acquired:
            try:
                lua_script = """
                if redis.call("get", KEYS[1]) == ARGV[1] then
                    return redis.call("del", KEYS[1])
                else
                    return 0
                end
                """
                await redis_pool.eval(lua_script, 1, lock_key, lock_value)
            except Exception as e:
                logger.warning(f"Failed to release lock {lock_key}: {e}")


async def cache_set(
    *,
    value: Any,
    args: list[Any] | None = None,
    kwargs: dict[str, Any] | None = None,
    expire: int = 300,
    ignore_null: bool = True,
    exclude_args: list[int] | None = None,
    exclude_kwargs: list[str] | None = None,
    key_prefix: str | None = None,
    lock_timeout: int = SETTING.FALL_BACK_FUNCTION_WINDOW,
    acquire_lock: bool = True,  # 新增:是否尝试加锁
    codec: CacheCodec = CacheCodec(),
) -> bool:
    """
    设置缓存

    :param value: 缓存的值
    :param args: 用于生成缓存 key 的位置参数
    :param kwargs: 用于生成缓存 key 的关键字参数
    :param expire: 缓存过期时间(秒)
    :param ignore_null: 如果值为 None 或空列表,则不缓存,默认值True表示不会存入空值
    :param exclude_args: 不参与缓存 key 生成的位置参数索引
    :param exclude_kwargs: 不参与缓存 key 生成的关键字参数名称
    :param key_prefix: 缓存 key 的前缀
    :param lock_timeout:函数执行过期时间(秒)
    :param acquire_lock: 是否加锁,如果True,则在lock_timeout时间内只有一个线程能够访问
    :param codec: 缓存值编解码器
    """
    if key_prefix is None or key_prefix == "":
        raise ValueError("key_prefix must be set")
    if ignore_null and (value is None or value == {} or value == []):
        return False

    cache_key = generate_cache_key(
        args, kwargs, exclude_args, exclude_kwargs, key_prefix
    )
    redis_pool = await get_redis()
    # 如果不需要锁,直接设置
    if not acquire_lock:
        try:
            await redis_pool.setex(cache_key, expire, codec.encode(value))
            logger.info(f"Manual cache set (no lock): {cache_key} (expire={expire}s)")
            return True
        except Exception as e:
            logger.error(f"Manual cache set failed: {e}")
            return False

    # === 以下为带锁逻辑(与 cache_get 兼容)===
    lock_key = f"lock:{cache_key}"
    lock_value = str(uuid.uuid4())
    lock_acquired = False

    try:
        # 尝试获取锁
        lock_acquired = await redis_pool.set(
            lock_key, lock_value, ex=lock_timeout, nx=True
        )
        if not lock_acquired:
            logger.debug(
                f"cache_set skipped: lock held by another process for {cache_key}"
            )
            return False

        # 双重检查:可能已有新值(比如 cache_get 刚写入)
        existing = await redis_pool.get(cache_key)
        if existing is not None:
            logger.debug(f"cache_set skipped: cache already exists for {cache_key}")
            return False

        # 设置缓存
        await redis_pool.setex(cache_key, expire, codec.encode(value))
        logger.info(f"Manual cache set with lock: {cache_key} (expire={expire}s)")
        return True

    except Exception as e:
        logger.error(f"Manual cache set with lock failed: {e}")
        return False

    finally:
        # 安全释放锁(仅删除属于自己的)
        if lock_acquired:
            try:
                lua_script = """
                if redis.call("get", KEYS[1]) == ARGV[1] then
                    return redis.call("del", KEYS[1])
                else
                    return 0
                end
                """
                await redis_pool.eval(lua_script, 1, lock_key, lock_value)
            except Exception as e:
                logger.warning(f"Failed to release lock in cache_set {lock_key}: {e}")


async def cache_delete(
    *,
    args: list[Any] | None = None,
    kwargs: dict[str, Any] | None = None,
    exclude_args: list[int] | None = None,
    exclude_kwargs: list[str] | None = None,
    key_prefix: str | None = None,
) -> bool:
    """
    删除缓存

    :param args: 用于生成缓存 key 的位置参数
    :param kwargs: 用于生成缓存 key 的关键字参数
    :param exclude_args: 不参与缓存 key 生成的位置参数索引
    :param exclude_kwargs: 不参与缓存 key 生成的关键字参数名称
    :param key_prefix: 缓存 key 的前缀
    """
    if key_prefix is None or key_prefix == "":
        raise ValueError("key_prefix must be set")
    redis_pool = await get_redis()
    try:
        cache_key = generate_cache_key(
            args, kwargs, exclude_args, exclude_kwargs, key_prefix
        )
        result = await redis_pool.delete(cache_key)
        deleted = result > 0
        if deleted:
            logger.info(f"Cache deleted: {cache_key}")
        else:
            logger.info(f"Cache key not found for deletion: {cache_key}")
        return deleted
    except Exception as e:
        logger.error(f"Cache delete failed: {e}")
        return False


def cache(
    *,
    expire: int = 300,
    ignore_null: bool = True,
    exclude_args: list[int] | None = None,
    exclude_kwargs: list[str] | None = None,
    key_prefix: str | None = None,
    lock_timeout: int = SETTING.FALL_BACK_FUNCTION_WINDOW,
    codec: CacheCodec = CacheCodec(),
):
    """
    缓存装饰器

    :param expire: 缓存过期时间(秒)
    :param ignore_null: 如果值为 None 或空列表,则不缓存,默认值True表示不会存入空值
    :param exclude_args: 不参与缓存 key 生成的位置参数索引
    :param exclude_kwargs: 不参与缓存 key 生成的关键字参数名称
    :param key_prefix: 缓存 key 的前缀
    :param lock_timeout:函数执行过期时间(秒)
    :param codec: 缓存值编解码器,默认不做特殊处理
    """

    def decorator(func: Callable) -> Callable:
        @functools.wraps(func)
        async def wrapper(*args, **kwargs) -> Any:
            async def _fallback():
                return await func(*args, **kwargs)

            return await cache_get(
                args=list(args),
                kwargs=kwargs,
                expire=expire,
                ignore_null=ignore_null,
                exclude_args=exclude_args,
                exclude_kwargs=exclude_kwargs,
                key_prefix=key_prefix if key_prefix else func.__qualname__,
                lock_timeout=lock_timeout,
                fallback_func=_fallback,
                codec=codec,
            )

        return wrapper

    return decorator
