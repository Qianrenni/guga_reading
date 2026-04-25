import asyncio

from app.core.database import get_redis
from app.middleware.logging import logger


class RenewLock:
    """
    自动续期锁
    """

    def __init__(
        self,
        lock_key: str,
        lock_value: str,
        lock_timeout: int,
        interval: float | None = None,
    ):
        """
        Args:
            lock_key (str): 锁的 key
            lock_value (str): 锁的值
            lock_timeout (int): 锁的过期时间(秒)
            interval (float | None): 锁续期的间隔(秒),默认为锁过期时间的三分之一
        """
        self.lock_key = lock_key
        self.lock_value = lock_value
        self.lock_timeout = lock_timeout
        self.interval = interval or max(1, lock_timeout // 3)
        self.renew_task = None

    async def _start(self):
        redis_pool = await get_redis()
        while True:
            try:
                # 使用 Lua 脚本安全续期:仅当锁仍属于当前持有者时才延长
                lua_script = """
                if redis.call("get", KEYS[1]) == ARGV[1] then
                    return redis.call("expire", KEYS[1], ARGV[2])
                else
                    return 0
                end
                """
                result = await redis_pool.eval(
                    lua_script,
                    1,
                    self.lock_key,
                    self.lock_value,
                    str(self.lock_timeout),
                )

                if result == 0:
                    logger.info(
                        f"Lock {self.lock_key} is no longer held (deleted or stolen). Stopping renewal."
                    )
                    break  # 主动退出
                logger.debug(f"Lock renewed: {self.lock_key}")
            except Exception as e:
                logger.warning(f"Failed to renew lock {self.lock_key}: {e}")
                break
            await asyncio.sleep(self.interval)

    def start(self):
        self.renew_task = asyncio.create_task(self._start())

    async def stop(self):
        # 停止锁续期任务
        if self.renew_task and not self.renew_task.done():
            self.renew_task.cancel()
            logger.debug(f"Canceling lock renewal task for {self.lock_key}")
            try:
                await self.renew_task
            except asyncio.CancelledError:
                pass
            except Exception as e:
                logger.warning(f"Error while cancelling lock renewal: {e}")
