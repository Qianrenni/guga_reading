from sqlmodel import select
from sqlmodel.ext.asyncio.session import AsyncSession

from app.models.sql.user_reading_progress import UserReadingProgress


class UserReadingProgressService:
    @staticmethod
    async def get_user_single_book_reading_progress(
        user_id: int, book_id: int, database: AsyncSession
    ):
        """
        获取用户阅读进度
        :param user_id: 用户ID
        :param book_id: 图书ID
        :param database: 数据库会话
        :return: 用户阅读进度
        """
        statement = select(
            UserReadingProgress.book_id,
            UserReadingProgress.last_chapter_id,
            UserReadingProgress.last_read_at,
            UserReadingProgress.last_position,
        ).where(
            UserReadingProgress.user_id == user_id,
            UserReadingProgress.book_id == book_id,
        )

        result = await database.exec(statement)
        reading_progress = result.one_or_none()

        return reading_progress if reading_progress else None

    @staticmethod
    async def update_user_single_book_reading_progress(
        user_id: int,
        book_id: int,
        last_chapter_id: int,
        last_position: int,
        database: AsyncSession,
    ):
        """
        更新用户阅读进度
        :param user_id: 用户ID
        :param book_id: 图书ID
        :param last_chapter_id: 最后阅读章节ID
        :param last_position: 最后阅读位置
        :param database: 数据库会话
        :return: 更新结果
        """
        statement = select(UserReadingProgress).where(
            UserReadingProgress.user_id == user_id,
            UserReadingProgress.book_id == book_id,
        )

        result = await database.exec(statement)
        reading_progress = result.one_or_none()
        if reading_progress:
            reading_progress.last_chapter_id = last_chapter_id
            reading_progress.last_position = last_position
        else:
            reading_progress = UserReadingProgress(  # pyright: ignore[reportCallIssue]
                user_id=user_id,
                book_id=book_id,
                last_chapter_id=last_chapter_id,
                last_position=last_position,
            )
        # 获取用户书架
        try:
            database.add(reading_progress)
            await database.commit()
            return True
        except Exception as error:
            raise ValueError(f"更新用户阅读进度失败: {error}") from error

    @staticmethod
    async def delete_user_single_book_reading_progress(
        user_id: int, book_id: int, database: AsyncSession
    ):
        """
        删除用户阅读进度
        :param user_id: 用户ID
        :param book_id: 图书ID
        :param database: 数据库会话
        :return: 删除结果
        """
        try:
            statement = select(UserReadingProgress).where(
                UserReadingProgress.user_id == user_id,
                UserReadingProgress.book_id == book_id,
            )

            result = await database.exec(statement)
            reading_progress = result.one_or_none()
            if reading_progress:
                await database.delete(reading_progress)
                await database.commit()
                return True
            else:
                pass
            return True

        except Exception as error:
            raise ValueError(f"删除用户阅读进度失败: {error}") from error

    @staticmethod
    async def get_user_reading_progress(user_id: int, database: AsyncSession):
        """
        获取用户阅读进度
        :param user_id: 用户ID
        :param database: 数据库会话
        :return: 用户阅读进度
        """
        statement = select(
            UserReadingProgress.book_id,
            UserReadingProgress.last_chapter_id,
            UserReadingProgress.last_read_at,
            UserReadingProgress.last_position,
        ).where(UserReadingProgress.user_id == user_id)

        result = await database.exec(statement)
        reading_progress = result.all()

        return (
            [
                {
                    "book_id": item[0],
                    "last_chapter_id": item[1],
                    "last_read_at": item[2],
                    "last_position": item[3],
                }
                for item in reading_progress
            ]
            if reading_progress
            else None
        )
