from sqlmodel import select
from sqlmodel.ext.asyncio.session import AsyncSession

from app.models.sql.shelf import Shelf


class ShelfService:
    @staticmethod
    async def get_shelf(user_id: int, database: AsyncSession):
        """
        获取用户书架
        :param user_id: 用户ID
        :param database:      数据库会话
        :return:     书架列表
        """
        statement = select(Shelf.book_id, Shelf.created_at).where(
            Shelf.user_id == user_id
        )
        result = await database.exec(statement)
        return [{"book_id": item[0], "created_at": item[1]} for item in result]

    @staticmethod
    async def add_shelf(book_id: int, user_id: int, database: AsyncSession):
        """
        添加图书到书架
        :param book_id:  图书ID
        :param user_id:    用户ID
        :param database:      数据库会话
        :return:          添加结果
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
        删除书架上的图书
        :param book_id:  图书ID
        :param user_id:      用户ID
        :param database:        数据库会话
        :return:              删除结果
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
