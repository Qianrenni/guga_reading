import tempfile
from pathlib import Path

import pytest

from app.core.database import close_redis, init_redis
from app.core.error_handler import AppError
from app.services.chapter_store_service import ChapterStore


@pytest.fixture(autouse=True)
async def clean_test_keys():
    """每次测试前后清理 test: 开头的 key"""
    await init_redis()
    yield
    await close_redis()


@pytest.mark.asyncio
async def test_chapter_store_initialization():
    """测试ChapterStore的初始化功能"""
    with tempfile.TemporaryDirectory() as temp_dir:
        store = ChapterStore(book_id=1, base_dir=Path(temp_dir))

        assert store.book_id == 1
        assert store.dir == Path(temp_dir) / "1"
        assert store.dir.exists()
        assert store.data_path == store.dir / "data.log"
        assert store.index_path == store.dir / "index.idx"
        assert isinstance(store._index, dict)
        assert len(store._index) == 0  # 初始化时空索引


@pytest.mark.asyncio
async def test_create_and_read_chapter():
    """测试创建和读取章节功能"""
    with tempfile.TemporaryDirectory() as temp_dir:
        store = ChapterStore(book_id=1, base_dir=Path(temp_dir))
        await store._load_index()  # 加载索引

        chapter_id = 1
        content = "这是第一章的内容"

        # 创建章节
        await store.create_chapter(chapter_id, content)

        # 读取章节
        result = await store.read_chapter(chapter_id)
        assert result == content


@pytest.mark.asyncio
async def test_update_chapter():
    """测试更新章节功能"""
    with tempfile.TemporaryDirectory() as temp_dir:
        store = ChapterStore(book_id=1, base_dir=Path(temp_dir))
        await store._load_index()  # 加载索引

        chapter_id = 1
        original_content = "原始内容"
        updated_content = "更新后的内容"

        # 创建章节
        await store.create_chapter(chapter_id, original_content)
        assert await store.read_chapter(chapter_id) == original_content

        # 更新章节
        await store.update_chapter(chapter_id, updated_content)
        assert await store.read_chapter(chapter_id) == updated_content


@pytest.mark.asyncio
async def test_delete_chapter():
    """测试删除章节功能"""
    with tempfile.TemporaryDirectory() as temp_dir:
        store = ChapterStore(book_id=1, base_dir=Path(temp_dir))
        await store._load_index()  # 加载索引

        chapter_id = 1
        content = "待删除的内容"

        # 创建章节
        await store.create_chapter(chapter_id, content)
        result = await store.read_chapter(chapter_id)
        assert result == content
        # 删除章节
        await store.delete_chapter(chapter_id)
        with pytest.raises(AppError):
            result = await store.read_chapter(chapter_id)

        # 验证章节列表中不包含已删除的章节
        chapters = await store.list_chapters()
        assert chapter_id not in chapters


@pytest.mark.asyncio
async def test_list_chapters():
    """测试列出章节功能"""
    with tempfile.TemporaryDirectory() as temp_dir:
        store = ChapterStore(book_id=1, base_dir=Path(temp_dir))
        await store._load_index()  # 加载索引

        # 创建多个章节
        chapters_data = {
            3: "第三章",
            1: "第一章",
            2: "第二章",
        }

        for chapter_id, content in chapters_data.items():
            await store.create_chapter(chapter_id, content)

        # 验证章节列表(应该按ID升序排列)
        chapter_list = await store.list_chapters()
        assert chapter_list == [1, 2, 3]

        # 删除一个章节,再次验证
        await store.delete_chapter(2)
        chapter_list = await store.list_chapters()
        assert chapter_list == [1, 3]


@pytest.mark.asyncio
async def test_create_existing_chapter_raises_error():
    """测试创建已存在的章节时抛出异常"""
    with tempfile.TemporaryDirectory() as temp_dir:
        store = ChapterStore(book_id=1, base_dir=Path(temp_dir))
        await store._load_index()  # 加载索引

        chapter_id = 1
        content = "章节内容"

        # 创建章节
        await store.create_chapter(chapter_id, content)

        # 尝试再次创建相同章节应该抛出异常
        with pytest.raises(AppError):
            await store.create_chapter(chapter_id, "新内容")


