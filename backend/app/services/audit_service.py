from sqlmodel import func, select
from sqlmodel.ext.asyncio.session import AsyncSession

from app.core.error_handler import AppError
from app.enum.enum import RoleEnum
from app.models.sql.admin import AuditBook
from app.models.sql.right import Role, UserRole
from app.models.sql.user import User


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
        return User(id=user_id, email=user_email)

    @staticmethod
    async def check_book_auditor(book_id: int, database: AsyncSession):
        """
        检查书籍是否关联审核人员,如果没有则分配一个
        Args:
          book_id: int
          database: AsyncSession
        """
        statement = (
            select(User.id, User.email)
            .join(AuditBook, AuditBook.user_id == User.id)
            .where(AuditBook.book_id == book_id)
        )
        result = await database.exec(statement)
        item = result.first()
        if item is None:
            related_auditor = await AuditService.allocate_auditor(
                book_id=book_id, database=database
            )
        else:
            related_auditor = User(id=item[0], email=item[1])
        return related_auditor
