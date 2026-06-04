package com.qianrenni.services

import com.qianrenni.config.appConfig
import com.qianrenni.utils.DistributedLock
import io.ktor.server.application.*
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.msgpack.core.MessageFormat
import org.msgpack.core.MessagePack
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 面向单本书的章节存储引擎,支持完整的 CRUD 操作。
 * 对应 Python 项目中的 app/services/chapter_store_service.py
 *
 * 设计目标:
 * - 解决海量小文件问题(1000 章 = 1 个 data.log 文件)
 * - 支持高效随机读取
 * - 支持章节更新与删除(通过追加写 + 索引)
 * - 支持垃圾回收(compaction)
 * - 全异步 I/O,适配 Ktor 协程框架
 *
 * 存储结构:
 * book/
 * └── book_{bookId}/
 *     ├── data.log   ← 所有章节记录以追加方式写入(二进制日志)
 *     └── index.idx  ← 内存索引的持久化快照(msgpack 格式)
 */
class ChapterStore(
    private val bookId: Int,
    baseDir: String? = null,
    private val application: Application
) {
    // 二进制记录头格式：小端字节序
    // I: uint32 (chapterId, 4字节)
    // Q: uint64 (timestamp 微秒级, 8字节)
    // ?: bool (deleted, 1字节)
    // I: uint32 (contentSize, 4字节)
    // 总计: 4 + 8 + 1 + 4 = 17 字节
    private val recordHeaderSize = 17

    private val dir: Path
    private val dataPath: Path
    private val indexPath: Path

    /**
     * 章节记录元数据
     */
    data class RecordMeta(
        val offset: Long,
        val size: Int,
        val deleted: Boolean,
        val timestamp: Long
    )

    // 内存索引：chapterId -> RecordMeta
    private val _index = mutableMapOf<Int, RecordMeta>()


    init {
        val base = baseDir ?: application.appConfig.bookDir
        dir = Paths.get(base, "$bookId")
        dir.toFile().mkdirs()
        dataPath = dir.resolve("data.log")
        indexPath = dir.resolve("index.idx")
    }

    /**
     * 将内存中的索引持久化到 index.idx 文件(使用 msgpack 二进制格式)
     * 注意：为兼容 Python 版本,key 使用字符串格式
     */
    private suspend fun saveIndex() {
        val lockKey = "chapter_store_save_index_$bookId"
        val lock = DistributedLock(lockKey, expireTime = 10, application = application)
        if (!lock.acquire()) {
            application.log.error("Failed to acquire lock for chapter store save index $bookId")
            return
        }
        try {
            withContext(Dispatchers.IO) {
                val out = ByteArrayOutputStream()
                val packer = MessagePack.newDefaultPacker(out)

                // 写入 map 头
                packer.packMapHeader(_index.size)
                _index.forEach { (chapterId, meta) ->
                    // 使用字符串 key 以兼容 Python 版本
                    packer.packString(chapterId.toString())
                    packer.packMapHeader(4)
                    packer.packString("offset")
                    packer.packLong(meta.offset)
                    packer.packString("size")
                    packer.packInt(meta.size)
                    packer.packString("deleted")
                    packer.packBoolean(meta.deleted)
                    packer.packString("timestamp")
                    packer.packLong(meta.timestamp)
                }
                packer.close()

                indexPath.toFile().writeBytes(out.toByteArray())
            }
        } finally {
            lock.releaseLock()
        }
    }

    /**
     * 启动时加载索引：
     * 1. 优先尝试从 index.idx 快速加载
     * 2. 若文件不存在或损坏,则回退到扫描 data.log 重建索引
     */
    suspend fun loadIndex() {
        _index.clear()

        // 尝试从持久化索引加载(快速路径)
        if (indexPath.toFile().exists()) {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    val data = indexPath.toFile().readBytes()
                    if (data.isNotEmpty()) {
                        val unpacker = MessagePack.newDefaultUnpacker(data)
                        val mapSize = unpacker.unpackMapHeader()
                        repeat(mapSize) {
                            // 兼容 Python 版本：key 是字符串
                            val keyType = unpacker.getNextFormat()
                            val chapterId = if (keyType == MessageFormat.STR32) {
                                unpacker.unpackString().toInt()
                            } else {
                                unpacker.unpackInt()
                            }
                            val metaSize = unpacker.unpackMapHeader()
                            var offset = 0L
                            var size = 0
                            var deleted = false
                            var timestamp = 0L

                            repeat(metaSize) {
                                when (unpacker.unpackString()) {
                                    "offset" -> offset = unpacker.unpackLong()
                                    "size" -> size = unpacker.unpackInt()
                                    "deleted" -> deleted = unpacker.unpackBoolean()
                                    "timestamp" -> timestamp = unpacker.unpackLong()
                                }
                            }
                            _index[chapterId] = RecordMeta(offset, size, deleted, timestamp)
                        }
                        true
                    } else {
                        false
                    }
                }
                if (loaded) return // 成功加载索引,直接返回
            } catch (e: Exception) {
                application.log.warn(
                    "Failed to load index.idx for book $bookId, falling back to data.log: ${e.message}"
                )
            }
        }

        // 回退：从 data.log 重建索引(慢路径)
        if (!dataPath.toFile().exists()) {
            return
        }

        withContext(Dispatchers.IO) {
            val channel = AsynchronousFileChannel.open(dataPath, StandardOpenOption.READ)
            channel.use { channel ->
                var position = 0L
                val buffer = ByteBuffer.allocate(recordHeaderSize).apply {
                    order(java.nio.ByteOrder.LITTLE_ENDIAN)
                }

                while (true) {
                    buffer.clear()
                    val bytesRead = channel.read(buffer, position).get()
                    if (bytesRead < recordHeaderSize) {
                        break
                    }

                    buffer.flip()
                    val chapterId = buffer.int
                    val timestamp = buffer.long
                    val deleted = buffer.get() != 0.toByte()
                    val contentSize = buffer.int

                    val offset = position + recordHeaderSize

                    _index[chapterId] = RecordMeta(offset, contentSize, deleted, timestamp)

                    position += recordHeaderSize + contentSize
                }
            }
        }

        // 重建完成后,立即持久化索引,加速下次启动
        saveIndex()
    }

    /**
     * 追加一条新记录到 data.log 末尾,并更新内存索引。
     * 所有写操作(create/update/delete)最终都调用此方法。
     */
    private suspend fun appendRecord(chapterId: Int, content: String, deleted: Boolean = false) {
        val ts = System.currentTimeMillis() * 1000 // 微秒级时间戳
        val data = content.toByteArray(Charsets.UTF_8)

        // 打包二进制头(小端字节序)
        val header = ByteBuffer.allocate(recordHeaderSize).apply {
            order(java.nio.ByteOrder.LITTLE_ENDIAN)
            putInt(chapterId)
            putLong(ts)
            put(if (deleted) 1.toByte() else 0.toByte())
            putInt(data.size)
            flip()
        }

        val lockKey = "chapter_store_append_record_$bookId"
        val lock = DistributedLock(lockKey, expireTime = 10, application = application)

        if (!lock.acquire()) {
            application.log.error("Failed to acquire lock for chapter store append record $bookId")
            return
        }

        var offset = 0L
        try {
            withContext(Dispatchers.IO) {
                // 注意：AsynchronousFileChannel 不支持 APPEND 选项
                // 需要先获取文件大小,然后手动定位到末尾写入
                val channel = AsynchronousFileChannel.open(
                    dataPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE
                )
                channel.use { channel ->
                    val fileSize = channel.size()
                    offset = fileSize

                    // 写入 header
                    channel.write(header, offset).get()
                    val contentOffset = offset + recordHeaderSize

                    // 写入 content
                    val contentBuffer = ByteBuffer.wrap(data)
                    channel.write(contentBuffer, contentOffset).get()
                }
            }
        } finally {
            lock.releaseLock()
        }

        // 更新内存索引
        _index[chapterId] = RecordMeta(
            offset = offset + recordHeaderSize,
            size = data.size,
            deleted = deleted,
            timestamp = ts
        )

        // 持久化索引
        saveIndex()
        application.log.debug("Appended record for book $bookId chapter $chapterId")
    }

    /**
     * 读取指定章节内容。
     * 若章节不存在或已被删除,抛出异常
     */
    suspend fun readChapter(chapterId: Int): String {
        val meta = _index[chapterId]
        if (meta == null || meta.deleted) {
            throw RuntimeException("Chapter not found: $chapterId")
        }

        return withContext(Dispatchers.IO) {
            val channel = AsynchronousFileChannel.open(dataPath, StandardOpenOption.READ)
            channel.use { channel ->
                val buffer = ByteBuffer.allocate(meta.size)
                val bytesRead = channel.read(buffer, meta.offset).get()
                if (bytesRead < 0) {
                    throw RuntimeException("Unexpected end of file while reading chapter $chapterId")
                }
                buffer.flip()
                String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8)
            }
        }
    }

    /**
     * 更新现有章节内容。
     */
    suspend fun updateChapter(chapterId: Int, content: String) {
        appendRecord(chapterId, content, deleted = false)
    }

    /**
     * 删除章节(逻辑删除)。
     * 幂等操作：多次删除无副作用。
     */
    suspend fun deleteChapter(chapterId: Int) {
        val meta = _index[chapterId]
        if (meta == null || meta.deleted) {
            return // 已删除或不存在,直接返回
        }
        // 写入一个空内容的删除标记
        appendRecord(chapterId, "", deleted = true)
    }

    /**
     * 创建章节
     */
    suspend fun createChapter(chapterId: Int, content: String) {
        appendRecord(chapterId, content, deleted = false)
    }

    /**
     * 返回所有有效章节 ID 列表(按 chapterId 升序)。
     */
    fun listChapters(): List<Int> {
        return _index
            .filter { !it.value.deleted }
            .keys
            .sorted()
    }

    /**
     * 执行 compaction(压缩/清理)：
     * - 仅保留未删除的最新章节
     * - 重写 data.log,移除历史版本和删除标记
     * - 减小文件体积,提升读取效率
     */
    suspend fun compact() {
        if (!dataPath.toFile().exists()) {
            return
        }

        // 第一步：收集所有有效章节内容
        val validRecords = mutableListOf<Pair<Int, String>>()
        for ((chapterId, meta) in _index) {
            if (!meta.deleted) {
                val content = readChapter(chapterId)
                validRecords.add(chapterId to content)
            }
        }

        // 第二步：写入临时文件
        val tempPath = dir.resolve("data.log.tmp")
        val newIndex = mutableMapOf<Int, RecordMeta>()
        var currentOffset = 0L

        withContext(Dispatchers.IO) {
            val channel = AsynchronousFileChannel.open(
                tempPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE
            )
            channel.use { channel ->
                for ((chapterId, content) in validRecords) {
                    val ts = System.currentTimeMillis() * 1000
                    val data = content.toByteArray(Charsets.UTF_8)

                    val header = ByteBuffer.allocate(recordHeaderSize).apply {
                        order(java.nio.ByteOrder.LITTLE_ENDIAN)
                        putInt(chapterId)
                        putLong(ts)
                        put(0.toByte()) // not deleted
                        putInt(data.size)
                        flip()
                    }

                    channel.write(header, currentOffset).get()
                    val contentOffset = currentOffset + recordHeaderSize

                    val contentBuffer = ByteBuffer.wrap(data)
                    channel.write(contentBuffer, contentOffset).get()

                    newIndex[chapterId] = RecordMeta(
                        offset = contentOffset,
                        size = data.size,
                        deleted = false,
                        timestamp = ts
                    )
                    currentOffset += recordHeaderSize + data.size
                }
            }
        }

        // 第三步：原子替换原文件
        withContext(Dispatchers.IO) {
            Files.move(tempPath, dataPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }

        // 第四步：更新内存索引并持久化
        _index.clear()
        _index.putAll(newIndex)
        saveIndex()

        application.log.info(
            "Compaction completed for book $bookId. Kept ${validRecords.size} chapters."
        )
    }
}

/**
 * ChapterStore 工厂管理器
 * 管理多个书籍的 ChapterStore 实例
 */
class ChapterStoreManager(private val application: Application) {
    private val stores = mutableMapOf<Int, ChapterStore>()
    private val lock = ReentrantLock()

    /**
     * 获取或创建指定书籍的 ChapterStore 实例
     */
    fun getStore(bookId: Int): ChapterStore {
        return lock.withLock {
            stores.getOrPut(bookId) {
                ChapterStore(bookId, application = application)
            }
        }
    }

    /**
     * 初始化指定书籍的索引(应在应用启动后调用)
     */
    suspend fun initStore(bookId: Int) {
        val store = getStore(bookId)
        store.loadIndex()
    }
}

private val ChapterStoreManagerKey = AttributeKey<ChapterStoreManager>("ChapterStoreManager")

val Application.chapterStoreManager: ChapterStoreManager
    get() = attributes[ChapterStoreManagerKey]

fun Application.configureChapterStore() {
    val manager = ChapterStoreManager(this)
    attributes.put(ChapterStoreManagerKey, manager)
}
