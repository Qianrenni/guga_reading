import asyncio
import uuid

from app.core.database import get_redis
from app.utils.renew_lock import RenewLock


class DistributedLock:
    """
    基于Redis的分布式锁实现
    支持阻塞等待和非阻塞两种模式,自动续期防止业务执行期间锁过期
    """

    def __init__(
        self,
        lock_key: str,
        expire_time: int = 10,
        blocking: bool = True,
        timeout: float = 10,
    ):
        """
        初始化分布式锁

        @param lock_key: 锁的唯一标识,如"lock:task:123"
        @param expire_time: 锁自动过期时间(秒),防止死锁,默认10秒
        @param blocking: 是否阻塞等待锁,默认True
        @param timeout: 最大等待时间(秒),仅在blocking=True时有效,默认10秒
        """
        self.lock_key = lock_key
        self.expire_time = expire_time
        self.blocking = blocking
        self.lock_value = str(uuid.uuid4())
        self.renew_lock = None
        self.timeout = timeout

    async def acquire(self) -> bool:
        """
        异步获取锁

        @return bool: 成功获取锁返回True,否则返回False
        """
        if self.blocking:
            start = asyncio.get_event_loop().time()
            while True:
                if await self._try_acquire():
                    self.renew_lock = RenewLock(
                        self.lock_key, self.lock_value, self.expire_time
                    )
                    self.renew_lock.start()
                    return True
                if (
                    self.timeout is not None
                    and asyncio.get_event_loop().time() - start >= self.timeout
                ):
                    return False
                await asyncio.sleep(0.05)  # 非阻塞等待
        else:
            return await self._try_acquire()

    async def _try_acquire(self) -> bool:
        """
        尝试一次获取锁

        @return bool: 成功获取锁返回True,否则返回False
        """
        # 注意:aioredis 的 set 支持 nx, px 参数(v2.x)
        redis = await get_redis()
        result = await redis.set(
            self.lock_key, self.lock_value, nx=True, ex=self.expire_time
        )
        return result is True

    async def release(self) -> None:
        """
        安全释放锁,确保只释放自己持有的锁
        """
        lua_script = """
        if redis.call("GET", KEYS[1]) == ARGV[1] then
            return redis.call("DEL", KEYS[1])
        else
            return 0
        end
        """
        redis = await get_redis()
        await redis.eval(lua_script, 1, self.lock_key, self.lock_value)

    async def __aenter__(self):
        """异步上下文管理器入口"""
        return await self.acquire()

    async def __aexit__(self, exc_type, exc_val, exc_tb):
        """异步上下文管理器出口,确保锁被正确释放"""
        # 停止锁续期任务
        if self.renew_lock:
            await self.renew_lock.stop()
        await self.release()
