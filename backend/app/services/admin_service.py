from sqlmodel import select
from sqlmodel.ext.asyncio.session import AsyncSession

from app.core.config import SETTING
from app.core.error_handler import AppError
from app.enum.enum import BookStatusEnum
from app.middleware.logging import logger
from app.models.database.admin import AuditBook, AuditBookChapter
from app.models.database.book import Book
from app.models.database.book_chapter import BookChapter
from app.models.database.user import FullUser
from app.services.book_service import BookService


class AdminService:
    @staticmethod
    async def get_audit_book(
        database: AsyncSession, user: FullUser, book_ids: list[int]
    ):
        """
        获取审核中的书
        Args:
            database: 数据库会话
            user: 用户信息
        Returns:
            list[AuditBook]: 审核中的书列表
        """
        statement = (
            select(Book)
            .join(AuditBook, Book.id == AuditBook.book_id)
            .where(AuditBook.user_id == user.id)
        )
        if book_ids is not None:
            statement = statement.where(Book.id.in_(book_ids))
        result = await database.exec(statement)
        books = list(result.all())
        for book in books:
            book.cover = (
                f"{SETTING.SERVER_URL}/static/temp/book/{book.id}/{book.cover}"
                if book.status != BookStatusEnum.PUBLISHED
                else f"{SETTING.SERVER_URL}/static/book/{book.id}/{book.cover}"
            )
        return books

    @staticmethod
    async def update_book(
        database: AsyncSession, user: FullUser, book_id: int, is_pass: bool
    ):
        """
        更新书审核状态
        Args:
            database: 数据库会话
            user: 用户信息
            book_id: 书ID
            is_pass: 是否通过审核
        """
        statement = (
            select(Book)
            .join(AuditBook, Book.id == AuditBook.book_id)
            .where(AuditBook.user_id == user.id)
            .where(Book.id == book_id)
            .where(Book.status == BookStatusEnum.REVIEWING)
        )
        result = await database.exec(statement)
        book = result.first()
        if book:
            book.status = (
                BookStatusEnum.APPROVED if is_pass else BookStatusEnum.REJECTED
            )
            database.add(book)
            await database.commit()
        else:
            raise AppError(message="书不存在", status_code=400)

    @staticmethod
    async def get_audit_book_chapter(
        database: AsyncSession, user: FullUser, chapter_ids: list[int]
    ):
        """
        获取审核中的书章节
        Args:
            database: 数据库会话
            user: 用户信息
        Returns:
            list[BookChapter]: 书章节列表
        """
        logger.info("get_audit_book_chapter", chapter_ids)
        statement = (
            select(BookChapter)
            .join(AuditBookChapter, BookChapter.id == AuditBookChapter.book_chapter_id)
            .where(AuditBookChapter.user_id == user.id)
        )
        if chapter_ids is not None:
            statement = statement.where(BookChapter.id.in_(chapter_ids))
        result = await database.exec(statement)
        return list(result.all())

    @staticmethod
    async def update_book_chapter(
        database: AsyncSession, user: FullUser, chapter_id: int, is_pass: bool
    ):
        """
        更新书章节审核状态
        Args:
            database: 数据库会话
            user: 用户信息
            chapter_id: 书章节ID
            is_pass: 是否通过审核
        """
        statement = (
            select(BookChapter)
            .join(AuditBookChapter, BookChapter.id == AuditBookChapter.book_chapter_id)
            .where(AuditBookChapter.user_id == user.id)
            .where(BookChapter.id == chapter_id)
            .where(BookChapter.status == BookStatusEnum.REVIEWING)
        )
        result = await database.exec(statement)
        book_chapter = result.first()
        if book_chapter:
            book_chapter.status = (
                BookStatusEnum.APPROVED if is_pass else BookStatusEnum.REJECTED
            )
            database.add(book_chapter)
            await database.commit()
        else:
            raise AppError(message="章节不存在", status_code=400)

    @staticmethod
    async def get_audit_book_chapter_content(
        database: AsyncSession, user: FullUser, chapter_id: int
    ):
        """
        获取审核中的书章节内容
        Args:
            database: 数据库会话
            user: 用户信息
        Returns:
            str: 书章节内容
        """
        statement = (
            select(BookChapter)
            .join(AuditBookChapter, BookChapter.id == AuditBookChapter.book_chapter_id)
            .where(AuditBookChapter.user_id == user.id)
            .where(BookChapter.id == chapter_id)
        )
        result = await database.exec(statement)
        book_chapter = result.first()
        if book_chapter:
            content = await BookService.read_temp_book_chapter(
                book_chapter.book_id, book_chapter.id
            )
            return content
        else:
            raise AppError(message="章节不存在", status_code=400)
