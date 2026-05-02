from sqlmodel import func, select
from sqlmodel.ext.asyncio.session import AsyncSession

from app.core.error_handler import AppError
from app.enum.enum import RoleEnum
from app.middleware.logging import logger
from app.models.sql.admin import AuditBook, AuditBookChapter
from app.models.sql.right import Role, UserRole
from app.models.sql.user import User
from app.services.email_service import email_sender


class AuditService:
    @staticmethod
    async def allocate_auditor(book_id: int, database: AsyncSession):
        """
        分配审核人员
        Args:
          book_id: int
          database: AsyncSession
        """
        # 选择当前关联书籍最少的审核人员
        statement = (
            select(
                User.id,
                User.email,
                func.count(AuditBook.book_id).label(
                    "book_count"
                ),  # 计数关联表ID无记录时为0
            )
            .select_from(User)  # 明确指定主表
            .outerjoin(AuditBook, User.id == AuditBook.user_id)  # 左连接保留无任务用户
            .join(UserRole, User.id == UserRole.user_id)  # 内连接必须是审核员
            .join(Role, UserRole.role_id == Role.id)
            .where(Role.code == RoleEnum.REVIEWER)
            .group_by(User.id, User.email)  # Group By 需要包含所有非聚合列
            .order_by(func.count(AuditBook.book_id).asc())  # 显式升序排列
            .limit(1)
        )
        result = await database.exec(statement)
        user_id, user_email, _ = result.first()
        if user_id is None:
            raise AppError(message="当前系统没有内容审核员", status_code=500)
        audit_book = AuditBook(book_id=book_id, user_id=user_id)
        database.add(audit_book)
        try:
            await database.commit()
            email_sender.send_email(
                to_emails=[user_email],
                subject="[内容审核]新增书籍",
                body="有新的书籍添加!",
            )
        except Exception as e:
            logger.error(e)
            raise AppError(message="服务器错误") from e
        return user_id

    @staticmethod
    async def allocate_book_chapter_auditor(
        chapter_id: int, user_id: int, database: AsyncSession
    ):
        audit_chapter = AuditBookChapter(book_chapter_id=chapter_id, user_id=user_id)
        try:
            database.add(audit_chapter)
            await database.commit()
        except Exception as e:
            logger.error(e)
            raise AppError(message="分配审核员发生错误", status_code=500) from e

    @staticmethod
    async def check_book_auditor(book_id: int, database: AsyncSession):
        """
        检查书籍是否关联审核人员,如果没有则分配一个
        Args:
          book_id: int
          database: AsyncSession
        """
        statement = select(AuditBook.user_id).where(AuditBook.book_id == book_id)
        result = await database.exec(statement)
        related_auditor = result.first()
        if related_auditor is None:
            related_auditor = await AuditService.allocate_auditor(
                book_id=book_id, database=database
            )
        return related_auditor