@pytest.mark.asyncio
async def test_update_nonexistent_chapter_raises_error():
    """测试更新不存在的章节时抛出异常"""
    with tempfile.TemporaryDirectory() as temp_dir:
        store = ChapterStore(book_id=1, base_dir=Path(temp_dir))
        await store._load_index()  # 加载索引

        chapter_id = 1
        content = "章节内容"

        # 尝试更新不存在的章节应该抛出异常
        with pytest.raises(AppError):
            await store.update_chapter(chapter_id, content)


@pytest.mark.asyncio
async def test_read_nonexistent_chapter_returns_empty():
    """测试读取不存在的章节返回空字符串"""
    with tempfile.TemporaryDirectory() as temp_dir:
        store = ChapterStore(book_id=1, base_dir=Path(temp_dir))
        await store._load_index()  # 加载索引

        # 读取不存在的章节应该返回空字符串
        with pytest.raises(AppError):
            await store.read_chapter(999)


@pytest.mark.asyncio
async def test_multiple_books_isolation():
    """测试不同书籍之间的数据隔离"""
    with tempfile.TemporaryDirectory() as temp_dir:
        store1 = ChapterStore(book_id=1, base_dir=Path(temp_dir))
        store2 = ChapterStore(book_id=2, base_dir=Path(temp_dir))
        await store1._load_index()  # 加载索引
        await store2._load_index()  # 加载索引

        chapter_id = 1
        content1 = "书籍1的章节"
        content2 = "书籍2的章节"

        # 在不同书籍中创建相同ID的章节
        await store1.create_chapter(chapter_id, content1)
        await store2.create_chapter(chapter_id, content2)

        # 验证数据隔离
        assert await store1.read_chapter(chapter_id) == content1
        assert await store2.read_chapter(chapter_id) == content2


@pytest.mark.asyncio
async def test_compact_removes_deleted_chapters():
    """测试压缩功能可以移除已删除的章节"""
    with tempfile.TemporaryDirectory() as temp_dir:
        store = ChapterStore(book_id=1, base_dir=Path(temp_dir))
        await store._load_index()  # 加载索引

        # 创建一些章节
        await store.create_chapter(1, "第一章")
        await store.create_chapter(2, "第二章")
        await store.create_chapter(3, "第三章")

        # 删除中间的章节
        await store.delete_chapter(2)

        # 验证章节确实被标记为删除
        with pytest.raises(AppError):
            await store.read_chapter(2)
        chapters_before = await store.list_chapters()
        assert 2 not in chapters_before

        # 执行压缩
        await store.compact()

        # 验证压缩后的状态
        chapters_after = await store.list_chapters()
        assert 2 not in chapters_after
        assert set(chapters_after) == {1, 3}
        assert await store.read_chapter(1) == "第一章"
        assert await store.read_chapter(3) == "第三章"


@pytest.mark.asyncio
async def test_index_persistence():
    """测试索引持久化功能"""
    with tempfile.TemporaryDirectory() as temp_dir:
        # 创建第一个实例并添加数据
        store1 = ChapterStore(book_id=1, base_dir=Path(temp_dir))
        await store1._load_index()  # 加载索引

        await store1.create_chapter(1, "第一章")
        await store1.create_chapter(2, "第二章")

        # 验证数据存在
        assert await store1.read_chapter(1) == "第一章"
        assert await store1.read_chapter(2) == "第二章"

        # 关闭第一个实例
        del store1

        # 创建第二个实例,使用相同的目录和书ID
        store2 = ChapterStore(book_id=1, base_dir=Path(temp_dir))
        await store2._load_index()  # 重新加载索引

        # 验证数据仍然存在
        assert await store2.read_chapter(1) == "第一章"
        assert await store2.read_chapter(2) == "第二章"

        # 验证章节列表
        chapters = await store2.list_chapters()
        assert set(chapters) == {1, 2}
