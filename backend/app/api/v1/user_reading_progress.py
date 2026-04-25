from typing import Annotated

from fastapi import APIRouter, Body, Depends, Path
from sqlmodel.ext.asyncio.session import AsyncSession

from app.core.database import get_session
from app.core.security import get_current_user
from app.models.response_model import ResponseModel
from app.models.sql.user import FullUser
from app.services.user_reading_progress_service import UserReadingProgressService

user_reading_progress_router = APIRouter(
    prefix="/user_reading_progress", tags=["user_reading_progress"]
)


@user_reading_progress_router.get("/get", response_model=ResponseModel)
async def get_user_reading_progress(
    database: Annotated[AsyncSession, Depends(get_session)],
    current_user: Annotated[FullUser, Depends(get_current_user)],
):
    result = await UserReadingProgressService.get_user_reading_progress(
        user_id=current_user.id, database=database
    )
    return ResponseModel(data=result)


@user_reading_progress_router.patch("/add", response_model=ResponseModel)
async def update_user_reading_progress(
    database: Annotated[AsyncSession, Depends(get_session)],
    book_id: Annotated[int, Body(embed=True)],
    last_chapter_id: Annotated[int, Body(embed=True)],
    last_position: Annotated[int, Body(embed=True)],
    current_user: Annotated[FullUser, Depends(get_current_user)],
):
    """
    更新用户阅读进度
    :param current_user:    当前用户
    :param book_id:    图书ID
    :param last_chapter_id:    章节ID
    :param last_position:    最后阅读位置
    :return:    更新结果
    """
    result = await UserReadingProgressService.update_user_single_book_reading_progress(
        user_id=current_user.id,
        book_id=book_id,
        last_chapter_id=last_chapter_id,
        last_position=last_position,
        database=database,
    )
    if result:
        return ResponseModel()
    else:
        raise ValueError("更新用户阅读进度失败")


@user_reading_progress_router.delete(
    path="/delete/{book_id}", response_model=ResponseModel
)
async def delete_user_reading_progress(
    database: Annotated[AsyncSession, Depends(get_session)],
    book_id: Annotated[int, Path(title="book_id", description="book_id", gt=0)],
    current_user: Annotated[FullUser, Depends(get_current_user)],
):
    """
    删除用户阅读进度
    :param current_user:    当前用户
    :param book_id:    图书ID
    :return:    删除结果
    """
    result = await UserReadingProgressService.delete_user_single_book_reading_progress(
        user_id=current_user.id, book_id=book_id, database=database
    )
    if result:
        return ResponseModel()
    else:
        raise ValueError("删除阅读进度失败")
