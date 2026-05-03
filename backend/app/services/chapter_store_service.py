import os
import shutil
import struct
import time
from pathlib import Path

import aiofiles  # 异步文件 I/O 库
import msgpack  # 高效二进制序列化库,用于持久化索引

from app.core.config import SETTING
from app.core.error_handler import AppError
from app.middleware.logging import logger
from app.utils.distribute_lock import DistributedLock


class ChapterStore:
    """
    面向单本书的章节存储引擎,支持完整的 CRUD 操作。

    设计目标:
    - 解决海量小文件问题(1000 章 = 1 个 data.log 文件)
    - 支持高效随机读取
    - 支持章节更新与删除(通过追加写 + 索引)
    - 支持垃圾回收(compaction)
    - 全异步 I/O,适配 FastAPI 异步框架

    存储结构:
    book/
    └── book_{book_id}/
        ├── data.log   ← 所有章节记录以追加方式写入(二进制日志)
        └── index.idx  ← 内存索引的持久化快照(msgpack 格式)
    """

    # 二进制记录头格式:小端字节序
    # I: uint32 (chapter_id, 4字节)
    # Q: uint64 (timestamp 微秒级, 8字节)
    # ?: bool (deleted, 1字节)
    # I: uint32 (content_size, 4字节)
    # 总计:4 + 8 + 1 + 4 = 17 字节
    RECORD_HEADER_FMT = "<IQ?I"
    RECORD_HEADER_SIZE = struct.calcsize(RECORD_HEADER_FMT)

    def __init__(self, book_id: int, base_dir: Path | None = None):
        """
        初始化章节存储实例。

        :param book_id: 书籍唯一标识
        :param base_dir: 存储根目录,默认为 ./data/books
        """
        self.book_id = book_id
        base_dir = base_dir or SETTING.BOOK_DIR_PATH
        self.dir = base_dir / f"{book_id}"
        # 自动创建书籍目录(parents=True 支持嵌套目录,exist_ok=True 避免重复创建报错)
        self.dir.mkdir(parents=True, exist_ok=True)
        self.data_path = self.dir / "data.log"  # 主数据日志文件
        self.index_path = self.dir / "index.idx"  # 索引持久化文件
        self.lock_path = self.dir / "write.lock"  # ← 锁文件
        # 内存索引:chapter_id -> {offset, size, deleted, timestamp}
        # 注意:该索引在 _load_index() 中初始化
        self._index: dict[int, dict[str, any]] = {}
        logger.debug(f"Initialized ChapterStore for book {book_id}")

    async def _save_index(self):
        """
        将内存中的索引字典持久化到 index.idx 文件(使用 msgpack 二进制格式)。

        说明:
        - msgpack 比 JSON 更紧凑、更快,且支持二进制安全
        - 字典 key 必须是字符串(msgpack 要求),因此将 int 转为 str
        - 使用 aiofiles 实现异步写入,避免阻塞事件循环
        """
        async with DistributedLock(f"chapter_store_save_index_{self.book_id}") as lock:
            # 如果没有获取到锁,则返回
            if not lock:
                logger.error(f"Failed to acquire lock for chapter store {self.book_id}")
                return
            async with aiofiles.open(self.index_path, "wb") as f:
                # 转换 key 为字符串,因为 msgpack 不允许非字符串 dict key
                serializable = {str(k): v for k, v in self._index.items()}
                packed_data = msgpack.packb(serializable)
                await f.write(packed_data)

    async def _load_index(self):
        """
        启动时加载索引:
        1. 优先尝试从 index.idx 快速加载(O(1) 启动)
        2. 若文件不存在或损坏,则回退到扫描 data.log 重建索引(较慢)

        注意:此方法应在 __init__ 后手动调用(因是 async 方法)
        """
        self._index.clear()

        # 尝试从持久化索引加载(快速路径)
        if self.index_path.exists():
            try:
                async with aiofiles.open(self.index_path, "rb") as f:
                    data = await f.read()
                    if data:  # 非空文件
                        loaded = msgpack.unpackb(data, raw=False)
                        # 将 key 从 str 转回 int
                        self._index = {int(k): v for k, v in loaded.items()}
                        return
            except Exception as e:
                logger.warning(
                    f"Failed to load index.idx for book {self.book_id}, falling back to data.log: {e}"
                )

        # 回退:从 data.log 重建索引(慢路径)
        # 注意:若 data.log 也不存在,则跳过(_index 保持为空)
        if not self.data_path.exists():
            return

        async with aiofiles.open(self.data_path, "rb") as f:
            while True:
                # 读取固定长度的记录头
                header_bytes = await f.read(self.RECORD_HEADER_SIZE)
                if len(header_bytes) < self.RECORD_HEADER_SIZE:
                    break  # 文件结束

                # 解析二进制头
                chapter_id, ts, deleted, content_size = struct.unpack(
                    self.RECORD_HEADER_FMT, header_bytes
                )
                # 当前位置即为内容起始偏移
                offset = await f.tell()

                # 更新索引:保留最新写入的记录(覆盖旧版本)
                self._index[chapter_id] = {
                    "offset": offset,
                    "size": content_size,
                    "deleted": deleted,
                    "timestamp": ts,
                }

                # 跳过内容部分,准备读取下一条记录
                await f.seek(content_size, os.SEEK_CUR)

        # 重建完成后,立即持久化索引,加速下次启动
        await self._save_index()

    async def _append_record(
        self, chapter_id: int, content: str, deleted: bool = False
    ):
        """
        追加一条新记录到 data.log 末尾,并更新内存索引。

        所有写操作(create/update/delete)最终都调用此方法。
        采用“追加写”策略,保证写入高效且线程安全(无随机写)。

        :param chapter_id: 章节ID
        :param content: 章节内容(若 deleted=True,可为空)
        :param deleted: 是否为删除标记(tombstone)
        """
        # 使用微秒级时间戳,确保版本唯一性
        ts = int(time.time() * 1e6)
        # 按配置编码(如 UTF-8)转为 bytes
        data = content.encode(SETTING.CHAPTER_ENCODING)
        # 打包二进制头
        header = struct.pack(self.RECORD_HEADER_FMT, chapter_id, ts, deleted, len(data))
        # 写入 data.log
        async with DistributedLock(
            f"chapter_store_append_record_{self.book_id}"
        ) as lock:
            if not lock:
                logger.error(f"Failed to acquire lock for chapter store {self.book_id}")
                return
            async with aiofiles.open(self.data_path, "ab") as f:
                await f.write(header)
                await f.write(data)
                offset = (await f.tell()) - len(data)

        # 更新内存索引
        self._index[chapter_id] = {
            "offset": offset,
            "size": len(data),
            "deleted": deleted,
            "timestamp": ts,
        }

        # 如果需要实时保存索引,也在锁内进行
        await self._save_index()
        logger.debug(
            f"Appended record for chapter book {self.book_id} chapter {chapter_id}"
        )

    async def read_chapter(self, chapter_id: int) -> str:
        """
        读取指定章节内容。
        若章节不存在或已被删除,返回 ''
        """
        meta = self._index.get(chapter_id)
        if not meta or meta["deleted"]:
            raise AppError(message="Chapter not found", status_code=404)
        # 替代方案(全异步):
        async with aiofiles.open(self.data_path, "rb") as f:
            await f.seek(meta["offset"])
            raw_content = await f.read(meta["size"])
            return raw_content.decode(SETTING.CHAPTER_ENCODING)

    async def update_chapter(self, chapter_id: int, content: str):
        """
        更新现有章节内容。
        """
        await self._append_record(chapter_id, content, deleted=False)

    async def delete_chapter(self, chapter_id: int):
        """
        删除章节(逻辑删除)。
        幂等操作:多次删除无副作用。
        """
        if chapter_id not in self._index or self._index[chapter_id]["deleted"]:
            return  # 已删除或不存在,直接返回
        # 写入一个空内容的删除标记
        await self._append_record(chapter_id, "", deleted=True)

    async def list_chapters(self) -> list[int]:
        """
        返回所有有效章节 ID 列表(按 chapter_id 升序)。
        """
        valid_ids = [cid for cid, meta in self._index.items() if not meta["deleted"]]
        return sorted(valid_ids)

    # ===== Compaction(垃圾回收)=====

    async def compact(self):
        """
        执行 compaction(压缩/清理):
        - 仅保留未删除的最新章节
        - 重写 data.log,移除历史版本和删除标记
        - 减小文件体积,提升读取效率

        注意:此操作会阻塞写入(建议在低峰期执行)
        """
        if not self.data_path.exists():
            return

        # 第一步:收集所有有效章节内容
        valid_records = []
        for chapter_id, meta in self._index.items():
            if not meta["deleted"]:
                # 从旧 data.log 中读取有效内容
                async with aiofiles.open(self.data_path, "rb") as f:
                    await f.seek(meta["offset"])
                    raw_content = await f.read(meta["size"])
                    content = raw_content.decode(SETTING.CHAPTER_ENCODING)
                valid_records.append((chapter_id, content))

        # 第二步:写入临时文件
        temp_path = self.data_path.with_suffix(".log.tmp")
        new_index = {}
        current_offset = 0  # 新文件中的累计偏移

        async with aiofiles.open(temp_path, "wb") as f_out:
            for chapter_id, content in valid_records:
                ts = int(time.time() * 1e6)
                data = content.encode(SETTING.CHAPTER_ENCODING)
                header = struct.pack(
                    self.RECORD_HEADER_FMT, chapter_id, ts, False, len(data)
                )
                await f_out.write(header)
                await f_out.write(data)
                # 记录新位置
                new_index[chapter_id] = {
                    "offset": current_offset + self.RECORD_HEADER_SIZE,
                    "size": len(data),
                    "deleted": False,
                    "timestamp": ts,
                }
                current_offset += self.RECORD_HEADER_SIZE + len(data)

        # 第三步:原子替换原文件(同文件系统下 rename 是原子的)
        shutil.move(str(temp_path), str(self.data_path))

        # 第四步:更新内存索引并持久化
        self._index = new_index
        await self._save_index()

        logger.info(
            f"Compaction completed for book {self.book_id}. "
            f"Kept {len(valid_records)} chapters."
        )
