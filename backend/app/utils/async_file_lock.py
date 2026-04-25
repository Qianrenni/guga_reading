import asyncio
from pathlib import Path

import portalocker


class AsyncFileLock:
    """
    异步跨进程文件锁(基于 portalocker)

    用法:
        async with AsyncFileLock("my.lock"):
            # 临界区代码

    特性:
    - 跨进程互斥(操作系统级别)
    - 异步非阻塞(通过线程池执行阻塞操作)
    - 自动创建锁文件目录
    - 进程崩溃时自动释放锁
    """

    def __init__(self, lock_file: str | Path, timeout: float = 10):
        self.lock_file = Path(lock_file)
        self.timeout = timeout  # -1 表示无限等待
        self._locker = None

    async def __aenter__(self):
        self.lock_file.parent.mkdir(parents=True, exist_ok=True)
        loop = asyncio.get_event_loop()
        await loop.run_in_executor(None, self._acquire_lock)
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb):
        loop = asyncio.get_event_loop()
        await loop.run_in_executor(None, self._release_lock)

    def _acquire_lock(self):
        # 转换 timeout:-1 → None(portalocker 要求)
        timeout = max(self.timeout, 10)

        self._locker = portalocker.Lock(
            str(self.lock_file),
            mode="w",  # 写模式创建文件
            timeout=timeout,  # 超时(秒),None 表示无限等待
            fail_when_locked=True,  # False = 阻塞等待 True = 立即失败
        )
        self._locker.acquire()  # 获取锁

    def _release_lock(self):
        if self._locker is not None:
            self._locker.release()
            self._locker = None
