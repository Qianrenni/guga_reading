# tests/test_cache_service.py
import asyncio
import time

import pytest

from app.core.database import close_redis, get_redis, init_redis
from app.services.cache_service import (
    RenewLock,
    cache,
    cache_delete,
    cache_get,
    cache_set,
    generate_cache_key,
)


@pytest.fixture(autouse=True)
async def clean_test_keys():
    """每次测试前后清理 test: 开头的 key"""
    await init_redis()
    try:
        redis = await get_redis()
        # 清理前
        keys = await redis.keys("test:*")
        if keys:
            await redis.delete(*keys)
        yield
    finally:
        # 清理后
        try:
            redis = await get_redis()
            keys = await redis.keys("test:*")
            if keys:
                await redis.delete(*keys)
        except Exception:
            pass  # 忽略清理错误
    await close_redis()


@pytest.mark.asyncio
async def test_generate_cache_key():
    key1 = generate_cache_key(
        args=[1, "hello"], kwargs={"a": 2, "b": "world"}, key_prefix="test"
    )
    key2 = generate_cache_key(
        args=[1, "hello"],
        kwargs={"b": "world", "a": 2},  # 顺序不同
        key_prefix="test",
    )
    assert key1 == key2  # 因为 sort_keys=True

    key3 = generate_cache_key(args=[1, "hello"], kwargs={"a": 2}, key_prefix="test")
    assert key1 != key3


@pytest.mark.asyncio
async def test_renew_lock_exits_when_lock_deleted():
    redis = await get_redis()
    lock_key = "test:lock:renew_exit"
    lock_value = "my_unique_value"
    lock_timeout = 2

    # 先设置锁
    is_set = await redis.set(lock_key, lock_value, ex=lock_timeout)
    assert is_set is True
    renew_lock = RenewLock(
        lock_key=lock_key,
        lock_value=lock_value,
        lock_timeout=lock_timeout,
        interval=0.5,
    )
    renew_lock.start()
    await asyncio.sleep(4)
    result = await redis.exists(lock_key)
    assert result == 1
    await renew_lock.stop()
    # 停止续期后,锁会在 lock_timeout 秒后自然过期
    # 等待足够长的时间让锁过期(至少 lock_timeout + 一些缓冲)
    await asyncio.sleep(lock_timeout + 1)
    result = await redis.exists(lock_key)
    assert result == 0


@pytest.mark.asyncio
async def test_cache_get_with_fallback():
    call_count = 0

    async def fallback():
        nonlocal call_count
        call_count += 1
        return {"data": "from_fallback"}

    result = await cache_get(
        key_prefix="test",
        args=[1],
        kwargs={"x": "y"},
        expire=10,
        fallback_func=fallback,
        lock_timeout=5,
    )

    assert result == {"data": "from_fallback"}
    assert call_count == 1

    # 第二次应命中缓存
    result2 = await cache_get(
        key_prefix="test", args=[1], kwargs={"x": "y"}, fallback_func=fallback
    )
    assert result2 == {"data": "from_fallback"}
    assert call_count == 1  # 未再次调用 fallback


@pytest.mark.asyncio
async def test_cache_get_concurrent_requests_only_one_fallback():
    call_count = 0
    barrier = asyncio.Event()

    async def slow_fallback():
        nonlocal call_count
        call_count += 1
        await asyncio.sleep(0.5)  # 模拟慢操作
        barrier.set()
        return {"data": f"result_{call_count}"}

    # 第一个请求
    task1 = asyncio.create_task(
        cache_get(
            key_prefix="test",
            args=["concurrent"],
            fallback_func=slow_fallback,
            lock_timeout=5,
        )
    )

    # 等待 fallback 开始
    await asyncio.sleep(0.1)

    # 第二个并发请求
    task2 = asyncio.create_task(
        cache_get(
            key_prefix="test",
            args=["concurrent"],
            fallback_func=slow_fallback,
            lock_timeout=5,
        )
    )

    # 等待两个完成
    res1 = await task1
    res2 = await task2

    assert res1 == res2 == {"data": "result_1"}
    assert call_count == 1  # 只调用一次 fallback


@pytest.mark.asyncio
async def test_cache_set_and_delete():
    key_prefix = "test"
    args = [123]

    # 设置缓存
    success = await cache_set(
        value={"msg": "hello"}, key_prefix=key_prefix, args=args, expire=10
    )
    assert success is True

    # 获取缓存
    value = await cache_get(key_prefix=key_prefix, args=args, fallback_func=None)
    assert value == {"msg": "hello"}

    # 删除缓存
    deleted = await cache_delete(key_prefix=key_prefix, args=args)
    assert deleted is True

    # 再次获取应为 None
    value2 = await cache_get(key_prefix=key_prefix, args=args, fallback_func=None)
    assert value2 is None


@pytest.mark.asyncio
async def test_cache_decorator():
    call_count = 0

    @cache(key_prefix="test:decorator", expire=10, lock_timeout=5)
    async def my_func(a: int, b: str = "default") -> dict:
        nonlocal call_count
        call_count += 1
        return {"a": a, "b": b, "call": call_count}

    # 第一次调用
    res1 = await my_func(10, b="x")
    assert res1 == {"a": 10, "b": "x", "call": 1}

    # 第二次调用(相同参数)
    res2 = await my_func(10, b="x")
    assert res2 == {"a": 10, "b": "x", "call": 1}  # call_count 未增加

    # 不同参数
    res3 = await my_func(20)
    assert res3 == {"a": 20, "b": "default", "call": 2}


@pytest.mark.asyncio
async def test_cache_get_fallback_timeout_and_retry():
    call_count = 0

    async def stuck_fallback():
        nonlocal call_count
        call_count += 1
        if call_count == 1:
            await asyncio.sleep(3)  # 模拟卡死(超时)
        return {"data": "success"}

    # 第一个请求(会超时并重试)
    start = time.time()
    result = await cache_get(
        key_prefix="test:timeout",
        args=["timeout"],
        fallback_func=stuck_fallback,
        lock_timeout=2,  # 锁只等 2s
    )
    elapsed = time.time() - start

    assert elapsed >= 3.0
    assert elapsed <= 6.0
    assert result == {"data": "success"}
