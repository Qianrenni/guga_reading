package com.qianrenni.com.qianrenni.guga.service

import com.qianrenni.services.ChapterStore
import io.ktor.client.request.*
import io.ktor.server.testing.*
import kotlinx.coroutines.test.runTest
import java.nio.file.Paths
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.*

class TestChapterStoreService {

    private lateinit var tempDir: java.nio.file.Path

    @BeforeTest
    fun setUp() {
        tempDir = createTempDirectory("chapter-store-test")
    }

    @OptIn(ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testChapterStoreCRUD() = testApplication {
        configure()
        client.get("")
        val chapterStore = ChapterStore(
            bookId = 1,
            baseDir = tempDir.toString(),
            application = application
        )
        chapterStore.loadIndex()

        runTest {
            // 创建章节
            chapterStore.createChapter(1, "第一章内容")
            chapterStore.createChapter(2, "第二章内容")
            chapterStore.createChapter(3, "第三章内容")

            // 读取章节
            val content1 = chapterStore.readChapter(1)
            assertEquals("第一章内容", content1)

            // 列出章节
            val chapters = chapterStore.listChapters()
            assertEquals(listOf(1, 2, 3), chapters)

            // 更新章节
            chapterStore.updateChapter(1, "第一章更新内容")
            assertEquals("第一章更新内容", chapterStore.readChapter(1))

            // 删除章节
            chapterStore.deleteChapter(2)
            val chaptersAfterDelete = chapterStore.listChapters()
            assertEquals(listOf(1, 3), chaptersAfterDelete)

            // 删除不存在的章节应抛出异常
            try {
                chapterStore.readChapter(2)
                throw AssertionError("Should throw exception")
            } catch (e: RuntimeException) {
                assertTrue(e.message!!.contains("Chapter not found"))
            }
        }
    }

    @Test
    fun testChapterStorePersistence() = testApplication {
        configure()
        client.get("")
        // 第一次创建并写入
        val chapterStore1 = ChapterStore(
            bookId = 2,
            baseDir = tempDir.toString(),
            application = application
        )
        chapterStore1.loadIndex()

        runTest {
            chapterStore1.createChapter(1, "持久化测试内容")
            chapterStore1.createChapter(2, "第二章内容")
        }

        // 重新加载，验证索引持久化
        val chapterStore2 = ChapterStore(
            bookId = 2,
            baseDir = tempDir.toString(),
            application = application
        )
        chapterStore2.loadIndex()

        runTest {
            val chapters = chapterStore2.listChapters()
            assertEquals(listOf(1, 2), chapters)
            assertEquals("持久化测试内容", chapterStore2.readChapter(1))
        }
    }

    @Test
    fun testChapterStoreCompact() = testApplication {
        configure()
        client.get("")
        val chapterStore = ChapterStore(
            bookId = 3,
            baseDir = tempDir.toString(),
            application = application
        )
        chapterStore.loadIndex()

        runTest {
            // 创建并更新多次
            chapterStore.createChapter(1, "初始内容")
            chapterStore.updateChapter(1, "第一次更新")
            chapterStore.updateChapter(1, "第二次更新")

            // 创建并删除
            chapterStore.createChapter(2, "临时章节")
            chapterStore.deleteChapter(2)

            // 执行压缩
            chapterStore.compact()

            // 验证压缩后数据正确
            assertEquals(listOf(1), chapterStore.listChapters())
            assertEquals("第二次更新", chapterStore.readChapter(1))
        }
    }

    /**
     * 测试读取 Python 版本创建的 data.log 和 index.idx 文件
     * 验证 Kotlin 实现与 Python 实现的二进制格式兼容性
     */
    @Test
    fun testReadPythonCreatedFiles() = testApplication {
        configure()
        client.get("")

        // 使用 Python 版本创建的实际数据目录
        val pythonBookDir = Paths.get("d:\\project\\guga_reading\\backend\\store\\book").toString()

        val chapterStore = ChapterStore(
            bookId = 1,
            baseDir = pythonBookDir,
            application = application
        )
        chapterStore.loadIndex()

        runTest {
            // 列出所有章节，验证能正确读取 Python 创建的索引
            val chapters = chapterStore.listChapters()
            assertTrue(chapters.isNotEmpty(), "Should have chapters from Python-created data")

            // 尝试读取第一个章节，验证二进制格式兼容
            val firstChapterId = chapters.first()
            val content = chapterStore.readChapter(firstChapterId)
            assertTrue(content.isNotEmpty(), "Chapter content should not be empty")

            println("Python-created book 1 has ${chapters.size} chapters")
            println("First chapter ($firstChapterId) content preview: ${content.take(100)}")
        }
    }
}
