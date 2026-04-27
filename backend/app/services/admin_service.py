from sqlmodel import select
from sqlmodel.ext.asyncio.session import AsyncSession

from app.models.sql.book_chapter import BookChapterDraft


class AdminService:
    @staticmethod
    async def get_book_chapter_draft(
        database: AsyncSession,
    ):
        statement = select(BookChapterDraft)
        result = await database.exec(statement)
        return list(result.all())
