import os

import aiofiles
from fastapi import BackgroundTasks, UploadFile
from sqlmodel import select
from sqlmodel.ext.asyncio.session import AsyncSession

from app.core.config import SETTING
from app.core.database import get_session_context
from app.core.error_handler import AppError
from app.middleware.logging import logger
from app.models.sql.author import AuthorBook
from app.models.sql.book import Book
from app.models.sql.book_chapter import BookChapter
from app.models.sql.statistics import ChapterReadStatistics
from app.models.sql.user import FullUser
from app.services.book_service import BookService
from app.services.cache_service import cache
from app.utils.codec import PydanticListCodec


async def save_book_cover(cover_file: UploadFile | None, book_id: int):
    """
    保存书籍封面
    :param cover_file: 封面文件
    :param book_draft_id: 书籍id
    :param is_created: 是否新建
    :return: None
    """
    statement = select(Book).where(Book.id == book_id)

    async with get_session_context() as database:
        try:
            result = await database.exec(statement)
            book = result.first()
            book_dir = SETTING.STATIC_TEMP_DIR_PATH / "book" / f"{book.id}"
            cover_path = book_dir / "cover.webp"
            book_dir.mkdir(parents=True, exist_ok=True)
            async with aiofiles.open(cover_path, "wb") as f:
                await f.write(await cover_file.read())
                logger.info(f"保存书籍封面成功: {cover_path}")
            book.cover = f"{cover_path}"
            database.add(book)
            await database.commit()
            logger.info("更新书籍封面成功")
        except Exception as e:
            logger.error(e, exc_info=True)


async def delete_book_cover(id: int):
    """
    删除书籍封面
    :param id: 书籍id
    :return: None
    """
    book_dir = SETTING.STATIC_TEMP_DIR_PATH / "book" / f"{id}"
    cover_path = book_dir / "cover.webp"
    if cover_path.exists():
        cover_path.unlink()
        logger.info(f"删除书籍封面: {cover_path}")
    if len(list(book_dir.iterdir())) == 0:
        os.removedirs(book_dir)
        logger.info(f"删除空目录{book_dir}")


