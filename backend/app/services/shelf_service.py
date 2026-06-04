from sqlmodel import select
from sqlmodel.ext.asyncio.session import AsyncSession

from app.models.database.shelf import Shelf


class ShelfService:
    """书架服务类,处理用户书架相关的业务逻辑"""

    @staticmethod
    async def get_shelf(user_id: int, database: AsyncSession):
        """
        获取用户书架中的图书列表

        @param user_id: 用户唯一标识ID
        @param database: 数据库会话对象
        @return list[dict]: 书架列表,每个元素包含book_id和created_at
        @raise AppError: 数据库查询失败时抛出
        """
        statement = select(Shelf.book_id, Shelf.created_at).where(
            Shelf.user_id == user_id
        )
        result = await database.exec(statement)
        return [{"book_id": item[0], "created_at": item[1]} for item in result]

    @staticmethod
    async def add_shelf(book_id: int, user_id: int, database: AsyncSession):
        """
        添加图书到用户书架

        @param book_id: 图书唯一标识ID
        @param user_id: 用户唯一标识ID
        @param database: 数据库会话对象
        @return bool: 添加成功返回True
        @raise Exception: 当添加失败时抛出异常
        """
        try:
            item = Shelf(book_id=book_id, user_id=user_id)
            database.add(item)
            await database.commit()
            return True
        except Exception as e:
            raise Exception("添加失败", e) from e

    @staticmethod
    async def delete_shelf(book_id: int, user_id: int, database: AsyncSession):
        """
        从用户书架中删除图书

        @param book_id: 图书唯一标识ID
        @param user_id: 用户唯一标识ID
        @param database: 数据库会话对象
        @return bool: 删除成功返回True,即使图书不在书架中也返回True
        @raise Exception: 当删除失败时抛出异常
        """
        try:
            statement = select(Shelf).where(
                Shelf.book_id == book_id, Shelf.user_id == user_id
            )

            result = await database.exec(statement)
            shelf_item = result.one_or_none()
            if shelf_item:
                await database.delete(shelf_item)
                await database.commit()
            return True
        except Exception as e:
            raise Exception(f"删除失败 {e}") from e
