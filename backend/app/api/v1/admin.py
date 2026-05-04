# from typing import Annotated

from typing import Annotated

from fastapi import APIRouter, Depends
from sqlmodel.ext.asyncio.session import AsyncSession

from app.core.database import get_session
from app.enum.enum import ActionEnum, ResourceTypeEnum, ScopeEnum
from app.models.response_model import ResponseModel
from app.models.sql.book import Book
from app.models.sql.book_chapter import BookChapter
from app.models.sql.user import FullUser
from app.services.admin_service import AdminService
from app.services.right_service import generate_permission_code, right_check

admin_router = APIRouter(prefix="/admin", tags=["admin"])


@admin_router.get("/book", response_model=ResponseModel[list[Book]])
async def get_audit_book(
    current_user: Annotated[
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
    result = await AdminService.get_audit_book(database=database, user=current_user)
    return ResponseModel[list[Book]](data=result)


@admin_router.get("/chapter", response_model=ResponseModel[list[BookChapter]])
async def get_audit_book_chapter(
    current_user: Annotated[
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
    result = await AdminService.get_audit_book_chapter(
        database=database, user=current_user
    )
    return ResponseModel[list[BookChapter]](data=result)
