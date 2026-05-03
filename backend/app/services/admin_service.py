from sqlmodel import select
from sqlmodel.ext.asyncio.session import AsyncSession

from app.enum.enum import BookStatusEnum
from app.models.sql.admin import AuditBook
from app.models.sql.author import AuthorBook
from app.models.sql.book_chapter import BookChapter
from app.models.sql.user import FullUser


class AdminService:
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
            .join(AuditBook, BookChapter.book_id == AuditBook.book_id)
            .where(AuthorBook.user_id == user.id)
            .where(
                BookChapter.status.notin_(
                    [BookStatusEnum.PENDING, BookStatusEnum.PUBLISHED]
                )
            )
        )
        result = await database.exec(statement)
        return list(result.all())
