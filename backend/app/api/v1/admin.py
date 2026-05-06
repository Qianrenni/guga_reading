# from typing import Annotated

from typing import Annotated

from fastapi import APIRouter, Depends, Query
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
    book_ids: Annotated[list[int] | None, Query()] = None,
):
    result = await AdminService.get_audit_book(
        database=database, user=current_user, book_ids=book_ids
    )
    return ResponseModel[list[Book]](data=result)


@admin_router.patch("/book", status_code=204)
async def update_book(
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
    book_id: Annotated[int, Query(ge=1)],
    is_pass: Annotated[bool, Query()],
):
    await AdminService.update_book(
        database=database, user=current_user, book_id=book_id, is_pass=is_pass
    )


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
    chapter_ids: Annotated[list[int] | None, Query()] = None,
):
    result = await AdminService.get_audit_book_chapter(
        database=database, user=current_user, chapter_ids=chapter_ids
    )
    return ResponseModel[list[BookChapter]](data=result)


@admin_router.patch("/chapter", status_code=204)
async def update_book_chapter(
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
    chapter_id: Annotated[int, Query()],
    is_pass: Annotated[bool, Query()],
):
    await AdminService.update_book_chapter(
        database=database, user=current_user, chapter_id=chapter_id, is_pass=is_pass
    )


@admin_router.get("/content/chapter", response_model=ResponseModel[str])
async def get_audit_book_chapter_content(
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
    chapter_id: Annotated[int, Query()],
):
    result = await AdminService.get_audit_book_chapter_content(
        database=database, user=current_user, chapter_id=chapter_id
    )
    return ResponseModel[str](data=result)
