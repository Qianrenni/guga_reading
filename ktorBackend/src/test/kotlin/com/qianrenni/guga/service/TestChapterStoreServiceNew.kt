package com.qianrenni.guga.service

import com.qianrenni.services.ChapterStoreServiceNew
import kotlinx.coroutines.test.runTest
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.random.Random
import kotlin.test.*

class TestChapterStoreServiceNew {

    private lateinit var tempDir: java.nio.file.Path

    @BeforeTest
    fun setUp() {
        tempDir = createTempDirectory("chapter-store-new-test")
    }

    @OptIn(ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testCRUD() = runTest {
        val store = ChapterStoreServiceNew(
            bookId = 1,
            baseDir = tempDir.toString()
        )
        store.loadIndex()

        // 创建章节
        store.update(1, "第一章内容")
        store.update(2, "第二章内容")
        store.update(3, "第三章内容")

        // 读取章节
        val content1 = store.readChapter(1)
        assertEquals("第一章内容", content1)

        // 列出章节
        val chapters = store.toList()
        assertEquals(listOf(1, 2, 3), chapters)

        // 更新章节
        store.update(1, "第一章更新内容")
        assertEquals("第一章更新内容", store.readChapter(1))

        // 删除章节
        store.delete(2)
        val chaptersAfterDelete = store.toList()
        assertEquals(listOf(1, 3), chaptersAfterDelete)

        // 删除不存在的章节应返回空
        assertEquals("", store.readChapter(2))
    }

    @Test
    fun testPersistence() = runTest {
        // 第一次创建并写入
        val store1 = ChapterStoreServiceNew(
            bookId = 2,
            baseDir = tempDir.toString()
        )
        store1.loadIndex()
        store1.update(1, "持久化测试内容")
        store1.update(2, "第二章内容")

        // 重新加载,验证索引持久化
        val store2 = ChapterStoreServiceNew(
            bookId = 2,
            baseDir = tempDir.toString()
        )
        store2.loadIndex()

        val chapters = store2.toList()
        assertEquals(listOf(1, 2), chapters)
        assertEquals("持久化测试内容", store2.readChapter(1))
    }

    @Test
    fun testCompact() = runTest {
        val store = ChapterStoreServiceNew(
            bookId = 3,
            baseDir = tempDir.toString()
        )
        store.loadIndex()

        // 创建并更新多次
        store.update(1, "初始内容")
        store.update(1, "第一次更新")
        store.update(1, "第二次更新")

        // 创建并删除
        store.update(2, "临时章节")
        store.delete(2)

        // 执行压缩
        store.compact()

        // 验证压缩后数据正确
        assertEquals(listOf(1), store.toList())
        assertEquals("第二次更新", store.readChapter(1))
    }

    /**
     * 测试超过 1KB 的大内容读写,使用随机字符验证数据完整性
     */
    @Test
    fun testLargeContent() = runTest {
        val store = ChapterStoreServiceNew(
            bookId = 4,
            baseDir = tempDir.toString()
        )
        store.loadIndex()

        // 生成约 10KB 的随机内容
        val contentSize = 10 * 1024
        val randomContent = buildString {
            val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*()_+-=[]{}|;':\",./<>?~"
            repeat(contentSize) {
                append(chars[Random.nextInt(chars.length)])
            }
        }
        assertTrue(randomContent.length > 1024, "Content should be larger than 1KB")

        // 写入大内容
        store.update(1, randomContent)

        // 读取并验证
        val readBack = store.readChapter(1)
        assertEquals(randomContent, readBack)

        // 验证索引持久化后仍能正确读取
        val store2 = ChapterStoreServiceNew(
            bookId = 4,
            baseDir = tempDir.toString()
        )
        store2.loadIndex()
        val reloadedContent = store2.readChapter(1)
        assertEquals(randomContent, reloadedContent)

        // 更新为另一个大内容
        val updatedContent = buildString {
            val chars = "0123456789ABCDEF"
            repeat(contentSize) {
                append(chars[Random.nextInt(chars.length)])
            }
        }
        store.update(1, updatedContent)
        assertEquals(updatedContent, store.readChapter(1))

        // compact 后验证
        store.compact()
        assertEquals(updatedContent, store.readChapter(1))
    }
}
