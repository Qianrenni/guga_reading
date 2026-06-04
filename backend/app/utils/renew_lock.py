import asyncio

from app.core.database import get_redis
from app.middleware.logging import logger


class RenewLock:
    """
    自动续期锁
    用于在业务执行期间自动延长Redis锁的过期时间,防止锁提前过期
    """

    def __init__(
        self,
        lock_key: str,
        lock_value: str,
        lock_timeout: int,
        interval: float | None = None,
    ):
        """
        初始化自动续期锁

        @param lock_key: 锁的key
        @param lock_value: 锁的值,用于确保只续期自己持有的锁
        @param lock_timeout: 锁的过期时间(秒)
        @param interval: 锁续期的间隔(秒),默认为锁过期时间的三分之一
        """
        self.lock_key = lock_key
        self.lock_value = lock_value
        self.lock_timeout = lock_timeout
        self.interval = interval or max(1, lock_timeout // 3)
        self.renew_task = None

    async def _start(self):
        """
        后台续期任务,定期延长锁的过期时间
        当锁不再属于当前持有者时自动停止
        """
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
        """
        启动后台续期任务
        """
        self.renew_task = asyncio.create_task(self._start())

    async def stop(self):
        """
        停止后台续期任务
        """
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