class AuthorBookService:
    @staticmethod
    async def is_own_book(
        database: AsyncSession,
        user: FullUser,
        id: int,
    ) -> bool:
        """
        判断书籍是否属于当前用户
        :param database: 数据库会话
        :param user: 用户
        :param id: 书籍id
        :param is_draft: 是否是草稿
        :return: bool
        """
        statement = (
            select(AuthorBook)
            .where(AuthorBook.user_id == user.id)
            .where(AuthorBook.book_id == id)
        )
        result = await database.exec(statement)
        return bool(result.first())

    @staticmethod
    async def get_author_book(
        database: AsyncSession,
        user: FullUser,
        id: int,
    ):
        """
        获取作者的书籍列表
        :param database: 数据库会话
        :param user: 用户
        :return:
        """
        statement = (
            select(Book)
            .join(AuthorBook, AuthorBook.book_id == Book.id)
            .where(AuthorBook.user_id == user.id)
        )
        if id != -1:
            statement = statement.where(AuthorBook.book_id == id)
        result = await database.exec(statement)
        return list(result.all())

    @staticmethod
    async def create_author_book(
        database: AsyncSession,
        name: str,
        author: str,
        cover: UploadFile,
        description: str,
        category: str,
        tags: str,
        background_tasks: BackgroundTasks,
        user: FullUser,
    ) -> bool:
        """
        创建作者书籍关系
        :param database: 数据库会话
        :param name: 书籍名称
        :param author: 作者
        :param cover: 封面文件
        :param description
        :param category
        :param tags
        :param background_tasks
        :param user: 用户
        :return: AuthorBook
        """

        try:
            book = Book(
                name=name,
                author=author,
                description=description,
                category=category,
                tags=tags,
            )
            database.add(book)
            await database.commit()
            await database.refresh(book)
            author_book = AuthorBook(
                user_id=user.id,
                book_id=book.id,
            )
            database.add(author_book)
            await database.commit()
            background_tasks.add_task(save_book_cover, cover, book.id, is_created=True)
            return True
        except Exception as e:
            logger.error(e)
            await database.rollback()
            raise AppError(message="创建书籍失败") from e

    @staticmethod
    async def update_author_book(
        id: int,
        database: AsyncSession,
        book: Book,
        background_task: BackgroundTasks,
        cover: UploadFile | None,
    ):
        """
        更新作者图书
        :param database:  数据库连接
        :param book:  图书信息
        :param background_task:  后台任务
        :param cover:  封面
        """
        try:
            book.parent_id = id
            database.add(book)
            await database.commit()
            if cover:
                background_task.add_task(save_book_cover, cover, book.id)
        except Exception as e:
            logger.error(e)
            await database.rollback()
            raise AppError(message="更新书籍失败") from e

    @staticmethod
    async def create_author_book_chapter(
        database: AsyncSession,
        book_id: int,
        title: str,
        content: str,
        sort_order: float,
        background_tasks: BackgroundTasks,
    ):
        """
        创建作者书籍章节
        :param database:  数据库连接
        :param book_id:  图书id
        :param title:  标题
        :param content:  内容
        :param sort_order: 排序key
        :param user:  当前用户
        :param background_tasks: 后台任务
        """
        try:
            book_chapter = BookChapter(
                book_id=book_id,
                title=title,
                sort_order=sort_order,
                word_count=len(content),
            )
            database.add(book_chapter)
            await database.commit()
            await database.refresh(book_chapter)
            temp_book_chapter_id = book_chapter.id
            background_tasks.add_task(
                BookService.update_temp_book_chapter,
                book_id,
                temp_book_chapter_id,
                content,
            )
            return True
        except Exception as e:
            logger.error(e)
            await database.rollback()
            raise AppError(message="创建书籍章节失败") from e

    @staticmethod
    async def update_author_book_chapter(
        database: AsyncSession,
        book_id: int,
        content: str,
        title: str,
        chapter_id: int,
        background_tasks: BackgroundTasks,
    ):
        """
        更新作者书籍章节
        :param database:  数据库连接
        :param book_id:  图书id
        :param sort_order: 排序key
        :param content:  内容
        :param is_draft: 是否为草稿
        :param title:  标题
        """
        book_chapter = BookChapter(
            title=title, word_count=len(content), parent_id=chapter_id
        )
        database.add(book_chapter)
        try:
            await database.commit()
            background_tasks.add_task(
                BookService.update_temp_book_chapter,
                book_id,
                book_chapter.id,
                content,
            )
        except Exception as e:
            logger.error(e)
            await database.rollback()
            raise AppError(message="更新书籍章节失败") from e

    @staticmethod
    async def get_author_book_chapter(
        database: AsyncSession, book_id: int, user: FullUser, chapter_id: int = -1
    ):
        """
        获取作者书籍章节草稿,如果chapter_id为-1则返回所有草稿
        :param database:  数据库连接
        :param book_id:  图书id
        :param user:  当前用户
        :param chapter_id:  章节id
        """
        # 获取与作者相关的所有书籍章节草稿
        statement = (
            select(BookChapter)
            .join(AuthorBook, BookChapter.book_id == AuthorBook.book_id)
            .where(AuthorBook.user_id == user.id)
        )
        if book_id != -1:
            statement = statement.where(BookChapter.book_id == book_id)
        if chapter_id != -1:
            statement = statement.where(BookChapter.id == chapter_id)
        result = await database.exec(statement)
        return list(result.all())

    @staticmethod
    @cache(
        exclude_kwargs=["database", "user"],
        codec=PydanticListCodec(ChapterReadStatistics),
        ignore_null=False,
    )
    async def get_author_book_statistics(
        database: AsyncSession, book_id: int, chapter_id: int = -1
    ):
        """
        获取作者图书统计信息
        Args:
            database (AsyncSession): 数据库会话
            book_id (int): 图书ID
            user (FullUser): 当前用户
        """
        query = select(ChapterReadStatistics).where(
            ChapterReadStatistics.book_id == book_id
        )
        if chapter_id != -1:
            query = query.where(ChapterReadStatistics.chapter_id == chapter_id)
        result = await database.exec(query)
        return list(result.all())
