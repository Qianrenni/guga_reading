from re import compile

from fastapi import BackgroundTasks
from sqlalchemy.exc import NoResultFound
from sqlalchemy.sql.functions import count
from sqlmodel import or_, select
from sqlmodel.ext.asyncio.session import AsyncSession

from app.core.config import SETTING
from app.models.sql.book import Book
from app.models.sql.book_chapter import BookChapter, BookChapterBase, BookChapterDraft
from app.schema.book import BookCatalogItemResponseModel
from app.services.cache_service import cache, cache_get, cache_set
from app.services.chapter_store_service import ChapterStore
from app.utils.codec import PydanticCodec, PydanticListCodec
from app.utils.sort import Reverse

key_exclude_pattern = compile(r"[^a-z0-9\u4e00-\u9fa5\s]")


def escape_like_pattern(value: str) -> str:
    """
    转义 SQL LIKE 中的特殊字符:% _ \
    """
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")


def normalize_search_keyword(keyword: str, max_length: int = 50) -> str:
    """
    标准化搜索关键字:
    - 去除首尾空白
    - 转为小写(可选,根据业务需求)
    - 限制长度
    - 只保留字母、数字、中文、空格(防止特殊字符污染缓存 key)
    """
    if not keyword or not isinstance(keyword, str):
        return ""

    # 限制长度
    if len(keyword) > max_length:
        keyword = keyword[:max_length]

    # 去除首尾空白
    keyword = keyword.strip()
    if not keyword:
        return ""

    # 可选:转小写(提升缓存命中率)
    keyword = keyword.lower()

    # 只保留安全字符:字母、数字、中文、空格
    # 根据业务需求调整正则(如需支持英文标点可扩展)
    keyword = key_exclude_pattern.sub("", keyword)

    # 合并多个空格为一个,并去除首尾空格
    keyword = " ".join(keyword.split())

    return keyword


