from typing import Annotated

from fastapi import APIRouter, Depends
from sqlmodel.ext.asyncio.session import AsyncSession

from app.core.database import get_session
from app.enum.enum import ActionEnum, ResourceTypeEnum, ScopeEnum
from app.models.response_model import ResponseModel
from app.models.sql.book_chapter import BookChapterDraft
from app.models.sql.user import FullUser
from app.services.admin_service import AdminService
from app.services.right_service import generate_permission_code, right_check

admin_router = APIRouter(prefix="/admin", tags=["admin"])


@admin_router.get(
    "/book-chapter-draft", response_model=ResponseModel[list[BookChapterDraft]]
)
async def get_book_chapter_draft(
    _current_user: Annotated[
        FullUser,
        Depends(
            right_check(
                [
                    generate_permission_code(
                        resource=ResourceTypeEnum.BOOK,
                        action=ActionEnum.AUDIT,
                        scope=ScopeEnum.ALL,
                    )
                ]
            )
        ),
    ],
    database: Annotated[AsyncSession, Depends(get_session)],
):
    result = await AdminService.get_book_chapter_draft(database=database)
    return ResponseModel[list[BookChapterDraft]](data=result)
