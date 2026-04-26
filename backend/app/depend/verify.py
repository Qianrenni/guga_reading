from sqlmodel.ext.asyncio.session import AsyncSession

from app.core.error_handler import AppError
from app.middleware.logging import logger
from app.models.sql.user import FullUser
from app.services.author_book_service import AuthorBookService


async def verify_author_book(
    id: int,
    current_user: FullUser,
    database: AsyncSession,
    is_draft: bool = False,
):
    """
    验证用户是否是该书籍的作者
    :param book_id: 书籍id
    :param is_draft: 是否是草稿
    :return: 无
    """
    logger.debug(f"verify_author_book: {id}, {is_draft}")
    is_own_book = await AuthorBookService.is_own_book(
        database=database, user=current_user, id=id, is_draft=is_draft
    )
    logger.debug(f"verify_author_book: {is_own_book}")
    if not is_own_book:
        # 403 Forbidden
        raise AppError(message="您没有操作权限", status_code=403)
    return current_user
