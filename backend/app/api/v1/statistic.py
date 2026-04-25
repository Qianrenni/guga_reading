from typing import Annotated

from fastapi import APIRouter, Body, Depends
from sqlmodel.ext.asyncio.session import AsyncSession

from app.core.database import get_session
from app.enum.enum import ActionEnum, ReportEnum, ResourceTypeEnum, ScopeEnum
from app.models.sql.user import FullUser
from app.services.right_service import generate_permission_code, right_check
from app.services.statistic_service import StatisticService

statistic_router = APIRouter(prefix="/statistic", tags=["statistic"])


@statistic_router.post("/book-chapter", status_code=204)
async def statistic_post_book_chapter(
    current_user: Annotated[
        FullUser,
        Depends(
            right_check(
                [
                    generate_permission_code(
                        resource=ResourceTypeEnum.BOOK,
                        action=ActionEnum.READ,
                        scope=ScopeEnum.ALL,
                    )
                ]
            )
        ),
    ],
    book_id: Annotated[int, Body(embed=True, ge=1)],
    chapter_id: Annotated[int, Body(embed=True, ge=1)],
    event_type: Annotated[ReportEnum, Body(embed=True)],
    database: Annotated[AsyncSession, Depends(get_session)],
):
    await StatisticService.post_book_chapter(
        book_id=book_id,
        chapter_id=chapter_id,
        event_type=event_type,
        user=current_user,
        database=database,
    )
    return
