package com.qianrenni.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import net.jpountz.lz4.LZ4Factory
import org.msgpack.core.MessagePack
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.*
import kotlin.io.path.exists

class ChapterStoreServiceNew(
    bookId: Int,
    baseDir: String,
) {
    // 二进制记录头格式：小端字节序
    // I: uint32 (chapterId, 4字节)
    // I: uint32 (contentSize, 4字节)
    // I: uint32 (originalSize, 4字节)
    // ?: bool (isDelete, 1字节)
    // ?: bool (isCompress,1字节)
    // Q: uint64 (timestamp 微秒级, 8字节)
    // 总计: 4+4+4+1+1+8 = 22 字节
    private val recordHeaderSize = 22
    private val dir: Path = Paths.get(baseDir, "$bookId")
    private val dataFile: File
    private val indexFile: File
    private val compressThreshold = 1024

    /**
     * 章节记录元数据
     */
    data class Record(
        val offset: Long,
        val contentSize: Int,
        val originalSize: Int,
        val isDelete: Boolean,
        val isCompress: Boolean,
        val timestamp: Long
    )

    // 内存索引：chapterId -> RecordMeta
    private val indexes = mutableMapOf<Int, Record>()
    val factory: LZ4Factory = LZ4Factory.fastestInstance()

    init {
        if (!dir.parent.exists()) {
            throw IllegalStateException("内容存储位置不存在")
        }
        dir.toFile().mkdirs()
        dataFile = dir.resolve("data.log").toFile()
        indexFile = dir.resolve("index.idx").toFile()
    }

    /**
     * 将内存中的索引持久化到 index.idx 文件(使用 msgpack 二进制格式)
     */
    private suspend fun saveIndex() {
        withContext(Dispatchers.IO) {
            val out = ByteArrayOutputStream()
            val packer = MessagePack.newDefaultPacker(out)
            packer.packArrayHeader(indexes.size)
            indexes.forEach { (chapterId, record) ->
                packer.packInt(chapterId)
                packer.packLong(record.offset)
                packer.packInt(record.contentSize)
                packer.packInt(record.originalSize)
                packer.packBoolean(record.isDelete)
                packer.packBoolean(record.isCompress)
                packer.packLong(record.timestamp)
            }
            packer.close()
            indexFile.writeBytes(out.toByteArray())

        }
    }

    /**
     * 启动时加载索引：
     * 1. 优先尝试从 index.idx 快速加载
     * 2. 若文件不存在或损坏,则回退到扫描 data.log 重建索引
     */
    suspend fun loadIndex() {
        indexes.clear()
        // 尝试从持久化索引加载(快速路径)
        if (indexFile.exists()) {
            withContext(Dispatchers.IO) {
                val data = indexFile.readBytes()
                if (data.isNotEmpty()) {
                    val unpacker = MessagePack.newDefaultUnpacker(data)
                    val mapSize = unpacker.unpackArrayHeader()
                    repeat(mapSize) {
                        val chapterId = unpacker.unpackInt()
                        indexes[chapterId] = Record(
                            offset = unpacker.unpackLong(),
                            contentSize = unpacker.unpackInt(),
                            originalSize = unpacker.unpackInt(),
                            isDelete = unpacker.unpackBoolean(),
                            isCompress = unpacker.unpackBoolean(),
                            timestamp = unpacker.unpackLong()
                        )
                    }
                }
            }
            return
        }
        // 回退：从 data.log 重建索引(慢路径)
        if (!dataFile.exists()) {
            return
        }

        withContext(Dispatchers.IO) {
            val asynchronousFileChannel = AsynchronousFileChannel.open(dataFile.toPath(), StandardOpenOption.READ)
            asynchronousFileChannel.use { channel ->
                var position = 0L
                val buffer = ByteBuffer.allocate(recordHeaderSize)
                while (isActive) {
                    buffer.clear()
                    val bytesRead = channel.read(buffer, position).get()
                    if (bytesRead < recordHeaderSize) {
                        break
                    }
                    buffer.flip()
                    val chapterId = buffer.int
                    val contentSize = buffer.int
                    val originalSize = buffer.int
                    val deleted = buffer.get() != 0.toByte()
                    val isCompress = buffer.get() != 0.toByte()
                    val timestamp = buffer.long
                    val offset = position + recordHeaderSize

                    indexes[chapterId] = Record(
                        offset = offset,
                        contentSize = contentSize,
                        originalSize = originalSize,
                        isDelete = deleted,
                        isCompress = isCompress,
                        timestamp = timestamp
                    )
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
    private suspend fun appendRecord(
        filePath: Path = dataFile.toPath(),
        chapterId: Int,
        content: String,
        deleted: Boolean = false
    ) {
        val ts = System.currentTimeMillis() // 时间戳
        var data = content.toByteArray(Charsets.UTF_8)
        val originalSize = data.size
        val isCompress = if (data.size > compressThreshold) 1.toByte() else 0.toByte()
        var compressLength: Int?
        if (isCompress == 1.toByte()) {
            val compressor = factory.fastCompressor()
            val tempArray = ByteArray(compressor.maxCompressedLength(data.size))
            compressLength = compressor.compress(data, 0, data.size, tempArray, 0, tempArray.size)
            data = tempArray.copyOf(compressLength)
        }
        // 打包二进制头(小端字节序)
        val header = ByteBuffer.allocate(recordHeaderSize).apply {
            putInt(chapterId)
            putInt(data.size)
            putInt(originalSize)
            put(if (deleted) 1.toByte() else 0.toByte())
            put(isCompress)
            putLong(ts)
            flip()
        }
        var offset = 0L
        withContext(Dispatchers.IO) {
            // 注意：AsynchronousFileChannel 不支持 APPEND 选项
            // 需要先获取文件大小,然后手动定位到末尾写入
            val channel = AsynchronousFileChannel.open(
                filePath,
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

        // 更新内存索引
        indexes[chapterId] = Record(
            offset = offset + recordHeaderSize,
            contentSize = data.size,
            originalSize = originalSize,
            isDelete = deleted,
            isCompress = isCompress == 1.toByte(),
            timestamp = ts
        )
        // 持久化索引
        saveIndex()
    }

    /**
     * 读取指定章节内容。
     * 若章节不存在或已被删除,抛出异常
     */
    suspend fun readChapter(chapterId: Int): String {
        val meta = indexes[chapterId]
        meta?.let {
            if (it.isDelete) {
                return ""
            }
            return withContext(Dispatchers.IO) {
                val channel = AsynchronousFileChannel.open(dataFile.toPath(), StandardOpenOption.READ)
                channel.use { channel ->
                    val buffer = ByteBuffer.allocate(meta.contentSize)
                    val bytesRead = channel.read(buffer, it.offset).get()
                    if (bytesRead <= 0) {
                        throw IllegalStateException("Unexpected end of file while reading chapter $chapterId")
                    }
                    buffer.flip()
                    var targetArray = buffer.array()
                    if (it.isCompress) {
                        val deCompressor = factory.fastDecompressor()
                        val restoreArray = ByteArray(it.originalSize)
                        deCompressor.decompress(buffer.array(), 0, restoreArray, 0, it.originalSize)
                        targetArray = restoreArray
                    }
                    String(targetArray, Charsets.UTF_8)
                }
            }
        }
        return ""
    }

    /**
     * 更新现有章节内容。
     */
    suspend fun update(chapterId: Int, content: String) {
        appendRecord(chapterId = chapterId, content = content, deleted = false)
    }

    /**
     * 删除章节(逻辑删除)。
     * 幂等操作：多次删除无副作用。
     */
    suspend fun delete(chapterId: Int) {
        val meta = indexes[chapterId]
        meta?.let {
            if (it.isDelete) {
                return
            }
        }
        // 写入一个空内容的删除标记
        appendRecord(chapterId = chapterId, content = "", deleted = true)
    }

    /**
     * 返回所有有效章节 ID 列表(按 chapterId 升序)。
     */
    fun toList(): List<Int> {
        return indexes
            .filter { !it.value.isDelete }
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
        if (!dataFile.exists()) {
            return
        }
        // 第一步：收集所有有效章节内容
        val validRecords = mutableListOf<Pair<Int, String>>()
        for ((chapterId, record) in indexes) {
            if (!record.isDelete) {
                val content = readChapter(chapterId)
                validRecords.add(chapterId to content)
            }
        }
        // 第二步：写入临时文件
        val tempPath = dir.resolve("data.log.tmp")
        val newIndex = mutableMapOf<Int, Record>()
        var currentOffset = 0L

        withContext(Dispatchers.IO) {
            val asynchronousFileChannel = AsynchronousFileChannel.open(
                tempPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE
            )
            asynchronousFileChannel.use { channel ->
                for ((chapterId, content) in validRecords) {
                    val ts = System.currentTimeMillis()
                    var data = content.toByteArray(Charsets.UTF_8)
                    val originalSize = data.size
                    val isCompress = if (data.size > compressThreshold) 1.toByte() else 0.toByte()
                    var compressLength: Int?
                    if (isCompress == 1.toByte()) {
                        val compressor = factory.fastCompressor()
                        val tempArray = ByteArray(compressor.maxCompressedLength(data.size))
                        compressLength = compressor.compress(data, 0, data.size, tempArray, 0, tempArray.size)
                        data = tempArray.copyOf(compressLength)
                    }
                    val header = ByteBuffer.allocate(recordHeaderSize).apply {
                        putInt(chapterId)
                        putInt(data.size)
                        putInt(originalSize)
                        put(0.toByte()) // not deleted
                        put(isCompress)
                        putLong(ts)
                        flip()
                    }

                    channel.write(header, currentOffset).get()
                    val contentOffset = currentOffset + recordHeaderSize

                    val contentBuffer = ByteBuffer.wrap(data)
                    channel.write(contentBuffer, contentOffset).get()

                    newIndex[chapterId] = Record(
                        offset = contentOffset,
                        contentSize = data.size,
                        originalSize = originalSize,
                        isDelete = false,
                        isCompress = isCompress == 1.toByte(),
                        timestamp = ts
                    )
                    currentOffset += recordHeaderSize + data.size
                }
            }
        }

        // 第三步：原子替换原文件
        withContext(Dispatchers.IO) {
            Files.move(tempPath, dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }
        // 第四步：更新内存索引并持久化
        indexes.clear()
        indexes.putAll(newIndex)
        saveIndex()
    }
}