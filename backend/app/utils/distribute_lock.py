import asyncio
import uuid

from app.core.database import get_redis
from app.utils.renew_lock import RenewLock


class DistributedLock:
    def __init__(
        self,
        lock_key: str,
        expire_time: int = 10,
        blocking: bool = True,
        timeout: float = 10,
    ):
        """
        Args:
            lock_key (str): 锁的唯一标识(如 "lock:task:123")
            expire_time (int): 锁自动过期时间(秒),防止死锁
            blocking (bool): 是否阻塞等待锁
            timeout (float): 最大等待时间(秒)
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
        :param blocking: 是否阻塞等待
        :param timeout: 最大等待时间(秒)
        :return: 是否成功获取锁
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
        """尝试一次获取锁"""
        # 注意:aioredis 的 set 支持 nx, px 参数(v2.x)
        redis = await get_redis()
        result = await redis.set(
            self.lock_key, self.lock_value, nx=True, ex=self.expire_time
        )
        return result is True

    async def release(self) -> None:
        """安全释放锁"""
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
        return await self.acquire()

    async def __aexit__(self, exc_type, exc_val, exc_tb):
        # 停止锁续期任务
        if self.renew_lock:
            await self.renew_lock.stop()
        await self.release()