class BookService:
    @staticmethod
    async def write_temp_book_chapter(
        book_id: int,
        chapter_id: int,
        content: str,
    ):
        """
        :param book_id: 图书ID
        :param chapter_id: 章节ID
        :param content:章节内容
        """
        temp_chapter_store = ChapterStore(
            book_id, SETTING.CONTENT_TEMP_DIR_PATH / "book"
        )
        await temp_chapter_store._load_index()
        await temp_chapter_store.create_chapter(chapter_id, content)

    @staticmethod
    async def update_temp_book_chapter(
        book_id: int,
        chapter_id: int,
        content: str,
    ):
        """
        更新临时图书章节内容
        :param book_id: 图书ID
        :param chapter_id: 章节ID
        :param content: 章节内容
        """
        temp_chapter_store = ChapterStore(
            book_id, SETTING.CONTENT_TEMP_DIR_PATH / "book"
        )
        await temp_chapter_store._load_index()
        await temp_chapter_store.update_chapter(chapter_id, content)

    @staticmethod
    async def delete_temp_book_chapter(
        book_id: int,
        chapter_id: int,
    ):
        """
        删除临时图书章节内容
        :param book_id: 图书ID
        :param chapter_id: 章节ID
        """
        temp_chapter_store = ChapterStore(
            book_id, SETTING.CONTENT_TEMP_DIR_PATH / "book"
        )
        await temp_chapter_store._load_index()
        await temp_chapter_store.delete_chapter(chapter_id)

    @staticmethod
    async def read_temp_book_chapter(book_id: int, chapter_id: int) -> str:
        """
        读取临时图书章节内容
        :param book_id: 图书ID
        :param chapter_id: 章节ID
        return: str章节内容
        """
        temp_chapter_store = ChapterStore(
            book_id, SETTING.CONTENT_TEMP_DIR_PATH / "book"
        )
        await temp_chapter_store._load_index()
        return await temp_chapter_store.read_chapter(chapter_id)

    @staticmethod
    @cache(expire=SETTING.BOOK_CACHE_EXPIRE)
    async def book_chapter_read_from_file(book_id: int, chapter_id: int) -> str:
        """
        读取章节内容
        :param book_id: 图书ID
        :param chapter_id: 章节ID
        return: str章节内容
        """
        chapter_store = ChapterStore(book_id)
        await chapter_store._load_index()
        content = await chapter_store.read_chapter(chapter_id)
        return content

    @staticmethod
    @cache(expire=SETTING.BOOK_CACHE_EXPIRE, exclude_kwargs=["database"])
    async def get_category(database: AsyncSession) -> list[str]:
        """
        获取图书分类
        return: list[str]分类列表
        """
        statement = select(Book.category).distinct()
        result = await database.exec(statement)
        categories = result.all()
        return [str(category) for category in categories]

    @staticmethod
    @cache(
        expire=SETTING.BOOK_CACHE_EXPIRE,
        exclude_kwargs=["database"],
        key_prefix="get_book_by_id",
        codec=PydanticCodec(Book),
    )
    async def get_book_by_id(book_id: int, database: AsyncSession) -> Book:
        """
        获取图书信息
        :param book_id: 图书ID
        :param database:    数据库会话
        :return:    图书信息
        """
        try:
            statement = select(Book).where(Book.id == book_id)
            result = await database.exec(statement)
            book = result.one_or_none()
            if book:
                book.cover = f"{SETTING.SERVER_URL}/static/book/{book.id}/{book.cover}"
                return book
            else:
                raise ValueError("图书不存在")
        except NoResultFound as e:
            raise ValueError("图书不存在") from e

    @staticmethod
    async def get_book_by_list(
        book_ids: list[int], database: AsyncSession, background_tasks: BackgroundTasks
    ) -> list[Book]:
        """
        获取图书信息

        :param book_ids:  图书ID列表
        :param database:        数据库会话
        :param background_tasks: 后台任务
        :return:         图书信息
        """
        if not book_ids:
            return []
        miss_book_ids = []
        book_list = []
        for book_id in book_ids:
            try:
                result = await cache_get(
                    args=[],
                    kwargs={"book_id": book_id},
                    key_prefix="get_book_by_id",
                    codec=PydanticCodec(Book),
                )
                if result:
                    book_list.append(result)
                else:
                    miss_book_ids.append(book_id)
            except Exception as e:
                raise ValueError("获取图书信息失败") from e
        if miss_book_ids:
            statement = select(Book).where(Book.id.in_(miss_book_ids))
            result = await database.exec(statement)
            books = result.all()
            for book in books:
                book.cover = f"{SETTING.SERVER_URL}/static/book/{book.id}/{book.cover}"
            _ = [
                background_tasks.add_task(
                    cache_set,
                    args=[],
                    kwargs={"book_id": book.id},
                    key_prefix="get_book_by_id",
                    value=book,
                    expire=SETTING.BOOK_CACHE_EXPIRE,
                    codec=PydanticCodec(Book),
                )
                for book in books
            ]
            book_list.extend(books)
        return book_list

    @staticmethod
    @cache(
        expire=SETTING.BOOK_CACHE_EXPIRE,
        exclude_kwargs=["database"],
        codec=PydanticListCodec(BookCatalogItemResponseModel),
    )
    async def get_book_toc_by_id(
        book_id: int, database: AsyncSession
    ) -> list[BookCatalogItemResponseModel]:
        """
        获取图书目录
        :param book_id: 图书ID
        :param database:      数据库会话
        :return:       图书目录
        """
        statement = select(BookChapter.id, BookChapter.title).where(
            BookChapter.book_id == book_id
        )
        result = await database.exec(statement)
        toc = result.all()
        return [
            BookCatalogItemResponseModel(id=chapter_id, title=title)
            for chapter_id, title in toc
        ]

    @staticmethod
    @cache(
        expire=SETTING.BOOK_CACHE_EXPIRE,
        exclude_kwargs=["database"],
        codec=PydanticListCodec(BookChapterBase),
    )
    async def get_catelog_by_id(
        book_id: int,
        database: AsyncSession,
        chapter_id: int = -1,
    ) -> list[BookChapterBase]:
        """
        获取图书目录,如果chapter_id=-1则返回所有章节,否则返回指定章节
        :param book_id: 图书ID
        :param database:      数据库会话
        :return:       list[BookChapter]
        """
        if chapter_id == -1:
            statement = select(
                BookChapter.id,
                BookChapter.title,
                BookChapter.sort_order,
                BookChapter.word_count,
                BookChapter.created_at,
                BookChapter.updated_at,
            ).where(BookChapter.book_id == book_id)
        else:
            statement = select(
                BookChapter.id,
                BookChapter.title,
                BookChapter.sort_order,
                BookChapter.word_count,
                BookChapter.created_at,
                BookChapter.updated_at,
            ).where(BookChapter.id == chapter_id)
        result = await database.exec(statement)
        return [
            BookChapterBase(
                id=item[0],
                title=item[1],
                sort_order=item[2],
                word_count=item[3],
                created_at=item[4],
                updated_at=item[5],
            )
            for item in result.all()
        ]

    @staticmethod
    @cache(expire=SETTING.BOOK_CACHE_EXPIRE, exclude_kwargs=["database"])
    async def get_book_chapter_by_id(chapter_id: int, database: AsyncSession) -> str:
        """
        获取图书章节内容

        :param chapter_id:    章节ID
        :param database:         数据库会话
        :return:          章节内容
        """
        statement = select(BookChapter.content).where(BookChapter.id == chapter_id)
        result = await database.exec(statement)
        chapter = result.one_or_none()
        return str(chapter)

    @staticmethod
    @cache(expire=SETTING.BOOK_CACHE_EXPIRE, exclude_kwargs=["database"])
    async def get_book_chapter_by_index(
        book_id: int,
        chapter_index: int,
        database: AsyncSession,
    ) -> str:
        """
        获取图书章节内容
        :param book_id:        图书ID
        :param chapter_index:     章节索引,从1开始
        :param database:             数据库会话
        :return:             章节内容
        """
        statement = (
            select(BookChapter.id)
            .where(BookChapter.book_id == book_id)
            .order_by(BookChapter.sort_order)
            .limit(1)
            .offset(chapter_index - 1)
        )
        result = await database.exec(statement)
        chapter_id = result.one_or_none()
        if chapter_id is not None:
            return await BookService.book_chapter_read_from_file(
                book_id=book_id, chapter_id=chapter_id
            )
        return ""

    @staticmethod
    @cache(expire=SETTING.BOOK_CACHE_EXPIRE, exclude_kwargs=["database"])
    async def get_books_total_count(
        database: AsyncSession,
    ) -> int:
        """
        获取图书总数
        :param database:      数据库会话
        :return:              图书总数
        """
        statement = select(count(Book.id))
        result = await database.exec(statement)
        total = result.one_or_none()
        return total if total else 0

    @staticmethod
    @cache(
        expire=SETTING.BOOK_CACHE_EXPIRE,
        exclude_kwargs=["database"],
        codec=PydanticListCodec(Book),
    )
    async def search_book(
        *,
        keyword: str,
        database: AsyncSession,
    ) -> list[Book]:
        """
        搜索图书
        :param keyword:   搜索关键字
        :param database:      数据库会话
        :return:              图书列表
        """
        normalized_keyword = normalize_search_keyword(keyword)
        if not normalized_keyword:
            return []
        # 2. 转义 LIKE 通配符,防止意外匹配或注入
        escaped_keyword = escape_like_pattern(normalized_keyword)
        pattern = f"{escaped_keyword}%"

        # 3. 使用参数化查询(SQLAlchemy 自动处理参数绑定)
        statement = select(Book).where(
            or_(
                Book.name.like(pattern, escape="\\"),
                Book.author.like(pattern, escape="\\"),
            )
        )

        # 4. 执行查询
        result = await database.exec(statement)
        books = result.all()
        for book in books:
            book.cover = f"{SETTING.SERVER_URL}/static/book/{book.id}/{book.cover}"
        # 5. 返回序列化结果
        return list(books)

    @staticmethod
    @cache(
        expire=SETTING.BOOK_CACHE_EXPIRE,
        exclude_kwargs=["database"],
        codec=PydanticListCodec(Book),
    )
    async def get_books_by_category(
        *,
        database: AsyncSession,
        category: str,
    ) -> list[Book]:
        """
        获取图书列表
        :param database:      数据库会话
        :param category:         分类
        :return:               图书列表
        """
        statement = select(Book).where(Book.category == category)
        result = await database.exec(statement)
        books = result.all()
        for book in books:
            book.cover = f"{SETTING.SERVER_URL}/static/book/{book.id}/{book.cover}"
        return list(books)

    @staticmethod
    async def get_book_select(
        *,
        database: AsyncSession,
        limit: int,
        offset: int,
        category: str,
        sorted_condition: dict[str, int],
    ) -> list[Book]:
        """
        获取图书列表
        :param database:      数据库会话
        :param limit:           每页数量
        :param offset:           偏移量
        :param category:         分类
        :param sorted_condition 排序条件
        :return:               图书列表
        """
        books = await BookService.get_books_by_category(
            database=database, category=category
        )

        def sort_key(book: Book):
            key_parts = []
            for field, order in sorted_condition.items():
                val = getattr(book, field)

                # 统一处理 None:我们希望 None 始终排在最后
                if val is None:
                    # 用一个极大对象表示 "最后"
                    val = (1, None)  # (优先级=1, 值)
                else:
                    val = (0, val)  # (优先级=0, 值)

                # 如果是降序(-1),用 Reverse 包装整个 (0, val)
                if order == -1:
                    val = Reverse(val)

                key_parts.append(val)

            return tuple(key_parts)

        sorted_books = sorted(books, key=sort_key)
        return sorted_books[offset : offset + limit]

    @staticmethod
    @cache(expire=SETTING.BOOK_CACHE_EXPIRE, exclude_kwargs=["database"])
    async def get_category_count(
        *,
        database: AsyncSession,
        category: str,
    ):
        """
        获取图书数量
        :param database:      数据库会话
        :param category:         分类
        :return:               图书数量
        """
        statement = select(count(Book.id)).where(Book.category == category)
        result = await database.exec(statement)
        book_ids = result.one_or_none()
        return book_ids if book_ids else 0

    @staticmethod
    async def get_book_chapter_draft(
        book_id: int, database: AsyncSession, chapter_id: int = -1
    ) -> list[BookChapterDraft]:
        """
        获取图书章节草稿,如果chapter_id=-1则返回所有草稿
        :param book_id:       图书ID
        :param database:      数据库会话
        :param chapter_id:     章节ID
        :return:              图书章节草稿
        """

        if chapter_id != -1:
            statement = select(BookChapterDraft).where(
                BookChapterDraft.id == chapter_id
            )
        else:
            statement = select(BookChapterDraft).where(
                BookChapterDraft.book_id == book_id
            )
        result = await database.exec(statement)
        book_chapter_drafts = result.all()
        return list(book_chapter_drafts)
