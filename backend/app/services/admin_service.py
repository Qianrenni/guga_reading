from sqlmodel import select
from sqlmodel.ext.asyncio.session import AsyncSession

from app.core.config import SETTING
from app.models.sql.admin import AuditBook, AuditBookChapter
from app.models.sql.book import Book
from app.models.sql.book_chapter import BookChapter
from app.models.sql.user import FullUser


class AdminService:
    @staticmethod
    async def get_audit_book(
        database: AsyncSession,
        user: FullUser,
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
        result = await database.exec(statement)
        books = list(result.all())
        for book in books:
            book.cover = f"{SETTING.SERVER_URL}/static/book/{book.id}/{book.cover}"
        return books

    @staticmethod
    async def get_audit_book_chapter(
        database: AsyncSession,
        user: FullUser,
    ):
        """
        获取审核中的书章节
        Args:
            database: 数据库会话
            user: 用户信息
        Returns:
            list[BookChapter]: 书章节列表
        """
        statement = (
            select(BookChapter)
            .join(AuditBookChapter, BookChapter.id == AuditBookChapter.book_chapter_id)
            .where(AuditBookChapter.user_id == user.id)
        )
        result = await database.exec(statement)
        return list(result.all())
