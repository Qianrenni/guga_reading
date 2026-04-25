from typing import Annotated

from fastapi import APIRouter, Body, Depends, status
from sqlmodel.ext.asyncio.session import AsyncSession

from app.core.database import get_session
from app.core.security import get_current_user
from app.models.response_model import ResponseCode, ResponseModel
from app.models.sql.user import FullUser
from app.services.shelf_service import ShelfService

shelf_router = APIRouter(prefix="/shelf", tags=["shelf"])


@shelf_router.get("/get", response_model=ResponseModel)
async def get_shelf(
    database: Annotated[AsyncSession, Depends(get_session)],
    current_user: Annotated[FullUser, Depends(get_current_user)],
):
    """
    获取用户书架
    :param current_user:    当前用户
    :param database:    数据库会话
    :return:    书架列表
    """
    result = await ShelfService.get_shelf(user_id=current_user.id, database=database)
    return ResponseModel(data=result)


@shelf_router.post(
    "/add", response_model=ResponseModel, status_code=status.HTTP_201_CREATED
)
async def add_shelf(
    book_id: Annotated[int, Body(embed=True)],
    database: Annotated[AsyncSession, Depends(get_session)],
    current_user: Annotated[FullUser, Depends(get_current_user)],
):
    """
    添加图书到书架
    :param book_id:  图书ID
    :param database:    数据库会话
    :param current_user:    当前用户
    :return:       添加结果
    """
    result = await ShelfService.add_shelf(
        book_id=book_id, user_id=current_user.id, database=database
    )
    if result:
        return ResponseModel()
    else:
        return ResponseModel(code=ResponseCode.ERROR, message="添加失败")


@shelf_router.delete("/delete/{book_id}", response_model=ResponseModel)
async def delete_shelf(
    book_id: int,
    database: Annotated[AsyncSession, Depends(get_session)],
    current_user: Annotated[FullUser, Depends(get_current_user)],
):
    """
    删除书架上的图书
    :param book_id:    图书ID
    :param database:      数据库会话
    :param current_user:      当前用户
    :return:           删除结果
    """
    result = await ShelfService.delete_shelf(
        book_id=book_id, user_id=current_user.id, database=database
    )
    if result:
        return ResponseModel()
    else:
        return ResponseModel(code=ResponseCode.ERROR, message="删除失败")
